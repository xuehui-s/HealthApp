"""
医疗工具集 v2 —— Tool Registry（工具注册表）
=============================================
企业级 Function Calling 的标准做法：
1. 声明式注册：每个工具 = OpenAI JSON Schema + 处理函数 + 超时 + 权限范围
2. 数据越权防护：患者身份强制只能查自己的数据（user_id 来自 Java 网关鉴权，
   不信任大模型生成的参数）
3. 执行保护：asyncio.wait_for 超时 + 参数校验 + 异常转为模型可读的错误反馈
   （工具失败不炸进程，把错误喂回模型让它自行调整或向用户致歉）
4. 全量审计：每次工具调用记录 谁/何时/调了什么/耗时，上报 metrics
"""
import asyncio
import json
import queue
import time
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

import pymysql
from loguru import logger

from app.core.config import settings
from app.core.metrics import metrics
from app.core.security import mask_pii
from app.rag.retriever import retriever


# ==================== MySQL 轻量连接池 ====================
class _MySQLPool:
    """基于队列的简单连接池（借还模式），坏连接自动重建"""

    def __init__(self, size: int = 5):
        self._pool: queue.LifoQueue = queue.LifoQueue(maxsize=size)
        for _ in range(size):
            self._pool.put(self._new_conn())

    def _new_conn(self):
        return pymysql.connect(
            host=settings.MYSQL_HOST, port=settings.MYSQL_PORT,
            user=settings.MYSQL_USER, password=settings.MYSQL_PASSWORD,
            database=settings.MYSQL_DATABASE, charset="utf8mb4",
            cursorclass=pymysql.cursors.DictCursor,
            connect_timeout=3, read_timeout=5,
        )

    def execute(self, sql: str, params: tuple = (), fetch: str = "all") -> Any:
        """借连接执行 SQL，异常时重建连接重试一次"""
        for attempt in (1, 2):
            conn = None
            try:
                conn = self._pool.get(timeout=3)
                with conn.cursor() as cur:
                    cur.execute(sql, params)
                    rows = cur.fetchall() if fetch == "all" else cur.fetchone()
                conn.commit()
                return rows
            except pymysql.err.OperationalError:
                if conn is not None:
                    try:
                        conn.close()
                    except Exception:
                        pass
                if attempt == 2:
                    raise
                self._pool.put(self._new_conn())  # 重建后重试
            finally:
                if conn is not None and conn.open:
                    self._pool.put(conn)
        raise RuntimeError("数据库查询失败")

    def health(self) -> bool:
        try:
            self.execute("SELECT 1", fetch="one")
            return True
        except Exception:
            return False


db_pool = _MySQLPool(settings.MYSQL_POOL_SIZE)


# ==================== 工具上下文（鉴权主体） ====================
@dataclass
class ToolContext:
    """当前对话用户身份，由 Java 网关传入（JWT 鉴权后的可信数据）"""
    user_id: Optional[int] = None      # 患者ID / 医生ID
    user_type: Optional[int] = None    # 1-患者 2-医生 3-管理员

    @property
    def is_patient(self) -> bool:
        return self.user_type == 1


# ==================== 工具注册表 ====================
@dataclass
class Tool:
    name: str
    description: str
    parameters: Dict[str, Any]           # OpenAI JSON Schema
    handler: Any                          # 同步函数 (**args) -> dict
    scope: str = "public"                 # public | patient(需登录) | doctor
    timeout: int = settings.TOOL_TIMEOUT
    # 需要强制归属校验的参数名：患者身份会被强制改写为自己的 user_id
    owned_param: Optional[str] = None

    def to_openai_schema(self) -> Dict[str, Any]:
        return {"type": "function", "function": {
            "name": self.name, "description": self.description,
            "parameters": self.parameters}}


REGISTRY: Dict[str, Tool] = {}


def tool(name: str, description: str, parameters: Dict, scope: str = "public",
         owned_param: Optional[str] = None, timeout: int = settings.TOOL_TIMEOUT):
    """装饰器：把函数注册为 Agent 工具"""
    def deco(fn):
        REGISTRY[name] = Tool(name=name, description=description, parameters=parameters,
                              handler=fn, scope=scope, timeout=timeout, owned_param=owned_param)
        return fn
    return deco


# ============================================================
# 工具实现（同步函数，在线程池中执行，避免阻塞事件循环）
# ============================================================
@tool(
    name="query_patient_info",
    description="查询患者基本信息（姓名、性别、年龄等）。患者身份只能查询本人信息。",
    parameters={"type": "object", "properties": {
        "patient_id": {"type": "integer", "description": "患者ID"}},
        "required": ["patient_id"]},
    scope="patient", owned_param="patient_id",
)
def query_patient_info(patient_id: int) -> dict:
    p = db_pool.execute(
        "SELECT id, name, gender, age, phone, status, create_time "
        "FROM patient WHERE id = %s AND deleted = 0", (patient_id,), fetch="one")
    if not p:
        return {"error": f"未找到ID为{patient_id}的患者"}
    return {"patient": p}


