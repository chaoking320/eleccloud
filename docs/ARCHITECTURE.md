# 分布式重试平台 — 架构设计文档

> **版本**: v2.0 | **更新日期**: 2026-07

---

## 1. 项目概述

分布式重试平台是一个专门处理分布式系统中**异步任务重试**问题的中间件平台。它对标专利 CN116662445A（数据同步中间件），为业务系统提供：

- **零代码入侵**的失败自动重试能力
- **可配置**的退避策略（CUSTOM/FIXED/LINEAR/EXPONENTIAL）
- **幂等性**保障（相同任务不重复执行）
- **状态追踪**和可视化运维管理

---

## 2. 整体架构

```
┌────────────────────────────────────────────────────────────┐
│                    业务系统（业务方应用）                      │
│                                                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐ │
│  │ @RetryableTask│  │ RetryClient  │  │ RetryableTask    │ │
│  │ 注解模式       │  │ API 模式      │  │ (preSubmit=true) │ │
│  │ (AOP自动拦截)  │  │ (手动提交)    │  │ 预提交模式        │ │
│  └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘ │
│         │                 │                   │           │
│  ┌──────┴─────────────────┴───────────────────┴─────────┐  │
│  │          retry-client-sdk（Spring Boot Starter）       │  │
│  │  • RetryClient      - HTTP 调用重试服务端               │  │
│  │  • RetryableTaskAspect - AOP 拦截注解方法              │  │
│  │  • RetryCallbackController - 供服务端回调              │  │
│  │  • RetryHook 接口    - 业务方实现状态回查逻辑            │  │
│  └───────────────────────────┬──────────────────────────┘  │
└──────────────────────────────┼─────────────────────────────┘
                               │ HTTP (REST API)
                               │
┌──────────────────────────────┼─────────────────────────────┐
│             retry-server（重试调度中心）                      │
│                               │                            │
│  ┌────────────────────────────▼─────────────────────────┐  │
│  │              RetryTaskController                      │  │
│  │   POST /api/retry/submit     - 接收任务提交            │  │
│  │   POST /api/retry/success/{id} - 标记成功(预提交模式)  │  │
│  │   POST /api/retry/cancel/{id} - 取消任务              │  │
│  └────────────────────────────────────────────────────┬─┘  │
│                                                        │    │
│  ┌─────────────────────────────────────────────────────▼─┐  │
│  │              RetryTaskScheduler（调度核心）              │  │
│  │   • 每 10s 扫描 Redis ZSET 中到期任务                   │  │
│  │   • Redisson 分布式锁（防多实例重复调度）                 │  │
│  │   • Redis 不可用时降级扫描 MySQL                        │  │
│  └────────────────────────────────────────────────────┬─┘  │
│                                                        │    │
│  ┌─────────────────────────────────────────────────────▼─┐  │
│  │              RetryTaskExecutor（执行引擎）               │  │
│  │   1. 获取任务 + 场景配置                                 │  │
│  │   2. HTTP 回调客户端 checkStatus → 判断是否跳过          │  │
│  │   3. INIT → executeMethod / WAIT → doQuery             │  │
│  │   4. 根据 BackoffStrategy 计算下次时间                   │  │
│  │   5. 超出次数/时间阈值 → 转入 failed_task               │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │  SceneConfig  │  │ DelayQueue   │  │ RetryMetrics     │  │
│  │ 场景配置+缓存  │  │ Redis ZSET   │  │ Prometheus 指标  │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────┬──────────────────────────────────┘
                          │
         ┌────────────────┴────────────────┐
         │                                 │
    ┌────▼────┐                      ┌─────▼────┐
    │  MySQL  │                      │  Redis   │
    │retry_task│                     │ ZSet     │
    │scene_cfg │                     │ Lock     │
    │fail_task │                     │ Cache    │
    └──────────┘                     └──────────┘
```

---

## 3. 核心流程

### 3.1 任务生命周期状态机

