# Integration Tests Workflow

Documentation for the DSP True Connector integration testing workflow that runs tests in parallel using GitHub Actions matrix strategy.

## 🚀 Quick Start

**Workflow Location**: `.github/workflows/integration-tests.yml`

The workflow automatically runs on:
- Push to `feature/*`, `hotfix/*`, `fix/*`, `main`, `develop` branches
- Pull requests to `main` or `develop`
- Manual trigger via GitHub Actions UI

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| **[INTEGRATION_TESTS_GUIDE.md](INTEGRATION_TESTS_GUIDE.md)** | 📖 Complete reference guide |
| **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)** | ⚡ Quick lookups and examples |
| **[ARCHITECTURE_DIAGRAM.md](ARCHITECTURE_DIAGRAM.md)** | 🎨 Visual workflow diagrams |

## 🏗️ Workflow Architecture

```
integration-tests.yml
│
├─ Build Phase (Parallel, ~7 min each)
│  ├─ build-connector-image
│  │  └─ Maven build → Docker image → Push to GHCR
│  │
│  └─ build-dcp-issuer-image
│     └─ Maven build DCP issuer → Docker image → Save as artifact
│
└─ Test Phase (Matrix: 9 parallel jobs, ~5-10 min each)
   │
   ├─ Standard Mode Only (3 jobs)
   │  Uses: docker-compose.yml
   │  │
   │  ├─ api-endpoints-tests
   │  ├─ dataset-api-tests
   │  └─ connector-tests
   │
   └─ Both Standard & DCP Modes (6 jobs)
      │
      ├─ Standard Mode (3 jobs)
      │  Uses: docker-compose.yml
      │  ├─ negotiation-api-without-counteroffer-tests
      │  ├─ datatransfer-api-http-pull-tests
      │  └─ datatransfer-api-http-push-tests
      │
      └─ DCP Mode (3 jobs)
         Uses: docker-compose-dcp.yml + DCP Issuer
         ├─ negotiation-api-without-counteroffer-tests (dcp)
         ├─ datatransfer-api-http-pull-tests (dcp)
         └─ datatransfer-api-http-push-tests (dcp)
         
         Each DCP job:
         1. Downloads DCP issuer artifact
         2. Starts docker-compose-dcp.yml
         3. Waits for services (health checks)
         4. Obtains Verifiable Credentials
         5. Runs test collection
         6. Cleanup
```

**Total Execution Time**: ~15-20 minutes

## 🎓 Key Concepts

### Matrix Strategy
Tests are defined once and run in multiple configurations using GitHub Actions matrix:

```yaml
matrix:
  include:
    - test-suite: datatransfer-api-http-pull-tests
      collection: ./ci/docker/test-cases/datatransfer-api-http-pull-tests/test.json
      docker-compose-file: ./ci/docker/docker-compose.yml
      mode: standard
      requires-dcp: false
    
    - test-suite: datatransfer-api-http-pull-tests
      collection: ./ci/docker/test-cases/datatransfer-api-http-pull-tests/test.json
      docker-compose-file: ./ci/docker/docker-compose-dcp.yml
      mode: dcp
      requires-dcp: true
      requires-vc: true
```

This creates **2 parallel jobs** with different configurations.

### Test Modes

| Mode | Docker Compose | Services | Use Case |
|------|----------------|----------|----------|
| **Standard** | `docker-compose.yml` | MongoDB, MinIO, Connector | Basic DSP protocol tests |
| **DCP** | `docker-compose-dcp.yml` | MongoDB, MinIO, Connector A/B, DCP Issuer | Tests with Verifiable Credentials |

### Conditional Execution

Steps run only when needed based on matrix parameters:

| Step | Condition | Purpose |
|------|-----------|---------|
| Download DCP issuer | `requires-dcp: true` | Get DCP issuer Docker image artifact |
| DCP health checks | `requires-dcp: true` | Verify DCP Issuer, Connector A/B are ready |
| Obtain VCs | `requires-vc: true` | Get Verifiable Credentials for authentication |
| Collect logs | `failure()` | Gather debugging information |
| Cleanup | `always()` | Remove Docker resources |

### Performance Optimizations
- **Parallel execution**: 9 jobs run simultaneously → ~60-70% faster
- **Artifact reuse**: DCP issuer built once, downloaded by 3 tests
- **Maven caching**: Dependencies cached between runs
- **Smart steps**: Only necessary steps execute per test

## 📝 Adding New Tests

