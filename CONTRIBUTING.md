# Contributing to ElecCloud

首先，感谢你考虑为 ElecCloud 做出贡献！正是像你这样的人让 ElecCloud 成为一个优秀的开源项目。

## 行为准则

本项目遵循 [Contributor Covenant](https://www.contributor-covenant.org/) 行为准则。参与本项目即表示你同意遵守其条款。

## 我如何贡献？

### 报告 Bug

在提交 Bug 报告前，请先检查[已有 Issues](https://github.com/chaoking320/eleccloud/issues)，确保该问题尚未被报告。

Bug 报告应包含：
- **清晰的标题**：简要描述问题
- **复现步骤**：详细的复现步骤
- **预期行为**：你期望发生什么
- **实际行为**：实际发生了什么
- **环境信息**：
  - ElecCloud 版本
  - Java 版本
  - Spring Boot 版本
  - 操作系统
  - 数据库版本（MySQL/PostgreSQL）
  - Redis 版本

**示例**：

```markdown
### Bug 描述
任务在 EXECUTING 状态下卡住，ExecutingTimeoutScanner 没有回滚

### 复现步骤
1. 提交一个重试任务
2. 在执行过程中强制 kill -9 进程
3. 等待 5 分钟
4. 任务仍保持 EXECUTING 状态

### 预期行为
ExecutingTimeoutScanner 应该在 5 分钟后将任务回滚为 INIT

### 实际行为
任务一直卡在 EXECUTING 状态

### 环境
- ElecCloud: 1.0.0
- Java: 17.0.8
- Spring Boot: 2.7.18
- MySQL: 8.0.33
- Redis: 6.2.7
- OS: Ubuntu 22.04
```

### 提出功能建议

功能建议应包含：
- **清晰的用例**：描述谁需要这个功能，为什么需要
- **详细的设计**：建议的实现方式
- **替代方案**：考虑过哪些其他方案
- **影响范围**：对现有功能的影响

### Pull Request 流程

#### 1. Fork 仓库

点击右上角的 "Fork" 按钮。

#### 2. 克隆到本地

```bash
git clone https://github.com/YOUR_USERNAME/eleccloud.git
# 原始仓库地址：https://github.com/chaoking320/eleccloud.git
cd eleccloud
```

#### 3. 创建分支

```bash
# 从 main 分支创建新分支
git checkout -b feature/your-feature-name

# 或修复 bug
git checkout -b fix/issue-123
```

**分支命名规范**：
- 新功能：`feature/功能名称`
- Bug 修复：`fix/issue编号` 或 `fix/问题描述`
- 文档：`docs/文档主题`
- 性能优化：`perf/优化内容`
- 重构：`refactor/重构内容`

#### 4. 进行开发

**代码规范**：

1. **Java 代码**：
   - 遵循 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
   - 使用 4 空格缩进
   - 类和方法必须有 JavaDoc 注释
   - 复杂逻辑必须有行内注释

2. **注释要求**：
   ```java
   /**
    * 处理重试任务的核心方法
    * 
    * <p>工作流程：
    * 1. CAS 锁定任务为 EXECUTING
    * 2. 执行 Hook 状态机
    * 3. 根据结果更新任务状态
    * 
    * @param payload 任务执行上下文
    * @throws IllegalStateException 如果任务状态不合法
    */
   public void executeTask(RetryMessagePayload payload) {
       // 实现代码
   }
   ```

3. **测试要求**：
   - 所有新功能必须有单元测试
   - 核心逻辑必须有集成测试
   - 测试覆盖率不得降低
   - 测试命名：`testMethodName_scenario_expectedBehavior`

   ```java
   @Test
   @DisplayName("CAS锁定失败 - 应立即退出不执行")
   void testExecuteTask_casLockFailed_shouldExitImmediately() {
       // Given
       when(retryClient.markExecuting("TASK001")).thenReturn(false);
       
       // When
       executor.executeWithPayload(payload);
       
       // Then
       verify(retryClient, never()).updateStatus(anyString(), anyString());
   }
   ```

4. **提交规范**：

   使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

   ```
   <type>(<scope>): <subject>

   <body>

   <footer>
   ```

   **Type 类型**：
   - `feat`: 新功能
   - `fix`: Bug 修复
   - `docs`: 文档更新
   - `style`: 代码格式（不影响功能）
   - `refactor`: 重构
   - `perf`: 性能优化
   - `test`: 测试相关
   - `chore`: 构建/工具/依赖更新

   **示例**：
   ```bash
   feat(client): add support for exponential backoff strategy

   Implement exponential backoff calculation in BackoffStrategy.
   Formula: delay = baseMinutes * 2^(retryCount-1)

   Closes #123
   ```

#### 5. 运行测试

```bash
# 运行所有测试
mvn clean test

# 运行特定模块测试
mvn test -pl retry-client-sdk

# 运行特定测试类
mvn test -Dtest=LocalRetryExecutorTest
```

确保所有测试通过：
```
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

#### 6. 推送并创建 PR

```bash
# 推送到你的 Fork
git push origin feature/your-feature-name
```

在 GitHub 上创建 Pull Request，填写 PR 模板。

**PR 标题规范**：
```
feat: Add exponential backoff strategy
fix: Resolve EXECUTING timeout issue (#123)
docs: Update quickstart guide
```

**PR 描述模板**：

```markdown
## 变更类型
- [ ] Bug 修复
- [ ] 新功能
- [ ] 破坏性变更
- [ ] 文档更新

## 变更描述
清晰描述本 PR 做了什么改动。

## 相关 Issue
Closes #123

## 测试
- [ ] 已添加单元测试
- [ ] 已添加集成测试
- [ ] 所有测试通过
- [ ] 手动测试通过

## 截图（如果适用）
（添加截图说明变更效果）

## 检查清单
- [ ] 代码遵循项目规范
- [ ] 已添加必要的注释
- [ ] 已更新相关文档
- [ ] 测试覆盖率未降低
- [ ] 无编译警告
- [ ] Commit 信息符合规范
```

#### 7. Code Review

维护者会审查你的代码并提供反馈。请及时响应评论并进行必要的修改。

**常见反馈**：
- 代码风格问题
- 缺少测试
- 性能问题
- 设计问题

#### 8. 合并

所有检查通过且得到批准后，PR 将被合并到主分支。

## 开发环境设置

### 前置要求

- JDK 17 或更高版本
- Maven 3.6+
- Docker（用于运行集成测试）
- Git

### 本地构建

```bash
# 克隆你fork的仓库
git clone https://github.com/YOUR_USERNAME/eleccloud.git
# 或者克隆原始仓库
git clone https://github.com/chaoking320/eleccloud.git
cd eleccloud

# 构建项目（跳过测试以加快速度）
mvn clean install -DskipTests

# 运行测试
mvn clean test

# 启动本地环境
docker compose up -d
```

### IDE 配置

**IntelliJ IDEA**：

1. 导入项目：File → Open → 选择 pom.xml
2. 等待 Maven 依赖下载完成
3. 配置代码风格：
   - File → Settings → Editor → Code Style
   - 导入 `code-style.xml`（如果有）
4. 启用注解处理：
   - Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   - 勾选 "Enable annotation processing"

**VS Code**：

1. 安装插件：
   - Extension Pack for Java
   - Spring Boot Extension Pack
2. 打开项目文件夹
3. Maven 会自动识别并加载

### 调试技巧

**调试 SDK**：

```yaml
# application.yml
logging:
  level:
    com.retry.platform.client: DEBUG
```

**调试 Server**：

```yaml
logging:
  level:
    com.retry.platform.server: DEBUG
    
# 查看 SQL
logging:
  level:
    com.retry.platform.server.mapper: DEBUG
```

**远程调试**：

```bash
# 启动应用时添加调试参数
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar app.jar
```

然后在 IDE 中连接到 `localhost:5005`。

## 项目结构

```
eleccloud/
├── retry-client-sdk/      # 客户端 SDK
│   ├── src/main/java/
│   │   └── com/retry/platform/client/
│   │       ├── annotation/    # 注解定义
│   │       ├── aspect/        # AOP 切面
│   │       ├── executor/      # 执行引擎
│   │       ├── hook/          # Hook 接口
│   │       └── ...
│   └── src/test/java/         # 单元测试
├── retry-server/          # 服务端
│   ├── src/main/java/
│   │   └── com/retry/platform/server/
│   │       ├── controller/    # REST API
│   │       ├── service/       # 业务逻辑
│   │       ├── scheduler/     # 定时任务
│   │       ├── mapper/        # MyBatis Mapper
│   │       └── ...
│   └── src/test/java/         # 集成测试
├── retry-admin/           # 管理后台
│   ├── src/                   # 后端
│   └── frontend/              # Vue3 前端
├── retry-example/         # 示例项目
├── docs/                  # 文档
└── deploy/               # 部署脚本
```

## 发布流程

发布由维护者负责：

1. 更新版本号：`mvn versions:set -DnewVersion=1.1.0`
2. 更新 CHANGELOG.md
3. 创建 Git Tag：`git tag -a v1.1.0 -m "Release 1.1.0"`
4. 推送 Tag：`git push origin v1.1.0`
5. GitHub Actions 自动构建和发布

## 常见问题

### Q: 我应该从哪里开始贡献？

A: 查看标记为 `good first issue` 的 Issue，这些是适合新手的任务。

### Q: 我的 PR 多久会被审查？

A: 通常在 48 小时内。如果超过一周没有回应，可以在 PR 中 @ 维护者。

### Q: 测试失败怎么办？

A: 先在本地运行测试定位问题：
```bash
mvn clean test -Dtest=FailedTestName -X
```

查看详细日志找到失败原因。

### Q: 如何运行集成测试？

A: 集成测试需要 Docker 环境：
```bash
# 启动依赖服务
docker compose -f docker-compose.test.yml up -d

# 运行集成测试
mvn verify -P integration-test

# 清理
docker compose -f docker-compose.test.yml down
```

### Q: 代码风格检查不通过？

A: 使用 Maven 插件自动格式化：
```bash
mvn spotless:apply
```

## 社区

- **GitHub Issues**: [Bug 报告和功能建议](../../issues)
- **GitHub Discussions**: [讨论和问答](../../discussions)
- **微信群**: （扫描 README 中的二维码）

## 许可证

通过提交代码，你同意你的贡献将在 [Apache 2.0 License](LICENSE) 下授权。

---

再次感谢你的贡献！🎉
