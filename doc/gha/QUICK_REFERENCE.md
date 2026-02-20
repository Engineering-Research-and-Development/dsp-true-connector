# Integration Tests Workflow - Quick Reference

## 📋 File Location
`.github/workflows/integration-tests.yml`

## 🔄 Workflow Triggers
- **Push**: `feature/*`, `hotfix/*`, `fix/*`, `main`, `develop`
- **Pull Request**: `main`, `develop`
- **Manual**: Via GitHub Actions UI (workflow_dispatch)

## 🏗️ Jobs Overview

| Job | Purpose | Runs | Outputs |
|-----|---------|------|---------|
| `build-connector-image` | Maven build + Docker image | Once | Pushes image to GHCR |
| `build-dcp-issuer-image` | DCP issuer build | Once | Artifact: `dcp-issuer-image` |
| `integration-tests` | Run all test suites | 9x (matrix) | Test results + logs |

## 🧪 Test Matrix (9 Parallel Jobs)

### Standard Mode Only
```
✓ api-endpoints-tests (standard)
✓ dataset-api-tests (standard)
✓ connector-tests (standard)
```

### Both Standard and DCP Modes
```
✓ negotiation-api-without-counteroffer-tests (standard)
✓ negotiation-api-without-counteroffer-tests (dcp)
✓ datatransfer-api-http-pull-tests (standard)
✓ datatransfer-api-http-pull-tests (dcp)
✓ datatransfer-api-http-push-tests (standard)
✓ datatransfer-api-http-push-tests (dcp)
```

## ⚙️ Matrix Parameters

| Parameter | Values | Description |
|-----------|--------|-------------|
| `test-suite` | Test name | Display name for job |
| `collection` | Path to .json | Newman/Postman collection |
| `docker-compose-file` | Path to .yml | Which compose file to use |
| `mode` | `standard` or `dcp` | Test mode indicator |
| `requires-dcp` | `true` or `false` | Need DCP issuer? |
| `requires-vc` | `true` or `false` | Need VC obtainment? |

## 📝 Adding a New Test - Cheat Sheet

### Standard Mode Only
```yaml
- test-suite: your-test-name
  collection: ./ci/docker/test-cases/your-test/test.json
  docker-compose-file: ./ci/docker/docker-compose.yml
  mode: standard
  requires-dcp: false
```

### DCP Mode Only
```yaml
- test-suite: your-test-name
  collection: ./ci/docker/test-cases/your-test/test.json
  docker-compose-file: ./ci/docker/docker-compose-dcp.yml
  mode: dcp
  requires-dcp: true
  requires-vc: true  # or false if VC not needed
```

### Both Modes (2 entries)
Copy the standard entry, then copy and modify for DCP mode.

## 🔍 Key Conditional Steps

| Condition | When It Runs |
|-----------|--------------|
| `if: matrix.requires-dcp == true` | Download/load DCP issuer image |
| `if: matrix.requires-vc == true` | Obtain Verifiable Credentials |
| `if: failure()` | Collect logs on test failure |
| `if: always()` | Cleanup docker-compose services |

## 📦 Docker Compose Files

| File | Purpose | Services |
|------|---------|----------|
| `docker-compose.yml` | Standard tests | MongoDB, MinIO, Connector |
| `docker-compose-dcp.yml` | DCP tests | MongoDB, MinIO, Connector A/B, DCP Issuer |

## 🐛 Debugging Failed Tests

1. **Check job logs**: Click on failed job in Actions tab
2. **View docker logs**: Scroll to "Dump docker logs on failure"
3. **Service-specific logs**: Check "Collect service logs" step
4. **Health checks**: Review health check outputs

## 🛠️ Local Testing

```bash
# Standard mode
docker compose -f ./ci/docker/docker-compose.yml --env-file ./ci/docker/.env up -d
newman run ./ci/docker/test-cases/your-test/test.json

# DCP mode
docker compose -f ./ci/docker/docker-compose-dcp.yml --env-file ./ci/docker/.env up -d
newman run ./ci/docker/test-cases/dcp-obtain-vc/dcp-gha-tests.postman_collection.json
newman run ./ci/docker/test-cases/your-test/test.json

# Cleanup
docker compose -f ./ci/docker/docker-compose.yml --env-file ./ci/docker/.env down -v
```

## ⏱️ Typical Execution Times

- **Build Jobs**: ~5-10 minutes each
- **Standard Tests**: ~2-5 minutes each
- **DCP Tests**: ~5-10 minutes each (includes VC obtainment)
- **Total Workflow**: ~15-20 minutes (parallel execution)

## 🎯 Best Practices

✅ Test locally with docker-compose first  
✅ Keep collection names consistent between modes  
✅ Always use `fetch-depth: 1` for checkouts (already done)  
✅ Pin action versions (e.g., `@v4`, not `@latest`)  
✅ Use descriptive step names  
✅ Set retention-days for artifacts (1 day for DCP issuer)  

## 🔐 Security Notes

- `GITHUB_TOKEN` has least-privilege permissions
- Secrets accessed via `secrets` context
- Docker images signed with attestations
- Concurrency prevents resource conflicts

## 📊 Workflow Visualization

```
┌─────────────────────────┐
│  build-connector-image  │
│  (Maven + Docker Push)  │
└───────────┬─────────────┘
            │
┌───────────┴─────────────┐
│ build-dcp-issuer-image  │
│  (Maven + Docker Save)  │
└───────────┬─────────────┘
            │
            ├──────────┬──────────┬──────────┬───────────┬──────────┬──────────┬──────────┬──────────┐
            │          │          │          │           │          │          │          │          │
            ▼          ▼          ▼          ▼           ▼          ▼          ▼          ▼          ▼
         Test 1    Test 2    Test 3    Test 4      Test 5    Test 6    Test 7    Test 8    Test 9
       (standard) (standard) (standard) (standard)   (dcp)  (standard)  (dcp)  (standard)  (dcp)
```

## 🚨 Common Issues

| Issue | Solution |
|-------|----------|
| DCP test fails, standard passes | Check DCP issuer logs, verify VC obtainment |
| All tests timeout | Increase `sleep` time after docker-compose up |
| Image not found | Check artifact upload/download steps |
| Health check fails | Review service startup logs |

## 📞 Support

- **Documentation**: `.github/workflows/INTEGRATION_TESTS_GUIDE.md`
- **Workflow File**: `.github/workflows/integration-tests.yml`
- **Test Collections**: `./ci/docker/test-cases/`

## 🔄 Migration Status

| Old Workflow | Status | Replacement |
|--------------|--------|-------------|
| `build.yml` | DEPRECATED | `integration-tests.yml` |
| `dcp-tests.yml` | DEPRECATED | `integration-tests.yml` |

---

**Quick Start**: Push to a feature branch and watch the magic happen! 🚀

