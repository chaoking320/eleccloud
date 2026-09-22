# SDK 接入指南

> **适用对象**: 需要接入 ElecCloud 分布式重试平台的业务方开发者
> **适用场景**: 所有需要自动重试的分布式调用（HTTP/RPC/消息/数据库等）

---

## 1. 快速接入（5 分钟）

### Step 1：添加依赖

```xml
<dependency>
    <groupId>com.retry.platform</groupId>
    <artifactId>retry-client-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2：添加配置

```yaml
# application.yml
retry:
  client:
    server-url: http://retry-server-host:8080  # 重试平台服务端地址
    enabled: true                              # 是否启用
    dev-mode: false                            # 开发模式（true 时只打日志不实际提交）

    # MQ 配置（二选一）
    mq-type: REDIS                             # REDIS（默认）或 RABBITMQ
    queue-name: your-biz.retry                 # 业务线专属队列名，实现隔离

    # 可选：消费者并发数（仅 REDIS 模式有效）
    consumer-concurrency: 2
```

> [!IMPORTANT]
> `queue-name` 是业务线隔离的关键配置。同一套 ElecCloud 平台上的不同业务线（支付、物流、营销等）应配置不同的 `queue-name`，确保消息互不干扰。

### Step 3：在 Admin 后台注册场景

登录 `http://retry-admin-host:8081`，创建场景配置：

| 字段 | 说明 | 示例值 |
|------|------|--------|
| 场景类型 | 唯一整数标识（业务约定） | 1 |
| 场景名称 | 描述性名称 | 电商退款场景 |
| 退避策略 | CUSTOM/FIXED/LINEAR/EXPONENTIAL | CUSTOM |
| 重试间隔 | CUSTOM 策略的间隔列表（分钟） | 1,5,10,30 |
| 最大重试次数 | 达到后写入死信 | 4 |
| 最大重试时长 | 秒，0=不限 | 3600 |
| Hook 类名 | 实现 RetryHook 的全限定类名 | com.your.app.RefundRetryHook |

### Step 4：实现 RetryHook 接口

```java
// Bean 名称必须是全限定类名
@Component("com.your.app.RefundRetryHook")
public class RefundRetryHook implements RetryHook {

    @Override
    public String checkStatus(RetryContext context) {
        // 查询本地数据库，判断任务当前状态
        // 返回 "INIT"（未完成）/ "WAIT"（等待第三方确认）/ "SUCCESS"（已完成）
        String transId = (String) context.getParams().get("transId");
        return yourDao.getRefundStatus(transId);
    }

    @Override
    public QueryResult doQuery(RetryContext context) {
        // checkStatus 返回 WAIT 时调用，主动查询第三方
        String transId = (String) context.getParams().get("transId");
        ThirdPartyResult result = paymentApi.queryRefund(transId);
        return result.isSuccess()
            ? QueryResult.success(result.getData())
            : QueryResult.failure(result.getMessage());
    }

    @Override
    public void doCallback(RetryContext context, QueryResult result) {
        // doQuery 确认成功后调用，更新本地状态
        String transId = (String) context.getParams().get("transId");
        yourDao.updateRefundStatus(transId, "SUCCESS");
    }
}
```

### Step 5：选择接入方式

---

## 2. 三种接入方式详解

### 2.1 注解模式（推荐）

**适用场景**: 方法失败时抛出异常，希望自动进入重试。

```java
@Service
public class RefundService {

    @RetryableTask(sceneType = 1, idempotentKey = "#transId")
    public boolean refund(String transId, String orderId, Double amount) {
        // 直接写业务逻辑
        // 抛出任何异常 → AOP 自动提交重试任务 → MQ 延时投递 → 本地状态机执行
        return paymentApi.refund(transId, amount);
    }
}
```

**注意事项**：
- > [!IMPORTANT]
  > **先决条件**：业务方接口的下游处理系统必须支持基于 `transId`（交易唯一流水号）的幂等校验，以避免因网络超时重发请求导致的资损或重复业务操作。