```
              ┌──────────────────────────────────┐
              │           任务提交                │
              └──────────────┬───────────────────┘
                             │
                        ┌────▼────┐
                        │  INIT   │ ← 任务已注册，等待首次执行
                        └────┬────┘
                             │
                   ┌─────────▼────────┐
                   │  checkStatus()   │  → SUCCESS → 直接标记成功
                   └─────────┬────────┘
                             │ INIT
                             │
              ┌──────────────▼──────────────────┐
              │           executeMethod()         │ 调用客户端执行原方法
              └──────────────┬──────────────────┘
                     ┌───────┴────────┐
                   成功              失败/WAIT
                     │                │
               ┌─────▼─────┐    ┌─────▼─────┐
               │  SUCCESS  │    │   WAIT    │ ← 已发出请求，等待第三方确认
               └───────────┘    └─────┬─────┘
                                      │
                             ┌────────▼──────────┐
                             │    doQuery()       │ 主动查询第三方状态
                             └────────┬──────────┘
                                ┌─────┴──────┐
                              成功           失败
                                │             │
                         ┌──────▼──────┐  调度下次重试
                         │ doCallback()│  (退避策略)
                         └──────┬──────┘
                                │
                          ┌─────▼─────┐
                          │  SUCCESS  │
                          └───────────┘

  若超过 maxRetryCount 或 maxRetryDuration:
                          ┌───────────┐
                          │  FAILED   │ → 写入 failed_task 表（死信）
                          └───────────┘
```

### 3.2 调度核心流程

```
RetryTaskScheduler（每 10 秒执行）
    │
    ├─ 1. 尝试获取全局分布式锁（Redisson）
    │      └─ 获取失败 → 跳过（其他节点正在处理）
    │
    ├─ 2. 从 Redis ZSET 取出 score ≤ now 的到期任务
    │      └─ Redis 不可用 → 降级查询 MySQL（retry_task WHERE next_retry_time ≤ now）
    │
    ├─ 3. 对每个 taskId 尝试加任务级分布式锁
    │      └─ 获取失败 → 跳过（避免并行重复执行同一任务）
    │
    └─ 4. 提交到 ThreadPoolExecutor → RetryTaskExecutor.execute(taskId)
```

---

## 4. 退避策略引擎

### 4.1 四种策略对比

| 策略 | 描述 | 计算公式 | 示例（base=2分钟）|
|------|------|----------|------------------|
| **CUSTOM** | 手动配置间隔列表 | 按列表顺序取值 | retry_intervals="2,5,10,30" |
| **FIXED** | 固定间隔 | base | 2, 2, 2, 2分钟 |
| **LINEAR** | 线性递增 | (n+1) × base | 2, 4, 6, 8, 10分钟 |
| **EXPONENTIAL** | 指数退避 | base × 2^n | 2, 4, 8, 16, 32分钟 |

> `n` 表示当前重试次数（从 0 开始）

### 4.2 时间阈值（maxRetryDuration）

除次数阈值外，平台还支持**时间阈值**：

```
maxRetryDuration = 3600 （秒，即 1 小时）
```

任务创建超过 1 小时后，即使重试次数未耗尽，也将终止重试并写入 `failed_task` 表。

设置为 `0` 表示不限制时长，仅按次数控制。

### 4.3 选型建议

| 场景 | 推荐策略 | 原因 |
|------|----------|------|
| 支付/退款 | CUSTOM（1,5,10,30分钟）| 精确控制重试节奏，符合支付SLA |
| 第三方API调用 | EXPONENTIAL（base=1）| 避免同时重试造成的请求风暴 |
| 内部系统同步 | LINEAR（base=1）| 均匀分布，便于预测重试时间 |
| 简单幂等操作 | FIXED（base=5）| 固定频率，实现简单 |

---

## 5. 数据库设计

### 5.1 ER 图

