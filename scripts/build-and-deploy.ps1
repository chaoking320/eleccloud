# ElecCloud Build and Deploy Script
# Usage: Execute from project root or scripts directory:
#   .\scripts\build-and-deploy.ps1

$rootDir = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path "$rootDir\pom.xml")) {
    $rootDir = Get-Location
}
Set-Location $rootDir

Write-Host "================================" -ForegroundColor Cyan
Write-Host "ElecCloud One-Click Deploy" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Clean build artifacts
Write-Host "[1/5] Cleaning old build artifacts..." -ForegroundColor Yellow
mvn clean
if ($LASTEXITCODE -ne 0) {
    Write-Host "Clean failed. Please verify Maven is properly installed." -ForegroundColor Red
    exit 1
}
Write-Host "Clean completed ✓" -ForegroundColor Green
Write-Host ""

# Step 2: Compile project
Write-Host "[2/5] Building project packages (skipping tests)..." -ForegroundColor Yellow
mvn package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed. Please check build logs above." -ForegroundColor Red
    exit 1
}
Write-Host "Build completed ✓" -ForegroundColor Green
Write-Host ""

# Step 3: Verify output JARs
Write-Host "[3/5] Verifying generated JAR artifacts..." -ForegroundColor Yellow
$serverJar = Get-ChildItem -Path "retry-server\target" -Filter "*-exec.jar" -ErrorAction SilentlyContinue
$adminJar = Get-ChildItem -Path "retry-admin\target" -Filter "*.jar" -ErrorAction SilentlyContinue
$exampleJar = Get-ChildItem -Path "retry-example\target" -Filter "*.jar" -ErrorAction SilentlyContinue

if ($null -eq $serverJar) {
    Write-Host "retry-server JAR not found!" -ForegroundColor Red
    exit 1
}
if ($null -eq $adminJar) {
    Write-Host "retry-admin JAR not found!" -ForegroundColor Red
    exit 1
}

Write-Host "retry-server: $($serverJar.Name) ✓" -ForegroundColor Green
Write-Host "retry-admin: $($adminJar.Name) ✓" -ForegroundColor Green
if ($null -ne $exampleJar) {
    Write-Host "retry-example: $($exampleJar.Name) ✓" -ForegroundColor Green
}
Write-Host ""

# Step 4: Stop existing containers
Write-Host "[4/5] Stopping existing Docker containers..." -ForegroundColor Yellow
docker compose -f docker-compose.simple.yml down 2>$null
Write-Host "Old containers stopped ✓" -ForegroundColor Green
Write-Host ""

# Step 5: Start Docker services
Write-Host "[5/5] Launching Docker services..." -ForegroundColor Yellow
Write-Host "Note: First launch may take 3-5 minutes to pull base images." -ForegroundColor Gray
docker compose -f docker-compose.simple.yml up -d --build --no-cache
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker startup failed!" -ForegroundColor Red
    Write-Host "Please ensure Docker daemon / Docker Desktop is running." -ForegroundColor Yellow
    exit 1
}
Write-Host "Docker services started successfully ✓" -ForegroundColor Green
Write-Host ""

# Waiting for services
Write-Host "Waiting for services to become healthy..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Display container status
Write-Host "Checking container status..." -ForegroundColor Yellow
docker compose -f docker-compose.simple.yml ps

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "Deployment Completed!" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Access URLs:" -ForegroundColor Yellow
Write-Host "  - Admin Dashboard:     http://localhost:8081" -ForegroundColor White
Write-Host "  - Server Health Check: http://localhost:8080/actuator/health" -ForegroundColor White
Write-Host ""
Write-Host "Helpful Commands:" -ForegroundColor Yellow
Write-Host "  - View server logs:    docker logs -f retry-server" -ForegroundColor White
Write-Host "  - Stop services:       docker compose -f docker-compose.simple.yml down" -ForegroundColor White
Write-Host "  - Restart services:    docker compose -f docker-compose.simple.yml restart" -ForegroundColor White
Write-Host ""
