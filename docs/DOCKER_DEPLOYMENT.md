# ElecCloud 容器化部署方案 (Docker & Docker Compose)

> **适用版本**：ElecCloud v1.0.0+  
> **更新时间**：2026-09  
> **文档定位**：针对开发、测试及生产环境的 Docker 容器化落地与排错全指南。

---

## 1. 现状评估与前置注意

当前项目已提供基础的 `Dockerfile` 与 `docker-compose.yml`，但需要注意以下关键点：
1. **JAR 包依赖前置**：当前子模块的 `Dockerfile` 采用轻量化 `eclipse-temurin:8-jre` 镜像，直接 `COPY target/*.jar app.jar`。因此**必须在宿主机先执行 Maven 编译打包**，否则直接执行 `docker-compose up --build` 会报错找不到 Jar 包。
2. **多阶段构建替代方案**：如果希望机器上无需安装 Maven / JDK 就能一键部署，可以使用本文提供的 **多阶段构建 Dockerfile (Multi-stage)**。
3. **配置兼容性**：`docker-compose.yml` 中服务端口分别为：
   - MySQL: `3306`
   - Redis: `6379`
   - retry-server: `8080` (调度与存储引擎)
   - retry-admin: `8081` (管理后台页面)
   - retry-example: `8082` (演示 Demo)

---

## 2. 方案一：标准一键构建部署（推荐：本地/内网 CI）

该方案适合宿主机具备 Maven 环境（或通过 Jenkins/GitLab CI 提前打好包）的标准部署。

### 步骤 1：本地编译打包
在项目根目录（`eleccloud`）执行：
```bash
# Windows PowerShell / CMD
mvn clean package -DskipTests

# Linux / macOS
./mvnw clean package -DskipTests
```
确保以下文件生成成功：
- `retry-server/target/retry-server-1.0.0-exec.jar` (或 `*.jar`)
- `retry-admin/target/retry-admin-1.0.0.jar`
- `retry-example/target/retry-example-1.0.0.jar`

### 步骤 2：启动容器集群
```bash
# 启动所有服务（后台运行）
docker-compose up -d --build

# 仅启动基础中间件与服务端（不包含 Demo）
docker-compose up -d mysql redis retry-server retry-admin
```

### 步骤 3：健康检查与服务验证
```bash
# 查看容器运行状态
docker-compose ps

# 查看 retry-server 日志
docker-compose logs -f retry-server

# 查看 retry-admin 日志
docker-compose logs -f retry-admin
```

启动完毕后访问：
- **管理后台**: `http://localhost:8081`
- **服务端健康探针**: `http://localhost:8080/actuator/health`
- **Prometheus 指标**: `http://localhost:8080/actuator/prometheus`
- **Demo 接口**: `http://localhost:8082/api/demo/mode1/refund?amount=100`

---

## 3. 方案二：云原生多阶段构建（无需本地 Maven / Java）

若服务器是纯 Docker 环境，没有安装 Maven/JDK，可为每个模块使用多阶段构建 Dockerfile。

### 多阶段 Dockerfile 示例（以 `retry-server` 为例）：
```dockerfile
# 阶段 1：构建环境
FROM maven:3.8.6-openjdk-8 AS builder
WORKDIR /app
COPY pom.xml .
COPY retry-server/pom.xml retry-server/
COPY retry-client-sdk/pom.xml retry-client-sdk/
COPY retry-admin/pom.xml retry-admin/
COPY retry-example/pom.xml retry-example/
# 复制源码
COPY . .
RUN mvn clean package -pl retry-server -am -DskipTests

# 阶段 2：轻量运行环境
FROM eclipse-temurin:8-jre-alpine
WORKDIR /app
COPY --from=builder /app/retry-server/target/retry-server-*.jar app.jar
ENV JAVA_OPTS="-Xms512m -Xmx512m -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

---

## 4. 方案三：接入已有生产中间件（外置 MySQL / Redis）

在生产环境中，通常不会在 docker-compose 内部新建单机 MySQL 和 Redis，而是复用企业已有的数据库与 Redis 集群。

### `docker-compose.prod.yml` 编排配置：
```yaml
version: '3.8'

services:
  retry-server:
    image: eleccloud/retry-server:1.0.0
    container_name: retry-server-prod
    restart: always
    ports:
      - "8080:8080"
    environment:
      - TZ=Asia/Shanghai
      - SPRING_DATASOURCE_URL=jdbc:mysql://${PROD_MYSQL_HOST}:3306/retry_platform?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
      - SPRING_DATASOURCE_USERNAME=${PROD_MYSQL_USER}
      - SPRING_DATASOURCE_PASSWORD=${PROD_MYSQL_PWD}
      - SPRING_REDIS_HOST=${PROD_REDIS_HOST}
      - SPRING_REDIS_PORT=6379
      - SPRING_REDIS_PASSWORD=${PROD_REDIS_PWD}
      - SPRING_REDIS_REDISSON_CONFIG=singleServerConfig:\n  address: "redis://${PROD_REDIS_HOST}:6379"\n  password: "${PROD_REDIS_PWD}"\n  database: 2
    logging:
      driver: "json-file"
      options:
        max-size: "100m"
        max-file: "5"

  retry-admin:
    image: eleccloud/retry-admin:1.0.0
    container_name: retry-admin-prod
    restart: always
    ports:
      - "8081:8081"
    environment:
      - TZ=Asia/Shanghai
      - SPRING_DATASOURCE_URL=jdbc:mysql://${PROD_MYSQL_HOST}:3306/retry_platform?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
      - SPRING_DATASOURCE_USERNAME=${PROD_MYSQL_USER}
      - SPRING_DATASOURCE_PASSWORD=${PROD_MYSQL_PWD}
    depends_on:
      - retry-server
    logging:
      driver: "json-file"
      options:
        max-size: "100m"
        max-file: "3"
```

启动命令：
```bash
docker-compose -f docker-compose.prod.yml up -d
```

---

## 5. 核心排错指南与注意事项

| 现象 / 报错 | 原因分析 | 解决方案 |
|---|---|---|
| `COPY failed: no source files were specified` | 未执行 `mvn package` 即构建 Docker 镜像 | 先在根目录运行 `mvn clean package -DskipTests` |
| `Cannot create PoolableConnectionFactory` | MySQL 容器仍在初始化建表中，Server 抢先连接 | 在 Dockerfile / Compose 中添加 healthcheck 或 wait-for-it 脚本，或等 MySQL 启动后 `docker-compose restart retry-server` |
| `Redisson: unable to connect to Redis` | Redis 连接配置错误或密码不匹配 | 检查环境变量 `SPRING_REDIS_HOST`，单机 Compose 中应指向容器名 `redis` 而非 `127.0.0.1` |
| `Unknown database 'retry_platform'` | `init.sql` 没有自动挂载或 MySQL volume 已存在旧数据 | 清理旧卷 `docker-compose down -v`，重新启动确保 `db/init.sql` 自动执行 |
