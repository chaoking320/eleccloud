# 分布式重试平台 (Distributed Retry Platform)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-blue.svg)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-6.x-red.svg)](https://redis.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-orange.svg)](https://www.mysql.com/)

分布式重试平台是一个高可靠、高内聚的分布式异步重试中间件。本项目深度借鉴并对标专利 `CN116662445A`（一种数据同步中间件），在此基础上实现了更灵活、企业级的退避策略引擎、预提交安全注册模式，并提供完善的可视化管理后台与全方位监控指标。

---

## 🚀 v2.0 核心亮点

- 📈 **全场景退避策略**：内置 4 种退避算法（`CUSTOM` 自定义列表、`FIXED` 固定间隔、`LINEAR` 线性递增、`EXPONENTIAL` 指数退避），完美契合专利要求的指数倍增策略。
- ⏱️ **次数+时间双重阈值**：不仅限制重试次数，新增 `maxRetryDuration` 属性支持限制任务最大生存时间，过期自动标记为死信。
- 🛡️ **预提交保护模式 (PRE_SUBMIT)**：`@RetryableTask(preSubmit=true)` 支持方法执行前注册任务（INIT 状态），执行成功后标记 SUCCESS。对于资金扣减、库存同步等强一致性场景，能确保进程中途崩溃后，仍可安全重试，保障**最终一致性**。
- 🔍 **智能幂等与状态回查**：核心 `RetryHook` 具备 `checkStatus` 和 `doQuery` 两阶段检查机制，重试前先查询本地状态，彻底防止重复消费与资金资损。
- 📊 **指标监控 (Actuator/Prometheus)**：天然集成 Prometheus 抓取端点，支持针对积压数、死信量、重试耗时进行全方位指标埋点。

---

## 📂 项目模块结构

```
distributed-retry-platform/
├── retry-server/           # 重试中心：负责 Redis ZSET 延迟队列扫描、分布式锁控制、调度执行
├── retry-client-sdk/       # SDK组件：AOP切面拦截、Rest API客户端、状态回调路由
├── retry-admin/            # 管理后台：管理场景配置（退避策略/地址/Hook类）、任务监控查询
├── retry-example/          # Demo应用：包含退款（注解）、结算（API）、库存（预提交）三种模式演示
├── db/                     # 数据库配置
│   └── init.sql            # MySQL 表结构与 Demo 种子数据
└── docs/                   # 技术文档目录
    ├── ARCHITECTURE.md     # 架构设计与流程状态机文档
    ├── SDK_GUIDE.md        # 5分钟快速接入与场景详解开发指南
    └── DEPLOYMENT.md       # 生产化部署与监控接入手册
```

---

## 📖 技术文档与接入指引

为了帮助你更深入地了解和使用该平台，请参阅以下详细文档：

1. **[架构设计文档](file:///d:/Workspace/mine/github/eleccloud/docs/ARCHITECTURE.md)**：包含核心状态机图、分布式锁机制、Redis 降级策略、高可用架构图及库表 ER 图。
2. **[SDK 接入开发指南](file:///d:/Workspace/mine/github/eleccloud/docs/SDK_GUIDE.md)**：包含 5 分钟上手教程、三类接入模式对比、配置说明及 `RetryHook` 开发规范。
3. **[部署与运维手册](file:///d:/Workspace/mine/github/eleccloud/docs/DEPLOYMENT.md)**：包含 Docker Compose 一键启动、手动二进制部署脚本、高并发调优及 Prometheus 监控配置。

---

## ⚡ 快速开始 (5分钟)

### 方式一：Docker Compose 一键构建 (推荐)

在根目录下直接执行：
```bash
docker-compose up -d --build
```
启动成功后，会自动初始化数据库脚本（`db/init.sql`），并运行所有服务：
- 服务端端口: `8080`
- 管理后台端口: `8081`
- 样例 Demo 端口: `8082`

### 方式二：手动开发调试
1. 导入 `db/init.sql` 到 MySQL (8.0+)。
2. 本地启动 Redis (6.x+)。
3. 在根目录执行 Maven 编译打包：
   ```bash
   mvn clean install
   ```
4. 依次以 Spring Boot App 形式运行 `retry-server`、`retry-admin` 与 `retry-example`。

---

## 🎯 Demo 场景测试

本项目的 `retry-example` 模块使用**电商退款与订单域**作为统一场景，提供了 3 种模式的演示接口。你可通过访问以下接口，在本地控制台和 Admin 后台观察重试轨迹：

### 1. 注解模式（失败后自动重试）
- **接口**：`GET http://localhost:8082/api/demo/mode1/refund?amount=100.0`
- **策略**：CUSTOM 策略（间隔 1、5、10、30 分钟）
- **轨迹**：首次执行故意抛超时异常，被 AOP 拦截并注册到重试中心。1分钟后平台定时触发回调，更新本地状态为 `WAIT`。之后定时执行 `doQuery`，重试1次确认扣减成功后，执行 `doCallback` 更新状态为 `SUCCESS`。

### 2. API 手动模式（精确代码控制）
- **接口**：`GET http://localhost:8082/api/demo/mode2/settlement?amount=5000.0`
- **策略**：LINEAR 线性策略（每次间隔递增 2 分钟，2, 4, 6...分钟）
- **轨迹**：`SettlementService` 手动通过 API `retryClient.submit()` 提交。平台使用线性策略自动进行多轮状态回查，并在 OTA 最终审批通过后执行成功逻辑。

### 3. 预提交安全模式（强一致性保障）
- **接口**：`GET http://localhost:8082/api/demo/mode3/inventory?delta=10`
- **策略**：EXPONENTIAL 指数策略（重试时间按 `1*2^n` 递增，如 1, 2, 4, 8, 16 分钟）
- **轨迹**：在扣减库存前，切面提前向服务端注册 INIT 状态任务。成功则标记 SUCCESS；失败或遇到应用宕机，该任务仍在服务端保持为 INIT 状态，平台将以指数退避算法对其进行安全重试，重试前先查询本地状态以防重复扣减。

可通过访问 `GET http://localhost:8082/api/demo/status` 实时查看这三个演示场景在本地模拟数据库中的最新状态。
