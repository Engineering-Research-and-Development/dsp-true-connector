---
name: slice-implementation
description: Use when the user asks to execute a fully decomposed functional slice as one coordinated branch and one PR while respecting child-task dependencies, slice QA, and slice docs.
---

# DSP TRUE Connector slice implementation workflow

The Copilot-native canonical version of this workflow lives at `.github/skills/slice-implementation/SKILL.md`. Keep this Claude-side file aligned as a compatibility mirror rather than a separate source of truth.

This is the **canonical slice-level implementation workflow** for delivering one fully decomposed functional slice through **one coordinated slice branch and one PR**. Keep the slice-intake, dependency scheduling, batch execution, and slice-PR handoff logic here. Other repo docs should point to this file instead of restating the full flow.

This workflow is **additive**. The existing `.claude/skills/task-implementation/SKILL.md` remains the canonical **one selected issue -> one branch -> one PR** path. Use this skill only when the delivery unit is the **entire remaining slice**, not one child task.

## Outcome

Start from a decomposed parent functional slice plus its child tasks and end with one of these outcomes:

- the slice is validated as eligible for slice-PR mode, the parent slice and all included open child tasks are claimed, implementation tasks run in dependency order, slice QA and slice docs complete, one PR opens against `develop`, and that PR references the parent slice plus every included child task
- or the slice is stopped cleanly on ambiguity, ownership conflict, inconsistent metadata, or failed verification, with the blocking issue(s) documented and no partial child PRs created

## Trigger cues

Use this workflow when the user says things like:

- "implement this whole slice in one PR"
- "ship all tasks for slice X together"
- "execute the decomposed slice as one branch"
- "batch the child tasks under one PR"
- "run this functional slice end-to-end"
- "deliver the remaining slice tasks together"

## Read first

1. `AGENTS.md`
2. `doc/architecture.md`
3. `doc/development_procedure.md`
4. `.claude/skills/task-implementation/SKILL.md`
5. `.github/ISSUE_TEMPLATE/functional-slice.yml`
6. `.github/ISSUE_TEMPLATE/task.yml`
7. `.github/ISSUE_TEMPLATE/qa-task.yml`
8. `.github/ISSUE_TEMPLATE/docs-task.yml`
9. `.github/workflows/project-automation.yml`
10. the selected parent slice issue
11. every child issue that carries the same slice tag
12. the directly relevant module docs and guides from `doc/`

For protocol-, security-, or model-sensitive work, load the relevant docs before editing. At minimum, include the affected module's docs and `doc/tck/tck_compliancy.md` for protocol-facing slices.

## Skills and workflows to use

### Primary workflow

- **slice-implementation** — this repo-local workflow skill is the primary slice-level playbook for shipping one decomposed functional slice in one PR.

### Canonical child-task execution rules

- **task-implementation** — keep using this as the canonical source of truth for:
  - ambiguity handling
  - task-family execution rules
  - per-task verification and the 3-retry `needs-human` path
  - task-local vs slice-level documentation boundaries
  - PR self-review after the slice PR exists

This skill defines its own intake, claim, scheduling, branch, and PR mechanics. Do **not** treat `task-implementation` as a hidden "no-PR mode."

### Supporting workflows

- **brainstorming** (`.agents/skills/brainstorming/`) — use before editing when the slice leaves material ambiguity unresolved
- **playwright-cli** — use only for browser-verifiable checks when applicable
- built-in `/code-review` — use after the slice PR is open

### Built-in Claude workers

- **Plan** — use for slice digestion, scheduling, and orchestration framing
- **Explore** — use for cross-file tracing or GitHub issue tracing
- **general-purpose** — use for bounded sub-problems that need synthesis plus GitHub operations

### GitHub execution path

- Prefer **GitHub MCP** for issue, project-board, label, assignment, comment, and PR operations
- Fall back to `gh` CLI when MCP support is missing or incomplete

## Slice PR mode eligibility

Use this workflow only when all of these are true:

1. the delivery unit is a **parent functional slice**, not a single child issue
2. the user intends to land the **entire remaining slice** in one PR
3. every open child issue of that slice is included in this run
4. there is exactly one slice-level QA issue and exactly one slice-level docs issue for the slice
5. no open child issue for that slice remains in **Backlog** or **To Do**

