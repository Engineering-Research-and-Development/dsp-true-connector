# Integration Tests Workflow

The integration tests run in GitHub Actions from `.github/workflows/integration-tests.yml`. Each
run builds the connector and DCP issuer images and runs the Newman (Postman) collections
against them in a parallel matrix.

## Documentation

| Document | Purpose |
|----------|---------|
| [INTEGRATION_TESTS_GUIDE.md](INTEGRATION_TESTS_GUIDE.md) | Complete reference: jobs, matrix, image tags, old images, permissions |
| [QUICK_REFERENCE.md](QUICK_REFERENCE.md) | Cheat sheet for common tasks |
| [ARCHITECTURE_DIAGRAM.md](ARCHITECTURE_DIAGRAM.md) | Diagrams of the job graph, image flow and test modes |
| [LOCAL_TESTING.md](LOCAL_TESTING.md) | Running the same tests on your machine |
| [D-TEC-012](../decisions/technical/D-TEC-012-publish-test-tag-after-green-run.md) | Why each run uses its own image tag and `test` moves only after a green run |

## When it runs

- **Push** to `feature/*`, `hotfix/*` and `fix/*`. The `*` matches one path segment, so
  `feature/398-si-token` triggers it and `feature/dcp-keycloak/398` does not.
- No pull request, `main`, `develop` or manual trigger.
- A new push to the same branch cancels that branch's running workflow
  (`concurrency: integration-tests-<ref>`). Runs on different branches run side by side.

## How a run works

```
maven-verify (all unit + integration tests) ─────────────────────────────┐
build-connector-image ──┐                                                 ├─► publish-test-tag
                        ├──► integration-tests (12 matrix jobs) ──────────┘   (only if all green)
build-dcp-issuer-image ─┘
```

1. **Verify.** `maven-verify` runs `mvn clean verify` with all tests, in parallel with the rest.
2. **Build.** Both images are built without tests and pushed to GHCR as
   `ci-<run_id>-<commit sha>`, a tag unique to this run that the test jobs use.
3. **Test.** Each matrix job starts a Docker Compose stack with `IMAGE_TAG` set to the run's tag,
   waits for the services to be healthy, optionally obtains Verifiable Credentials, and runs one
   Newman collection.
4. **Publish.** When `maven-verify` and every matrix job pass, `publish-test-tag` points the
   `test` tag (manual TCK run, Terraform) at this run's images. A run with any failure leaves
   `test` where it was.

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
4. **Re-run failed jobs** works: the run's images stay in GHCR. If the re-run turns green,
   `publish-test-tag` runs too and moves `test`.

Per-run images are not deleted automatically; see
[INTEGRATION_TESTS_GUIDE.md](INTEGRATION_TESTS_GUIDE.md#old-images).
