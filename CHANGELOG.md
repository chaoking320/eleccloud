# Changelog

## [1.0.0] - 2026-09-13

### Added
- Core retry engine with @RetryableTask annotation
- 4 backoff strategies: CUSTOM, FIXED, LINEAR, EXPONENTIAL
- Dual MQ channels: Redis ZSET with Lua atomic claim / RabbitMQ
- Visual admin dashboard (Vue 3 + Element Plus)
- Prometheus metrics integration
- Multi-channel alerting (Email, DingTalk, WeChat)
- ShedLock distributed scheduler lock
- Standalone mode (zero server dependency)
- API key authentication

### Fixed
- Redis ZSET range(0,-1) OOM vulnerability
- JSON serialization framework inconsistency
- Transaction + Redis long transaction issue
- Timeout scanner race condition
- Admin API unauthenticated access
- Frontend/backend Result structure mismatch

### Improved
- RetryStatus enum replacing string literals
- RetryClient interface segregation
- Caffeine cache for scene config
- Adaptive poll interval for Redis consumer
- Nested SpEL support in idempotentKey
