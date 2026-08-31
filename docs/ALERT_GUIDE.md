# 告警配置指南

> **版本**: v1.0 | **更新日期**: 2026-08

ElecCloud 分布式重试平台提供了完善的多渠道告警机制，支持邮件、钉钉、企业微信等多种告警方式。

---

## 1. 快速开始

### 启用告警

在 `retry-server/src/main/resources/application.yml` 中配置：

```yaml
retry:
  alert:
    enabled: true  # 启用告警
    channels: EMAIL,DINGTALK  # 配置告警渠道
```

---

## 2. 支持的告警渠道

### 2.1 邮件告警

#### 配置步骤

**Step 1: 配置SMTP服务器**

```yaml
spring:
  mail:
    host: smtp.qq.com  # QQ邮箱
    port: 465
    username: your-email@qq.com
    password: your-auth-code  # QQ邮箱需要使用授权码
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
          ssl:
            enable: true
```

**常用邮箱SMTP配置**：

| 邮箱服务商 | SMTP地址 | 端口 | 说明 |
|-----------|---------|------|------|
| QQ邮箱 | smtp.qq.com | 465 | 需开启SMTP并获取授权码 |
| 163邮箱 | smtp.163.com | 465 | 需开启SMTP并获取授权码 |
| Gmail | smtp.gmail.com | 587 | 需开启"允许不够安全的应用" |
| 企业邮箱 | 咨询IT部门 | - | 通常需要内网访问 |

**Step 2: 配置收件人**

```yaml
retry:
  alert:
    enabled: true
    channels: EMAIL
    email:
      recipients:
        - admin@example.com
        - ops-team@example.com
      from-name: ElecCloud Alert
```

#### 邮件告警效果

```
主题: [CRITICAL] 死信任务突增告警

【ElecCloud 重试平台告警】

告警标题: 死信任务突增告警
告警级别: CRITICAL
告警时间: 2026-08-31 14:30:00
告警内容:
检测到死信任务数量异常增长！

统计时间: 2026-08-31 13:30:00 至 2026-08-31 14:30:00
失败任务数: 15
告警阈值: 10
超出比例: 50.0%

建议: 请立即检查系统日志和下游服务状态

---
此邮件由 ElecCloud 分布式重试平台自动发送
```

---

### 2.2 钉钉告警

#### 配置步骤

**Step 1: 创建钉钉机器人**

1. 进入钉钉群 → 群设置 → 智能群助手 → 添加机器人
2. 选择"自定义"机器人
3. 设置安全配置：
   - **方式1（推荐）**：加签验证（勾选后获得secret）
   - **方式2**：自定义关键词（如"告警"）
4. 复制Webhook地址

**Step 2: 配置告警参数**

```yaml
retry:
  alert:
    enabled: true
    channels: DINGTALK
    dingtalk:
      webhook: https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN
      secret: SEC1234567890abcdef  # 加签密钥（可选）
      keyword: 告警  # 自定义关键词（可选）
      at-mobiles:  # @指定人（可选）
        - "13800138000"
      at-all: false  # 是否@所有人
```

#### 钉钉告警效果

钉钉机器人会发送Markdown格式的消息：

```markdown
### 🚨 死信任务突增告警

> **告警级别**: CRITICAL
> **告警时间**: 2026-08-31 14:30:00

---

**告警内容**:

检测到死信任务数量异常增长！

统计时间: 2026-08-31 13:30:00 至 2026-08-31 14:30:00
失败任务数: 15
告警阈值: 10
超出比例: 50.0%

建议: 请立即检查系统日志和下游服务状态

---

ElecCloud 分布式重试平台
```

#### 安全配置说明

**加签验证（推荐）**：
- 更安全，防止webhook泄露后被滥用
- 需要配置 `secret` 参数
- SDK会自动计算签名

**自定义关键词**：
- 简单方便，但安全性较低
- 需要配置 `keyword` 参数
- 消息内容必须包含关键词

