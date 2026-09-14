# Example 示例项目详解 - 完整流程图解

> **目标**：彻底搞懂 retry-example 项目中各个角色的关系和重试流程

---

## 🎭 角色分工

### 核心理解

retry-example 项目包含**两个控制器**，扮演不同角色：

| 控制器 | 角色 | 类比 | 端口 |
|--------|------|------|------|
| **BusinessController** | 你的业务系统（发起方） | 你的电商平台 | 8082 |
| **MockExternalApiController** | 第三方系统（被调用方） | 支付宝、OTA平台、仓库系统 | 8082 |

---

## 📊 完整调用链路图

### 场景 1：支付退款（注解模式）

```
┌────────────────────────────────────────────────────────────────────┐
│                        retry-example (8082)                        │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │          BusinessController（你的业务系统）                    │ │
│  │                                                                │ │
│  │  POST /business/trigger/refund  ← 浏览器/Postman 触发          │ │
│  │         ↓                                                      │ │
│  │  refundBusinessService.refund(transId, orderId, amount)        │ │
│  └──────────────────────────┬─────────────────────────────────────┘ │
│                             │                                       │
│  ┌──────────────────────────▼─────────────────────────────────────┐ │
│  │        RefundBusinessService（业务服务）                        │ │
│  │                                                                 │ │
│  │  @RetryableTask(sceneType = 10, idempotentKey = "#transId")   │ │
│  │  public void refund(String transId, ...) {                     │ │
│  │      // 调用支付宝退款接口                                       │ │
│  │      restTemplate.postForObject(                               │ │
│  │          "http://localhost:8082/mock-api/payment/refund",      │ │
│  │          request                                               │ │
│  │      );                                                        │ │
│  │  }                                                             │ │
│  └──────────────────────────┬──────────────────────────────────────┘ │
│                             │ HTTP POST                             │
│  ┌──────────────────────────▼──────────────────────────────────────┐ │
│  │     MockExternalApiController（模拟支付宝）                     │ │
│  │                                                                 │ │
│  │  POST /mock-api/payment/refund                                 │ │
│  │  public Map<String, Object> refund(...) {                      │ │
│  │      int callCount = ...;                                      │ │
│  │                                                                 │ │
│  │      if (callCount <= 2) {                                     │ │
│  │          // 前 2 次调用：模拟超时                               │ │
│  │          throw new RuntimeException("支付宝网关超时");          │ │
│  │      }                                                          │ │
│  │                                                                 │ │
│  │      // 第 3 次及以后：返回成功                                 │ │
│  │      return {"code": "SUCCESS", ...};                          │ │
│  │  }                                                             │ │
│  └─────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
                             │
                             │ 如果失败（前2次）
                             ↓
            ┌────────────────────────────────────┐
            │  ElecCloud SDK (AOP 拦截)          │
            │                                    │
            │  1. 捕获到 RuntimeException        │
            │  2. 提交任务到 retry-server        │
            │  3. 投递延时消息到 Redis MQ       │
            └────────────────┬───────────────────┘
                             │
                             ↓
            ┌────────────────────────────────────┐
            │  retry-server (8080)               │
            │  - 保存任务到 MySQL                │
            │  - 状态：INIT                      │
            └────────────────┬───────────────────┘
                             │
                             │ 5 秒后（第1次重试间隔）
                             ↓
            ┌────────────────────────────────────┐
            │  Redis MQ 延时消息到期             │
            │  → SDK 的 LocalRetryExecutor 消费  │
            └────────────────┬───────────────────┘
                             │
                             ↓
            ┌────────────────────────────────────┐
            │  LocalRetryExecutor（本地状态机）  │
            │                                    │
            │  1. 从 server 获取任务详情         │
            │  2. CAS 抢占执行权（状态→EXECUTING）│
            │  3. 调用 Hook.checkStatus()        │
            │  4. 反射调用业务方法                │
            │     refund(transId, ...)           │
            └────────────────┬───────────────────┘
                             │
                             │ 再次调用
                             ↓
            ┌────────────────────────────────────┐
            │  MockExternalApiController         │
            │  callCount = 3（第3次）            │
            │  → 返回成功 ✅                      │
            └────────────────┬───────────────────┘
                             │
                             ↓
            ┌────────────────────────────────────┐
            │  任务状态 → SUCCESS                │
            │  写入 MySQL                        │
            │  管理后台可见 ✓                    │
            └────────────────────────────────────┘
```

