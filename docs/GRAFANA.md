# Grafana 监控面板

ElecCloud 的 `retry-server` 已通过 Micrometer 暴露 Prometheus 指标（见 [架构设计文档 - 监控指标](ARCHITECTURE.md#7-监控指标)），地址为：

```
http://retry-server:8080/actuator/prometheus
```

本目录提供配套 Grafana Dashboard 配置文件：`deploy/grafana/dashboard.json`。

## 面板包含

| 面板 | 指标 | 说明 |
|------|------|------|
| 活跃重试任务数 | `retry_active_tasks_count` | 当前待处理任务总量 |
| 死信/失败任务数 | `retry_failed_tasks_count` | 超出最大重试次数进入死信的任务量 |
| 任务提交速率 | `retry_tasks_submitted_total` | 按 `sceneType` 统计的提交 QPS |
| 任务执行结果 | `retry_tasks_executed_total` | 按 `sceneType` / `result` 统计执行结果分布 |
| 平均执行耗时 | `retry_tasks_execution_duration_seconds` | 任务执行平均耗时（秒） |

## 接入步骤

1. 在 Grafana 中配置 Prometheus 数据源（指向 `retry-server` 的 `/actuator/prometheus`）。
2. 导入面板：
   - **方式 A（Provisioning）**：将 `deploy/grafana/dashboard.json` 放入 Grafana 的 `provisioning/dashboards` 目录，并在 `datasources` 中把 `uid` 与模板变量 `${DS_PROMETHEUS}` 对齐。
   - **方式 B（UI 导入）**：Grafana → Dashboards → Import → 上传 `dashboard.json`，在弹窗中选择你的 Prometheus 数据源。
3. 导入时若提示选择数据源，请选择上面配置的 Prometheus 数据源。

## 指标命名说明

Micrometer 会将指标名中的 `.` 转换为 `_`，并在 Prometheus 中以下划线形式暴露，例如：

- `retry.active.tasks.count` → `retry_active_tasks_count`
- `retry.tasks.submitted.total` → `retry_tasks_submitted_total`（含 `sceneType` 标签）
- `retry.tasks.executed.total` → `retry_tasks_executed_total`（含 `sceneType`、`result` 标签）
- `retry.tasks.execution.duration` → `retry_tasks_execution_duration_seconds_*`（Timer 直方图，单位为秒）
