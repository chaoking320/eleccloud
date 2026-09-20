# 🚀 ElecCloud 5 分钟快速开始

> **最快路径**：从零到运行第一个重试任务

---

## 前置要求

- ✅ Docker Desktop (Windows) 或 Docker Engine (Linux/macOS)
- ✅ JDK 17 或 21
- ✅ Maven 3.6+

---

## 第 1 步：编译项目（2 分钟）

```bash
cd <eleccloud 项目目录>
mvn clean package -DskipTests
```

**成功标志**：看到 `BUILD SUCCESS`

---

## 第 2 步：启动 Docker 服务（3 分钟）

```bash
docker compose -f docker-compose.simple.yml up -d --build
```

**成功标志**：
```bash
docker compose -f docker-compose.simple.yml ps

# 应该看到 4 个服务都是 Up 状态
```

---

## 第 3 步：验证部署

### 检查 Server

```bash
curl http://localhost:8080/actuator/health
# 返回: {"status":"UP"}
```

### 访问管理后台

浏览器打开：**http://localhost:8081**

---

## 第 4 步：创建你的第一个场景

1. 在管理后台点击"场景配置" → "新建场景"
2. 填写：
   - 场景类型：`1001`
   - 场景名称：`我的第一个重试场景`
   - 退避策略：`CUSTOM`
   - 重试间隔：`1,5,10`（分钟）
   - 最大重试次数：`3`
   - Hook 类名：留空（使用零 Hook 模式）
3. 点击"保存"

---

## 第 5 步：本地项目接入

### 5.1 安装 SDK 到本地仓库

```bash
cd <eleccloud 项目目录>
mvn install -DskipTests
```

### 5.2 在你的项目中添加依赖

```xml
<dependency>
    <groupId>com.retry.platform</groupId>
    <artifactId>retry-client-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 5.3 配置 application.yml

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

### 5.4 使用注解

```java
@Service
public class MyService {
    
    @RetryableTask(sceneType = 1001, idempotentKey = "#orderId")
    public void processOrder(String orderId) {
        // 你的业务逻辑
        // 如果抛出异常，会自动重试（1分钟、5分钟、10分钟）
        externalApi.call(orderId);
    }
}
```

---

## 🎉 完成！

现在你可以：
- 调用 `myService.processOrder("ORDER001")`
- 在管理后台查看任务状态
- 模拟失败，观察自动重试

---

## 📚 下一步

- **完整部署指南**：[docs/QUICK_DOCKER_DEPLOY.md](docs/QUICK_DOCKER_DEPLOY.md)
- **SDK 接入指南**：[docs/SDK_GUIDE.md](docs/SDK_GUIDE.md)
- **架构设计**：[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

---

## ❓ 遇到问题？

### Server 启动失败

```bash
# 查看日志
docker logs retry-server

# 常见原因：MySQL 还在初始化
# 解决：等待 30 秒后重启
docker compose -f docker-compose.simple.yml restart retry-server
```

### 本地 SDK 连接不上

```bash
# 确认 Server 可访问
curl http://localhost:8080/actuator/health

# 确认 Redis 可访问
redis-cli -h localhost -p 6379 PING
```

### 任务不重试

1. 检查管理后台场景是否已创建
2. 检查 `sceneType` 是否匹配
3. 检查方法是否抛出了异常
4. 查看 SDK 日志确认消息已投递

---

## 🛠️ 常用命令

```bash
# 停止所有服务
docker compose -f docker-compose.simple.yml down

# 重启服务
docker compose -f docker-compose.simple.yml restart

# 查看日志
docker logs -f retry-server
docker logs -f retry-admin

# 进入数据库
docker exec -it retry-mysql mysql -uroot -ppassword retry_platform
```

---

**祝你使用愉快！** 🎉

有问题查看 [完整部署文档](docs/QUICK_DOCKER_DEPLOY.md) 或提 Issue。
