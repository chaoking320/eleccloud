# 更新日志 (Changelog)

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)（SemVer）。

## [1.0.0] - 2026-07-29

### 新增 (Added)
- 去中心化 MQ-SDK 驱动架构：`LocalRetryExecutor` 在业务进程内驱动状态机与重试，Server 仅作数据存储。
- 双 MQ 延时引擎：Redis ZSET（零依赖）与 RabbitMQ（高可靠）可配置切换。
- 三种接入模式：注解模式（`@RetryableTask`）、API 模式、预提交模式（PRE_SUBMIT）。
- 四种退避策略：`CUSTOM` / `FIXED` / `LINEAR` / `EXPONENTIAL`，含 24h 上限保护。
- 两阶段幂等保障：`checkStatus` → `doQuery`，适配支付等场景。
- 可视化管理后台（Vue 3 + Vite）：场景配置、任务监控、手动触发。
- Prometheus 监控指标：活跃任务数、失败任务数、提交/执行计数、执行耗时。
- 开源工程化补充：MIT LICENSE、GitHub Actions CI、单元测试、贡献指南、Issue/PR 模板、Grafana 面板。

### 已知限制 (Known Limitations)
- 集成测试（`RetryPlatformIntegrationTest`）依赖 Redis / MySQL，需要本地中间件才能跑，CI 中默认跳过。
- SDK 尚未发布到 Maven Central，接入文档中的 `<dependency>` 待正式发布后可用。
- 管理后台缺少系统截图，README 待补充。
