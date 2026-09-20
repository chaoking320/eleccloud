# ElecCloud 快速 Docker 部署指南

> **目标受众**：希望将 retry-server 和 retry-admin 部署到 Docker，本地业务系统通过 SDK 接入的开发者  
> **部署时间**：15-20 分钟  
> **前置要求**：Docker Desktop (Windows) 或 Docker Engine (Linux/macOS)，JDK 17+，Maven 3.6+

---

## 📋 部署架构

```
┌─────────────────────────────────────────────────────┐
│               Docker 容器环境                        │
│                                                     │
│  ┌──────────┐  ┌──────────┐  ┌─────────┐          │
│  │  MySQL   │  │  Redis   │  │ Server  │          │
│  │  :3306   │  │  :6379   │  │ :8080   │          │
│  └──────────┘  └──────────┘  └─────────┘          │
│                                    │                │
│                              ┌─────────┐           │
│                              │ Admin   │           │
│                              │ :8081   │           │
│                              └─────────┘           │
└─────────────────────────────────────────────────────┘
                                    ▲
                                    │ HTTP API
                                    │
                    ┌───────────────┴───────────────┐
                    │     本地业务系统 (SDK 接入)     │
                    │  retry-client-sdk 依赖        │
                    └───────────────────────────────┘
```

**说明**：
- **MySQL** 和 **Redis** 作为基础依赖运行在 Docker 中
- **retry-server** (8080) - 重试任务的存储和调度中心
- **retry-admin** (8081) - Web 管理后台
- **你的业务系统** 运行在本地，通过 SDK 连接到 Docker 中的 Server

---

## 🚀 一键部署步骤

### 步骤 1：环境检查

确保以下端口空闲（可根据需要修改）：

```bash
# Windows PowerShell
netstat -ano | findstr "3306 6379 8080 8081"

# Linux/macOS
lsof -i :3306 -i :6379 -i :8080 -i :8081
```

如果端口被占用，可以：
1. 停止占用端口的服务
2. 或修改 `docker-compose.simple.yml` 中的端口映射

---

### 步骤 2：编译项目

在项目根目录执行：

```bash
# Windows PowerShell
cd <eleccloud 项目目录>
mvn clean package -DskipTests

# Linux/macOS
cd /path/to/eleccloud
mvn clean package -DskipTests
```

**预期输出**：
```
[INFO] retry-server ................................. SUCCESS
[INFO] retry-admin .................................. SUCCESS
[INFO] retry-client-sdk ............................. SUCCESS
[INFO] BUILD SUCCESS
```

**产物验证**：
```bash
# 检查 jar 包是否生成
ls retry-server/target/*.jar
ls retry-admin/target/*.jar
```

---

### 步骤 3：启动 Docker 服务

使用简化版 docker-compose 配置（仅包含 Server + Admin）：

```bash
# 启动所有服务（MySQL、Redis、Server、Admin）
docker compose -f docker-compose.simple.yml up -d --build
```

**首次启动会**：
1. 拉取 MySQL 8.0 和 Redis 6 镜像（约 2-3 分钟）
2. 自动执行数据库初始化脚本（创建表结构）
3. 构建并启动 retry-server 和 retry-admin

---

### 步骤 4：验证部署成功

#### 4.1 检查容器状态

```bash
docker compose -f docker-compose.simple.yml ps
```

**预期输出**：所有服务状态为 `Up` (running)

```
NAME                     STATUS    PORTS
retry-mysql              Up        0.0.0.0:3306->3306/tcp
retry-redis              Up        0.0.0.0:6379->6379/tcp
retry-server             Up        0.0.0.0:8080->8080/tcp
retry-admin              Up        0.0.0.0:8081->8081/tcp
```

#### 4.2 检查服务健康状态

```bash
# Server 健康检查
curl http://localhost:8080/actuator/health

# 预期返回
{"status":"UP"}
```

#### 4.3 访问管理后台

浏览器打开：**http://localhost:8081**