---

## 🔍 关键问题解答

### Q1: MockExternalApiController 是什么？

**答案**：它是**模拟的第三方系统**（被调用方）

**类比**：
- 在真实场景中，你会调用真实的支付宝 API：`https://openapi.alipay.com/gateway.do`
- 在 Demo 中，为了方便演示，用 `MockExternalApiController` 模拟支付宝的行为

**对应关系**：

| Demo 中 | 真实场景 |
|---------|----------|
| `POST /mock-api/payment/refund` | `POST https://openapi.alipay.com/gateway.do?method=alipay.trade.refund` |
| `POST /mock-api/hotel/settlement` | `POST https://ota-api.meituan.com/v1/settlement` |
| `POST /mock-api/warehouse/inventory/sync` | `POST https://wms.yourcompany.com/api/inventory/sync` |

---

### Q2: BusinessController 是什么？

**答案**：它是**你的业务系统**（发起方）

**类比**：
- 你的电商平台要给用户退款
- 你的酒店管理系统要向 OTA 平台结算
- 你的库存中心要同步库存到仓库

**对应关系**：

| Demo 中 | 真实场景 |
|---------|----------|
| `POST /business/trigger/refund` | 你的订单系统触发退款流程 |
| `refundBusinessService.refund()` | 你的业务服务层方法 |
| `restTemplate.post()` 调用 `/mock-api/payment/refund` | `restTemplate.post()` 调用真实支付宝 API |

---

### Q3: Hook 在哪里？

**答案**：Hook 在 `retry-example/src/main/java/com/retry/platform/example/hook/` 目录

让我查看一个 Hook 示例：

<function_calls>
<invoke name="read_file">
<parameter name="path">d:\workspace\mine\eleccloud\retry-example\src\main\java\com\retry\platform\example\hook\DemoRefundHook.java


**Hook 示例**：

```java
@Component("com.retry.platform.example.hook.DemoRefundHook")
public class DemoRefundHook implements RetryHook {
    
    // 方法1: 检查本地状态（每次重试前调用）
    @Override
    public RetryStatus checkStatus(RetryContext ctx) {
        String transId = (String) ctx.getParams().get("transId");
        String status = localDb.get(transId);  // 查本地数据库
        return RetryStatus.valueOf(status);    // INIT/WAIT/SUCCESS
    }
    
    // 方法2: 查询支付宝状态
    @Override
    public QueryResult doQuery(RetryContext ctx) {
        String transId = (String) ctx.getParams().get("transId");
        
        // 调用 Mock 支付宝查询接口
        Map result = restTemplate.getForObject(
            "http://localhost:8082/mock-api/payment/refund/status?transId=" + transId,
            Map.class
        );
        
        if ("SUCCESS".equals(result.get("status"))) {
            return QueryResult.success("退款成功");
        } else {
            return QueryResult.failure("还在处理中");
        }
    }
    
    // 方法3: 成功后更新本地状态
    @Override
    public void doCallback(RetryContext ctx, QueryResult result) {
        String transId = (String) ctx.getParams().get("transId");
        localDb.put(transId, "SUCCESS");  // 更新本地数据库
    }
}
```

---

## 🔄 完整时序图

### 第 1 次调用（失败）