```
scene_config                    retry_task
┌──────────────────────┐        ┌──────────────────────────┐
│ id             BIGINT│        │ id               BIGINT  │
│ scene_type     INT   │◄──────►│ scene_type       INT     │
│ scene_name     VARCHAR│       │ task_id          VARCHAR │
│ retry_intervals VARCHAR│      │ idempotent_key   VARCHAR │
│ max_retry_count INT  │        │ method_class     VARCHAR │
│ backoff_strategy VARCHAR│     │ method_name      VARCHAR │
│ backoff_base   INT   │        │ method_params    TEXT    │
│ max_retry_duration INT│       │ task_status      VARCHAR │
│ hook_class     VARCHAR│       │   INIT/WAIT/SUCCESS/FAILED│
│ client_app_url VARCHAR│       │ submit_mode      VARCHAR │
│ enabled        TINYINT│       │   POST_FAIL/PRE_SUBMIT   │
│ create_time    DATETIME│      │ retry_count      INT     │
│ update_time    DATETIME│      │ max_retry_count  INT     │
└──────────────────────┘        │ next_retry_time  BIGINT  │
                                │ create_time      DATETIME│
                                │ update_time      DATETIME│
                                └──────────────────────────┘

retry_history                    failed_task
┌──────────────────────┐        ┌──────────────────────────┐
│ id             BIGINT│        │ id               BIGINT  │
│ task_id        VARCHAR│       │ task_id          VARCHAR │
│ retry_count    INT   │        │ scene_type       INT     │
│ execute_time   DATETIME│      │ idempotent_key   VARCHAR │
│ execute_result VARCHAR│       │ method_class     VARCHAR │
│ error_message  TEXT  │        │ method_name      VARCHAR │
│ cost_time      INT   │        │ method_params    TEXT    │
│ create_time    DATETIME│      │ retry_count      INT     │
└──────────────────────┘        │ fail_reason      TEXT    │
                                │ create_time      DATETIME│
                                │ fail_time        DATETIME│
                                └──────────────────────────┘
```

### 5.2 索引策略

- `retry_task`：`(scene_type, idempotent_key)` 唯一索引，防止重复提交
- `retry_task`：`(task_status, next_retry_time)` 复合索引，加速到期任务查询
- `failed_task`：`(fail_time)` 索引，支持按时间范围查询死信任务

---

## 6. 高可用设计

### 6.1 Redis 故障降级

```
正常模式：Redis ZSET 存储延迟队列
    ↓（Redis 不可用时自动降级）
降级模式：MySQL 轮询（每60秒扫描一次 next_retry_time ≤ NOW() 的任务）
```

### 6.2 分布式锁策略

- **全局扫描锁**：`retry:scan:lock` — 防止多个 Server 实例同时扫描（同一时刻只有一个节点扫描）
- **任务执行锁**：`retry:task:lock:{taskId}` — 防止同一任务被并发执行（任务级别互斥）

### 6.3 幂等性保障

- 任务提交：`(scene_type, idempotent_key)` 唯一约束，相同业务键只能存在一条任务
- 任务执行：每次执行前调用 `checkStatus`，业务方返回 `SUCCESS` 时跳过本次执行

---

## 7. 监控指标

基于 Micrometer + Prometheus，暴露以下指标：

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `retry.active.tasks.count` | Gauge | 当前活跃重试任务数 |
| `retry.failed.tasks.count` | Gauge | 当前失败（死信）任务数 |
| `retry.tasks.submitted.total{sceneType}` | Counter | 按场景统计提交总数 |
| `retry.tasks.executed.total{sceneType,result}` | Counter | 按场景和结果统计执行总数 |
| `retry.tasks.execution.duration{sceneType}` | Timer | 任务执行耗时分布 |

访问地址：`http://retry-server:8080/actuator/prometheus`

---

## 8. 模块依赖关系

```
retry-client-sdk  ←──── retry-example
       ↑                      │
       └─────────────────────►│ retry-server
                                     │
                               retry-admin（独立，通过HTTP对接retry-server）
```
