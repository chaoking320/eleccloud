# 贡献指南 (Contributing)

感谢你考虑为 **ElecCloud** 分布式重试平台做贡献！无论是提交 Bug、完善文档，还是新增特性，我们都非常欢迎。

## 行为准则

请友好、尊重地交流。我们致力于营造一个开放、包容的社区环境。

## 如何开始

1. **Fork** 本仓库到你的 GitHub 账号。
2. **Clone** 你的 Fork 到本地：`git clone https://github.com/<your-username>/eleccloud.git`
3. 添加上游仓库：`git remote add upstream https://github.com/your-org/eleccloud.git`
4. 创建特性分支：`git checkout -b feat/your-feature`（或 `fix/your-bug`）。

## 开发环境

- JDK 8+
- Maven 3.8+
- 可选：Redis 6.x / RabbitMQ 3.x / MySQL 8.0（用于本地联调与集成测试）

```bash
# 编译（跳过测试）
mvn clean install -Dmaven.test.skip=true

# 仅运行单元测试（集成测试依赖 Redis/MySQL，需本地中间件）
mvn test -Dtest='!RetryPlatformIntegrationTest'
```

## 代码规范

- 遵循 Google Java Style 基本约定，缩进 4 空格，UTF-8 编码。
- 关键类与方法添加 Javadoc / 注释，尤其是状态机、退避策略、MQ 投递等核心逻辑。
- 新功能**必须**附带单元测试；核心模块（`LocalRetryExecutor`、AOP 切面、退避策略、参数提取）改动需保证现有测试通过。
- 提交前请确保 `mvn test -Dtest='!RetryPlatformIntegrationTest'` 全绿。

## 提交信息规范

建议使用 [Conventional Commits](https://www.conventionalcommits.org/)：

```
feat: 新增 XXX 退避策略
fix: 修复预提交模式下进程崩溃后任务丢失
docs: 补充 SDK 接入指南
test: 补充 LocalRetryExecutor 守卫路径单测
```

## 提交 Pull Request

1. 将你的分支 push 到 Fork：`git push origin feat/your-feature`
2. 在 GitHub 发起 PR 到上游 `main` 分支。
3. PR 描述请说明：
   - 解决了什么问题 / 实现了什么特性
   - 关键设计取舍
   - 测试覆盖情况
4. 等待 CI（GitHub Actions）通过后，维护者会进行 Review 与合入。

## 报告问题

- **Bug**：请使用 Bug Report 模板，附上复现步骤、环境、日志。
- **新特性**：请使用 Feature Request 模板，说明场景与预期行为。

## 发布与版本

当前版本号遵循语义化版本（SemVer）。SDK 以 `com.retry.platform:retry-client-sdk` 发布，正式版本（非 SNAPSHOT）会同步至 Maven Central。