### Option 1: Standard Mode Only

For tests that don't require DCP features, add a single matrix entry:

```yaml
- test-suite: my-new-api-test
  collection: ./ci/docker/test-cases/my-new-api-test/test.json
  docker-compose-file: ./ci/docker/docker-compose.yml
  mode: standard
  requires-dcp: false
```

**Result**: Creates 1 job that runs with standard connector setup.

### Option 2: DCP Mode Only

For DCP-specific features (e.g., credential issuance tests):

```yaml
- test-suite: my-dcp-specific-test
  collection: ./ci/docker/test-cases/my-dcp-specific-test/test.json
  docker-compose-file: ./ci/docker/docker-compose-dcp.yml
  mode: dcp
  requires-dcp: true
  requires-vc: true  # Set to false if VC obtainment not needed
```

**Result**: Creates 1 job with DCP issuer and VC authentication.

### Option 3: Both Modes (Recommended for Protocol Tests)

For tests validating core DSP protocol behavior in both environments:

```yaml
# Standard mode
- test-suite: my-protocol-test
  collection: ./ci/docker/test-cases/my-protocol-test/test.json
  docker-compose-file: ./ci/docker/docker-compose.yml
  mode: standard
  requires-dcp: false

# DCP mode
- test-suite: my-protocol-test
  collection: ./ci/docker/test-cases/my-protocol-test/test.json
  docker-compose-file: ./ci/docker/docker-compose-dcp.yml
  mode: dcp
  requires-dcp: true
  requires-vc: true
```

**Result**: Creates 2 parallel jobs testing the same behavior in different environments.

## 🧪 Local Testing

You can run the integration tests locally on your machine for development and debugging.

### Quick Start

**Automated Script (Windows)**:
```powershell
cd doc/gha
.\test-workflow-locally.ps1                    # Full automated test
.\test-workflow-locally.ps1 -SkipBuild         # Use existing images
.\test-workflow-locally.ps1 -SkipTests -KeepRunning  # Start services for Postman
```

**Interactive Menu (Windows)**:
```bash
# Double-click test-workflow.bat for interactive menu
cd doc/gha
.\test-workflow.bat
```

### Manual Testing

Test your changes locally before pushing to GitHub:

#### Standard Mode
```bash
cd ci/docker
docker compose -f docker-compose.yml --env-file .env up -d
sleep 30
newman run test-cases/your-test/test.json
docker compose -f docker-compose.yml --env-file .env down -v
```

#### DCP Mode
```bash
cd ci/docker
docker compose -f docker-compose-dcp.yml --env-file .env up -d
sleep 45

# Wait for services to be ready
curl -f http://localhost:8084/actuator/health  # DCP Issuer
curl -f http://localhost:8080/actuator/health  # Connector A
curl -f http://localhost:8090/actuator/health  # Connector B

# MANDATORY: Obtain Verifiable Credentials first
newman run test-cases/dcp-obtain-vc/dcp-gha-tests.postman_collection.json

# Run your test
newman run test-cases/your-test/test.json

# Cleanup
docker compose -f docker-compose-dcp.yml --env-file .env down -v
```

**📖 For complete local testing guide, including troubleshooting and advanced usage, see [LOCAL_TESTING.md](LOCAL_TESTING.md)**

## 📊 Viewing Results

### GitHub Actions UI
1. Go to **Actions** tab in GitHub repository
2. Click on latest "Integration Tests" workflow run
3. View individual matrix job results

### Job Naming Convention
Jobs are named: `{test-suite} ({mode})`

Examples:
- `datatransfer-api-http-pull-tests (standard)`
- `datatransfer-api-http-pull-tests (dcp)`
- `api-endpoints-tests (standard)`

### Debug Information
Failed jobs automatically collect:
- Full docker logs (all containers)
- Service-specific logs (DCP issuer, connectors, MongoDB, MinIO)
- Newman test output with request/response details

Check the **"Dump docker logs on failure"** and **"Collect service logs"** steps in failed jobs.

## 🔧 Troubleshooting

### Test Fails in CI but Passes Locally
**Common Causes**:
- Timing differences (services not ready)
- Environment variable mismatches
- Hardcoded values (localhost, specific ports)

**Solutions**:
- Add retry logic or increase wait times
- Verify `.env` file matches CI configuration
- Use docker service names instead of localhost

