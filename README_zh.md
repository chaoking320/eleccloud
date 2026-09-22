<div align="center">

# ⚡ ElecCloud

**面向单向网络约束与进程崩溃防护的轻量级分布式重试框架**

*专注于解决企业内网隔离环境下的重试调度痛点：零反向回调依赖、防进程异常退出丢任务。*

[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/chaoking320/eleccloud)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

[English](README.md) | [简体中文](README_zh.md)

[快速开始](#-快速开始5-分钟) • [设计定位](#-设计定位与适用场景) • [核心特性](#-核心特性) • [架构设计](#-架构设计) • [文档导航](#-文档导航) • [参与贡献](#-参与贡献)

</div>

---

## 🌌 项目名称由来

> **ElecCloud（电子云）**
>
> 在量子物理中，电子不是沿固定轨道绕核旋转，而是以**概率云**的形式弥漫在原子核周围——它们永不停歇地运动，始终守护着核心不受外界干扰。
>
> 这正是本项目的设计初衷：**当核心服务因网络抖动、下游超时等短暂不可抗力调用失败时，不再需要人工紧急救火。ElecCloud 尝试在业务客户端内默默守护，按退避策略调度重试，直至最终完成。**

---

## ⚙️ 运行环境与技术栈选型考量

ElecCloud 采用**企业生产优先（Enterprise-First）的兼顾型兼容策略**：

| 组件 | 推荐/支持版本 | 选型与兼容性考量 |
|:---|:---|:---|
| **Java 运行时** | **JDK 17 / 21 (LTS)** | 基于现代 JVM 开发与测试，全面享受新一代垃圾收集器（如 ZGC）及低延迟优势。 |
| **基础框架** | **Spring Boot 2.7.x** | 优先兼顾绝大多数企业生产线上广泛运行的微服务存量集群，**避免强制迁移至 `jakarta.*` 命名空间对宿主工程造成破坏性升级成本**。 |
| **持久化存储** | **MySQL 8.0+** | 依赖行级 CAS 抢占与 `(scene_type, idempotent_key)` 唯一索引防并发倾轧。 |
| **延迟队列引擎** | **Redis 6+ (ZSET) / RabbitMQ 3.8+** | 双引擎灵活切换：轻量演示与简单微服务首选 Redis；高可靠企业级重试首选 RabbitMQ。 |

---

## 🎯 设计定位与适用场景

ElecCloud **并不追求做大而全的分布式任务调度器**，而是专注于解决分布式重试领域几个具体的工程痛点：

### 技术权衡与场景定位

- **对比 Spring Retry**：Spring Retry 是优秀的单机内存重试工具。但如果业务进程崩溃重启，或者重试周期需要拉长到数小时（指数退避），内存状态就会全部丢失。ElecCloud 补充了持久化与跨进程恢复能力。
- **对比 XXL-JOB / ElasticJob**：分布式定时调度器的强项在于批量定时批处理作业（Cron Job）。如果针对每一次方法级别的偶发失败都去配置一个独立的 JobHandler，运维和接入成本过高。ElecCloud 专注于基于注解的方法级细粒度重试。
- **对比中心化 HTTP 回调型重试平台**：传统重试平台通常由 Server 主动发起 HTTP 请求回调业务服务。但在容器化、K8s 动态 Pod、跨 VPC 或内网安全隔离的场景下，Server 往往无法反向访问业务容器。ElecCloud 将调度权移交至客户端 SDK，服务端仅作为被动存储节点，**只需要单向网络连通**。

| 场景维度 | Spring Retry | 传统分布式定时调度 | 中心化 HTTP 重试平台 | ElecCloud |
| :--- | :--- | :--- | :--- | :--- |
| **主要定位** | 进程内即时重试 | 定时批量作业调度 | 集中式任务管理与回调 | 异步方法级轻量重试 |
| **防崩溃安全** | ❌ 进程退出即丢 | ✅ 具备 | ✅ 具备 | ✅ `PRE_SUBMIT` 预提交保障 |
| **网络要求** | 本地 JVM | 需双向通信 / Agent | **必须双向互通 (Server→业务)** | **仅需单向网络 (SDK→Server)** |
| **回调耦合** | 无 | 需维护独立 Handler | Server 需配置业务 IP 与端口 | **无 (SDK 本地拉取并就地执行)** |
| **接入成本** | 极低 | 中 ~ 高 | 中等 | 低 (一个注解) |

---

## ✨ 核心特性

### 1. 去中心化执行（无反向网络依赖）
针对云原生网络隔离环境，彻底去除 Server 反向回调业务服务的设计。SDK 通过延时队列自行消费并就地反射执行，极大简化跨网段、容器漂移等复杂网络环境下的部署门槛。

### 2. 常见场景零配置（零 Hook 模式）
针对不需要向第三方复杂反查状态的场景（例如同步库存、清理缓存、发送消息等，方法无异常即成功），无需编写自定义 Hook：

```java
@RetryableTask(sceneType = 1001, idempotentKey = "#orderId")
public void processPayment(String orderId) {
    paymentApi.pay(orderId);
    // 抛出异常后自动进入重试平台，按场景配置退避重试
}
```

### 3. 预提交防护（PRE_SUBMIT 模式）
在核心业务方法执行前先行写入持久化 `INIT` 状态，即便业务服务器在执行过程中突发断电或 OOM 退出，未竟任务依然能在重启后被安全恢复。

### 4. 稳健的工程细节
- **四种退避策略**：`CUSTOM`（自定义）、`FIXED`（固定）、`LINEAR`（线性）、`EXPONENTIAL`（指数退避）
- **多层兜底保障**：延时队列 → 数据库扫描兜底 → 超时自愈机制
- **并发与防重**：CAS 状态原子转换，基于 `(scene_type, idempotent_key)` 唯一索引强防重
- **Standalone 模式**：支持纯 SDK + 本地数据库运行，完全脱离外部 Server 依赖

---

## 🚀 快速开始（5 分钟）

### 方式一：Docker Compose 一键启动（推荐）

```bash
# 克隆项目
git clone https://github.com/chaoking320/eleccloud.git
cd eleccloud

# 一键启动所有服务
docker compose -f docker-compose.simple.yml up -d
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
docker run -d -p 6379:6379 redis:7-alpine

# 3. 编译
mvn clean install -DskipTests

# 4. 依次启动各服务
cd retry-server  && mvn spring-boot:run
cd retry-admin   && mvn spring-boot:run
cd retry-example && mvn spring-boot:run
```

---

## 🔌 业务接入（3 步）

> 💡 **本地体验**：在正式发布至 Maven Central（计划于 v1.1 里程碑）前，请在项目根目录执行一次 `mvn clean install -DskipTests` 将 `retry-client-sdk` 安装至本地 `.m2` 仓库，随后即可在你的项目中直接添加依赖。

### Step 1：添加 SDK 依赖

```xml
<dependency>
    <groupId>com.retry.platform</groupId>
    <artifactId>retry-client-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2：配置 SDK（Remote 模式）

```yaml
retry:
  client:
    server-url: http://retry-server:8080  # 重试平台地址
    enabled: true
    mq-type: REDIS                         # REDIS 或 RABBITMQ
    queue-name: payment.retry              # 业务线专属队列名（实现隔离）
    dev-mode: false                        # true 时仅打日志，不实际提交
```

### Step 2（可选）：Standalone 模式（无需 Server）

```yaml
retry:
  client:
    mode: standalone                        # 纯本地模式，无需 retry-server
    scenes:
      - scene-type: 1001
        max-retry-count: 4
        retry-intervals: "1,5,10,30"       # 单位：分钟
```

### Step 3：注解一行搞定

```java
@Service
public class RefundService {

    // 方法失败后自动进入重试平台，直到成功
    @RetryableTask(sceneType = 1, idempotentKey = "#transId")
    public boolean refund(String transId, String orderId, Double amount) {
        return paymentApi.refund(transId, amount); // 失败会自动重试
    }
}
```

> 详细接入说明请参阅 [SDK 接入指南](docs/SDK_GUIDE.md) | [零 Hook 快速入门](docs/QUICK_START_ZERO_HOOK.md)

---

## 🏗️ 架构设计

ElecCloud 采用**去中心化 MQ-SDK 驱动**架构 — 调度权完全移交给 SDK，Server 只负责数据存储，不再主动回调业务服务。

```
┌─────────────────────────────────────────────────────────┐
│                 业务系统（内含 SDK）                      │
│                                                         │
│  方法失败                                                │
│    ↓ AOP 拦截 (@RetryableTask)                          │
│  RetryClient.submit() ──► Retry Server（仅做持久化）     │
│    ↓                                                    │
│  MQ Producer ──► [Redis ZSET / RabbitMQ 延时队列]       │
│    ↓ 延时到期                                           │
│  MQ Consumer ──► LocalRetryExecutor（本地状态机）        │
│    ↓                                                    │
│  Hook.checkStatus() ──► Hook.doQuery() ──► doCallback() │
│    ↓                                                    │
│  SUCCESS / 重新调度 / 超次数 → FAILED                   │
└─────────────────────────────────────────────────────────┘
```

### 核心组件

| 组件 | 职责 | 技术栈 |
|------|------|--------|
| **retry-client-sdk** | AOP 拦截、本地状态机、Hook 生命周期 | Spring AOP, Reflection, MyBatis |
| **retry-server** | 任务持久化、REST API（仅数据存取） | Spring Boot, MyBatis, Redis |
| **retry-admin** | 可视化管理、监控、手动触发 | Vue 3, Element Plus |
| **retry-example** | 接入示例、三种模式完整演示 | Spring Boot |

### 关键设计模式

- **CAS 原子抢占**：`UPDATE ... WHERE status IN ('INIT','WAIT')` — 无锁分布式互斥
- **三阶段 Hook 状态机**：`checkStatus` → `doQuery` → `doCallback`
- **胖消息设计**：MQ 消息携带完整执行上下文，减少 HTTP 往返次数
- **多层兜底**：MQ 故障 → DB 兜底扫描 → EXECUTING 超时自愈

---

## 📚 文档导航

| 文档 | 内容 |
|------|------|
| [架构设计](docs/ARCHITECTURE.md) | 整体架构图、状态机流转、数据库 ER 图 |
| [SDK 接入指南](docs/SDK_GUIDE.md) | 5 分钟上手、三种接入模式、完整配置参数说明 |
| [测试实操指南](docs/TEST_GUIDE.md) | 5 分钟接口体验完整重试流转（退款、短信、积分） |
| [Hook 机制详解](docs/HOOK_EXPLAINED.md) | 自定义 Hook 生命周期开发指南 |
| [部署运维手册](docs/QUICK_DOCKER_DEPLOY.md) | Docker Compose + 手动 Jar 部署 + Prometheus/Grafana 监控 |
| [安全配置指南](docs/SECURITY.md) | API Key 鉴权、密钥管理、安全最佳实践 |
| [告警配置指南](docs/ALERT_GUIDE.md) | 邮件/钉钉/企业微信告警、阈值配置 |
| [参与贡献](CONTRIBUTING.md) | 贡献指南 |

---

## 🎯 Demo 场景

`retry-example` 提供了电商域的三种完整演示：

| 接口 | 模式 | 说明 |
|------|------|------|
| `GET /business/refund?amount=100` | 注解模式 | 退款失败 → AOP 自动提交重试 |
| `GET /business/settlement?amount=5000` | API 模式 | 结算超时 → 手动提交，线性策略 |
| `GET /business/inventory?delta=10` | 预提交模式 | 库存同步 → 进程崩溃也不丢失 |

---

## 🧩 技术栈

| 技术 | 用途 |
|------|------|
| Java 17 + Spring Boot 2.7.x | 基础框架 |
| Spring AOP | 注解拦截，零代码入侵 |
| Redis ZSET | 延迟消息队列（无额外依赖首选） |
| RabbitMQ + 延时插件 | 延迟消息队列（可靠性首选） |
| MySQL 8.0 | 任务持久化存储 |
| MyBatis | ORM |
| ShedLock | 分布式调度锁 |
| Vue 3 + Vite | 管理后台前端 |
| Micrometer + Prometheus | 监控指标 |

---

## 🤝 参与贡献

欢迎贡献！请阅读 [参与贡献指南](CONTRIBUTING.md) 了解详情。

寻找标有 [`good first issue`](../../issues?q=label%3A%22good+first+issue%22) 的 Issue — 适合新手入门！

---

## 📄 License

本项目采用 [Apache License 2.0](LICENSE) 开源协议，欢迎 Star、Fork 与 Contribution。

---

<div align="center">

**如果 ElecCloud 在你的项目中带来了帮助，欢迎点亮右上角的 ⭐️ Star 支持！**

Crafted with care by the ElecCloud maintainers & contributors

</div>
