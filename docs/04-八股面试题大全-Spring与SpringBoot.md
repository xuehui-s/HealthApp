# 八股面试题大全 - Spring与Spring Boot篇

> 覆盖：IOC、AOP、Bean生命周期、事务、Spring Boot自动配置、Spring Cloud

---

## 一、Spring IOC

### 1. 什么是IOC？

**答案**：IOC（Inversion of Control，控制反转）是一种设计思想，将对象的创建和依赖管理交给Spring容器，而不是在代码中手动new。

**DI（依赖注入）**是IOC的实现方式：容器主动将依赖对象注入到需要它的类中。

**DI的三种方式**：
1. 构造器注入（推荐，依赖不可变，保证依赖不为空）
2. Setter注入
3. 字段注入（@Autowired，不推荐，测试困难）

---

### 2. Bean的生命周期？

**答案**：

```
1. 实例化Bean（调用构造方法）
2. 属性赋值（依赖注入，@Autowired）
3. 调用Aware接口方法（BeanNameAware、BeanFactoryAware、ApplicationContextAware）
4. BeanPostProcessor.postProcessBeforeInitialization（前置处理）
5. 初始化：
   - @PostConstruct注解方法
   - InitializingBean.afterPropertiesSet()
   - init-method指定方法
6. BeanPostProcessor.postProcessAfterInitialization（后置处理，AOP代理在此生成）
7. Bean就绪，可以使用
8. 销毁：
   - @PreDestroy注解方法
   - DisposableBean.destroy()
   - destroy-method指定方法
```

**关键点**：AOP代理对象在BeanPostProcessor的后置处理中生成（如AnnotationAwareAspectJAutoProxyCreator）。

---

### 3. Bean的作用域？

**答案**：

| 作用域 | 说明 |
|--------|------|
| singleton | 默认，整个容器只有一个实例 |
| prototype | 每次获取创建新实例 |
| request | Web环境，每个HTTP请求一个实例 |
| session | Web环境，每个Session一个实例 |
| application | Web环境，ServletContext一个实例 |
| websocket | WebSocket一个实例 |

**singleton注入prototype的问题**：singleton只注入一次prototype，之后不会更新。解决：方法注入（lookup-method）或ApplicationContext.getBean()。

---

### 4. Spring如何解决循环依赖？

**答案**：Spring通过三级缓存解决单例Bean的循环依赖。

**三级缓存**：
1. singletonObjects：一级缓存，存放完全初始化好的Bean
2. earlySingletonObjects：二级缓存，存放实例化但未属性赋值的Bean（半成品）
3. singletonFactories：三级缓存，存放Bean的ObjectFactory（可生成早期引用）

**解决流程**（A依赖B，B依赖A）：
1. 创建A，实例化后放入三级缓存（ObjectFactory）
2. A属性赋值，发现需要B，去创建B
3. B实例化，属性赋值发现需要A
4. B从三级缓存获取A的早期引用（ObjectFactory.getObject()），放入二级缓存
5. B完成初始化，放入一级缓存
6. A获取到B，完成初始化，放入一级缓存

**为什么需要三级缓存而不是二级**：三级缓存的ObjectFactory可以在获取时生成AOP代理对象，保证注入的是代理对象而不是原始对象。

**不能解决的循环依赖**：
1. prototype作用域（每次新建，无法缓存）
2. 构造器注入（实例化时就需要依赖，无法提前暴露）
3. 多例Bean

---

### 5. @Autowired和@Resource的区别？

**答案**：

| 特性 | @Autowired | @Resource |
|------|-----------|-----------|
| 来源 | Spring | JSR-250（Java标准） |
| 注入方式 | byType优先，byName兜底 | byName优先，byType兜底 |
| 属性 | required | name/type |
| 支持 | 构造器/字段/方法 | 字段/方法 |

---

### 6. Spring的事务传播行为？

**答案**：7种传播行为，默认REQUIRED。

