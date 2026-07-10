# DSP TRUE Connector — Agent Instructions

Production-grade connector implementing the [Dataspace Protocol (DSP) 2025-1](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/) for secure, sovereign data sharing in dataspaces. Java 17, Spring Boot 3.5.x, multi-module Maven, MongoDB. A single codebase runs as either a **provider** or a **consumer** connector, selected via Spring profile.

## Key Documents

Always reference these before making decisions or writing code:

| Document | Path | Purpose |
|---|---|---|
| **Documentation index** | [`doc/README.md`](doc/README.md) | Map of all guides — start here to find any doc |
| **Architecture overview** | [`doc/architecture.md`](doc/architecture.md) | Module map, layering, protocol flows, runtime roles |
| **Development procedure** | [`doc/development_procedure.md`](doc/development_procedure.md) | Scrum process, DoR/DoD, branching, GitHub Actions |
| **Contributing guide** | [`CONTRIBUTING.md`](CONTRIBUTING.md) | Prerequisites, build/test commands, PR flow |
| **Glossary** | [`doc/glossary.md`](doc/glossary.md) | DSP and dataspace domain terminology |
| **Architecture decisions** | [`doc/decisions/`](doc/decisions/README.md) | ADRs — why things are the way they are |
| **Changelog** | [`CHANGELOG.md`](CHANGELOG.md) | Version history; must be updated with every applicable change |
| **Java conventions** | [`.github/instructions/java.instructions.md`](.github/instructions/java.instructions.md) | Authoritative Java coding rules |
| **Testing conventions** | [`.github/instructions/junittest.instructions.md`](.github/instructions/junittest.instructions.md) | JUnit 5 test structure, coverage expectations |
| **Model class rules** | [`.github/instructions/model-class-guidelines.instructions.md`](.github/instructions/model-class-guidelines.instructions.md) | Builder pattern, validation, Jackson serialization for protocol models |
| **CI/CD practices** | [`.github/instructions/github-actions-ci-cd-best-practices.instructions.md`](.github/instructions/github-actions-ci-cd-best-practices.instructions.md) | Workflow authoring rules |

## Process Discipline