@tool(
    name="query_appointments",
    description="查询患者的预约挂号记录（含医生、科室、日期时段、状态）。患者身份只能查询本人记录。",
    parameters={"type": "object", "properties": {
        "patient_id": {"type": "integer", "description": "患者ID"},
        "status": {"type": "integer", "description": "筛选状态：0-待就诊 1-已签到 2-待缴费 3-已缴费 4-患者取消 5-医生请假取消 6-超时终止"}},
        "required": ["patient_id"]},
    scope="patient", owned_param="patient_id",
)
def query_appointments(patient_id: int, status: Optional[int] = None) -> dict:
    sql = ("SELECT a.id, a.appoint_date, a.time_period, a.queue_num, a.status, "
           "d.name AS doctor_name, dept.name AS dept_name "
           "FROM appointment a LEFT JOIN doctor d ON a.doctor_id = d.id "
           "LEFT JOIN department dept ON a.dept_id = dept.id "
           "WHERE a.patient_id = %s AND a.deleted = 0")
    params: list = [patient_id]
    if status is not None:
        sql += " AND a.status = %s"
        params.append(status)
    sql += " ORDER BY a.appoint_date DESC LIMIT 20"
    rows = db_pool.execute(sql, tuple(params))
    status_map = {0: "待就诊", 1: "已签到", 2: "待缴费", 3: "已缴费",
                  4: "患者取消", 5: "医生请假取消", 6: "超时终止"}
    for r in rows:
        r["status_name"] = status_map.get(r["status"], "未知")
    return {"count": len(rows), "records": rows}


@tool(
    name="query_medical_records",
    description="查询患者的电子病历（主诉、诊断、治疗方案）。患者身份只能查询本人病历。",
    parameters={"type": "object", "properties": {
        "patient_id": {"type": "integer", "description": "患者ID"},
        "limit": {"type": "integer", "description": "返回条数，默认5"}},
        "required": ["patient_id"]},
    scope="patient", owned_param="patient_id",
)
def query_medical_records(patient_id: int, limit: int = 5) -> dict:
    rows = db_pool.execute(
        "SELECT id, chief_complaint, preliminary_diagnosis, treatment_plan, "
        "doctor_id, create_time FROM medical_record "
        "WHERE patient_id = %s AND deleted = 0 ORDER BY create_time DESC LIMIT %s",
        (patient_id, max(1, min(int(limit), 20))))
    return {"count": len(rows), "records": rows}


@tool(
    name="query_drug_info",
    description="查询药品信息（规格、用法用量、禁忌、不良反应、价格），支持名称模糊查询。",
    parameters={"type": "object", "properties": {
        "drug_name": {"type": "string", "description": "药品名称关键字，如'阿莫西林'"}},
        "required": ["drug_name"]},
)
def query_drug_info(drug_name: str) -> dict:
    rows = db_pool.execute(
        "SELECT drug_code, drug_name, generic_name, specification, manufacturer, "
        "category, price, usage_dosage, contraindication, side_effect "
        "FROM drug WHERE drug_name LIKE %s AND status = 1 LIMIT 10",
        (f"%{drug_name}%",))
    return {"count": len(rows), "drugs": rows}


@tool(
    name="query_department_doctors",
    description="查询科室的在职医生列表（职称等）。支持科室ID或科室名称。",
    parameters={"type": "object", "properties": {
        "dept_id": {"type": "integer", "description": "科室ID"},
        "dept_name": {"type": "string", "description": "科室名称，如'心血管内科'"}},
    },
)
def query_department_doctors(dept_id: Optional[int] = None, dept_name: Optional[str] = None) -> dict:
    if dept_name and not dept_id:
        row = db_pool.execute("SELECT id FROM department WHERE name LIKE %s LIMIT 1",
                              (f"%{dept_name}%",), fetch="one")
        if not row:
            return {"error": f"未找到名称包含'{dept_name}'的科室"}
        dept_id = row["id"]
    if not dept_id:
        return {"error": "请提供科室ID或科室名称"}
    rows = db_pool.execute(
        "SELECT id, name, title, status FROM doctor "
        "WHERE department_id = %s AND deleted = 0 AND status = 1", (dept_id,))
    return {"count": len(rows), "doctors": rows}


@tool(
    name="query_doctor_schedule",
    description="查询某科室某天上午/下午各医生的预约占用情况（可约余量），帮助患者选择就医时间。",
    parameters={"type": "object", "properties": {
        "dept_id": {"type": "integer", "description": "科室ID"},
        "date": {"type": "string", "description": "日期，格式 YYYY-MM-DD"},
        "period": {"type": "string", "description": "时段：上午 或 下午"}},
        "required": ["dept_id", "date", "period"]},
)
def query_doctor_schedule(dept_id: int, date: str, period: str) -> dict:
    rows = db_pool.execute(
        "SELECT d.id, d.name, d.title, "
        "(SELECT COUNT(*) FROM appointment a WHERE a.doctor_id = d.id "
        " AND a.appoint_date = %s AND a.time_period = %s AND a.status = 0) AS booked "
        "FROM doctor d WHERE d.department_id = %s AND d.status = 1 AND d.deleted = 0",
        (date, period, dept_id))
    capacity = 15  # 与Java后端 MAX_PER_PERIOD 保持一致
    for r in rows:
        r["capacity"] = capacity
        r["remaining"] = max(capacity - r["booked"], 0)
    return {"date": date, "period": period, "schedule": rows}


