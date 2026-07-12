# 部署与运维指南

> **版本**: v2.0 | **更新日期**: 2026-07

分布式重试平台支持两种部署方式：**Docker Compose 一键部署**（推荐用于开发、测试环境）以及 **二进制手动部署**（推荐用于生产环境）。

---

## 1. 前置环境要求

- **操作系统**: Linux (如 Ubuntu, CentOS) 或 macOS, Windows
- **JDK**: Java 8 及以上版本
- **Maven**: 3.6 及以上版本（如需从源码编译）
- **数据库**: MySQL 8.0+
- **缓存/队列**: Redis 6.x+
- **容器环境**: Docker 20.10+ 及 Docker Compose 2.0+（容器化部署需要）

---

## 2. 方式一：Docker Compose 一键部署（推荐）

通过根目录下的 `docker-compose.yml`，可以一键构建并启动 MySQL、Redis、重试服务端、管理后台和 Demo 应用。

### Step 1: 准备源码与配置
确保当前目录包含项目所有文件，并且 `db/init.sql` 存在。

### Step 2: 启动容器
在根目录下执行以下命令：

```bash
# 构建并后台启动所有服务
docker-compose up -d --build
```

### Step 3: 查看状态
```bash
# 查看容器运行状态
docker-compose ps
```

运行成功后，各服务端口映射如下：

- **MySQL**: `3306` (用户名: `root`, 密码: `password`)
- **Redis**: `6379`
- **retry-server**: `8080` (监控端点: `http://localhost:8080/actuator/prometheus`)
- **retry-admin**: `8081` (管理后台页面)
- **retry-example**: `8082` (Demo 接口地址: `http://localhost:8082/api/demo/mode1/refund`)

---

## 3. 方式二：手动打包与部署（生产环境推荐）

在生产环境中，通常将数据库与中间件独立部署，这里介绍如何将项目编译为可执行的 jar 包进行部署。

### Step 1: 初始化数据库
1. 连接到你的生产环境 MySQL 实例。
2. 创建数据库 `retry_platform`：
   ```sql
   CREATE DATABASE IF NOT EXISTS retry_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
3. 导入初始化脚本 `db/init.sql`。

### Step 2: 编译打包
在根目录下执行 Maven 打包命令：

```bash
mvn clean install -Dmaven.test.skip=true
```

打包成功后，各模块生成的 Jar 包位于：
- `retry-server/target/retry-server-1.0.0.jar`
- `retry-admin/target/retry-admin-1.0.0.jar`
- `retry-example/target/retry-example-1.0.0.jar`

### Step 3: 运行服务

可以通过命令行参数或外部配置文件覆盖默认的数据库和 Redis 连接信息：

#### 1. 运行重试服务端 (retry-server)
```bash
nohup java -jar retry-server-1.0.0.jar \
  --spring.datasource.url="jdbc:mysql://YOUR_DB_IP:3306/retry_platform?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2B8" \
  --spring.datasource.username="your_user" \
  --spring.datasource.password="your_password" \
  --spring.redis.hhost="YOUR_REDIS_IP" \
  --spring.redis.password="your_redis_pwd" \
  --spring.redis.redisson.config="singleServerConfig:\n  address: \"redis://YOUR_REDIS_IP:6379\"\n  password: \"your_redis_pwd\"\n  database: 2" \
  > retry-server.log 2>&1 &
```

#### 2. 运行管理后台 (retry-admin)
```bash
nohup java -jar retry-admin-1.0.0.jar \
  --spring.datasource.url="jdbc:mysql://YOUR_DB_IP:3306/retry_platform?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2B8" \
  --spring.datasource.username="your_user" \
  --spring.datasource.password="your_password" \
  > retry-admin.log 2>&1 &
```

---

## 4. 生产环境优化配置建议

### 4.1 调度参数微调

在 `retry-server` 的 `application.yml` 中，可以根据集群规模和任务量调整扫描参数：

```yaml
retry:
  scheduler:
    scan-interval: 5000           # 扫描间隔缩短为 5秒，提高任务调度时效性
    batch-size: 500               # 每次从延迟队列取出 500个 任务，应对高并发
    fallback-scan-interval: 60000 # 数据库降级扫描间隔（建议保持在1分钟）
  executor:
    core-pool-size: 30            # 增加核心执行线程数
    max-pool-size: 150            # 增加最大执行线程数
    queue-capacity: 5000          # 缓冲队列容量增大
```

### 4.2 告警集成（LogAlertServiceImpl 生产化）

默认的告警实现类 `LogAlertServiceImpl` 仅将告警输出到 LogBack 日志。
建议生产环境中进行以下扩展：
1. 继承或修改 `LogAlertServiceImpl` 类。
2. 对接邮件发送服务（`JavaMailSender`）或企业微信、钉钉的 Webhook 机器人。
3. 对触发 `failed_task` 的任务以及执行耗时过长的任务发出即时告警。

---

## 5. 运维监控

### 5.1 Prometheus 指标接入
重试平台采用 Micrometer 收集指标，对外暴露 `/actuator/prometheus` 端点。

在 `prometheus.yml` 中添加抓取任务：
```yaml
scrape_configs:
  - job_name: 'retry-platform'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:8080'] # 重试服务端IP和端口
```

### 5.2 核心监控看板配置

建议在 Grafana 中配置以下监控大盘：

1. **待执行任务积压趋势**: 曲线图展示 `retry.active.tasks.count`。若指标呈持续上升趋势，说明消费能力不足，需增加 `retry-server` 实例或调大线程池。
2. **任务失败率（死信量）**: 展示 `retry.failed.tasks.count` 的增量。若短时间内死信任务暴增，说明下游系统发生了宕机或不可达故障。
3. **接口重试耗时**: 观察 `retry.tasks.execution.duration` 的 95/99 分位数，及时发现因为网络缓慢导致的延迟。
