# DCP Workflow Local Testing Script (Hardcoded Version)
# This is a backup version with hardcoded test steps (not reading from dcp-tests.yml)
# Usage: .\test-dcp-workflow-locally-hardcoded.ps1 [-SkipBuild] [-SkipTests] [-KeepRunning]

[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$SkipTests,
    [switch]$KeepRunning
)

$ErrorActionPreference = "Stop"
$RepoRoot = "C:\Users\igobalog\work\code\engineering\dsp-true-connector"

Write-Host "=== DCP Workflow Local Testing Script (Hardcoded Version) ===" -ForegroundColor Cyan
Write-Host "Repository: $RepoRoot`n" -ForegroundColor Gray

# Step 1: Validate YAML syntax
Write-Host "[1/6] Validating YAML syntax..." -ForegroundColor Yellow
try {
    if (Get-Command actionlint -ErrorAction SilentlyContinue) {
        $yamlPath = Join-Path $RepoRoot ".github\workflows\dcp-tests.yml"
        actionlint $yamlPath
        Write-Host "  [OK] YAML validation passed" -ForegroundColor Green
    } else {
        Write-Host "  [!] actionlint not found, skipping validation" -ForegroundColor DarkYellow
        Write-Host "    Install with: choco install actionlint" -ForegroundColor Gray
    }
} catch {
    Write-Host "  [X] YAML validation failed: $_" -ForegroundColor Red
    exit 1
}

# Step 2: Check Docker is running
Write-Host "`n[2/6] Checking Docker..." -ForegroundColor Yellow
try {
    docker ps | Out-Null
    Write-Host "  [OK] Docker is running" -ForegroundColor Green
} catch {
    Write-Host "  [X] Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    exit 1
}

# Step 3: Build DCP Issuer
if (-not $SkipBuild) {
    Write-Host "`n[3/6] Building DCP Issuer..." -ForegroundColor Yellow
    $dcpIssuerPath = Join-Path $RepoRoot "dcp\dcp-issuer"
    Push-Location $dcpIssuerPath
    try {
        Write-Host "  Building with Maven..." -ForegroundColor Gray
        mvn clean package -DskipTests -q

        $jarPath = "target\dcp-issuer-exec.jar"
        if (-not (Test-Path $jarPath)) {
            throw "JAR file not created at $jarPath"
        }
        Write-Host "  [OK] Maven build successful" -ForegroundColor Green

        Write-Host "  Building Docker image..." -ForegroundColor Gray
        docker build -t dcp-issuer:test . --quiet
        Write-Host "  [OK] Docker image built: dcp-issuer:test" -ForegroundColor Green
    } catch {
        Write-Host "  [X] DCP Issuer build failed: $_" -ForegroundColor Red
        Pop-Location
        exit 1
    }
    Pop-Location
} else {
    Write-Host "`n[3/6] Skipping DCP Issuer build (-SkipBuild flag)" -ForegroundColor DarkYellow
    # Verify image exists
    $imageExists = docker images -q dcp-issuer:test
    if (-not $imageExists) {
        Write-Host "  [X] Image dcp-issuer:test not found. Remove -SkipBuild flag." -ForegroundColor Red
        exit 1
    }
    Write-Host "  [OK] Using existing image: dcp-issuer:test" -ForegroundColor Green
}

