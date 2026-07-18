# ElecCloud — 分布式重试平台

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-blue.svg)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-6.x-red.svg)](https://redis.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-orange.svg)](https://www.mysql.com/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.x-ff6600.svg)](https://www.rabbitmq.com/)

---

## 🌌 项目名称由来

> **ElecCloud（电子云）**
>
> 在量子物理中，电子不是沿固定轨道绕核旋转，而是以**概率云**的形式弥漫在原子核周围——它们永不停歇地运动，始终守护着核心不受外界干扰。
>
> 这正是本项目的设计初衷：**当核心服务因网络抖动、下游超时、第三方不可用等不可抗力因素调用失败时，不再需要人工介入，ElecCloud 像电子云一样，在业务进程内无感知地持续守护，直到任务最终成功为止。**

---

## 💡 项目背景

在分布式系统中，跨服务调用失败是常见现象：

- 支付回调超时 → 资金状态悬空，人工对账
- 第三方 API 限流 → 批量请求丢失，手动补录
- 消息发送失败 → 数据不一致，费力排查

这些"不可抗力"失败往往需要人工介入，带来极大的**人力成本和响应延迟**。

**ElecCloud 的目标是：让失败任务自动重试到成功，让工程师从重复的人工救火中解放出来。**

---

## 🚀 核心特性

- ⚡ **去中心化 MQ-SDK 驱动**：调度权完全移交给 SDK。SDK 内嵌 `LocalRetryExecutor`，在业务进程内直接执行状态机与重试逻辑。Server 仅负责数据持久化存储，不参与调度，实现零网络依赖（Server 无需主动连接业务服务）。
- 🎯 **双 MQ 延时引擎支持**：内置 Redis ZSET 延迟队列（轻量、零额外依赖）与 RabbitMQ 延迟队列（高可靠）实现，通过配置项无缝切换。
- 🏢 **业务线主题隔离**：支持多业务线共用一套重试中心。各业务线可配置专属的 `queue-name`，在 Redis Key 或 RabbitMQ Queue 层面实现物理隔离，互不干扰。
- 🛡️ **四种退避策略**：提供 `CUSTOM`（自定义列表）、`FIXED`（固定间隔）、`LINEAR`（线性递增）、`EXPONENTIAL`（指数退避）四种策略，灵活适应各种业务场景。
- 🔒 **两阶段幂等保障**：`checkStatus` 检查本地状态，`doQuery` 校验第三方状态，彻底防止重复消费。
- 📋 **预提交安全模式 (PRE_SUBMIT)**：方法执行前先注册 INIT 状态任务，即使进程在执行中途崩溃，也能通过本地延迟队列恢复并重试，保障最终一致性。
- 📊 **可视化管理后台**：提供直观的 Web 管理台，实时查看任务状态、管理场景配置、追踪重试轨迹。

---

## 📂 项目结构

```
eleccloud/
├── retry-server/           # 任务存储中心：提供 REST API 供 SDK 存取任务数据
├── retry-client-sdk/       # 核心 SDK：AOP 切面 + MQ 延时队列 + 本地状态机
│   └── mq/
│       ├── redis/          # Redis ZSET 延时队列实现
│       └── rabbit/         # RabbitMQ 延迟队列实现
├── retry-admin/            # 管理后台：场景配置、任务监控、手动触发
├── retry-example/          # Demo 应用：三种接入模式完整演示
├── db/
│   └── init.sql            # MySQL 建表脚本及初始场景数据
└── docs/
    ├── ARCHITECTURE.md     # 架构设计与状态机文档
    ├── SDK_GUIDE.md        # 5 分钟接入指南
    └── DEPLOYMENT.md       # 部署与运维手册
```

---

## 📖 文档导航

| 文档 | 内容 |
|------|------|
| [架构设计](docs/ARCHITECTURE.md) | 整体架构图、状态机流转、业务线隔离原理、数据库 ER 图 |
| [SDK 接入指南](docs/SDK_GUIDE.md) | 5 分钟上手、三种接入模式、配置详解、RetryHook 开发规范 |
| [部署运维手册](docs/DEPLOYMENT.md) | Docker Compose 一键部署、手动部署、Prometheus 监控 |

---

## ⚡ 快速开始（5 分钟）

### 方式一：Docker Compose 一键启动（推荐）

```bash
# 克隆项目
git clone https://github.com/your-org/eleccloud.git
cd eleccloud

# 一键构建并启动所有服务
docker-compose up -d --build
```

启动成功后访问：

| 服务 | 地址 |
|------|------|
| 重试服务端 | http://localhost:8080 |
| 管理后台 | http://localhost:8081 |
| Demo 应用 | http://localhost:8082 |

### 方式二：本地开发调试

```bash
# 1. 初始化数据库
mysql -u root -p < db/init.sql

# 2. 启动 Redis（本地或 Docker）
docker run -d -p 6379:6379 redis:6-alpine

# 3. 编译
mvn clean install -Dmaven.test.skip=true

# 4. 依次启动各服务
cd retry-server  && mvn spring-boot:run
cd retry-admin   && mvn spring-boot:run
cd retry-example && mvn spring-boot:run
```

---

## 🔌 业务接入（3 步）

### Step 1：添加 SDK 依赖

```xml
<dependency>
    <groupId>com.retry.platform</groupId>
    <artifactId>retry-client-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Step 2：配置 SDK

```yaml
retry:
  client:
    server-url: http://retry-server:8080   # 重试平台地址
    enabled: true
    mq-type: REDIS                          # REDIS 或 RABBITMQ
    queue-name: payment.retry               # 业务线专属队列名（实现隔离）
    dev-mode: false                         # true 时仅打日志，不实际提交
```

### Step 3：注解一行搞定

```java
@Service
public class RefundService {

    // 加一个注解，方法失败后自动进入重试平台，直到成功
    // 前提条件：下游业务系统必须支持基于交易流水号 transId 的幂等校验
    @RetryableTask(sceneType = 1, idempotentKey = "#transId")
    public boolean refund(String transId, String orderId, Double amount) {
        return paymentApi.refund(transId, amount); // 失败会自动重试
    }
}
```

> 详细接入说明请参阅 [SDK 接入指南](docs/SDK_GUIDE.md)

---

## 🏢 业务线隔离

ElecCloud 支持多业务线共用同一套重试平台，彼此完全隔离：

```yaml
# 支付线
retry.client.queue-name: payment.retry

# 物流线
retry.client.queue-name: logistics.retry

# 营销线
retry.client.queue-name: marketing.retry
```

- **Redis 模式**：各业务线使用独立 ZSET Key，轮询互不干扰。
- **RabbitMQ 模式**：各业务线绑定独立 Queue，路由完全隔离。

---

## 🎯 Demo 场景

`retry-example` 提供了电商域的三种完整演示：

| 接口 | 模式 | 说明 |
|------|------|------|
| `GET /api/demo/mode1/refund?amount=100` | 注解模式 | 退款失败 → AOP 自动提交重试 |
| `GET /api/demo/mode2/settlement?amount=5000` | API 模式 | 结算超时 → 手动提交，线性策略 |
| `GET /api/demo/mode3/inventory?delta=10` | 预提交模式 | 库存同步 → 进程崩溃也不丢失 |
| `GET /api/demo/status` | — | 实时查看三个场景的当前状态 |

---

## 🧩 技术栈

| 技术 | 用途 |
|------|------|
| Spring Boot 2.7.x | 基础框架 |
| Spring AOP | 注解拦截，零代码入侵 |
| Redis ZSET | 延迟消息队列（无额外依赖首选） |
| RabbitMQ | 延迟消息队列（可靠性首选） |
| MySQL 8.0 | 任务持久化存储 |
| Redisson | 分布式锁 |
| MyBatis | ORM |
| Vue 3 + Vite | 管理后台前端 |
| Micrometer + Prometheus | 监控指标 |

---

## 📄 License

本项目采用 [MIT License](LICENSE) 开源协议，欢迎 Star、Fork 与 Contribution。
