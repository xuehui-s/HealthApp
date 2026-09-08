"""
医疗智能Agent - FastAPI 主应用 v2
=================================
中间件链：CORS → 请求ID/访问日志 → 限流 → 指标采集
企业级要点：
- lifespan 统一初始化/释放资源（知识库、连接池）
- 全局异常兜底，统一响应封装
- /health /ready /metrics 三件套对接 K8s 与 Prometheus
"""
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from loguru import logger

from app.api.routes import router
from app.core.config import settings
from app.core.logging import new_request_id, request_id_ctx, setup_logging

setup_logging()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期：启动初始化 → 服务 → 优雅关闭"""
    logger.info("启动 {} v{} ...", settings.APP_NAME, settings.APP_VERSION)

    # 1. 知识库（为空时载入内置医疗指南）
    from app.rag.retriever import init_sample_knowledge, retriever
    init_sample_knowledge()
    logger.info("知识库就绪: {} 个文本块", retriever.count())

    # 2. 依赖自检（只打日志，不阻塞启动——生产环境由 /ready 决定是否放流量）
    redis_ok = await _check_redis()
    mysql_ok = _check_mysql()
    llm_ok = settings.LLM_API_KEY != "your-api-key"
    logger.info("依赖自检: Redis={} MySQL={} LLM配置={}",
                "OK" if redis_ok else "FAIL", "OK" if mysql_ok else "FAIL",
                "OK" if llm_ok else "未配置(请设置 LLM_API_KEY)")

    yield
    logger.info("应用关闭")


async def _check_redis() -> bool:
    try:
        from app.memory.session_store import session_store
        return await session_store.ping()
    except Exception:
        return False


def _check_mysql() -> bool:
    try:
        from app.tools.medical_tools import db_pool
        return db_pool.health()
    except Exception:
        return False


app = FastAPI(
    title="HealthApp 医疗智能Agent",
    description="企业级医疗AI助手：ReAct编排 / Function Calling / RAG混合检索"
                " / Redis会话记忆 / 安全护栏 / SSE流式 / Prometheus可观测",
    version=settings.APP_VERSION,
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)

# ---------- CORS（来源收敛为配置，不再 *） ----------
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8081", "http://localhost:3000",
                   "http://127.0.0.1:8081"],
    allow_credentials=True,
    allow_methods=["GET", "POST", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)


# ---------- 中间件：请求ID + 访问日志 + 限流 + 指标 ----------
class RequestContextMiddleware:
    """每个请求：生成 request_id（响应头返回，全链路日志串联）+ 访问日志 + 指标"""

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        rid = new_request_id()
        start = time.time()
        status_holder = {"status": 500}

        async def send_wrapper(message):
            if message["type"] == "http.response.start":
                status_holder["status"] = message["status"]
                headers = message.setdefault("headers", [])
                headers.append((b"x-request-id", rid.encode()))
            await send(message)

        try:
            await self.app(scope, receive, send_wrapper)
        finally:
            cost = time.time() - start
            path = scope.get("path", "-")
            method = scope.get("method", "-")
            if path not in ("/metrics", "/health", "/ready"):  # 探针不记访问日志
                from app.core.security import mask_pii
                logger.info("{} {} -> {} ({}ms)", method, path,
                            status_holder["status"], int(cost * 1000))
                try:
                    from app.core.metrics import metrics
                    metrics.observe_request(method, path, status_holder["status"], cost)
                except Exception:
                    pass


class RateLimitMiddleware:
    """进程级固定窗口限流（单机部署足够；多实例部署建议改用 Redis 令牌桶）"""

    def __init__(self, app):
        self.app = app
        self.window = 60.0
        self.limit = settings.RATE_LIMIT_PER_MINUTE
        self._buckets: dict = {}

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or not settings.RATE_LIMIT_ENABLED:
            await self.app(scope, receive, send)
            return
        path = scope.get("path", "")
        if not path.startswith("/api/"):  # 只限流业务接口
            await self.app(scope, receive, send)
            return
        client = "anon"
        for h, v in scope.get("headers", []):
            if h == b"x-forwarded-for":
                client = v.decode().split(",")[0].strip()
                break
            if h == b"x-real-ip":
                client = v.decode()
                break
        now = time.time()
        bucket = self._buckets.setdefault(client, [0, now])
        if now - bucket[1] > self.window:
            bucket[0], bucket[1] = 0, now
        bucket[0] += 1
        if bucket[0] > self.limit:
            resp = JSONResponse({"code": 429, "message": "请求过于频繁，请稍后再试", "data": None},
                                status_code=429)
            await resp(scope, receive, send)
            return
        await self.app(scope, receive, send)


class GlobalExceptionMiddleware:
    """兜底异常：任何未捕获异常统一为 {code:500,...}，不向前端泄漏堆栈"""

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        try:
            await self.app(scope, receive, send)
        except Exception as e:
            logger.exception("未捕获异常: {}", e)
            resp = JSONResponse({"code": 500, "message": "AI服务内部错误", "data": None},
                                status_code=500)
            await resp(scope, receive, send)


app.add_middleware(GlobalExceptionMiddleware)
app.add_middleware(RateLimitMiddleware)
app.add_middleware(RequestContextMiddleware)

# ---------- 路由 ----------
app.include_router(router)


@app.get("/", tags=["运维"], summary="服务信息")
async def root():
    return {
        "name": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "docs": "/docs",
        "api": "/api/v1",
        "metrics": "/metrics",
        "health": "/health",
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host=settings.APP_HOST,
                port=settings.APP_PORT, reload=settings.APP_DEBUG)