- `idempotentKey` 在同一 `sceneType` 下必须全局唯一（统一使用交易流水号 `transId`，避免使用 `orderId` 导致同一订单不能进行多次不同金额的退款/结算）。
- 方法参数需支持 JSON 序列化（重试时用于重建方法入参）
- 业务方法应实现幂等（因为会被重复调用）

---

### 2.2 API 模式（精确控制）

**适用场景**: 需要根据异常类型决定是否重试。

```java
@Service
public class SettlementService {

    @Autowired
    private RetryClient retryClient;

    public void settle(String settlementId, Double amount) {
        try {
            otaApi.submitSettlement(settlementId, amount);
        } catch (TimeoutException e) {
            // 只有超时才重试
            RetryTaskRequest request = new RetryTaskRequest();
            request.setSceneType(2);
            request.setIdempotentKey(settlementId);
            request.setMethodClass(this.getClass().getName());
            request.setMethodName("settle");
            request.setMethodParams(JsonUtil.toJson(Map.of(
                "settlementId", settlementId, "amount", amount)));
            retryClient.submit(request);

        } catch (BusinessException e) {
            // 业务异常直接抛出，不重试
            throw e;
        }
    }
}
```

---

### 2.3 预提交模式（强一致性）

**适用场景**: 资金扣减、库存同步等操作，要求即使进程崩溃也不丢任务。

```java
@Service
public class InventoryService {

    /**
     * preSubmit = true：方法执行前先注册 INIT 任务
     * 执行流程：
     *   1. AOP 先向平台注册 INIT 状态任务
     *   2. 执行 syncInventory()
     *   3a. 成功 → AOP 自动调用 retryClient.markSuccess() 标记 SUCCESS
     *   3b. 失败/进程崩溃 → 任务保持 INIT，MQ 触发后续重试
     */
    @RetryableTask(sceneType = 3, idempotentKey = "#skuId", preSubmit = true)
    public void syncInventory(String skuId, Integer delta) {
        warehouseApi.deductStock(skuId, delta); // 必须实现幂等
    }
}
```

**与 POST_FAIL 的关键区别**：

```
POST_FAIL 模式:          PRE_SUBMIT 模式:
  调用方法                  注册任务(INIT)
     ↓                          ↓
  进程崩溃！                 调用方法
     ↓                          ↓
  任务丢失！              进程崩溃！
                                ↓
                          任务保持 INIT → MQ 触发重试 ✅
```

---

## 3. RetryHook 接口详解

```java
public interface RetryHook {

    /**
     * 检查任务当前状态（每次重试前调用，是幂等安全阀）
     *
     * @return "INIT"    - 任务未完成，继续执行重试逻辑
     *         "WAIT"    - 已发出请求，等待第三方确认，触发 doQuery()
     *         "SUCCESS" - 任务已完成，跳过本次重试
     */
    String checkStatus(RetryContext context);

    /**
     * 主动查询第三方系统状态（checkStatus 返回 WAIT 时调用）
     *
     * @return QueryResult.success() → 触发 doCallback()
     *         QueryResult.failure() → 等待下次重试继续查询
     */
    QueryResult doQuery(RetryContext context);

    /**
     * 任务最终成功时的本地处理（doQuery 返回 success 后调用）
     * 典型实现：更新本地状态为成功，发送通知等
     */
    void doCallback(RetryContext context, QueryResult result);
}
```

### RetryContext 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `taskId` | String | 任务 ID |
| `sceneType` | Integer | 场景类型 |
| `idempotentKey` | String | 幂等键（如订单号） |
| `methodClass` | String | 方法所在类全限定名 |
| `methodName` | String | 方法名 |
| `methodParamsJson` | String | 方法参数 JSON 原文 |
| `params` | Map | 解析后的参数 Map，直接 `context.getParams().get("参数名")` |
| `retryCount` | Integer | 当前已重试次数（从 0 开始） |
| `maxRetryCount` | Integer | 最大重试次数上限 |

---

## 4. 完整配置项说明

