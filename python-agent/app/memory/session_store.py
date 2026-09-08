"""
会话记忆（服务端多轮记忆）
企业级方案：Redis 存储滑动窗口对话历史，TTL 自动过期；
Redis 不可用时自动降级为进程内存字典（单机可用，重启丢失）。

Key 设计：agent:session:{session_id}:messages  (List，每条为 JSON)
"""
import json
from typing import Dict, List, Optional

from loguru import logger

from app.core.config import settings


class SessionStore:
    def __init__(self):
        self._memory: Dict[str, List[dict]] = {}
        self._redis = None
        self._init_redis()

    def _init_redis(self):
        try:
            import redis.asyncio as aioredis
            self._redis = aioredis.Redis(
                host=settings.REDIS_HOST,
                port=settings.REDIS_PORT,
                db=settings.REDIS_DB,
                password=settings.REDIS_PASSWORD or None,
                decode_responses=True,
                socket_timeout=2,
                socket_connect_timeout=2,
            )
            logger.info("会话记忆已连接 Redis: {}:{}/{}", settings.REDIS_HOST, settings.REDIS_PORT, settings.REDIS_DB)
        except Exception as e:
            self._redis = None
            logger.warning("Redis 连接失败，会话记忆降级为进程内存: {}", e)

    @property
    def redis_available(self) -> bool:
        return self._redis is not None

    def _key(self, session_id: str) -> str:
        return f"agent:session:{session_id}:messages"

    async def get_history(self, session_id: str, window: Optional[int] = None) -> List[Dict[str, str]]:
        """读取最近 window 轮历史（[{role, content}, ...]，按时间正序）"""
        window = window or settings.AGENT_MEMORY_WINDOW
        try:
            if self._redis is not None:
                raw = await self._redis.lrange(self._key(session_id), -window * 2, -1)
            else:
                raw = [json.dumps(m, ensure_ascii=False) for m in self._memory.get(session_id, [])][-window * 2:]
            return [json.loads(r) for r in raw if r]
        except Exception as e:
            logger.warning("读取会话历史失败(降级内存): {}", e)
            return [m for m in self._memory.get(session_id, [])][-window * 2:]

    async def append(self, session_id: str, role: str, content: str):
        """追加一条消息并裁剪窗口 + 刷新 TTL"""
        msg = json.dumps({"role": role, "content": content}, ensure_ascii=False)
        try:
            if self._redis is not None:
                key = self._key(session_id)
                await self._redis.rpush(key, msg)
                # 只保留最近 window*2 条（一轮 = user+assistant 两条）
                await self._redis.ltrim(key, -settings.AGENT_MEMORY_WINDOW * 2, -1)
                await self._redis.expire(key, settings.SESSION_TTL)
            else:
                hist = self._memory.setdefault(session_id, [])
                hist.append({"role": role, "content": content})
                if len(hist) > settings.AGENT_MEMORY_WINDOW * 2:
                    del hist[:len(hist) - settings.AGENT_MEMORY_WINDOW * 2]
        except Exception as e:
            logger.warning("写入会话记忆失败: {}", e)
            hist = self._memory.setdefault(session_id, [])
            hist.append({"role": role, "content": content})

    async def clear(self, session_id: str):
        try:
            if self._redis is not None:
                await self._redis.delete(self._key(session_id))
        except Exception as e:
            logger.warning("清空会话失败: {}", e)
        self._memory.pop(session_id, None)

    async def ping(self) -> bool:
        if self._redis is None:
            return False
        try:
            return bool(await self._redis.ping())
        except Exception:
            return False


# 全局单例
session_store = SessionStore()
