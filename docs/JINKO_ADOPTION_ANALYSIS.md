# ElecCloud 重试 SDK 接入分析 —— 晶科项目群

> **目标**：先以 **SDK standalone 模式** 落地（**不部署 retry-server**）。
> **本文回答**：接在哪最合适、为什么不接在哪。
> **证据基线**：结论全部来自对四个项目**实际源码**的检索，附 `文件:行号`。未经验证的信息已标注。

被评估项目：

| 项目 | 本地路径 | 技术栈 |
|---|---|---|
| jk-kms-backend | `D:/workspace/jinko/jk-kms-backend` | Java 8/17 · Spring Boot **2.7.18** · Jeecg Boot · MyBatis-Plus · Redisson |
| jdp-backend-cloud | `D:/workspace/jinko/jdp-backend-cloud` | Java 17 · Spring Boot **3.5.5** · Jeecg Boot · dynamic-datasource |
| jdp-ai-cloud | `D:/workspace/jinko/jdp-ai-cloud` | Java 17 · Spring Boot **4.0.2** · AgentScope 1.0.12 · Nacos |
| jk-kms-ragflow | `D:/workspace/jinko/jk-kms-ragflow` | **Python** · Flask · RagFlow 二次开发 |

---

## 一、结论速览

| 项目 | 兼容性 | 推荐度 | 首选场景 | 优先级 |
|---|---|---|---|---|
| **jk-kms-backend** | ✅ 开箱即用 | ⭐⭐⭐⭐⭐ | ①钉钉消息发送 ②RagFlow 文档解析触发 | **P0 试点** |
| **jdp-backend-cloud** | ⚠️ 需改 jakarta | ⭐⭐⭐⭐⭐ | 云盘→KMS 文档同步流水线（业务价值最高） | P1 主攻 |
| **jdp-ai-cloud** | ⚠️ 需改 jakarta + SB4 适配 | ⭐⭐ | 仅「文档下载 + 解析」一处值得做 | P2 观望 |
| **jk-kms-ragflow** | ❌ 语言不通 | — | **不适用，且不该硬塞**（原因见 4.4） | 不接 |

**一句话**：**先用 jk-kms-backend 做试点（零改造成本、有真实痛点），把价值跑出来，再攻 jdp-backend-cloud（价值最大但要先解决命名空间适配）。jdp-ai-cloud 大部分场景不适合重试语义，硬塞反而有害。**

---

## 二、先搞清楚：standalone 模式到底要什么

### 2.1 它不需要什么 ✅

- **不需要部署 retry-server / retry-admin**，不需要独立的重试数据库
- 不需要 Server 反向调用业务服务（这正是原设计的"去中心化"卖点）

### 2.2 它需要什么

| 依赖 | 说明 |
|---|---|
| **业务库 2 张表** | `retry_task` / `retry_history`，脚本见 `retry-client-sdk/src/main/resources/standalone-retry-schema.sql`，在业务自己的库里执行 |
| **Redis** | `StringRedisTemplate` 做 ZSET 延时队列（`mq-type: REDIS`，默认）；或 RabbitMQ |
| 被重试的方法 | 必须是 **Spring Bean 的 public 方法** |
| 入参 | 必须能 **JSON 序列化** 且能被**按参数名还原** |

场景策略直接在 yml 里配（`retry.client.scenes[]`），不需要查 `scene_config` 表。

### 2.3 ⚠️ 三个必须提前知道的硬约束（"硬塞"踩坑高发区）

**约束 1：重试时 ThreadLocal 上下文全部丢失**

重试发生在**调度线程**里，通过反射调用（`LocalRetryExecutor.handleInit` → `targetMethod.invoke`），**不是 web 请求线程**。因此：

- Jeecg 的 `SecurityUtils.getSubject()`、租户上下文、`TokenUtils.getCurrentUser()`、`RequestContextHolder` 在重试时**全部为 null**
- **推论**：能被注解的方法必须"自给自足"——`workNo` / `tenantId` / `knowledgeId` 等上下文**必须作为方法入参显式传入**，不能靠 ThreadLocal 取

> 这条是决定"哪些方法有资格打注解"的分水岭。建议 SDK 侧补一个 `RetryContextPropagator` SPI（见第六节）。

**约束 2：入参按"参数名"还原，要求编译期保留参数名**

`LocalRetryExecutor.java:499-514`：

