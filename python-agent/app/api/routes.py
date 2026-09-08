"""
API 路由 v1 —— 医疗智能Agent
============================
| 方法   | 路径                          | 用途                       |
|--------|-------------------------------|----------------------------|
| POST   | /api/v1/agent/chat            | 对话（ReAct 完整结果）      |
| POST   | /api/v1/agent/chat/stream     | 对话（SSE 流式）            |
| GET    | /api/v1/agent/session/history | 会话历史                    |
| DELETE | /api/v1/agent/session/{id}    | 清空会话                    |
| GET    | /health  /ready  /metrics     | 健康检查/就绪/指标          |
| POST   | /api/v1/knowledge/documents   | 知识库添加文档              |
| POST   | /api/v1/knowledge/search      | 知识库检索调试              |

兼容：保留 /api/agent/* 旧前缀别名（Java 老网关仍在使用）。
"""
import uuid
from typing import Optional

from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import StreamingResponse
from loguru import logger

from app.agent.medical_agent import medical_agent
from app.core.config import settings
from app.memory.session_store import session_store
from app.rag.retriever import init_sample_knowledge, retriever
from app.schemas.chat import (
    ApiResponse, ChatData, ChatRequest, KnowledgeAddRequest,
    KnowledgeSearchRequest, StreamChatRequest,
)

router = APIRouter()
v1 = APIRouter(prefix="/api/v1")


# ==================== 健康检查 ====================
@router.get("/health", tags=["运维"])
async def health():
    """存活探针（Liveness）：进程活着即通过"""
    return {"status": "ok", "service": settings.APP_NAME, "version": settings.APP_VERSION}


@router.get("/ready", tags=["运维"])
async def ready():
    """就绪探针（Readiness）：核心依赖全部可用才放流量"""
    redis_ok = await session_store.ping()
    db_ok = False
    try:
        from app.tools.medical_tools import db_pool
        db_ok = db_pool.health()
    except Exception:
        pass
    llm_ok = await medical_agent_llm_ready()
    return {
        "status": "ok" if (llm_ok and db_ok) else "degraded",
        "checks": {"redis": redis_ok, "mysql": db_ok, "llm_config": llm_ok,
                   "knowledge_base": retriever.count()},
    }


async def medical_agent_llm_ready() -> bool:
    from app.models.llm_client import llm_client
    return await llm_client.check_ready()


@router.get("/metrics", tags=["运维"])
async def metrics_endpoint():
    """Prometheus 指标端点"""
    from app.core.metrics import metrics as m
    from fastapi import Response
    return Response(content=m.render(), media_type="text/plain; version=0.0.4; charset=utf-8")


# ==================== 对话 ====================
async def _do_chat(req: ChatRequest) -> ApiResponse:
    session_id = req.session_id or uuid.uuid4().hex
    try:
        result = await medical_agent.chat(
            question=req.question, history=req.history,
            user_id=req.user_id, user_type=req.user_type, session_id=session_id)
        result.pop("steps", None)  # 完整响应默认不带中间步骤（减小报文）
        return ApiResponse(code=0, message="ok", data=result)
    except Exception as e:
        logger.exception("对话失败: {}", e)
        raise HTTPException(status_code=500, detail="AI服务内部错误")


@v1.post("/agent/chat", response_model=ApiResponse, tags=["智能对话"], summary="对话（ReAct完整结果）")
async def chat(request: ChatRequest):
    return await _do_chat(request)


def _sse_event(payload: dict) -> str:
    import json as _json
    return f"data: {_json.dumps(payload, ensure_ascii=False)}\n\n"


async def _do_stream(req: StreamChatRequest):
    """SSE 流式输出：每个事件一行 data: {json}"""
    session_id = req.session_id or uuid.uuid4().hex

    async def gen():
        try:
            yield _sse_event({"type": "start", "session_id": session_id})
            async for event in medical_agent.stream_chat(
                    question=req.question, history=req.history,
                    user_id=req.user_id, user_type=req.user_type,
                    session_id=session_id):
                if event.get("type") == "error":
                    yield _sse_event(event)
                else:
                    yield _sse_event(event)
            yield "data: [DONE]\n\n"
        except Exception as e:
            logger.exception("流式对话异常: {}", e)
            yield _sse_event({"type": "error", "message": "AI服务内部错误"})
            yield "data: [DONE]\n\n"

    return StreamingResponse(gen(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache",
                                      "X-Accel-Buffering": "no"})


@v1.post("/agent/chat/stream", tags=["智能对话"], summary="对话（SSE流式输出）")
async def chat_stream(request: StreamChatRequest):
    return await _do_stream(request)


# ==================== 会话记忆 ====================
@v1.get("/agent/session/history", response_model=ApiResponse, tags=["智能对话"], summary="查询会话历史")
async def get_history(session_id: str = Query(..., description="会话ID")):
    history = await session_store.get_history(session_id)
    return ApiResponse(data={"session_id": session_id, "messages": history})


@v1.delete("/agent/session/{session_id}", response_model=ApiResponse, tags=["智能对话"], summary="清空会话")
async def clear_session(session_id: str):
    await session_store.clear(session_id)
    return ApiResponse(message="会话已清空")


# ==================== 知识库（运维/调试） ====================
@v1.post("/knowledge/documents", response_model=ApiResponse, tags=["知识库"], summary="添加知识文档")
async def add_documents(request: KnowledgeAddRequest):
    added = retriever.add_documents(request.documents, request.metadatas)
    return ApiResponse(data={"added_chunks": added, "total_chunks": retriever.count()})


@v1.post("/knowledge/search", response_model=ApiResponse, tags=["知识库"], summary="知识库检索（调试）")
async def search_knowledge(request: KnowledgeSearchRequest):
    results = retriever.search(request.query, top_k=request.top_k)
    return ApiResponse(data={"query": request.query, "results": results})


@v1.post("/knowledge/init", response_model=ApiResponse, tags=["知识库"], summary="初始化内置知识库")
async def init_knowledge():
    init_sample_knowledge()
    return ApiResponse(data={"total_chunks": retriever.count()})


router.include_router(v1)


# ==================== 旧前缀兼容（/api/agent/*） ====================
legacy = APIRouter(prefix="/api/agent", tags=["旧版兼容"])


@legacy.post("/chat", summary="[兼容] 对话")
async def legacy_chat(request: ChatRequest):
    return await _do_chat(request)


@legacy.post("/chat/stream", summary="[兼容] 流式对话")
async def legacy_stream(request: StreamChatRequest):
    return await _do_stream(request)


@legacy.get("/health", summary="[兼容] 健康检查")
async def legacy_health():
    return await health()


# 注册兼容路由
router.include_router(legacy)
