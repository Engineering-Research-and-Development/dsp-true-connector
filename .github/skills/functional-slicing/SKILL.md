---
name: functional-slicing
description: Use when the user asks to split a requirement, backlog source, GitHub issue, DSP specification section, or roadmap item into parallel functional slices that land in Backlog and are decomposed later into implementation tasks.
---

# DSP TRUE Connector functional slicing workflow

This is the **canonical Stage 1 workflow** for turning requirement sources into functional-slice GitHub issues. Keep the detailed slicing logic here. Other repo docs should only point to this file instead of restating the full flow.

## Outcome

Turn a source requirement issue or a scoped requirement source into GitHub issues that:

- start by offering **exactly 3 viable slicing choices** for the user to pick from
- use `.github/ISSUE_TEMPLATE/functional-slice.yml`
- describe **functional segments**, not implementation tasks
- maximize independent parallel streams where the requirement allows it
- land in the **Backlog** column on the **DSP TRUE Connector** project board
- preserve traceability back to the source requirement
- assign a stable slice tag such as `C1`, `B1`, or `X1` to every created slice
- make that slice tag visible in the slice title, issue body, and GitHub label/tag
- collectively cover the full source requirement before finishing

## Trigger cues

Use this workflow when the user says things like:

- "create functional slices from this requirement"
- "split this DSP spec section into backlog slices"
- "break this issue into functional segments"
- "define parallel capability slices from these requirements"
- "separate this requirement into independent backlog issues"
- "prepare requirement slices before task decomposition"

## Read first

1. `AGENTS.md`
2. `doc/architecture.md`
3. `doc/development_procedure.md`
4. `.github/ISSUE_TEMPLATE/functional-slice.yml`
5. the source GitHub issue, DSP specification section, or roadmap item being sliced

## Skills and workflows to use

### Primary workflow

- **functional-slicing** — this repo-local workflow skill is the primary Stage 1 playbook for DSP TRUE Connector.

### Supporting workflows

- **brainstorming** (`.agents/skills/brainstorming/`) — use before slicing when the source is ambiguous, mixes multiple initiatives, or needs sharper capability boundaries before issues are created.

### Supporting repository skills

- **dsp-foundations** — use as the default DSP reference when the source touches DSP 2025-1 behavior generally or spans multiple DSP areas.
- **dsp-catalog** / **dsp-contract-negotiation** / **dsp-transfer-process** — use the protocol-specific skill when the requirement source is clearly scoped to one protocol area.
- **dsp-compliance-review** — use when the slicing boundary is driven by conformance, TCK expectations, or protocol-state validation concerns.
- **github-actions-ci-cd-best-practices** — use when the source requirement is primarily about GitHub Actions, automation, or deployment workflow behavior.

### Follow-on workflow

- **task-decomposition** — use later, in a separate session, to turn one functional-slice backlog issue into implementation-ready GitHub tasks in **To Do**.

### Built-in agent workers

- **Plan** — use for source analysis, slice framing, and the coverage matrix before issue creation.
- **Explore** — use for issue/doc tracing without flooding the main context.
- **general-purpose** — use when the workflow needs both synthesis and GitHub operations.

### GitHub execution path

- Prefer a configured **GitHub MCP** for issue and project operations.
- Fall back to `gh` CLI when MCP is unavailable.

## Optional supporting context

Use the existing documentation when the source touches protocol-, security-, or architecture-sensitive work. It is optional support, not a mandatory read for every slicing pass.

Examples include:

- `doc/glossary.md` for DSP domain terminology
- module docs (`catalog/doc/`, `negotiation/doc/`, `connector/documentation/`, `data-transfer/doc/`, `tools/doc/`) when the source affects a specific protocol concern
- `doc/security.md`, `doc/identity_hub.md`, `doc/verifiable_credentials.md` for security/identity-sensitive sources
- `.github/skills/dsp-compliance-review/SKILL.md` when the source affects protocol-facing behavior
- `doc/decisions/` ADRs when the source challenges an existing architectural decision

## Source types

### Existing GitHub issue

Use the issue as the parent/source item. Preserve its number in every created functional slice issue's traceability section.

### Requirement source without a tracking issue

If the source is a DSP specification section, a roadmap item, or a feature request that has no parent tracking issue yet, first create or identify a parent issue for that requirement. Then create the functional slice issues under that parent so downstream task decomposition keeps a traceable root.

## Slice tag convention

Every slicing batch must assign a stable slice tag to each created slice.

- Use any short, stable tag such as `C1`, `B1`, `X1`, or another moderator/tag that fits the slice set created from one source item or parent issue.
- Keep the chosen tag stable for the life of the slice. Later task decomposition must reuse it unchanged.
- Recommended title format: `[SLICE][X1] concise slice title`
- Required GitHub label/tag: `slice:X1` (replace `X1` with the chosen slice tag)
- Record the tag in the issue body under **Source & Parent Traceability** and reference it in **Later task-decomposition guidance**.
- If the issue-creation path cannot set the final title or label in one step, update the issue immediately after creation.

## Choice-first requirement

Before creating slice issues, always offer the user **exactly 3 slicing choices** for how the requirement set can be split.

Each choice must:

1. use a distinct slicing lens or boundary strategy
2. name the expected parallel streams
3. call out the main tradeoff or risk
4. make clear what kind of downstream decomposition it will enable

Presentation rules:

- label one option as **Recommended**
- keep all 3 options functionally valid, not strawmen
- if the user already suggested a direction, include it as one of the 3 options when possible
- do not create GitHub issues until the user selects an option, unless they explicitly delegate the choice to the agent
- if the user delegates, pick the **Recommended** option and state that choice before creating issues

## Functional slicing rules

