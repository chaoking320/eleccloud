# Retry Client SDK

客户端SDK模块，提供注解和API两种接入方式。

## 功能特性

- **注解模式**：通过 `@RetryableTask` 注解自动拦截方法异常并创建重试任务
- **API模式**：通过 `RetryClient` 接口显式提交重试任务
- **自动配置**：Spring Boot Starter 自动配置，开箱即用
- **开发模式**：支持本地开发模式，仅记录日志不实际发送任务

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.retry.platform</groupId>
    <artifactId>retry-client-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置文件

```yaml
retry:
  client:
    server-url: http://localhost:8080  # 重试服务端地址
    enabled: true                       # 是否启用
    dev-mode: false                     # 开发模式
    connect-timeout: 5000               # 连接超时(ms)
    read-timeout: 10000                 # 读取超时(ms)
```

### 3. 使用注解模式

```java
@Service
public class RefundService {
    
    @RetryableTask(
        sceneType = 1,              // 场景类型
        idempotentKey = "transId",  // 幂等键参数名
        async = true,               // 异步提交
        throwException = false      // 失败时不抛异常
    )
    public void refund(String transId, BigDecimal amount) {
        // 调用远程退款接口
        remoteRefundApi.refund(transId, amount);
    }
}
```

### 4. 使用API模式

```java
@Service
public class OrderService {
    
    @Autowired
    private RetryClient retryClient;
    
    public void processOrder(Order order) {
        try {
            // 处理订单
            processOrderLogic(order);
        } catch (Exception e) {
            // 手动提交重试任务
            RetryTaskRequest request = new RetryTaskRequest();
            request.setSceneType(2);
            request.setIdempotentKey(order.getOrderId());
            request.setMethodClass("com.example.OrderService");
            request.setMethodName("processOrder");
            request.setMethodParams(JsonUtil.toJson(order));
            
            String taskId = retryClient.submit(request);
            log.info("Retry task submitted: {}", taskId);
        }
    }
}
```

## 核心组件

### 注解

- `@RetryableTask`：标注在方法上，自动拦截异常并创建重试任务

### API接口

- `RetryClient`：重试客户端接口
  - `submit(RetryTaskRequest)`：提交重试任务
  - `cancel(String taskId)`：取消重试任务
  - `queryTask(String taskId)`：查询任务状态

### 工具类

- `JsonUtil`：JSON序列化工具
- `ParameterExtractor`：参数提取工具，支持从方法参数或对象字段中提取幂等键

## 配置说明

| 配置项 | 说明 | 默认值 |
|-------|------|--------|
| retry.client.server-url | 重试服务端地址 | http://localhost:8080 |
| retry.client.enabled | 是否启用客户端 | true |
| retry.client.dev-mode | 开发模式（仅记录日志） | false |
| retry.client.connect-timeout | HTTP连接超时(ms) | 5000 |
| retry.client.read-timeout | HTTP读取超时(ms) | 10000 |

## 开发模式

开启开发模式后，重试任务不会实际发送到服务端，仅记录日志，方便本地开发调试：

```yaml
retry:
  client:
    dev-mode: true
```

## 幂等键提取

支持两种方式提取幂等键：

1. **直接参数**：幂等键作为方法参数
```java
@RetryableTask(sceneType = 1, idempotentKey = "transId")
public void refund(String transId, BigDecimal amount) { }
```

2. **对象字段**：幂等键在参数对象的字段中
```java
@RetryableTask(sceneType = 1, idempotentKey = "orderId")
public void processOrder(Order order) { }  // order.orderId
```