---

### 2.3 企业微信告警

#### 配置步骤

**Step 1: 创建企业微信机器人**

1. 进入企业微信群 → 群设置 → 群机器人 → 添加机器人
2. 设置机器人名称和头像
3. 复制Webhook地址

**Step 2: 配置告警参数**

```yaml
retry:
  alert:
    enabled: true
    channels: WECHAT
    wechat:
      webhook: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=YOUR_KEY
      mentioned-list:  # @指定人的userid（可选）
        - zhangsan
        - lisi
      mentioned-mobile-list:  # @指定人的手机号（可选）
        - "13800138000"
```

#### 企业微信告警效果

企业微信机器人会发送Markdown格式的消息：

```markdown
### 🚨 死信任务突增告警
> **告警级别**: CRITICAL
> **告警时间**: 2026-08-31 14:30:00

**告警内容**:
检测到死信任务数量异常增长！
...

ElecCloud 分布式重试平台
```

---

## 3. 告警规则配置

### 3.1 死信任务突增告警

当失败任务数量超过阈值时触发告警。

```yaml
retry:
  alert:
    threshold:
      enable-failed-task-alert: true  # 启用死信突增告警
      failed-tasks-per-hour: 10       # 阈值：10个/小时
      time-window-minutes: 60         # 统计窗口：60分钟
```

**触发条件**：
- 统计窗口内的失败任务数 > 阈值
- 默认每10分钟检查一次

**应用场景**：
- 下游服务宕机导致大量任务失败
- 网络故障导致批量超时
- 配置错误导致任务无法执行

---

### 3.2 执行失败率告警

当任务执行失败率超过阈值时触发告警。

```yaml
retry:
  alert:
    threshold:
      enable-failure-rate-alert: true  # 启用失败率告警
      failure-rate-percent: 20         # 阈值：20%
      time-window-minutes: 60          # 统计窗口：60分钟
```

**触发条件**：
- 失败次数 / 总执行次数 > 阈值
- 默认每15分钟检查一次

**应用场景**：
- 下游服务响应慢或不稳定
- 第三方API限流
- 网络抖动频繁

---

## 4. 多渠道配置

可以同时启用多个告警渠道：

```yaml
retry:
  alert:
    enabled: true
    channels: EMAIL,DINGTALK,WECHAT  # 同时启用三个渠道
    
    email:
      recipients:
        - admin@example.com
    
    dingtalk:
      webhook: https://oapi.dingtalk.com/robot/send?access_token=TOKEN1
    
    wechat:
      webhook: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=KEY1
```

**推荐配置**：
- **开发环境**：仅启用钉钉，发送到测试群
- **生产环境**：邮件 + 钉钉/企业微信，确保告警可达

---

## 5. 告警级别

系统支持4个告警级别：

| 级别 | 说明 | 使用场景 |
|------|------|----------|
| INFO | 一般信息 | 系统正常运行的通知 |
| WARN | 警告 | 需要关注但不紧急的问题 |
| ERROR | 错误 | 影响部分功能的问题 |
| CRITICAL | 严重 | 影响核心功能，需立即处理 |

**当前告警规则对应级别**：
- 死信任务突增：CRITICAL
- 执行失败率过高：ERROR

---

## 6. 测试告警

### 方法1：调用测试接口（推荐）

```bash
curl -X POST http://localhost:8080/api/admin/test-alert \
  -H "X-API-Key: demo-key-12345678" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "告警测试",
    "message": "这是一条测试告警消息",
    "level": "WARN"
  }'
```

### 方法2：触发真实告警

1. 修改阈值为极低值：
   ```yaml
   retry:
     alert:
       threshold:
         failed-tasks-per-hour: 1  # 降低到1
   ```

2. 提交几个必定失败的任务

3. 等待10分钟，观察告警

---

## 7. 故障排查

### 问题1：配置了告警但没有收到消息

**排查步骤**：

