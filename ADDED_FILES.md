# Added Files Manifest — DSP TRUE Connector

All files added during the Inlay Studio → DSP TRUE Connector workflow port (2026-06-12). Paths are relative to `dsp-true-connector-main/`. **45 files added, 3 files modified.**

## Added Files

### Root — agent contract & contributor docs

| Path | Purpose |
|---|---|
| `AGENTS.md` | Agent operational contract: key documents, non-negotiables, 7-stage GitHub-integrated AI task workflow |
| `CLAUDE.md` | Claude Code entry point — points to AGENTS.md |
| `CONTRIBUTING.md` | Contributor entry point: prerequisites, build/test, workflow, DoD |
| `skills-lock.json` | Vendored-skill provenance (brainstorming, task-decomposition origins) |

### Claude Code workflow skills

| Path | Purpose |
|---|---|
| `.claude/skills/functional-slicing/SKILL.md` | Stage 1 — requirement sources → functional backlog slices |
| `.claude/skills/task-decomposition/SKILL.md` | Stage 2 — slice → impl + QA + docs child tasks |
| `.claude/skills/task-implementation/SKILL.md` | Stage 4/5 — Ready issue → branch → verify → PR |
| `.claude/skills/slice-implementation/SKILL.md` | Stage 4 alternate — whole slice as one branch/PR |
| `.claude/skills/playwright-cli/SKILL.md` | Browser automation (verbatim copy) |
| `.claude/skills/playwright-cli/references/request-mocking.md` | playwright-cli reference |
| `.claude/skills/playwright-cli/references/running-code.md` | playwright-cli reference |
| `.claude/skills/playwright-cli/references/session-management.md` | playwright-cli reference |
| `.claude/skills/playwright-cli/references/storage-state.md` | playwright-cli reference |
| `.claude/skills/playwright-cli/references/test-generation.md` | playwright-cli reference |
| `.claude/skills/playwright-cli/references/tracing.md` | playwright-cli reference |
| `.claude/skills/playwright-cli/references/video-recording.md` | playwright-cli reference |
| `.playwright/cli.config.json` | playwright-cli configuration |

### Vendored external skill (brainstorming)

| Path | Purpose |
|---|---|
| `.agents/skills/brainstorming/SKILL.md` | Design-before-code brainstorming skill (from obra/superpowers) |
| `.agents/skills/brainstorming/spec-document-reviewer-prompt.md` | Companion prompt |
| `.agents/skills/brainstorming/visual-companion.md` | Companion doc |
| `.agents/skills/brainstorming/scripts/frame-template.html` | Visual companion asset |
| `.agents/skills/brainstorming/scripts/helper.js` | Visual companion asset |
| `.agents/skills/brainstorming/scripts/server.cjs` | Visual companion asset |
| `.agents/skills/brainstorming/scripts/start-server.sh` | Visual companion asset |
| `.agents/skills/brainstorming/scripts/stop-server.sh` | Visual companion asset |

### GitHub issue templates & automation

| Path | Purpose |
|---|---|
| `.github/ISSUE_TEMPLATE/functional-slice.yml` | Backlog functional slice template |
| `.github/ISSUE_TEMPLATE/task.yml` | AI implementation task template (DSP Protocol/TCK Impact + Security sections) |
| `.github/ISSUE_TEMPLATE/qa-task.yml` | Slice-level QA task template |
| `.github/ISSUE_TEMPLATE/docs-task.yml` | Slice-level documentation task template (API changes register) |
| `.github/workflows/project-automation.yml` | Post-merge board automation: PR closing refs → Done + close (needs `PROJECT_TOKEN` secret) |

### Documentation layer

| Path | Purpose |
|---|---|
| `doc/README.md` | Documentation index — map of all guides |
| `doc/architecture.md` | Architecture overview: module map, layering, protocol flows, runtime roles |
| `doc/glossary.md` | DSP / dataspace domain glossary |
| `doc/decisions/README.md` | ADR index and how-to |
| `doc/decisions/template.md` | ADR template |
| `doc/decisions/architecture/D-ARC-001-multi-module-maven-structure.md` | ADR: module split by protocol concern |
| `doc/decisions/architecture/D-ARC-002-provider-consumer-spring-profiles.md` | ADR: roles via Spring profiles |
| `doc/decisions/technical/D-TEC-001-mongodb-persistence.md` | ADR: MongoDB persistence |
| `doc/decisions/technical/D-TEC-002-testcontainers-integration-testing.md` | ADR: Testcontainers integration testing |
| `doc/decisions/technical/D-TEC-003-async-s3-multipart-upload.md` | ADR: async parallel S3 multipart upload |

### Module READMEs

