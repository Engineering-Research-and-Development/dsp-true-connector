# Data-transfer CI Sync / Async Workflow Design

**Date:** 2026-05-29  
**Branch baseline:** `feature/suspend-resume`  
**Reference branch:** `feature/suspend-resume-transfers`

## Problem

`feature/suspend-resume-transfers` added separate CI coverage for HTTP-PULL sync, HTTP-PULL async, HTTP-PUSH sync, and HTTP-PUSH async. On the current branch, the data-transfer test collections have moved forward to cover suspend/resume scenarios and the workflow startup has improved with:

- generated large transfer fixtures
- pull/push-specific Docker Compose profiles
- explicit connector and dataplane health checks
- dataplane registration polling before Newman runs

The gap is that the current branch now runs only one pull job and one push job, and both of those jobs are wired through the async override. That means the intent from the reference branch is only partially preserved: async coverage exists, but dedicated sync coverage no longer does.

The design goal is therefore to bring back the reference branch's four-way sync/async coverage while keeping the current branch's newer startup, readiness, and suspend/resume test behavior.

## Goals

- Restore distinct CI coverage for:
  - HTTP-PULL sync
  - HTTP-PULL async
  - HTTP-PUSH sync
  - HTTP-PUSH async
- Keep the current branch's suspend/resume-aware Newman collections as the single source of protocol assertions.
- Keep the current branch's large-fixture generation, profile-scoped Compose startup, health checks, dataplane registration polling, failure diagnostics, and teardown behavior.
- Keep the workflow shape as close as practical to `feature/suspend-resume-transfers`, especially in job naming and sync/async visibility in CI results.
- Limit the implementation to the current branch's build workflow and the helper assets that already exist on this branch.

## Non-goals

- Revert the current branch to the reference branch's older sleep-based startup flow.
- Introduce separate Newman collections for sync and async variants of the same protocol.
- Rewrite the workflow into a matrix or reusable-workflow refactor as part of this task.
- Copy unrelated workflow changes from the reference branch line-for-line without reconciling them against the current branch.

## Decisions

| Question | Decision |
| --- | --- |
| Where the data-transfer suites live | Keep them in `.github/workflows/build.yml` |
| Workflow shape | Use four explicit jobs instead of a matrix |
| Sync vs async distinction | The only intentional mode difference is whether `ci/docker/docker-compose-async-override.yml` is layered in |
| Pull vs push distinction | Keep the current branch's `--profile pull` and `--profile push` separation |
| Test collections | Reuse the current branch's existing pull and push Newman collections for both sync and async jobs |
| Startup/readiness behavior | Keep the current branch's connector health checks, dataplane health checks, and dataplane registration polling |
| Reference-branch parity | Preserve the reference branch's explicit pull/push sync/async job layout and naming, but adapt the implementation details to the current branch |

## Workflow Shape

`build-and-push-image` remains the upstream dependency for all four data-transfer jobs.

The workflow should expose these jobs:

| Job | Compose files | Profile | Newman collection |
| --- | --- | --- | --- |
| `datatransfer-api-http-pull-tests` | `ci/docker/docker-compose.yml` | `pull` | `ci/docker/test-cases/datatransfer-api-http-pull-tests/datatransfer-api-http-pull-tests.json` |
| `datatransfer-api-http-pull-tests-async` | `ci/docker/docker-compose.yml` + `ci/docker/docker-compose-async-override.yml` | `pull` | `ci/docker/test-cases/datatransfer-api-http-pull-tests/datatransfer-api-http-pull-tests.json` |
| `datatransfer-api-http-push-tests` | `ci/docker/docker-compose.yml` | `push` | `ci/docker/test-cases/datatransfer-api-http-push-tests/datatransfer-api-http-push-tests.json` |
| `datatransfer-api-http-push-tests-async` | `ci/docker/docker-compose.yml` + `ci/docker/docker-compose-async-override.yml` | `push` | `ci/docker/test-cases/datatransfer-api-http-push-tests/datatransfer-api-http-push-tests.json` |

This keeps the CI surface easy to read: when a run fails, the job name itself tells whether the failure belongs to pull or push and to sync or async mode.

## Shared Job Behavior

Each of the four jobs should follow the current branch's runtime pattern rather than the reference branch's older minimal bootstrap:

1. check out the repository
2. generate the large transfer fixture with the current branch's existing `TARGET_MEBIBYTES=128 bash ./ci/docker/generate-test-data.sh` step
3. start Docker Compose with the correct profile and the correct compose-file set for the job's mode
4. wait for both connectors to become healthy
5. wait for the relevant dataplane pair to become healthy:
   - pull jobs wait for the HTTP-PULL dataplanes
   - push jobs wait for the HTTP-PUSH dataplanes
6. wait for dataplane registration to appear in both connectors
7. run the current branch's Newman collection for that protocol
8. on failure, dump logs from the same compose/profile combination used by the job
9. always tear down with the same compose-file set used for startup

The current branch already has more informative readiness and registration checks than the reference branch. Those checks should be copied across the four jobs rather than replaced by unconditional sleeps.

## Mode and Assertion Strategy

The sync and async jobs for a given protocol intentionally share the same Newman collection. The purpose of the split is to validate runtime behavior under two S3 upload modes, not to validate two different sets of API assertions.

That means:

- suspend/resume assertions stay identical between sync and async runs
- presigned URL and downloaded-content assertions stay identical between sync and async runs
- any divergence between sync and async results should point to runtime behavior, not to drift between two separate test collections

If a future mode-specific assertion becomes necessary, it should be justified by an actual protocol/runtime difference. The default rule for this work is shared protocol collections, different compose wiring.

## Scope Boundaries

This work should stay tight:

- prefer updating `build.yml` instead of refactoring the workflow architecture
- reuse `ci/docker/docker-compose-async-override.yml`, `ci/docker/generate-test-data.sh`, and the current branch's updated Newman collections as they already exist
- keep the current branch's pull/push profile model
- do not overwrite the current branch's suspend/resume collection changes with older files from the reference branch
- do not spread the change into unrelated workflows unless the current branch later proves that `build.yml` alone is insufficient

In short: port the **intent** of the reference branch's sync/async coverage, not the exact implementation text.

## Validation Expectations

After implementation, the workflow should satisfy these checks:

- `build.yml` defines four explicit data-transfer jobs with the names listed above
- the sync jobs use only `ci/docker/docker-compose.yml`
- the async jobs add `ci/docker/docker-compose-async-override.yml`
- pull jobs run only the pull dataplanes and use the pull collection
- push jobs run only the push dataplanes and use the push collection
- all four jobs preserve the current branch's readiness, registration, failure-log, and teardown behavior
- the same current-branch suspend/resume Newman collections are exercised in both sync and async mode for each protocol

## Recommended Implementation Order

1. split the current pull job into sync and async variants
2. split the current push job into sync and async variants
3. factor only the minimum shared environment or shell snippets needed to keep the YAML readable
4. verify that job naming, compose-file wiring, and profile-specific waits still match the intended mode

This keeps the change close to the reference branch's visible CI shape while preserving the current branch's newer behavior.