@tool(
    name="medical_knowledge_search",
    description="检索医学知识库，获取权威诊疗指南、用药规范、急救处理等专业知识（RAG）。",
    parameters={"type": "object", "properties": {
        "query": {"type": "string", "description": "检索问题，如'高血压诊断标准'"},
        "top_k": {"type": "integer", "description": "返回条数，默认3"}},
        "required": ["query"]},
)
def medical_knowledge_search(query: str, top_k: int = 3) -> dict:
    results = retriever.search(query, top_k=max(1, min(top_k, 5)))
    return {"query": query, "results": results}


@tool(
    name="calculate_drug_dosage",
    description="按体重计算儿童用药剂量（单次剂量/日剂量），用于给药参考。",
    parameters={"type": "object", "properties": {
        "drug_name": {"type": "string", "description": "药品名称"},
        "weight_kg": {"type": "number", "description": "体重(kg)"},
        "dose_per_kg": {"type": "number", "description": "每公斤体重每次剂量(mg/kg)"},
        "daily_times": {"type": "integer", "description": "每日给药次数，默认3"}},
        "required": ["drug_name", "weight_kg", "dose_per_kg"]},
)
def calculate_drug_dosage(drug_name: str, weight_kg: float, dose_per_kg: float,
                          daily_times: int = 3) -> dict:
    single = weight_kg * dose_per_kg
    return {
        "drug_name": drug_name, "weight_kg": weight_kg,
        "single_dose_mg": round(single, 1),
        "daily_dose_mg": round(single * daily_times, 1),
        "daily_times": daily_times,
        "warning": "以上为按体重的理论计算值，实际用药必须遵医嘱并注意药品规格换算",
    }


# ==================== 对外导出 ====================
def get_openai_tool_schemas() -> List[Dict[str, Any]]:
    return [t.to_openai_schema() for t in REGISTRY.values()]


async def execute_tool(name: str, arguments: Dict[str, Any], ctx: ToolContext) -> str:
    """
    执行工具（async：线程池 + 超时 + 越权防护 + 审计）
    始终返回 JSON 字符串（成功结果 或 错误反馈——错误会喂回模型自我修正）
    """
    t = REGISTRY.get(name)
    if t is None:
        return json.dumps({"error": f"未知工具: {name}"}, ensure_ascii=False)

    args = dict(arguments or {})

    # ---- 数据归属校验：患者强制只能查自己 ----
    if t.owned_param:
        if ctx.user_id is None:
            return json.dumps({"error": "需要登录后才能查询个人信息，请提示用户先登录系统"},
                              ensure_ascii=False)
        if ctx.is_patient:
            requested = args.get(t.owned_param)
            if requested is not None and int(requested) != int(ctx.user_id):
                logger.warning("越权拦截: user={} 尝试查询 patient={}", ctx.user_id, requested)
                return json.dumps({"error": "患者身份只能查询本人的信息"}, ensure_ascii=False)
            args[t.owned_param] = int(ctx.user_id)  # 强制改写为自己的ID

    def _run() -> str:
        start = time.time()
        ok = True
        try:
            result = t.handler(**args)
            return json.dumps(result, ensure_ascii=False, default=str)
        except TypeError as e:
            ok = False
            return json.dumps({"error": f"工具参数错误: {e}. 正确参数: {list(t.parameters.get('properties', {}).keys())}"},
                              ensure_ascii=False)
        except Exception as e:
            ok = False
            logger.error("工具执行失败: {} {}", name, e)
            return json.dumps({"error": "工具内部错误，请稍后重试或改用其他方式回答"},
                              ensure_ascii=False)
        finally:
            metrics.observe_tool(name, ok, time.time() - start)
            logger.info("工具调用: {} args={} 耗时={}ms", name, mask_pii(str(args))[:120],
                        int((time.time() - start) * 1000))

    try:
        # 同步 handler 丢进线程池执行，避免阻塞事件循环；wait_for 兜底超时
        return await asyncio.wait_for(asyncio.to_thread(_run), timeout=t.timeout)
    except asyncio.TimeoutError:
        metrics.observe_tool(name, False, t.timeout)
        return json.dumps({"error": f"工具执行超时({t.timeout}s)，请提示用户稍后再试"},
                          ensure_ascii=False)


# 兼容旧引用：老代码直接拿 schema 列表
MEDICAL_TOOLS = get_openai_tool_schemas()
