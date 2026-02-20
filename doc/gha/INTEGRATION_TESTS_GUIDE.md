# Integration Tests Workflow - Complete Guide

## Overview

The DSP True Connector uses a unified integration testing workflow with GitHub Actions matrix strategy to run tests in parallel with multiple configurations. This guide provides comprehensive documentation for understanding, using, and maintaining the workflow.

## Key Features

1. **Matrix Strategy**: Tests run in parallel with different configurations (standard vs DCP mode)
2. **Single Source of Truth**: All integration tests defined in one workflow file
3. **Easy Maintenance**: Add new tests once, they automatically run in appropriate modes
4. **Clear Visibility**: Test names clearly indicate which mode they're running in
5. **Efficient Resource Usage**: DCP issuer built once and reused by multiple tests
6. **Smart Execution**: Conditional steps run only when needed

## Workflow Structure

### File: `.github/workflows/integration-tests.yml`

The workflow consists of three main jobs:

### 1. Build Connector Image (`build-connector-image`)
- Checks out repository
- Sets up JDK 17 with Maven cache
- Runs Maven build and tests (`mvn clean verify`)
- Builds and pushes Docker image to GHCR
- **Runs once per workflow execution**

### 2. Build DCP Issuer Image (`build-dcp-issuer-image`)
- Checks out repository
- Sets up JDK 17 with Maven cache
- Builds DCP Issuer module (`mvn clean package -DskipTests`)
- Builds DCP Issuer Docker image
- Uploads image as artifact for DCP tests
- **Runs once per workflow execution**

### 3. Integration Tests (`integration-tests`)
- Runs as a **matrix** with multiple configurations
- Downloads DCP issuer image only when needed
- Starts appropriate docker-compose configuration
- Obtains Verifiable Credentials for DCP tests
- Runs test collection via Newman (Postman)
- Collects logs on failure
- Cleans up resources

## Matrix Configuration

The matrix includes:

### Tests that run ONLY in standard mode:
- `api-endpoints-tests`
- `dataset-api-tests`
- `connector-tests`

### Tests that run in BOTH standard AND DCP modes:
- `negotiation-api-without-counteroffer-tests` (standard)
- `negotiation-api-without-counteroffer-tests` (dcp)
- `datatransfer-api-http-pull-tests` (standard)
- `datatransfer-api-http-pull-tests` (dcp)
- `datatransfer-api-http-push-tests` (standard)
- `datatransfer-api-http-push-tests` (dcp)

### Matrix Parameters:

Each test configuration specifies:

- `test-suite`: Human-readable test name
- `collection`: Path to Postman/Newman collection
- `docker-compose-file`: Which docker-compose file to use
- `mode`: Either `standard` or `dcp`
- `requires-dcp`: Boolean - whether DCP issuer is needed
- `requires-vc`: Boolean - whether to obtain Verifiable Credentials first

## How to Add New Tests

### 1. Standard Test (no DCP)

Add to the matrix in `integration-tests.yml`:

```yaml
- test-suite: my-new-test
  collection: ./ci/docker/test-cases/my-new-test/my-test.json
  docker-compose-file: ./ci/docker/docker-compose.yml
  mode: standard
  requires-dcp: false
```

### 2. Test that needs DCP mode

Add to the matrix in `integration-tests.yml`:

```yaml
- test-suite: my-new-test
  collection: ./ci/docker/test-cases/my-new-test/my-test.json
  docker-compose-file: ./ci/docker/docker-compose-dcp.yml
  mode: dcp
  requires-dcp: true
  requires-vc: true  # Set to true if VC is needed
```

### 3. Test that runs in BOTH modes

Add TWO entries to the matrix:

```yaml
# Standard mode
- test-suite: my-new-test
  collection: ./ci/docker/test-cases/my-new-test/my-test.json
  docker-compose-file: ./ci/docker/docker-compose.yml
  mode: standard
  requires-dcp: false

# DCP mode
- test-suite: my-new-test
  collection: ./ci/docker/test-cases/my-new-test/my-test.json
  docker-compose-file: ./ci/docker/docker-compose-dcp.yml
  mode: dcp
  requires-dcp: true
  requires-vc: true
```

## Conditional Steps Explained

The workflow uses conditional execution for efficiency:

### DCP Issuer Steps
```yaml
if: matrix.requires-dcp == true
```
- Downloads DCP issuer image artifact
- Loads image into Docker
- Verifies image is available
- Checks DCP issuer health
- Checks Connector A/B health

### VC Obtainment
```yaml
if: matrix.requires-vc == true
```
- Runs the `dcp-obtain-vc` collection
- Must succeed before running the actual test
- Only runs for DCP mode tests that need credentials

### Log Collection
```yaml
if: failure()
```
- Dumps all Docker logs
- Collects specific service logs based on mode
- Only runs when test fails

## Triggers

The workflow triggers on:

- **Push** to branches: `feature/*`, `hotfix/*`, `fix/*`, `main`, `develop`
- **Pull Request** to branches: `main`, `develop`
- **Manual** trigger via `workflow_dispatch`

## Concurrency Control

```yaml
concurrency:
  group: integration-tests-${{ github.ref }}
  cancel-in-progress: true
```

This ensures:
- Only one workflow runs per branch at a time
- New pushes cancel old runs for the same branch
- Prevents resource conflicts

## Permissions

The workflow uses least-privilege permissions:

```yaml
permissions:
  contents: read          # Read repository code
  packages: write         # Push Docker images to GHCR
  attestations: write     # Create image attestations
  id-token: write         # OIDC token for attestations
```

## Migration from Old Workflows

### Old `build.yml`
- Status: **DEPRECATED**
- Trigger: Changed to `__deprecated__` branch (never matches)
- Recommendation: Delete after confirming new workflow works

### Old `dcp-tests.yml`
- Status: **DEPRECATED**
- Trigger: Changed to `__deprecated__` branch (never matches)
- Recommendation: Delete after confirming new workflow works

## Troubleshooting

### Test fails in DCP mode but passes in standard mode
1. Check DCP issuer logs in the workflow output
2. Verify VC obtainment step succeeded
3. Check connector A/B health check outputs

### Test fails in both modes
1. Review docker logs in workflow artifacts
2. Check if service health checks passed
3. Verify docker-compose configuration

### Matrix job doesn't run
1. Verify matrix configuration syntax
2. Check conditional steps (`if:` statements)
3. Ensure required artifacts exist

## Best Practices

1. **Always test locally first**: Use docker-compose to test before pushing
2. **Keep test names consistent**: Use the same collection name in both modes
3. **Monitor artifact storage**: DCP issuer image is stored for 1 day
4. **Use fail-fast: false**: Allows all matrix jobs to complete even if one fails
5. **Clean up resources**: Always run `docker compose down -v` in cleanup steps

## Future Enhancements

Potential improvements:

1. **Reusable workflows**: Extract common steps into reusable workflows
2. **Parallel test execution**: Run multiple Newman collections in parallel
3. **Test result reporting**: Upload JUnit XML reports for better visibility
4. **Performance metrics**: Track test execution times
5. **Nightly full regression**: Schedule comprehensive tests nightly

## Questions?

For questions or issues with the integration tests workflow, please:
1. Review this documentation
2. Check workflow run logs in GitHub Actions
3. Open an issue with workflow run URL and error details

