"""
医疗智能Agent 编排核心 v2
=========================
架构分层（一次请求的数据流）：

  请求 → [输入护栏] → [会话记忆装载] → [RAG知识检索] → [ReAct推理循环]
       → [工具调用(带越权防护)] → [输出护栏] → [会话记忆保存] → 响应

- ReAct 循环：思考(Reason) → 行动(Act: Function Calling) → 观察(Observe: 工具结果)
  循环有界（AGENT_MAX_ITERATIONS），防止死循环与费用失控
- 工具报错会作为 Observation 喂回模型，让它自行修正参数或换路回答
- 流式模式：每一轮推理通过 SSE 实时输出，工具调用过程以事件形式可视化
"""
import json
import time
from typing import Any, AsyncGenerator, Dict, List, Optional

from loguru import logger

from app.core.config import settings
from app.core.metrics import metrics
from app.core.security import GuardResult, guardrails, mask_pii
from app.memory.session_store import session_store
from app.models.llm_client import LLMError, llm_client
from app.rag.retriever import retriever
from app.tools.medical_tools import MEDICAL_TOOLS, ToolContext, execute_tool

SYSTEM_PROMPT = """你是智慧医疗平台的专业医疗智能助手"医智助手"，服务于医院的医生和患者。

## 你的能力
1. 解答医学问题：疾病知识、症状解读、用药指导、检查报告含义等
2. 调用工具查询：患者信息、预约记录、电子病历、药品信息、科室医生、号源余量
3. 检索医学知识库：基于权威诊疗指南与规范回答专业问题（RAG）
4. 协助医生进行临床决策支持

## 工作原则
1. **专业严谨**：回答必须基于权威指南和循证医学，检索到知识库资料时优先引用，不编造
2. **工具优先**：涉及患者具体数据（预约/病历/信息）时必须调用工具查询，禁止臆测
3. **安全第一**：涉及诊断和用药时必须提醒"具体诊疗请遵医嘱"；遇到急症症状立即建议拨打120或急诊
4. **身份边界**：患者用户只能查询其本人的信息，这是系统强制规则，无法绕过
5. **表达清晰**：先给核心结论再展开；分点陈述；重要提醒加粗；术语需通俗解释

## 工具使用规范
- 查患者信息/预约/病历：query_patient_info / query_appointments / query_medical_records
- 查药品：query_drug_info；查科室医生：query_department_doctors
- 查号源余量（推荐患者预约时间）：query_doctor_schedule
- 检索医学知识：medical_knowledge_search；儿童剂量计算：calculate_drug_dosage
- 工具返回 error 时：根据错误信息调整参数重试一次，仍失败则如实告知用户并提供通用建议

## 安全红线
- 不替代医生下诊断；不开具处方（剂量计算工具仅作参考）
- 与医疗无关的问题礼貌拒绝并引导回医疗话题
- 急症（胸痛、呼吸困难、大出血、意识障碍等）→ 立即建议急诊/120
"""

DISCLAIMER = "\n\n> 以上内容由 AI 生成，仅供参考，不能替代专业医生的诊断和治疗，具体诊疗请遵医嘱。"


def _has_disclaimer(text: str) -> bool:
    return ("仅供参考" in text) or ("遵医嘱" in text) or ("及时就医" in text)