```
时间: 10:00:00

[浏览器]
   │
   │ POST /business/trigger/refund
   ↓
[BusinessController]
   │
   │ 调用 refundBusinessService.refund(transId="RFD_001")
   ↓
[RefundBusinessService]  ← @RetryableTask 注解在这里
   │
   │ HTTP POST /mock-api/payment/refund
   ↓
[MockExternalApiController]
   │ callCount = 1 (第1次)
   │ if (callCount <= 2) { throw Exception; }  ← 模拟支付宝超时
   │
   ✗ 抛出异常：支付宝网关超时
   ↓
[RetryableTaskAspect]  ← AOP 拦截到异常
   │
   │ 1. 构建 RetryTaskRequest
   │ 2. retryClient.submit(request)
   │ 3. POST http://retry-server:8080/api/retry/submit
   ↓
[retry-server]
   │
   │ 1. 保存任务到 MySQL (status=INIT, next_retry_time=10:00:05)
   │ 2. 返回 taskId
   ↓
[RetryMessageProducer]  ← SDK 中的消息生产者
   │
   │ 投递延时消息到 Redis ZSET
   │ - key: retry:client:delay:queue:payment.retry
   │ - score: 当前时间戳 + 5秒
   │ - value: {"taskId": "xxx", "sceneType": 10}
   ↓
[返回给浏览器]
   {
     "transId": "RFD_001",
     "status": "PENDING",
     "message": "支付宝接口返回失败，已提交重试任务"
   }
```

---

### 第 2 次调用（5 秒后，自动重试）

```
时间: 10:00:05

[Redis ZSET]
   │ 轮询线程检测到消息到期（score <= 当前时间戳）
   ↓
[RedisDelayQueueConsumer]  ← SDK 中的消费者线程
   │
   │ 从 ZSET 中取出消息：{"taskId": "xxx", "sceneType": 10}
   ↓
[LocalRetryExecutor]  ← SDK 中的本地状态机
   │
   │ 1. HTTP GET http://retry-server:8080/api/retry/task/xxx
   │    获取任务详情：transId=RFD_001, methodClass=RefundBusinessService
   │
   │ 2. HTTP POST http://retry-server:8080/api/retry/executing/xxx
   │    CAS 抢占执行权（INIT → EXECUTING）
   │
   │ 3. 调用 Hook.checkStatus(transId=RFD_001)
   │    返回：INIT（表示：任务未完成，继续执行）
   │
   │ 4. 反射调用业务方法：
   │    RefundBusinessService.refund("RFD_001", ...)
   ↓
[RefundBusinessService]
   │
   │ HTTP POST /mock-api/payment/refund
   ↓
[MockExternalApiController]
   │ callCount = 2 (第2次)
   │ if (callCount <= 2) { throw Exception; }  ← 还是失败！
   │
   ✗ 抛出异常：支付宝网关超时
   ↓
[LocalRetryExecutor]
   │ 捕获异常
   │
   │ 1. 根据退避策略计算下次重试时间：
   │    strategy=CUSTOM, intervals=[1,5,10], retryCount=1
   │    → nextDelay = 5 分钟 = 300 秒
   │
   │ 2. HTTP POST http://retry-server:8080/api/retry/retry-info
   │    更新：retryCount=2, nextRetryTime=10:05:05
   │
   │ 3. 重新投递延时消息到 Redis ZSET
   │    score = 当前时间戳 + 300 秒
   ↓
[继续等待下次重试...]
```

---

### 第 3 次调用（5 分钟后，成功）

```
时间: 10:05:05

[Redis ZSET]
   │ 消息再次到期
   ↓
[LocalRetryExecutor]
   │ 同样流程：获取任务 → 抢占执行权 → 调用 Hook
   │
   │ 反射调用：RefundBusinessService.refund("RFD_001", ...)
   ↓
[MockExternalApiController]
   │ callCount = 3 (第3次)
   │ if (callCount <= 2) { ... }  ← 不满足，跳过！
   │
   ✅ 返回：{"code": "SUCCESS", "message": "退款成功"}
   ↓
[LocalRetryExecutor]
   │ 业务方法执行成功（无异常）
   │
   │ 1. 调用 Hook.doQuery(transId=RFD_001)
   │    → 查询支付宝状态：GET /mock-api/payment/refund/status?transId=RFD_001
   │    → 返回：{"status": "SUCCESS"}
   │    → QueryResult.success()
   │
   │ 2. 调用 Hook.doCallback(transId=RFD_001)
   │    → 更新本地数据库：localDb.put("RFD_001", "SUCCESS")
   │
   │ 3. HTTP POST http://retry-server:8080/api/retry/success/xxx
   │    更新 MySQL：status=SUCCESS
   ↓
[任务完成！]
   管理后台显示：
   - 任务状态：SUCCESS ✅
   - 重试次数：2 次
   - 总耗时：5 分钟
```

