---
name: task-implementation
description: Use when the user asks to start implementing a Ready GitHub issue, choose the next task, execute the implementation, run per-task verification, and prepare the PR handoff. Also covers slice-level QA tasks (`qa-task.yml`) and slice-level documentation tasks (`docs-task.yml`) picked up from Ready.
---

# DSP TRUE Connector task implementation workflow

This is the **canonical implementation workflow** for Copilot-first execution of Ready-column issues on the GitHub Project board. Keep the detailed issue-selection, implementation, per-task verification, and PR-handoff procedure here. Other active repo docs should point to this file instead of restating the full flow.

This skill handles all three task families produced by task decomposition:

- **Implementation tasks** created from `.github/ISSUE_TEMPLATE/task.yml` — produce product code per the Implementation Prompt, self-run the per-task verification loop before PR.
- **Slice-level QA tasks** created from `.github/ISSUE_TEMPLATE/qa-task.yml` — the Verification Checklist *is* the work; the agent runs the end-to-end checks across the slice and does not write product code.
- **Slice-level documentation tasks** created from `.github/ISSUE_TEMPLATE/docs-task.yml` — update the named authoritative docs, register every API/protocol-message change in its authoritative doc, capture reusable learnings as `doc/` guides or ADRs under `doc/decisions/`.

## Scope and reuse boundary

This skill is the canonical **standalone Ready-issue workflow**: one selected issue, one branch, and one PR. Slice-level orchestration workflows may reuse the child-execution rules below, but they must define their own slice intake, issue-batch claiming, branch strategy, and PR mechanics explicitly instead of treating this skill as a hidden no-PR mode.

## Outcome

Start from the **Ready** column on the **DSP TRUE Connector** project board and end with one of these outcomes:

- an unblocked issue is selected by the user, assigned, moved to **In Progress**, implemented, verified, self-reviewed and fixed, documented as needed, and moved to **In Review** with a PR open
- or the issue fails verification after 3 fix-and-retry cycles, is labeled `needs-human`, moved back to **Ready**, and documented with a failure comment

## Trigger cues

Use this workflow when the user says things like:

- "implement the next Ready issue"
- "show me the Ready tasks and let me pick one"
- "start implementing a GitHub issue"
- "take the next task from the board"
- "move a Ready issue into progress and build it"
- "implement this approved task"

## Read first

1. `AGENTS.md`
2. `doc/architecture.md`
3. `doc/development_procedure.md`
4. the selected GitHub issue, including:
   - `Dependencies`
   - `Implementation Prompt`
   - `Agent Instructions`
   - `Verification Checklist`
5. the directly relevant module docs and guides from `doc/`

For protocol-, security-, or model-sensitive work, load the relevant docs before editing. At minimum, include the affected module's docs (`catalog/doc/`, `negotiation/doc/`, `connector/documentation/`, `data-transfer/doc/`, `tools/doc/`), and `negotiation/doc/model.md` plus `.github/instructions/model-class-guidelines.instructions.md` for protocol message model changes.

## Skill availability and prerequisites

- The Copilot-native `task-implementation` workflow lives under `.github/skills/` and is the primary implementation playbook for this repository.
- Prefer a configured **GitHub MCP** for issue, project-board, label, assignment, comment, and PR operations.
- Fall back to `gh` CLI when GitHub MCP is unavailable for a required operation.
- Docker must be running: integration tests use Testcontainers and run in the `mvn clean verify` phase.
- Use the repo-local `playwright-cli` skill only for browser-verifiable checks when applicable (e.g. verifying behavior through the separate GUI frontend).
- Do not block implementation on optional helpers; if a helper is unavailable, fall back to the next approved path instead of inventing new tooling.

## Skills and workflows to use

### Primary workflow

- **task-implementation** — this repo-local workflow skill is the primary Ready-column execution playbook for DSP TRUE Connector.

### Supporting workflows

- **brainstorming** (`.agents/skills/brainstorming/`) — use before editing when the selected issue prompt is ambiguous, conflicts with itself, or leaves material behavioral choices unresolved.

