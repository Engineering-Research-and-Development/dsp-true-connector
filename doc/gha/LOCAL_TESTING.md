# Running Integration Tests Locally

This guide explains how to run the GitHub Actions integration tests on your local machine for development and debugging.

## 📋 Prerequisites

### Required Tools
- **Docker Desktop** - Must be running
- **Maven** - Java build tool
- **Newman** - Postman CLI test runner
- **PowerShell** - For automation scripts (Windows)

### Optional Tools
- **actionlint** - GitHub Actions workflow linter
  - Install: `choco install actionlint` (Windows)
  - Install: `brew install actionlint` (macOS)

### Installation Commands

```bash
# Install Newman globally
npm install -g newman

# Verify installations
docker --version
mvn --version
newman --version
```

## 🚀 Quick Start

### Option 1: Automated Script (Windows)

The easiest way to run tests locally:

```powershell
# Navigate to documentation directory
cd doc/gha

# Full automated test (build, start, test, cleanup)
.\test-workflow-locally.ps1

# Skip build if images exist
.\test-workflow-locally.ps1 -SkipBuild

# Build and start services without running tests (for manual Postman testing)
.\test-workflow-locally.ps1 -SkipTests -KeepRunning

# Use existing build, just run tests
.\test-workflow-locally.ps1 -SkipBuild
```

**Alternative: Hardcoded Version**

