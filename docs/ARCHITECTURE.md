# ElecCloud — 架构设计文档

> **版本**: v3.0（MQ-SDK 驱动） | **更新日期**: 2026-07

---

## 1. 整体架构

### 1.1 核心设计思路

v3.0 的核心转变：**调度权从 Server 移交给 SDK**。

```
【旧架构 v2.0 — 中心化 HTTP 调度】

  业务系统                     retry-server
  ┌──────────────┐             ┌────────────────────────────┐
  │ 方法失败      │──提交任务──►│  RetryTaskScheduler         │
  │              │             │  （每 10s 扫描 Redis ZSET）  │
  │ RetryCallback│◄──HTTP回调─│  RetryTaskExecutor          │
  │ Controller   │             │  （主动 HTTP 调用业务服务）  │
  └──────────────┘             └────────────────────────────┘
  ⚠️ 问题：Server 需要主动连接业务服务，要求双向网络互通

【新架构 v3.0 — 去中心化 MQ-SDK 驱动】

  业务系统（内含 SDK）
  ┌──────────────────────────────────────────────────┐
  │  方法失败                                         │
  │    ↓ AOP 拦截                                    │
  │  RetryMessageProducer → 投递延时消息               │
  │    ↓ 延时到期                                    │
  │  MQ Consumer → LocalRetryExecutor（本地状态机）   │
  │    ↓ 需要持久化                                  │
  │  REST API → retry-server（仅做数据存取）           │
  └──────────────────────────────────────────────────┘
  ✅ 业务服务主动消费 MQ，Server 不再主动连接任何人
```

### 1.2 架构全图

```
┌─────────────────────────────────────────────────────────────┐
│                    业务系统（引入 retry-client-sdk）           │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────┐ │
│  │@RetryableTask│  │ RetryClient  │  │ @RetryableTask    │ │
│  │ 注解模式      │  │ API 模式      │  │ (preSubmit=true)  │ │
│  │ (失败后自动)  │  │ (手动提交)   │  │ 预提交模式         │ │
│  └──────┬───────┘  └──────┬───────┘  └────────┬──────────┘ │
│         └─────────────────┴───────────────────┘           │
│                           │ 任务提交/状态更新               │
│  ┌────────────────────────▼──────────────────────────────┐  │
│  │                  retry-client-sdk                      │  │
│  │                                                        │  │
│  │  RetryableTaskAspect  →  RetryMessageProducer          │  │
│  │        AOP 拦截              发送延时消息               │  │
│  │                                  ↓                    │  │
│  │              ┌───────────────────┤                    │  │
│  │              │                   │                    │  │
│  │    ┌─────────▼──────┐  ┌────────▼────────┐           │  │
│  │    │  Redis ZSET    │  │    RabbitMQ     │           │  │
│  │    │ 延时队列        │  │  延迟队列        │           │  │
│  │    │(mq-type=REDIS) │  │(mq-type=RABBITMQ│           │  │
│  │    └─────────┬──────┘  └────────┬────────┘           │  │
│  │              └─────────┬─────────┘                    │  │
│  │                        │ 到期消费                      │  │
│  │  ┌─────────────────────▼──────────────────────────┐   │  │
│  │  │           LocalRetryExecutor（本地状态机）        │   │  │
│  │  │  1. 从 Server 获取任务详情                       │   │  │
│  │  │  2. 加锁（CAS → EXECUTING）                    │   │  │
│  │  │  3. 调用 RetryHook.checkStatus()               │   │  │
│  │  │  4. INIT → 反射调用本地方法                     │   │  │
│  │  │     WAIT → RetryHook.doQuery()                 │   │  │
│  │  │     SUCCESS → RetryHook.doCallback()           │   │  │
│  │  │  5. 失败 → 计算退避延时，重新投递 MQ            │   │  │
│  │  └────────────────────────────────────────────────┘   │  │
│  └────────────────────────────────────────────────────────┘  │
│                           │ REST API (仅数据读写)             │
└───────────────────────────┼─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                     retry-server（任务存储中心）               │
│                                                             │
│  POST /api/retry/submit          - 提交新任务                │
│  GET  /api/retry/task/{id}       - 查询任务详情              │
│  POST /api/retry/executing/{id}  - CAS 抢占执行权            │
│  POST /api/retry/success/{id}    - 标记成功                  │
│  POST /api/retry/status          - 更新状态                  │
│  POST /api/retry/retry-info      - 更新重试次数              │
│  POST /api/retry/rollback        - 回滚到 INIT               │
│  POST /api/retry/failed          - 标记彻底失败              │
│  POST /api/retry/trigger/{id}    - 管理员手动触发            │
│                                                             │
│  ┌────────────────────┐     ┌──────────────────────────┐   │
│  │  SceneConfig 缓存  │     │  Prometheus 指标          │   │
│  │  场景退避策略       │     │  /actuator/prometheus      │   │
│  └────────────────────┘     └──────────────────────────┘   │
└──────────────────────────────────┬──────────────────────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    │                             │
               ┌────▼────┐                  ┌────▼────┐
               │  MySQL  │                  │  Redis  │
               │retry_task│                 │  ZSET   │
               │scene_cfg │                 │  Lock   │
               │fail_task │                 └─────────┘
               └──────────┘
```

---

## 2. 任务状态机

