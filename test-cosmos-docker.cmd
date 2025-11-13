@echo off
REM Cosmos DB Emulator - Manual Docker Test Script (Windows CMD)
REM This script helps test the Cosmos DB Emulator container setup

setlocal enabledelayedexpansion

echo.
echo ====================================================
echo  Cosmos DB Emulator - Docker Manual Test
echo ====================================================
echo.

REM Step 1: Check Docker
echo [1] Checking Docker daemon...
docker ps > nul 2>&1
if errorlevel 1 (
    echo   X Docker daemon NOT available
    echo   Please start Docker Desktop and try again
    exit /b 1
) else (
    echo   ✓ Docker daemon is available
)

REM Step 2: Check image
echo.
echo [2] Checking Cosmos Emulator image...
for /f "tokens=*" %%i in ('docker images --format "{{.Repository}}" ^| findstr cosmosdb') do (
    echo   ✓ Image found: %%i
    goto :image_found
)
echo   - Image not cached locally (will be pulled)
:image_found

REM Step 3: Stop any existing test container
echo.
echo [3] Cleaning up old test containers...
docker stop cosmos-manual-test > nul 2>&1
docker rm cosmos-manual-test > nul 2>&1
echo   ✓ Cleanup complete

REM Step 4: Start new container
echo.
echo [4] Starting Cosmos DB Emulator container...
echo   This may take 1-2 minutes on first run...
docker run --name cosmos-manual-test ^
  -p 8081:8081 ^
  -m 4g ^
  --cpus=2 ^
  --privileged ^
  -e COSMOS_DB_EMULATOR_PARTITION_COUNT=1 ^
  -e AZURE_COSMOS_EMULATOR_ENABLE_DATA_EXPLORER=true ^
  mcr.microsoft.com/cosmosdb/linux/azure-cosmos-emulator:latest

REM Container will run in foreground. Open another terminal and run step 5
echo.
echo ====================================================
echo  CONTAINER IS RUNNING
echo ====================================================
echo.
echo Open another Command Prompt window and run:
echo   telnet localhost 8081
echo.
echo If telnet succeeds, the port is mapped correctly!
echo.
echo To test the actual endpoint:
echo   curl -k https://127.0.0.1:8081/
echo.
echo When done testing, press Ctrl+C to stop the container
echo.

REM Cleanup when container stops
echo.
echo [5] Cleaning up...
docker stop cosmos-manual-test > nul 2>&1
docker rm cosmos-manual-test > nul 2>&1
echo   ✓ Test container stopped and removed
echo.
echo Test complete!

