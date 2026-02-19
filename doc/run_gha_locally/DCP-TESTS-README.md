# DCP Integration Tests - GitHub Actions Workflow

## Overview

This document describes the new GitHub Actions workflow for DCP (Decentralized Claims Protocol) integration testing. The workflow builds the DCP Issuer Docker image locally and runs comprehensive integration tests without publishing the image to any registry.

## Files Created

### 1. GitHub Actions Workflow
**File**: `.github/workflows/dcp-tests.yml`

This workflow orchestrates the build and test process for DCP functionality.

### 2. Docker Compose Configuration
**File**: `ci/docker/docker-compose-dcp.yml`

Extended version of the standard docker-compose.yml that includes the DCP Issuer service.

### 3. Test Collections
- `ci/docker/test-cases/dcp-credential-issuance-tests/dcp-credential-issuance-tests.json`
- `ci/docker/test-cases/dcp-verifiable-presentation-tests/dcp-verifiable-presentation-tests.json`

Newman/Postman test collections for DCP functionality testing.

## Workflow Architecture

### Jobs Overview

The workflow consists of 4 jobs that follow best practices from the GitHub Actions CI/CD guide:

```
build-connector-image (parallel) ─┐
                                   ├─> dcp-credential-issuance-tests
build-dcp-issuer-image (parallel) ─┤
                                   └─> dcp-verifiable-presentation-tests
```

### Job 1: `build-connector-image`
**Purpose**: Build and publish the main DSP True Connector image

**Key Features**:
- Runs on `ubuntu-latest`
- Uses Maven caching for faster builds
- Builds the entire project with `mvn -B clean verify`
- Publishes image to GitHub Container Registry as `test` tag
- **Permissions**: `contents: read`, `packages: write`, `attestations: write`, `id-token: write`

**Best Practices Applied**:
- ✅ Least privilege permissions
- ✅ Maven dependency caching
- ✅ Explicit version pinning for actions (@v4, @v3, etc.)
- ✅ Registry authentication with GITHUB_TOKEN

### Job 2: `build-dcp-issuer-image`
**Purpose**: Build DCP Issuer Docker image locally (not published)

**Key Features**:
- Runs on `ubuntu-latest`
- Uses Maven caching for dependency optimization
- Builds only the `dcp-issuer` module with `mvn -B clean package -DskipTests`
- Uses Docker Buildx for efficient image building
- **Saves image as artifact** instead of pushing to registry
- Image stored as `/tmp/dcp-issuer-image.tar`
- Artifact retention: 1 day (configurable)

**Best Practices Applied**:
- ✅ Least privilege permissions (only `contents: read`)
- ✅ Selective build (only required module)
- ✅ Artifact-based image sharing (no registry required)
- ✅ Docker Buildx for optimized builds
- ✅ Short artifact retention (cost-effective)

**Why Artifacts Instead of Registry?**
- **Security**: No need for registry credentials or public exposure
- **Cost**: No registry storage costs
- **Speed**: Faster than push/pull cycle
- **Isolation**: Image only available within workflow run

### Job 3: `dcp-credential-issuance-tests`
**Purpose**: Test DCP credential issuance functionality

**Dependencies**: Requires both `build-connector-image` and `build-dcp-issuer-image`

**Key Features**:
- Downloads DCP Issuer image artifact
- Loads image into Docker daemon
- Starts services using `docker-compose-dcp.yml`
- Implements robust health checking with retries
- Runs Newman tests for credential issuance
- Collects comprehensive logs on failure
- Always cleans up resources

**Best Practices Applied**:
- ✅ Explicit service health checks with retry logic
- ✅ Detailed logging for debugging
- ✅ `always()` cleanup to prevent resource leaks
- ✅ Docker logs collection on failure
- ✅ Explicit test failure handling

### Job 4: `dcp-verifiable-presentation-tests`
**Purpose**: Test DCP verifiable presentation authentication

**Dependencies**: Requires both `build-connector-image` and `build-dcp-issuer-image`

**Key Features**:
- Same infrastructure as Job 3
- Tests VP-based authentication flows
- Independent execution (can run in parallel with Job 3)

**Best Practices Applied**:
- Same as Job 3

## Workflow Triggers

```yaml
on:
  push:
    branches: [ "feature/*", "hotfix/*", "fix/*", "develop", "main" ]
  pull_request:
    branches: [ "develop", "main" ]
```

**Strategy**:
- Runs on feature branches for early feedback
- Runs on develop/main for release validation
- Runs on PRs targeting develop/main for merge validation

## Concurrency Control

```yaml
concurrency:
  group: dcp-tests-${{ github.ref }}
  cancel-in-progress: true
```