```java
String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
String paramName = (paramNames != null && paramNames.length > i) ? paramNames[i] : parameters[i].getName();
Object paramValue = paramsMap.get(paramName);   // ← 按名字取值
```

实测各项目编译配置：

| 项目 | `<parameters>true</parameters>` | 影响 |
|---|---|---|
| jk-kms-backend | ❌ **未配置**（`pom.xml:379-382` 只配了 source/target） | 依赖 `-g` 调试信息的 LocalVariableTable 兜底，**脆弱**；接入前必须补上 |
| jdp-backend-cloud | ✅ 已配置（`pom.xml:455-460`） | 正常 |
| jdp-ai-cloud | ❌ 未配置（`pom.xml:286-290`） | 同 jk-kms-backend |

**约束 3：方法必须幂等**——这是前提而非选项。SDK 通过 `checkStatus` / `doQuery` 两阶段兜底，但业务侧仍需可重入。

---

## 三、第一道筛子：技术栈兼容性

SDK 基于 **Spring Boot 2.7 / `javax.*`** 命名空间，且 `RetryClientAutoConfiguration` 通过 `META-INF/spring.factories` + `AutoConfiguration.imports` 双注册。

| 项目 | Spring Boot | 命名空间 | SDK 能否直接落 |
|---|---|---|---|
| jk-kms-backend | 2.7.18 | `javax` | ✅ **原生匹配** |
| jdp-backend-cloud | 3.5.5 | `jakarta` | ❌ `javax.annotation.PostConstruct` 等需改 `jakarta` |
| jdp-ai-cloud | 4.0.2 | `jakarta` | ❌ 同上，且 SB4 跨度更大 |
| jk-kms-ragflow | — | — | ❌ Python，无落地可能 |

SDK 里需要改的 `javax` 点很集中（`RetryClientAutoConfiguration` 的 `javax.annotation.PostConstruct`、`javax.sql.DataSource`），**改造量不大**，但需要出一个 `retry-client-sdk-boot3` 分支/工件，否则没法进 SB3+ 的项目。

---

## 四、逐项目分析：哪里值得接

### 4.1 jk-kms-backend —— ✅ 首推（零改造成本 + 真实痛点）

**兼容性**：SB 2.7.18，与 SDK 原生匹配。`StringRedisTemplate`（`ShiroRealm.java:43`）、Redisson（`common/lock/RedissonDistributedLock.java`）、MyBatis-Plus 全部现成。

**全项目无 Spring Retry**（`@Retryable` / `RetryTemplate` 检索无匹配），说明这是真正的空白区。

---

#### 候选 A（建议作为第一个试点）：**钉钉消息发送**

| 项 | 内容 |
|---|---|
| 调用点 | `message/handle/impl/DdSendMsgHandle.java:34` → `dingtalkService.sendMessage(messageDTO, true)`<br>`system/service/impl/SysBaseApiImpl.java:459,477` → `dingtalkService.sendMessage(message, true)`<br>实现：`system/service/impl/ThirdAppDingtalkServiceImpl.java:89,785,843` |
| 现状 | **失败无任何补偿**，通知静默丢失，用户无感知 |
| 为什么适合 | 钉钉 OpenAPI 有 QPS 限流，网络抖动常见；**天然幂等**（最坏重复发一条通知）；影响面可控 |
| 幂等键建议 | 业务实体 id（如流程实例 id / 待办 id） |
| 接入成本 | **最低**——`useDefaultHook = true` 可免写 Hook 类 |

---

#### 候选 B（业务价值最大）：**RagFlow 文档解析 / 向量化触发**

| 项 | 内容 |
|---|---|
| 统一出口 | `airag/llm/handler/RagFlowHandler.java:101,148` —— OkHttp `client.newCall(httpRequest).execute()` **单次调用** |
| 失败处理 | `:118 / :127 / :165 / :174 / :192 / :223` —— catch 后直接 `Result.error(...)`，**不重试、不放回队列** |
| 业务入口 | `airag/llm/service/impl/AiragKnowledgeDocServiceImpl.java:245`（`createKnowDoc`）、`:823`（`runKnowDocs`，解析+向量化） |
| 现状 | 失败 → `doc.setStatus("failed")`（`:862`）。重跑入口 `rebuildDocumentByKnowId(knowId, onlyFailed=true)`（`:758`）**是人工点击「重建失败文档」触发的，没有自动重试** |
| 为什么适合 | 文档解析是「**慢 + 可重入 + 幂等**」的教科书场景；RagFlow 偶发超时/队列满就会让整份文档长期 failed，只能等人发现 |
| 幂等键建议 | `docId` |
| Hook 要点 | `checkStatus` → 查 doc 表 status；`doQuery` → 查 RagFlow 文档解析进度 |

