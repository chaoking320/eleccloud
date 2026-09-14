# Hook 模式图解 - 5 分钟看懂

> **目标**：用最简单的方式解释什么是 Hook，什么时候需要 Hook

---

## 🎯 一句话总结

**Hook = 给重试任务装上"探测器"，让它知道任务的真实状态**

---

## 📊 两种模式对比

### 模式 1：零 Hook（推荐，80% 场景够用）

```
你的代码：
┌─────────────────────────────────────┐
│ @RetryableTask(...)                 │
│ public void syncStock() {           │
│     api.updateStock();  ← 调用接口   │
│ }                                   │
└─────────────────────────────────────┘
           ↓
    成功？ ✓ → 任务完成
    失败？ ✗ → 1分钟后重试
```

**适用场景**：
- 库存同步
- 消息发送
- 文件上传
- **任何"调用成功 = 任务完成"的场景**

**特点**：
- ✅ 代码极简（3 行）
- ✅ 零额外配置
- ⚠️ 无法查询第三方状态

---

### 模式 2：自定义 Hook（需要查第三方）

```
你的代码：
┌─────────────────────────────────────┐
│ @RetryableTask(...)                 │
│ public void refund() {              │
│     alipayApi.refund();  ← 发起退款  │
│ }                                   │
└─────────────────────────────────────┘
           ↓
      网络超时！❌
           ↓
   钱退了没？不知道... 😰
           ↓
   需要 Hook 来查！👇
```

**Hook 登场**：

```java
@Component("com.company.RefundHook")
public class RefundHook implements RetryHook {
    
    // Hook 方法 1: 检查本地状态
    public String checkStatus(...) {
        // 先查本地数据库
        return "WAIT";  // 表示：去查支付宝
    }
    
    // Hook 方法 2: 查支付宝
    public QueryResult doQuery(...) {
        // 主动调用支付宝查询接口
        AlipayResponse resp = alipayApi.queryRefund(...);
        return resp.isSuccess() 
            ? QueryResult.success()  // 退款成功
            : QueryResult.failure(); // 还在处理
    }
    
    // Hook 方法 3: 成功后做什么
    public void doCallback(...) {
        // 更新数据库、发通知
    }
}
```

**流程图**：

```
第1次重试：1分钟后
   ↓
checkStatus()  → 查本地 DB → 状态是 "PROCESSING"
   ↓
返回 "WAIT"  → 触发 doQuery()
   ↓
doQuery()  → 调用支付宝查询接口
   ↓
支付宝返回：还在处理中
   ↓
等待下次重试（5分钟后）
   ↓
   
第2次重试：5分钟后
   ↓
checkStatus()  → 查本地 DB → 状态是 "PROCESSING"
   ↓
返回 "WAIT"  → 触发 doQuery()
   ↓
doQuery()  → 调用支付宝查询接口
   ↓
支付宝返回：退款成功！✓
   ↓
doCallback()  → 更新本地状态、发送短信通知
   ↓
任务完成！🎉
```

---

## 🤔 什么时候需要 Hook？

### ✅ 需要 Hook（场景特征）

**判断标准**：如果你需要"主动查询第三方系统确认状态"，就需要 Hook

| 场景 | 为什么需要 Hook |
|------|----------------|
| **支付退款** | 退款接口超时，需要查支付宝确认是否真的退了 |
| **订单创建** | 下游系统返回"处理中"，需要定期查询订单号 |
| **实名认证** | 提交认证后，需要查询认证结果 |
| **异步回调** | 等待第三方回调，需要定期检查回调是否到达 |

**共同特点**：
- ⚠️ 接口可能返回"处理中"
- ⚠️ 网络超时不确定是否成功
- ⚠️ 需要主动查询第三方状态

---

### ❌ 不需要 Hook（场景特征）

**判断标准**：如果"接口调用成功 = 任务完成"，就不需要 Hook

