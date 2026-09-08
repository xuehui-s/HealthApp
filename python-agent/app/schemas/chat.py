"""
API 数据模型（Pydantic v2）
统一响应封装：{code, message, data}，与 Java 网关的 Result 对齐
"""
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class ApiResponse(BaseModel):
    """统一响应封装"""
    code: int = 0
    message: str = "ok"
    data: Any = None


class ChatRequest(BaseModel):
    """对话请求"""
    user_id: Optional[int] = Field(None, description="用户ID（Java网关鉴权后传入）")
    user_type: Optional[int] = Field(None, description="用户类型：1-患者 2-医生 3-管理员")
    question: str = Field(..., min_length=1, max_length=2000, description="用户问题")
    session_id: Optional[str] = Field(None, description="会话ID（多轮记忆），不传则服务端生成")
    history: Optional[List[Dict[str, str]]] = Field(None, description="外部历史对话（优先于会话存储）")


class ChatData(BaseModel):
    """对话响应 data"""
    answer: str
    session_id: str = ""
    tools_used: List[str] = []
    iterations: int = 0
    latency: int = 0
    usage: Dict[str, int] = {}
    blocked: bool = False
    steps: List[Dict[str, Any]] = []


class StreamChatRequest(ChatRequest):
    """流式对话请求（SSE）"""
    pass


class HistoryMessage(BaseModel):
    role: str
    content: str


class KnowledgeAddRequest(BaseModel):
    """知识库文档新增（自动切分入索引）"""
    documents: List[str] = Field(..., min_length=1, description="文档内容列表")
    metadatas: Optional[List[Dict[str, Any]]] = Field(None, description="元数据（source/category等）")


class KnowledgeSearchRequest(BaseModel):
    query: str
    top_k: int = Field(3, ge=1, le=10)