你应该能看到 ElecCloud 管理后台首页。

---

## 🔧 本地业务系统 SDK 接入

### 步骤 1：添加 SDK 依赖

在你的本地 Spring Boot 项目中添加依赖：

```xml
<dependency>
    <groupId>com.retry.platform</groupId>
    <artifactId>retry-client-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

> ⚠️ **注意**：由于项目尚未发布到 Maven Central，你需要先在 eleccloud 项目根目录执行 `mvn install`，将 SDK 安装到本地 Maven 仓库。

---

### 步骤 2：配置 SDK

在你的 `application.yml` 中添加配置：

```yaml
retry:
  client:
    # Docker 中的 retry-server 地址
    server-url: http://localhost:8080
    
    # 启用重试功能
    enabled: true
    
    # MQ 类型：REDIS（推荐，零额外依赖）或 RABBITMQ
    mq-type: REDIS
    
    # 业务线标识（用于多业务线隔离）
    queue-name: your-business.retry
    
    # 开发模式（可选，true 时仅打日志不实际提交任务）
    dev-mode: false

# Redis 配置（SDK 需要连接 Redis 消费延时消息）
spring:
  redis:
    host: localhost
    port: 6379
    database: 2
```

**配置说明**：
- `server-url`: 指向 Docker 中的 retry-server
- `queue-name`: 你的业务线唯一标识，如 `payment.retry`、`order.retry` 等
- `redis.database`: 使用独立的 Redis database（建议 2），避免与业务缓存冲突

---

### 步骤 3：在管理后台创建场景

1. 访问 http://localhost:8081
2. 进入"场景配置"页面
3. 点击"新建场景"
4. 填写场景信息：

| 字段 | 示例值 | 说明 |
|------|--------|------|
| 场景类型 | 1001 | 唯一数字标识 |
| 场景名称 | 支付退款 | 描述性名称 |
| 退避策略 | CUSTOM | 推荐使用 CUSTOM 自定义间隔 |
| 重试间隔 | 1,5,10,30,60 | 单位：分钟 |
| 最大重试次数 | 5 | 超出后进入死信队列 |
| Hook 类名 | （留空使用零 Hook 模式） | 简单场景可留空 |

5. 点击"保存"

---

### 步骤 4：业务代码接入

#### 方式 A：零 Hook 模式（推荐，80% 场景适用）

适用于方法执行成功即认为任务完成的场景：

```java
@Service
public class PaymentService {
    
    @Autowired
    private AlipayApi alipayApi;
    
    /**
     * 支付退款 - 失败自动重试
     * 
     * @param transId 交易流水号（必须全局唯一）
     * @param amount 退款金额
     */
    @RetryableTask(
        sceneType = 1001,           // 与管理后台创建的场景类型一致
        idempotentKey = "#transId"  // 幂等键（SpEL 表达式）
    )
    public void refund(String transId, Double amount) {
        // 直接调用第三方接口
        // 如果抛出异常，会自动进入重试流程
        alipayApi.refund(transId, amount);
        
        log.info("退款成功: transId={}, amount={}", transId, amount);
    }
}
```

**使用**：
```java
// 正常调用
paymentService.refund("TXN20261231001", 100.0);

// 如果失败：
// 1. 第1次重试：1分钟后
// 2. 第2次重试：5分钟后
// 3. 第3次重试：10分钟后
// ...直到成功或达到最大重试次数
```

---

#### 方式 B：自定义 Hook 模式（需要查询第三方状态）

适用于需要主动查询第三方系统确认状态的场景（如支付、订单等）：

**1. 实现 RetryHook 接口**：

```java
@Component("com.your.company.PaymentRefundHook")  // Bean 名称必须是全限定类名
public class PaymentRefundHook implements RetryHook {
    
    @Autowired
    private AlipayApi alipayApi;
    
    @Autowired
    private PaymentDao paymentDao;
    
