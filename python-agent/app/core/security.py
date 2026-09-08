"""
安全护栏层（Guardrails）
企业级 Agent 的输入/输出双向防护，位于大模型之前和之后：

输入护栏（InputGuard）
1. 急症分诊拦截：命中急症关键词直接返回急诊建议，不消耗大模型 Token
2. 提示注入防御：识别"忽略之前指令/泄露系统提示词"类攻击并净化
3. 长度与空值校验
4. PII 脱敏：手机号/身份证号在日志中打码，防止敏感信息落盘

输出护栏（OutputGuard）
1. 医疗免责声明兜底（模型漏说时自动补齐）
2. 急症升级提醒
"""
import re
from dataclasses import dataclass, field
from typing import Optional

from loguru import logger

from app.core.config import settings
from app.core.metrics import metrics

# 常见提示注入模式（大小写不敏感）
_INJECTION_PATTERNS = [
    r"忽略(以上|之前|上面)(的)?(所有)?(指令|设定|要求)",
    r"ignore\s+(all\s+)?(previous|prior|above)\s+instructions",
    r"(reveal|show|print|输出|打印)(你的)?(系统提示|system\s*prompt|初始指令)",
    r"你现在是?(一个)?(不受限制|没有限制|无道德)的",
    r"(developer\s+mode|DAN\s*mode)",
]

# PII：手机号 / 18位身份证
_PHONE_RE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
_IDCARD_RE = re.compile(r"(?<!\d)\d{17}[\dXx](?!\d)")
_INJECTION_RE = re.compile("|".join(_INJECTION_PATTERNS), re.IGNORECASE)


@dataclass
class GuardResult:
    """护栏裁决结果"""
    allowed: bool
    reason: str = ""
    # 拦截时直接返回给用户的安全回复（如急症建议）
    safe_response: Optional[str] = None
    sanitized_text: str = ""
    risks: list = field(default_factory=list)


def mask_pii(text: str) -> str:
    """PII 脱敏：用于日志输出，不用于大模型输入"""
    text = _PHONE_RE.sub(lambda m: m.group()[:3] + "****" + m.group()[-4:], text)
    text = _IDCARD_RE.sub(lambda m: m.group()[:6] + "********" + m.group()[-4:], text)
    return text


class Guardrails:
    """输入/输出双向护栏"""

    # 急症关键词（可由配置覆盖）
    _emergency_keywords = [k.strip() for k in settings.EMERGENCY_KEYWORDS.split(",") if k.strip()]

    _EMERGENCY_REPLY = (
        "**您描述的情况可能属于急症，请立即拨打 120 或前往最近的急诊科！**\n\n"
        "在等待救援期间：保持患者呼吸道通畅，不要随意移动伤者，"
        "如出现心跳呼吸骤停请立即进行心肺复苏（CPR）。\n\n"
        "本助手已暂停常规问答，优先保障急症处置。"
    )

    # ==================== 输入护栏 ====================
    def check_input(self, question: str) -> GuardResult:
        q = (question or "").strip()
        if not q:
            return GuardResult(False, "问题不能为空")

        if len(q) > settings.INPUT_MAX_LENGTH:
            metrics.observe_guardrail("too_long")
            return GuardResult(False, "问题过长，请精简后再试")

        # 1. 急症分诊：最高优先级，直接短路返回
        for kw in self._emergency_keywords:
            if kw in q:
                metrics.observe_guardrail("emergency")
                logger.warning("护栏拦截-急症关键词: keyword={}", mask_pii(kw))
                return GuardResult(False, "emergency", safe_response=self._EMERGENCY_REPLY,
                                   sanitized_text=q, risks=["emergency"])

        # 2. 提示注入：命中则剥离攻击片段并记录（不直接拒绝，保证可用性）
        risks = []
        if _INJECTION_RE.search(q):
            metrics.observe_guardrail("injection")
            q = _INJECTION_RE.sub("[已过滤]", q)
            risks.append("prompt_injection")
            logger.warning("检测到疑似提示注入，已净化处理")

        return GuardResult(True, sanitized_text=q, risks=risks)

    # ==================== 输出护栏 ====================
    def check_output(self, answer: str) -> str:
        """输出兜底：确保免责声明存在"""
        if not answer:
            return answer
        if ("遵医嘱" in answer) or ("仅供参考" in answer) or ("及时就医" in answer):
            return answer
        return answer + "\n\n> 以上内容由 AI 生成，仅供参考，不能替代专业医生的诊断和治疗，具体诊疗请遵医嘱。"


guardrails = Guardrails()
