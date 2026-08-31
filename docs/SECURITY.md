# 安全配置指南

> **版本**: v1.0 | **更新日期**: 2026-08

ElecCloud 分布式重试平台提供了 API Key 鉴权机制，保护服务端接口免受未授权访问。

---

## 1. 快速开始

### 服务端配置

在 `retry-server/src/main/resources/application.yml` 中配置：

```yaml
retry:
  security:
    enabled: true  # 启用API鉴权（生产环境必须为true）
    api-keys: your-secret-key-12345678,backup-key-87654321  # 支持多个密钥，逗号分隔
    whitelist: /actuator/**,/error  # 白名单路径，不需要鉴权
```

### 客户端配置

在业务应用的 `application.yml` 中配置：

```yaml
retry:
  client:
    server-url: http://retry-server:8080
    api-key: your-secret-key-12345678  # 与服务端配置的密钥之一匹配
```

---

## 2. API Key 传递方式

客户端可以通过以下两种方式传递 API Key：

### 方式1：HTTP Header（推荐）

```bash
curl -X POST http://localhost:8080/api/retry/submit \
  -H "X-API-Key: your-secret-key-12345678" \
  -H "Content-Type: application/json" \
  -d '{"sceneType":1,"idempotentKey":"ORDER_123"}'
```

### 方式2：Query Parameter

```bash
curl -X POST "http://localhost:8080/api/retry/submit?apiKey=your-secret-key-12345678" \
  -H "Content-Type: application/json" \
  -d '{"sceneType":1,"idempotentKey":"ORDER_123"}'
```

**优先级**：Header > Query Parameter

---

## 3. 白名单配置

以下路径默认不需要鉴权：

- `/actuator/**` - Prometheus 监控端点
- `/error` - 错误页面

如需添加其他路径到白名单，修改配置：

```yaml
retry:
  security:
    whitelist: /actuator/**,/error,/health,/public/**
```

支持两种匹配模式：
- `/**` - 前缀匹配（如 `/actuator/**` 匹配所有以 `/actuator/` 开头的路径）
- `/exact` - 精确匹配（如 `/error` 只匹配 `/error` 路径）

---

## 4. 多密钥管理

支持配置多个 API Key，适用于以下场景：

### 场景1：多业务线隔离

```yaml
retry:
  security:
    api-keys: payment-key-abc123,logistics-key-def456,marketing-key-ghi789
```

- 支付团队使用 `payment-key-abc123`
- 物流团队使用 `logistics-key-def456`
- 营销团队使用 `marketing-key-ghi789`

### 场景2：密钥轮换

```yaml
# 步骤1：添加新密钥（保留旧密钥）
api-keys: old-key-12345678,new-key-87654321

# 步骤2：客户端逐步切换到新密钥

# 步骤3：所有客户端切换完成后，移除旧密钥
api-keys: new-key-87654321
```

---

## 5. 开发环境配置

### 临时关闭鉴权（仅开发环境）

```yaml
retry:
  security:
    enabled: false  # 关闭鉴权，所有请求直接放行
```

> ⚠️ **警告**：生产环境必须设置 `enabled: true`！

### 使用弱密钥（仅开发环境）

```yaml
retry:
  security:
    enabled: true
    api-keys: dev-key-123456  # 开发环境可使用简单密钥
```

---

## 6. 密钥生成建议

### 强密钥生成（推荐）

```bash
# Linux/macOS
openssl rand -base64 32

# 或使用 uuidgen
uuidgen | tr -d '-'

# 输出示例：
# k7H9mP2vX8nQ5tL4wY1jR6uS3bC0dF9aE7gZ
```

### 密钥格式要求

- 长度：至少 20 个字符
- 字符集：字母、数字、短横线
- 避免使用：空格、特殊符号（除了短横线）
- 不要使用：明文密码、可预测的字符串

---

## 7. 鉴权失败处理

### 返回格式

```json
{
  "success": false,
  "message": "Unauthorized: Invalid or missing API Key"
}
```

### HTTP 状态码

- `401 Unauthorized` - API Key 无效或缺失

### 日志记录

服务端会记录未授权的访问尝试：

