# HealthApp 企业级智慧医疗系统

> 秋招王牌项目 - 从Demo进化为企业级架构的智慧医疗预约系统

## 项目简介

HealthApp 是一套面向中小型医院/诊所的智慧医疗系统，覆盖**患者端、医生端、管理端**三端业务，并集成**AI智能医疗助手**，为医生提供临床决策支持。

## 技术架构

```
┌─────────────────────────────────────────────────────┐
│                   前端 (Vue3 + TS)                    │
│  患者端  │  医生端  │  管理端  │  AI助手             │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│              Java 后端 (Spring Boot 3)               │
│  Spring Cloud Alibaba │ MyBatis-Plus │ Redis+Redisson │
│  RabbitMQ │ 多级缓存 │ 分布式锁 │ 限流/幂等/日志      │
└──────────┬───────────────────────────┬───────────────┘
           │                           │
┌──────────▼──────────┐   ┌──────────▼───────────────┐
│   MySQL 8.0         │   │  Python AI Agent          │
│   18张表            │   │  FastAPI + LangChain      │
│   预约/支付/病历    │   │  RAG + Function Calling   │
└─────────────────────┘   │  ReAct + 多轮对话         │
                          └──────────────────────────┘
```

## 目录结构

```
HealthApp/
├── src/                    # Java后端源码
│   └── main/java/it/guowei/healthapp/
│       ├── common/         # 统一响应/异常/工具/注解
│       ├── config/         # 配置类 + AOP切面
│       ├── domain/         # 实体/DTO/VO
│       ├── infrastructure/ # Mapper/缓存/消息队列
│       ├── service/        # 业务服务
│       ├── controller/     # 接口层（患者/医生/管理/AI）
│       ├── filter/         # JWT过滤器
│       └── job/            # 定时任务
├── python-agent/           # Python AI Agent服务
│   ├── app/
│   │   ├── agent/          # Agent核心（ReAct推理）
│   │   ├── tools/          # 7个医疗工具
│   │   ├── rag/            # RAG检索引擎（ChromaDB）
│   │   ├── models/         # LLM客户端
│   │   ├── api/            # FastAPI路由
│   │   └── core/           # 配置
│   └── requirements.txt
├── healthapp-web/          # Vue3前端
│   ├── src/
│   │   ├── views/          # 页面（患者/医生/管理/AI）
│   │   ├── layouts/        # 三端布局
│   │   ├── router/         # 路由
│   │   ├── stores/         # Pinia状态
│   │   ├── api/            # API封装
│   │   └── utils/          # 工具
│   └── package.json
├── docs/                   # 文档（7份）
│   ├── 00-八股面试题总索引.md
│   ├── 01-技术架构文档.md
│   ├── 02-八股面试题大全-Java基础与JVM.md
│   ├── 03-八股面试题大全-并发编程.md
│   ├── 04-八股面试题大全-Spring与SpringBoot.md
│   ├── 05-八股面试题大全-MySQL与Redis.md
│   ├── 06-八股面试题大全-消息队列与AI.md
│   └── 07-八股面试题大全-前端与项目实战.md
└── src/main/resources/db/  # 数据库脚本
    ├── init.sql            # 基础表（10张）
    └── upgrade_enterprise.sql # 企业版扩展表（8张）
```

## 核心技术栈

### 后端
- **框架**：Spring Boot 3.5 + Java 21
- **微服务**：Spring Cloud Alibaba（Nacos + Sentinel + OpenFeign）
- **ORM**：MyBatis-Plus 3.5 + 动态数据源
- **缓存**：Redis + Redisson + Caffeine（多级缓存）
- **消息队列**：RabbitMQ（死信队列 + 手动ACK）
- **安全**：Spring Security + JWT + BCrypt
- **任务调度**：XXL-Job
- **监控**：Actuator + Prometheus
- **API文档**：SpringDoc OpenAPI

### AI Agent
- **框架**：FastAPI + LangChain + LangGraph
- **RAG**：ChromaDB + BGE嵌入模型
- **Agent**：ReAct推理 + Function Calling（7个工具）
- **LLM**：OpenAI兼容接口（豆包/通义/DeepSeek）

### 前端
- **框架**：Vue 3 + TypeScript + Vite
- **UI**：Element Plus
- **状态**：Pinia
- **路由**：Vue Router 4
- **图表**：ECharts
- **HTTP**：Axios

## 快速启动

### 1. 数据库初始化
```bash
mysql -u root -p < src/main/resources/db/init.sql
mysql -u root -p < src/main/resources/db/upgrade_enterprise.sql
```

### 2. 启动Java后端
```bash
mvn clean install
mvn spring-boot:run
# http://localhost:8081/swagger-ui.html
```

### 3. 启动Python Agent
```bash
cd python-agent
pip install -r requirements.txt
cp .env.example .env  # 配置API Key
python -m app.main
# http://localhost:8000/docs
```

### 4. 启动前端
```bash
cd healthapp-web
npm install
npm run dev
# http://localhost:3000
```

## 文档说明

| 文档 | 说明 |
|------|------|
| [技术架构文档](docs/01-技术架构文档.md) | 4万字超级详细，涵盖架构、数据库、全部接口说明 |
| [八股面试题总索引](docs/00-八股面试题总索引.md) | 159+道面试题总目录 + Top20必背 |
| [Java基础与JVM](docs/02-八股面试题大全-Java基础与JVM.md) | 27题 |
| [并发编程](docs/03-八股面试题大全-并发编程.md) | 25题 |
| [Spring与SpringBoot](docs/04-八股面试题大全-Spring与SpringBoot.md) | 25题 |
| [MySQL与Redis](docs/05-八股面试题大全-MySQL与Redis.md) | 25题 |
| [消息队列与AI](docs/06-八股面试题大全-消息队列与AI.md) | 25题 |
| [前端与项目实战](docs/07-八股面试题大全-前端与项目实战.md) | 32题 |

## 项目亮点

1. **微服务架构准备**：Spring Cloud Alibaba全生态
2. **AI Agent**：LangChain + RAG + ReAct + 工具调用
3. **高并发预约**：Redisson分布式锁 + 幂等 + 限流
4. **订单超时**：延迟队列 + 死信队列双方案
5. **企业级规范**：统一响应/异常/日志/操作审计
6. **三端分离**：患者/医生/管理独立布局权限
7. **数据可视化**：ECharts多维管理看板
