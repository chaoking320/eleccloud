# 🧪 ElecCloud 测试指南 - 5 分钟体验重试流程

> **目标**：通过 Demo 接口，亲眼看到重试的完整过程

---

## 🎬 前置准备

确保服务已启动（参考截图，所有服务都是绿色）：

```bash
docker compose ps

# 应该看到 5 个服务全部正常运行：
# - eleccloud-mysql    (Up, 3306)
# - eleccloud-redis    (Up, 6379)
# - eleccloud-server   (Up, 8080)
# - eleccloud-admin    (Up, 8081)
# - eleccloud-example  (Up, 8082)
```

---

## 🚀 场景 1：支付退款（最简单）

### 步骤 1：触发退款

```bash
curl -X POST http://localhost:8082/business/trigger/refund
```

**预期返回**：
```json
{
  "transId": "RFD_A1B2C3D4",
  "status": "PENDING",
  "message": "支付宝接口返回失败，已提交重试任务",
  "adminUrl": "http://localhost:8081"
}
```

### 步骤 2：查看管理后台

1. 浏览器打开：http://localhost:8081
2. 进入"任务监控"页面
3. 找到刚才的 `transId`（如 RFD_A1B2C3D4）

你会看到：
- **状态**：INIT（等待重试）
- **重试次数**：0
- **下次重试时间**：5 秒后

### 步骤 3：等待重试（5 秒）

刷新管理后台，你会看到状态变化：

```
第 1 次重试（5 秒后）：
  → 状态：EXECUTING（执行中）
  → 调用 Mock 支付宝：第 2 次调用，还是失败
  → 状态：INIT（等待下次重试）
  → 重试次数：1
  → 下次重试时间：5 分钟后

第 2 次重试（5 分钟后）：
  → 调用 Mock 支付宝：第 3 次调用，成功！
  → 状态：SUCCESS ✅
  → 任务完成
```

### 步骤 4：验证结果

```bash
# 查询任务状态
curl "http://localhost:8082/mock-api/payment/refund/status?transId=RFD_A1B2C3D4"

# 返回：
{
  "transId": "RFD_A1B2C3D4",
  "status": "SUCCESS",
  "callCount": 3
}
```

---

## 📊 观察重试日志

### 查看 retry-server 日志

```bash
docker logs -f retry-server --tail 50
```

你会看到：
```
[INFO] RetryTaskController - 提交重试任务: transId=RFD_A1B2C3D4
[INFO] RetryTaskService - 保存任务到数据库: taskId=xxx, status=INIT
[INFO] RetryTaskController - 任务执行中: taskId=xxx
[INFO] RetryTaskController - 任务执行成功: taskId=xxx
```

### 查看 retry-example 日志（SDK 日志）

```bash
docker logs -f retry-example --tail 50
```

你会看到：
```
[INFO] RetryableTaskAspect - 方法执行失败，提交重试任务
[INFO] RetryMessageProducer - 延时消息已投递: delay=5000ms
[INFO] LocalRetryExecutor - 开始执行重试任务: taskId=xxx
[INFO] DemoRefundHook - checkStatus: transId=RFD_A1B2C3D4, status=INIT
[INFO] RefundBusinessService - 调用支付宝退款接口
[INFO] DemoRefundHook - doQuery: 查询支付宝状态
[INFO] DemoRefundHook - doCallback: 退款成功
```

---

## 🔍 查看 Redis 延时队列

```bash
# 进入 Redis 容器
docker exec -it retry-redis redis-cli

# 查看延时队列
> ZRANGE retry:client:delay:queue:payment.retry 0 -1 WITHSCORES

# 你会看到类似：
1) "{\"taskId\":\"xxx\",\"sceneType\":10}"
2) "1609459200"  ← score（过期时间戳）
```

**解释**：
- key: `retry:client:delay:queue:payment.retry`
- value: 任务信息（JSON）
- score: 过期时间戳（毫秒）

当前时间戳 >= score 时，消息被消费。

---

## 🎯 场景 2：结算（API 模式）

```bash
curl -X POST http://localhost:8082/business/trigger/settlement
```

