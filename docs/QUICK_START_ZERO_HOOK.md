# 零Hook模式 - 5分钟快速接入指南

> **适用场景**: 简单重试场景，方法执行成功即认为任务完成，无需查询第三方状态  
> **接入时间**: **5-10分钟** ⚡  
> **学习成本**: **极低** 👶

---

## 🎯 什么是零Hook模式？

**传统模式** (需要实现3个方法):
```java
@Component
public class MyHook implements RetryHook {
    String checkStatus(...)  { /* 写代码 */ }
    QueryResult doQuery(...) { /* 写代码 */ }
    void doCallback(...)     { /* 写代码 */ }
}
```

**零Hook模式** (无需写代码):
```java
@RetryableTask(sceneType = 1, idempotentKey = "#orderId")
public void syncInventory(String orderId) {
    warehouseApi.syncStock(orderId);
    // ✅ 就这么简单！SDK自动处理重试
}
```

---

## ⚡ 5分钟接入步骤

### Step 1: 添加依赖 (1分钟)

```xml
<dependency>
    <groupId>com.retry.platform</groupId>
    <artifactId>retry-client-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2: 配置文件 (2分钟)

```yaml
# application.yml
retry:
  client:
    server-url: http://retry-server:8080  # 重试服务地址
    enabled: true
```

### Step 3: Admin创建场景 (2分钟)

登录 `http://retry-admin:8081`，点击"快速创建"：

```
场景类型: 1001
场景名称: 库存同步
使用模板: ✅ 库存同步模板
Hook类名: (留空)  ← 关键：留空即使用默认Hook
```

### Step 4: 添加注解 (30秒)

```java
@Service
public class InventoryService {
    
    @RetryableTask(sceneType = 1001, idempotentKey = "#skuId")
    public void syncStock(String skuId, Integer quantity) {
        // 业务代码，失败自动重试
        warehouseApi.updateStock(skuId, quantity);
    }
}
```

### ✅ 完成！开始使用

```java
inventoryService.syncStock("SKU-12345", 100);
// 如果失败，会自动重试（1分钟、5分钟、10分钟...）
```

---

## 📋 适用场景

### ✅ 适合零Hook模式

| 场景 | 说明 | 示例 |
|------|------|------|
| **库存扣减** | 方法成功即扣减成功 | `warehouseApi.deductStock()` |
| **数据同步** | 同步完成无需回查 | `syncOrderToDataWarehouse()` |
| **消息发送** | 发送成功即完成 | `smsService.send()` |
| **日志记录** | 写入成功即完成 | `auditLogger.log()` |
| **缓存更新** | 更新成功即完成 | `redisCache.set()` |

### ❌ 不适合零Hook模式（需要自定义Hook）

| 场景 | 原因 | 推荐方案 |
|------|------|----------|
| **支付退款** | 需要查询支付宝确认状态 | 自定义RefundHook |
| **订单创建** | 需要查询下游订单号 | 自定义OrderHook |
| **异步回调** | 需要等待第三方回调 | 自定义CallbackHook |

---

## 💡 完整示例

### 示例1: 库存扣减

```java
@Service
public class InventoryService {
    
    @Autowired
    private WarehouseApi warehouseApi;
    
    /**
     * 扣减库存 - 零Hook模式
     * 
     * 特点：
     * 1. 无需写Hook代码
     * 2. 方法执行成功即认为扣减成功
     * 3. 失败自动重试（1min、5min、10min...）
     */
    @RetryableTask(
        sceneType = 1001,
        idempotentKey = "#skuId"  // 使用SKU ID作为幂等键
    )
    public void deductStock(String skuId, Integer quantity) {
        // ⚠️ 确保下游接口支持幂等
        warehouseApi.deductStock(skuId, quantity);
        
        log.info("Stock deducted: skuId={}, quantity={}", skuId, quantity);
    }
}
```

**测试**:
```java
// 正常调用
inventoryService.deductStock("SKU-001", 10);

// 如果warehouseApi抛异常，会自动重试：
// - 第1次重试：1分钟后
// - 第2次重试：5分钟后
// - 第3次重试：10分钟后
// ...直到成功或达到最大重试次数
```

---

### 示例2: 数据同步

```java
@Service
public class DataSyncService {
    
    @Autowired
    private DataWarehouseApi dataWarehouse;
    
    /**
     * 同步订单到数据仓库 - 零Hook模式
     */
    @RetryableTask(
        sceneType = 1002,
        idempotentKey = "#order.orderId"  // 支持对象字段提取
    )
    public void syncOrder(Order order) {
        dataWarehouse.upsertOrder(order);
        log.info("Order synced: orderId={}", order.getOrderId());
    }
    
    /**
     * 批量同步 - 配合批量接口
     */
    @RetryableTask(
        sceneType = 1003,
        idempotentKey = "#batchId"
    )
    public void syncOrdersBatch(String batchId, List<Order> orders) {
        dataWarehouse.batchUpsert(orders);
        log.info("Batch synced: batchId={}, count={}", batchId, orders.size());
    }
}
```

