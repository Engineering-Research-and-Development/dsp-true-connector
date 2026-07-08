# Async S3 Upload CI Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `datatransfer-api-http-pull-tests-async` and `datatransfer-api-http-push-tests-async` CI jobs that run the same Newman collections as the sync variants but with `s3.upload-mode=ASYNC`.

**Architecture:** A new Docker Compose override file (`docker-compose-async-override.yml`) injects `S3_UPLOAD_MODE=ASYNC` into both connector services via environment variable — Spring Boot's relaxed binding maps this to `s3.upload-mode`, overriding the `SYNC` default in `application.properties`. Two new jobs in `build.yml` use `docker compose -f ... -f ...` to merge the override on top of the base compose file.

**Tech Stack:** GitHub Actions, Docker Compose (v2 CLI), Newman (matt-ball/newman-action@v2.0.0), YAML

---

## File Map

| Action   | Path                                             | Purpose                                       |
|----------|--------------------------------------------------|-----------------------------------------------|
| Create   | `ci/docker/docker-compose-async-override.yml`    | Compose override: sets `S3_UPLOAD_MODE=ASYNC` |
| Modify   | `.github/workflows/build.yml`                    | Add two async transfer test jobs              |

---

### Task 1: Create the Docker Compose override file

**Files:**
- Create: `ci/docker/docker-compose-async-override.yml`

- [ ] **Step 1: Create the override file**

  Create `ci/docker/docker-compose-async-override.yml` with the following content:

  ```yaml
  services:
    connector-a:
      environment:
        - S3_UPLOAD_MODE=ASYNC
    connector-b:
      environment:
        - S3_UPLOAD_MODE=ASYNC
  ```

- [ ] **Step 2: Validate the YAML is syntactically correct**

  Run:
  ```bash
  python3 -c "import yaml; yaml.safe_load(open('ci/docker/docker-compose-async-override.yml'))" && echo "OK"
  ```
  Expected output: `OK`

- [ ] **Step 3: Smoke-test that Compose merges the override cleanly (requires Docker)**

  Run:
  ```bash
  docker compose \
    -f ci/docker/docker-compose.yml \
    -f ci/docker/docker-compose-async-override.yml \
    --env-file ci/docker/.env \
    config --quiet
  ```
  Expected: exits 0, no errors. (If Docker is unavailable in this environment, skip this step.)

- [ ] **Step 4: Commit**

  ```bash
  git add ci/docker/docker-compose-async-override.yml
  git commit -m "ci: add Docker Compose override for async S3 upload mode

  Injects S3_UPLOAD_MODE=ASYNC into connector-a and connector-b.
  Spring Boot relaxed binding maps this to s3.upload-mode, overriding
  the SYNC default in application.properties.

  Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
  ```

---

### Task 2: Add the async pull-tests CI job

**Files:**
- Modify: `.github/workflows/build.yml` — append after `datatransfer-api-http-pull-tests` job

- [ ] **Step 1: Append the async pull job to `build.yml`**

  Add the following job directly after the closing line of `datatransfer-api-http-pull-tests`
  (before `datatransfer-api-http-push-tests`):

  ```yaml
    datatransfer-api-http-pull-tests-async:
      needs: build-and-push-image
      runs-on: ubuntu-latest

      steps:
        - name: Git Checkout
          uses: actions/checkout@v4

        - name: Generate large test data file for suspend/resume timing
          run: bash ./ci/docker/generate-test-data.sh

        - name: Run docker container for datatransfer-api-http-pull-tests-async
          run: docker compose -f ./ci/docker/docker-compose.yml -f ./ci/docker/docker-compose-async-override.yml --env-file ./ci/docker/.env up -d

        - name: Wait for container starting
          run: sleep 60

        - name: Check if the container is up and running
          run: docker ps -a

        - uses: matt-ball/newman-action@v2.0.0
          with:
            collection: ./ci/docker/test-cases/datatransfer-api-http-pull-tests/datatransfer-api-http-pull-tests.json
            timeoutScript: 25000

        - name: Dump docker datatransfer-api-http-pull-tests-async
          if: failure()
          uses: jwalton/gh-docker-logs@v2

        - name: Stop docker container datatransfer-api-http-pull-tests-async
          run: docker compose -f ./ci/docker/docker-compose.yml -f ./ci/docker/docker-compose-async-override.yml --env-file ./ci/docker/.env down -v
  ```

- [ ] **Step 2: Validate the workflow YAML**

  Run:
  ```bash
  python3 -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml'))" && echo "OK"
  ```
  Expected output: `OK`

- [ ] **Step 3: Commit**

  ```bash
  git add .github/workflows/build.yml
  git commit -m "ci: add datatransfer-api-http-pull-tests-async job

  Mirrors the sync pull job but boots connectors with S3_UPLOAD_MODE=ASYNC
  via the new docker-compose-async-override.yml. Reuses the same Newman
  collection. Runs in parallel on a separate ubuntu-latest runner.

  Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
  ```

---

### Task 3: Add the async push-tests CI job

**Files:**
- Modify: `.github/workflows/build.yml` — append after `datatransfer-api-http-push-tests` job

- [ ] **Step 1: Append the async push job to `build.yml`**

  Add the following job directly after the closing line of `datatransfer-api-http-push-tests`
  (before `api-endpoints-tests`):

  ```yaml
    datatransfer-api-http-push-tests-async:
      needs: build-and-push-image
      runs-on: ubuntu-latest

      steps:
        - name: Git Checkout
          uses: actions/checkout@v4

        - name: Generate large test data file for suspend/resume timing
          run: bash ./ci/docker/generate-test-data.sh

        - name: Run docker container for datatransfer-api-http-push-tests-async
          run: docker compose -f ./ci/docker/docker-compose.yml -f ./ci/docker/docker-compose-async-override.yml --env-file ./ci/docker/.env up -d

        - name: Wait for container starting
          run: sleep 60

        - name: Check if the container is up and running
          run: docker ps -a

        - uses: matt-ball/newman-action@v2.0.0
          with:
            collection: ./ci/docker/test-cases/datatransfer-api-http-push-tests/datatransfer-api-http-push-tests.json
            timeoutScript: 25000

        - name: Dump docker datatransfer-api-http-push-tests-async
          if: failure()
          uses: jwalton/gh-docker-logs@v2

        - name: Stop docker container datatransfer-api-http-push-tests-async
          run: docker compose -f ./ci/docker/docker-compose.yml -f ./ci/docker/docker-compose-async-override.yml --env-file ./ci/docker/.env down -v
  ```

- [ ] **Step 2: Validate the workflow YAML**

  Run:
  ```bash
  python3 -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml'))" && echo "OK"
  ```
  Expected output: `OK`

- [ ] **Step 3: Commit**

  ```bash
  git add .github/workflows/build.yml
  git commit -m "ci: add datatransfer-api-http-push-tests-async job

  Mirrors the sync push job but boots connectors with S3_UPLOAD_MODE=ASYNC
  via the new docker-compose-async-override.yml. Reuses the same Newman
  collection. Runs in parallel on a separate ubuntu-latest runner.

  Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
  ```
