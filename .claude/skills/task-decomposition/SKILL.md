---
name: task-decomposition
description: Use when the user asks to break up, break down, split, define, separate, or decompose backlog items, releases, GitHub issues, or requirement sources into implementation-ready GitHub tasks and parallel workstreams.
---

# DSP TRUE Connector task decomposition workflow

The Copilot-native canonical version of this workflow lives at `.github/skills/task-decomposition/SKILL.md`. Keep this Claude-side file aligned as a compatibility mirror rather than a separate source of truth.

This is the **canonical Stage 2 workflow** for turning functional-slice backlog items into implementation-ready GitHub issues. Keep the detailed decomposition logic here. Other repo docs should only point to this file instead of restating the full flow.

## Outcome

Turn a functional-slice backlog issue or an explicitly scoped requirement slice into GitHub issues that:

- use `.github/ISSUE_TEMPLATE/task.yml`, `.github/ISSUE_TEMPLATE/qa-task.yml`, and `.github/ISSUE_TEMPLATE/docs-task.yml`
- maximize independent parallel workstreams where the dependency graph allows it
- land in the **To Do** column on the **DSP TRUE Connector** project board
- preserve traceability back to the decomposed source
- preserve the parent slice tag across every child issue
- derive each child issue alias from that child issue's own GitHub number (`T107`, `B108`, ...)
- update the starting issue with a decomposition summary table after the child tasks are ready

## Trigger cues

Use this workflow when the user says things like:

- "break up this backlog item"
- "break down these requirements"
- "separate this slice into tasks"
- "define implementation issues from this doc"
- "split this issue into parallel streams"
- "decompose this into GitHub tasks"

## Read first

1. `AGENTS.md`
2. `doc/architecture.md`
3. `doc/development_procedure.md`
4. `.github/ISSUE_TEMPLATE/task.yml`
5. `.github/ISSUE_TEMPLATE/qa-task.yml`
6. `.github/ISSUE_TEMPLATE/docs-task.yml`
7. the source backlog issue or the relevant requirement source being decomposed

## Skills and workflows to use

### Primary workflow

- **task-decomposition** — this repo-local workflow skill is the primary Stage 2 playbook for DSP TRUE Connector.

### Supporting workflows

- **brainstorming** (`.agents/skills/brainstorming/`) — use before decomposition when the source is ambiguous, combines multiple initiatives, or needs clarification before tasks are created.
- **functional-slicing** — prefer this upstream workflow when the starting point is a broad requirement source that has not yet been split into backlog functional slices.

### Built-in Claude workers

- **Plan** — use for source analysis and decomposition prep
- **Explore** — use for issue/doc tracing without flooding the main context
- **general-purpose** — use when the workflow needs both synthesis and GitHub operations

### GitHub execution path

- Prefer a configured **GitHub MCP** for issue and project operations
- Fall back to `gh` CLI when MCP is unavailable

## Optional supporting context

Use the existing documentation when the source touches protocol-, security-, or architecture-sensitive work. It is optional support, not a mandatory read for every decomposition pass.

Examples include:

- module docs (`catalog/doc/`, `negotiation/doc/`, `connector/documentation/`, `data-transfer/doc/`, `tools/doc/`) for the affected protocol concern
- `negotiation/doc/model.md` and `.github/instructions/model-class-guidelines.instructions.md` when protocol message models change
- `.github/skills/dsp-compliance-review/SKILL.md` when the slice is protocol-facing
- `doc/security.md`, `doc/s3_configuration.md`, `doc/identity_hub.md` for the respective cross-cutting concerns

## Source types

### Existing backlog issue

Prefer a functional-slice backlog issue created by `.github/ISSUE_TEMPLATE/functional-slice.yml`. Preserve its number in every child issue's traceability section.

### Requirement source

If the source is only a document section, spec reference, or feature request and there is no parent backlog issue yet, prefer running `functional-slicing` first. If the user explicitly wants direct decomposition, first create or identify a parent tracking issue for that requirement slice. Then decompose under that parent so downstream issues still have a traceable source.