# Step 4: Start Docker Compose
Write-Host "`n[4/6] Starting Docker Compose services..." -ForegroundColor Yellow
$dockerPath = Join-Path $RepoRoot "ci\docker"
Push-Location $dockerPath
try {
    # Clean up any existing services first to avoid conflicts
    Write-Host "  Checking for existing services..." -ForegroundColor Gray
    $existingContainers = docker compose -f docker-compose-dcp.yml ps -q 2>$null
    if ($existingContainers) {
        Write-Host "  Cleaning up existing services..." -ForegroundColor Gray
        docker compose -f docker-compose-dcp.yml down -v 2>&1 | Out-Null
        Start-Sleep -Seconds 3
    }

    Write-Host "  Starting services..." -ForegroundColor Gray

    # Capture output - store as array to preserve all lines
    $composeProcess = Start-Process -FilePath "docker" `
        -ArgumentList "compose -f docker-compose-dcp.yml --env-file .env up -d" `
        -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput "$env:TEMP\compose-stdout.txt" `
        -RedirectStandardError "$env:TEMP\compose-stderr.txt"

    $exitCode = $composeProcess.ExitCode
    $stdOut = Get-Content "$env:TEMP\compose-stdout.txt" -ErrorAction SilentlyContinue
    $stdErr = Get-Content "$env:TEMP\compose-stderr.txt" -ErrorAction SilentlyContinue

    # Clean up temp files
    Remove-Item "$env:TEMP\compose-stdout.txt" -ErrorAction SilentlyContinue
    Remove-Item "$env:TEMP\compose-stderr.txt" -ErrorAction SilentlyContinue

    # Check actual container status instead of just exit code
    # Docker Compose can return non-zero even when containers start successfully
    Write-Host "  Verifying container status..." -ForegroundColor Gray
    Start-Sleep -Seconds 5  # Give containers time to start

    $containerStatus = docker compose -f docker-compose-dcp.yml ps --format json 2>$null | ConvertFrom-Json
    $runningContainers = @($containerStatus | Where-Object { $_.State -eq "running" })
    $requiredContainers = @("dcp-issuer", "connector-a", "connector-b", "mongodb", "minio")
    $failedContainers = @()

    foreach ($required in $requiredContainers) {
        $found = $runningContainers | Where-Object { $_.Name -like "*$required*" }
        if (-not $found) {
            $failedContainers += $required
        }
    }

    if ($failedContainers.Count -gt 0) {
        Write-Host "  [X] Failed to start services - some containers not running" -ForegroundColor Red
        Write-Host "  Failed containers: $($failedContainers -join ', ')" -ForegroundColor Red

        if ($stdOut) {
            Write-Host "`n  Docker Compose STDOUT:" -ForegroundColor Yellow
            $stdOut | ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }
        }

        if ($stdErr) {
            Write-Host "`n  Docker Compose STDERR:" -ForegroundColor Red
            $stdErr | ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }
        }

        # Check container logs for failures
        Write-Host "`n  Checking logs of failed containers:" -ForegroundColor Yellow
        foreach ($failed in $failedContainers) {
            Write-Host "`n  Logs for $failed (last 20 lines):" -ForegroundColor Yellow
            docker logs $failed --tail 20 2>&1 | ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }
        }

        throw "Docker Compose failed to start all required containers"
    } elseif ($exitCode -ne 0) {
        # Exit code non-zero but containers are running - likely warnings, not errors
        Write-Host "  [!] Docker Compose returned exit code $exitCode, but all containers are running" -ForegroundColor DarkYellow
        if ($stdErr -and $stdErr.Length -gt 0) {
            Write-Host "  Warnings:" -ForegroundColor DarkYellow
            $stdErr | ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }
        }
    }

    Write-Host "  [OK] All services started successfully" -ForegroundColor Green

    # Show running containers
    Write-Host "`n  Running containers:" -ForegroundColor Gray
    docker compose -f docker-compose-dcp.yml ps

    # Wait for services
    Write-Host "`n  [*] Waiting 25 seconds for services to initialize..." -ForegroundColor Yellow
    Start-Sleep -Seconds 25

} catch {
    Write-Host "  [X] Failed to start services: $_" -ForegroundColor Red
    Pop-Location
    exit 1
}

# Step 5: Health checks
Write-Host "`n[5/6] Performing health checks..." -ForegroundColor Yellow
$healthChecks = @(
    @{ Name = "DCP Issuer"; Url = "http://localhost:8084/actuator/health"; Container = "dcp-issuer" },
    @{ Name = "Connector A"; Url = "http://localhost:8080/actuator/health"; Container = "connector-a" },
    @{ Name = "Connector B"; Url = "http://localhost:8090/actuator/health"; Container = "connector-b" }
)

