# ⚡ ElecCloud - Distributed Retry Platform

[badges: Java 8+ | Spring Boot 2.7 | License MIT]

A production-ready distributed retry platform for Java applications. Provides automatic retry with configurable backoff strategies, visual management dashboard, and multi-channel alerting.

## ✨ Features
- **Annotation-driven**: Simple `@RetryableTask` annotation to automatically intercept and retry failures.
- **4 Backoff Strategies**: CUSTOM (custom list), FIXED (fixed interval), LINEAR (linear increase), and EXPONENTIAL (exponential backoff).
- **Dual MQ Channels**: Supports both Redis ZSET (lightweight, no extra dependencies) and RabbitMQ (high reliability) for delayed queues.
- **Visual Dashboard**: Intuitive web admin panel to monitor tasks, manage retry scenes, and view retry trajectories.
- **Prometheus Monitoring**: Full integration with Micrometer and Prometheus for metrics.
- **Multi-channel Alerting**: Configurable alerting via Email, DingTalk, and WeChat Work.

## 🚀 Quick Start
### Prerequisites
- Java 8+
- Spring Boot 2.7.x
- Redis 6.x (or RabbitMQ 3.x)
- MySQL 8.0+

### Installation
Add the SDK dependency to your project:
```xml
<dependency>
    <groupId>com.retry.platform</groupId>
    <artifactId>retry-client-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

Configure your `application.yml`:
```yaml
retry:
  client:
    server-url: http://retry-server:8080
    enabled: true
    mq-type: REDIS
    queue-name: payment.retry
```

### Usage Example
Simply add the `@RetryableTask` annotation to any method you want to make resilient:
```java
@Service
public class RefundService {

    @RetryableTask(sceneType = 1, idempotentKey = "#transId")
    public boolean refund(String transId, String orderId, Double amount) {
        return paymentApi.refund(transId, amount);
    }
}
```

## 🏗️ Architecture
```text
+-------------------+       +-------------------+       +-------------------+
|   Business App    |       |   Retry Server    |       |   Admin Dashboard |
| (Retry Client SDK)| <---> | (Data Persistence)| <---> |    (Management)   |
|                   |       |                   |       |                   |
|  +-------------+  |       |  +-------------+  |       |  +-------------+  |
|  | Local Engine|  |       |  | REST API    |  |       |  | Vue 3 / UI  |  |
|  +-------------+  |       |  +-------------+  |       |  +-------------+  |
|         |         |       |         |         |       +-------------------+
+---------+---------+       +---------+---------+
          |                           |
          |       +---------------+   |
          +-----> |  Redis/Rabbit | <-+
                  | (Delay Queue) |
                  +---------------+
```

## 📦 Modules
| Module | Description |
|--------|-------------|
| `retry-server` | Core server responsible for task persistence and REST API. |
| `retry-client-sdk` | Client SDK containing AOP interceptors, local state machine, and MQ integrations. |
| `retry-admin` | Visual administration dashboard (Vue 3 + Element Plus). |
| `retry-example` | Example project demonstrating different integration modes. |

## ⚙️ Configuration
| Property | Description | Default |
|----------|-------------|---------|
| `retry.client.server-url` | The URL of the retry server. | `http://localhost:8080` |
| `retry.client.enabled` | Enable or disable the retry client. | `true` |
| `retry.client.mq-type` | The MQ type to use (`REDIS` or `RABBITMQ`). | `REDIS` |
| `retry.client.queue-name` | Isolation queue name for current business line. | `default.retry` |
| `retry.client.dev-mode` | If true, logs instead of submitting. | `false` |

## 📊 Monitoring
ElecCloud exposes standard Prometheus metrics at `/actuator/prometheus`. You can easily import these metrics into Grafana to monitor retry success rates, active tasks, queue sizes, and more.

## 📝 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
