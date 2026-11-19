# Integration Tests Workflow - Complete Guide

Reference for `.github/workflows/integration-tests.yml`. For an overview start with
[README.md](README.md); the design decision behind the image tags is
[D-TEC-012](../decisions/technical/D-TEC-012-publish-test-tag-after-green-run.md).

## Triggers and concurrency

```yaml
on:
  push:
    branches: [ "feature/*", "hotfix/*", "fix/*" ]

concurrency:
  group: integration-tests-${{ github.ref }}
  cancel-in-progress: true
```

- Only pushes to matching branches trigger the workflow. `*` does not match `/`, so nested branch
  names such as `feature/a/b` are not covered.
- A new push cancels the older run **of the same branch**. Different branches run concurrently;
  per-run image tags keep them isolated.
- Merging a PR into a matching branch is a push to that branch and triggers a run.

## Permissions

```yaml
permissions:
  contents: read        # check out the repository
  packages: write       # push images to GHCR
  attestations: write
  id-token: write
```

## Image tags

```yaml
env:
  IMAGE_TAG: ci-${{ github.run_id }}-${{ github.sha }}
```

| Tag | Pushed by | Used by |
|-----|-----------|---------|
| `ci-<run_id>-<sha>` | both build jobs (every run, green or not) | the test jobs of the same run |
| `test` | `publish-test-tag`, only after a fully green run | manual TCK workflow (`ci/tck/docker-compose.yml`), Terraform (`terraform.tfvars`), local runs |

The run ID is part of the tag so two branches at the same commit still get different tags. The CI
compose files reference `${IMAGE_TAG:-test}`, so they pull the run's tag in CI and fall back to
`test` locally.

`test` is shared: it points at the images of whichever branch's run went green last. Only use it
where "the most recent green feature-branch build" is acceptable. `build.yml` on `develop` also
pushes `test`.

## Jobs

### 1. `maven-verify`
- Checks out the repository, sets up JDK 21 (Temurin) with the Maven cache.
- Runs `mvn -B clean verify` for the whole project: all unit and integration tests (Testcontainers).
- Runs in parallel with the image builds and the Newman matrix. The run fails if it fails, and
  `publish-test-tag` waits for it. It is the longest job (about 6 minutes, most of it connector integration tests).

### 2. `build-connector-image`
- Checks out the repository, sets up JDK 21 with the Maven cache.
- Runs `mvn -B clean package -DskipTests -pl connector -am`: the connector and the modules it
  depends on, without tests (those run in `maven-verify`).
- Builds `connector/Dockerfile` with `no-cache: true` and pushes the `ci-<run_id>-<sha>` tag to
  `ghcr.io/engineering-research-and-development/dsp-true-connector`. The Dockerfile copies the jars
  with `COPY --chown` instead of a later `chown -R`, which would store the dependency jars twice.

### 3. `build-dcp-issuer-image`
- Checks out the repository, sets up JDK 21 with the Maven cache.
- Runs `mvn -B clean package -DskipTests --file dcp/pom.xml`.
- Builds `dcp/dcp-issuer/Dockerfile` and pushes the `ci-<run_id>-<sha>` tag to
  `ghcr.io/engineering-research-and-development/dcp-issuer`.

The three jobs above run in parallel.

### 4. `integration-tests` (matrix)

`fail-fast: false`, so every matrix job finishes even when one fails. Steps:

| Step | Runs when | What it does |
|------|-----------|--------------|
| Checkout | always | |
| Start Docker Compose services | always | `docker compose -f <compose file> --env-file ./ci/docker/.env up -d`, pulling the run's images |
| Wait for connectors to be healthy | always | Polls `:8080` and `:8090` `/actuator/health`, up to 36 × 10 s |
| Check DCP Issuer health | `requires-dcp == true` | Polls `:8084/actuator/health`, up to 10 × 5 s |
| Obtain Verifiable Credentials | `requires-vc == true` | Runs `ci/docker/test-cases/dcp-obtain-vc/dcp-gha-tests.postman_collection.json` |
| Run `<test-suite>` | always | `npx --yes newman run <collection>` |
| Dump docker logs on failure | `failure()` | Logs of every compose service, grouped per service |
| Stop Docker Compose services | `always()` | `docker compose down -v` |

### 5. `publish-test-tag`
- Needs `maven-verify` and every `integration-tests` matrix job; it is skipped if any of them
  failed or was cancelled.