$allHealthy = $true
foreach ($check in $healthChecks) {
    $retries = 10
    $healthy = $false

    Write-Host "  Checking $($check.Name)..." -ForegroundColor Gray
    for ($i = 1; $i -le $retries; $i++) {
        try {
            $response = Invoke-WebRequest -Uri $check.Url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Write-Host "    [OK] $($check.Name) is healthy" -ForegroundColor Green
                $healthy = $true
                break
            }
        } catch {
            if ($i -lt $retries) {
                Write-Host "    [*] Attempt $i of ${retries}: Waiting..." -ForegroundColor DarkGray
                Start-Sleep -Seconds 5
            }
        }
    }

    if (-not $healthy) {
        Write-Host "    [X] $($check.Name) failed health check" -ForegroundColor Red
        $allHealthy = $false

        # Show last 30 lines of logs
        Write-Host "`n    Last 30 lines of logs:" -ForegroundColor Yellow
        docker logs $check.Container --tail 30
    }
}

# Step 6: Run tests (HARDCODED VERSION)
if (-not $SkipTests -and $allHealthy) {
    Write-Host "`n[6/6] Running tests..." -ForegroundColor Yellow

    if (Get-Command newman -ErrorAction SilentlyContinue) {
        $testsPassed = $true

        try {
            # Step 1: Obtain Verifiable Credentials (MANDATORY)
            Write-Host "`n  Step 1: Obtaining Verifiable Credentials..." -ForegroundColor Cyan
            Write-Host "  (This step is mandatory - workflow stops if it fails)" -ForegroundColor Gray
            newman run "test-cases\dcp-obtain-vc\dcp-gha-tests.postman_collection.json" --bail
            Write-Host "  [OK] Step 1 completed - VCs obtained" -ForegroundColor Green
        } catch {
            Write-Host "  [X] Step 1 FAILED: Could not obtain Verifiable Credentials" -ForegroundColor Red
            Write-Host "  Cannot proceed with negotiation and data transfer tests without VCs" -ForegroundColor Red
            $testsPassed = $false
            $allHealthy = $false
            # Stop here - don't run Steps 2 & 3
            throw "Step 1 (VC obtainment) failed - stopping test execution"
        }

        # Only proceed if Step 1 succeeded
        if ($testsPassed) {
            try {
                # Step 2: Run Negotiation Tests
                Write-Host "`n  Step 2: Running Negotiation Tests..." -ForegroundColor Cyan
                newman run "test-cases\negotiation-api-without-counteroffer-tests\negotiation-api-without-counteroffer-tests.json" --bail
                Write-Host "  [OK] Step 2 completed - Negotiation tests passed" -ForegroundColor Green
            } catch {
                Write-Host "  [X] Step 2 FAILED: Negotiation tests failed" -ForegroundColor Red
                $testsPassed = $false
                $allHealthy = $false
            }

            try {
                # Step 3: Run Data Transfer Tests
                Write-Host "`n  Step 3: Running Data Transfer HTTP Pull Tests..." -ForegroundColor Cyan
                newman run "test-cases\datatransfer-api-http-pull-tests\datatransfer-api-http-pull-tests.json" --bail
                Write-Host "  [OK] Step 3 completed - Data Transfer tests passed" -ForegroundColor Green
            } catch {
                Write-Host "  [X] Step 3 FAILED: Data Transfer tests failed" -ForegroundColor Red
                $testsPassed = $false
                $allHealthy = $false
            }
        }

        # Summary
        Write-Host "`n  === Test Results Summary ===" -ForegroundColor Cyan
        if ($testsPassed) {
            Write-Host "  Step 1 - Obtain VCs: SUCCESS" -ForegroundColor Green
            Write-Host "  Step 2 - Negotiation Tests: SUCCESS" -ForegroundColor Green
            Write-Host "  Step 3 - Data Transfer Tests: SUCCESS" -ForegroundColor Green
            Write-Host "`n  [OK] All test stages passed!" -ForegroundColor Green
        } else {
            Write-Host "  [X] One or more test stages failed" -ForegroundColor Red
            Write-Host "  Check the output above for details" -ForegroundColor Red
        }
    } else {
        Write-Host "  [!] Newman not installed. Manual testing required:" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "  To install Newman:" -ForegroundColor White
        Write-Host "    npm install -g newman" -ForegroundColor Gray
        Write-Host ""
        Write-Host "  Or test manually in Postman (run in this order):" -ForegroundColor White
        Write-Host "    1. Import: test-cases\dcp-obtain-vc\dcp-gha-tests.postman_collection.json" -ForegroundColor Gray
        Write-Host "       (MUST run first - obtains VCs for connectors)" -ForegroundColor DarkGray
        Write-Host "    2. Import: test-cases\negotiation-api-without-counteroffer-tests\negotiation-api-without-counteroffer-tests.json" -ForegroundColor Gray
        Write-Host "    3. Import: test-cases\datatransfer-api-http-pull-tests\datatransfer-api-http-pull-tests.json" -ForegroundColor Gray
        Write-Host "    4. Run collections in order" -ForegroundColor Gray
        Write-Host ""
    }
} elseif ($SkipTests) {
    Write-Host "`n[6/6] Skipping tests (-SkipTests flag)" -ForegroundColor DarkYellow
} elseif (-not $allHealthy) {
    Write-Host "`n[6/6] Skipping tests (health checks failed)" -ForegroundColor Red
}