If the user wants to ship only a subset of child tasks, use `.claude/skills/task-implementation/SKILL.md` instead.

## Slice intake and membership resolution

Start from either:

- the parent slice issue
- or any child issue that already belongs to the target slice

Resolve the full slice using these rules:

1. **Canonical membership key**: the shared `slice:<tag>` GitHub label
2. **Required cross-checks**:
   - the same slice tag appears in the issue title, such as `[TASK][X1][T107] ...`
   - the same slice tag appears in **Source & Parent Traceability**
   - the child issue points back to the same parent slice/root issue
3. **Audit-only signals**:
   - the same decomposition batch
   - the parent slice summary comment created during decomposition

If the `slice:<tag>` label, title tag, and traceability disagree in a way that makes membership ambiguous, hard-stop and fix the metadata before execution.

## Child family classification

Classify each child issue in this order:

1. title prefix:
   - `[TASK]` -> implementation
   - `[QA]` -> slice-level QA
   - `[DOCS]` -> slice-level docs
2. fallback by template-specific sections:
   - `Implementation Prompt` -> implementation
   - `Slice-level QA scope` -> QA
   - `Documentation scope` -> docs

Treat a classification mismatch as blocking metadata drift. Stop and repair it before execution.

## Child issue status model

For slice scheduling, classify child issues like this:

- **Done** — already satisfied before this slice run
- **Ready** — startable when its hard dependencies are satisfied
- **In Progress** / **In Review** — active but unresolved; if owned by another branch, PR, or agent, treat as an ownership conflict
- **To Do** / **Backlog** — not startable for slice PR mode
- **Blocked** — stop and surface the blocker explicitly

Inside an active slice run, a dependency is considered satisfied when the referenced child issue is either:

1. already in **Done** before the slice run starts
2. or completed, verified, and integrated into the active slice branch during this run

That is the key difference from standalone task execution: inside slice PR mode, child dependencies become **execution order within one slice branch**, not a requirement that sibling issues already be **Done** on the board before work begins.

## Hard dependency parsing

Parse only the exact dependency prefixes from the issue templates:

- `Depends on:`
- `Blocked by:`
- `Relates to:`

Rules:

1. `Depends on:` and `Blocked by:` are hard gates
2. `Relates to:` is context only
3. Only **Done** or **completed-and-integrated in this slice session** satisfies a hard dependency
4. Do not infer dependencies from prose outside the named prefixes

## Slice scheduling and execution order

Build the execution graph like this:

1. gather all implementation child issues for the slice
2. build a DAG from their hard dependencies
3. compute the current runnable batch as the implementation child issues whose hard dependencies are already satisfied
4. after a runnable batch passes verification and is integrated into the slice branch, recompute the next implementation batch
5. the slice-level QA issue becomes eligible only when **all implementation child issues are satisfied**
6. the slice-level docs issue becomes eligible only when **all implementation child issues plus the slice-level QA issue are satisfied**

If the implementation-task dependency graph is cyclic, stop and repair the decomposition before continuing.

## Claiming the slice

Before editing:

1. confirm the parent slice and all included open child issues belong to the same slice
2. confirm there is no ownership conflict
3. assign the parent slice and every included open child issue to the active user
4. move the parent slice to **In Progress**
5. move every included open child issue in **Ready** to **In Progress**

If any included child issue is already **In Progress** or **In Review** under another active branch, PR, or agent, stop and surface the ownership conflict instead of trying to absorb it.

## Worktree and branch strategy

Use an isolated worktree when the current checkout has unrelated in-progress work or when parallel child execution would otherwise disturb the current branch.

### Final slice integration branch

Create the final slice branch from a **synchronized** `develop` using:

- `feature/{parent-slice-number}-slice-{short-name}`

This branch is the only branch that opens the final PR.

### Temporary child implementation branches

For implementation tasks that run in parallel, create one temporary worktree/branch per child issue from the current slice integration branch using a clear local naming scheme such as:

- `feature/{parent-slice-number}-slice-{short-name}-t{child-number}`

Rules:

1. do not push multiple agents into the same branch at once
2. do not open PRs from temporary child branches
3. integrate verified child branches back into the final slice branch before the next dependent batch starts

## Executing implementation child tasks

For each runnable implementation batch:

1. create one temporary worktree/branch per child issue in the batch
2. execute that child issue using the canonical rules from `.claude/skills/task-implementation/SKILL.md`:
   - read the child issue and relevant context
   - resolve ambiguity before editing
   - implement only the child issue scope
   - run the child issue's verification checklist (`mvn clean verify` with Docker running, Checkstyle, targeted integration tests)
   - apply the 3 fix-and-retry limit
   - respect the task-local vs slice-level documentation boundary
3. after the child issue passes verification:
   - integrate it into the final slice branch
   - record what was completed and which checks passed
   - keep the child issue in **In Progress** until the slice PR opens
4. after integration, recompute the next runnable implementation batch

## Executing slice-level QA and docs child tasks

Run these sequentially on the final slice branch after implementation batches are integrated.

### Slice-level QA

Execute the QA child issue using the canonical QA rules from `.claude/skills/task-implementation/SKILL.md`:

- the Verification Checklist is the work
- prefer existing repo commands: `mvn clean verify`, targeted integration tests, SpotBugs scan, and the TCK compliance run for protocol-facing slices
- do not write product code unless the QA issue explicitly scopes verification artifacts

On pass, keep the QA issue in **In Progress** until the slice PR opens.

### Slice-level docs

Execute the docs child issue using the canonical docs rules from `.claude/skills/task-implementation/SKILL.md`:

- update only the named authoritative docs (`doc/architecture.md`, module docs, `doc/README.md`, `CHANGELOG.md`)
- register every API changes register row in its authoritative doc, with Postman collection updates where required
- capture reusable learnings as `doc/` guides or ADRs under `doc/decisions/` when appropriate
- do not write product code unless the docs issue explicitly justifies it

On pass, keep the docs issue in **In Progress** until the slice PR opens.

## Failure handling inside a slice run

If any child issue fails its verification path after 3 fix-and-retry cycles:

1. add the `needs-human` label to that child issue
2. comment what failed and what was attempted
3. move that child issue back to **Ready**
4. stop the slice run without opening a PR
5. move any other still-open child issues claimed only for this slice run back to their pre-PR state
6. move the parent slice back to its prior board state or comment clearly if the board state could not be restored automatically

If a pre-existing bug blocks QA or docs, stop and comment on the blocking child issue instead of widening scope.

## Slice PR handoff

After all included open child issues are completed and integrated into the final slice branch:

1. commit the final slice branch changes
2. push the final slice branch
3. open **one PR** against `develop`
4. move the parent slice and every included open child issue to **In Review**

### Required PR body convention

Use one closing line per issue in the PR body. Include the parent slice and every open child issue shipped by this PR.

```md
## Slice completion
Parent slice:
- Closes #184

Child tasks:
- Closes #211
- Closes #212
- Closes #213
- Closes #214
```

The PR body is the authoritative closure list. Do not rely on commit messages or on the branch name to imply which child issues were shipped.

## PR self-review policy

After the slice PR is open, run the Claude Code built-in `/code-review` skill at **high** effort against the full slice branch diff using the same self-review policy documented in `.claude/skills/task-implementation/SKILL.md`.

Apply fixes on the final slice branch, re-run the relevant checks, and keep the batch of referenced issues in **In Review** unless a blocking issue requires a `needs-human` handoff.

## Post-merge verification and fallback

The repository merge automation (`.github/workflows/project-automation.yml`) is expected to:

1. parse **all** PR-body closing references first
2. fall back to the branch number only when the PR body has no closing references
3. move every referenced issue to **Done**
4. close every referenced issue if it is still open

If the workflow fails or is unavailable, use this fallback after merge:

1. verify which referenced issues GitHub already closed
2. move any referenced issue not yet in **Done** to **Done** using GitHub MCP or `gh`
3. close any referenced issue still open
4. post a summary comment on the parent slice with the merged PR link and any manual board moves performed

## Do not do this

- do not use slice PR mode for only part of a slice
- do not start if any open child issue for that slice is still in **Backlog** or **To Do**
- do not absorb an already active child issue owned by another branch, PR, or agent
- do not let multiple agents push to the same branch at once
- do not open child PRs from temporary implementation branches
- do not treat `Relates to:` as a hard dependency
- do not infer child membership from branch names alone
- do not skip the per-child verification rules inherited from `.claude/skills/task-implementation/SKILL.md`
