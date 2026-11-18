# D-TEC-011 — Per-run image tags with cleanup after each CI run

## Metadata
- Status: Accepted
- Date: 2026-11-18
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: ci, github-actions, docker, ghcr, integration-tests
- Risk Level: Medium

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

Each run tags both images `ci-<run_id>-<sha>` as well as `test`. Its test jobs pull only the
per-run tag, via `${IMAGE_TAG:-test}` in the CI compose files. A final `cleanup-images` job, which
always runs, deletes this run's package versions unless they still carry `test`. It also deletes
older versions whose only tags are `ci-` tags once they are more than six hours old.

## Alternatives Considered
- **Serialise all runs with one repository-wide concurrency group** → rejected because it removes
  parallel CI entirely; runs on different branches would queue behind each other.
- **Pass images between jobs as workflow artifacts (`docker save` / `docker load`)** → rejected
  because it moves the full image to every matrix job, and the workflow already moved away from
  this for the issuer image. It also leaves `:test` without a publisher for TCK and Terraform.
- **Per-run tags only, without `:test`** → rejected by the maintainer to keep the manual TCK
  workflow and Terraform working unchanged.
- **Per-run tags with a scheduled (weekly) cleanup workflow** → rejected by the maintainer in
  favour of cleanup inside the run itself.
- **Commit SHA alone as the tag** → rejected because two branches pointing at the same commit
  would share a tag, and one run's cleanup would delete images the other run is still testing.

## Rationale

The per-run tag makes what a run tests depend only on what that run built, which is the property
parallel ticket branches need. Keeping `:test` on the same push preserves every existing consumer
with no change outside the CI compose files. The `${IMAGE_TAG:-test}` default keeps local use of
`ci/docker/*.yml` unchanged.

Cleanup at the end of each run removes the storage cost of unique tags without a separate
scheduled workflow. The version that still holds `:test` cannot be deleted without breaking those
consumers, so it is kept. Once a later push moves `:test`, that version only has its `ci-` tag; the
next run's cleanup collects it as an orphan. The six-hour age limit keeps the sweep from touching
images of runs that are still in progress. The cleanup never deletes untagged versions, because
images built with Buildx (`develop.yml`) store provenance attestations as untagged manifests.

## Consequences

### Positive
- Concurrent runs on different branches test only their own images.
- Within a run, all matrix jobs and both images come from the same build.
- Package versions created by CI are removed automatically; at most the version holding `:test`
  and runs younger than six hours remain.
- TCK, Terraform and local workflows keep using `:test` unchanged.

### Negative
- **Re-run failed jobs** no longer works after the run has finished, because the images are gone;
  a full **Re-run all jobs** is needed.
- `:test` itself is still overwritten by whichever branch pushed last, so its consumers still get
  "some recent feature build", not a specific one.
- The cleanup job needs Admin access to each package for the repository's `GITHUB_TOKEN`, an extra
  setting outside the workflow file.
- `build.yml` on `develop` still pushes `:test`, so it can overwrite the `:test` used by TCK and
  Terraform. CI on the DCP branches is not affected, since it no longer pulls `:test`.

### Risks
- **Cleanup lacks permission**: images accumulate. Mitigation: the job logs a warning per failed
  listing or deletion, and the troubleshooting guide explains the fix.
- **A run lasts longer than six hours**: a later run's sweep could delete its images. Mitigation:
  GitHub-hosted jobs time out at six hours by default; raise `ORPHAN_MIN_AGE_HOURS` if jobs ever get
  longer timeouts.
- **Pagination cost grows with the number of versions**: each cleanup lists every version of both
  packages. Mitigation: the sweep keeps the version count small.

## Related
- Decisions: [D-TEC-002 Testcontainers for integration testing](D-TEC-002-testcontainers-integration-testing.md)
- Docs: [doc/gha/INTEGRATION_TESTS_GUIDE.md](../../gha/INTEGRATION_TESTS_GUIDE.md), [doc/gha/README.md](../../gha/README.md)
- Tickets: [#397 DCP v1.0.1 spec gap remediation roadmap](https://github.com/Engineering-Research-and-Development/dsp-true-connector/issues/397) (parallel ticket branches)