## Issue alias and slice tag conventions

Every decomposition pass must preserve the parent slice tag and derive child aliases from the created child issue numbers.

- Read the slice tag from the source slice issue and reuse it unchanged on every child issue.
- Use `T<number>` for child issues unless the child issue's **Type** is `bug`; bug-classified child issues use `B<number>`.
- The slice tag is not restricted to any specific prefix; it can be any stable short tag such as `C1`, `B1`, `X1`, or another moderator/tag chosen for that slice set.
- Recommended title formats:
  - implementation: `[TASK][X1][T107] concise task title`
  - bug task: `[TASK][X1][B108] concise bugfix title`
  - slice-level QA: `[QA][X1][T109] concise QA title`
  - slice-level docs: `[DOCS][X1][T110] concise docs title`
- Required GitHub label/tag on every child issue: `slice:X1` (replace `X1` with the chosen slice tag)
- Record both the shared slice tag and the issue-derived alias in the child issue body under **Source & Parent Traceability**.
- Because the GitHub issue number is only known after creation, create the issue, capture its number, then immediately update the title/body if the creation path could not set the final alias-bearing title in one step.

## Decomposition rules

1. **Decompose one coherent source slice at a time.** If the source spans multiple releases, split by release first.
2. **Prefer many small issues over a few large ones.** If a candidate issue would likely touch more than 5 files or bundle multiple verbs, split it. This also keeps tasks within the 8–16 hour estimation ceiling from `doc/development_procedure.md` — a task estimated above 16 hours must be split.
3. **Maximize parallelism.** Create separate streams when work can proceed independently, such as:
   - catalog vs negotiation vs data-transfer module work
   - protocol logic vs management API vs docs
   - infrastructure (CI, Docker, Terraform) vs feature delivery
   - parent scaffolding (shared `tools` change) vs sibling leaf tasks
4. **Make dependencies explicit.** Use `Depends on`, `Blocked by`, and `Relates to` consistently. Avoid circular graphs.
5. **Keep each issue agent-sized.** A task should be completable in one focused agent session.
6. **Write downstream-ready prompts.** Every child issue must contain enough context for implementation without requiring guesswork.
7. **Carry the slice tag through every child.** Every child issue must keep the parent slice tag in its title, issue body, and GitHub label/tag.
8. **Do not invent a separate ticket counter.** The only valid child alias is the one derived from that child issue's own GitHub number.

## Three required task families per slice

Every functional-slice decomposition must produce **three task families** as children:

1. **Implementation tasks** — one or more, created with `.github/ISSUE_TEMPLATE/task.yml`. These carry the product code work and self-run their own Verification Checklist (per-task QA) before opening their PRs.
2. **Exactly one slice-level QA task** — created with `.github/ISSUE_TEMPLATE/qa-task.yml`. Verifies the slice end-to-end across what all implementation tasks produced. Its Verification Checklist *is* the work. The decomposition should prefer durable JUnit/Testcontainers integration coverage over one-off manual execution when practical, and must include a TCK compliance run when the slice touches protocol-facing behavior (see `doc/tck/tck_compliancy.md`).
3. **Exactly one slice-level documentation task** — created with `.github/ISSUE_TEMPLATE/docs-task.yml`. Records what was implemented, updates the named authoritative docs (`doc/architecture.md`, module docs, `doc/README.md`, `CHANGELOG.md`), registers every API/protocol-message change in its authoritative doc and the Postman collection, and captures reusable learnings.

Dependency shape — required, no exceptions:

- QA task: `Depends on: #` every sibling implementation task.
- Docs task: `Depends on: #` every sibling implementation task **and** the QA task, so documentation reflects verified state.

The rule applies regardless of implementation task count — a slice decomposed into a single impl task still gets its own QA and docs children. They may be minimal, but they must exist so the work flows through the `Ready → In Progress → In Review → Done` lifecycle and is reviewed explicitly.

## Template routing

Pick the template per task family:

| Task family | Template | Type dropdown default |
|---|---|---|
| Implementation | `.github/ISSUE_TEMPLATE/task.yml` | `feature` / `enhancement` / `refactor` / etc. |
| Slice-level QA | `.github/ISSUE_TEMPLATE/qa-task.yml` | `test` |
| Slice-level documentation | `.github/ISSUE_TEMPLATE/docs-task.yml` | `docs` |

Do not place slice-level QA content inside `task.yml`, and do not merge QA and documentation into one issue.

## Required issue content

Each task family populates its own template.

Every created child issue, regardless of family, must also include:

- the shared slice tag in the title, **Source & Parent Traceability**, and `slice:<tag>` GitHub label/tag
- an issue alias (`T###` or `B###`) derived from that child issue's own GitHub number

### Implementation tasks — `.github/ISSUE_TEMPLATE/task.yml`

- **Context**
- **Source & Parent Traceability**
- **Implementation Prompt**
- **Dependencies**
- **Delivery Stream & Parallelization**
- **DSP Protocol / TCK Impact** when relevant
- **Security & Quality Requirements** when relevant
- **Type / Priority / Milestone**
- **AI Model**
- **Agent Mode**
- **Agent Instructions**
- **Verification Checklist** — covers only this task's own scope (per-task QA)

### Slice-level QA task — `.github/ISSUE_TEMPLATE/qa-task.yml`

- **Context** — slice link, functional outcome under test
- **Source & Parent Traceability** — parent slice + every sibling implementation task gated
- **Slice-level QA scope** — end-to-end flows derived from the slice's Functional Outcome and In-Scope Outcomes, cross-task integration points, protocol state transitions, data/state transitions
- **Automation expectation** — the QA task should create or extend durable JUnit/Testcontainers integration coverage when practical; protocol-facing slices must include a TCK compliance run; if automation is not practical, the issue must state the reason explicitly
- **Dependencies** — `Depends on: #` every sibling implementation task
- **Delivery Stream & Parallelization**
- **Type / Priority / Milestone** — Type `test`
- **AI Model / Agent Mode / Agent Instructions** — must state that this task's implementation is *running the Verification Checklist*, adding durable automated QA coverage where the task explicitly calls for it, and otherwise not writing product code
- **Verification Checklist** — deterministic slice-level end-to-end pass/fail items; `[manual]` prefix where automation is not possible (e.g. Postman collection walkthroughs); prefer existing repo commands (`mvn clean verify`, TCK profile run, SpotBugs scan); include explicit items for adding/updating durable integration coverage and running that targeted suite whenever practical for the slice

### Slice-level documentation task — `.github/ISSUE_TEMPLATE/docs-task.yml`

- **Context** — slice link, what was implemented across the slice
- **Source & Parent Traceability** — parent slice + sibling impl tasks + the QA task
- **Documentation scope** — authoritative doc files and sections to update (`doc/architecture.md`, the affected module docs under `catalog/doc/`, `negotiation/doc/`, `connector/documentation/`, `data-transfer/doc/`, `tools/doc/`, the `doc/README.md` index, `CHANGELOG.md`)
- **API changes register** — one row per protocol endpoint, message type, or management API that was added or changed during this slice, naming the authoritative doc and section that receives it and whether the Postman collection (`True_connector_DSP.postman_collection.json`) needs updating
- **What was learned** — reusable knowledge worth a new `doc/` guide entry or an ADR under `doc/decisions/`
- **Dependencies** — `Depends on: #` every sibling implementation task and the QA task
- **Delivery Stream & Parallelization**
- **Type / Priority / Milestone** — Type `docs`
- **AI Model / Agent Mode / Agent Instructions** — must state that this task is doc-only (no product code) unless a doc-only patch is insufficient
- **Verification Checklist** — deterministic: named doc sections updated, every API change row appears in its authoritative doc, `CHANGELOG.md` updated per DoD, every learning entry lands as a `doc/` guide or an ADR under `doc/decisions/`