| 传播行为 | 说明 |
|----------|------|
| REQUIRED | 有事务则加入，没有则新建（默认） |
| REQUIRES_NEW | 总是新建事务，挂起当前事务 |
| SUPPORTS | 有事务则加入，没有则非事务运行 |
| NOT_SUPPORTED | 非事务运行，挂起当前事务 |
| MANDATORY | 必须在事务中运行，否则抛异常 |
| NEVER | 必须非事务运行，有事务则抛异常 |
| NESTED | 嵌套事务，保存点（savepoint），子事务回滚不影响父事务 |

**REQUIRES_NEW vs NESTED**：
- REQUIRES_NEW：完全独立的新事务，子事务提交后父事务回滚不影响子
- NESTED：嵌套在父事务中，子事务回滚到保存点，父事务回滚子也回滚

---

### 7. Spring事务失效的场景？

**答案**：
1. 方法不是public（@Transactional只能作用于public方法）
2. 同类中方法调用（this调用，不走代理）
3. 异常类型不对（默认只回滚RuntimeException和Error，受检异常不回滚）
4. 异常被catch吞掉了
5. 数据库引擎不支持事务（MyISAM）
6. 没有被Spring管理（没有@Component等注解）
7. 多线程环境下事务不生效（线程不在Spring事务管理中）
8. propagation设置错误（如SUPPORTS在无事务时）

**解决同类调用失效**：注入自己（@Lazy）、AopContext.currentProxy()、拆分到不同类。

---

### 8. Spring事务的隔离级别？

**答案**：

| 隔离级别 | 脏读 | 不可重复读 | 幻读 |
|----------|------|-----------|------|
| READ_UNCOMMITTED | 可能 | 可能 | 可能 |
| READ_COMMITTED | 不可能 | 可能 | 可能 |
| REPEATABLE_READ | 不可能 | 不可能 | 可能（InnoDB通过MVCC+间隙锁解决） |
| SERIALIZABLE | 不可能 | 不可能 | 不可能 |

**Spring默认**：使用数据库默认隔离级别（MySQL默认REPEATABLE_READ）。

---

## 二、Spring AOP

### 9. 什么是AOP？应用场景？

**答案**：AOP（面向切面编程），将横切关注点（日志、事务、权限、监控）从业务逻辑中分离，通过动态代理在运行时织入。

**应用场景**：
- 事务管理（@Transactional）
- 日志记录（本项目的OperationLogAspect）
- 权限校验
- 限流（本项目的RateLimitAspect）
- 幂等性（本项目的IdempotentAspect）
- 性能监控
- 异常处理

---

### 10. Spring AOP的动态代理？

**答案**：
- **JDK动态代理**：目标类实现接口时使用，基于接口生成代理类
- **CGLIB动态代理**：目标类没有实现接口时使用，基于继承生成子类

**Spring Boot 2.x默认**：使用CGLIB（spring.aop.proxy-target-class=true）。

**区别**：
- JDK代理：基于接口，不能代理没有接口的类，代理类实现目标接口
- CGLIB：基于继承，不能代理final类/方法，通过ASM字节码生成子类

---

### 11. AOP的核心概念？

**答案**：
- **JoinPoint（连接点）**：可以被拦截的点（方法）
- **PointCut（切点）**：实际被拦截的连接点（表达式匹配）
- **Advice（通知）**：拦截后执行的代码
  - Before：前置通知
  - AfterReturning：返回通知
  - AfterThrowing：异常通知
  - After：后置通知（finally）
  - Around：环绕通知（最强大）
- **Aspect（切面）**：切点+通知的组合
- **Weaving（织入）**：将切面应用到目标对象的过程（编译期/类加载期/运行期）
- **Target（目标对象）**：被代理的对象

---

## 三、Spring Boot

### 12. Spring Boot的核心原理？自动配置？

**答案**：Spring Boot核心是"约定优于配置"，通过自动配置简化Spring应用开发。

