# ElecCloud 一键编译和部署脚本
# 使用方法：在项目根目录执行 .\build-and-deploy.ps1

Write-Host "================================" -ForegroundColor Cyan
Write-Host "ElecCloud 一键部署脚本" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# 步骤 1: 清理旧的构建产物
Write-Host "[1/5] 清理旧的构建产物..." -ForegroundColor Yellow
mvn clean
if ($LASTEXITCODE -ne 0) {
    Write-Host "清理失败，请检查 Maven 是否已安装" -ForegroundColor Red
    exit 1
}
Write-Host "清理完成 ✓" -ForegroundColor Green
Write-Host ""

# 步骤 2: 编译项目
Write-Host "[2/5] 编译项目（跳过测试）..." -ForegroundColor Yellow
mvn package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "编译失败，请检查错误信息" -ForegroundColor Red
    exit 1
}
Write-Host "编译完成 ✓" -ForegroundColor Green
Write-Host ""

# 步骤 3: 验证 JAR 包是否生成
Write-Host "[3/5] 验证 JAR 包..." -ForegroundColor Yellow
$serverJar = Get-ChildItem -Path "retry-server\target" -Filter "*-exec.jar" -ErrorAction SilentlyContinue
$adminJar = Get-ChildItem -Path "retry-admin\target" -Filter "*.jar" -ErrorAction SilentlyContinue
$exampleJar = Get-ChildItem -Path "retry-example\target" -Filter "*.jar" -ErrorAction SilentlyContinue

if ($null -eq $serverJar) {
    Write-Host "retry-server JAR 包不存在！" -ForegroundColor Red
    exit 1
}
if ($null -eq $adminJar) {
    Write-Host "retry-admin JAR 包不存在！" -ForegroundColor Red
    exit 1
}

Write-Host "retry-server: $($serverJar.Name) ✓" -ForegroundColor Green
Write-Host "retry-admin: $($adminJar.Name) ✓" -ForegroundColor Green
if ($null -ne $exampleJar) {
    Write-Host "retry-example: $($exampleJar.Name) ✓" -ForegroundColor Green
}
Write-Host ""

# 步骤 4: 停止旧的容器（如果存在）
Write-Host "[4/5] 停止旧的 Docker 容器..." -ForegroundColor Yellow
docker compose -f docker-compose.simple.yml down 2>$null
Write-Host "旧容器已停止 ✓" -ForegroundColor Green
Write-Host ""

# 步骤 5: 启动 Docker 服务（不使用缓存）
Write-Host "[5/5] 启动 Docker 服务..." -ForegroundColor Yellow
Write-Host "提示: 首次启动需要下载镜像，可能需要 3-5 分钟" -ForegroundColor Gray
docker compose -f docker-compose.simple.yml up -d --build --no-cache
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker 启动失败！" -ForegroundColor Red
    Write-Host "请检查 Docker Desktop 是否正在运行" -ForegroundColor Yellow
    exit 1
}
Write-Host "Docker 服务启动成功 ✓" -ForegroundColor Green
Write-Host ""

# 等待服务启动
Write-Host "等待服务启动..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# 检查容器状态
Write-Host "检查容器状态..." -ForegroundColor Yellow
docker compose -f docker-compose.simple.yml ps

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "部署完成！" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "访问地址：" -ForegroundColor Yellow
Write-Host "  - 管理后台: http://localhost:8081" -ForegroundColor White
Write-Host "  - Server 健康检查: http://localhost:8080/actuator/health" -ForegroundColor White
Write-Host ""
Write-Host "常用命令：" -ForegroundColor Yellow
Write-Host "  - 查看日志: docker logs -f retry-server" -ForegroundColor White
Write-Host "  - 停止服务: docker compose -f docker-compose.simple.yml down" -ForegroundColor White
Write-Host "  - 重启服务: docker compose -f docker-compose.simple.yml restart" -ForegroundColor White
Write-Host ""