---

### 示例3: 消息推送

```java
@Service
public class NotificationService {
    
    @Autowired
    private SmsApi smsApi;
    
    /**
     * 发送短信 - 零Hook模式
     */
    @RetryableTask(
        sceneType = 1004,
        idempotentKey = "#mobile + '-' + #templateCode"  // 组合幂等键
    )
    public void sendSms(String mobile, String templateCode, Map<String, String> params) {
        smsApi.send(mobile, templateCode, params);
        log.info("SMS sent: mobile={}, template={}", mobile, templateCode);
    }
}
```

---

## ⚠️ 重要注意事项

### 1. 幂等性要求

**业务方法必须幂等**，因为会被多次调用：

```java
// ✅ 幂等示例：使用唯一键
warehouseApi.deductStock(skuId, quantity);  // SKU ID保证幂等

// ❌ 非幂等示例：多次调用会重复扣减
currentStock = getStock() - quantity;  // 危险！
```

### 2. 下游接口支持

**下游系统必须支持幂等**，避免重复操作：

```java
// ✅ 下游接口支持幂等键
POST /api/stock/deduct
{
  "requestId": "REQ-12345",  ← 下游根据此字段去重
  "skuId": "SKU-001",
  "quantity": 10
}

// ❌ 下游接口不支持幂等
POST /api/stock/deduct  ← 每次请求都会扣减
{
  "skuId": "SKU-001",
  "quantity": 10
}
```

### 3. 异常处理

**只有抛异常才会重试**：

```java
@RetryableTask(sceneType = 1001, idempotentKey = "#id")
public void process(String id) {
    try {
        api.call(id);
    } catch (Exception e) {
        log.error("Failed", e);
        // ❌ 吞掉异常不会重试！
    }
}

// ✅ 正确：让异常抛出
@RetryableTask(sceneType = 1001, idempotentKey = "#id")
public void process(String id) {
    api.call(id);  // 异常自动抛出，触发重试
}
```

---

## 🆚 零Hook vs 自定义Hook对比

| 维度 | 零Hook模式 | 自定义Hook模式 |
|------|-----------|---------------|
| **接入时间** | 5-10分钟 | 1-2小时 |
| **代码量** | 0行 | 30-50行 |
| **适用场景** | 简单场景（80%） | 复杂场景（20%） |
| **学习成本** | 极低 | 中等 |
| **灵活性** | 低（固定逻辑） | 高（完全自定义） |
| **状态查询** | ❌ 不支持 | ✅ 支持 |
| **回调处理** | ❌ 不支持 | ✅ 支持 |

### 何时升级到自定义Hook？

当你需要以下能力时：
1. ✅ 查询第三方系统状态（如支付宝退款查询）
2. ✅ 复杂的状态判断逻辑
3. ✅ 成功后的回调处理（发送通知、更新状态）
4. ✅ 根据不同状态采取不同动作

参考：[Hook接口详细文档](./SDK_GUIDE.md#retryHook接口详解)

---

## 🚀 进阶配置

### 自定义重试策略

```yaml
# Admin后台场景配置
场景类型: 1001
重试策略: CUSTOM
重试间隔: 1,5,10,30,60  # 分钟
最大重试: 5次
```

### 开发模式（本地调试）

```yaml
retry:
  client:
    dev-mode: true  # 只打日志，不实际提交任务
```

### 禁用重试（临时）

```yaml
retry:
  client:
    enabled: false  # 禁用重试功能
```

---

## 📞 常见问题

**Q1: 零Hook模式能满足多少场景？**  
A: 根据我们的统计，**80%的重试场景**都可以使用零Hook模式。只有需要查询第三方状态的场景才需要自定义Hook。

**Q2: 零Hook模式性能如何？**  
A: 与自定义Hook性能完全一致，因为底层使用同一套引擎。

**Q3: 如何从零Hook升级到自定义Hook？**  
A: 很简单，实现Hook接口并在Admin后台配置hookClass即可，无需修改业务代码。

**Q4: 零Hook模式支持预提交吗？**  
A: 支持！添加 `preSubmit = true` 即可：
```java
@RetryableTask(sceneType = 1001, idempotentKey = "#id", preSubmit = true)
public void process(String id) { ... }
```

**Q5: 如何查看重试日志？**  
A: 访问 Admin后台 → 任务监控 → 搜索幂等键

---

## ✨ 总结

**零Hook模式让重试变得简单**：

```
传统模式：Maven依赖 → 配置 → 创建场景 → 实现Hook → 添加注解 → 测试
零Hook模式：Maven依赖 → 配置 → 创建场景 → 添加注解 → 完成！ ✅

接入时间：2-3小时 → 5-10分钟 ⚡
代码量：  50行 → 0行 🎉
学习成本：中等 → 极低 👶
```

**立即开始使用零Hook模式，让重试更简单！** 🚀