```yaml
retry:
  client:
    enabled: true                    # 是否启用，false 时所有操作变为 no-op
    server-url: http://host:8080     # retry-server 地址
    dev-mode: false                  # true 时只打日志，不发 HTTP 请求

    mq-type: REDIS                   # REDIS（默认）| RABBITMQ
    queue-name: default.retry        # 业务线专属队列名，多业务线必须不同

    connect-timeout: 3000            # 连接 server 超时（ms）
    read-timeout: 5000               # 读取 server 超时（ms）
    consumer-concurrency: 2          # Redis 模式消费者并发线程数
```

> [!TIP]
> 使用 RabbitMQ 模式时，还需要在 `application.yml` 中配置 `spring.rabbitmq.*` 标准连接信息。

---

## 5. ⚠️ 生产最佳实践与避坑指南 (Caveats & Best Practices)

注解式重试的本质是**将方法调用上下文持久化并在未来通过反射重新执行**。为确保线上稳定运行，请务必阅读以下生产避坑建议：

### 5.1 强烈建议开启 `-parameters` 编译参数
Spring 在解析 SpEL 表达式（如 `idempotentKey = "#orderId"`）以及 SDK 在进行方法形参名匹配时，依赖字节码中的真实参数名。若未开启此选项，Java 默认会将参数编译为 `arg0, arg1`，可能导致 SpEL 无法获取参数值。

**在业务工程的 `pom.xml` 中配置：**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
        <parameters>true</parameters> <!-- 开启形参名保留 -->
    </configuration>
</plugin>
```

### 5.2 避免使用复杂匿名嵌套泛型作为方法入参
SDK 内部使用 Jackson 将方法入参序列化为 JSON 存储并在重试时反序列化。
- **推荐写法（清晰明确）**：
  ```java
  @RetryableTask(sceneType = 1001, idempotentKey = "#req.orderId")
  public void syncOrder(OrderSyncDTO req) { ... }
  ```
- **不推荐写法（存在泛型擦除风险）**：
  ```java
  // 尽量避免：复杂的深层嵌套泛型在反序列化时可能被降级解析为 LinkedHashMap
  @RetryableTask(...)
  public void processBatch(List<Map<String, Object>> items) { ... }
  ```

### 5.3 保持重试方法签名的向后兼容性
如果线上队列中存在尚未完成重试的任务（例如指数退避数小时的任务），请注意：
- **避免直接重命名方法名**：否则重启后待重试任务反射查找原方法将抛出 `NoSuchMethodException`。
- **重构建议**：若需修改方法参数列表，建议保留原有方法并作为转发入口，或者新增重载方法过渡。

### 5.4 业务方法必须保证最终幂等
重试可能会因网络超时发生多次执行。无论使用何种重试框架，业务方法自身必须基于业务主键（如订单号、流水号）具备天然的防重入或幂等更新能力。

---

## 6. 常见问题（FAQ）

**Q: 幂等键已存在，提交时报错怎么处理？**
A: 说明相同业务 ID 已有进行中的重试任务。等待其完成后再提交，或在 Admin 后台手动取消旧任务。

**Q: 方法参数有复杂对象能序列化吗？**
A: SDK 使用 Jackson 序列化，需要有无参构造器和标准 getter/setter，避免循环引用。

**Q: Bean 名称如何配置？**
A: `RetryHook` 实现类必须以**全限定类名**注册为 Spring Bean：
```java
// ✅ 正确
@Component("com.your.company.app.hook.RefundRetryHook")
public class RefundRetryHook implements RetryHook { }

// ❌ 错误（平台找不到 Bean）
@Component
public class RefundRetryHook implements RetryHook { }
```

**Q: 如何不让 doQuery 一直查不到结果？**
A: 设置 `maxRetryDuration`（场景最大重试时长，秒），超时后任务进入死信队列，Admin 后台可查看并告警。

**Q: 开发环境不想提交到真实平台怎么办？**
A: 配置 `retry.client.dev-mode: true`，所有操作只打 INFO 日志，不发出任何 HTTP 请求。