    /**
     * 检查本地状态（每次重试前调用）
     */
    @Override
    public String checkStatus(RetryContext context) {
        String transId = (String) context.getParams().get("transId");
        
        // 查询本地数据库
        String localStatus = paymentDao.getRefundStatus(transId);
        
        if ("SUCCESS".equals(localStatus)) {
            return "SUCCESS";  // 已完成，跳过重试
        } else if ("PROCESSING".equals(localStatus)) {
            return "WAIT";     // 等待确认，触发 doQuery()
        } else {
            return "INIT";     // 未完成，执行重试
        }
    }
    
    /**
     * 查询第三方状态（checkStatus 返回 WAIT 时调用）
     */
    @Override
    public QueryResult doQuery(RetryContext context) {
        String transId = (String) context.getParams().get("transId");
        
        // 主动查询支付宝退款状态
        try {
            AlipayRefundResponse response = alipayApi.queryRefund(transId);
            
            if (response.isSuccess()) {
                return QueryResult.success(response.getRefundNo());
            } else {
                return QueryResult.failure(response.getMessage());
            }
        } catch (Exception e) {
            return QueryResult.failure("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 成功回调（doQuery 确认成功后调用）
     */
    @Override
    public void doCallback(RetryContext context, QueryResult result) {
        String transId = (String) context.getParams().get("transId");
        String refundNo = (String) result.getData();
        
        // 更新本地状态
        paymentDao.updateRefundStatus(transId, "SUCCESS", refundNo);
        
        // 发送通知
        notificationService.sendRefundNotification(transId);
        
        log.info("退款确认成功: transId={}, refundNo={}", transId, refundNo);
    }
}
```

**2. 在管理后台配置 Hook 类名**：

编辑场景 1001，将 `hookClass` 设置为：`com.your.company.PaymentRefundHook`

**3. 业务代码使用（与零 Hook 模式一致）**：

```java
@RetryableTask(sceneType = 1001, idempotentKey = "#transId")
public void refund(String transId, Double amount) {
    alipayApi.refund(transId, amount);
}
```

---

### 步骤 5：监控和管理

#### 5.1 查看任务状态

登录管理后台 http://localhost:8081：

1. **任务监控** - 查看所有重试任务的实时状态
2. **失败任务** - 查看死信队列中的失败任务
3. **场景配置** - 管理重试策略
4. **手动触发** - 对失败任务进行手动重试

#### 5.2 查看 Prometheus 指标

访问 http://localhost:8080/actuator/prometheus 查看监控指标：

```
# 活跃任务数
retry.active.tasks.count

# 失败任务数
retry.failed.tasks.count

# 任务提交总数（按场景分组）
retry.tasks.submitted.total{scene_type="1001"}

# 任务执行总数（按结果分组）
retry.tasks.executed.total{scene_type="1001",result="success"}
```

---

## 📊 完整的测试流程

### 测试 1：正常重试流程

```java
// 1. 调用业务方法（模拟失败）
paymentService.refund("TXN20261231001", 100.0); // 假设第三方接口超时

// 2. 查看管理后台
// 访问 http://localhost:8081 -> 任务监控
// 可以看到任务状态为 INIT，等待第一次重试

// 3. 等待 1 分钟后
// 任务自动重试，如果成功则状态变为 SUCCESS

// 4. 查看日志
docker logs -f retry-server
```

---

### 测试 2：死信任务处理

```java
// 1. 修改场景配置，将最大重试次数设置为 2 次

// 2. 调用业务方法（确保会失败）
paymentService.refund("TXN20261231002", 100.0);

// 3. 等待两次重试后（1分钟 + 5分钟 = 6分钟）
// 任务进入死信队列

// 4. 在管理后台查看
// 访问 http://localhost:8081 -> 失败任务
// 可以看到失败原因和详细信息

// 5. 手动触发重试（如果下游服务已恢复）
// 点击"重新触发"按钮
```

---

## 🔧 常用运维命令

### 查看日志

```bash
# 查看 Server 日志
docker logs -f retry-server

# 查看 Admin 日志
docker logs -f retry-admin

# 查看 MySQL 日志
docker logs -f retry-mysql

# 查看所有服务日志
docker compose -f docker-compose.simple.yml logs -f
```

### 重启服务

```bash
# 重启所有服务
docker compose -f docker-compose.simple.yml restart

# 仅重启 Server
docker compose -f docker-compose.simple.yml restart retry-server

# 仅重启 Admin
docker compose -f docker-compose.simple.yml restart retry-admin
```

### 停止服务

```bash
# 停止所有服务（保留数据）
docker compose -f docker-compose.simple.yml down

# 停止并删除数据（⚠️ 会清空数据库）
docker compose -f docker-compose.simple.yml down -v
```

### 数据库管理

```bash
# 进入 MySQL 容器
docker exec -it retry-mysql mysql -uroot -ppassword

# 查看重试任务
USE retry_platform;
SELECT * FROM retry_task ORDER BY create_time DESC LIMIT 10;

# 查看失败任务
SELECT * FROM failed_task ORDER BY fail_time DESC LIMIT 10;

# 查看场景配置
SELECT * FROM scene_config;
```

---

## ❌ 常见问题排查

### 问题 1：容器启动失败

**现象**：`docker compose ps` 显示服务 `Exited`

**排查**：
```bash
# 查看具体错误日志
docker logs retry-server
docker logs retry-admin
```

**常见原因**：
1. **编译失败** - jar 包不存在
   - 解决：回到步骤 2 重新编译
   
2. **端口被占用**
   - 解决：修改 `docker-compose.simple.yml` 中的端口映射

3. **MySQL 未就绪** - Server 启动时 MySQL 还在初始化
   - 解决：等待 30 秒后执行 `docker compose restart retry-server`

---

### 问题 2：本地 SDK 连接不上 Server

**现象**：业务系统日志报错 `Connection refused: localhost:8080`

**排查**：
```bash
# 测试 Server 是否可访问
curl http://localhost:8080/actuator/health
```

**常见原因**：
1. **Server 未启动** - 检查容器状态
2. **端口映射错误** - 确认 `docker-compose.simple.yml` 端口配置
3. **防火墙拦截** - 临时关闭防火墙测试

---

### 问题 3：任务提交成功但不重试

**现象**：管理后台能看到任务，但状态一直是 INIT

**排查步骤**：

1. **检查 Redis 连接**：
```bash
# 本地业务系统能否连接 Redis
redis-cli -h localhost -p 6379 PING
```

2. **检查 MQ 消费者**：
```yaml
# 确认配置正确
retry:
  client:
    mq-type: REDIS
    queue-name: your-business.retry  # 不要包含特殊字符
```

3. **查看 SDK 日志**：
```
# 应该能看到类似日志
[INFO] RetryMessageProducer - 延时消息已投递: taskId=xxx, delay=60000ms
[INFO] LocalRetryExecutor - 开始执行重试任务: taskId=xxx
```

---

### 问题 4：重试逻辑没有执行

**现象**：方法抛异常但没有进入重试

**排查**：

1. **检查注解**：
```java
// ✅ 正确
@RetryableTask(sceneType = 1001, idempotentKey = "#transId")
public void refund(String transId, Double amount) {
    // 必须抛出异常
    throw new RuntimeException("支付失败");
}

// ❌ 错误 - 吞掉了异常
@RetryableTask(sceneType = 1001, idempotentKey = "#transId")
public void refund(String transId, Double amount) {
    try {
        api.call();
    } catch (Exception e) {
        log.error("失败", e);  // 不会触发重试！
    }
}
```

2. **检查场景配置**：
   - 登录管理后台，确认 sceneType = 1001 的场景存在且 enabled = true

---

### 问题 5：Docker Desktop 内存不足

**现象**：容器启动慢或频繁重启

**解决**：
1. 打开 Docker Desktop -> Settings -> Resources
2. 将 Memory 调整到至少 4GB
3. 点击 Apply & Restart

---

## 🎯 性能调优建议

### 1. Redis 连接池优化

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 8    # 最大连接数
        max-idle: 8      # 最大空闲连接
        min-idle: 2      # 最小空闲连接
```

### 2. Server 应用调优

编辑 `docker-compose.simple.yml`：

```yaml
retry-server:
  environment:
    - JAVA_OPTS=-Xms512m -Xmx1g -XX:+UseG1GC
```

### 3. 数据库连接池

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20     # 最大连接数
      minimum-idle: 5           # 最小空闲连接
      connection-timeout: 30000 # 连接超时 30s
```

---

## 📚 进阶配置

### 1. 配置告警（邮件/钉钉）

编辑 `retry-server/src/main/resources/application.yml`：

```yaml
retry:
  alert:
    enabled: true
    channels: DINGTALK
    dingtalk:
      webhook: https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN
      secret: YOUR_SECRET
    threshold:
      failed-tasks-per-hour: 10  # 每小时失败任务超过 10 个告警
```

重新编译并重启：
```bash
mvn clean package -pl retry-server -am -DskipTests
docker compose -f docker-compose.simple.yml up -d --build retry-server
```

---

### 2. 配置 API Key 鉴权

编辑 `retry-server/src/main/resources/application.yml`：

```yaml
retry:
  security:
    enabled: true
    api-keys: your-secret-key-32-chars-min
```

本地 SDK 配置：
```yaml
retry:
  client:
    api-key: your-secret-key-32-chars-min
```

---

### 3. 数据归档（自动清理历史数据）

```yaml
retry:
  archive:
    enabled: true
    history-retention-days: 90    # 历史记录保留 90 天
    success-task-retention-days: 30  # 成功任务保留 30 天
```

---

## 📦 附录：简化 Docker Compose 配置

如果原项目没有 `docker-compose.simple.yml`，可以创建一个：

```yaml
# docker-compose.simple.yml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: retry-mysql
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: retry_platform
      TZ: Asia/Shanghai
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./db/init.sql:/docker-entrypoint-initdb.d/init.sql
    command: --default-authentication-plugin=mysql_native_password
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-ppassword"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - retry-network

  redis:
    image: redis:6-alpine
    container_name: retry-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5
    networks:
      - retry-network

  retry-server:
    build:
      context: ./retry-server
      dockerfile: Dockerfile
    container_name: retry-server
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/retry_platform?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2B8
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=password
      - SPRING_REDIS_HOST=redis
      - SPRING_REDIS_REDISSON_CONFIG=singleServerConfig:\n  address: "redis://redis:6379"\n  database: 2
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
    restart: unless-stopped
    networks:
      - retry-network

  retry-admin:
    build:
      context: ./retry-admin
      dockerfile: Dockerfile
    container_name: retry-admin
    ports:
      - "8081:8081"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/retry_platform?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2B8
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=password
    depends_on:
      mysql:
        condition: service_healthy
    restart: unless-stopped
    networks:
      - retry-network

volumes:
  mysql-data:
  redis-data:

networks:
  retry-network:
    driver: bridge
```

---

## ✅ 总结

本文档提供了一个**最简化、最实用**的 Docker 部署方案：

1. ✅ **15-20 分钟完成部署** - 从编译到启动
2. ✅ **Server + Admin 容器化** - 隔离环境，便于管理
3. ✅ **本地 SDK 接入** - 业务系统无需容器化
4. ✅ **完整的测试流程** - 从提交到重试到监控
5. ✅ **详细的故障排查** - 覆盖常见问题

**下一步建议**：
- 熟悉管理后台的使用
- 尝试不同的退避策略
- 配置告警和监控
- 阅读进阶文档（ARCHITECTURE.md、SDK_GUIDE.md）

**遇到问题**？
- 查看本文"常见问题排查"章节
- 查看容器日志：`docker logs retry-server`
- 检查管理后台任务详情

祝你使用愉快！🎉
