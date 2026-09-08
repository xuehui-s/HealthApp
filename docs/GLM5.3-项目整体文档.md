# GLM5.3 · 智慧医疗综合服务平台 — 项目整体文档

> **阅读对象**：任何人（包括完全没有编程背景的人）。本文档假设读者对系统一无所知，从"这是什么"开始讲起，把每个接口、每个模块、AI Agent 的搭建原理与运行方式全部讲清楚。
>
> **本文档由 GLM5.3 基于代码逐行分析生成**，与代码同步（生成日期：2026-09-05）。

---

## 目录

1. [这个项目是什么？](#1-这个项目是什么)
2. [系统总体架构](#2-系统总体架构)
3. [技术栈清单（每项是干嘛的）](#3-技术栈清单)
4. [如何启动运行（手把手）](#4-如何启动运行)
5. [数据库设计](#5-数据库设计)
6. [接口大全（每个接口都有说明）](#6-接口大全)
   - 6.1 患者端接口
   - 6.2 医生端接口
   - 6.3 缴费接口
   - 6.4 消息接口
   - 6.5 管理端接口（新增企业版）
   - 6.6 AI Agent 接口
7. [AI Agent 深度解析（怎么搭的、怎么跑的、用了什么知识）](#7-ai-agent-深度解析)
8. [前端页面说明](#8-前端页面说明)
9. [关键业务流程图解](#9-关键业务流程图解)
10. [安全设计](#10-安全设计)
11. [已知限制与改进路线](#11-已知限制与改进路线)

---

## 1. 这个项目是什么？

一个**智慧医疗预约挂号系统**，模拟一家医院的线上业务闭环：

```
患者注册/登录 → 选科室 → 选医生 → 选日期时段 → 挂号（拿到排队号）
     → 到院签到 → 医生看诊 → 医生开缴费单 → 患者缴费（现金/微信/支付宝/银行卡/医保）
     → 退款可申请、可审核；医生可请假（自动取消受影响预约）；订单超时自动作废
全程有：站内消息通知、管理后台数据看板、以及一个可对话的"医疗 AI 智能助手"
```

它由 **三个程序** 组成：

| 程序 | 语言/框架 | 端口 | 作用 |
|---|---|---|---|
| `healthapp` 主后端 | Java 21 / Spring Boot 3.5 | **8081** | 业务中枢：登录鉴权、预约、缴费、消息、管理端接口，同时充当 AI 的"网关" |
| `python-agent` AI 服务 | Python 3.13 / FastAPI | **8000** | 医疗 AI 智能体：大模型对话、知识库检索、工具调用 |
| 前端页面（无框架） | 原生 HTML/CSS/JS | 由 8081 直接托管 | 两套页面：患者/医生**门户**（index.html）+ 运营**管理控制台**（admin.html） |

外部依赖（必须装在本机）：

| 依赖 | 端口 | 用途 |
|---|---|---|
| MySQL 8 | 3306 | 存所有业务数据（库名 `healthapp2`） |
| Redis 5+ | 6379 | 登录会话、验证码、分布式锁、延迟队列、AI 任务结果、AI 会话记忆 |
| RabbitMQ（可选） | 5672 | 站内消息异步落库；**没装也能跑**，只是消息链路静止并刷重试日志 |
| DeepSeek 等 LLM API Key | - | AI 对话的大模型；**没配也能跑**，AI 会返回优雅的降级提示 |

---

## 2. 系统总体架构

```
┌───────────────────────  浏览器  ────────────────────────┐
│  门户 index.html（患者/医生）      管理控制台 admin.html   │
└────────────┬──────────────────────────┬────────────────┘
             │ 同源 HTTP(fetch)          │ 同源 HTTP(fetch)
             ▼                          ▼
┌─────────────────────────────────────────────────────────┐
│              Java 主后端  :8081  (Spring Boot 3.5)        │
│                                                          │
│  ┌── 认证层 ─────────────────────────────────────────┐   │
│  │ 旧版 JwtInterceptor(门户) │ AdminAuthInterceptor   │   │
│  │ JwtAuthenticationFilter(统一解析Token→UserContext) │   │
│  └───────────────────────────────────────────────────┘   │
│  ┌── 业务层 ─────────────────────────────────────────┐   │
│  │ 预约(Redisson分布式锁+号源限流)  缴费(状态机+幂等)   │   │
│  │ 请假(常规/紧急)  消息  登录(验证码+BCrypt+JWT)      │   │
│  └───────────────────────────────────────────────────┘   │
│  ┌── 管理端 /api/v1/admin/** ─────────────────────────┐   │
│  │ 数据看板(JdbcTemplate统计) + 8类管理资源 + 操作审计  │   │
│  └───────────────────────────────────────────────────┘   │
│  ┌── AI 网关 /api/v1/agent/** ────────────────────────┐   │
│  │ 异步任务(线程池+Redis) │ SSE流式转发(OkHttp)        │   │
│  │ 对话审计落库(ai_conversation)                        │   │
│  └───────────────────────────────────────────────────┘   │
└───────┬──────────┬───────────┬──────────────┬───────────┘
        │          │           │              │
        ▼          ▼           ▼              ▼
     MySQL      Redis      RabbitMQ      Python Agent :8000
   (业务数据) (会话/锁/   (消息队列,     (FastAPI)
               延迟队列)    可选)              │
                                        ┌─────┴─────┐
                                        │ 大模型API  │
                                        │ (DeepSeek) │
                                        └───────────┘
```

**一句话理解各部分怎么配合**：
- 前端只跟 Java 后端说话（同源，无跨域问题）；
- Java 后端管人（鉴权）、管事（业务）、管家（管理端），自己**不做任何 AI 推理**，只做 AI 的"门卫 + 记录员"；
- Python Agent 专心做 AI：拿大模型、查数据库、检索知识库，把结果递回给 Java；
- Redis 是大家的"公共记事本"，MySQL 是"正式账本"。

---

## 3. 技术栈清单（每项是干嘛的）

### Java 主后端

| 技术 | 在本项目中的用途 |
|---|---|
| **Spring Boot 3.5**（Java 21） | 一切的地基：自动装配、内嵌 Tomcat、配置管理 |
| **Spring Security** | 提供过滤器链容器与 BCrypt 密码编码器（登录密码加密比对） |
| **MyBatis-Plus 3.5.5** | ORM：一条接口搞定增删改查；分页插件（必须显式注册才生效）；逻辑删除 |
| **MySQL + HikariCP** | 数据库与连接池 |
| **Redis + StringRedisTemplate** | 登录 Token 会话、短信验证码、防刷锁 |
| **Redisson 3.52** | ① 分布式锁（防止号源超卖）② 延迟队列（缴费单到期自动作废）③ 限流器（AI 接口限流）。**注意**：必须 3.45+，旧版 3.35 与 Boot 3.5 的 spring-data-redis 存在 `pExpire` 无限递归的兼容缺陷（本项目踩过并已修复） |
| **RabbitMQ (starter-amqp)** | 预约/缴费成功后的站内通知异步落库；带死信队列、生产者确认 |
| **Spring AOP** | 三个自定义切面：`@RateLimit` 限流、`@Idempotent` 幂等、`@OperationLog` 操作审计（写日志文件+数据库） |
| **JWT (jjwt 0.12)** | 无状态登录令牌，claims 携带 userId/userType（1患者 2医生 3管理员），subject=username 兼容新旧两代拦截器 |
| **Springdoc OpenAPI** | Swagger 文档：启动后访问 `/swagger-ui/index.html` |
| **Actuator + Micrometer** | 运维端点（`/actuator/health`、`/actuator/prometheus`） |
| **OkHttp** | AI 网关向 Python 发起 HTTP 与 SSE 流式转发 |
| **Lombok / MapStruct / Hutool / EasyExcel** | 消除模板代码 / 对象映射 / 工具集 / Excel 导出（后两者为能力储备） |

### Python AI Agent

| 技术 | 用途 |
|---|---|
| **FastAPI + Uvicorn** | 异步 Web 框架，自动生成接口文档 `/docs` |
| **Pydantic v2** | 请求/响应模型校验（问题长度、类型等在入口就拦住） |
| **OpenAI 兼容协议** | 一套代码适配 DeepSeek / 通义 / 豆包 / GLM 等所有兼容厂商 |
| **tenacity** | 大模型调用的指数退避重试（网络抖动/限流/5xx 才重试） |
| **redis-py (asyncio)** | 会话记忆（多轮对话滑动窗口），Redis 挂了自动降级为进程内存 |
| **pymysql（自建连接池）** | 工具查询业务库（患者/预约/药品/病历），借还式连接池 |
| **jieba + 自实现 BM25** | 医疗知识库检索（详见第 7 节），chromadb 可选开启向量混合检索 |
| **loguru** | 结构化日志（控制台+滚动文件+可选 JSON），request_id 全链路追踪 |

### 前端

| 技术 | 用途 |
|---|---|
| 原生 HTML/CSS/JS（**零依赖、零构建**） | 双页面应用；fetch 请求；SSE 用 fetch 流式读取（可携带鉴权头，EventSource 做不到） |
| 内联 SVG 图标 | 无 emoji、无图标字体、无 CDN，完全离线可用 |
| 手写 SVG/DOM 图表 | 管理端看板的折线/柱状/条形图，不依赖 ECharts 等外部库 |

---

## 4. 如何启动运行（手把手）

> 前置：已安装 JDK 21、Python 3.12+、MySQL 8、Redis。本机对应路径：JDK21 在 `D:\bin`，Redis 在 `D:\Redis\Redis-x64-5.0.14.1`，MySQL 客户端在 `D:\mysql\...`。

### 第 1 步：数据库

```bash
# 1) 建库建表（若还没有基础表）
mysql -uroot -p123456 < src/main/resources/db/init.sql
# 2) 企业版升级表（管理端/AI审计/药品/管理员等 8 张表 + 逻辑删除列 + 种子数据）
mysql -uroot -p123456 --default-character-set=utf8mb4 healthapp2 < src/main/resources/db/upgrade_enterprise.sql
```
> 注意：`upgrade_enterprise.sql` 中的 `ALTER TABLE ... ADD COLUMN deleted` 若重复执行会报 Duplicate column 错误，**忽略即可**（MySQL 8 不支持 IF NOT EXISTS 语法，已在脚本注释说明）。
> 内置管理员：`admin / 123456`。

### 第 2 步：Redis

```bash
cd D:\Redis\Redis-x64-5.0.14.1
redis-server.exe --port 6379     # 项目配置使用 6379
```

### 第 3 步：Python AI Agent

```bash
cd python-agent
pip install -r requirements.txt          # 全部为轻量依赖
copy .env.example .env                   # 然后编辑 .env：
#   LLM_API_KEY=sk-你的key               ← 不填也能启动，AI会优雅降级
#   LLM_BASE_URL=https://api.deepseek.com/v1
#   LLM_MODEL=deepseek-chat
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
# 验证：浏览器打开 http://localhost:8000/docs （接口文档）或 http://localhost:8000/health
```

### 第 4 步：Java 主后端

```bash
# IDEA 直接运行 HealthAppApplication；或命令行：
set JAVA_HOME=D:\bin
mvn package -DskipTests
java -jar target/healthapp-1.0.0-ENTERPRISE.jar
```

### 第 5 步：使用

| 入口 | 地址 | 说明 |
|---|---|---|
| 患者/医生门户 | http://localhost:8081/index.html | 登录后进入门户 |
| 运营管理控制台 | http://localhost:8081/admin.html | admin / 123456 |
| Swagger 接口文档 | http://localhost:8081/swagger-ui/index.html | 全部接口可在线调试 |
| AI 服务文档 | http://localhost:8000/docs | Python Agent 接口 |
| Prometheus 指标 | http://localhost:8000/metrics | Agent 的 QPS/Token/工具调用指标 |

> **测试提示**：登录需要短信验证码——演示环境验证码打印在 Java 后端控制台日志里（形如 `手机号：13800000001，验证码：123456`）。医生工号为身份证后 4 位（种子数据见 init.sql）。RabbitMQ 未安装不影响主流程，只是站内消息不会落库。

---

## 5. 数据库设计

共 18 张表，分两层（`src/main/resources/db/` 下两个 SQL 文件）：

### 基础业务（init.sql，10 张）

| 表 | 作用 | 关键字段 |
|---|---|---|
| `patient` | 患者 | username(手机号唯一)、password(BCrypt)、status |
| `doctor` | 医生 | username(身份证后4位)、department_id、title、status(1在职/0停诊) |
| `department` | 科室 | name、status |
| `appointment` | 预约单 | patient/doctor/dept_id、appoint_date、time_period(上午/下午)、queue_num、status(见下) |
| `patient_limit` | 患者每日行为限制 | patient_id+date 唯一，appoint_count、cancel_count |
| `doctor_leave` | 医生请假 | leave_date、time_period、type(1常规/2紧急)、status(1生效/0取消) |
| `pay_order` | 缴费单 | order_no(雪花)、transaction_no(流水)、total_amount、pay_method、status、expire_time |
| `bill_item` | 缴费明细 | order_no、category(DRUG/EXAM/...)、item_name、unit_price、quantity |
| `refund_order` | 退款单 | refund_no、order_no、refund_amount、status(0待审核/1已退款/2已拒绝) |
| `sys_message` | 站内消息 | user_id、user_type、msg_type(1~8)、is_read |

**预约单状态机**：`0 待就诊 → 1 已签到 → 2 待缴费 → 3 已缴费`；旁路：`4 患者取消 / 5 医生请假取消 / 6 缴费超时终止`。
**缴费单状态机**：`0 待缴费 → 1 已缴费`；旁路：`2 医生作废 / 3 超时作废`；退款后：`4 已退款 / 5 部分退款`。

### 企业版（upgrade_enterprise.sql，8 张）

| 表 | 作用 |
|---|---|
| `admin` | 管理员（BCrypt 密码，内置 admin/123456，角色 SUPER_ADMIN/ADMIN/OPERATOR） |
| `ai_conversation` | AI 对话审计：每轮"用户提问 + AI 回答"各一条，含 tokens、耗时、调用的工具 |
| `medical_record` | 电子病历（主诉/现病史/诊断/医嘱，0草稿 1提交 2审核） |
| `prescription` / `prescription_item` | 处方与处方明细 |
| `drug` | 药品字典（含 5 条种子数据：阿莫西林、布洛芬等） |
| `operation_log` | 管理端操作审计（谁/何时/干了什么/IP/耗时/成败） |
| `sys_dict` | 数据字典 |

另外：为 patient/doctor/department/appointment/pay_order/refund_order 六张表补了 `deleted` 逻辑删除列（MyBatis-Plus `@TableLogic`）。

---

## 6. 接口大全

> 两种响应格式并存（历史演进的结果，前端均已兼容）：
> - **legacy 格式**：`{success: true/false, errorMsg, data, total}` —— 门户业务接口（/patient、/doctor、/appointment、/payOrder、/message、/ai）
> - **企业版格式**：`{code: 200/..., message, data, timestamp, traceId}` —— 管理端与新版 AI 接口（/api/v1/**）
>
> 鉴权：除登录/验证码/注册外，请求需带 `Authorization: Bearer <token>`；患者/医生端历史约定还传 `patientId`/`doctorId` 请求头（自报身份，属已知历史设计，见第 10 节）。

### 6.1 患者端 `/patient`

| 方法 | 路径 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| GET | `/patient/getCode?username=` | username(手机号) | 无 | 发送 6 位验证码，存 Redis 5 分钟；1 分钟内防重复发送；演示环境打印到后端控制台 |
| POST | `/patient/login` | body `{username,password,code}` | `{token, patientId, username}` | 验证码+BCrypt 比对后签发 JWT（claims: userId/userType=1），同时写入 Redis 会话（30 分钟滑动续期） |
| POST | `/patient/register` | body `{username,password}` | 无 | 患者注册，手机号唯一，BCrypt cost=12 加密 |
| POST | `/patient/logout` | Header Authorization | 无 | 注销：删除 Redis 中的 Token |

### 6.2 医生端 `/doctor`

| 方法 | 路径 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| POST | `/doctor/login` | body `{username,password,code}` | `{token, doctorId, username, name}` | 同患者登录，工号=身份证后4位 |
| GET | `/doctor/sendCode?username=` | username | 无 | 医生验证码 |
| POST | `/doctor/logout` | Header | 无 | 注销 |
| GET | `/doctor/appointment/my` | Header doctorId | 预约列表 | 医生的门诊队列（待就诊预约） |
| POST | `/doctor/appointment/leave/normal` | body `{leaveDate,endDate,timePeriod}` | 无 | 常规请假：须提前 7~30 天；按天×时段逐条生效（Redisson 锁防并发） |
| POST | `/doctor/appointment/leave/emergency` | 同上 | 无 | 紧急请假：限未来 7 天内；**自动取消**该时段全部待就诊预约（状态置 5）并归还号源、逐个通知患者 |
| POST | `/doctor/appointment/leave/cancel?id=` | id | 无 | 取消请假 |
| GET | `/doctor/appointment/leave/my` | - | 请假列表 | 我的请假记录 |

### 6.3 预约挂号 `/appointment`

| 方法 | 路径 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| GET | `/appointment/dept/list` | - | 科室数组 | 在用科室列表 |
| GET | `/appointment/day/status?deptId=` | deptId | 7 天数组 `[{date,isFull,remaining}]` | 未来 7 天号源概况（容量=有效医生数×15×2） |
| GET | `/appointment/doctor/list/with-leave?deptId&date&period` | 三参数 | `[{id,name,onLeave}]` | 该时段出诊医生，onLeave=true 前端置灰 |
| GET | `/appointment/period/status?deptId&date&period` | 三参数 | `{period,canAppoint,remaining}` | 该时段余量与可约性 |
| POST | `/appointment/submit` | body `{deptId,doctorId,appointDate,timePeriod}` + patientId 头 | `{queueNum, frontCount, appointment}` | **核心预约**，校验链：非过去日/最多提前7天/时段截止(上午12点/下午18点)/医生在岗/未请假/防重复预约/每日限1单/单时段每医生限15号 → 排队号=已约数+1。全程 Redisson 分布式锁 `appoint:lock:{doctorId}:{date}:{period}` 保证不超卖 |
| GET | `/appointment/my` | patientId 头 | 预约列表 | 我的预约 |
| POST | `/appointment/cancel?id=` | id | 无 | 取消（仅待就诊可取消；当日累计取消上限 3 次；归还号源） |

### 6.4 缴费 `/payOrder`

| 方法 | 路径 | 入参 | 说明 |
|---|---|---|---|
| POST | `/payOrder/create` | body `{appointmentId, items:[{category,itemName,unitPrice,quantity}], remark}` | 医生为"已签到"的预约开缴费单；服务端按明细重新计算总额（防篡改）；雪花单号；同时投递 Redisson 延迟队列（1 天后仍未缴费自动作废） |
| POST | `/payOrder/pay` | body `{orderNo, payMethod, payerId, remark?}` | 确认缴费：状态机+Redisson 锁+二次校验保证幂等；生成交易流水号 TXN+时间戳+随机数；预约推进为"已缴费" |
| POST | `/payOrder/invalid?orderNo=` | orderNo | 医生作废待缴费单 |
| GET | `/payOrder/waitPay/{patientId}` | patientId | 查患者待缴费单（收费员收款用） |
| GET | `/payOrder/myList?patientId=&page=&size=` | 分页参数 | 我的缴费记录（legacy 格式 data+total） |
| GET | `/payOrder/detail/{orderNo}` | orderNo | 单据详情（含费用明细） |
| POST | `/payOrder/refund/apply` | body `{orderNo,refundAmount,refundMethod,refundReason,operatorId}` | 申请退款：可退额=总额-已通过退款额；全额退→状态4，部分退→状态5；生成退款单(待审核) |
| POST | `/payOrder/refund/audit?refundId=&auditResult=&auditRemark=&auditorId=` | 四参 | 退款审核：通过→打款+通知；拒绝→订单状态回滚 |
| GET | `/payOrder/refund/list/{patientId}` | patientId | 退款记录 |
| POST | `/payOrder/settlement?cashierId=` | cashierId | 日终结算：当日总笔数/营收/退款/净营收+分支付方式统计 |
| POST | `/payOrder/revenueStats` | body `{startDate,endDate,payMethod?}` | 区间营收统计 |
| POST | `/payOrder/receiptPrinted?orderNo=` | orderNo | 标记收据已打印 |

### 6.5 消息 `/message`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/message/my?userId=&userType=` | 我的站内消息（1患者 2医生） |
| GET | `/message/unread?userId=&userType=` | 未读数（前端 30 秒轮询，导航栏红点） |
| POST | `/message/read/{id}` | 标记已读 |

消息的产生：业务事件 → `MessageListener`（事务提交后）→ Redis 队列 → `MessageConsumeJob`（每秒批量 50 条）落库；RabbitMQ 链路（`healthapp.message.exchange` → 患者队列/医生队列 → `MessageConsumer` 落库）为并行能力，含死信队列兜底。

### 6.6 管理端 `/api/v1/admin/**`（需管理员身份）

鉴权：`POST /api/v1/admin/auth/login` 换取 token（userType=3），`AdminAuthInterceptor` 拦截全部管理端接口（除登录外），校验 JWT 签名+Redis 会话一致性+角色，30 分钟滑动续期。**所有写操作自动进入 `operation_log` 审计表**（AOP 切面，异步落库）。

认证：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/admin/auth/login` | body `{username,password}` → `{token,userId,username,name,role}` |
| GET | `/api/v1/admin/auth/me` | 当前登录管理员 |
| POST | `/api/v1/admin/auth/logout` | 注销 |

数据看板（6 个统计接口）：

| 方法 | 路径 | 返回 |
|---|---|---|
| GET | `/api/v1/admin/stats/dashboard` | `{todayAppointments, todayRevenue, totalPatients, totalDoctors, waitPayOrders, todayAiChats}` |
| GET | `/api/v1/admin/stats/appointment-trend?days=7` | 近 N 天预约量 `[{date,count}]` |
| GET | `/api/v1/admin/stats/revenue-trend?days=7` | 近 N 天营收 `[{date,revenue}]` |
| GET | `/api/v1/admin/stats/department-ranking` | 科室预约 Top10 |
| GET | `/api/v1/admin/stats/doctor-workload` | 医生接诊量 Top10 |
| GET | `/api/v1/admin/stats/pay-method-distribution` | 支付方式分布 |

资源管理（全部支持服务端分页，返回 `PageResult{records,total,page,size,totalPages}`）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/admin/patients?page&size&keyword` | 患者分页（keyword 匹配姓名/手机号；**密码已脱敏**） |
| PUT | `/api/v1/admin/patients/{id}/status?status=` | 启用/禁用患者 |
| GET | `/api/v1/admin/doctors?page&size&deptId&keyword` | 医生分页（密码、身份证号脱敏） |
| PUT | `/api/v1/admin/doctors/{id}/status?status=` | 停诊/恢复 |
| GET | `/api/v1/admin/departments` | 科室列表 |
| POST | `/api/v1/admin/departments` | 新增科室（查重） |
| PUT | `/api/v1/admin/departments/{id}` | 编辑科室 |
| PUT | `/api/v1/admin/departments/{id}/status?status=` | 启用/停用 |
| GET | `/api/v1/admin/appointments?page&size&status&date&deptId` | 预约分页（含患者/医生/科室姓名，批量查询防 N+1） |
| POST | `/api/v1/admin/appointments/{id}/cancel` | 管理员取消预约（归还号源） |
| GET | `/api/v1/admin/pay-orders?page&size&status&orderNo` | 缴费单分页 |
| GET | `/api/v1/admin/pay-orders/{orderNo}/detail` | 单据详情（复用业务服务） |
| GET | `/api/v1/admin/refunds?page&size&status` | 退款单分页 |
| POST | `/api/v1/admin/refunds/{id}/audit?auditResult=&auditRemark=` | 退款审核（复用业务服务，管理员身份作为审核人） |
| GET | `/api/v1/admin/ai/conversations?page&size&sessionId&role` | AI 对话审计记录 |
| GET | `/api/v1/admin/logs?page&size&module&username` | 操作日志 |

### 6.7 AI Agent 接口

**Java 网关层**（`/api/v1/agent/**`，`@RateLimit` 限流：每用户每分钟 30 次）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/agent/chat` | body `{question, session_id?}` → `{task_id, status:"processing"}`；后台线程池调 Python，结果写 Redis；前端轮询下一条接口 |
| GET | `/api/v1/agent/result/{taskId}` | → `{status: processing/done/not_found, answer?}` |
| GET | `/api/v1/agent/stream?question=&sessionId=` | **SSE 流式对话**（推荐，门户 AI 助手用的就是它）；事件协议见下 |
| GET | `/api/v1/agent/history?sessionId=` | 结构化历史 `[{role,content,ts}]`（Redis，滑动窗口 40 条，24h 过期） |
| DELETE | `/api/v1/agent/history?sessionId=` | 清空会话 |

**SSE 事件协议**（每行 `data: {JSON}`，以 `data: [DONE]` 结束）：

```
data: {"type":"start","session_id":"..."}          连接建立
data: {"type":"status","stage":"guarding"}          护栏检查中
data: {"type":"status","stage":"retrieving"}        知识库检索中
data: {"type":"status","stage":"thinking","iteration":1}  第 N 轮推理
data: {"type":"tool_call","tool":"query_drug_info","args":{...}}   调用工具
data: {"type":"tool_result","tool":"query_drug_info","summary":"..."}  工具结果
data: {"type":"content","delta":"对"}               增量正文（多次）
data: {"type":"done","tools_used":[...],"usage":{...},"latency":1234}  结束
data: {"type":"error","message":"..."}              出错（如未配置 LLM key）
data: [DONE]
```

**Python Agent 层**（`/api/v1/**`，另有 `/api/agent/**` 旧前缀兼容）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/agent/chat` | Java 网关调用的核心接口，返回完整结果（answer/steps/usage/latency/iterations/blocked） |
| POST | `/api/v1/agent/chat/stream` | SSE 版本，事件协议同上 |
| GET | `/health` / GET `/ready` / GET `/metrics` | 存活探针 / 就绪探针（Redis+MySQL+LLM配置+知识库） / Prometheus 指标 |
| POST | `/api/v1/knowledge/documents` | 添加知识文档（自动切分入索引） |
| POST | `/api/v1/knowledge/search` | 知识库检索调试 |
| POST | `/api/v1/knowledge/init` | 重新载入内置知识库 |
| GET | `/api/v1/agent/session/history`、DELETE `/api/v1/agent/session/{id}` | 会话历史（Python 侧自有存储） |

**兼容旧版**：`POST /ai/chat` + `GET /ai/result/{taskId}`（legacy 响应格式），内部已委托给新版 AI 网关。

---

## 7. AI Agent 深度解析

> 这一节回答三个问题：**怎么搭的？怎么跑的？用了什么知识/技术？**

### 7.1 总体架构：一次对话的完整旅程

```
用户提问 "布洛芬一次吃多少？"
   │
   ▼ ①输入护栏 (app/core/security.py)
   │   ├─ 长度/空值校验
   │   ├─ 急症分诊：命中"呼吸困难/大出血/抽搐不止..."→ 直接返回120建议，
   │   │   不消耗大模型Token（秒级响应）
   │   └─ 提示注入防御：识别"忽略之前的指令/泄露系统提示词"等攻击并净化
   │
   ▼ ②会话记忆装载 (app/memory/session_store.py)
   │   Redis List 存储 agent:session:{id}:messages，滑动窗口最近10轮，
   │   TTL 24h；Redis不可用自动降级进程内存
   │
   ▼ ③RAG 知识检索 (app/rag/retriever.py)
   │   jieba分词 → 同义词扩展("小孩发烧"→"儿童 发热") → BM25打分召回
   │   → (可选)chroma向量召回 → RRF融合排序 → 相关度阈值过滤
   │   命中的权威指南片段将注入系统提示词
   │
   ▼ ④ReAct 推理循环 (app/agent/medical_agent.py)   ← Agent 的心脏
   │      ┌──────────────────────────────────────────┐
   │      │ 思考(Reason)：大模型读上下文决定下一步      │
   │      │ 行动(Act)：输出 Function Calling 工具调用  │──┐
   │      │ 观察(Observe)：工具结果喂回大模型          │◄─┘
   │      │ 循环，直到大模型不再要求调用工具，输出最终答案│
   │      └──────────────────────────────────────────┘
   │   有界循环（最多 6 轮）防止死循环与费用失控；
   │   工具报错不炸进程——错误信息作为"观察"喂回模型，让它自行修正参数重试
   │
   ▼ ⑤输出护栏：免责声明兜底（模型漏说时自动补齐）
   │
   ▼ ⑥会话记忆保存 + 审计落库(ai_conversation) + 指标上报(metrics)
   │
   ▼ Java 网关 → 前端（SSE 逐事件实时渲染）
```

### 7.2 用到的核心技术与"为什么"

| 技术 | 在本项目中的实现 | 为什么是它 |
|---|---|---|
| **Function Calling（OpenAI 协议）** | 8 个医疗工具，Pydantic/JSON Schema 声明式注册（`@tool` 装饰器自动生成 OpenAI Schema） | 所有主流大模型（DeepSeek/Qwen/GLM/GPT）都兼容该协议，是企业接入的事实标准；比"把函数名塞进提示词让模型猜"可靠一个量级 |
| **ReAct 编排（自研）** | `medical_agent.py` 的 ReAct 循环 | 生产中大量企业**不用** LangChain 这类重框架（调试困难、抽象泄漏、版本动荡），而是用原生 SDK + 自研编排拿回控制权。文档层面对应 LangGraph 的 StateGraph：`guardrail → retrieve → agent → tools → respond` 节点化思路完全一致 |
| **RAG（BM25+同义词+RRF）** | 见 7.3 | 医疗问答不能全靠模型"记忆"（会编造），必须挂权威知识库。BM25 纯算法实现零依赖、可解释；chromadb 可选安装即升级为混合检索 |
| **多轮记忆（服务端）** | Redis 滑动窗口 | 前端只传 session_id，历史由服务端管理：防篡改、可审计、可清空，跨设备一致 |
| **安全护栏（双向）** | 输入：急症分诊/注入防御/PII 脱敏（日志中手机号打码）；输出：免责声明兜底 | 医疗是高风险域：急症必须硬编码分流（模型慢还可能说错）；免责声明是合规底线 |
| **数据越权防护** | 工具层的 `ToolContext`：患者身份强制只能查自己的数据（`owned_param` 强制改写 patient_id） | 大模型生成的参数**不可信**，数据归属必须在代码层强制，而不是指望提示词 |
| **可观测性** | `/metrics`（Prometheus 文本格式）+ loguru JSON 日志 + request_id 全链路追踪 + Token 用量统计 | 企业 AI 上线三问：调了多少 Token（钱）、延迟多少（体验）、工具失败率（质量）——都有量化 |
| **优雅降级** | LLM 挂→返回固定话术；Redis 挂→内存记忆；MySQL 挂→知识类工具报错喂回模型 | AI 服务是全站的"最外层花瓶"，绝不能因为花瓶碎了把房子带塌 |

### 7.3 RAG 细节：BM25 是怎么检索的

1. **知识库**：内置 24 篇权威指南摘要（高血压/胸痛鉴别/儿童发热/心肺复苏/抗菌药物原则/中医煎服法等），启动时若库为空自动载入；也可通过接口/文件添加。
2. **切分（Chunking）**：长文档按 ~220 字符切块、相邻块重叠 40 字符，尽量在句号处断开——重叠保证语义不断裂。
3. **索引**：jieba 分词（未安装则字符 bigram 兜底）→ 建立倒排文档频率（IDF）表。
4. **打分（Okapi BM25）**：`score = Σ IDF(t) · f(t,d)·(k1+1) / (f(t,d) + k1·(1-b+b·|d|/avgdl))`，k1=1.5、b=0.75——兼顾词频、文档长度与词稀缺性，信息检索领域 40 年的事实标准。
5. **同义词扩展**：查询侧把口语映射到书面语（小孩→儿童、发烧→发热、心梗→心肌梗死、拉肚子→腹泻……），解决"用户说的"和"指南写的"用词不一致问题——这是中文医疗检索命中率的**最大杠杆**。
6. **融合**：若安装了 chromadb，向量召回与 BM25 召回用 **RRF（Reciprocal Rank Fusion，k=60）**融合——只看排名不看分数，两路信号互补（BM25 擅长精确词匹配，向量擅长语义泛化）。
7. **阈值过滤**：低相关片段不进提示词，避免噪声误导模型，也省 Token。

### 7.4 工具清单（8 个）

| 工具 | 数据来源 | 权限 |
|---|---|---|
| `query_patient_info` | patient 表 | 需登录；患者只能查本人 |
| `query_appointments` | appointment+doctor+department 联查 | 同上 |
| `query_medical_records` | medical_record 表 | 同上 |
| `query_drug_info` | drug 表模糊查询（用法/禁忌/不良反应） | 公开 |
| `query_department_doctors` | department+doctor | 公开 |
| `query_doctor_schedule` | 按 dept+date+period 统计各医生余号 | 公开（AI 可推荐就医时段） |
| `medical_knowledge_search` | RAG 知识库 | 公开 |
| `calculate_drug_dosage` | 纯计算（体重×剂量） | 公开（结果强制附"遵医嘱"警告） |

每个工具：独立超时（8s）、参数校验、异常转为模型可读的错误反馈、调用审计（谁/何时/参数脱敏后/耗时）。

### 7.5 可靠性设计

- **重试**：tenacity 指数退避（1s→2s→4s，最多 3 次）；只对网络错误/限流/5xx 重试，参数错误立即失败。
- **超时**：LLM 60s、工具 8s、Java 网关读 180s。
- **限流**：Python 进程内固定窗口（30 次/分/IP）+ Java 侧 Redisson 注解级限流（30 次/分/用户）双保险。
- **探针**：`/health`（活着吗）与 `/ready`（依赖都好吗，K8s 就绪门控）分离。

### 7.6 怎么扩展它

- **加工具**：在 `app/tools/medical_tools.py` 写一个函数 + `@tool(...)` 装饰器声明 Schema 即可，Agent 自动发现。
- **加知识**：`POST /api/v1/knowledge/documents` 或启动时自动载入；生产建议换向量库（Milvus/ES）+ 文档解析管线。
- **换大模型**：只改 `.env` 三个变量（KEY/BASE_URL/MODEL），代码零改动。

---

## 8. 前端页面说明

### 8.1 患者/医生门户（`index.html` + `css/portal.css` + `js/portal.js`）

- **登录页**：左品牌区+右表单的分栏布局，患者/医生同页切换（角色 Tab）。无渐变、无 emoji，品牌色 `#17649F` 深医疗蓝。
- **门户骨架**：顶部导航（患者：预约挂号/我的预约/缴费中心/消息中心/AI 助手；医生：门诊工作台/收费开单/日结营收/请假管理/消息中心/AI 助手）。
- **预约流程**：四步引导（科室网格 → 7 天号源日历条 → 时段余量卡片 → 医生卡片，请假医生自动置灰），右侧挂号单实时汇总，成功弹窗显示排队号。
- **缴费中心**：患者视角=待缴费/缴费记录/退款记录三页签+缴费凭证弹窗（可打印）+退款申请表单；医生视角=患者待缴费查询（收款/作废）+ 开单器（动态费用明细行、自动算总额）+ 日终结算与营收统计。
- **AI 助手**：聊天气泡流式渲染，工具调用以"过程标签"实时显示（如 `query_drug_info`），Ctrl+Enter 发送；SSE 用 **fetch 流式读取**（原生 EventSource 无法携带 Authorization 头，这是企业里常见的坑）。
- **消息中心**：未读数 30 秒轮询，导航栏红点提醒。
- 工程上：**零依赖**（无 axios/无 CDN/无构建），同源请求；兼容两代后端响应格式；所有用户输入经 HTML 转义防 XSS。

### 8.2 运营管理控制台（`admin.html` + `css/admin.css` + `js/admin.js`）

- 深色侧边栏 + 浅色内容区，9 个页面：**数据看板**（6 张指标卡 + 预约/营收趋势折线图 + 科室排行条形图 + 医生工作量 Top10 + 支付方式分布，图表为手写 SVG/DOM，零图表库）、**预约管理**（多维筛选+分页+取消）、**缴费订单**（筛选+分页+明细）、**退款审核**（通过/拒绝+备注，侧边栏待审角标）、**科室管理**（增改停启用）、**医生管理**（停诊/恢复）、**患者管理**（禁用/启用）、**AI 对话记录**、**操作日志**。
- 登录即管理端 JWT（userType=3），所有请求带 Bearer Token，401 类错误码自动退回登录页。

---

## 9. 关键业务流程图解

### 9.1 挂号防超卖（并发安全）

```
100 个患者同时抢同医生同时段最后 1 个号源
  → Redisson tryLock(appoint:lock:{doctorId}:{date}:{period})   只有 1 个进入
      → 校验号源 countByDoctorAndDatePeriod < 15
      → 插入预约 queue_num = count+1
  → 未抢到的快速失败（3 秒等待内拿不到锁→"号源已满"）
锁的释放与事务提交存在微小时序窗口，属已知细节（见第 11 节）
```

### 9.2 缴费单生命周期（延迟队列）

```
开单 → 投递 Redisson RDelayedQueue（1 天延迟）
    → 当日 23:59:59 为 expire_time
    → 期间缴费：成功回调 + 从延迟队列移除
    → 到期未缴费：延迟队列到期 → 作废订单(3) + 预约终止(6) + 通知患者
```

### 9.3 管理端操作审计

```
带 @OperationLog 的接口被调用
  → OperationLogAspect(AOP @Around) 记录：模块/描述/类型/参数/结果/IP/URI/耗时/成败
  → OperationLogRecorder(@Async 独立Bean，避免自调用导致异步失效) 异步写 operation_log 表
  → 管理端"操作日志"页可查；失败请求同时记录 errorMsg
```

---

## 10. 安全设计

**已实现**：
- 密码 BCrypt 存储（cost 10~12），登录限流（验证码 1 分钟防刷）、登录态 Redis 单点互踢（重新登录旧 Token 立即失效）；
- 管理端三层校验：JWT 签名 → Redis 会话一致性 → 角色（userType=3），`/api/v1/admin/**` 全覆盖 + 全操作审计；
- AI：患者数据越权强制拦截、提示注入净化、急症硬分流、日志 PII 脱敏、双层限流；
- 前端：全量 HTML 转义防 XSS；CORS 白名单收敛。

**已知历史局限（门户侧，作为改进路线保留）**：
1. 患者/医生接口的身份由前端 `patientId`/`doctorId` 请求头**自报**——横向越权风险。Token 现已携带 userId claims，后续改造点：在拦截器中将身份从 Token 注入而非信任 Header（保持前端兼容的前提下可灰度切换）。
2. 旧版 JWT secret 硬编码在代码里有默认值——生产应仅通过环境变量注入。
3. RabbitMQ 未配置发布确认回调的具体处理逻辑（空实现）。

---

## 11. 已知限制与改进路线

| # | 现状 | 改进方向 |
|---|---|---|
| 1 | 单体架构（业务+管理端+AI网关同进程） | 按现有 `/api/v1` 包结构拆微服务（Nacos/Feign 依赖已就位但默认关闭） |
| 2 | 患者端身份自报 Header | Token 声明注入身份（见第 10 节） |
| 3 | 知识库 BM25 内存索引，重启重建 | 接入向量数据库/ES，持久化索引+文档解析管线（PDF/Word） |
| 4 | RAG 无重排序(Rerank) | 接入 bge-reranker 或 LLM rerank，Top5→Top3 |
| 5 | AI 异步任务轮询 | 可升级 WebSocket 长连接 |
| 6 | 消息双链路（Redis队列 + RabbitMQ）并存 | 统一到 RabbitMQ，删除 Redis 队列链路 |
| 7 | 分布式锁与事务提交的时序窗口 | 锁释放移至事务同步器 `afterCompletion` |
| 8 | 未接入真实支付/短信 SDK | 预留 payMethod/验证码打印位，对接支付宝沙箱/阿里云短信 |

---

## 附录 A：本项目的运行清单（速查）

```bash
# 1) Redis（6379）
D:\Redis\Redis-x64-5.0.14.1\redis-server.exe --port 6379
# 2) MySQL：确保 healthapp2 库存在且执行过两个 db/*.sql
# 3) Python Agent（8000）
cd python-agent && python -m uvicorn app.main:app --port 8000
# 4) Java（8081）
cd HealthApp && java -jar target/healthapp-1.0.0-ENTERPRISE.jar
# 5) 浏览器
http://localhost:8081/index.html   （患者/医生门户）
http://localhost:8081/admin.html   （管理控制台 admin/123456）
```

## 附录 B：AI 服务环境变量速查（python-agent/.env）

| 变量 | 默认 | 说明 |
|---|---|---|
| `LLM_API_KEY` | your-api-key | **必填**才能有真实回答；不填服务可启动、AI 优雅降级 |
| `LLM_BASE_URL` | https://api.deepseek.com/v1 | 任意 OpenAI 兼容厂商 |
| `LLM_MODEL` | deepseek-chat | 模型名 |
| `AGENT_MAX_ITERATIONS` | 6 | ReAct 最大轮次 |
| `AGENT_MEMORY_WINDOW` | 10 | 记忆滑动窗口轮数 |
| `RAG_TOP_K` / `RAG_SCORE_THRESHOLD` | 3 / 0.5 | 检索条数/相关度阈值 |
| `REDIS_*` / `MYSQL_*` | 本机默认 | 会话记忆与工具数据源 |
| `RATE_LIMIT_PER_MINUTE` | 30 | 进程级限流 |