```
                     任务提交
                         │
                    ┌────▼────┐
                    │  INIT   │ ← 任务已注册，等待 MQ 触发首次执行
                    └────┬────┘
                         │ MQ 到期消费
                ┌────────▼────────┐
                │  EXECUTING     │ ← CAS 抢占，防多节点并发
                └────────┬────────┘
                         │
              ┌──────────▼──────────┐
              │  checkStatus()      │
              └──────────┬──────────┘
           ┌─────────────┼─────────────┐
         SUCCESS        INIT          WAIT
           │              │              │
    ┌──────▼──────┐  调用本地方法    doQuery()
    │  SUCCESS   │  (反射调用)         │
    └─────────────┘       │         ┌──┴───┐
                     ┌────▼────┐  成功  失败
                     │  WAIT   │    │     │
                     └─────────┘  回调  重新投递
                                        │
                               ┌────────▼────────┐
                               │  计算退避延时     │
                               │  重新入 MQ 队列  │
                               └────────┬─────────┘
                                        │
                              超出最大重试次数
                                        │
                                   ┌────▼────┐
                                   │ FAILED  │ → 写入 failed_task 死信表
                                   └─────────┘
```

---

## 3. 退避策略

| 策略 | 描述 | 计算公式 | 示例（base=2分钟）|
|------|------|----------|-----------------|
| **CUSTOM** | 手动配置间隔列表 | 按列表顺序取值 | `retry_intervals="2,5,10,30"` |
| **FIXED** | 固定间隔 | `base` | 2, 2, 2, 2 分钟 |
| **LINEAR** | 线性递增 | `n × base` | 2, 4, 6, 8 分钟 |
| **EXPONENTIAL** | 指数退避 | `base × 2^(n-1)` | 2, 4, 8, 16 分钟 |

### 选型建议

| 场景 | 推荐策略 | 原因 |
|------|----------|------|
| 支付/退款 | CUSTOM（1,5,10,30分钟） | 精确控制重试节奏，符合支付 SLA |
| 第三方 API | EXPONENTIAL（base=1）| 指数退避，防止请求风暴 |
| 内部系统同步 | LINEAR（base=1）| 均匀分布，便于预测 |
| 简单幂等操作 | FIXED（base=5）| 固定频率，实现简单 |

---

## 4. 业务线隔离

ElecCloud 通过 `queue-name` 配置实现多业务线的主题隔离：

### 4.1 Redis 模式隔离

```
支付线  queue-name: payment.retry
  └── ZSET Key: retry:client:delay:queue:payment.retry
  └── 轮询 Consumer: 只消费 payment.retry 的消息

物流线  queue-name: logistics.retry
  └── ZSET Key: retry:client:delay:queue:logistics.retry
  └── 轮询 Consumer: 只消费 logistics.retry 的消息
```

### 4.2 RabbitMQ 模式隔离

```
Exchange: retry.delay.exchange (direct)
  ├── RoutingKey: payment.retry  → Queue: payment.retry
  └── RoutingKey: logistics.retry → Queue: logistics.retry

各业务线 Consumer 只监听自己的 Queue，路由完全隔离
```

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
│ hook_class     VARCHAR│       │   INIT/EXECUTING/WAIT   │
│ enabled        TINYINT│       │   SUCCESS/FAILED         │
│ create_time    DATETIME│      │ submit_mode      VARCHAR │
│ update_time    DATETIME│      │   POST_FAIL/PRE_SUBMIT   │
└──────────────────────┘        │ retry_count      INT     │
                                │ max_retry_count  INT     │
                                │ backoff_strategy VARCHAR │
                                │ backoff_base     INT     │
                                │ retry_intervals  VARCHAR │
                                │ next_retry_time  BIGINT  │
                                │ create_time      DATETIME│
                                │ update_time      DATETIME│
                                └──────────────────────────┘

failed_task
┌──────────────────────────┐
│ id               BIGINT  │
│ task_id          VARCHAR │
│ scene_type       INT     │
│ idempotent_key   VARCHAR │
│ method_class     VARCHAR │
│ method_name      VARCHAR │
│ method_params    TEXT    │
│ retry_count      INT     │
│ fail_reason      TEXT    │
│ create_time      DATETIME│
│ fail_time        DATETIME│
└──────────────────────────┘
```

### 5.2 关键索引

- `retry_task`：`(scene_type, idempotent_key)` 唯一索引，防止重复提交
- `retry_task`：`(task_status, next_retry_time)` 复合索引，加速查询
- `failed_task`：`(fail_time)` 索引，支持时间范围查询死信

---

## 6. 幂等性保障

1. **提交层**：`(scene_type, idempotent_key)` 唯一约束，相同业务键只有一条任务
2. **执行层**：`markExecuting` CAS 操作，多节点同时消费同一 MQ 消息时只有一个能成功抢占
3. **业务层**：每次执行前调用 `checkStatus()`，业务返回 `SUCCESS` 时立即跳过

---

## 7. 监控指标

基于 Micrometer + Prometheus，暴露以下指标：

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `retry.active.tasks.count` | Gauge | 当前活跃重试任务数 |
| `retry.failed.tasks.count` | Gauge | 当前死信任务数 |
| `retry.tasks.submitted.total` | Counter | 按场景统计提交总数 |
| `retry.tasks.executed.total` | Counter | 按场景和结果统计执行总数 |
| `retry.tasks.execution.duration` | Timer | 任务执行耗时分布 |

访问地址：`http://retry-server:8080/actuator/prometheus`

---

## 8. 模块依赖关系

```
retry-client-sdk  ←──── retry-example
                                │
                          retry-server（HTTP REST）

retry-admin  ←──────── 独立后台，通过 HTTP 对接 retry-server
```
