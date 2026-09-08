"""
LLM 客户端 v2 —— 企业级大模型接入层
====================================
基于 OpenAI 兼容协议（DeepSeek / 通义 / 豆包 / GLM 等均兼容），在此之上补齐
生产必需的四件事：
1. 指数退避重试（tenacity）：连接错误 / 限流 / 5xx 自动重试，参数错误不重试
2. 可观测：每次调用的时延、Token 消耗全部上报 metrics
3. 统一超时与异常分类，调用方拿到的错误信息可直接给用户看
4. 流式接口：SSE 增量输出 + 用量统计
"""
import json
import time
from typing import AsyncGenerator, Dict, List, Optional

from loguru import logger
from openai import APIConnectionError, APIStatusError, AsyncOpenAI, RateLimitError
from tenacity import (
    retry,
    retry_if_exception,
    stop_after_attempt,
    wait_exponential,
)

from app.core.config import settings
from app.core.metrics import metrics


def _retryable(exc: Exception) -> bool:
    """仅对瞬时故障重试：网络错误、限流、5xx；参数/权限错误不重试"""
    if isinstance(exc, APIConnectionError):
        return True
    if isinstance(exc, RateLimitError):
        return True
    if isinstance(exc, APIStatusError):
        return exc.status_code >= 500
    return False


class LLMError(Exception):
    """LLM 调用失败（对外可安全展示）"""

    def __init__(self, message: str = "AI 服务暂时不可用，请稍后再试"):
        super().__init__(message)


class LLMClient:
    """大模型客户端（进程级单例）"""

    def __init__(self):
        self.client = AsyncOpenAI(
            api_key=settings.LLM_API_KEY,
            base_url=settings.LLM_BASE_URL,
            timeout=settings.LLM_TIMEOUT,
            max_retries=0,  # 重试交给 tenacity 统一管理
        )
        self.model = settings.LLM_MODEL

    # ---------- 同步式（带工具调用） ----------
    @retry(
        retry=retry_if_exception(_retryable),
        stop=stop_after_attempt(settings.LLM_MAX_RETRIES),
        wait=wait_exponential(multiplier=settings.LLM_RETRY_BASE_DELAY, max=10),
        reraise=True,
    )
    async def chat(self, messages: List[Dict], tools: Optional[List[Dict]] = None,
                   temperature: Optional[float] = None,
                   max_tokens: Optional[int] = None) -> Dict:
        start = time.time()
        kwargs = {
            "model": self.model,
            "messages": messages,
            "temperature": settings.LLM_TEMPERATURE if temperature is None else temperature,
            "max_tokens": max_tokens or settings.LLM_MAX_TOKENS,
        }
        if tools:
            kwargs["tools"] = tools
            kwargs["tool_choice"] = "auto"
        try:
            resp = await self.client.chat.completions.create(**kwargs)
            choice = resp.choices[0]
            latency = time.time() - start
            usage = resp.usage
            metrics.observe_llm(self.model, latency,
                                usage.prompt_tokens if usage else 0,
                                usage.completion_tokens if usage else 0, True)
            logger.info("LLM调用完成: latency={}ms finish={}", int(latency * 1000), choice.finish_reason)
            return {
                "content": choice.message.content or "",
                "tool_calls": [
                    {
                        "id": tc.id,
                        "name": tc.function.name,
                        "arguments": json.loads(tc.function.arguments) if tc.function.arguments else {},
                    }
                    for tc in (choice.message.tool_calls or [])
                ],
                "usage": {
                    "prompt_tokens": usage.prompt_tokens if usage else 0,
                    "completion_tokens": usage.completion_tokens if usage else 0,
                    "total_tokens": usage.total_tokens if usage else 0,
                },
                "latency": int(latency * 1000),
                "finish_reason": choice.finish_reason,
            }
        except Exception as e:
            metrics.observe_llm(self.model, time.time() - start, 0, 0, False)
            if _retryable(e):
                logger.warning("LLM瞬时故障，准备重试: {}", type(e).__name__)
                raise
            logger.error("LLM调用失败(不可重试): {}", e)
            raise LLMError() from e

    # ---------- 流式 ----------
    async def stream_chat(self, messages: List[Dict], tools: Optional[List[Dict]] = None,
                          temperature: Optional[float] = None) -> AsyncGenerator[Dict, None]:
        """
        流式输出。yield 两种块：
          {"type": "delta", "content": "..."}
          {"type": "final", "tool_calls": [...], "finish_reason": "...", "usage": {...}}
        """
        start = time.time()
        kwargs = {
            "model": self.model,
            "messages": messages,
            "temperature": settings.LLM_TEMPERATURE if temperature is None else temperature,
            "max_tokens": settings.LLM_MAX_TOKENS,
            "stream": True,
            "stream_options": {"include_usage": True},
        }
        if tools:
            kwargs["tools"] = tools
            kwargs["tool_choice"] = "auto"
        try:
            stream = await self.client.chat.completions.create(**kwargs)
            tool_calls_acc: Dict[int, Dict] = {}
            finish_reason = None
            usage = {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}
            async for chunk in stream:
                if getattr(chunk, "usage", None):
                    usage = {"prompt_tokens": chunk.usage.prompt_tokens or 0,
                             "completion_tokens": chunk.usage.completion_tokens or 0,
                             "total_tokens": chunk.usage.total_tokens or 0}
                if not chunk.choices:
                    continue
                delta = chunk.choices[0].delta
                if chunk.choices[0].finish_reason:
                    finish_reason = chunk.choices[0].finish_reason
                if delta and delta.content:
                    yield {"type": "delta", "content": delta.content}
                if delta and delta.tool_calls:
                    for tc in delta.tool_calls:
                        acc = tool_calls_acc.setdefault(tc.index, {"id": "", "name": "", "arguments": ""})
                        if tc.id:
                            acc["id"] = tc.id
                        if tc.function and tc.function.name:
                            acc["name"] = tc.function.name
                        if tc.function and tc.function.arguments:
                            acc["arguments"] += tc.function.arguments
            metrics.observe_llm(self.model, time.time() - start,
                                usage["prompt_tokens"], usage["completion_tokens"], True)
            parsed_calls = []
            for tc in tool_calls_acc.values():
                try:
                    args = json.loads(tc["arguments"]) if tc["arguments"] else {}
                except json.JSONDecodeError:
                    args = {}
                parsed_calls.append({"id": tc["id"] or f"call_{len(parsed_calls)}",
                                     "name": tc["name"], "arguments": args})
            yield {"type": "final", "tool_calls": parsed_calls,
                   "finish_reason": finish_reason, "usage": usage}
        except Exception as e:
            metrics.observe_llm(self.model, time.time() - start, 0, 0, False)
            logger.error("流式LLM调用失败: {}", e)
            raise LLMError() from e

    async def check_ready(self) -> bool:
        """就绪探测：仅校验配置完整性（不真实发请求，避免探针烧 Token）"""
        return bool(settings.LLM_API_KEY and settings.LLM_API_KEY != "your-api-key")


# 全局单例
llm_client = LLMClient()
