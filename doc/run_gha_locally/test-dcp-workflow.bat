@echo off
REM Quick test launcher for DCP workflow
REM Double-click this file or run from command prompt

echo =====================================
echo DCP Workflow Local Testing
echo =====================================
echo.

REM Check if PowerShell is available
where powershell >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: PowerShell not found!
    pause
    exit /b 1
)

REM Check if Docker is running
docker ps >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Docker is not running!
    echo Please start Docker Desktop and try again.
    pause
    exit /b 1
)

echo Choose testing mode:
echo.
echo 1. Full automated test (build, start, test, cleanup)
echo 2. Build and start services (keep running for manual Postman tests)
echo 3. Use existing build, just start services
echo 4. Cleanup only (stop and remove containers)
echo.
set /p choice="Enter choice (1-4): "

if "%choice%"=="1" (
    echo.
    echo Running full automated test...
    powershell -ExecutionPolicy Bypass -File .\test-dcp-workflow-locally.ps1
) else if "%choice%"=="2" (
    echo.
    echo Building and starting services...
    echo Services will remain running for manual testing in Postman.
    echo.
    powershell -ExecutionPolicy Bypass -File .\test-dcp-workflow-locally.ps1 -SkipTests -KeepRunning
    echo.
    echo =====================================
    echo Services are running!
    echo =====================================
    echo.
    echo Import these collections into Postman:
    echo   1. ci\docker\test-cases\dcp-credential-issuance-tests\dcp-credential-issuance-tests.json
    echo   2. ci\docker\test-cases\dcp-verifiable-presentation-tests\dcp-verifiable-presentation-tests.json
    echo.
    echo Service URLs:
    echo   DCP Issuer:  http://localhost:8084/actuator/health
    echo   Connector A: http://localhost:8080/actuator/health
    echo   Connector B: http://localhost:8090/actuator/health
    echo.
    echo To stop services, run this batch file again and choose option 4.
    echo.
) else if "%choice%"=="3" (
    echo.
    echo Starting services with existing build...
    powershell -ExecutionPolicy Bypass -File .\test-dcp-workflow-locally.ps1 -SkipBuild -SkipTests -KeepRunning
) else if "%choice%"=="4" (
    echo.
    echo Stopping and cleaning up services...
    cd ci\docker
    docker compose -f docker-compose-dcp.yml down -v
    cd ..\..
    echo.
    echo Cleanup complete!
) else (
    echo Invalid choice. Please run again and select 1-4.
)

echo.
pause