### Supporting repository skills

- **java-development** — use for Java source changes, Javadoc, Checkstyle expectations, and general code-quality guidance.
- **junit-5-tests** — use when adding or updating unit tests, Mockito-based tests, or JUnit 5 structure.
- **model-class-guidelines** — use for protocol model, builder, validation, Jackson, or Spring Data model work.
- **dsp-foundations** — use as the baseline DSP rule reference before changing protocol-facing behavior.
- **dsp-catalog** / **dsp-contract-negotiation** / **dsp-transfer-process** — use the matching protocol skill for scoped protocol work.
- **dsp-compliance-review** — use when validating protocol semantics, state transitions, or TCK-sensitive changes.
- **github-actions-ci-cd-best-practices** — use when the task touches `.github/workflows/` or related automation.

### Built-in agent workers

- **Plan** — use for issue digestion, execution framing, and the in-memory implementation plan before edits begin.
- **Explore** — use for repo tracing when the issue spans multiple unfamiliar files or systems.
- **general-purpose** — use when a bounded sub-problem needs both synthesis and GitHub operations in one focused thread.

### GitHub execution path

- Prefer **GitHub MCP** for issue/project/PR operations.
- Fall back to `gh` CLI when MCP support is missing or incomplete.

## Ready-issue intake

1. Fetch candidate issues from the **Ready** column on the **DSP TRUE Connector** project board.
2. Read each issue's:
   - title and number
   - priority
   - AI model
   - agent mode
   - `Dependencies`
   - `Implementation Prompt`
   - `Verification Checklist`
3. Parse dependency lines using the exact prefixes from the issue template:
   - `Depends on:`
   - `Blocked by:`
   - `Relates to:`

## Dependency classification

Classify each Ready issue into one of these groups before asking the user to choose:

- **Selectable** — in Ready and all `Depends on` / `Blocked by` issues are already in **Done**
- **Blocked** — in Ready but at least one dependency or blocker is unresolved
- **Related only** — no blocking dependency, but `Relates to` references should be surfaced for context

When multiple issues are selectable:

1. recommend the highest-priority unblocked issue first
2. break ties with the lowest issue number unless the issue prompt or board metadata gives a clearer ordering signal

Blocked issues should remain visible to the user, but they are not startable.

## User choice flow

1. Present the user with:
   - the recommended selectable issue first
   - other selectable issues
   - blocked issues labeled with the unresolved dependency reason
2. Ask the user which selectable issue they want to start.
3. If the user selects a blocked issue:
   - explain the blocker clearly
   - do not assign it
   - do not move it to **In Progress**
   - do not create a branch for it

## Claiming the selected issue

Once the user chooses a selectable issue:

1. assign the issue to the active user
2. move the issue to **In Progress**
3. create and switch to `feature/{issue-number}-{short-name}` from a **synchronized** `develop` branch (pull latest first, per `doc/development_procedure.md`)

If another agent or user already claimed or moved the issue, stop and surface the ownership conflict instead of proceeding.

## Pre-edit execution framing

Before changing files:

1. read `AGENTS.md`
2. read the issue's `Agent Instructions`
3. read the issue's `Implementation Prompt`
4. read the issue's `Verification Checklist`
5. load the directly relevant module docs and `doc/` guides
6. form a brief in-memory plan with **Plan**

## Ambiguity handling

Before editing, check whether the issue leaves material uncertainty about:

- feature scope
- behavior
- protocol compliance impact
- permanent repository structure
- defaults, limits, or destructive actions

When ambiguity exists:

1. use **brainstorming** first when it helps sharpen the choices
2. ask the user focused clarification questions
3. do not start editing until the ambiguity is resolved well enough to avoid guesswork

## Task-family execution rules

Before executing, identify which template the selected issue was created from and route accordingly. The template is identifiable from the issue body's top markdown block, the title prefix (`[TASK]` / `[QA]` / `[DOCS]`), or the `Type` dropdown.

### Implementation task — `task.yml`