| 场景 | 为什么不需要 Hook |
|------|------------------|
| **库存同步** | `updateStock()` 成功 = 库存已更新 |
| **消息发送** | `sendSms()` 成功 = 短信已发送 |
| **文件上传** | `uploadFile()` 成功 = 文件已上传 |
| **日志记录** | `writeLog()` 成功 = 日志已写入 |
| **缓存更新** | `cache.set()` 成功 = 缓存已更新 |

**共同特点**：
- ✅ 接口返回成功就是真的成功
- ✅ 不需要查询其他系统确认
- ✅ 幂等性由下游接口保证

---

## 📝 完整示例对比

### 示例 1：库存同步（零 Hook）

```java
// 就这一行！
@RetryableTask(sceneType = 1001, idempotentKey = "#skuId")
public void syncStock(String skuId, Integer quantity) {
    warehouseApi.updateStock(skuId, quantity);
    // 成功 → 任务完成
    // 失败 → 1分钟后重试，直到成功
}
```

**为什么不需要 Hook？**
- `updateStock()` 返回成功 = 库存真的更新了
- 仓库系统保证幂等（多次调用结果一样）
- 不需要查询其他系统

---

### 示例 2：支付退款（需要 Hook）

**业务代码（还是一行）**：

```java
@RetryableTask(sceneType = 1002, idempotentKey = "#transId")
public void refund(String transId, Double amount) {
    alipayApi.refund(transId, amount);
    // 问题：网络超时了，钱退了没？😰
}
```

**Hook 实现（3个方法）**：

```java
@Component("com.company.RefundHook")
public class RefundHook implements RetryHook {
    
    @Autowired
    private PaymentDao dao;
    
    @Autowired
    private AlipayApi alipayApi;
    
    /**
     * 方法1: 检查本地状态
     * 作用：避免重复调用退款接口
     */
    @Override
    public String checkStatus(RetryContext ctx) {
        String transId = (String) ctx.getParams().get("transId");
        
        // 查本地数据库
        String status = dao.getRefundStatus(transId);
        
        if ("SUCCESS".equals(status)) {
            return "SUCCESS";  // 已完成，不用重试了
        } else if ("PROCESSING".equals(status)) {
            return "WAIT";     // 正在处理，去查支付宝
        } else {
            return "INIT";     // 还没开始，继续重试
        }
    }
    
    /**
     * 方法2: 查询支付宝
     * 作用：主动确认退款是否真的成功
     */
    @Override
    public QueryResult doQuery(RetryContext ctx) {
        String transId = (String) ctx.getParams().get("transId");
        
        try {
            // 调用支付宝查询接口
            AlipayRefundQueryResponse response = alipayApi.queryRefund(transId);
            
            if (response.isSuccess()) {
                // 支付宝确认：退款成功！
                return QueryResult.success(response.getRefundNo());
            } else {
                // 支付宝返回：还在处理中
                return QueryResult.failure("退款处理中，请稍后查询");
            }
        } catch (Exception e) {
            // 查询接口调用失败
            return QueryResult.failure("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 方法3: 成功后的处理
     * 作用：更新本地状态、发通知
     */
    @Override
    public void doCallback(RetryContext ctx, QueryResult result) {
        String transId = (String) ctx.getParams().get("transId");
        String refundNo = (String) result.getData();
        
        // 更新本地数据库
        dao.updateRefundStatus(transId, "SUCCESS", refundNo);
        
        // 发送短信通知用户
        smsService.send("您的退款已成功，退款单号：" + refundNo);
        
        log.info("退款成功: transId={}, refundNo={}", transId, refundNo);
    }
}
```

**为什么需要 Hook？**
1. ⚠️ 退款接口可能超时（网络问题）
2. ⚠️ 超时不代表失败（钱可能已经退了）
3. ⚠️ 需要查支付宝确认状态
4. ✅ Hook 提供了查询能力

---

## 🎨 状态流转图

### 零 Hook 模式