---

#### 候选 C（清理重复造轮子）：**JipExternalApiService 的自研重试**

`airag/llm/service/JipExternalApiService.java:58-76`：

```java
private <T> T retry(Callable<T> task, int maxAttempts, long initialDelayMs) throws IOException {
    for (int i = 1; i <= maxAttempts; i++) {
        try { return task.call(); }
        catch (IOException e) { ... Thread.sleep(delay); delay *= 2; }
        catch (Exception e) { throw new IOException(e); }
    }
}
```

问题：**阻塞调用线程**（`Thread.sleep`）、**只重试 IOException**（非 IO 异常被包装后直接抛）、**进程重启即丢失**、无可视化。
→ 迁移到 SDK 后获得：持久化 + 跨重启 + 可视化轨迹。

---

### 4.2 jdp-backend-cloud —— ✅ 业务价值最高（但要先解决 jakarta）

**兼容性**：SB 3.5.5，需 SDK 出 jakarta 版本。好消息：`pom.xml:455-460` 已开 `<parameters>true</parameters>`。

**全项目无 Spring Retry**（检索无匹配）。

---

#### 最高价值候选：**云盘 → KMS 文档同步流水线**

文件：`jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/jkCloudFile/service/impl/JkCloudSyncEngineServiceImpl.java`

```
executeSyncPipeline  :157-296
  └─ for (ScannedFile file : filesToSync)   :197
       └─ try                                :203
            ├─ extractFileBytes              :234
            ├─ kmsDocSyncClient.deleteDoc    :262   ← 级联清理老文档
            ├─ kmsDocSyncClient.uploadFile   :265   ← MinIO 上传 + KMS doc/edit 两步
            └─ saveOrUpdateDocMapping        :274
          catch (Exception ex) { failed++; } :278   ← ★ 文件被静默丢弃
  └─ 只有全部失败才置 STATUS_FAILED          :292
```

**核心问题**：单个文件失败只累计计数，**文件永久丢失，没有任何重跑入口**。用户的云盘文件同步失败 = 内容没进知识库，只能整批重来。

**注解的正确落点（叶子方法，不要打在整个流水线上）**：

| 落点 | 位置 | 说明 |
|---|---|---|
| `KmsDocSyncClient.uploadFile` | `client/KmsDocSyncClient.java:167,189` | 内部两步（`:229` uploadMinio + `:280` doc/edit），任一步失败整体失败 |
| `DingTalkOpenApiClient.sendJsonRequest` | `client/DingTalkOpenApiClient.java:402` | 失败直接 `throw new JeecgBootException`，**钉钉 QPS 限流是常态** |
| `DingTalkOpenApiClient.downloadStorageFile` | `client/DingTalkOpenApiClient.java:322` | 下载流，网络抖动重试刚需 |
| `FangcloudOpenApiClient` | `client/FangcloudOpenApiClient.java:41,380,398` | 方舟云盘同类问题 |

**幂等键设计**：`workNo + sourceType + sourceNodeId`，正好对应实体 `entity/AiragCloudDocMapping.java` 的唯一键 → 与 SDK 的 `idempotentKey` **天然咬合**。

> ⚠️ **切法提醒**：**不要把 `@RetryableTask` 打在 `executeSyncPipeline` 上**。该方法处理 `byte[] contentBytes`、含 for 循环与 DB 写，参数无法 JSON 还原、重试会导致整批重跑。必须下沉到叶子方法。

---

### 4.3 jdp-ai-cloud —— ⚠️ 只有一个点真正合适

**兼容性**：SB 4.0.2 + AgentScope，SDK 改造最重。`<parameters>true</parameters>` 未配置（`pom.xml:286-290`）。

#### ✅ 唯一推荐：**文档下载 + 解析**

| 调用点 | 位置 | 现状 |
|---|---|---|
| `DownloadReferenceFileTool` | `document-sub-agent/.../tools/DownloadReferenceFileTool.java:97-101` | HttpClient connect 15s / req 60s，失败只返回提示文本，**无重试** |
| `DocumentParseTool` | `document-sub-agent/.../tools/DocumentParseTool.java:159-164` | **只对 HTTP 500 重试一次**，超时/网络异常不重试 |
| `ImageParseTool` | `document-sub-agent/.../tools/ImageParseTool.java:179-184` | 同上 |

