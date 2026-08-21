# ElecCloud 本地部署手册

> 适用环境：Windows / macOS / Linux，本机已安装 Docker Desktop（或 Docker Engine + Compose）。
> 提供两种部署方式，按需选择：

| 方式 | 说明 | 适合 |
|------|------|------|
| **A. 公共基础设施（推荐）** | 单独起一套 MySQL + Redis，供所有项目共用 | 长期多项目开发 |
| B. 独立全包 | 每个项目 compose 自带一套 MySQL + Redis | 演示、一次性项目 |

---

## 0. 部署架构一览

三个应用容器：重试服务端（8080）、管理后台（8081）、Demo 应用（8082）。

**方式 A**：MySQL / Redis 在公共的 `infra` 项目里，应用容器 join 共享网络连接它们。
**方式 B**：MySQL / Redis 由 eleccloud 自己的 compose 一并启动。

> ⚠️ 本项目**不要用**根目录的旧版 `docker-compose.yml`（有 Redis 配置拼写错误，且会连到源码里硬编码的生产库）。方式 A 用 `docker-compose.shared.yml`，方式 B 用 `docker-compose.local.yml`。

---

## 1. 前置环境检查

### 1.1 Docker 与 Compose

```bash
docker --version
docker compose version
```

- Docker 20.10+，Compose v2（或 v1 的 `docker-compose`）。
- Windows 需开启 WSL2 / Hyper-V，Docker Desktop 内存建议 **≥ 4GB**。

### 1.2 JDK 与 Maven（编译用）

```bash
java -version
mvn -version
```

- JDK 8+，Maven 3.6+。Dockerfile 是单阶段 COPY 已编译 jar，所以必须先编译。

### 1.3 端口检查

确保 **3306 / 6379 / 8080 / 8081 / 8082** 空闲：

```bash
# Windows (PowerShell)
netstat -ano | findstr "3306 6379 8080 8081 8082"

# macOS / Linux
lsof -i :3306 -i :6379 -i :8080 -i :8081 -i :8082
```

---

## 2. 编译打包（两种方式都需要）

在项目根目录执行：

```bash
mvn clean package -DskipTests
```

产物：`retry-server/target/retry-server-1.0.0-exec.jar`、`retry-admin/target/retry-admin-1.0.0.jar`、`retry-example/target/retry-example-1.0.0.jar`。

> 改过 `retry-admin/frontend` 前端代码的，先 `cd retry-admin/frontend && npm install && npm run build` 再打包。

---

## 3. 方式 A：公共基础设施（推荐）

### 3.1 首次必做：修改密码

公共底座用了占位密码 `ChangeMe2026`，首次使用请**全局替换成你自己的强密码**，涉及 4 个文件：

```
infra/docker-compose.yml        # MySQL 密码 + Redis 密码
retry-server-shared.yml         # 数据库密码 + Redis 密码 + Redisson 密码
retry-admin-shared.yml          # 同上
docker-compose.shared.yml       # retry-example 的 SPRING_REDIS_PASSWORD
```

> 只需保证这 4 个文件里的密码一致，并在 IDE 里「全局替换 ChangeMe2026 → 你的密码」即可。

### 3.2 启动公共 MySQL + Redis

```bash
docker compose -p infra -f infra/docker-compose.yml up -d
```

首次会拉取 `mysql:8.0`、`redis:6-alpine` 镜像，并创建共享网络 `shared-network`。

### 3.3 首次导入数据库表结构

公共 MySQL 不会自动执行 eleccloud 的建表脚本，首次需要手动导入一次：

```bash
docker exec -i shared-mysql mysql -uroot -p'你的密码' < db/init.sql
```

> `init.sql` 内含 `CREATE DATABASE IF NOT EXISTS retry_platform`，会自动建库并建表。以后其他项目共享这个 MySQL 时，各自建自己的 database 即可。

### 3.4 启动 eleccloud 应用

```bash
docker compose -p retry-platform -f docker-compose.shared.yml up -d --build
```

### 3.5 验证

```bash
docker compose -p infra -f infra/docker-compose.yml ps
docker compose -p retry-platform -f docker-compose.shared.yml ps
```

| 服务 | 地址 |
|------|------|
| 重试服务端 | http://localhost:8080/actuator/health |
| 管理后台 | http://localhost:8081 |
| Demo 应用 | http://localhost:8082 |

---

## 4. 方式 B：独立全包（备选）

每个项目自带一套 MySQL / Redis，与外部隔离（MySQL root 密码固定为 `password`，Redis 无密码）。

```bash
docker compose -p retry-platform -f docker-compose.local.yml up -d --build
```

MySQL 首次启动会自动执行 `db/init.sql` 建库建表（挂载到 `/docker-entrypoint-initdb.d`）。

---

## 5. 常用运维命令

```bash
# 停止（保留数据）
docker compose -p retry-platform -f docker-compose.shared.yml down
docker compose -p infra -f infra/docker-compose.yml down

# 停止并删除数据卷（⚠️ 清空数据，慎用）
docker compose -p infra -f infra/docker-compose.yml down -v

# 看日志
docker logs -f retry-platform-server
docker logs -f shared-mysql
```

---

## 6. 常见问题排查

| 现象 | 原因与处理 |
|------|-----------|
| `COPY failed ... retry-server-*-exec.jar` | 没先 `mvn clean package`，回到第 2 步 |
| `network shared-network declared as external, but could not be found` | 公共底座还没启动，先执行 3.2 |
| 应用日志报 MySQL 连不上 | 密码不一致，或没执行 3.3 导表；核对 4 个文件的密码是否统一 |
| 端口被占用 | 见 1.3，改 compose 里 `ports` 左侧宿主机端口 |
| 健康检查 `unhealthy` 但日志正常 | `eclipse-temurin:8-jre` 可能缺 `wget`，只要日志启动成功、接口能访问即可忽略 |
| 本机已装 MySQL/Redis 占 3306/6379 | 停掉本机服务，或改 `infra/docker-compose.yml` 端口映射 |

---

## 附：镜像离线迁移到测试环境

见根目录 `docker-compose.test.yml`，配合 `docker save` / `docker load` 使用。
