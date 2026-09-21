<div align="center">

# ⚡ ElecCloud

**生产级分布式重试平台 — 5 分钟接入，零代码覆盖 80% 场景**

[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/chaoking320/eleccloud)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

[English](README.md) | [简体中文](README_zh.md)

[快速开始](#-快速开始5-分钟) • [核心特性](#-核心特性) • [架构设计](#-架构设计) • [文档导航](#-文档导航) • [参与贡献](#-参与贡献)

</div>

---

## 🌌 项目名称由来

> **ElecCloud（电子云）**
>
> 在量子物理中，电子不是沿固定轨道绕核旋转，而是以**概率云**的形式弥漫在原子核周围——它们永不停歇地运动，始终守护着核心不受外界干扰。
>
> 这正是本项目的设计初衷：**当核心服务因网络抖动、下游超时、第三方不可用等不可抗力因素调用失败时，不再需要人工介入。ElecCloud 像电子云一样，在业务进程内无感知地持续守护，直到任务最终成功为止。**

在分布式系统中，跨服务调用失败是常见现象：

- 💳 支付回调超时 → 资金状态悬空，人工对账
- 🌐 第三方 API 限流 → 批量请求丢失，手动补录
- 📨 消息发送失败 → 数据不一致，费力排查

**ElecCloud 的目标：让失败任务自动重试到成功，让工程师从重复的人工救火中解放出来。**

---

## 🎯 为什么选择 ElecCloud？

### 与同类方案对比

| 特性 | ElecCloud | 人工重试 | XXL-JOB | Spring Retry |
|------|-----------|----------|---------|--------------|
| **零 Hook 模式** | ✅ 一个注解搞定 | ❌ 需要编码 | ❌ 配置复杂 | ⚠️ 无持久化 |
| **PRE_SUBMIT 模式** | ✅ 进程崩溃也不丢 | ❌ 数据丢失风险 | ❌ 不支持 | ❌ 不支持 |
| **Standalone 模式** | ✅ 零额外基础设施 | ❌ | ❌ | ❌ |
| **可视化管理台** | ✅ 实时监控 | ❌ 仅日志 | ✅ 支持 | ❌ 无 UI |
| **多层兜底** | ✅ MQ + DB + 超时扫描 | ❌ 单点 | ⚠️ 依赖 Redis | ❌ 内存 |
| **接入成本** | 🟢 极低 | 🟡 中等 | 🔴 高 | 🟡 中等 |

---

## ✨ 核心特性

### 🚀 零 Hook 模式 — 一个注解，自动重试

```java
// 之前：需要 50+ 行 Hook 代码
// 现在：只需这一行 ↓
@RetryableTask(sceneType = 1001, idempotentKey = "#orderId")
public void processPayment(String orderId) {
    paymentApi.pay(orderId);
    // 失败自动重试：1分钟 → 5分钟 → 10分钟 → 30分钟 ✅
}
```

### 💎 生产级功能清单

| 特性 | 说明 |
|------|------|
| **🎯 PRE_SUBMIT 模式** | 方法执行前先注册任务，进程崩溃也能自动恢复 |
| **🔄 四种退避策略** | `CUSTOM`（自定义列表）、`FIXED`（固定间隔）、`LINEAR`（线性递增）、`EXPONENTIAL`（指数退避）|
| **📊 实时管理后台** | 任务监控、重试轨迹、手动触发 |
| **🛡️ 多层兜底机制** | Redis/RabbitMQ → MySQL 兜底扫描 → 超时自愈 |
| **🔐 API Key 鉴权** | 内置认证机制，支持白名单配置 |
| **📈 Prometheus 监控** | 完整的 Grafana 可观测性面板 |
| **🌐 Standalone 模式** | 纯 SDK 模式，无需部署 Server |
| **🔔 多渠道告警** | 邮件、钉钉、企业微信 |
| **🏢 业务线隔离** | 多业务线共用同一套平台，队列物理隔离 |

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

ElecCloud v3.0 采用**去中心化 MQ-SDK 驱动**架构 — 调度权完全移交给 SDK，Server 只负责数据存储，不再主动回调业务服务。

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

## 🌟 Roadmap

- [ ] **v1.1**: Spring Boot 3.x 适配
- [ ] **v1.2**: PostgreSQL 支持
- [ ] **v1.3**: 发布到 Maven Central
- [ ] **v2.0**: WebSocket 实时面板更新
- [ ] **v2.1**: 多租户支持
- [ ] **v2.2**: Kafka MQ 引擎

---

## 🤝 参与贡献

欢迎贡献！请阅读 [参与贡献指南](CONTRIBUTING.md) 了解详情。

寻找标有 [`good first issue`](../../issues?q=label%3A%22good+first+issue%22) 的 Issue — 适合新手入门！

---

## 📄 License

本项目采用 [MIT License](LICENSE) 开源协议，欢迎 Star、Fork 与 Contribution。

---

<div align="center">

**如果 ElecCloud 对你有帮助，请给个 ⭐️ Star！**

Made with ❤️ by the ElecCloud community

</div>
