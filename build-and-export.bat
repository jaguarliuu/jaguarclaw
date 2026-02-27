@echo off
REM ========================================
REM JaguarClaw 一键打包脚本 (Windows)
REM 构建所有镜像并导出为 tar 包
REM ========================================

setlocal enabledelayedexpansion

REM 版本号
set VERSION=%1
if "%VERSION%"=="" set VERSION=latest

set OUTPUT_FILE=jaguarclaw-%VERSION%.tar.gz

echo.
echo   __  __ _       _  _____ _
echo  ^|  \/  (_)     (_)/ ____^| ^|
echo  ^| \  / ^|_ _ __  _^| ^|    ^| ^| __ ___      __
echo  ^| ^|\/^| ^| ^| '_ \^| ^| ^|    ^| ^|/ _` \ \ /\ / /
echo  ^| ^|  ^| ^| ^| ^| ^| ^| ^| ^|____^| ^| (_^| ^|\ V  V /
echo  ^|_^|  ^|_^|_^|_^| ^|_^|_^|\_____|_^|\__,_^| \_/\_/
echo.
echo Docker Build and Export Script
echo Version: %VERSION%
echo Output: %OUTPUT_FILE%
echo.

REM 检查 Docker
docker --version >nul 2>&1
if errorlevel 1 (
    echo Error: Docker is not installed
    exit /b 1
)

REM Step 1: 构建后端镜像
echo [1/4] Building backend image...
docker build -t jaguarclaw/backend:%VERSION% -f Dockerfile .
if errorlevel 1 (
    echo Error: Failed to build backend image
    exit /b 1
)
echo Backend image built

REM Step 2: 构建前端镜像
echo [2/4] Building frontend image...
docker build -t jaguarclaw/frontend:%VERSION% -f jaguarclaw-ui/Dockerfile jaguarclaw-ui/
if errorlevel 1 (
    echo Error: Failed to build frontend image
    exit /b 1
)
echo Frontend image built

REM Step 3: 拉取依赖镜像
echo [3/4] Pulling dependency images...
docker pull pgvector/pgvector:pg16
echo Dependency images ready

REM Step 4: 打包所有镜像
echo [4/4] Exporting images...

REM 创建临时目录
set TEMP_DIR=%TEMP%\jaguarclaw-build-%RANDOM%
mkdir "%TEMP_DIR%"

REM 导出镜像
docker save jaguarclaw/backend:%VERSION% -o "%TEMP_DIR%\backend.tar"
docker save jaguarclaw/frontend:%VERSION% -o "%TEMP_DIR%\frontend.tar"
docker save pgvector/pgvector:pg16 -o "%TEMP_DIR%\postgres.tar"

REM 复制部署文件
copy docker-compose.prod.yml "%TEMP_DIR%\docker-compose.yml"
copy .env.example "%TEMP_DIR%\.env.example"

REM 创建部署说明
(
echo # JaguarClaw Deploy Guide
echo.
echo ## Quick Start
echo.
echo ```bash
echo # Load images
echo docker load -i backend.tar
echo docker load -i frontend.tar
echo docker load -i postgres.tar
echo.
echo # Configure
echo cp .env.example .env
echo # Edit .env with your LLM credentials
echo.
echo # Start
echo docker-compose up -d
echo ```
) > "%TEMP_DIR%\README.md"

REM 创建快速部署脚本
(
echo #!/bin/bash
echo docker load -i backend.tar
echo docker load -i frontend.tar
echo docker load -i postgres.tar
echo echo "Images loaded. Run: docker-compose up -d"
) > "%TEMP_DIR%\deploy.sh"

REM 打包（使用 tar，Windows 10+ 自带）
tar -czf %OUTPUT_FILE% -C "%TEMP_DIR%" .

REM 清理
rmdir /s /q "%TEMP_DIR%"

echo.
echo ========================================
echo Build completed successfully!
echo ========================================
echo.
echo Output file: %OUTPUT_FILE%
echo.
echo Deploy on target machine:
echo   tar -xzf %OUTPUT_FILE%
echo   ./deploy.sh
echo   cp .env.example .env
echo   docker-compose up -d
echo.

endlocal