Produce product code per the `Implementation Prompt`. Follow the Java conventions (`.github/instructions/java.instructions.md`), the model-class builder/validation pattern, module boundaries, and Javadoc requirements from `AGENTS.md`. Run the **per-task verification loop** below before handing off to the PR step. Task-local documentation (Javadoc on new public/protected methods, module doc notes scoped to this task) happens here.

### Slice-level QA task — `qa-task.yml`

The `Verification Checklist` *is* the work. Execute the checks end-to-end across the slice:

1. read the parent slice issue and every sibling implementation task listed in `Source & Parent Traceability` so you understand what landed
2. run the listed deterministic checks using existing repo commands: `mvn clean verify` (Docker running), targeted integration tests, SpotBugs scan, and the TCK compliance run for protocol-facing slices (`doc/tck/tck_compliancy.md`)
3. mark `[manual]` items as manual verification targets (e.g. Postman collection walkthroughs against running provider/consumer instances) — do not attempt to automate them
4. do **not** write product code as part of this task. If verification surfaces a pre-existing bug, stop and comment on this QA issue describing the bug, then hand off — the fix belongs in a separate implementation task
5. on pass: proceed to the PR handoff with a comment that shows each check's outcome
6. on fail: apply the 3-retry rule below, but retries here mean re-running checks (and, if explicitly in scope, fixing the agent's own verification artifacts), not rewriting product code

### Slice-level documentation task — `docs-task.yml`

Update the named authoritative documents per the task's `Documentation scope`:

1. for each authoritative document listed, apply the described updates in the named sections (`doc/architecture.md`, the affected module docs, the `doc/README.md` index, `CHANGELOG.md`)
2. register every row in the **API changes register** in its named authoritative doc and section, and update the Postman collection note where the register requires it
3. for each **What was learned** entry, either land a new `doc/` guide or record an ADR under `doc/decisions/` only when the learning is a real architectural or workflow decision
4. do **not** write product code as part of this task unless a doc-only patch is insufficient (for example, a typo in a code snippet embedded in a doc); in that case stop and comment on the docs issue first
5. the `Verification Checklist` for a docs task is about doc-update diffs, API-change-register presence, and the CHANGELOG entry — run it before handing off to the PR step

## Implementation execution

When the issue is clear:

1. execute the implementation prompt within the issue scope
2. reuse existing repo patterns and helpers instead of creating parallel workflow systems — shared logic belongs in `tools`, protocol models follow the builder/validation pattern, integration tests extend the Testcontainers base class (`doc/test_containers_starting_guide.md`)
3. use **Explore** for cross-file tracing and **general-purpose** only for bounded sub-problems that benefit from a separate synthesis thread
4. follow the issue's specified AI model, the Java conventions, module boundaries, and security requirements

## Shared verification policy

Applies to every task family. For implementation tasks this runs against the task's own `Verification Checklist`; for QA and docs tasks this is the primary execution step already covered under **Task-family execution rules**, re-stated here as a single unified loop.

Map the issue's `Verification Checklist` into these buckets where applicable:

- build checks — `mvn clean verify` (Docker required; integration tests run in the verify phase)
- code quality checks — Checkstyle (Javadoc rules), SpotBugs / Find Security Bugs scan
- integration checks — targeted JUnit/Testcontainers integration tests
- protocol compliance checks — TCK profile run when the change is protocol-facing
- manual-only checks — Postman collection walkthroughs, multi-instance provider/consumer flows

Rules:

1. run only commands and checks that already exist in the repository
2. prefer durable JUnit/Testcontainers coverage for functional checks
3. mark or respect `[manual]` items as manual verification targets
4. after a failed check, fix the issue and re-run the relevant checks
5. maximum **3 fix-and-retry cycles** per issue
6. **full-suite gate**: before pushing to the remote branch, always run `mvn clean verify` (Docker required) as the final local check — targeted test runs (`-Dtest=...` or `-Dit.test=...`) are development aids only and do not substitute for this gate; a known pre-existing flaky test is not a reason to skip the full suite — run through it and note the known failure explicitly

If the issue still fails after 3 cycles:

1. add the `needs-human` label
2. comment which checks failed and what was attempted
3. move the issue back to **Ready**
4. stop without creating a PR

## Task-local vs slice-level documentation boundary

Applies to implementation tasks (`task.yml`) after the per-task verification loop passes. Slice-level architecture updates, API documentation, API-change records, and slice-level ADRs are **out of scope here** — they belong in the slice's dedicated documentation task (`docs-task.yml`). Do not preemptively update `doc/architecture.md` or module docs from an implementation task.

After the per-task verification loop passes:

1. verify Javadoc is present on all new/changed public and protected methods (Checkstyle enforces this)
2. verify the `CHANGELOG.md` entry when the task's own scope explicitly requires one (per DoD: "Changelog is updated (if applicable)")
3. update a module doc only when the implementation introduces a new durable pattern *and* the task's own `Implementation Prompt` explicitly scopes that update to this task
4. leave `doc/architecture.md` updates, ADRs, API-change records, and Postman collection updates for the slice's dedicated documentation task

## Pull request handoff

After implementation and verification are complete:

1. commit the changes, referencing the issue (`Closes #N`)
2. push the feature branch
3. open a PR against `develop` with:
   - summary bullets
   - verification results
   - files changed
4. wait for GitHub Actions to complete — see **GitHub Actions check after PR** below
5. move the issue to **In Review** only after all GA checks pass

Per `doc/development_procedure.md`, the PR will be **squash merged** to `develop` after review, and the feature branch deleted.

## GitHub Actions check after PR

After the PR is opened, GitHub Actions trigger automatically on the pushed branch. Wait for all checks to complete:

```bash
gh pr checks <PR-number> --watch
```

Treat every GA check outcome as part of the Shared verification policy, sharing the same **3-retry budget** already used by any local verification retries:

- **All checks pass** — proceed to the PR self-review step.
- **One or more checks fail** — this is a verification failure; enter the retry loop:
  1. Read the failing run log: `gh run view <run-id> --log-failed`
  2. Diagnose the root cause (compilation error, test failure, lint violation, Docker issue).
  3. Apply the fix, commit, and push to the feature branch — the push re-triggers GA automatically.
  4. Wait for the new run: `gh pr checks <PR-number> --watch`
  5. Each push-and-wait counts as one retry cycle against the shared 3-cycle budget.
- **After 3 total cycles** (combined local + GA) still failing:
  1. Add the `needs-human` label.
  2. Post a comment with the GA log excerpt and a summary of what was attempted.
  3. Move the issue back to **Ready**.
  4. Stop — do not complete the PR.

## PR self-review policy

After the PR is open, run a self-review of the branch diff using the built-in `/code-review` skill at **high** effort.

1. Classify every finding by severity.
2. Apply fixes immediately for blocking and important findings; apply cleanly scoped nit fixes.
3. Do **not** apply suggestion-level findings — surface them in the PR comment only. Suggestions may introduce scope creep or require a design decision.
4. Do **not** apply any finding that requires changes outside the issue scope (new requirements, architectural decisions, out-of-scope files). Document these as a note in the PR comment for the human reviewer instead.
5. If any fixes were applied:
   - Re-run the relevant verification checks (at minimum: `mvn clean verify`).
   - Commit with a message referencing the review (e.g. `fix: address code review findings on <module>`).
   - Push to the feature branch — the open PR picks up the new commits automatically.
6. Post a review summary comment on the PR listing findings, applied fixes, and surfaced suggestions.
7. If no blockers remain after fixes: leave the issue in **In Review**. A remaining blocker that cannot be fixed within scope → add `needs-human` label and note it in the PR comment.

## Do not do this

- do not auto-start a blocked issue
- do not skip the user choice step unless the user explicitly asked for automatic selection
- do not start editing while a material ambiguity is unresolved
- do not invent new build, test, or QA tooling
- do not create a PR after the 3-attempt failure path
- do not skip the code review step after PR creation
- do not move the issue to **In Review** while any required GitHub Actions checks are still failing
- do not restate this workflow in `AGENTS.md` or `README.md`