- Logs in to GHCR and runs `docker buildx imagetools create` to add the `test` tag to this run's
  `ci-<run_id>-<sha>` connector and DCP issuer images. It copies no layers and rebuilds nothing,
  so `test` always points at images that passed the tests.

### Old images

Nothing deletes the per-run images automatically: every run adds one `ci-…` version to each
package. Remove old versions by hand in the package settings on GitHub (filter by the `ci-` tag
prefix). Do not delete untagged versions: `develop.yml` builds with Buildx, whose provenance
attestations are stored as separate untagged manifests of other images.

## Matrix

| # | `test-suite` | `mode` | `requires-dcp` | `requires-vc` |
|---|--------------|--------|----------------|---------------|
| 1 | `api-endpoints-tests` | standard | false | - |
| 2 | `dataset-api-tests` | standard | false | - |
| 3 | `connector-tests` | standard | false | - |
| 4 | `negotiation-api-without-counteroffer-tests` | standard | false | - |
| 5 | `negotiation-api-without-counteroffer-tests` | dcp | true | true |
| 6 | `datatransfer-api-http-pull-tests` | standard | false | - |
| 7 | `datatransfer-api-http-pull-tests` | dcp | true | true |
| 8 | `datatransfer-api-http-push-tests` | standard | false | - |
| 9 | `datatransfer-api-http-push-tests` | dcp | true | true |
| 10 | `negotiation-api-with-counteroffer-tests` | standard | false | - |
| 11 | `api-endpoints-tenant-tests` | standard | false | - |
| 12 | `dcp-credential-issuance-tests` | dcp | true | - |

Each entry also sets `collection` (path to the Newman collection) and `docker-compose-file`.
`dcp-credential-issuance-tests` does not set `requires-vc` because issuing credentials is what it
tests.

### Matrix parameters

| Parameter | Meaning |
|-----------|---------|
| `test-suite` | Job display name, shown as `<test-suite> (<mode>)` |
| `collection` | Newman collection to run |
| `docker-compose-file` | `./ci/docker/docker-compose.yml` or `./ci/docker/docker-compose-dcp.yml` |
| `mode` | `standard` or `dcp`, used in the job name |
| `requires-dcp` | Run the DCP Issuer health check |
| `requires-vc` | Obtain Verifiable Credentials before the test collection |

## Compose stacks

| Service | Container | Host port | Standard | DCP |
|---------|-----------|-----------|----------|-----|
| RustFS (S3) | `s3storage` | 9000, 9001 (console) | yes | yes |
| Connector A | `connector-a` | 8080 | yes | yes |
| MongoDB A | `mongodb-a` | 27017 | yes | yes |
| Connector B | `connector-b` | 8090 | yes | yes |
| MongoDB B | `mongodb-b` | 27018 | yes | yes |
| DCP Issuer | `dcp-issuer` | 8084 | no | yes |
| Issuer MongoDB | `mongodb` | 27019 | no | yes |

## Adding a test

Add one matrix entry per mode the test should run in:

```yaml
# Standard mode
- test-suite: my-new-test
  collection: ./ci/docker/test-cases/my-new-test/my-new-test.json
  docker-compose-file: ./ci/docker/docker-compose.yml
  mode: standard
  requires-dcp: false

# DCP mode
- test-suite: my-new-test
  collection: ./ci/docker/test-cases/my-new-test/my-new-test.json
  docker-compose-file: ./ci/docker/docker-compose-dcp.yml
  mode: dcp
  requires-dcp: true
  requires-vc: true   # omit if the test must not start with credentials
```

## Troubleshooting

| Symptom | Check |
|---------|-------|
| `manifest unknown` / image not found when starting compose | Did both build jobs succeed and push the `ci-…` tag? Was that version deleted by hand from GHCR? |
| Connector health check times out | `connector-a` / `connector-b` logs printed by the health-check step |
| DCP test fails, standard passes | DCP Issuer health step, then the **Obtain Verifiable Credentials** step, then the `dcp-issuer` logs |
| Images pile up in GHCR | Expected: there is no automatic cleanup (see *Old images*) |

## Deprecated workflows

| Workflow | Status |
|----------|--------|
| `build.yml` | Disabled: triggers only on a branch named `__deprecated__`. Still active on `develop`, where `integration-tests.yml` does not exist yet. |
| `dcp-tests.yml` | Disabled for push; still has `workflow_dispatch`. |