---

## 🔑 关键问题解答

### Q4: MockExternalApiController 如何知道是第几次调用？

**答案**：通过 `callCounters` Map 记录每个 `transId` 的调用次数

```java
// MockExternalApiController.java
private final Map<String, AtomicInteger> callCounters = new ConcurrentHashMap<>();

@PostMapping("/payment/refund")
public Map<String, Object> refund(@RequestBody Map<String, Object> req) {
    String transId = String.valueOf(req.get("transId"));
    
    // 原子递增计数器
    int callCount = callCounters.computeIfAbsent(transId, k -> new AtomicInteger(0))
                                .incrementAndGet();
    
    // 根据调用次数决定成功或失败
    if (callCount <= 2) {
        throw new RuntimeException("支付宝网关超时（第" + callCount + "次）");
    }
    
    return Map.of("code", "SUCCESS");
}
```

**解释**：
- 第 1 次调用 `transId="RFD_001"` → `callCount = 1` → 抛异常
- 第 2 次调用 `transId="RFD_001"` → `callCount = 2` → 抛异常
- 第 3 次调用 `transId="RFD_001"` → `callCount = 3` → 返回成功

---

### Q5: 在真实场景中如何替换 MockExternalApiController？

**答案**：直接修改 `RefundBusinessService` 中的 URL

**Demo 中**：
```java
restTemplate.postForObject(
    "http://localhost:8082/mock-api/payment/refund",  // 调用 Mock
    request,
    Map.class
);
```

**真实场景**：
```java
restTemplate.postForObject(
    "https://openapi.alipay.com/gateway.do",  // 调用真实支付宝
    request,
    Map.class
);
```

**对应关系**：

| 代码位置 | Demo | 真实场景 |
|---------|------|----------|
| `RefundBusinessService` | `http://localhost:8082/mock-api/payment/refund` | `https://openapi.alipay.com/gateway.do` |
| `DemoRefundHook.doQuery()` | `http://localhost:8082/mock-api/payment/refund/status` | `https://openapi.alipay.com/gateway.do?method=query` |

---

## 📍 本地重试机制详解

### 核心问题：SDK 如何做到"本地重试"？

**答案**：通过 **Redis ZSET 延时队列** + **本地消费者线程**

### 架构图

```
┌─────────────────────────────────────────────────────────────┐
│           你的业务系统 (本地，端口 8080)                       │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │             retry-client-sdk (JAR 包)               │   │
│  │                                                      │   │
│  │  ┌──────────────────────────────────────────────┐   │   │
│  │  │  RetryableTaskAspect                         │   │   │
│  │  │  - 拦截 @RetryableTask 方法                  │   │   │
│  │  │  - 失败时提交任务到 retry-server             │   │   │
│  │  └──────────────────┬───────────────────────────┘   │   │
│  │                     │ HTTP POST                     │   │
│  │  ┌──────────────────▼───────────────────────────┐   │   │
│  │  │  RetryMessageProducer                        │   │   │
│  │  │  - 投递延时消息到 Redis ZSET                  │   │   │
│  │  │  - key: retry:client:delay:queue:业务线名     │   │   │
│  │  │  - score: 当前时间戳 + 延时秒数               │   │   │
│  │  └──────────────────┬───────────────────────────┘   │   │
│  │                     │                               │   │
│  │                     ↓ Redis 连接                    │   │
│  │           ┌─────────────────────┐                   │   │
│  │           │   Redis (外部)      │                   │   │
│  │           │   ZSET: 延时队列    │                   │   │
│  │           └─────────┬───────────┘                   │   │
│  │                     │                               │   │
│  │                     ↑ 轮询（每秒）                  │   │
│  │  ┌──────────────────┴───────────────────────────┐   │   │
│  │  │  RedisDelayQueueConsumer（后台线程）          │   │   │
│  │  │  - while(true) { 每秒查询到期消息 }           │   │   │
│  │  │  - 到期消息 → LocalRetryExecutor              │   │   │
│  │  └──────────────────┬───────────────────────────┘   │   │
│  │                     │                               │   │
│  │  ┌──────────────────▼───────────────────────────┐   │   │
│  │  │  LocalRetryExecutor（本地状态机）            │   │   │
│  │  │  1. 从 retry-server 获取任务详情              │   │   │
│  │  │  2. CAS 抢占执行权                            │   │   │
│  │  │  3. 调用 Hook.checkStatus()                  │   │   │
│  │  │  4. 反射调用本地业务方法                      │   │   │
│  │  │  5. 失败 → 重新计算延时 → 再次投递到 Redis   │   │   │
│  │  └──────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                         ↕ HTTP REST API
┌─────────────────────────────────────────────────────────────┐
│             retry-server (Docker, 端口 8080)                │
│             - 任务数据持久化（MySQL）                         │
│             - 不参与调度，只提供 CRUD API                      │
└─────────────────────────────────────────────────────────────┘
```