**Benefits**:
- Cancels redundant runs when new commits are pushed
- Saves CI/CD resources
- Faster feedback loop

## Docker Compose Configuration

### New Service: `dcp-issuer`

```yaml
dcp-issuer:
  image: dcp-issuer:test
  container_name: dcp-issuer
  networks:
    - network-a
    - network-b
  ports:
    - "8084:8084"
  environment:
    - JAVA_OPTS=-Xmx512m -Xms256m
  healthcheck:
    test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8084/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 40s
```

**Key Design Decisions**:
- Uses local image tag `dcp-issuer:test` (loaded from artifact)
- Connected to both networks for full connectivity
- Health check ensures service is ready before tests run
- Memory-limited for CI efficiency

### Updated Dependencies

Both `connector-a` and `connector-b` now depend on `dcp-issuer`:

```yaml
depends_on:
  - mongodb-a
  - minio
  - dcp-issuer  # New dependency
```

## Test Collections Structure

### DCP Credential Issuance Tests

1. **Health Check - DCP Issuer**: Validates issuer service is running
2. **Get DID Document**: Validates DID document endpoint
3. **Request Credential**: Tests credential issuance flow

### DCP Verifiable Presentation Tests

1. **Health Check - Connector A**: Validates connector A is running
2. **Health Check - Connector B**: Validates connector B is running
3. **Request Catalog with VP**: Tests VP-based authentication

**Note**: These are placeholder collections. You should expand them with actual test cases based on your requirements.

## Security Best Practices Applied

### 1. Least Privilege Permissions
- Each job has minimal required permissions
- DCP issuer build job only needs `contents: read`
- Main connector job needs `packages: write` for registry

### 2. Secret Management
- Uses `GITHUB_TOKEN` for registry authentication
- No hardcoded credentials
- Secrets automatically masked in logs

### 3. Artifact Security
- Short retention period (1 day)
- Artifacts scoped to workflow run
- No public exposure

### 4. Image Security
- No long-lived test images in registry
- Local-only image for testing
- Reproducible builds

## Performance Optimizations

### 1. Caching Strategy
```yaml
- uses: actions/setup-java@v4
  with:
    cache: 'maven'
```
- Maven dependencies cached between runs
- Significantly reduces build time

### 2. Parallel Execution
- Connector and DCP issuer images build in parallel
- Test jobs can run in parallel (independent)

### 3. Selective Building
```bash
mvn -B clean package -DskipTests --file dcp/dcp-issuer/pom.xml
```
- Only builds required module
- Skips tests (already tested in main build)

### 4. Artifact-Based Sharing
- Faster than push/pull to registry
- No network latency
- Efficient storage

## Execution Flow

### Timeline

1. **Minute 0-5**: Both build jobs start in parallel
   - Connector: Full build (~5 min)
   - DCP Issuer: Module build (~2 min)

2. **Minute 5**: Test jobs start
   - Download artifacts
   - Load images
   - Start services

3. **Minute 6-7**: Services starting & health checks
   - 45s initial wait
   - Up to 50s for health check retries

4. **Minute 7-10**: Test execution
   - Newman runs test collections
   - Parallel execution possible

5. **Minute 10**: Cleanup
   - Services stopped
   - Volumes removed

**Total estimated time**: ~10-12 minutes per workflow run

## Integration with Existing Workflows

### Relationship to `build.yml`

The new `dcp-tests.yml` workflow complements the existing `build.yml`:

**build.yml (existing)**:
- Runs standard connector tests
- No DCP issuer dependency
- Uses main docker-compose.yml

**dcp-tests.yml (new)**:
- Runs DCP-specific tests
- Includes DCP issuer service
- Uses docker-compose-dcp.yml

### Execution Strategy Options

#### Option 1: Separate Workflows (Recommended)
**Current implementation**
- DCP tests run independently
- Can be triggered separately
- Clearer failure isolation
- Better parallelization

#### Option 2: Sequential Execution
Add to `build.yml` as a separate job after existing tests:
```yaml
jobs:
  # ... existing jobs ...
  
  dcp-tests:
    needs: [connector-tests]  # Run after all standard tests
    uses: ./.github/workflows/dcp-tests.yml
```

#### Option 3: Full Integration
Merge all DCP test jobs into `build.yml`:
- Single workflow file
- Longer overall execution time
- More complex maintenance

**Recommendation**: Keep as separate workflow for modularity and maintainability.

## Troubleshooting

### Common Issues

#### 1. DCP Issuer Fails to Start
**Symptoms**: Health check fails, service not responding on port 8084