| Path | Purpose |
|---|---|
| `catalog/README.md` | Catalog module overview + doc links |
| `negotiation/README.md` | Negotiation module overview + doc links |
| `data-transfer/README.md` | Data transfer module overview + doc links |
| `connector/README.md` | Connector wrapper module overview + doc links |
| `tools/README.md` | Shared utilities module overview + doc links |

## Plain path list (copy-friendly)

```text
AGENTS.md
CLAUDE.md
CONTRIBUTING.md
skills-lock.json
.claude/skills/functional-slicing/SKILL.md
.claude/skills/task-decomposition/SKILL.md
.claude/skills/task-implementation/SKILL.md
.claude/skills/slice-implementation/SKILL.md
.claude/skills/playwright-cli/SKILL.md
.claude/skills/playwright-cli/references/request-mocking.md
.claude/skills/playwright-cli/references/running-code.md
.claude/skills/playwright-cli/references/session-management.md
.claude/skills/playwright-cli/references/storage-state.md
.claude/skills/playwright-cli/references/test-generation.md
.claude/skills/playwright-cli/references/tracing.md
.claude/skills/playwright-cli/references/video-recording.md
.playwright/cli.config.json
.agents/skills/brainstorming/SKILL.md
.agents/skills/brainstorming/spec-document-reviewer-prompt.md
.agents/skills/brainstorming/visual-companion.md
.agents/skills/brainstorming/scripts/frame-template.html
.agents/skills/brainstorming/scripts/helper.js
.agents/skills/brainstorming/scripts/server.cjs
.agents/skills/brainstorming/scripts/start-server.sh
.agents/skills/brainstorming/scripts/stop-server.sh
.github/ISSUE_TEMPLATE/functional-slice.yml
.github/ISSUE_TEMPLATE/task.yml
.github/ISSUE_TEMPLATE/qa-task.yml
.github/ISSUE_TEMPLATE/docs-task.yml
.github/workflows/project-automation.yml
doc/README.md
doc/architecture.md
doc/glossary.md
doc/decisions/README.md
doc/decisions/template.md
doc/decisions/architecture/D-ARC-001-multi-module-maven-structure.md
doc/decisions/architecture/D-ARC-002-provider-consumer-spring-profiles.md
doc/decisions/technical/D-TEC-001-mongodb-persistence.md
doc/decisions/technical/D-TEC-002-testcontainers-integration-testing.md
doc/decisions/technical/D-TEC-003-async-s3-multipart-upload.md
catalog/README.md
negotiation/README.md
data-transfer/README.md
connector/README.md
tools/README.md
```

## Modified Files (merge, don't overwrite)

These existing files were edited — copy the changes manually or diff them against your repo:

| Path | What changed |
|---|---|
| `README.md` | Added a "Documentation" section (links to doc index, architecture, CONTRIBUTING, AGENTS, ADRs, glossary) before "GUI tool for DSP TRUEConnector" |
| `doc/development_procedure.md` | Added the six-column board model (Backlog \| To Do \| Ready \| In Progress \| In Review \| Done) and the Ready human-approval gate, after the dashboard intro paragraph |
| `../CLAUDE.md` (project root, outside the repo) | Notes that the in-repo AGENTS.md is the authoritative agent contract |

## Copy command

From `dsp-true-connector-main/`, copy all added files into your real repo preserving structure (set `TARGET` first):

```bash
TARGET=/path/to/your/dsp-true-connector

rsync -av --relative \
  AGENTS.md CLAUDE.md CONTRIBUTING.md skills-lock.json \
  .claude/skills/functional-slicing .claude/skills/task-decomposition \
  .claude/skills/task-implementation .claude/skills/slice-implementation \
  .claude/skills/playwright-cli .playwright \
  .agents/skills/brainstorming \
  .github/ISSUE_TEMPLATE/functional-slice.yml .github/ISSUE_TEMPLATE/task.yml \
  .github/ISSUE_TEMPLATE/qa-task.yml .github/ISSUE_TEMPLATE/docs-task.yml \
  .github/workflows/project-automation.yml \
  doc/README.md doc/architecture.md doc/glossary.md doc/decisions \
  catalog/README.md negotiation/README.md data-transfer/README.md \
  connector/README.md tools/README.md \
  "$TARGET/"
```

Then merge the two modified files (`README.md`, `doc/development_procedure.md`) by hand.

## GitHub setup that travels with the copy

1. **`PROJECT_TOKEN` repository secret** — token with ProjectV2 access, required by `project-automation.yml`
2. **Project board columns** — Backlog | To Do | Ready | In Progress | In Review | Done
3. **Labels** — `needs-human`, `sliced`, `decomposed` (plus `slice:<tag>` per slice as you go)