# Cleanup
Write-Host "`nCleanup..." -ForegroundColor Yellow
if (-not $KeepRunning) {
    try {
        Write-Host "  Stopping and removing services..." -ForegroundColor Gray
        $cleanupOutput = docker compose -f docker-compose-dcp.yml down -v 2>&1
        $exitCode = $LASTEXITCODE

        if ($exitCode -ne 0) {
            Write-Host "  [!] Warning during cleanup (exit code: $exitCode)" -ForegroundColor DarkYellow
            $cleanupOutput | ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }
        } else {
            Write-Host "  [OK] Services stopped and cleaned up" -ForegroundColor Green
        }
    } catch {
        Write-Host "  [!] Error during cleanup: $_" -ForegroundColor DarkYellow
    }
} else {
    Write-Host "  Services are still running (-KeepRunning flag)" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  Service URLs:" -ForegroundColor White
    Write-Host "    DCP Issuer:  http://localhost:8084/actuator/health" -ForegroundColor Gray
    Write-Host "    Connector A: http://localhost:8080/actuator/health" -ForegroundColor Gray
    Write-Host "    Connector B: http://localhost:8090/actuator/health" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  To stop services:" -ForegroundColor White
    Write-Host "    cd ci\docker" -ForegroundColor Gray
    Write-Host "    docker compose -f docker-compose-dcp.yml down -v" -ForegroundColor Gray
}

Pop-Location

# Summary
Write-Host "`n=== Testing Complete ===" -ForegroundColor Cyan
if ($allHealthy) {
    Write-Host "[OK] All checks passed! Workflow should work on GitHub." -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor White
    Write-Host "  1. git add ." -ForegroundColor Gray
    Write-Host "  2. git commit -m 'Add DCP integration tests'" -ForegroundColor Gray
    Write-Host "  3. git push" -ForegroundColor Gray
    Write-Host "  4. Monitor in GitHub Actions" -ForegroundColor Gray
    exit 0
} else {
    Write-Host "[X] Some checks failed. Fix issues before pushing." -ForegroundColor Red
    Write-Host ""
    Write-Host "To debug:" -ForegroundColor White
    Write-Host "  docker logs dcp-issuer" -ForegroundColor Gray
    Write-Host "  docker logs connector-a" -ForegroundColor Gray
    Write-Host "  docker logs connector-b" -ForegroundColor Gray
    exit 1
}