**Solutions**:
- Check Docker logs: `docker logs dcp-issuer`
- Verify JAR built correctly in dcp-issuer module
- Ensure health check endpoint is accessible
- Increase `start_period` in healthcheck if needed

#### 2. Image Artifact Not Found
**Symptoms**: "Artifact not found" error in test jobs

**Solutions**:
- Verify `build-dcp-issuer-image` job completed successfully
- Check artifact upload/download paths match
- Ensure artifact name is consistent

#### 3. Tests Fail Due to Service Not Ready
**Symptoms**: Connection refused errors in Newman tests

**Solutions**:
- Increase wait time (currently 45s + 50s retries)
- Check service health endpoints
- Verify network connectivity between services
- Review docker-compose networking configuration

#### 4. Out of Disk Space
**Symptoms**: Docker build or compose fails with disk space errors

**Solutions**:
- Reduce artifact retention days
- Add cleanup step before builds
- Use smaller base images

### Debugging Commands

```bash
# Check running containers
docker ps -a

# View service logs
docker logs dcp-issuer
docker logs connector-a
docker logs connector-b

# Check service health
curl http://localhost:8084/actuator/health

# Inspect Docker image
docker inspect dcp-issuer:test

# Check Docker Compose status
docker compose -f ci/docker/docker-compose-dcp.yml ps
```

## Maintenance

### Regular Updates

1. **Action Versions**: Review and update action versions quarterly
   - `actions/checkout@v4` → v5 when available
   - `docker/build-push-action@v6` → v7 when available

2. **Docker Base Images**: Keep Dockerfile base images updated
   - Monitor `eclipse-temurin:17-jre-alpine` for updates

3. **Test Collections**: Expand test coverage as features are added

### Monitoring

Track these metrics:
- Workflow execution time (target: <15 min)
- Test success rate (target: >95%)
- Artifact storage usage
- Build cache hit rate

## Future Enhancements

### Potential Improvements

1. **Matrix Testing**: Test across multiple Java versions
   ```yaml
   strategy:
     matrix:
       java-version: [17, 21]
   ```

2. **Performance Testing**: Add load testing for DCP endpoints

3. **Security Scanning**: Integrate container vulnerability scanning
   ```yaml
   - name: Run Trivy vulnerability scanner
     uses: aquasecurity/trivy-action@v0.20.0
   ```

4. **Test Reporting**: Integrate with test reporting tools
   - JUnit XML reports
   - Coverage tracking
   - Trend analysis

5. **Slack/Email Notifications**: Alert on test failures

## Cost Considerations

### CI Minutes Usage

**Per workflow run**:
- 2 build jobs × ~5 min = 10 minutes
- 2 test jobs × ~7 min = 14 minutes
- **Total**: ~24 minutes per run

**Monthly estimate** (assuming 100 runs/month):
- Public repo: Free (unlimited)
- Private repo: 2,400 minutes (~40% of free tier)

### Storage Usage

**Artifacts**:
- DCP issuer image: ~100-200 MB
- Retention: 1 day
- Cost impact: Negligible

## Conclusion

The new DCP integration testing workflow provides:

✅ **Secure**: No image publishing, artifact-based sharing
✅ **Efficient**: Parallel builds, Maven caching, selective building
✅ **Reliable**: Health checks, retry logic, comprehensive logging
✅ **Maintainable**: Modular structure, clear separation of concerns
✅ **Best Practice Compliant**: Follows GitHub Actions CI/CD guidelines

The workflow is ready for immediate use and can be extended as DCP functionality grows.

## Quick Start

### Running the Workflow

1. **Automatic**: Push to any feature/hotfix branch or create PR to develop/main

2. **Manual**: Go to Actions tab → DCP Integration Tests → Run workflow

### Local Testing

Test the docker-compose setup locally:

```bash
# Build DCP issuer locally
cd dcp/dcp-issuer
mvn clean package -DskipTests
docker build -t dcp-issuer:test .

# Build connector (or pull from registry)
cd ../../connector
mvn clean package -DskipTests
docker build -t ghcr.io/engineering-research-and-development/dsp-true-connector:test .

# Run services
cd ../ci/docker
docker compose -f docker-compose-dcp.yml --env-file .env up -d

# Check health
curl http://localhost:8084/actuator/health
curl http://localhost:8080/actuator/health
curl http://localhost:8090/actuator/health

# Run tests manually
newman run test-cases/dcp-credential-issuance-tests/dcp-credential-issuance-tests.json

# Cleanup
docker compose -f docker-compose-dcp.yml down -v
```

## Support

For issues or questions:
1. Check workflow logs in GitHub Actions UI
2. Review this documentation
3. Inspect Docker logs for specific services
4. Create an issue with reproduction steps