1. **Slice one coherent source area at a time.** If the source spans multiple releases or protocol versions, split by release first.
2. **Slice by functional outcome, not by code layer.** Prefer protocol capabilities, dataspace participant flows, or domain segments over controller/service/repository splits.
3. **Maximize parallelism.** Separate streams when slices can be decomposed and delivered independently later. The module boundaries (`catalog`, `negotiation`, `data-transfer`, `tools`, `connector`) are natural stream candidates, but only when the functional boundary genuinely aligns with them.
4. **Keep slices decomposition-ready, not implementation-ready.** Do not write detailed file-level implementation prompts, model choices, or QA checklists here.
5. **State boundaries explicitly.** Every slice must say what is in scope, what is out of scope, and where it hands off to sibling slices.
6. **Use dependencies sparingly.** Only add `Depends on` or `Blocked by` when a slice truly cannot be decomposed or delivered independently.
7. **Avoid overlap unless it is intentional and named.** If two slices touch the same requirement area, explain the boundary in both issues.
8. **Require a coverage audit before finishing.** Do not stop once issues exist; verify that every source requirement item is mapped to at least one created slice.
9. **Seed downstream QA and documentation expectations.** Every slice, when decomposed, will always produce exactly one slice-level QA task (`.github/ISSUE_TEMPLATE/qa-task.yml`) and exactly one slice-level documentation task (`.github/ISSUE_TEMPLATE/docs-task.yml`) alongside its implementation tasks. The slice issue's **Later task-decomposition guidance** section must seed this by naming the expected end-to-end flows for QA (including whether a TCK compliance run is required for protocol-facing slices) and the authoritative docs the documentation task will touch (candidates: `doc/architecture.md`, module docs, `doc/README.md`, `CHANGELOG.md`). See `.github/skills/task-decomposition/SKILL.md` for the full dependency shape.
10. **Carry the slice tag everywhere.** Every created slice must include the same chosen slice tag in its title, Source & Parent Traceability section, and GitHub label/tag.

## Required issue content

Populate slice issues using the sections in `.github/ISSUE_TEMPLATE/functional-slice.yml`:

- **Slice tag** — present in the title, **Source & Parent Traceability**, and the GitHub `slice:<tag>` label/tag
- **Context**
- **Source & Parent Traceability**
- **Functional Outcome**
- **In-Scope Outcomes**
- **Out-of-Scope Boundaries**
- **Parallelization & Related Slices**
- **Dependencies**
- **Later task-decomposition guidance**
- **Requirement Coverage**
- **Type / Priority / Milestone**

## Parallelization heuristics

When splitting work:

- separate slices by coherent protocol capability or participant workflow stage (e.g. catalog publication vs negotiation policy handling vs transfer channel support)
- prefer slices that can later be decomposed without reopening sibling issue scope
- create a shared foundation slice only when multiple sibling slices truly depend on it (e.g. a shared model change in `tools`)
- use the **Parallelization & Related Slices** section to record:
  - the stream name
  - which sibling slice issues can progress independently
  - where this slice hands off to a sibling
  - why the split is safe and functional rather than technical-only

## Coverage audit

Before creating issues:

1. extract a coverage checklist from the source requirement
2. normalize it into concrete requirement items, flows, or acceptance points
3. draft a source-to-slice mapping and look for gaps or accidental overlap

After creating issues:

1. map every requirement item to one or more created slice issues
2. confirm every created slice has at least one explicit source reference
3. flag uncovered items and fix the slice set before finishing
4. include a final summary comment that shows the source-to-slice coverage result

## GitHub creation workflow

1. Read the source item and the required repo docs.
2. Use the supporting workflows above only when they improve clarity; keep this workflow as the primary source of truth.
3. Draft **3 slicing choices** and present them to the user before issue creation.
4. After the user selects an option, draft the slice map and coverage matrix for that option, including planned slice tags (`C1`, `B1`, `X1`, ...).
5. Create or identify a parent tracking issue first when the source has no tracking issue yet.
6. Create the functional slice issues with content that matches `.github/ISSUE_TEMPLATE/functional-slice.yml` and titles like `[SLICE][X1] ...`.
7. Apply the matching `slice:<tag>` GitHub label/tag to each created slice and record the same tag in the issue body.
8. Add dependency references between created slice issues when they are truly needed.
9. Place each created slice issue in the **Backlog** column on the **DSP TRUE Connector** project board.
10. Mark the source item as sliced:
   - preferred: add a `sliced` label
   - always: post a summary comment that lists the created slice issues and the coverage result

If the project board move cannot be automated in the current environment, create the issues first and leave an explicit follow-up note identifying which issues still need to be moved to **Backlog**.

## Quality checklist before finishing

- the user was offered exactly 3 viable slicing choices before issue creation
- every slice has one coherent functional outcome
- every slice has explicit source traceability
- every slice has a stable chosen slice tag in the title, issue body, and `slice:<tag>` GitHub label/tag
- every slice has clear in-scope and out-of-scope boundaries
- dependencies form a DAG when they exist
- at least one parallel stream exists when the source naturally allows it
- no implementation-ready prompts leaked into the slice issues
- the coverage audit shows the full requirement is covered
- every slice's **Later task-decomposition guidance** section seeds the slice-level QA scope (including TCK applicability) and slice-level documentation scope (expected authoritative docs) so decomposition can produce the dedicated QA and docs children without guesswork
- the source item is labeled/commented as sliced

## Do not do this

- do not restate this workflow in `AGENTS.md` or `README.md`
- do not create implementation-ready child tasks in this stage
- do not move slice issues to **To Do**
- do not skip the coverage audit just because the issue list "looks complete"
- do not create technical-layer slices with no functional boundary
- do not leave overlap between slices unexplained
- do not create slice issues without assigning and preserving a stable slice tag
