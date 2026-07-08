# Async S3 Upload CI Test Jobs — Design

## Problem

The `datatransfer-api-http-pull-tests` and `datatransfer-api-http-push-tests` CI jobs run
the connectors with `s3.upload-mode=SYNC`. The `ASYNC` upload path is never exercised in CI,
leaving a gap in transfer lifecycle coverage.

## Proposed Solution

Add two new CI jobs — one for HTTP pull, one for HTTP push — that run the same Newman
collections against connectors configured with `s3.upload-mode=ASYNC`. The async jobs run in
parallel with the existing sync jobs on separate GitHub Actions runners.

## Components

### 1. `ci/docker/docker-compose-async-override.yml`

A minimal Docker Compose override file. It adds `S3_UPLOAD_MODE=ASYNC` to the `environment`
of both `connector-a` and `connector-b`. Spring Boot relaxed binding maps `S3_UPLOAD_MODE`
to the `s3.upload-mode` property, overriding the `SYNC` default in `application.properties`.

No existing files are modified.

```yaml
services:
  connector-a:
    environment:
      - S3_UPLOAD_MODE=ASYNC
  connector-b:
    environment:
      - S3_UPLOAD_MODE=ASYNC
```

### 2. Two new jobs in `.github/workflows/build.yml`

**`datatransfer-api-http-pull-tests-async`** and **`datatransfer-api-http-push-tests-async`**

Both jobs are exact mirrors of their sync counterparts with one change: the
`docker compose up` command uses both the base file and the override file.

```
docker compose \
  -f ./ci/docker/docker-compose.yml \
  -f ./ci/docker/docker-compose-async-override.yml \
  --env-file ./ci/docker/.env up -d
```

The `down -v` teardown command also uses both `-f` flags to ensure a clean shutdown.

Both jobs:
- Depend on `build-and-push-image`
- Call `generate-test-data.sh` (required for suspend/resume timing)
- Sleep 60 seconds for container startup
- Run the same Newman collections as their sync counterparts with `timeoutScript: 25000`
- Dump Docker logs on failure

## What Does Not Change

- `ci/docker/connector_a_resources/application.properties` — unchanged
- `ci/docker/connector_b_resources/application.properties` — unchanged
- Existing Newman test collection `.json` files — unchanged
- Existing sync CI jobs — unchanged
- `develop.yml` — unchanged (it does not run these test jobs)

## Parallel Execution

All four transfer test jobs (`pull`, `push`, `pull-async`, `push-async`) share only the
`build-and-push-image` dependency. Each runs on a separate `ubuntu-latest` runner, so
there are no port conflicts or shared state between them.