**自动配置原理**：
1. @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
2. @EnableAutoConfiguration通过@Import(AutoConfigurationImportSelector.class)
3. AutoConfigurationImportSelector读取META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports文件（Spring Boot 2.7前是spring.factories）
4. 加载所有自动配置类
5. 通过@Conditional系列注解条件判断是否生效
   - @ConditionalOnClass：类路径存在某类
   - @ConditionalOnMissingBean：容器中没有某Bean
   - @ConditionalOnProperty：配置文件满足条件
   - @ConditionalOnWebApplication：Web应用

---

### 13. Spring Boot的starter是什么？

**答案**：starter是一组依赖的集合，一个starter引入相关的所有依赖，避免手动管理依赖版本。

**本项目用到的starter**：
- spring-boot-starter-web
- spring-boot-starter-data-redis
- spring-boot-starter-security
- spring-boot-starter-validation
- spring-boot-starter-actuator
- spring-boot-starter-amqp（RabbitMQ）
- spring-boot-starter-cache
- mybatis-plus-spring-boot3-starter
- redisson-spring-boot-starter

**自定义starter**：
1. 创建autoconfigure模块，写自动配置类
2. 创建starter模块，只依赖autoconfigure
3. 注册AutoConfiguration.imports

---

### 14. Spring Boot的配置文件加载顺序？

**答案**（优先级从高到低）：
1. 命令行参数
2. 系统环境变量
3. application-{profile}.yml（jar包外）
4. application-{profile}.yml（jar包内）
5. application.yml（jar包外）
6. application.yml（jar包内）

**profile切换**：spring.profiles.active=dev/test/prod

---

### 15. Spring Boot如何实现热部署？

**答案**：
- spring-boot-devtools：监听classpath变化，自动重启（比手动快，使用双类加载器）
- JRebel：商业工具，更快，不重启JVM
- IDEA的Spring Boot DevTools配置

---

## 四、Spring Cloud / 微服务

### 16. 什么是微服务？优缺点？

**答案**：微服务是将单体应用拆分为多个小型服务，每个服务独立部署、独立数据库、通过轻量级协议（HTTP/RPC）通信。

**优点**：
- 技术栈灵活，各服务可选用不同技术
- 独立部署，一个服务修改不影响其他
- 独立扩展，按需扩容
- 故障隔离，一个服务挂了不影响全局
- 团队自治，小团队负责一个服务

**缺点**：
- 分布式系统复杂度高（网络延迟、分布式事务、数据一致性）
- 运维成本高（需要服务治理、监控、链路追踪）
- 接口变更影响大
- 数据一致性难保证

---

### 17. 服务注册发现的原理？Nacos？

**答案**：

**原理**：
1. 服务启动时向注册中心注册自己的地址
2. 消费方从注册中心获取服务方地址列表
3. 注册中心通过心跳检测服务健康状态，剔除不健康服务
4. 消费方本地缓存地址列表，定期更新

**Nacos**：
- CP+AP切换：临时实例AP（Distro协议），持久实例CP（Raft协议）
- 支持配置中心：动态配置推送，长轮询
- 支持服务治理：权重、负载均衡、保护阈值

**Nacos vs Eureka**：
- Eureka：AP，只做注册发现，已停止维护
- Nacos：AP+CP，注册发现+配置中心，阿里开源，活跃

---

### 18. 什么是CAP定理？

**答案**：分布式系统中，一致性(C)、可用性(A)、分区容错性(P)三者不可兼得，最多满足两个。

- C（Consistency）：所有节点数据一致
- A（Availability）：每个请求都能得到响应
- P（Partition tolerance）：网络分区时系统仍能运行

**实际选择**：分布式系统P必须满足，所以在C和A之间权衡。
- CP：ZooKeeper、Nacos持久实例、HBase
- AP：Eureka、Nacos临时实例、Cassandra

---

### 19. 什么是熔断降级？Sentinel？

**答案**：

**熔断**：当依赖服务异常率达到阈值时，直接切断调用，快速失败，防止雪崩。
**降级**：系统压力大时，关闭非核心功能，保证核心功能可用。