```
INIT（初始）
  ↓
调用方法
  ↓
成功？
  ├─ ✓ → SUCCESS（完成）
  └─ ✗ → INIT（等待重试）
           ↓
        1分钟后继续
```

---

### Hook 模式（完整流程）

```
INIT（初始）
  ↓
checkStatus()  ← 每次重试前先检查
  ├─ 返回 "SUCCESS" → SUCCESS（完成）
  ├─ 返回 "WAIT" → 进入查询流程 ↓
  └─ 返回 "INIT" → 调用业务方法 ↓
  
调用业务方法
  ↓
成功？
  ├─ ✓ → WAIT（等待确认）
  └─ ✗ → INIT（等待重试）
  
WAIT（等待确认）
  ↓
doQuery()  ← 查第三方
  ├─ 返回 success → doCallback() → SUCCESS（完成）✓
  └─ 返回 failure → WAIT（继续等待）
                      ↓
                  5分钟后再查
```

---

## 💡 选择建议

### 快速判断

**问自己一个问题**：

> "如果我的接口调用超时了，我能确定任务是否成功吗？"

- ✅ **能确定**（如库存同步，查本地 DB 就知道） → **零 Hook 模式**
- ❌ **不能确定**（如支付退款，必须查支付宝） → **自定义 Hook 模式**

---

### 复杂度对比

| 模式 | 代码量 | 配置复杂度 | 适用场景比例 |
|------|--------|-----------|-------------|
| **零 Hook** | 3 行 | 极简 | 80% |
| **自定义 Hook** | 30-50 行 | 中等 | 20% |

---

## 🚀 推荐实践

1. **优先使用零 Hook 模式**
   - 简单、快速、维护成本低
   - 80% 的场景够用

2. **只在必要时使用自定义 Hook**
   - 需要查询第三方状态
   - 需要复杂的状态判断
   - 需要成功后的额外处理

3. **如何决策？**
   ```
   我的场景 → 需要查第三方吗？
              ├─ 不需要 → 零 Hook ✓
              └─ 需要 → 自定义 Hook
   ```

---

## 📚 进阶阅读

- **零 Hook 详细指南**：[QUICK_START_ZERO_HOOK.md](QUICK_START_ZERO_HOOK.md)
- **SDK 完整文档**：[SDK_GUIDE.md](SDK_GUIDE.md)
- **架构设计（状态机）**：[ARCHITECTURE.md](ARCHITECTURE.md)

---

## ❓ 常见问题

**Q1: 我的场景到底要不要 Hook？**

A: 看这个：
```java
// 场景：调用下游接口
api.call();

// 问自己：如果这个方法超时，我能确定是否成功吗？
// - 能确定（查本地 DB） → 零 Hook
// - 不能确定（必须查下游） → 自定义 Hook
```

**Q2: Hook 的 3 个方法都必须实现吗？**

A: 是的，但可以简单实现：
```java
// 最简实现
public String checkStatus(...) { return "INIT"; }  // 总是重试
public QueryResult doQuery(...) { return QueryResult.success(); }  // 不查询
public void doCallback(...) { }  // 什么都不做
```

但这样就失去了 Hook 的意义，不如用零 Hook 模式。

**Q3: 零 Hook 模式有什么限制？**

A: 
- ❌ 不能查询第三方状态
- ❌ 不能在成功后做额外处理（如发通知）
- ✅ 但 80% 场景不需要这些功能

**Q4: 可以中途从零 Hook 升级到自定义 Hook 吗？**

A: 可以！步骤：
1. 实现 Hook 接口
2. 在管理后台配置 `hookClass`
3. 重启应用
4. 新任务会使用 Hook，旧任务不受影响

---

## 总结

**Hook = 探测器**

- **零 Hook**：简单场景，调用成功 = 任务完成
- **自定义 Hook**：复杂场景，需要查询第三方确认状态

**推荐**：优先用零 Hook，80% 场景够用！只在必要时才用自定义 Hook。

**记住**：能用零 Hook 就别用自定义 Hook，简单就是美！🎉
