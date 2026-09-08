"""
医疗智能Agent - 全局配置（v2）
所有配置支持环境变量 / .env 文件覆盖，命名统一使用大写下划线。
"""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # ==================== 应用 ====================
    APP_NAME: str = "healthapp-medical-agent"
    APP_VERSION: str = "2.0.0"
    APP_HOST: str = "0.0.0.0"
    APP_PORT: int = 8000
    APP_DEBUG: bool = False

    # ==================== LLM ====================
    LLM_API_KEY: str = "your-api-key"
    LLM_BASE_URL: str = "https://api.deepseek.com/v1"
    LLM_MODEL: str = "deepseek-chat"
    LLM_TEMPERATURE: float = 0.3
    LLM_MAX_TOKENS: int = 2048
    LLM_TIMEOUT: int = 60
    # 网络抖动重试：次数与退避基数（指数退避 1s/2s/4s...）
    LLM_MAX_RETRIES: int = 3
    LLM_RETRY_BASE_DELAY: float = 1.0

    # ==================== Agent 编排 ====================
    # ReAct 最大推理轮次（防止死循环/费用失控）
    AGENT_MAX_ITERATIONS: int = 6
    # 多轮记忆滑动窗口（保留最近 N 轮对话）
    AGENT_MEMORY_WINDOW: int = 10
    # 单个工具执行超时（秒）
    TOOL_TIMEOUT: int = 8

    # ==================== RAG 检索 ====================
    # BM25 检索返回条数
    RAG_TOP_K: int = 3
    # BM25 相关度阈值：低于该分数的结果不进入上下文（过滤噪声）
    RAG_SCORE_THRESHOLD: float = 0.5
    # 文档切分：块最大字符数 / 相邻块重叠字符数
    RAG_CHUNK_SIZE: int = 220
    RAG_CHUNK_OVERLAP: int = 40
    # 可选向量库（安装 chromadb 后自动启用，实现 BM25+向量 混合检索）
    CHROMA_PERSIST_DIR: str = "./data/chroma"
    CHROMA_COLLECTION_NAME: str = "medical_knowledge"

    # ==================== 会话记忆（Redis） ====================
    REDIS_HOST: str = "localhost"
    REDIS_PORT: int = 6379
    REDIS_DB: int = 1
    REDIS_PASSWORD: str = ""
    # 会话记忆保留时长（秒），默认 24 小时
    SESSION_TTL: int = 86400

    # ==================== MySQL（工具数据源） ====================
    MYSQL_HOST: str = "localhost"
    MYSQL_PORT: int = 3306
    MYSQL_USER: str = "root"
    MYSQL_PASSWORD: str = "123456"
    MYSQL_DATABASE: str = "healthapp2"
    # 工具查询连接池大小
    MYSQL_POOL_SIZE: int = 5

    # ==================== 安全护栏 ====================
    # 触发急症拦截的关键词（命中后直接建议急诊，不进入大模型）
    EMERGENCY_KEYWORDS: str = "呼吸困难,大出血,意识不清,昏迷,抽搐不止,心脏骤停,剧烈胸痛,中毒,溺水,严重外伤"
    # 单次提问最大长度
    INPUT_MAX_LENGTH: int = 2000

    # ==================== 限流（进程级令牌桶） ====================
    RATE_LIMIT_ENABLED: bool = True
    RATE_LIMIT_PER_MINUTE: int = 30

    # ==================== 日志 ====================
    LOG_LEVEL: str = "INFO"
    LOG_FILE: str = "logs/agent.log"
    # True: 输出 JSON 结构化日志（接入 ELK 等采集平台）
    LOG_JSON: bool = False

    # ==================== 兼容旧配置（未使用，保留避免启动报错） ====================
    EMBEDDING_MODEL: str = "BAAI/bge-small-zh-v1.5"
    JAVA_BACKEND_URL: str = "http://localhost:8081"

    class Config:
        env_file = ".env"
        case_sensitive = True


settings = Settings()