**Sentinel**：阿里开源的流量控制组件。
- 流量控制：QPS、线程数、预热、排队等待
- 熔断降级：慢调用比例、异常比例、异常数
- 系统保护：CPU、负载、RT、入口QPS
- 热点参数限流

**Sentinel vs Hystrix**：
- Hystrix：线程池隔离，已停止维护
- Sentinel：信号量隔离，更轻量，支持更丰富的流控规则

---

### 20. 分布式事务解决方案？

**答案**：

1. **2PC（两阶段提交）**：准备阶段+提交阶段，强一致，性能差，阻塞
2. **TCC（Try-Confirm-Cancel）**：业务层面的两阶段，Try预留资源，Confirm确认，Cancel取消。性能好，侵入性强
3. **本地消息表**：本地事务+消息表，定时轮询发送，最终一致
4. **MQ事务消息**：RocketMQ半消息机制，最终一致
5. **Saga**：长事务拆分为多个本地事务，失败时补偿（反向操作）
6. **Seata**：阿里开源，支持AT（自动补偿）、TCC、Saga、XA模式

**本项目场景**：订单支付+消息通知，使用RabbitMQ最终一致性即可。

---

### 21. OpenFeign的原理？

**答案**：OpenFeign是声明式HTTP客户端，通过动态代理生成请求。

**原理**：
1. @EnableFeignClients扫描@FeignClient接口
2. 为每个接口生成JDK动态代理
3. 代理对象将方法调用转换为HTTP请求
4. 整合LoadBalancer做负载均衡
5. 整合Sentinel做熔断降级

**Feign vs RestTemplate**：Feign更优雅，声明式，支持参数绑定；RestTemplate更底层，手动拼接URL。

---

### 22. 网关的作用？Spring Cloud Gateway？

**答案**：

**网关作用**：
- 统一入口，路由转发
- 统一认证鉴权
- 限流熔断
- 日志监控
- 灰度发布
- 协议转换

**Spring Cloud Gateway**：基于WebFlux（响应式），非阻塞，性能高。
- Route：路由，由ID、目标URI、断言、过滤器组成
- Predicate：断言，匹配请求（路径、方法、Header等）
- Filter：过滤器，修改请求/响应

---

### 23. 分布式ID生成方案？

**答案**：

1. **UUID**：简单，但无序，字符串，占空间
2. **数据库自增**：简单，但单点瓶颈
3. **号段模式**：一次取一个号段，用完再取（美团Leaf）
4. **Redis INCR**：高性能，但依赖Redis
5. **雪花算法（Snowflake）**：64位，1位符号+41位时间戳+10位机器ID+12位序列号
   - 本项目使用SnowflakeIdGenerator生成订单号
   - 优点：趋势递增、性能高、不依赖第三方
   - 缺点：时钟回拨问题

---

### 24. 接口幂等性如何保证？

**答案**：

1. **唯一索引**：数据库唯一约束，防重复插入
2. **Token机制**：先获取Token，提交时携带Token，Redis SETNX校验
3. **乐观锁**：version字段，更新时带version条件
4. **分布式锁**：Redis锁，业务唯一key
5. **状态机**：订单状态流转，只能从特定状态变更
6. **去重表**：记录已处理的请求ID

**本项目实现**：@Idempotent注解 + Redis SETNX，应用于预约提交、支付等接口。

---

### 25. 如何设计一个高并发的秒杀/预约系统？

**答案**（结合本项目预约场景）：

1. **前端限流**：按钮置灰、验证码
2. **网关限流**：Sentinel限流
3. **接口限流**：@RateLimit + Redisson
4. **缓存预热**：号源信息预热到Redis
5. **库存扣减**：Redis原子操作（DECR），而非数据库
6. **异步下单**：请求入队（RabbitMQ），异步消费写库
7. **分布式锁**：Redisson锁防超卖
8. **结果轮询**：前端轮询订单状态
9. **数据库优化**：分库分表、读写分离
10. **服务降级**：非核心功能关闭

---

> **本部分共25题**，涵盖Spring IOC/AOP/事务、Spring Boot自动配置、Spring Cloud微服务。
