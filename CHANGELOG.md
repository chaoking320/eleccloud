# Changelog

## [1.0.0] - 2026-10-22

### Added
- **Maven Central Release**: Published `io.github.chaoking320:retry-client-sdk:1.0.0` and parent POM to Maven Central
- **Two-Tier Hybrid Retry**: Fast in-memory pod retry (`localRetryTimes` / `localIntervalMs`) escalating seamlessly to persistent distributed delay queue retry
- **Core Retry Engine**: Declarative `@RetryableTask` AOP interceptor, programmatic `RetryClient` API, and crash-resilient `PRE_SUBMIT` mode
- **4 Backoff Strategies**: `CUSTOM`, `FIXED`, `LINEAR`, `EXPONENTIAL` with task-level and scene-level persistence
- **Dual MQ Engines**: Redis ZSET (with atomic Lua claim script) and RabbitMQ (`x-delayed-message`)
- **Standalone Mode**: Zero-server-dependency SDK mode using local MySQL/H2 database
- **Visual Admin Console**: Vue 3 + Element Plus dashboard with bilingual i18n, real-time metrics, scene configuration, and DLQ (`failed_task`) management
- **Out-of-the-Box Docker Compose**: Multi-stage root `Dockerfile` for one-command startup (`docker compose up -d --build`)

### Security & Reliability Hardening
- Enforced strict reflection whitelist (`@RetryableTask`, registered `submit()` methods, and business `@Service` beans) in `LocalRetryExecutor`
- Guaranteed "persist `SUCCESS` first, invoke `doCallback` second" ordering with isolated callback exception handling
- Reactivated terminated (`SUCCESS` / `FAILED`) tasks on subsequent failures sharing the same `(sceneType, idempotentKey)`
- Enhanced `DatabaseFallbackScheduler` to recover both `INIT` and `WAIT` tasks and scan across multi-application delay queues (`retry:client:delay:queue:*`)
- Hardened Admin token authentication with constant-time comparison and TTL session management