---

### 关键点 1：是的，客户端需要配置 Redis

**原因**：SDK 使用 Redis 作为延时队列的存储

**配置示例**：

```yaml
# application.yml
retry:
  client:
    server-url: http://localhost:8080
    mq-type: REDIS  # 使用 Redis 作为 MQ

spring:
  redis:
    host: localhost  # ← 必须配置 Redis 连接
    port: 6379
    database: 2      # ← 建议使用独立 database
```

**为什么不能跳过 Redis？**
- SDK 需要一个"定时器"机制来触发重试
- Redis ZSET 是最简单的延时队列实现
- 如果不想用 Redis，可以切换到 RabbitMQ：
  ```yaml
  retry:
    client:
      mq-type: RABBITMQ  # 使用 RabbitMQ 作为 MQ
  ```

---

### 关键点 2：本地重试的完整流程

#### 步骤 1：失败时投递延时消息

```java
// SDK 内部代码（RetryMessageProducer.java）
public void sendDelayMessage(String taskId, long delaySeconds) {
    // 计算消息的 score（过期时间戳）
    long score = System.currentTimeMillis() + (delaySeconds * 1000);
    
    // 投递到 Redis ZSET
    // key: retry:client:delay:queue:payment.retry
    // score: 1609459200000（10分钟后的时间戳）
    // value: {"taskId": "xxx", "sceneType": 10}
    redisTemplate.opsForZSet().add(
        "retry:client:delay:queue:payment.retry",
        message,
        score
    );
}
```

#### 步骤 2：后台线程轮询

```java
// SDK 内部代码（RedisDelayQueueConsumer.java）
@Component
public class RedisDelayQueueConsumer {
    
    @Scheduled(fixedDelay = 1000)  // 每秒执行一次
    public void pollMessages() {
        String key = "retry:client:delay:queue:payment.retry";
        long now = System.currentTimeMillis();
        
        // 查询 score <= now 的消息（已到期）
        Set<String> messages = redisTemplate.opsForZSet()
            .rangeByScore(key, 0, now, 0, 10);  // 每次最多取10条
        
        for (String message : messages) {
            // 提交给 LocalRetryExecutor 处理
            localRetryExecutor.execute(message);
            
            // 从 ZSET 中删除
            redisTemplate.opsForZSet().remove(key, message);
        }
    }
}
```

#### 步骤 3：本地执行