- Work is tracked in the [GitHub Project dashboard](https://github.com/users/Engineering-Research-and-Development/projects/2) (private — request access). The board uses six columns: **Backlog | To Do | Ready | In Progress | In Review | Done** (see the GitHub-Integrated Task Workflow below). There is no in-repo task breakdown.
- Follow the Scrum workflow in [`doc/development_procedure.md`](doc/development_procedure.md): tasks must satisfy **Definition of Ready** before work starts and **Definition of Done** before they close.
- Feature branches are created from a **synchronized `develop` branch**, squash-merged back via reviewed PR, then deleted.
- Do **not** extend the scope of the current task. If something out of scope needs attention, create a new backlog task instead.
- Every change satisfies DoD: code implemented, covered by junit/integration tests, **all tests pass via `mvn clean verify`** (Docker required — integration tests use Testcontainers), documentation updated, `CHANGELOG.md` updated (if applicable).

## Non-Negotiable Constraints

These apply to every change and must not be skipped:

- **DSP 2025-1 compliance must never regress.** The connector passes 100% of the DSP TCK suite. Any change touching protocol-facing behavior (catalog, negotiation, transfer endpoints or message models) must be verified against the TCK profile — see [`doc/tck/tck_compliancy.md`](doc/tck/tck_compliancy.md).
- **`mvn clean verify` must pass before any PR.** Integration tests run in the `verify` phase and require Docker.
- **Java conventions are enforced** per [`.github/instructions/java.instructions.md`](.github/instructions/java.instructions.md): Records for DTOs and immutable data, `Optional<T>` instead of `null`, immutability by default (`final`, `List.of()`, `Stream.toList()`), pattern matching, Google-style naming.
- **Javadoc is required on all public and protected methods** (Checkstyle `JavadocMethod`/`JavadocStyle`, config in `scripts/ci/checkstyle.xml`): capitalized one-sentence summary ending with a period, plus `@param`/`@return`/`@throws` tags where applicable.
- **Protocol model classes follow the builder/validation pattern** in [`.github/instructions/model-class-guidelines.instructions.md`](.github/instructions/model-class-guidelines.instructions.md) and [`negotiation/doc/model.md`](negotiation/doc/model.md): private constructor, builder, `@NotNull` validation enforced in `build()`, junit coverage for valid construction and validation failure.
- **New features and bug fixes must be covered by tests**: JUnit 5 + Mockito for units; MockMvc + Testcontainers for integration tests (see [`doc/test_containers_starting_guide.md`](doc/test_containers_starting_guide.md)).
- **Module boundaries are respected**: `catalog`, `negotiation`, and `data-transfer` implement their protocol concern independently; shared logic goes in `tools`; `connector` only wires modules together. No cross-module reach-ins.
- **Architecturally significant decisions require an ADR** in [`doc/decisions/`](doc/decisions/README.md) before implementation. Undocumented architectural drift is not acceptable.
- **Security posture is actively maintained**: dependency upgrades addressing CVEs are documented in `CHANGELOG.md` under Security; run SpotBugs + Find Security Bugs via `spotbugs-scan.sh` / `spotbugs-scan.cmd` (see [`doc/spotbugs.md`](doc/spotbugs.md)).
- **All `initial_data*.json` seed files must be updated together, in the same commit, whenever a change alters the shape, required fields, or referencing convention of any seeded document type** (model field renames/additions, `@Id`/technical-key changes, new `@NotNull` constraints, DBRef target changes, etc.). This repository seeds MongoDB from **12 separate files** across 5 directories, and none of them are generated from a single source of truth — each must be edited by hand:
  - `connector/src/main/resources/initial_data.json`
  - `connector/src/main/resources/initial_data-provider.json`
  - `connector/src/main/resources/initial_data-consumer.json`
  - `connector/src/main/resources/initial_data-tck.json`
  - `connector/src/test/resources/initial_data.json`
  - `connector/src/test/resources/initial_data-unittest.json`
  - `connector/src/test/resources/initial_data-tck.json`
  - `ci/docker/connector_a_resources/initial_data.json`
  - `ci/docker/connector_b_resources/initial_data.json`
  - `ci/tck/connector_tck_resources/initial_data-tck.json`
  - `terraform/app-resources/connector_a_resources/initial_data.json`
  - `terraform/app-resources/connector_b_resources/initial_data.json`

  Before closing out any task that changes a seeded model (e.g. adding/renaming a field, splitting a technical id from a business id, changing what a `$ref`/`$id` DBRef points to), run `find . -iname "initial_data*.json" -not -path "*/target/*"` and `grep -rl "it.eng.<pkg>.model.<ModelName>" --include=*.json .` to enumerate every file containing that model, then apply the same structural change to each one. Skipping any file passes local `mvn clean verify` (Testcontainers use their own seed-free state) but fails the Docker Compose + Newman GitHub Actions suites (`ci/docker/test-cases/**`) and/or the TCK profile, because those load the on-disk seed files directly into a real MongoDB instance.

## Project Structure

```
AGENTS.md                         This file — agent instructions
CLAUDE.md                         Pointer to this file for Claude Code
README.md                         Project overview
CONTRIBUTING.md                   Contributor entry point
CHANGELOG.md                      Version history (DoD requires updating it)

.github/skills/                   Copilot-native repository skills (workflow + topic guidance)
.claude/skills/                   Imported Claude workflow mirrors / compatibility files
.agents/skills/                   Vendored external skills (brainstorming)

catalog/                          Catalog document processing (DCAT-AP)
negotiation/                      Contract negotiation state machine
data-transfer/                    Data transfer lifecycle (HTTPS pull, S3, SFTP)
tools/                            Shared utilities (audit, properties, serialization)
connector/                        Application wrapper — entry point, integration tests

doc/                              Documentation (see doc/README.md for the index)
  architecture.md                   Architecture overview
  glossary.md                       Domain terminology
  decisions/                        Architecture Decision Records
  ...                               Operational and configuration guides

.github/instructions/             Authoritative coding/testing/CI rules
.github/ISSUE_TEMPLATE/           Workflow issue templates (slice, task, QA, docs)
.github/workflows/                CI/CD + project board automation
ci/                               Dockerized test environment
scripts/                          Checkstyle config, utility scripts
terraform/                        Kubernetes deployment (IaC)
```

## Workflow Skills

Copilot-native repository skills live under `.github/skills/`; imported Claude workflow mirrors live under `.claude/skills/`; vendored external skills live under `.agents/skills/`. For primary day-to-day workflow routing, prefer the Copilot-native `.github/skills/` versions. Keep `AGENTS.md` as the always-on ruleset for this repository.

| Skill | Use for |
|---|---|
| `functional-slicing` | Stage 1 — turning requirement sources into functional backlog slices |
| `task-decomposition` | Stage 2 — turning backlog functional slices into implementation, QA, and documentation tasks |
| `task-implementation` | Stage 4 — executing any Ready task (implementation, QA, or documentation) through its per-task verification and PR handoff |
| `slice-implementation` | Stage 4 alternate path — executing a fully decomposed functional slice as one coordinated branch and one PR while respecting child-task dependencies |
| `brainstorming` (`.agents/skills/`) | Sharpening ambiguous sources or unresolved design choices before slicing, decomposition, or implementation |
| `playwright-cli` | Browser automation — only relevant for browser-verifiable checks (e.g. against the separate GUI frontend) |
| built-in `/code-review` | Stage 6 — automated self-review and fix after PR creation |

## Workflow Model

This repository uses a phased workflow split across instruction surfaces:

- `CLAUDE.md` is the project entry point for Claude Code sessions.
- `AGENTS.md` holds the always-on rules for this repo.
- `.github/skills/` holds the primary Copilot-native workflow skills and topic skills.
- `.claude/skills/` holds imported workflow mirrors for Claude compatibility.
- `README.md` and `CONTRIBUTING.md` give contributors human-readable entry points.
- `doc/` holds the authoritative guides; `doc/decisions/` holds ADRs.

Use that split intentionally:

- keep global repo constraints and source-of-truth document order in `AGENTS.md`
- keep primary workflow playbooks in `.github/skills/`
- keep topic expertise reusable through the existing repository skills in `.github/skills/`
- keep `.claude/skills/` aligned as secondary mirrors/pointers rather than the canonical Copilot source
- keep contributor-facing workflow explanations in `README.md` / `CONTRIBUTING.md`

Do not duplicate the full workflow playbook in `AGENTS.md`. Keep this file concise and always relevant.

## GitHub-Integrated Task Workflow

This repository uses a 7-stage pipeline that connects requirements slicing, GitHub Issues, AI implementation, per-task verification, slice-level QA, and slice-level documentation into a single traceable flow.

**GitHub Project board** columns: **Backlog** | **To Do** | **Ready** | **In Progress** | **In Review** | **Done**

- **Backlog** — raw requirement sources, manually created issues, and functional slice issues awaiting task decomposition.
- **To Do** — AI-decomposed child tasks created from Backlog functional slices. Every slice's decomposition emits three task families — implementation, slice-level QA, and slice-level documentation — and each family's tasks flow through the same columns below. Visually distinct from raw issues so humans know exactly what needs review.

**Branch strategy**: single-task PRs use `feature/{issue-number}-{short-name}` off a synchronized `develop`; slice PRs use `feature/{parent-slice-number}-slice-{short-name}` off `develop`. PRs target `develop` and are **squash merged** per [`doc/development_procedure.md`](doc/development_procedure.md).

**Issue templates**:

- `.github/ISSUE_TEMPLATE/functional-slice.yml` — Backlog slices
- `.github/ISSUE_TEMPLATE/task.yml` — implementation tasks
- `.github/ISSUE_TEMPLATE/qa-task.yml` — slice-level QA tasks
- `.github/ISSUE_TEMPLATE/docs-task.yml` — slice-level documentation tasks

### Stage 1 — AI Functional Slicing

**Actor**: AI agent.
**Canonical workflow file**: `.github/skills/functional-slicing/SKILL.md`
**Input**: GitHub issues, DSP specification sections, roadmap items, or scoped requirement sources.
**Output**: GitHub issues in **Backlog**.

At a high level, Stage 1 must: turn a requirement source into functional backlog slice issues using `functional-slice.yml`, maximize independent parallel workstreams with explicit slice boundaries, place created issues in **Backlog**, and confirm the full source requirement is covered.

### Stage 2 — AI Task Decomposition

**Actor**: AI agent.
**Canonical workflow file**: `.github/skills/task-decomposition/SKILL.md`
**Input**: existing Backlog functional slice issues, or explicitly scoped requirement slices when requested.
**Output**: GitHub issues in **To Do**, split across three task families per slice.

At a high level, Stage 2 must: turn a backlog functional slice into child issues across three task families (implementation tasks; exactly one slice-level QA task that `Depends on` every sibling implementation task; exactly one slice-level documentation task that `Depends on` every sibling implementation task and the QA task), maximize parallel workstreams with explicit dependencies, place created issues in **To Do**, and mark the source item as decomposed.

### Stage 3 — Human Review

**Actor**: Human (project owner or tech lead).
**Transition**: To Do → **Ready**.

1. Review each To Do issue for completeness and correctness.
2. Verify the implementation prompt is unambiguous.
3. Verify dependencies are accurate — no circular dependencies, no missing prerequisites.
4. Verify the verification checklist contains only deterministic pass/fail items.
5. Edit or refine the issue as needed.
6. Move approved issues to **Ready**.

### Stage 4 — AI Implementation

**Actor**: AI agent (model specified in the issue).
**Canonical workflow file**: `.github/skills/task-implementation/SKILL.md`
**Transition**: Ready → **In Progress**.

At a high level, Stage 4 must: fetch candidates from **Ready** and let the user choose an unblocked issue; claim it, create `feature/{issue-number}-{short-name}` from a synchronized `develop`; resolve material ambiguity before editing; route by task family (implementation → product code; QA → the Verification Checklist *is* the work; docs → authoritative doc updates); self-run the per-task Verification Checklist (`mvn clean verify` with Docker, Checkstyle, integration tests, TCK when protocol-facing) with at most 3 fix-and-retry cycles before `needs-human` and return to **Ready**.

Use `.github/skills/slice-implementation/SKILL.md` instead when the delivery unit is the **entire remaining functional slice** and one PR should close the parent slice plus every included open child task.

### Stage 5 — Pull Request

**Actor**: AI agent (same session).
**Canonical workflow file**: `.github/skills/task-implementation/SKILL.md`
**Transition**: In Progress → **In Review**.

Commit and push the feature branch, create a PR against `develop` with summary and verification results, move the issue to **In Review**. For slice PR mode, the parent slice and every included open child issue move to **In Review** together when the one slice PR opens.

### Stage 6 — PR Review

**Actors**: AI agent (automated self-review), then human reviewer.
**Canonical workflow**: built-in `/code-review` skill.

1. AI agent runs `/code-review` self-review on the open PR. Blocking, important, and cleanly scoped nit findings are fixed and pushed before the human reviewer is asked to approve. See the `task-implementation` skill for the full self-fix procedure.
2. Human reviewer verifies: work matches spec, code and docs follow AGENTS.md rules, no scope creep, DoD satisfied (tests, docs, CHANGELOG).
3. If changes requested: issue moves back to **In Progress**, agent addresses feedback, pushes new commits, moves back to **In Review**. Maximum **2 review cycles** before `needs-human` label and manual takeover.
4. If approved: squash merge PR into `develop` and delete the feature branch.

### Stage 7 — Post-Merge Automation

**Actor**: GitHub Actions (`.github/workflows/project-automation.yml`).
**Transition**: In Review → **Done** (automated on PR merge).
**Trigger**: PR merged into `develop`.
**Setup**: requires a `PROJECT_TOKEN` repository secret with ProjectV2 access.

1. GitHub Action parses **all** closing references from the PR body first and falls back to the branch number only when the PR body has no closing references.
2. Every referenced issue is automatically moved to **Done** on the project board.
3. Every referenced issue is closed if it is not already closed by GitHub's native closing keywords.

**Convention updates** (when the task established new patterns): include updates to `AGENTS.md`, `CLAUDE.md`, or `doc/` in the PR itself, before merge. Slice-level architecture and API documentation updates belong in the slice's dedicated documentation task, not in an implementation task PR.

### Edge Cases

| Scenario | Response |
|---|---|
| Circular dependency in decomposition | Restructure tasks until the graph is a DAG |
| External blocker (outside this repo) | Use `Blocked by` with a description comment; leave in Backlog |
| Per-task verification fails after 3 retries | Label `needs-human`, move to Ready, comment error log |
| Docker unavailable for integration tests | Stop — `mvn clean verify` cannot complete; surface the environment problem instead of skipping ITs |
| TCK compliance fails on a protocol-facing change | Blocking — fix before PR; compliance must never regress |
| A check cannot be automated | Prefix the item with `[manual]` — per-task verification skips it, PR review verifies |
| QA task finds a pre-existing bug outside its slice's impl tasks | Stop and comment on the QA issue describing the bug; the fix belongs in a new implementation task, not in the QA task itself |
| Docs task needs product code to fix a typo in an embedded code snippet | Stop and comment; expand the task's scope explicitly or split off a tiny implementation task |
| Implementation task touches `doc/architecture.md` or module API docs | Out of scope — move those updates into the slice's dedicated documentation task |
| `initial_data*.json` seed files drift between provider/consumer profiles | Blocking — per-task verification fails; update **all 12 seed files** listed in Non-Negotiable Constraints together, in the same commit as the model/schema change |
| Agent creates files not in the issue | PR review catches scope creep; request removal |
| Merge conflict on `develop` | Agent rebases feature branch, re-runs per-task verification |
| Missing service function referenced in issue | Fail the task, comment on issue — missing function is an untracked dependency |
| Multiple agents pick the same issue | First agent to move to In Progress owns it; others skip |
