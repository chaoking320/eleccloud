# SDK 接入指南

> **适用对象**: 需要接入分布式重试平台的业务方开发者  
> **适用场景**: 所有需要自动重试的分布式调用（HTTP/RPC/消息/数据库等）

---

## 1. 快速接入（5分钟）

### Step 1: 添加依赖

```xml
<dependency>
    <groupId>com.retry.platform</groupId>
    <artifactId>retry-client-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2: 添加配置

```yaml
# application.yml
retry:
  client:
    server-url: http://retry-server-host:8080  # 重试平台服务端地址
    enabled: true                              # 是否启用，false 时所有操作变为 no-op
    dev-mode: false                            # 开发模式，仅打日志不实际提交
```

### Step 3: 在 Admin 后台注册场景

登录 `http://retry-admin-host:8081`，创建场景配置：

| 字段 | 说明 | 示例值 |
|------|------|--------|
| 场景类型 | 唯一整数标识 | 1 |
| 场景名称 | 描述性名称 | 电商退款场景 |
| 退避策略 | CUSTOM/FIXED/LINEAR/EXPONENTIAL | CUSTOM |
| 重试间隔 | CUSTOM策略下的间隔列表（分钟） | 1,5,10,30 |
| 最大重试次数 | 达到后写入死信 | 4 |
| 最大重试时长 | 秒，0=不限 | 3600 |
| Hook 类名 | 实现 RetryHook 的全限定类名 | com.your.app.RefundRetryHook |
| 客户端 URL | 业务服务的回调地址 | http://your-app:8082 |

### Step 4: 实现 RetryHook 接口

```java
@Component("com.your.app.RefundRetryHook") // Bean 名称必须是全限定类名
public class RefundRetryHook implements RetryHook {

    @Override
    public String checkStatus(RetryContext context) {
        // 查询本地数据库，返回 INIT / WAIT / SUCCESS
        String orderId = (String) context.getParams().get("orderId");
        return yourDao.getOrderStatus(orderId);
    }

    @Override
    public QueryResult doQuery(RetryContext context) {
        // 主动调用第三方查询接口
        String orderId = (String) context.getParams().get("orderId");
        ThirdPartyResult result = paymentApi.queryRefund(orderId);
        return result.isSuccess() 
            ? QueryResult.success(result.getData())
            : QueryResult.failure(result.getMessage());
    }

    @Override
    public void doCallback(RetryContext context, QueryResult result) {
        // 更新本地数据库状态
        String orderId = (String) context.getParams().get("orderId");
        yourDao.updateOrderStatus(orderId, "SUCCESS");
    }
}
```

### Step 5: 选择接入方式（三选一）

---

## 2. 接入方式详解

### 2.1 注解模式（推荐，适合大多数场景）

**适用场景**: 方法失败时明确抛出异常，且不需要复杂的重试提交决策。

```java
@Service
public class RefundService {

    /**
     * 在方法上添加 @RetryableTask
     *   sceneType     - 对应 Admin 后台的场景类型
     *   idempotentKey - 幂等键表达式，#参数名 或 #对象.字段名
     */
    @RetryableTask(sceneType = 1, idempotentKey = "#orderId")
    public boolean refund(String orderId, Double amount) {
        // 只需写正常业务逻辑
        // 如果抛出任何异常，AOP 自动提交重试任务
        return paymentApi.refund(orderId, amount);
    }
}
```

**原理**：AOP 环绕通知拦截方法，捕获异常后自动构建 `RetryTaskRequest` 提交到平台。

**注意事项**:
- `idempotentKey` 值必须在同一 sceneType 下全局唯一（如订单号）
- 方法参数需支持 JSON 序列化（平台回调时用于重建参数）
- 业务方法应实现幂等性（因为会被重复调用）

---

### 2.2 API 模式（灵活控制）

**适用场景**: 需要根据异常类型决定是否重试，或方法无法添加注解。

```java
@Service
public class SettlementService {

    @Autowired
    private RetryClient retryClient;

    public void settle(String settlementId, Double amount) {
        try {
            otaApi.submitSettlement(settlementId, amount);
        } catch (TimeoutException e) {
            // 只有超时才重试，业务异常不重试
            log.warn("Settlement timeout, submitting to retry platform: {}", settlementId);

            RetryTaskRequest request = new RetryTaskRequest();
            request.setSceneType(2);
            request.setIdempotentKey(settlementId);
            request.setMethodClass(this.getClass().getName());
            request.setMethodName("settle");
            // 方法参数 JSON，平台回调时透传
            request.setMethodParams("{\"settlementId\":\"" + settlementId + "\",\"amount\":" + amount + "}");

            retryClient.submit(request);

        } catch (BusinessException e) {
            // 业务异常直接抛出，不重试
            throw e;
        }
    }
}
```

---

### 2.3 预提交模式（强一致性场景）

**适用场景**: 资金/库存等对数据一致性要求极高的操作，必须保证即使进程崩溃也能最终执行。

```java
@Service
public class InventoryService {

    /**
     * preSubmit = true 开启预提交模式
     * 执行流程：
     *   1. AOP 先向平台注册 INIT 状态任务
     *   2. 执行 syncInventory()
     *   3a. 成功 → AOP 自动调用 retryClient.markSuccess(taskId) 标记 SUCCESS
     *   3b. 失败 → 任务保持 INIT，平台定时触发重试
     */
    @RetryableTask(sceneType = 3, idempotentKey = "#skuId", preSubmit = true)
    public void syncInventory(String skuId, Integer delta) {
        // 方法需要幂等：重复调用结果一致
        warehouseApi.deductStock(skuId, delta);
    }
}
```