If you prefer a more reliable version with hardcoded test steps (doesn't parse workflow YAML):

```powershell
.\test-workflow-locally-hardcoded.ps1
```

This version has the exact test sequence hardcoded for maximum reliability.

### Option 2: Batch File Launcher (Windows)

Double-click `test-workflow.bat` for an interactive menu:

```
1. Full automated test (build, start, test, cleanup)
2. Build and start services (keep running for manual Postman tests)
3. Use existing build, just start services
4. Cleanup only (stop and remove containers)
```

### Option 3: Manual Commands

For more control, run commands step-by-step.

## 🔧 Manual Testing - Standard Mode

### Step 1: Build Connector Image

```bash
# From repository root
cd connector

# Build with Maven
mvn clean package -DskipTests

# Build Docker image
docker build -t ghcr.io/engineering-research-and-development/dsp-true-connector:test .
```

### Step 2: Start Services

```bash
cd ../ci/docker

# Start standard mode services
docker compose -f docker-compose.yml --env-file .env up -d

# Wait for services to be ready
sleep 30
```

### Step 3: Verify Services

```bash
# Check container status
docker compose -f docker-compose.yml ps

# Check logs if needed
docker compose -f docker-compose.yml logs -f
```

### Step 4: Run Tests

```bash
# Run specific test collection
newman run test-cases/api-tests/api-endpoints-tests.json

newman run test-cases/dataset-api-tests/dataset-api-tests.json

newman run test-cases/negotiation-api-without-counteroffer-tests/negotiation-api-without-counteroffer-tests.json

newman run test-cases/datatransfer-api-http-pull-tests/datatransfer-api-http-pull-tests.json
```

### Step 5: Cleanup

```bash
# Stop and remove containers
docker compose -f docker-compose.yml --env-file .env down -v
```

## 🔐 Manual Testing - DCP Mode

### Step 1: Build DCP Issuer Image

```bash
# From repository root
cd dcp/dcp-issuer

# Build with Maven
mvn clean package -DskipTests

# Build Docker image
docker build -t dcp-issuer:test .
```

### Step 2: Build Connector Image (if needed)

```bash
cd ../../connector
mvn clean package -DskipTests
docker build -t ghcr.io/engineering-research-and-development/dsp-true-connector:test .
```

### Step 3: Start DCP Services

```bash
cd ../ci/docker

# Start DCP mode services
docker compose -f docker-compose-dcp.yml --env-file .env up -d

# Wait for services to initialize
sleep 45
```

### Step 4: Verify Services with Health Checks

```bash
# Check DCP Issuer health (retry until ready)
for i in {1..10}; do
  curl -f http://localhost:8084/actuator/health && break || sleep 5
done

# Check Connector A health
for i in {1..10}; do
  curl -f http://localhost:8080/actuator/health && break || sleep 5
done

# Check Connector B health
for i in {1..10}; do
  curl -f http://localhost:8090/actuator/health && break || sleep 5
done
```

**PowerShell equivalent**:
```powershell
# DCP Issuer
1..10 | ForEach-Object {
    try { Invoke-WebRequest -Uri http://localhost:8084/actuator/health -UseBasicParsing; break }
    catch { Start-Sleep -Seconds 5 }
}
```

### Step 5: Obtain Verifiable Credentials (MANDATORY)

This step **must succeed** before running other tests:

```bash
# STEP 1: Get VCs (REQUIRED - don't skip!)
newman run test-cases/dcp-obtain-vc/dcp-gha-tests.postman_collection.json
```

**Why is this mandatory?**
- DCP mode tests require Verifiable Credentials for authentication
- This collection obtains VCs for both Connector A and Connector B
- Subsequent tests will fail without VCs

### Step 6: Run DCP Tests

Only run these **after** Step 5 succeeds:

```bash
# STEP 2: Negotiation tests with DCP
newman run test-cases/negotiation-api-without-counteroffer-tests/negotiation-api-without-counteroffer-tests.json

# STEP 3: Data transfer tests with DCP
newman run test-cases/datatransfer-api-http-pull-tests/datatransfer-api-http-pull-tests.json

newman run test-cases/datatransfer-api-http-push-tests/datatransfer-api-http-push-tests.json
```

### Step 7: Cleanup

```bash
# Stop and remove all DCP services
docker compose -f docker-compose-dcp.yml --env-file .env down -v
```

## 📊 Understanding the Automated Script

The PowerShell script `test-workflow-locally.ps1` performs these steps:

### 1. Validate YAML Syntax
```powershell
# Checks workflow file syntax if actionlint is installed
actionlint .github/workflows/integration-tests.yml
```

### 2. Check Docker Status
```powershell
# Verifies Docker daemon is running
docker ps
```

### 3. Build Images
```powershell
# Builds DCP Issuer (skip with -SkipBuild)
cd dcp/dcp-issuer
mvn clean package -DskipTests
docker build -t dcp-issuer:test .
```

### 4. Start Services
```powershell
# Starts docker-compose with proper error handling
docker compose -f docker-compose-dcp.yml --env-file .env up -d

# Verifies all required containers are running:
# - dcp-issuer
# - connector-a
# - connector-b
# - mongodb
# - minio
```

### 5. Health Checks with Retries
```powershell
# Checks each service up to 10 times with 5-second delays
$healthChecks = @(
    @{ Name = "DCP Issuer"; Url = "http://localhost:8084/actuator/health" },
    @{ Name = "Connector A"; Url = "http://localhost:8080/actuator/health" },
    @{ Name = "Connector B"; Url = "http://localhost:8090/actuator/health" }
)

# Retries: 10 attempts × 5 seconds = 50 seconds timeout per service
```

### 6. Run Tests Dynamically
```powershell
# Parses integration-tests.yml to extract test collections
# Runs each collection in order
# Tracks pass/fail status for each
```

### 7. Cleanup
```powershell
# Always runs (unless -KeepRunning flag used)
docker compose -f docker-compose-dcp.yml down -v
```

## 🎯 Script Parameters

### `-SkipBuild`
Skips Maven and Docker image building. Useful when images already exist.

```powershell
.\test-workflow-locally.ps1 -SkipBuild
```

**Use when**:
- Images already built
- Testing configuration changes
- Running tests multiple times

### `-SkipTests`
Starts services but doesn't run Newman tests. For manual testing in Postman.

```powershell
.\test-workflow-locally.ps1 -SkipTests -KeepRunning
```

**Use when**:
- Developing new test collections
- Debugging API calls in Postman
- Need interactive environment

### `-KeepRunning`
Prevents automatic cleanup, leaves services running.

```powershell
.\test-workflow-locally.ps1 -KeepRunning
```

**Use when**:
- Inspecting service state after tests
- Running additional manual tests
- Debugging issues

### Combining Parameters

```powershell
# Quick test with existing images
.\test-workflow-locally.ps1 -SkipBuild

# Start services for Postman testing (no build)
.\test-workflow-locally.ps1 -SkipBuild -SkipTests -KeepRunning

# Full test but keep services running for inspection
.\test-workflow-locally.ps1 -KeepRunning
```

## 🐛 Troubleshooting

### Services Won't Start

**Check Docker status**:
```bash
docker ps
docker compose -f docker-compose-dcp.yml ps
```

**Check logs**:
```bash
# All services
docker compose -f docker-compose-dcp.yml logs

# Specific service
docker logs dcp-issuer
docker logs connector-a
```

**Common issues**:
- Port conflicts (8080, 8084, 8090, 27017, 9000)
- Insufficient memory allocated to Docker
- Previous containers not cleaned up

**Solution**:
```bash
# Full cleanup
docker compose -f docker-compose-dcp.yml down -v
docker system prune -f

# Check port usage
netstat -ano | findstr "8080"  # Windows
lsof -i :8080                   # Linux/Mac
```

### Health Checks Failing

**Symptoms**: "DCP Issuer not ready yet, waiting..." repeated 10 times

**Diagnosis**:
```bash
# Check if container is running
docker ps | grep dcp-issuer

# Check logs for errors
docker logs dcp-issuer --tail 50

# Check resource usage
docker stats --no-stream
```

**Common causes**:
- Service startup taking too long
- Configuration errors in application properties
- Database connection issues
- Out of memory

**Solution**:
```bash
# Increase wait time in script
Start-Sleep -Seconds 60  # Instead of 25

# Check MongoDB is ready
docker logs mongodb --tail 20

# Restart specific service
docker restart dcp-issuer
```

### VC Obtainment Fails

**Symptoms**: "Obtain Verifiable Credentials" test fails

**This is critical** - other tests will fail without VCs.

**Diagnosis**:
```bash
# Check DCP Issuer logs
docker logs dcp-issuer --tail 100

# Verify DCP Issuer is healthy
curl http://localhost:8084/actuator/health

# Check connector logs
docker logs connector-a --tail 50
docker logs connector-b --tail 50
```

**Common causes**:
- DCP Issuer not fully initialized
- Connector DID configuration issues
- Network connectivity between containers

**Solution**:
```bash
# Restart and wait longer
docker compose -f docker-compose-dcp.yml restart dcp-issuer
sleep 30

# Re-run VC obtainment
newman run test-cases/dcp-obtain-vc/dcp-gha-tests.postman_collection.json -v
```

### Tests Fail Locally but Pass in CI

**Common causes**:
- Timing differences (local slower/faster)
- Environment variable differences
- Port conflicts with local services
- Different Docker versions

**Solution**:
```bash
# Match CI environment as closely as possible
docker --version  # Check version matches CI

# Use exact same .env file
cat ci/docker/.env

# Check for conflicting local services
docker ps -a
```

### Newman Output Shows Garbled Characters

**Symptoms**: Newman output shows strange characters like `Ôöé`, `ÔöÇ`, `Ôöñ` instead of nice boxes/lines

**Example**:
```
Ôöé total data received: 35.53kB (approx)                            Ôöé
Ôö£ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ... [more garbled characters]
```

**Cause**: Windows Command Prompt (cmd.exe) can't properly display Unicode box-drawing characters that Newman uses for progress bars and summaries.

**Solutions** (Already Implemented):

The scripts have been **automatically fixed** with:
1. **UTF-8 encoding** in batch file: `chcp 65001`
2. **PowerShell encoding**: `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`
3. **Newman flag**: `--disable-unicode` to use ASCII characters instead

**If issues persist**:

**Option 1: Run directly in PowerShell** (recommended):
```powershell
# Instead of using test-workflow.bat, run PowerShell directly
cd doc/gha
.\test-workflow-locally.ps1
```

**Option 2: Use Windows Terminal** (modern terminal):
- Download from Microsoft Store
- Has full Unicode support
- Run your scripts from Windows Terminal instead of cmd.exe

**Option 3: Disable Newman Reporter** (if you just want pass/fail):
```powershell
# Edit the script and add --reporter-cli-no-summary
newman run collection.json --bail --disable-unicode --reporter-cli-no-summary
```

**Technical Explanation**:
- Newman uses Unicode characters (U+2500 series) for drawing boxes
- Old Windows Command Prompt uses codepage 437 or 850 by default
- UTF-8 (codepage 65001) is needed for Unicode display
- The `--disable-unicode` flag makes Newman use ASCII fallback characters

### Maven Build Fails

**Symptoms**: "JAR file not created"

**Diagnosis**:
```bash
# Run Maven with verbose output
mvn clean package -X

# Check Java version
java -version  # Should be Java 17

# Check for port conflicts during tests
mvn clean test
```

**Solution**:
```bash
# Skip tests during build
mvn clean package -DskipTests

# Clean Maven cache if needed
mvn clean install -U

# Check for sufficient disk space
df -h  # Linux/Mac
```

## 📈 Performance Tips

### Speed Up Builds

```bash
# Use Maven offline mode (after first build)
mvn -o clean package -DskipTests

# Parallel builds
mvn -T 4 clean package -DskipTests
```

### Reuse Images

```bash
# Tag images to avoid rebuilds
docker tag dcp-issuer:test dcp-issuer:latest

# Use -SkipBuild flag
.\test-workflow-locally.ps1 -SkipBuild
```

### Faster Startup

```bash
# Increase Docker resources
# Docker Desktop → Settings → Resources
# - CPUs: 4+
# - Memory: 8GB+
# - Disk: 50GB+
```

## 🔍 Advanced Usage

### Debug Mode

Run services with debug ports exposed:

```bash
# Edit docker-compose-dcp.yml temporarily
# Add debug port to connector-a:
environment:
  - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
ports:
  - "5005:5005"  # Debug port

# Restart service
docker compose -f docker-compose-dcp.yml up -d connector-a

# Attach debugger to localhost:5005
```

### Custom Test Collections

```bash
# Run your own collection
newman run path/to/your-collection.json \
  --environment path/to/environment.json \
  --reporters cli,json \
  --reporter-json-export results.json
```

### Inspect Service State

```bash
# Keep services running
.\test-workflow-locally.ps1 -KeepRunning

# Open Postman and test interactively
# Use collections in ci/docker/test-cases/

# Check MongoDB data
docker exec -it mongodb mongosh
> use connector_db
> db.negotiations.find()

# Check MinIO storage
# Open browser: http://localhost:9001
# Login: minioadmin / minioadmin
```

## 📞 Getting Help

### Script Output

The automated script provides detailed output:
- ✅ Green: Success
- ❌ Red: Failure
- ⚠️ Yellow: Warnings
- ℹ️ Gray: Information

### Common Error Messages

| Error | Meaning | Solution |
|-------|---------|----------|
| "Docker is not running" | Docker daemon not started | Start Docker Desktop |
| "Image dcp-issuer:test not found" | Image not built | Remove `-SkipBuild` flag |
| "Health check failed" | Service not ready | Check logs, increase wait time |
| "VCs not obtained" | VC test failed | Check DCP Issuer logs |
| "Port already in use" | Conflicting service | Stop conflicting service or change ports |

### Resources

- **Workflow file**: `.github/workflows/integration-tests.yml`
- **Docker compose**: `ci/docker/docker-compose-dcp.yml`
- **Test collections**: `ci/docker/test-cases/`
- **Main documentation**: [README.md](README.md)
- **Architecture**: [ARCHITECTURE_DIAGRAM.md](ARCHITECTURE_DIAGRAM.md)

---

**Script Location**: `doc/gha/test-workflow-locally.ps1`  
**Batch Launcher**: `doc/gha/test-workflow.bat`  
**Last Updated**: February 2026

