# D-TEC-012 — Move the `test` image tag only after a green integration test run

## Metadata
- Status: Accepted
- Date: 2026-11-19
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: [D-TEC-011](D-TEC-011-per-run-ci-image-tags.md)
- Superseded by: —
- Tags: ci, github-actions, docker, ghcr, integration-tests
- Risk Level: Low

## Context

[D-TEC-011](D-TEC-011-per-run-ci-image-tags.md) gave each run of
`.github/workflows/integration-tests.yml` its own image tag, `ci-<run_id>-<sha>`, so parallel runs
on different branches never test each other's images. The same build jobs also pushed `test`, at
build time and before any test had run.

`test` has consumers outside this workflow: the manual TCK workflow (`ci/tck/docker-compose.yml`)
and the Terraform deployment (`terraform.tfvars`). Because it moved on every build, a run whose
`mvn clean verify` or Newman suites later failed still left `test` pointing at its broken images.
With several ticket branches running CI in parallel (roadmap #397), red runs are frequent, and TCK
or Terraform could pick up an image that never passed the tests.

The Newman matrix pulls its images from GHCR, so the per-run tag must still be pushed before the
tests start.

## Decision

The build jobs push only `ci-<run_id>-<sha>`. A final job, `publish-test-tag`, which needs
`maven-verify` and every `integration-tests` matrix job, adds the `test` tag to that run's
connector and DCP issuer images with `docker buildx imagetools create`. Everything else in
D-TEC-011 stays: test jobs pull the per-run tag through `${IMAGE_TAG:-test}`, and per-run images
are not deleted by the workflow.

## Alternatives Considered
- **Push nothing until the run is green; pass images to the test jobs as workflow artifacts
  (`docker save` / `docker load`)** → rejected by the maintainer in favour of the faster option.
  It moves the full images into every one of the 12 matrix jobs (roughly 1–3 minutes more per run)
  and rewrites every job. Its gain, keeping untested `ci-…` images out of GHCR, is not needed to
  protect the `test` consumers.
- **Rebuild and push the images in a final job after the tests** → rejected because the images
  behind `test` would be a new build, not the ones that passed the tests.
- **Keep pushing `test` at build time (D-TEC-011)** → rejected because red runs keep moving `test`
  to images that failed.

## Rationale

Retagging in the registry copies no layers and rebuilds nothing, so `publish-test-tag` takes
seconds and `test` points at exactly the image digests the matrix tested. The per-run push before
the tests is unchanged, so the isolation between parallel branch runs from D-TEC-011 is kept.
GitHub Actions skips a job whose `needs` failed or were cancelled, so no extra condition is
needed, and **Re-run failed jobs** re-runs the skipped `publish-test-tag` once the failed jobs pass.

## Consequences

### Positive
- TCK and Terraform only receive images that passed `mvn clean verify` and all Newman suites.
- The run gains one short job; the image builds and the matrix are unchanged.
- A failed run no longer changes `test`.

### Negative
- `test` moves later than before: after the slowest Newman suite, not after the image build.
- Failed runs still push their `ci-…` images to GHCR, and storage still grows by one version per
  run, as in D-TEC-011.
- `test` is still shared: it points at whichever branch's run went green last, not at a specific
  branch.
- `build.yml` on `develop` still pushes `test` at build time and can overwrite it.

### Risks
- **A green run finishing before an older green run on another branch**: the older run's images
  then replace the newer ones behind `test`. Mitigation: consumers already treat `test` as "a
  recent green feature build"; use a `ci-…` tag when a specific build is needed.
- **`imagetools create` needs registry access in the final job**: it logs in to GHCR with the
  workflow's `GITHUB_TOKEN` and `packages: write`, the same permission the build jobs use.

## Related
- Decisions: [D-TEC-011 Per-run image tags for integration test runs](D-TEC-011-per-run-ci-image-tags.md) (superseded),
  [D-TEC-002 Testcontainers for integration testing](D-TEC-002-testcontainers-integration-testing.md)
- Docs: [doc/gha/INTEGRATION_TESTS_GUIDE.md](../../gha/INTEGRATION_TESTS_GUIDE.md), [doc/gha/README.md](../../gha/README.md)
- Tickets: [#397 DCP v1.0.1 spec gap remediation roadmap](https://github.com/Engineering-Research-and-Development/dsp-true-connector/issues/397) (parallel ticket branches)
