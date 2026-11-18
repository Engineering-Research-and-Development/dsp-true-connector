# Integration Tests Workflow - Quick Reference

**Workflow:** `.github/workflows/integration-tests.yml`. Full details:
[INTEGRATION_TESTS_GUIDE.md](INTEGRATION_TESTS_GUIDE.md).

## Triggers
- **Push** to `feature/*`, `hotfix/*`, `fix/*` (one path segment after the prefix)
- A newer push to the same branch cancels the running workflow; other branches run in parallel

## Jobs

| Job | Does | Output |
|-----|------|--------|
| `build-connector-image` | `mvn clean verify` + Docker build | `dsp-true-connector:ci-<run_id>-<sha>` and `:test` in GHCR |
| `build-dcp-issuer-image` | Maven package of `dcp/` + Docker build | `dcp-issuer:ci-<run_id>-<sha>` and `:test` in GHCR |
| `integration-tests` | 12 matrix jobs, one Newman collection each | Test results, logs on failure |
| `cleanup-images` | Always runs last | Deletes this run's images and old orphans from GHCR |

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
| `always()` | `docker compose down -v`; `cleanup-images` job |

## Image tags

| Tag | Meaning |
|-----|---------|
| `ci-<run_id>-<sha>` | This run only; deleted by `cleanup-images` |
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

# Test a specific CI build locally (only while that run is in progress; its images are deleted at the end)
IMAGE_TAG=ci-<run_id>-<sha> docker compose -f ./ci/docker/docker-compose.yml --env-file ./ci/docker/.env up -d
```

## Common issues

| Issue | Fix |
|-------|-----|
| Image not found after re-running a job | Use **Re-run all jobs**; cleanup deleted the run's images |
| DCP test fails, standard passes | Check the issuer health and VC steps, then `dcp-issuer` logs |
| Cleanup warnings | Give the repository Admin access on the GHCR packages |
| Health check timeout | Read the connector logs printed by the health-check step |