### DCP Mode Test Fails
**Check These Steps** (in order):
1. **Build phase**: Verify DCP issuer image built successfully
2. **Artifact download**: Check artifact was downloaded
3. **Health checks**: Review DCP issuer, Connector A/B health check outputs
4. **VC obtainment**: Verify "Obtain Verifiable Credentials" step succeeded
5. **Service logs**: Review collected logs for error messages

### Health Check Timeouts
**Symptoms**: "DCP Issuer not ready yet, waiting..." repeated 10 times

**Solutions**:
- Check docker-compose service dependencies
- Verify service startup logs for errors
- Increase retry count in workflow (currently 10 attempts × 5 seconds)
- Check for port conflicts

## 📊 Visual Execution Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    TRIGGER: Push/PR/Manual                  │
└────────────────────────┬────────────────────────────────────┘
                         │
         ┌───────────────┴───────────────┐
         │                               │
         ▼                               ▼
┌─────────────────┐            ┌──────────────────┐
│ Build Connector │            │ Build DCP Issuer │
│   Image (7min)  │            │   Image (7min)   │
│      ↓          │            │       ↓          │
│  Push to GHCR   │            │ Upload Artifact  │
└────────┬────────┘            └─────────┬────────┘
         │                               │
         └───────────────┬───────────────┘
                         │
         ┌───────────────┴────────────────────────────┐
         │         Matrix: 9 Parallel Jobs            │
         │                                            │
         ├─ api-endpoints (std)                       │
         ├─ dataset-api (std)                         │
         ├─ connector (std)                           │
         │                                            │
         ├─ negotiation (std) ──┐                    │
         ├─ negotiation (dcp) ──┼─ Same test,        │
         │                       └─ different config  │
         ├─ datatransfer-pull (std) ──┐              │
         ├─ datatransfer-pull (dcp) ──┼─ Same test   │
         │                             │              │
         ├─ datatransfer-push (std) ──┤              │
         └─ datatransfer-push (dcp) ──┘              │
                         │                            │
                         ▼                            │
         ┌──────────────────────────┐                │
         │   All Tests Complete     │                │
         │   (~15-20 min total)     │                │
         └──────────────────────────┘                │
```

## 📞 Support Resources

For help and additional information:

1. **[INTEGRATION_TESTS_GUIDE.md](INTEGRATION_TESTS_GUIDE.md)** - Comprehensive guide with detailed explanations
2. **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)** - Quick lookups and common patterns
3. **[ARCHITECTURE_DIAGRAM.md](ARCHITECTURE_DIAGRAM.md)** - Visual workflow diagrams and flows
4. **GitHub Actions Logs** - Detailed execution information and error messages

## 🎯 Workflow Features

- ✅ **Matrix Strategy**: Parameterized test execution with clear job naming
- ✅ **Parallel Execution**: 9 jobs run simultaneously for faster feedback
- ✅ **Smart Conditionals**: Steps run only when needed (resource optimization)
- ✅ **Artifact Management**: DCP issuer built once, reused by multiple tests
- ✅ **Health Checks**: Automatic service readiness verification
- ✅ **Error Handling**: Comprehensive log collection on failure
- ✅ **Maven Caching**: Faster builds with dependency caching
- ✅ **Best Practices**: 100% compliance with GitHub Actions guidelines

## 📈 Performance Metrics

- **Execution Time**: ~15-20 minutes total
  - Build Phase: ~7 minutes (parallel)
  - Test Phase: ~5-10 minutes (parallel matrix)
- **Parallel Jobs**: 9 simultaneous test executions
- **Artifact Retention**: 1 day (optimized storage costs)
- **Cache Hit Rate**: High (Maven dependencies cached)

## 🚀 Getting Started

### For New Team Members
1. Read this README for overview
2. Review [QUICK_REFERENCE.md](QUICK_REFERENCE.md) for common tasks
3. Check [ARCHITECTURE_DIAGRAM.md](ARCHITECTURE_DIAGRAM.md) for visual understanding
4. Test locally before making workflow changes

### For Adding Tests
1. Create Postman/Newman collection in `ci/docker/test-cases/`
2. Add matrix entry to `integration-tests.yml`
3. Test locally with appropriate docker-compose file
4. Push to feature branch and verify in GitHub Actions

### For Debugging Failures
1. Check GitHub Actions logs for specific error
2. Review docker logs in "Dump docker logs on failure" step
3. Reproduce locally using same docker-compose configuration
4. Check service health check outputs

---

**Workflow Location**: `.github/workflows/integration-tests.yml`  
**Documentation Version**: 1.0  
**Last Updated**: February 2026

