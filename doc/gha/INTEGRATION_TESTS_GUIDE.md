# Integration Tests Workflow - Complete Guide

Reference for `.github/workflows/integration-tests.yml`. For an overview start with
[README.md](README.md); the design decision behind the image tags is
[D-TEC-011](../decisions/technical/D-TEC-011-per-run-ci-image-tags.md).

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
  packages: write       # push and delete images in GHCR
  attestations: write
  id-token: write
```

Deleting package versions with `GITHUB_TOKEN` also needs the repository to have **Admin** access
on each package (`dsp-true-connector`, `dcp-issuer`): *Package settings → Manage Actions access*.
Packages first published by this repository's workflows normally have it. Without it, the cleanup
job logs a warning and leaves the images in place; the workflow still passes.

## Image tags

```yaml
env:
  IMAGE_TAG: ci-${{ github.run_id }}-${{ github.sha }}
```

| Tag | Pushed by | Used by |
|-----|-----------|---------|
| `ci-<run_id>-<sha>` | both build jobs | the test jobs of the same run |
| `test` | both build jobs (moves on every run) | manual TCK workflow (`ci/tck/docker-compose.yml`), Terraform (`terraform.tfvars`), local runs |

The run ID is part of the tag so two branches at the same commit still get different tags. The CI
compose files reference `${IMAGE_TAG:-test}`, so they pull the run's tag in CI and fall back to
`test` locally.

`test` is shared and is overwritten by whichever branch pushed last. Only use it where "the most
recent feature-branch build" is acceptable.

## Jobs

### 1. `build-connector-image`
- Checks out the repository, sets up JDK 21 (Temurin) with the Maven cache.
- Runs `mvn -B clean verify` for the whole project, including unit and integration tests.
- Builds `connector/Dockerfile` with `no-cache: true` and pushes both tags to
  `ghcr.io/engineering-research-and-development/dsp-true-connector`.

### 2. `build-dcp-issuer-image`
- Checks out the repository, sets up JDK 21 with the Maven cache.
- Runs `mvn -B clean package -DskipTests --file dcp/pom.xml`.
- Builds `dcp/dcp-issuer/Dockerfile` and pushes both tags to
  `ghcr.io/engineering-research-and-development/dcp-issuer`.

The two build jobs run in parallel.

### 3. `integration-tests` (matrix)

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

### 4. `cleanup-images`

Runs after the other jobs with `if: always()`: on success, failure and cancellation. For each
package it lists the versions and deletes:

1. **This run's version**: the one tagged `ci-<run_id>-<sha>`, unless it still carries `test`.
   The version holding `test` is kept so TCK and Terraform keep working.
2. **Orphans**: versions whose tags are all `ci-…` tags and that were last updated more than
   `ORPHAN_MIN_AGE_HOURS` (6) hours ago. These are earlier runs' images that were holding `test`
   when their run ended and lost it later. The age limit protects runs still in progress.

It never deletes untagged versions, `test`, `develop`, `latest` or release tags. Untagged versions
can be parts of other images: `develop.yml` builds with Buildx, whose provenance attestations are
stored as separate untagged manifests. Deleting them could break those images.

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
| `manifest unknown` / image not found when starting compose | Did both build jobs succeed? Was the run re-run with **Re-run failed jobs** after cleanup deleted the images? Use **Re-run all jobs**. |
| Connector health check times out | `connector-a` / `connector-b` logs printed by the health-check step |
| DCP test fails, standard passes | DCP Issuer health step, then the **Obtain Verifiable Credentials** step, then the `dcp-issuer` logs |
| Cleanup job warns `Cannot list versions` or `Could not delete` | The repository lacks Admin access on the package (see Permissions) |
| Images pile up in GHCR | Same as above; or runs whose image still held `test` are younger than 6 hours |

## Deprecated workflows

| Workflow | Status |
|----------|--------|
| `build.yml` | Disabled: triggers only on a branch named `__deprecated__`. Still active on `develop`, where `integration-tests.yml` does not exist yet. |
| `dcp-tests.yml` | Disabled for push; still has `workflow_dispatch`. |
