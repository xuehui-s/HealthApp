"""
结构化日志（loguru）
- 控制台 + 滚动文件双通道，支持 JSON 格式输出（对接 ELK / Loki）
- 提供 request_id 上下文变量，一次请求的全链路日志可串联
"""
import sys
from contextvars import ContextVar

from loguru import logger

# 请求级链路追踪 ID（由中间件注入，日志/响应头共用）
request_id_ctx: ContextVar[str] = ContextVar("request_id", default="-")


def setup_logging():
    """初始化日志系统（应在应用启动最早阶段调用一次）"""
    from app.core.config import settings

    logger.remove()
    fmt = (
        "<green>{time:YYYY-MM-DD HH:mm:ss.SSS}</green> | "
        "<level>{level: <8}</level> | "
        "<cyan>{extra[request_id]}</cyan> | "
        "<cyan>{name}</cyan>:<cyan>{line}</cyan> - <level>{message}</level>"
    )
    logger.configure(extra={"request_id": "-"})
    logger.add(sys.stderr, level=settings.LOG_LEVEL, format=fmt,
               backtrace=False, diagnose=False)

    if settings.LOG_FILE:
        logger.add(
            settings.LOG_FILE,
            level=settings.LOG_LEVEL,
            rotation="00:00",          # 每天 0 点滚动
            retention="30 days",
            enqueue=True,              # 多进程/多线程安全
            serialize=settings.LOG_JSON,
            encoding="utf-8",
        )
    return logger


def new_request_id() -> str:
    """生成并绑定新的 request_id，返回其值"""
    import uuid
    rid = uuid.uuid4().hex[:16]
    request_id_ctx.set(rid)
    return rid
