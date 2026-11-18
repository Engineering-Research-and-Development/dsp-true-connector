# Integration Tests Workflow

The integration tests run in GitHub Actions from `.github/workflows/integration-tests.yml`. Each
run builds the connector and DCP issuer images, runs the Newman (Postman) collections against
them in a parallel matrix, and deletes its own images when it finishes.

## Documentation

| Document | Purpose |
|----------|---------|
| [INTEGRATION_TESTS_GUIDE.md](INTEGRATION_TESTS_GUIDE.md) | Complete reference: jobs, matrix, image tags, cleanup, permissions |
| [QUICK_REFERENCE.md](QUICK_REFERENCE.md) | Cheat sheet for common tasks |
| [ARCHITECTURE_DIAGRAM.md](ARCHITECTURE_DIAGRAM.md) | Diagrams of the job graph, image flow and test modes |
| [LOCAL_TESTING.md](LOCAL_TESTING.md) | Running the same tests on your machine |
| [D-TEC-011](../decisions/technical/D-TEC-011-per-run-ci-image-tags.md) | Why each run uses its own image tag |

## When it runs

- **Push** to `feature/*`, `hotfix/*` and `fix/*`. The `*` matches one path segment, so
  `feature/398-si-token` triggers it and `feature/dcp-keycloak/398` does not.
- No pull request, `main`, `develop` or manual trigger.
- A new push to the same branch cancels that branch's running workflow
  (`concurrency: integration-tests-<ref>`). Runs on different branches run side by side.

## How a run works

```
build-connector-image ──┐
                        ├──► integration-tests (12 matrix jobs) ──► cleanup-images
build-dcp-issuer-image ─┘
```

1. **Build.** Both images are built and pushed to GHCR with two tags:
   - `ci-<run_id>-<commit sha>`: unique to this run; the test jobs use it.
   - `test`: kept for consumers outside this workflow (manual TCK run, Terraform).
2. **Test.** Each matrix job starts a Docker Compose stack with `IMAGE_TAG` set to the run's tag,
   waits for the services to be healthy, optionally obtains Verifiable Credentials, and runs one
   Newman collection.
3. **Clean up.** `cleanup-images` runs whether tests pass, fail or are cancelled, and deletes this
   run's image versions from GHCR (see the guide for the exact rules).

Because every run tests only the images it built, parallel runs on different branches cannot
overwrite each other's images.

## Test modes

| Mode | Compose file | Services |
|------|--------------|----------|
| `standard` | `ci/docker/docker-compose.yml` | RustFS, Connector A + MongoDB A, Connector B + MongoDB B |
| `dcp` | `ci/docker/docker-compose-dcp.yml` | The standard services plus DCP Issuer and its MongoDB |

## Adding a test

1. Add a Postman collection under `ci/docker/test-cases/`.
2. Add a matrix entry in `integration-tests.yml` (examples in [QUICK_REFERENCE.md](QUICK_REFERENCE.md)).
3. Run it locally ([LOCAL_TESTING.md](LOCAL_TESTING.md)), then push a feature branch.

## Debugging a failure

1. Open the failed job, named `<test-suite> (<mode>)`, in the Actions tab.
2. Check the health-check step output and the **Dump docker logs on failure** step.
3. Reproduce locally with the same compose file and collection.
4. To re-run, use **Re-run all jobs**. **Re-run failed jobs** cannot work, because
   `cleanup-images` has already deleted that run's images.
