# D-TEC-011 — Per-run image tags for integration test runs

## Metadata
- Status: Superseded
- Date: 2026-11-18
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: [D-TEC-012](D-TEC-012-publish-test-tag-after-green-run.md)
- Tags: ci, github-actions, docker, ghcr, integration-tests
- Risk Level: Medium

> **Superseded (2026-11-19).** The per-run tags below still apply, but the build jobs no longer
> push `test`. A final `publish-test-tag` job moves `test` to the run's images only after every test
> job is green; see [D-TEC-012](D-TEC-012-publish-test-tag-after-green-run.md).

## Context

`.github/workflows/integration-tests.yml` runs on every push to `feature/*`, `hotfix/*` and
`fix/*`. It builds the connector and DCP issuer images, pushes them to GHCR, and runs the Newman
suites in a 12-job matrix. Every test job starts its stack with Docker Compose, which pulls the
images from GHCR by name.

All runs pushed and pulled the same tag, `:test`. The concurrency group is per branch
(`integration-tests-<ref>`), so runs on different branches run at the same time. When two branches
ran concurrently, one branch's push replaced `:test` while the other branch's matrix jobs were
still pulling it. A run could then test another branch's code, or a mix of branches across its
matrix jobs or between its connector and issuer images. Implementing several tickets in parallel,
each on its own branch, made this a routine situation instead of a rare one.

`:test` also has consumers outside this workflow: the manual TCK workflow
(`ci/tck/docker-compose.yml`) and the Terraform deployment (`terraform.tfvars`). They expect
`:test` to hold a recent feature-branch build.

Unique tags add one package version per run, and GHCR storage for private packages counts against
the organisation's quota. Deleting a GHCR package version removes every tag on it, so an image
cannot lose its per-run tag while keeping `:test`.

## Decision

Each run tags both images `ci-<run_id>-<sha>` as well as `test`, and its test jobs pull only the
per-run tag, via `${IMAGE_TAG:-test}` in the CI compose files. Per-run images are not deleted by
the workflow; old versions are removed by hand when needed.

## Alternatives Considered
- **Serialise all runs with one repository-wide concurrency group** → rejected because it removes
  parallel CI entirely; runs on different branches would queue behind each other.
- **Pass images between jobs as workflow artifacts (`docker save` / `docker load`)** → rejected
  because it moves the full image to every matrix job, and the workflow already moved away from
  this for the issuer image. It also leaves `:test` without a publisher for TCK and Terraform.
- **Per-run tags only, without `:test`** → rejected by the maintainer to keep the manual TCK
  workflow and Terraform working unchanged.
- **Per-run tags with a scheduled (weekly) cleanup workflow** → rejected by the maintainer.
- **Per-run tags with a cleanup job at the end of each run** → tried and removed. The job deleted
  the run's versions (unless still tagged `test`) plus older `ci-`-only orphans, but on its first
  run the workflow's `GITHUB_TOKEN` could not list the package versions, so it deleted nothing. It
  also made **Re-run failed jobs** impossible and depended on a package permission outside the
  repository. The maintainer chose to drop it.
- **Commit SHA alone as the tag** → rejected because two branches pointing at the same commit
  would share a tag, so a parallel run could overwrite the image another run is testing.

## Rationale

The per-run tag makes what a run tests depend only on what that run built, which is the property
parallel ticket branches need. Keeping `:test` on the same push preserves every existing consumer
with no change outside the CI compose files. The `${IMAGE_TAG:-test}` default keeps local use of
`ci/docker/*.yml` unchanged.

Leaving deletion out of the workflow keeps CI free of a package-admin permission and keeps
failed jobs re-runnable, at the cost of storage that grows with the number of runs. Manual
deletion must never touch untagged versions, because images built with Buildx (`develop.yml`)
store provenance attestations as untagged manifests.

## Consequences

### Positive
- Concurrent runs on different branches test only their own images.
- Within a run, all matrix jobs and both images come from the same build.
- **Re-run failed jobs** works, because a run's images stay available.
- TCK, Terraform and local workflows keep using `:test` unchanged.

### Negative
- Every run adds one version to each of the `dsp-true-connector` and `dcp-issuer` packages, and
  nothing removes them automatically.
- `:test` itself is still overwritten by whichever branch pushed last, so its consumers still get
  "some recent feature build", not a specific one.
- `build.yml` on `develop` still pushes `:test`, so it can overwrite the `:test` used by TCK and
  Terraform. CI on the DCP branches is not affected, since it no longer pulls `:test`.

### Risks
- **Package storage grows** (counts against the organisation quota if the packages are private).
  Mitigation: delete old `ci-` versions by hand in the package settings; most layers are shared
  between versions, so each run mostly adds the application layer. Revisit automatic cleanup if
  storage becomes a problem.
- **Manual deletion removes a version a run is still using**: that run's later jobs fail to pull.
  Mitigation: only delete versions older than the newest runs.

## Related
- Decisions: [D-TEC-002 Testcontainers for integration testing](D-TEC-002-testcontainers-integration-testing.md)
- Docs: [doc/gha/INTEGRATION_TESTS_GUIDE.md](../../gha/INTEGRATION_TESTS_GUIDE.md), [doc/gha/README.md](../../gha/README.md)
- Tickets: [#397 DCP v1.0.1 spec gap remediation roadmap](https://github.com/Engineering-Research-and-Development/dsp-true-connector/issues/397) (parallel ticket branches)