流程与场景 1 类似，区别：
- 使用手动 API 调用 `retryClient.submit()`
- 不使用 `@RetryableTask` 注解

---

## 🔒 场景 3：库存同步（预提交模式）

```bash
curl -X POST http://localhost:8082/business/trigger/inventory
```

特点：
- **preSubmit = true**：方法执行前先注册任务
- 即使进程崩溃，任务也不会丢失

验证方法：
1. 触发接口后，立即重启 retry-example
   ```bash
   docker restart retry-example
   ```
2. 重启后，任务仍然会被重试（从 MySQL 恢复）

---

## 🧪 高级测试：调整失败次数

### 修改 Mock 失败次数

默认前 2 次失败，第 3 次成功。可以修改：

```bash
# 修改为前 5 次失败
curl -X POST "http://localhost:8082/mock-api/config?failTimes=5"

# 现在触发退款
curl -X POST http://localhost:8082/business/trigger/refund

# 需要重试 5 次才会成功
```

### 重置计数器

```bash
# 清空所有调用计数
curl -X POST http://localhost:8082/mock-api/reset

# 现在可以重新演示
```

---

## 📈 验证场景配置

### 查看场景配置

```bash
# 查看场景 10（退款）的配置
curl http://localhost:8080/api/scene/config/10

# 返回：
{
  "sceneType": 10,
  "sceneName": "电商退款场景",
  "backoffStrategy": "CUSTOM",
  "retryIntervals": "1,5,10,30,60",
  "maxRetryCount": 5
}
```

### 修改场景配置

在管理后台修改：
1. 访问 http://localhost:8081
2. 进入"场景配置"
3. 编辑场景 10
4. 修改重试间隔为 `1,2,3`（单位：分钟）
5. 保存

现在触发退款，重试间隔变为：1分钟、2分钟、3分钟。

---

## 🐛 常见问题

### Q1: 触发接口返回 500 错误

**检查**：
```bash
# 确认 retry-example 是否启动
docker logs retry-example --tail 20

# 常见原因：连接不到 retry-server
```

**解决**：
```bash
# 重启 retry-example
docker restart retry-example
```

---

### Q2: 任务一直是 INIT 状态，不重试

**检查**：
```bash
# 查看 Redis 延时队列是否有消息
docker exec -it retry-redis redis-cli
> ZRANGE retry:client:delay:queue:payment.retry 0 -1 WITHSCORES

# 查看 SDK 日志
docker logs retry-example --tail 50 | grep "LocalRetryExecutor"
```

**原因**：
1. Redis 连接失败
2. SDK 消费者线程异常

**解决**：
```bash
# 重启 retry-example
docker restart retry-example
```

---

### Q3: 管理后台看不到任务

**检查**：
```bash
# 确认任务是否保存到数据库
docker exec -it retry-mysql mysql -uroot -ppassword retry_platform

mysql> SELECT * FROM retry_task ORDER BY create_time DESC LIMIT 5;
```

**原因**：
1. retry-server 未启动
2. 数据库连接失败

---

## 📝 完整测试清单

- [ ] 场景 1：触发退款，观察重试流程
- [ ] 场景 2：触发结算，验证 API 模式
- [ ] 场景 3：触发库存同步，验证预提交模式
- [ ] 查看管理后台任务列表
- [ ] 查看任务详细信息和重试历史
- [ ] 修改场景配置，验证重试间隔变化
- [ ] 调整 Mock 失败次数，验证不同重试次数
- [ ] 查看 Redis 延时队列
- [ ] 查看数据库任务表
- [ ] 查看各服务日志

---

## 🎉 恭喜！

你已经完整体验了 ElecCloud 的重试流程！

**下一步**：
1. 在你的项目中接入 SDK
2. 创建自己的场景配置
3. 实现自定义 Hook（如果需要）
4. 部署到生产环境

**参考文档**：
- [完整部署指南](docs/QUICK_DOCKER_DEPLOY.md)
- [Example 详解](docs/EXAMPLE_EXPLAINED.md)
- [Hook 概念图解](docs/HOOK_EXPLAINED.md)
- [SDK 接入指南](docs/SDK_GUIDE.md)