## Stream and dependency heuristics

When splitting work:

- create a **foundation** task only when multiple sibling tasks truly depend on it (typically a shared change in `tools` or a model change consumed by several modules)
- keep **leaf tasks** independent so multiple agents can run at once
- put shared prerequisites in one task and reference them with `Depends on`
- use the **Delivery Stream & Parallelization** section to say:
  - the stream name
  - which sibling issues can run in parallel
  - why the task is safe to execute independently
  - what must finish first when it is not independent

## Starting issue summary update

After the child tasks are ready, update the starting issue with a summary comment that includes a markdown table.

The table must include one row per child task, across all three families:

- **Task** — child issue number and title
- **Alias** — `T###` / `B###` derived from the child issue number
- **Slice** — shared slice tag such as `C1`, `B1`, or `X1`
- **Family** — `impl` / `QA` / `docs`
- **Dependencies** — `Depends on` / `Blocked by` summary, or `None`
- **Model** — the selected AI model from the issue template
- **Parallel execution** — which sibling tasks can run in parallel, or `None`

The QA row must list every implementation task in its dependencies. The docs row must list every implementation task **and** the QA task in its dependencies. Both must appear explicitly; do not collapse them.

Use this table to give the human reviewer a one-glance view of the decomposition result. Keep the entries concise but explicit enough that execution order, task family coverage, and parallel workstreams are obvious.

## GitHub creation workflow

1. Read the source item and the required repo docs.
2. Use the supporting workflows above only when they improve clarity; keep this workflow as the primary source of truth.
3. Draft the dependency graph before creating issues.
4. Read the parent slice tag from the source issue and plan the final child titles around it.
5. Create child issues with content that matches the appropriate issue template.
6. Capture each created child issue number, derive its alias (`T###` or `B###`), then update the child title/body to include both the shared slice tag and the issue-derived alias.
7. Apply the matching `slice:<tag>` GitHub label/tag to every child issue.
8. Add dependency references between the created issues.
9. Place each child issue in the **To Do** column on the **DSP TRUE Connector** project board.
10. Mark the source item as decomposed:
   - preferred: add a `decomposed` label
   - always: post a summary comment that lists the created issue links and includes a markdown table with task numbers, aliases, slice tags, dependencies, models, and parallel execution notes

If the project board move cannot be automated in the current environment, create the issues first and leave an explicit follow-up note identifying which issues still need to be moved to **To Do**.

## Quality checklist before finishing

- every issue has a single clear action and bounded scope
- every issue includes explicit source traceability
- every child issue carries the parent slice tag in the title, issue body, and `slice:<tag>` label/tag
- every child issue alias matches that child issue's own GitHub number (`T###` or `B###`)
- every issue is placed in the right milestone
- dependencies form a DAG
- at least one parallel stream exists when the source naturally allows it
- verification items are deterministic
- **exactly one slice-level QA task exists** for this slice using `qa-task.yml`
- **exactly one slice-level documentation task exists** for this slice using `docs-task.yml`
- the QA task lists `Depends on: #` for every sibling implementation task
- protocol-facing slices include a TCK compliance run in the QA task; slices that skip durable automated coverage state an explicit rationale
- the documentation task lists `Depends on: #` for every sibling implementation task and for the QA task
- the starting issue was updated with the required decomposition summary table, with all three families (`impl`, `QA`, `docs`) represented
- the source item is labeled/commented as decomposed

## Do not do this

- do not restate this workflow in `AGENTS.md` or `README.md`
- do not create oversized issues just to keep the count small
- do not leave child issues without dependency or stream notes
- do not decompose requirement sources straight into orphaned child issues without a traceable parent/source record
- do not skip the slice-level QA task or slice-level documentation task — the rule applies even when the slice has only one implementation task
- do not merge QA and documentation into a single child issue
- do not place slice-level QA content inside `task.yml` or slice-level documentation content inside an implementation task's body
- do not invent or preserve a separate ticket numbering scheme that differs from the created child issue numbers
