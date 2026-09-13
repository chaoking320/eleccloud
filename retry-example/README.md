# Retry Example

This module demonstrates the usage of the ElecCloud distributed retry platform with three distinct scenarios.

## 场景说明 (Example Scenarios)

1. **注解模式 (Annotation Mode)**
   - **URL**: `GET /api/demo/mode1/refund?amount=100`
   - **说明**: Demonstrates the easiest way to integrate. When a method fails, the `@RetryableTask` annotation automatically intercepts the exception and submits the retry task to the platform.

2. **API 模式 (API Mode)**
   - **URL**: `GET /api/demo/mode2/settlement?amount=5000`
   - **说明**: Demonstrates manual submission using the SDK API. Useful for scenarios where you need fine-grained control over when and how a retry task is created (e.g., catching specific timeout exceptions and submitting manually using a linear backoff strategy).

3. **预提交模式 (Pre-submit Mode)**
   - **URL**: `GET /api/demo/mode3/inventory?delta=10`
   - **说明**: Demonstrates the pre-submit pattern for guaranteed delivery. A task is registered in an `INIT` state before the actual business logic executes. Even if the process crashes midway, the retry platform can recover the task and ensure it eventually succeeds, guaranteeing eventual consistency.

You can view the current state of all three scenarios by visiting:
- **URL**: `GET /api/demo/status`

## 快速启动 (Quick Start)

1. **Start the Retry Server**: Follow the instructions in the root README to start the `retry-server`.
2. **Start the Example Application**:
   ```bash
   cd retry-example
   mvn spring-boot:run
   ```
3. **Trigger Scenarios**: Call the URLs listed above via browser or Postman to see the retry platform in action.

## H2 内存数据库零配置选项 (Zero-Configuration H2 Option)

For local testing without setting up a MySQL database, this example application provides an H2 in-memory database profile.

To start the application using H2:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

When running with the `h2` profile, the application will:
- Use an in-memory database (`jdbc:h2:mem:retry_platform`).
- Automatically create the necessary tables on startup.
- Enable the H2 console at `http://localhost:8082/h2-console` (JDBC URL: `jdbc:h2:mem:retry_platform`, Username: `sa`, Password: *empty*).
