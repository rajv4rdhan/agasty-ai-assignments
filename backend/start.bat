@echo off
REM RAG Application Quick Start Script

echo.
echo ^|========================================^|
echo ^|  RAG Application Quick Start          ^|
echo ^|========================================^|
echo.

REM Check if .env exists
if not exist .env (
    echo [WARNING] .env file not found. Creating from .env.example...
    copy .env.example .env
    echo [OK] Created .env file
    echo.
    echo [IMPORTANT] Edit .env and add your GEMINI_API_KEY before continuing!
    echo.
    pause
)

REM Check if Docker is running
docker info >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker is not running. Please start Docker Desktop and try again.
    pause
    exit /b 1
)

echo [OK] Docker is running
echo.

REM Ask user which mode to run
echo Choose deployment mode:
echo 1^) Full Stack ^(Docker^) - PostgreSQL + Java App in Docker
echo 2^) Development Mode - Only PostgreSQL in Docker, run app locally
echo.
set /p choice="Enter choice (1 or 2): "

if "%choice%"=="1" (
    echo.
    echo [Docker] Starting Full Stack with Docker Compose...
    echo.
    docker compose up -d
    echo.
    echo [OK] Services started!
    echo.
    echo [Info] Checking service status...
    timeout /t 5 /nobreak >nul
    docker compose ps
    echo.
    echo [Info] View logs with: docker compose logs -f
    echo [Info] Application URL: http://localhost:8080
    echo [Info] Health Check: http://localhost:8080/actuator/health
    echo.
    echo [Info] Stop services with: docker compose down
    echo.
) else if "%choice%"=="2" (
    echo.
    echo [Docker] Starting PostgreSQL only...
    echo.
    docker compose -f docker-compose.dev.yml up -d
    echo.
    echo [OK] PostgreSQL started!
    echo.
    echo Now run the Java application:
    echo   - From IDE with VM options: -Xms512m -Xmx2048m
    echo   - Or run: run.bat
    echo.
) else (
    echo [ERROR] Invalid choice
    pause
    exit /b 1
)

pause
