<div align="center">

# ⚡ ElecCloud

**Production-Ready Distributed Retry Platform — 5 Minutes to Integrate**

[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/chaoking320/eleccloud)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

[English](README.md) | [简体中文](README_zh.md)

[Quick Start](#-quick-start) • [Features](#-core-features) • [Architecture](#-architecture) • [Documentation](#-documentation) • [Contributing](#-contributing)

</div>

---

## 🌌 The Origin Story

> **ElecCloud — The Electron Cloud**
>
> In quantum physics, electrons don't orbit the nucleus in fixed paths. Instead, they form a **probability cloud** — constantly in motion, tirelessly guarding the core from external interference.
>
> This is the philosophy behind ElecCloud: **When your critical services fail due to network jitters, downstream timeouts, or third-party unavailability, you shouldn't need manual intervention. Like an electron cloud, ElecCloud silently and persistently guards your business processes until tasks eventually succeed.**

In distributed systems, cross-service failures are inevitable:

- 💳 Payment callback timeout → Fund status unknown, manual reconciliation needed
- 🌐 Third-party API rate limit → Batch requests lost, manual data entry required
- 📨 Message delivery failure → Data inconsistency, tedious troubleshooting

**ElecCloud's mission: Let failed tasks automatically retry until success, freeing engineers from repetitive firefighting.**

---

## 🎯 Why ElecCloud?

### vs. Traditional Solutions

| Feature | ElecCloud | Manual Retry | XXL-JOB | Spring Retry |
|---------|-----------|--------------|---------|--------------|
| **Zero-Hook Mode** | ✅ One annotation | ❌ Need code | ❌ Complex setup | ⚠️ No persistence |
| **PRE_SUBMIT Mode** | ✅ Process crash safe | ❌ Data loss risk | ❌ Not supported | ❌ Not supported |
| **Standalone Mode** | ✅ Zero infrastructure | ❌ | ❌ | ❌ |
| **Visual Dashboard** | ✅ Real-time monitoring | ❌ Log only | ✅ Supported | ❌ No UI |
| **Multi-layer Fallback** | ✅ MQ + DB + Scanner | ❌ Single point | ⚠️ Redis dependent | ❌ In-memory |
| **Learning Curve** | 🟢 Very Low | 🟡 Medium | 🔴 High | 🟡 Medium |

---

## ✨ Core Features

### 🚀 Zero-Hook Mode — One Annotation, Auto Retry

```java
// Before: 50+ lines of Hook code
// After: Just this ↓
@RetryableTask(sceneType = 1001, idempotentKey = "#orderId")
public void processPayment(String orderId) {
    paymentApi.pay(orderId);
    // Auto retry on failure: 1min → 5min → 10min → 30min ✅
}
```

### 💎 Production-Ready Features

| Feature | Description |
|---------|-------------|
| **🎯 PRE_SUBMIT Mode** | Register task before execution — process crash safe |
| **🔄 4 Backoff Strategies** | `CUSTOM`, `FIXED`, `LINEAR`, `EXPONENTIAL` |
| **📊 Real-time Dashboard** | Task monitoring, timeline view, manual trigger |
| **🛡️ Multi-layer Fallback** | Redis/RabbitMQ → MySQL fallback → Timeout recovery scanner |
| **🔐 API Key Security** | Built-in authentication with whitelist support |
| **📈 Prometheus Ready** | Full observability with Grafana dashboard |
| **🌐 Standalone Mode** | SDK-only mode, no server required |
| **🔔 Multi-channel Alerts** | Email, DingTalk, WeChat Work |

---

## 🚀 Quick Start

### Option 1: Docker Compose (Recommended)

```bash
git clone https://github.com/chaoking320/eleccloud.git
cd eleccloud
docker compose -f docker-compose.simple.yml up -d
```

| Service | URL |
|---------|-----|
| Retry Server | http://localhost:8080 |
| Admin Dashboard | http://localhost:8081 |
| Demo App | http://localhost:8082 |

### Option 2: SDK Dependency (Remote Mode)

**Step 1: Add dependency**

```xml
<dependency>
    <groupId>com.retry.platform</groupId>
    <artifactId>retry-client-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Step 2: Configure**

```yaml
retry:
  client:
    server-url: http://localhost:8080  # retry-server address
    enabled: true
    mq-type: REDIS                      # REDIS or RABBITMQ
    queue-name: my-service.retry        # per-service queue for isolation
```

**Step 3: Add annotation**

```java
@Service
public class OrderService {

    @RetryableTask(sceneType = 1001, idempotentKey = "#orderId")
    public void syncOrder(String orderId) {
        warehouseApi.sync(orderId);
        // Auto retry: 1min → 5min → 10min → 30min
    }
}
```

### Option 3: Standalone Mode (Zero Infrastructure)

No server, no Redis, no RabbitMQ needed — tasks persist in your own database.

```yaml
retry:
  client:
    mode: standalone
    scenes:
      - scene-type: 1001
        max-retry-count: 4
        retry-intervals: "1,5,10,30"
```

> 📖 **Full guides**: [QUICKSTART.md](QUICKSTART.md) | [Zero-Hook Guide](docs/QUICK_START_ZERO_HOOK.md) | [SDK Guide](docs/SDK_GUIDE.md)

---

## 🏗️ Architecture

ElecCloud v3.0 uses a **decentralized MQ-SDK-driven** design — scheduling authority is fully delegated to the SDK, eliminating the need for the server to actively call back into business services.

```
┌─────────────────────────────────────────────────────────┐
│              Business Application (with SDK)            │
│                                                         │
│  Method fails                                           │
│    ↓ AOP intercept (@RetryableTask)                    │
│  RetryClient.submit() ──► Retry Server (persist only)  │
│    ↓                                                    │
│  MQ Producer ──► [Redis ZSET / RabbitMQ Delay Queue]   │
│    ↓ delay expires                                      │
│  MQ Consumer ──► LocalRetryExecutor (state machine)     │
│    ↓                                                    │
│  Hook.checkStatus() ──► Hook.doQuery() ──► doCallback() │
│    ↓                                                    │
│  SUCCESS / reschedule / max-retries → FAILED           │
└─────────────────────────────────────────────────────────┘
```

### Core Components

| Component | Responsibility | Technology |
|-----------|---------------|------------|
| **retry-client-sdk** | AOP interception, local state machine, Hook lifecycle | Spring AOP, Reflection, MyBatis |
| **retry-server** | Task persistence, REST API (data store only) | Spring Boot, MyBatis, Redis |
| **retry-admin** | Visual management, monitoring, manual trigger | Vue 3, Element Plus |
| **retry-example** | Integration examples, Hook templates | Spring Boot |

### Key Design Patterns

- **CAS Atomic Claim**: `UPDATE ... WHERE status IN ('INIT','WAIT')` — distributed mutex without locks
- **3-Phase Hook State Machine**: `checkStatus` → `doQuery` → `doCallback`
- **Fat Message**: Full execution context carried in MQ message — reduces HTTP round trips
- **Multi-layer Fallback**: MQ failure → DB scan fallback → ExecutingTimeout recovery

---

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [Architecture](docs/ARCHITECTURE.md) | System design, state machine, ER diagram |
| [SDK Guide](docs/SDK_GUIDE.md) | Complete SDK reference & all configuration options |
| [Hook Explained](docs/HOOK_EXPLAINED.md) | Custom Hook lifecycle guide (checkStatus → doQuery → doCallback) |
| [Deployment Guide](docs/QUICK_DOCKER_DEPLOY.md) | Docker Compose, bare-metal JAR deployment, Prometheus + Grafana |
| [Security](docs/SECURITY.md) | API Key auth, best practices |
| [Alert Guide](docs/ALERT_GUIDE.md) | Email, DingTalk, WeChat Work alerting setup |
| [Contributing](CONTRIBUTING.md) | How to contribute |

---

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

```bash
# Clone the repository
git clone https://github.com/chaoking320/eleccloud.git

# Build
mvn clean install -DskipTests

# Run tests
mvn test

# Start local environment
docker compose -f docker-compose.simple.yml up -d
```

Look for issues tagged [`good first issue`](../../issues?q=label%3A%22good+first+issue%22) — perfect for newcomers!

---

## 🌟 Roadmap

- [ ] **v1.1**: Spring Boot 3.x support
- [ ] **v1.2**: PostgreSQL support
- [ ] **v1.3**: Maven Central publishing
- [ ] **v2.0**: WebSocket real-time dashboard updates
- [ ] **v2.1**: Multi-tenant support
- [ ] **v2.2**: Kafka MQ engine

---

## 💬 Community

- **GitHub Issues**: [Bug reports and feature requests](../../issues)
- **GitHub Discussions**: [Q&A and discussions](../../discussions)

---

## 📄 License

This project is licensed under the Apache License 2.0 — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**If ElecCloud helps you, please give it a ⭐️ Star!**

Made with ❤️ by the ElecCloud community

</div>
