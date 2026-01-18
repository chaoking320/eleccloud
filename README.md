# 分布式重试平台 (Distributed Retry Platform)

## 项目简介

分布式重试平台是一个通用的异步重试中间件，用于处理远程服务调用失败后的延时重试场景，支持可配置的重试策略、幂等性保证、以及可视化管理后台。

## 技术栈

- **框架**: Spring Boot 2.7.x
- **数据库**: MySQL 8.0
- **缓存**: Redis 6.x
- **分布式锁**: Redisson
- **持久层**: MyBatis
- **监控**: Prometheus + Micrometer
- **构建工具**: Maven 3.8+
- **JDK**: 1.8+

## 项目结构

```
distributed-retry-platform/
├── retry-server/           # 重试服务端
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── mapper/
│   │   └── test/
│   └── pom.xml
├── retry-client-sdk/       # 客户端SDK
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   │       └── META-INF/
│   │   │           └── spring.factories
│   │   └── test/
│   └── pom.xml
├── retry-admin/            # 管理后台
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── mapper/
│   │   └── test/
│   └── pom.xml
├── db/
│   └── init.sql           # 数据库初始化脚本
└── pom.xml                # 父POM
```

## 快速开始

### 1. 环境准备

- JDK 1.8+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.x+

### 2. 数据库初始化

```bash
mysql -u root -p < db/init.sql
```

### 3. 配置修改

修改各模块的 `application.yml` 文件，配置数据库和Redis连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/retry_platform
    username: your_username
    password: your_password
  redis:
    host: localhost
    port: 6379
```

### 4. 编译项目

```bash
mvn clean install
```

### 5. 启动服务

启动 retry-server:
```bash
cd retry-server
mvn spring-boot:run
```

启动 retry-admin:
```bash
cd retry-admin
mvn spring-boot:run
```

## 模块说明

### retry-server

重试服务端，核心功能包括：
- 任务调度和执行
- 延时队列管理
- 状态检查和智能重试
- 分布式锁保证
- 监控指标暴露

默认端口: 8080

### retry-client-sdk

客户端SDK，提供：
- @RetryableTask 注解支持
- AOP切面自动拦截
- RetryClient API接口
- Spring Boot Starter自动配置

### retry-admin

管理后台，提供：
- 场景配置管理
- 任务监控和查询
- 失败任务管理
- 系统配置

默认端口: 8081

## 核心特性

- ✅ 注解和API两种接入方式
- ✅ 可配置的重试策略
- ✅ 幂等性保证
- ✅ 智能状态检查
- ✅ 分布式任务调度
- ✅ 可视化管理后台
- ✅ Prometheus监控集成
- ✅ 钩子接口扩展机制

## 开发文档

详细的开发文档请参考 `.kiro/specs/distributed-retry-platform/` 目录：
- `requirements.md` - 需求文档
- `design.md` - 设计文档
- `tasks.md` - 实现任务列表

## License

MIT License