```java
// SDK 内部代码（LocalRetryExecutor.java）
public void execute(String message) {
    String taskId = parseTaskId(message);
    
    // 1. 从 retry-server 获取任务详情
    RetryTask task = retryClient.queryTask(taskId);
    
    // 2. CAS 抢占执行权（防止多节点并发执行）
    boolean locked = retryClient.markExecuting(taskId);
    if (!locked) return;  // 其他节点已在执行
    
    // 3. 调用 Hook 检查状态
    RetryHook hook = getHook(task.getSceneType());
    RetryStatus status = hook.checkStatus(context);
    if (status == RetryStatus.SUCCESS) {
        retryClient.markSuccess(taskId);
        return;  // 已完成，跳过
    }
    
    // 4. 反射调用业务方法
    try {
        Object bean = applicationContext.getBean(task.getMethodClass());
        Method method = bean.getClass().getMethod(task.getMethodName(), ...);
        method.invoke(bean, task.getParams());
        
        // 5. 成功：调用 Hook 回调
        QueryResult result = hook.doQuery(context);
        if (result.isSuccess()) {
            hook.doCallback(context, result);
            retryClient.markSuccess(taskId);
        }
        
    } catch (Exception e) {
        // 6. 失败：重新计算延时，再次投递
        long nextDelay = calculateNextDelay(task);  // 根据退避策略
        retryMessageProducer.sendDelayMessage(taskId, nextDelay);
        retryClient.updateRetryInfo(taskId, nextDelay);
    }
}
```

---

### 关键点 3：Redis 的作用

| 功能 | Redis 的角色 |
|------|-------------|
| **延时队列** | ZSET 存储待执行的任务 + 过期时间戳 |
| **定时触发** | 后台线程每秒查询 score <= now 的消息 |
| **分布式协调** | 多节点部署时，只有一个节点能从 ZSET 中取出消息 |

**为什么用 ZSET？**
- ✅ 支持按 score 排序（天然的定时功能）
- ✅ 原子操作（`rangeByScore` + `remove` 保证只有一个消费者）
- ✅ 简单高效（无需额外依赖）

---

### 关键点 4：如果不想用 Redis？

**方案 1：切换到 RabbitMQ**

```yaml
retry:
  client:
    mq-type: RABBITMQ

spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

RabbitMQ 使用 **延迟插件**（rabbitmq_delayed_message_exchange）实现延时消息。

**方案 2：自己实现定时任务（不推荐）**

如果既不想用 Redis 也不想用 RabbitMQ，可以：
- 用数据库轮询（性能差）
- 用 ScheduledExecutorService（进程重启丢失）
- 用 Quartz（复杂）

但都不如 Redis ZSET 简单高效。

---

## 🎯 总结

### 核心理解

1. **BusinessController** = 你的业务系统（发起方）
2. **MockExternalApiController** = 模拟的第三方系统（被调用方）
3. **Hook** = 探测器（检查状态、查询第三方、成功回调）
4. **SDK** = 本地重试引擎（AOP + Redis ZSET + 状态机）

### 本地重试的关键

- ✅ SDK 运行在你的应用进程中
- ✅ 使用 Redis ZSET 作为延时队列
- ✅ 后台线程每秒轮询到期消息
- ✅ 消息到期 → 反射调用本地方法
- ✅ 失败 → 重新计算延时 → 再次投递

### 依赖关系

```
你的应用
  ├─ retry-client-sdk (JAR)
  ├─ Redis (延时队列)
  └─ retry-server (HTTP API，可选)
```

**为什么说 retry-server 可选？**
- SDK 只需要 Redis 就能完成"本地重试"
- retry-server 的作用是"持久化任务"和"提供管理后台"
- 如果你不需要管理后台，理论上可以不部署 retry-server（但不推荐）

---

## 📚 下一步

1. **访问 Demo 接口**：
   ```bash
   # 触发退款场景
   curl -X POST http://localhost:8082/business/trigger/refund
   
   # 查看任务状态
   curl http://localhost:8082/business/tasks
   ```

2. **查看管理后台**：
   http://localhost:8081

3. **查看 Redis 延时队列**：
   ```bash
   redis-cli
   > ZRANGE retry:client:delay:queue:payment.retry 0 -1 WITHSCORES
   ```

4. **阅读源码**：
   - `retry-client-sdk/src/main/java/com/retry/platform/client/`
   - 重点看 `LocalRetryExecutor.java` 和 `RedisDelayQueueConsumer.java`

祝你使用愉快！🎉