**开销说明**: 每次方法调用多一次 HTTP 请求（注册任务），成功后再多一次 HTTP 请求（标记成功）。换来的收益是：即使应用在执行中崩溃，任务也不会丢失。

**与 POST_FAIL 的关键区别**:

```
POST_FAIL 模式:          PRE_SUBMIT 模式:
  调用方法                 注册任务(INIT)
     ↓                       ↓
  方法崩溃！               调用方法
     ↓                       ↓
  任务丢失！              方法崩溃！
                              ↓
                        任务保持 INIT → 平台重试
```

---

## 3. RetryHook 接口详解

```java
public interface RetryHook {

    /**
     * 检查任务当前状态（每次重试前调用）
     *
     * 返回值：
     *   "INIT"    - 任务未完成，继续执行重试逻辑
     *   "WAIT"    - 已发出请求，等待第三方确认，触发 doQuery()
     *   "SUCCESS" - 任务已完成，平台跳过本次重试
     *
     * 典型实现：查询本地数据库的业务状态字段
     */
    String checkStatus(RetryContext context);

    /**
     * 主动查询第三方系统状态（checkStatus 返回 WAIT 时调用）
     *
     * 典型实现：调用第三方 API 查询处理结果
     * 返回 QueryResult.success() 时触发 doCallback()
     * 返回 QueryResult.failure() 时等待下次重试继续查询
     */
    QueryResult doQuery(RetryContext context);

    /**
     * 执行成功后的本地处理（doQuery 返回成功后调用）
     *
     * 典型实现：更新本地数据库状态为成功，发送通知等
     */
    void doCallback(RetryContext context, QueryResult result);
}
```

### RetryContext 字段说明

```java
public class RetryContext {
    String taskId;          // 任务ID
    Integer sceneType;      // 场景类型
    String idempotentKey;   // 幂等键（如订单号）
    String methodClass;     // 方法所在类
    String methodName;      // 方法名
    String methodParamsJson;// 方法参数 JSON
    Map<String, Object> params; // 解析后的参数 Map（直接用 context.getParams().get("参数名")）
    Integer retryCount;     // 当前重试次数（从 0 开始）
    String taskStatus;      // 当前任务状态
}
```

---

## 4. 常见场景示例

### 场景 A：调用第三方支付，可能超时

```
checkStatus → "INIT"（本地未记录成功）
executeMethod → 调用支付宝退款接口 → 成功，本地状态改为 WAIT
doQuery → 查询支付宝退款状态 → 已到账 → SUCCESS
doCallback → 更新本地订单状态 = SUCCESS
```

### 场景 B：消息发送失败

```
checkStatus → "INIT"（消息未发送成功）
executeMethod → 发送 MQ 消息 → 成功（无需 doQuery，消息发送即完成）
doCallback → 可选：更新本地发送记录状态
```

> 注意：如果你的重试场景不需要 `doQuery`，可以在 `checkStatus` 返回 SUCCESS 的时机处理，或在 `doQuery` 始终返回 `QueryResult.success()`。

### 场景 C：数据库更新重试

```
@RetryableTask(sceneType = 5, idempotentKey = "#userId")
public void updateUserPoints(String userId, Integer points) {
    // 数据库操作，失败自动重试
    userDao.addPoints(userId, points);
}
```

---

## 5. Bean 名称规范

> [!IMPORTANT]
> `RetryHook` 的实现类必须以**全限定类名**注册为 Spring Bean：

```java
// 正确
@Component("com.your.company.app.hook.RefundRetryHook")
public class RefundRetryHook implements RetryHook { }

// 错误（平台找不到 Bean）
@Component
public class RefundRetryHook implements RetryHook { }
```

在 Admin 后台配置时，`hookClass` 字段填写相同的全限定类名：
`com.your.company.app.hook.RefundRetryHook`

---

## 6. 常见问题（FAQ）

**Q: 提交重试任务时提示"幂等键已存在"怎么处理？**  
A: 说明相同业务 ID 已有一条进行中的重试任务。可以查询任务状态，等待其完成后再提交，或者在场景配置中针对该业务修改幂等键策略。

**Q: 方法参数中有复杂对象，能序列化吗？**  
A: SDK 使用 Jackson 序列化参数，只要参数类有无参构造器和标准 getter/setter 即可。建议避免使用循环引用对象。

**Q: 开发环境不想真实提交到重试平台怎么办？**  
A: 配置 `retry.client.dev-mode: true`，所有提交只打印日志，不发送 HTTP 请求。

**Q: 平台在哪个线程调用我的 RetryHook 方法？**  
A: 通过 `RetryCallbackController` 由 HTTP 请求线程调用，即平台服务端发起 HTTP 请求打到你的应用，你的应用处理这个 HTTP 请求。

**Q: 如何处理 doQuery 一直查不到结果的情况？**  
A: 设置 `maxRetryDuration`（最大重试时长），超时后任务进入 `failed_task` 死信队列，在 Admin 后台手动处理或告警通知。