**为什么合适**：文件解析天然「**幂等**（同一文件重复解析结果一致）+ **异步** + **慢**」，正是重试平台的靶心。

#### ❌ 明确不建议用（硬塞会出问题）

| 场景 | 位置 | 为什么不建议 |
|---|---|---|
| LLM / VLM 调用 | `supervisor/.../config/RouterModelConfig.java:41,76`（`maxAttempts(1)`）、`supervisor/.../message/AttachmentContentService.java:92-99` | 有 **token 成本** + **分钟级耗时**；失败时用户端会话已断，**重试产生的结果无处投递**。正确降级是"让用户重发"，不是后台偷跑 |
| SSE 流式调用 | `consult-sub-agent/.../controller/KmsChatStreamingCaller.java:80-92` | 流式响应重试会**破坏 SSE 语义** |
| MCP 工具调用 | `jdp-ai-common-starter/.../mcp/AccessTokenMcpClientWrapper.java:56-111` | 仅**读类**工具（查知识库）适合；写类需先确认幂等 |
| ChatLog MQ | `jdp-ai-common-starter/.../logging/ChatLogConsumer.java:37,82` | 已有 MQ + 本地库兜底 + DLQ。缺的是 **DLQ 重放**（`ChatLogAutoConfiguration.java:52-53` 只声明了死信路由，**没有消费者**）→ 这是"补 DLQ 消费者"的事，**不是重试 SDK 的事** |

---

### 4.4 jk-kms-ragflow —— ❌ 不适用，且不该硬塞

1. **技术栈**：Python / Flask 的 RagFlow 二次开发，Java SDK **无落地可能**。
2. **它自己已有任务级重试**：task 表的 `progress` / `run` 字段（你们此前正是用 `UPDATE ... progress=-1, run='0'` 重置来恢复卡死文档）。
3. **关键判断**：**它的重试是"任务级重跑"，SDK 是"方法级重试"，不在一个层面**，硬塞只会造成两套重试机制打架。

> **正确做法**：让 **Java 侧持有重试职责**。jk-kms-backend 调用 RagFlow 失败 → 由 SDK 重试那个 Java 调用方法。**改 Java，不改 Python。**

---

## 五、落地路径（三步走，不硬塞）

### Phase 1 —— jk-kms-backend + 钉钉消息发送（验证价值）

1. 在 jk-kms-backend 业务库执行 `standalone-retry-schema.sql`（2 张表）
2. **`pom.xml:379-382` 的 maven-compiler-plugin 补 `<parameters>true</parameters>`** ← 前置必做
3. 加 SDK 依赖，配置：
   ```yaml
   retry:
     client:
       mode: standalone        # 关键：不依赖 server
       enabled: true
       mq-type: REDIS
       queue-name: kms.retry
       scenes:
         - scene-type: 1001
           scene-name: 钉钉消息发送
           retry-intervals: "1,3,6,9"
           max-retry-count: 4
   ```
4. 方法上打注解：
   ```java
   @RetryableTask(sceneType = 1001, idempotentKey = "#msgId", useDefaultHook = true)
   public void sendDingtalkNotice(String msgId, String content, List<String> userIds, String tenantId) { ... }
   ```
   > 注意 `tenantId` 必须作为入参传入 —— 见约束 1。

### Phase 2 —— jk-kms-backend + RagFlow 文档解析（价值放大）

带 Hook 的两阶段幂等：`checkStatus` 查 doc 表状态，`doQuery` 查 RagFlow 解析进度，幂等键 = `docId`。

### Phase 3 —— SDK 适配 jakarta → jdp-backend-cloud 云盘同步

把 `KmsDocSyncClient.uploadFile` / `DingTalkOpenApiClient.downloadStorageFile` 接入，解决"同步失败文件永久丢失"。

---

## 六、⚠️ 接入前必须验证的头号风险：SqlSessionFactory 冲突

**这是我认为最可能"一接入就起不来"的点，务必在试点前先跑一次启动验证。**

### 事实链

1. SDK 在 standalone 模式下注册了一个 **`SqlSessionFactory`** bean：
   ```java
   // RetryClientAutoConfiguration.java:206-217
   @Bean("standaloneRetrySqlSessionFactory")
   @ConditionalOnProperty(prefix = "retry.client", name = "mode", havingValue = "standalone")
   public SqlSessionFactory standaloneRetrySqlSessionFactory(DataSource dataSource) { ... }
   ```