```
[Security] Unauthorized access attempt: uri=/api/retry/submit, ip=192.168.1.100, apiKey=****5678
```

---

## 8. 安全最佳实践

### ✅ 建议

1. **生产环境必须启用鉴权**
   ```yaml
   retry.security.enabled: true
   ```

2. **使用强密钥**
   - 至少 32 个字符
   - 随机生成，不要使用可预测的字符串

3. **定期轮换密钥**
   - 建议每 6 个月更换一次
   - 使用多密钥支持平滑过渡

4. **密钥存储安全**
   - 不要将密钥提交到版本控制系统
   - 使用环境变量或配置中心
   ```bash
   export RETRY_API_KEY="your-secret-key"
   ```
   ```yaml
   retry:
     security:
       api-keys: ${RETRY_API_KEY}
   ```

5. **限制网络访问**
   - 配合防火墙规则，只允许特定IP访问
   - 使用 VPC 内网访问

6. **监控异常访问**
   - 定期检查日志中的 401 错误
   - 设置告警规则，异常访问超过阈值时触发

### ❌ 禁止

1. **不要在代码中硬编码密钥**
   ```java
   // ❌ 错误示例
   String apiKey = "my-api-key-123456";
   ```

2. **不要在日志中打印完整密钥**
   - 系统已自动脱敏显示

3. **不要在 URL 中传递密钥（如果会被记录到访问日志）**
   - 优先使用 Header 方式

4. **不要使用弱密钥**
   - 避免：`123456`, `password`, `test`, `demo`

---

## 9. 集成示例

### Spring Boot 应用接入

```yaml
# application.yml
retry:
  client:
    server-url: http://retry-server:8080
    api-key: ${RETRY_API_KEY:default-dev-key}  # 支持环境变量覆盖
```

SDK 会自动在所有请求中添加 API Key Header。

### 直接 HTTP 调用

```java
RestTemplate restTemplate = new RestTemplate();
HttpHeaders headers = new HttpHeaders();
headers.set("X-API-Key", "your-secret-key-12345678");
headers.setContentType(MediaType.APPLICATION_JSON);

HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
ResponseEntity<String> response = restTemplate.postForEntity(
    "http://retry-server:8080/api/retry/submit",
    entity,
    String.class
);
```

---

## 10. 故障排查

### 问题1：客户端收到 401 错误

**原因**：
- API Key 配置错误
- API Key 未传递
- 服务端未配置该密钥

**解决方法**：
1. 检查客户端配置：`retry.client.api-key`
2. 检查服务端配置：`retry.security.api-keys`
3. 确认密钥完全匹配（区分大小写）
4. 查看服务端日志中的脱敏密钥（前4位+后4位）

### 问题2：服务端启动后所有请求都被拒绝

**原因**：
- 启用了鉴权但未配置密钥
- 配置格式错误

**解决方法**：
```yaml
# 检查配置格式
retry:
  security:
    enabled: true
    api-keys: key1,key2  # 逗号分隔，不要有空格
```

### 问题3：监控端点无法访问

**原因**：
- 监控路径不在白名单中

**解决方法**：
```yaml
retry:
  security:
    whitelist: /actuator/**,/error  # 确保包含 /actuator/**
```

---

## 11. 升级指南

### 从旧版本（无鉴权）升级

1. **更新配置文件**
   ```yaml
   retry:
     security:
       enabled: true  # 新增
       api-keys: your-generated-key  # 新增
   ```

2. **更新客户端配置**
   ```yaml
   retry:
     client:
       api-key: your-generated-key  # 新增
   ```

3. **重启服务**
   - 先重启客户端应用（添加API Key配置）
   - 再重启服务端（启用鉴权）

4. **验证**
   ```bash
   # 测试鉴权是否生效
   curl -X GET http://localhost:8080/api/retry/task/test-123
   # 应返回 401 Unauthorized
   
   curl -X GET http://localhost:8080/api/retry/task/test-123 \
     -H "X-API-Key: your-generated-key"
   # 应正常返回数据
   ```

---

## 12. 参考链接

- [SDK 接入指南](SDK_GUIDE.md)
- [部署运维手册](DEPLOYMENT.md)
- [架构设计](ARCHITECTURE.md)

