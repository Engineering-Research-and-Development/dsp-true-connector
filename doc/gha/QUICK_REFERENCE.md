# Integration Tests Workflow - Quick Reference

**Workflow:** `.github/workflows/integration-tests.yml`. Full details:
[INTEGRATION_TESTS_GUIDE.md](INTEGRATION_TESTS_GUIDE.md).

## Triggers
- **Push** to `feature/*`, `hotfix/*`, `fix/*` (one path segment after the prefix)
- A newer push to the same branch cancels the running workflow; other branches run in parallel

## Jobs

| Job | Does | Output |
|-----|------|--------|
| `maven-verify` | `mvn clean verify`, all unit + integration tests | Pass/fail; runs in parallel |
| `build-connector-image` | `mvn package -DskipTests -pl connector -am` + Docker build | `dsp-true-connector:ci-<run_id>-<sha>` and `:test` in GHCR |
| `build-dcp-issuer-image` | Maven package of `dcp/` + Docker build | `dcp-issuer:ci-<run_id>-<sha>` and `:test` in GHCR |
| `integration-tests` | 12 matrix jobs, one Newman collection each | Test results, logs on failure |

## Matrix (12 jobs)

```
standard: api-endpoints-tests, dataset-api-tests, connector-tests,
          negotiation-api-without-counteroffer-tests, negotiation-api-with-counteroffer-tests,
          datatransfer-api-http-pull-tests, datatransfer-api-http-push-tests,
          api-endpoints-tenant-tests
dcp:      negotiation-api-without-counteroffer-tests, datatransfer-api-http-pull-tests,
          datatransfer-api-http-push-tests, dcp-credential-issuance-tests
```

## Matrix entry templates

```yaml
# Standard
- test-suite: your-test
  collection: ./ci/docker/test-cases/your-test/your-test.json
  docker-compose-file: ./ci/docker/docker-compose.yml
  mode: standard
  requires-dcp: false

# DCP
- test-suite: your-test
  collection: ./ci/docker/test-cases/your-test/your-test.json
  docker-compose-file: ./ci/docker/docker-compose-dcp.yml
  mode: dcp
  requires-dcp: true
  requires-vc: true
```

## Conditional steps

| Condition | Step |
|-----------|------|
| `matrix.requires-dcp == true` | DCP Issuer health check (`:8084`) |
| `matrix.requires-vc == true` | Obtain Verifiable Credentials |
| `failure()` | Dump docker logs |
| `always()` | `docker compose down -v` |

## Image tags

| Tag | Meaning |
|-----|---------|
| `ci-<run_id>-<sha>` | This run only; not deleted automatically |
| `test` | Last feature-branch build; used by TCK, Terraform and local runs |

Compose files use `${IMAGE_TAG:-test}`: unset locally means `:test`.

## Local run

```bash
# Standard mode
docker compose -f ./ci/docker/docker-compose.yml --env-file ./ci/docker/.env up -d
npx --yes newman run ./ci/docker/test-cases/your-test/your-test.json
docker compose -f ./ci/docker/docker-compose.yml --env-file ./ci/docker/.env down -v

# DCP mode
docker compose -f ./ci/docker/docker-compose-dcp.yml --env-file ./ci/docker/.env up -d
npx --yes newman run ./ci/docker/test-cases/dcp-obtain-vc/dcp-gha-tests.postman_collection.json
npx --yes newman run ./ci/docker/test-cases/your-test/your-test.json
docker compose -f ./ci/docker/docker-compose-dcp.yml --env-file ./ci/docker/.env down -v

# Test a specific CI build locally
IMAGE_TAG=ci-<run_id>-<sha> docker compose -f ./ci/docker/docker-compose.yml --env-file ./ci/docker/.env up -d
```

## Common issues

| Issue | Fix |
|-------|-----|
| DCP test fails, standard passes | Check the issuer health and VC steps, then `dcp-issuer` logs |
| Old `ci-…` images in GHCR | Delete them by hand in the package settings |
| Health check timeout | Read the connector logs printed by the health-check step |
