---
name: update-changelog
description: Use when asked to update the changelog, or when changes on the working branch need to be recorded in CHANGELOG.md.
---

# Update Changelog

## Overview

Collect all changes from the working branch, classify them, and prepend a new versioned entry to `CHANGELOG.md` following the project's exact format.

## Instructions

### 1. Read the version and today's date

- Extract the `<revision>` property from the **root `pom.xml`** — that is the version string (e.g. `0.6.12-SNAPSHOT`).
- Use today's date, formatted as `DD.MM.YYYY.` (two-digit day, two-digit month, four-digit year, **trailing dot**).

```bash
grep "<revision>" pom.xml
```

### 2. Gather changes from the working branch

Collect commits that are on the working branch but not yet on `develop`:

```bash
git log --no-merges --oneline develop..HEAD
git diff develop...HEAD --stat
git diff develop...HEAD
```

Inspect commit messages and diffs to classify every change.

### 3. Draft the entry

Only three sub-sections are allowed, and only in this order:

| Sub-section | When to include |
|-------------|-----------------|
| `### Added` | New classes, features, endpoints, fields, events |
| `### Changed` | Modified behaviour, renamed items, updated logic |
| `### Removed` | Deleted classes, fields, endpoints, modes |

**Omit a sub-section entirely when it has no items.** Never add empty headings and never invent other headings (`### Fixed`, `### Bug Fixes`, `### Deprecated`, etc.).

Entry shape:

```markdown
## [VERSION] - DD.MM.YYYY.

### Added
- `NewClass` — description of what it does.

### Changed
- **`ModifiedClass`** — description of what changed and why.

### Removed
- `DeletedClass` — removed because …
```

Style rules (match existing entries):
- Bold the primary component name with `**Name**` when it is the subject of the bullet.
- Use backtick code spans for class, method, field, and property names.
- Keep bullets concise but informative — one bullet per logical unit of change.

### 4. Insert at the top of CHANGELOG.md

The new entry must be the **first `##` section** in the file, immediately after the opening paragraph:

```
# Changelog

All notable changes to this project will be documented in this file.

## [NEW VERSION] - TODAY          ← insert here
...
## [previous version] - …
```

Use the edit tool to insert after the blank line that follows "All notable changes…".

### 5. Verify

Read the first 60–80 lines of the updated `CHANGELOG.md` to confirm:
- New entry is at the top.
- Date format matches `DD.MM.YYYY.` (with trailing dot).
- Only `### Added`, `### Changed`, `### Removed` headings are present.
- No empty sub-sections.

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Appending at the bottom | Always prepend — new entry is first |
| Wrong date format (`2026-05-05` or `05/05/2026`) | Use `DD.MM.YYYY.` with trailing dot |
| Empty `### Removed` when nothing was removed | Omit the sub-section entirely |
| Inventing `### Fixed` or `### Bug Fixes` | Only Added / Changed / Removed |
| Reading version from a module pom instead of root | Always read `<revision>` from root `pom.xml` |
| Wrong order of sub-sections | Always: Added → Changed → Removed |