1. **检查告警是否启用**
   ```yaml
   retry.alert.enabled: true
   ```

2. **检查渠道配置**
   ```yaml
   retry.alert.channels: EMAIL  # 确认包含目标渠道
   ```

3. **查看日志**
   ```bash
   tail -f logs/retry-server.log | grep Alert
   ```

   正常日志示例：
   ```
   [AlertMonitor] Failed tasks spike detected: 15 tasks
   [DingTalkAlert] Alert sent successfully: 死信任务突增告警
   ```

4. **邮件特殊检查**
   - SMTP配置是否正确
   - 用户名/密码是否正确
   - 是否使用了授权码（QQ/163邮箱）
   - 查看邮件服务器连接日志

5. **钉钉特殊检查**
   - Webhook地址是否正确
   - 安全配置是否匹配（关键词/加签）
   - 机器人是否被移除群聊

---

### 问题2：告警发送失败

**常见错误**：

**邮件错误**：
```
Failed to send email alert: Authentication failed
```
解决：检查邮箱密码/授权码

**钉钉错误**：
```
{"errcode":310000,"errmsg":"sign not match"}
```
解决：检查secret配置是否正确

**企业微信错误**：
```
{"errcode":93000,"errmsg":"invalid webhook url"}
```
解决：检查webhook地址是否正确

---

### 问题3：告警太频繁

**解决方法**：

1. **调高阈值**
   ```yaml
   retry:
     alert:
       threshold:
         failed-tasks-per-hour: 50  # 从10调整到50
   ```

2. **增大统计窗口**
   ```yaml
   retry:
     alert:
       threshold:
         time-window-minutes: 120  # 从60分钟增加到120分钟
   ```

3. **临时禁用告警**
   ```yaml
   retry:
     alert:
       enabled: false  # 临时关闭
   ```

---

## 8. 最佳实践

### 8.1 分环境配置

**开发环境** (`application-dev.yml`):
```yaml
retry:
  alert:
    enabled: true
    channels: DINGTALK
    dingtalk:
      webhook: https://oapi.dingtalk.com/robot/send?access_token=DEV_TOKEN
      at-all: false
```

**生产环境** (`application-prod.yml`):
```yaml
retry:
  alert:
    enabled: true
    channels: EMAIL,DINGTALK
    email:
      recipients:
        - ops-oncall@example.com
    dingtalk:
      webhook: https://oapi.dingtalk.com/robot/send?access_token=PROD_TOKEN
      at-mobiles:
        - "13800138000"  # 值班人员手机号
```

---

### 8.2 告警分级响应

| 级别 | 响应时间 | 处理方式 |
|------|---------|---------|
| CRITICAL | 立即 | @值班人员，电话通知 |
| ERROR | 30分钟内 | @责任人，群内通知 |
| WARN | 工作时间内 | 记录日志，定期检查 |
| INFO | 无要求 | 仅记录 |

---

### 8.3 告警收敛

避免告警风暴，建议：

1. **设置合理阈值**：不要过于敏感
2. **增加告警间隔**：同一问题30分钟内不重复告警
3. **分时段调整**：夜间适当提高阈值
4. **业务分组**：不同场景配置不同阈值

---

## 9. 扩展开发

### 自定义告警渠道

实现 `AlertChannel` 接口：

```java
@Component
public class CustomAlertChannel implements AlertChannel {
    
    @Override
    public void send(String title, String message, AlertLevel level) {
        // 自定义发送逻辑
    }
    
    @Override
    public String getChannelName() {
        return "CUSTOM";
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
}
```

配置启用：
```yaml
retry:
  alert:
    channels: CUSTOM
```

---

## 10. 参考链接

- [部署运维手册](DEPLOYMENT.md)
- [监控指标说明](ARCHITECTURE.md#7-监控指标)
- [钉钉机器人文档](https://open.dingtalk.com/document/robots/custom-robot-access)
- [企业微信机器人文档](https://developer.work.weixin.qq.com/document/path/91770)