2. 而 jk-kms-backend / jdp-backend-cloud **都依赖 MyBatis-Plus 自动配置**，自身**没有**定义 `SqlSessionFactory`：
   - `jeecg-boot-base-core/.../config/mybatis/MybatisPlusSaasConfig.java:33` → `@MapperScan(value={"org.jeecg.**.mapper*"})`
   - 同文件 `:85-86` → 只定义了 `MybatisPlusInterceptor`
3. MyBatis-Plus 的 `MybatisPlusAutoConfiguration` 对 `sqlSessionFactory` bean 带 `@ConditionalOnMissingBean`。
4. SDK 的 `RetryClientAutoConfiguration` 与 MyBatis-Plus 自动配置**同为自动配置类**，按类名排序时 `com.baomidou.*` **排在** `com.retry.platform.*` **之前**。

### 风险结论

可能出现两种坏结果之一：
- **A（更糟）**：Jeecg 的 `@MapperScan` 按类型装配 `SqlSessionFactory` 时出现 **两个候选 bean** → `NoUniqueBeanDefinitionException`，**应用启动失败**；
- **B**：自动配置退让/顺序叠加导致 Jeecg 全量 Mapper 与 SDK 的 Mapper 抢同一个工厂，产生难排查的运行时异常。

### 建议的修复方向（任选或叠加）

- **SDK 侧（推荐，治本）**：standalone 模式的工厂不要暴露成可被按类型装配的候选 bean —— 用 `AbstractBeanDefinition#setAutowireCandidate(false)`（SB2.7 可用 `BeanFactoryPostProcessor`，SB3+ 可直接 `@Bean(defaultCandidate = false)`）；SDK 自己的 MapperFactoryBean 是**显式引用**该工厂的，不受影响。
- **接入侧（兜底）**：给 Jeecg 显式定义一个主 `SqlSessionFactory`，并让 SDK 的工厂保持非候选。

> 说明：以上是从源码推导的结论，**我尚未在你的应用中实际启动验证**。请在 Phase 1 第一步就先做一次"加上 SDK 依赖、暂不启用场景"的**空跑启动测试**，确认应用能正常起来、Jeecg 原有 Mapper 全部可用，再继续后面的接入。

---

## 七、给 SDK 的 4 个改进建议（吃自己的狗粮才会发现）

| # | 问题 | 位置 | 建议 |
|---|---|---|---|
| 1 | **依赖未标 optional** | `retry-client-sdk/pom.xml:34-37,60-69`：`spring-boot-starter-web` / `data-redis` / `amqp` 均为 compile 依赖 | 改为 `<optional>true</optional>`。否则接入方被动引入 **RabbitMQ 自动配置**——jdp-backend-cloud 全项目未使用 amqp，接入后若开着 Actuator 健康检查，会额外探测 `localhost:5672` 并可能产生 DOWN 告警 |
| 2 | **缺上下文传递机制** | `LocalRetryExecutor.java:225-228` 只设了 `IN_RETRY_CONTEXT` 标记 | 提供 `RetryContextPropagator` SPI（重试前恢复租户/登录态）。**这是 Jeecg 系项目能否大面积使用注解的决定性因素** |
| 3 | **`-parameters` 未强校验** | `LocalRetryExecutor.java:499-514` | 启动时校验宿主应用是否保留参数名，未开启直接明确报错，而不是等到重试时参数**静默变 null** |
| 4 | **文档口径不一致** | `README_zh.md:206` 技术栈表列了「Redisson 分布式锁」，但 SDK pom **无 Redisson 依赖** | 实际两阶段幂等靠 DB 唯一键 + `casUpdateToExecuting`（方案没问题），文档应统一口径 |

另有 **SqlSessionFactory 冲突** 问题见第六节。

---

## 八、待确认项

1. **jk-kms-backend 的钉钉消息**：是否存在"绝对不能重复发"的业务顾虑（如已带业务单号的卡片消息）？
2. **jdp-ai-cloud 是否现在就要动**？SB 4.0.2 适配（javax→jakarta + 自动配置机制）成本明显高于收益，建议排到 Phase 3 之后。
3. **`scene_type` 编号规范**：SDK 用 `int`，建议按项目/模块分段（如 1000–1999 给 kms，2000–2999 给 jdp），需要你定一个规范并落到文档。