class MedicalAgent:
    """医疗智能Agent 编排器"""

    def __init__(self):
        self.max_iterations = settings.AGENT_MAX_ITERATIONS
        self.memory_window = settings.AGENT_MEMORY_WINDOW

    # ------------------------------------------------------------------
    # 消息构建：system + RAG上下文 + 滑动窗口历史 + 当前问题
    # ------------------------------------------------------------------
    def _build_messages(self, question: str, history: List[Dict[str, str]],
                        context: str) -> List[Dict[str, str]]:
        messages = [{"role": "system", "content": SYSTEM_PROMPT}]
        if context:
            messages.append({
                "role": "system",
                "content": "以下是从医院医学知识库检索到的权威资料，回答时优先参考并在相应位置"
                           "标注来源（如 [参考1]）：\n\n" + context,
            })
        if history:
            messages.extend(history[-self.memory_window * 2:])
        messages.append({"role": "user", "content": question})
        return messages

    # ------------------------------------------------------------------
    # 非流式对话（完整 ReAct）
    # ------------------------------------------------------------------
    async def chat(self, question: str,
                   history: Optional[List[Dict[str, str]]] = None,
                   user_id: Optional[int] = None,
                   user_type: Optional[int] = None,
                   session_id: Optional[str] = None) -> Dict[str, Any]:
        start = time.time()
        metrics.counter.inc("agent_chat_total", "mode=\"chat\"")
        logger.info("Agent收到问题: user={} q={}", user_id, mask_pii(question[:80]))

        # 1. 输入护栏
        guard: GuardResult = guardrails.check_input(question)
        if not guard.allowed:
            metrics.counter.inc("agent_chat_total", "mode=\"chat\",blocked=\"true\"")
            return {
                "answer": guard.safe_response or guard.reason,
                "session_id": session_id or "",
                "blocked": True, "guardrail": guard.reason,
                "tools_used": [], "steps": [], "usage": {}, "latency": int((time.time() - start) * 1000),
                "iterations": 0,
            }
        question = guard.sanitized_text

        # 2. 历史（外部传入优先，否则读会话存储）
        if not history and session_id:
            history = await session_store.get_history(session_id)
        ctx = ToolContext(user_id=user_id, user_type=user_type)

        # 3. RAG 检索
        rag_results = retriever.search(question)
        context = retriever.format_context(rag_results) if rag_results else ""
        if rag_results:
            metrics.counter.inc("agent_rag_hits_total")

        # 4. ReAct 循环
        messages = self._build_messages(question, history or [], context)
        steps, tools_used = [], []
        usage_total = {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}
        answer = ""

        for iteration in range(1, self.max_iterations + 1):
            try:
                resp = await llm_client.chat(messages, tools=MEDICAL_TOOLS)
            except LLMError as e:
                answer = str(e)
                break
            for k in usage_total:
                usage_total[k] += resp["usage"].get(k, 0)

            if not resp["tool_calls"]:
                answer = resp["content"]
                answer = guardrails.check_output(answer)
                break

            # 记录本轮思考与行动
            step = {
                "iteration": iteration,
                "thought": (resp["content"] or "调用工具中")[:200],
                "actions": [{"tool": tc["name"], "args": tc["arguments"]} for tc in resp["tool_calls"]],
                "observations": [],
            }
            steps.append(step)
            messages.append({
                "role": "assistant", "content": resp["content"] or "",
                "tool_calls": [{"id": tc["id"], "type": "function",
                                "function": {"name": tc["name"],
                                             "arguments": json.dumps(tc["arguments"], ensure_ascii=False)}}
                               for tc in resp["tool_calls"]],
            })

            # 执行工具 → 观察
            for tc in resp["tool_calls"]:
                tools_used.append(tc["name"])
                result = await execute_tool(tc["name"], tc["arguments"], ctx)
                step["observations"].append({"tool": tc["name"], "result": result[:400]})
                messages.append({"role": "tool", "tool_call_id": tc["id"], "content": result})
        else:
            # 达到最大轮次：不再给工具，强制总结
            logger.warning("达到最大推理轮次，强制总结")
            resp = await llm_client.chat(messages)
            for k in usage_total:
                usage_total[k] += resp["usage"].get(k, 0)
            answer = resp["content"] + DISCLAIMER

        # 5. 记忆保存
        if session_id and answer:
            await session_store.append(session_id, "user", question)
            await session_store.append(session_id, "assistant", answer)

        latency = int((time.time() - start) * 1000)
        logger.info("Agent完成: latency={}ms iterations={} tools={}", latency, len(steps), tools_used)
        return {
            "answer": answer, "session_id": session_id or "", "blocked": False,
            "steps": steps, "tools_used": tools_used, "usage": usage_total,
            "latency": latency, "iterations": len(steps),
        }

    # ------------------------------------------------------------------
    # 流式对话（SSE 事件流）
    # 事件协议（JSON per event）：
    #   {"type": "status",  "stage": "guarding|retrieving|thinking|tool_call|tool_result"}
    #   {"type": "content", "delta": "增量文本"}
    #   {"type": "tool_call",  "tool": "...", "args": {...}}
    #   {"type": "tool_result","tool": "...", "summary": "..."}
    #   {"type": "done", meta...} / {"type": "error", "message": "..."}
    # ------------------------------------------------------------------
    async def stream_chat(self, question: str,
                          history: Optional[List[Dict[str, str]]] = None,
                          user_id: Optional[int] = None,
                          user_type: Optional[int] = None,
                          session_id: Optional[str] = None) -> AsyncGenerator[Dict, None]:
        start = time.time()
        metrics.counter.inc("agent_chat_total", "mode=\"stream\"")

        # 1. 输入护栏
        yield {"type": "status", "stage": "guarding"}
        guard = guardrails.check_input(question)
        if not guard.allowed:
            yield {"type": "content", "delta": guard.safe_response or guard.reason}
            yield {"type": "done", "blocked": True, "tools_used": [], "session_id": session_id or ""}
            return
        question = guard.sanitized_text

        # 2. 历史 + RAG
        if not history and session_id:
            history = await session_store.get_history(session_id)
        ctx = ToolContext(user_id=user_id, user_type=user_type)

        yield {"type": "status", "stage": "retrieving"}
        rag_results = retriever.search(question)
        context = retriever.format_context(rag_results) if rag_results else ""

        # 3. ReAct 流式循环
        messages = self._build_messages(question, history or [], context)
        tools_used, full_answer, usage_total = [], "", {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}

        for iteration in range(1, self.max_iterations + 1):
            yield {"type": "status", "stage": "thinking", "iteration": iteration}
            round_text = ""
            tool_calls, usage = [], {}

            try:
                async for block in llm_client.stream_chat(messages, tools=MEDICAL_TOOLS):
                    if block["type"] == "delta":
                        round_text += block["content"]
                        full_answer += block["content"]
                        yield {"type": "content", "delta": block["content"]}
                    else:  # final
                        tool_calls, usage = block["tool_calls"], block["usage"]
            except LLMError as e:
                yield {"type": "error", "message": str(e)}
                return

            for k in usage_total:
                usage_total[k] += usage.get(k, 0)

            if not tool_calls:
                break

            # 有工具调用：输出过程事件并继续循环
            messages.append({
                "role": "assistant", "content": round_text or "",
                "tool_calls": [{"id": tc["id"], "type": "function",
                                "function": {"name": tc["name"],
                                             "arguments": json.dumps(tc["arguments"], ensure_ascii=False)}}
                               for tc in tool_calls],
            })
            for tc in tool_calls:
                tools_used.append(tc["name"])
                yield {"type": "tool_call", "tool": tc["name"], "args": tc["arguments"]}
                result = await execute_tool(tc["name"], tc["arguments"], ctx)
                try:
                    summary_obj = json.loads(result)
                    summary = "查询失败：" + summary_obj["error"] if "error" in summary_obj \
                        else json.dumps(summary_obj, ensure_ascii=False)[:200]
                except Exception:
                    summary = result[:200]
                yield {"type": "tool_result", "tool": tc["name"], "summary": summary}
                messages.append({"role": "tool", "tool_call_id": tc["id"], "content": result})
        else:
            yield {"type": "content", "delta": DISCLAIMER}
            full_answer += DISCLAIMER

        # 4. 输出护栏兜底（流式过程中免责声明可能缺失）
        if full_answer and not _has_disclaimer(full_answer):
            delta = DISCLAIMER
            full_answer += delta
            yield {"type": "content", "delta": delta}

        # 5. 记忆保存
        if session_id and full_answer:
            await session_store.append(session_id, "user", question)
            await session_store.append(session_id, "assistant", full_answer)

        yield {"type": "done", "session_id": session_id or "", "tools_used": tools_used,
               "iterations": min(len(tools_used), self.max_iterations),
               "usage": usage_total, "latency": int((time.time() - start) * 1000),
               "blocked": False}


# 全局单例
medical_agent = MedicalAgent()
