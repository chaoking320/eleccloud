# 🚀 ElecCloud — Quick Start (5 Minutes)

> **Fastest path**: From zero to your first auto-retrying task

---

## Prerequisites

- ✅ Docker Desktop (Windows) or Docker Engine (Linux/macOS)
- ✅ JDK 17 or 21
- ✅ Maven 3.6+

---

## Step 1: Build the Project (2 min)

```bash
cd <eleccloud project directory>
mvn clean package -DskipTests
```

**Success indicator**: `BUILD SUCCESS`

---

## Step 2: Start Docker Services (3 min)

```bash
docker compose up -d --build
```

**Verify all services are running:**

```bash
docker compose ps
# All 5 services should show "Up"
```

| Service | URL |
|---------|-----|
| Retry Server | http://localhost:8080 |
| Admin Dashboard | http://localhost:8081 |
| Demo App | http://localhost:8082 |

---

## Step 3: Verify the Deployment

```bash
# Check server health
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

Open the Admin Dashboard at **http://localhost:8081**

---

## Step 4: Create Your First Scene

1. In the Admin Dashboard, go to **Scene Config** → **New Scene**
2. Fill in:
   - Scene Type: `1001`
   - Scene Name: `My First Retry Scene`
   - Backoff Strategy: `CUSTOM`
   - Retry Intervals: `1,5,10` (minutes)
   - Max Retry Count: `3`
   - Hook Class: *(leave empty — Zero-Hook mode)*
3. Click **Save**

---

## Step 5: Integrate the SDK into Your Project

### 5.1 Install SDK to local Maven repo

```bash
cd <eleccloud project directory>
mvn install -DskipTests
```

### 5.2 Add dependency to your project

```xml
<dependency>
    <groupId>com.retry.platform</groupId>
    <artifactId>retry-client-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 5.3 Configure application.yml

```yaml
retry:
  client:
    server-url: http://localhost:8080
    enabled: true
    mq-type: REDIS
    queue-name: my-business.retry

spring:
  redis:
    host: localhost
    port: 6379
    database: 2
```

### 5.4 Add one annotation

```java
@Service
public class MyService {

    @RetryableTask(sceneType = 1001, idempotentKey = "#orderId")
    public void processOrder(String orderId) {
        // Your business logic here.
        // If an exception is thrown, it will auto-retry at: 1min, 5min, 10min
        externalApi.call(orderId);
    }
}
```

---

## 🎉 Done!

You can now:
- Call `myService.processOrder("ORDER001")`
- Watch the task appear in the Admin Dashboard
- Simulate a failure and observe automatic retries

---

## 📚 Next Steps

| Guide | Description |
|-------|-------------|
| [Deployment Guide](docs/QUICK_DOCKER_DEPLOY.md) | Full Docker + bare-metal + Prometheus setup |
| [SDK Guide](docs/SDK_GUIDE.md) | All configuration options and advanced usage |
| [Architecture](docs/ARCHITECTURE.md) | System design and state machine |
| [Hook Explained](docs/HOOK_EXPLAINED.md) | Custom retry logic with hooks |

---

## ❓ Troubleshooting

### Server fails to start

```bash
# View server logs
docker logs retry-server

# Common cause: MySQL is still initializing
# Fix: wait 30 seconds then restart
docker compose restart retry-server
```

### SDK cannot connect to server

```bash
# Verify server is reachable
curl http://localhost:8080/actuator/health

# Verify Redis is reachable
redis-cli -h localhost -p 6379 PING
```

### Tasks are not being retried

1. Check that the scene exists in the Admin Dashboard
2. Verify `sceneType` in annotation matches the scene config
3. Confirm the method is actually throwing an exception
4. Check SDK logs to confirm the message was sent to the queue

---

## 🛠️ Useful Commands

```bash
# Stop all services
docker compose down

# Restart services
docker compose restart

# Follow logs
docker compose logs -f retry-server
docker compose logs -f retry-admin

# Access the database
docker exec -it eleccloud-mysql mysql -uroot -ppassword retry_platform
```

---

Have fun! 🎉 If you run into issues, check [Deployment Guide](docs/QUICK_DOCKER_DEPLOY.md) or open an [Issue](../../issues).
