# Architecture Decision Records

This directory captures architecturally significant decisions: choices that shape module structure, technology selection, protocols, or cross-cutting behavior. Per [AGENTS.md](../../AGENTS.md), such decisions require an ADR **before implementation**.

## Index

| ID | Title | Status | Category |
|---|---|---|---|
| [D-TEC-001](technical/D-TEC-001-keycloak-user-registration.md) | Keycloak user registration via Admin REST API | Accepted | Technical |

## Structure

```
decisions/
  architecture/   D-ARC-NNN — system structure, module boundaries, runtime topology
  technical/      D-TEC-NNN — technology choices, implementation strategies
  deprecated/     superseded ADRs (kept for history, status updated)
```

## How to Add an ADR

1. Copy `template.md` into the right category folder.
2. Name it `D-{ARC|TEC}-NNN-short-kebab-title.md` with the next free number in that category.
3. Fill in all sections — *Alternatives Considered* and *Consequences* are mandatory, not optional.
4. Open it for review in the same PR as (or before) the implementation.
5. Add a row to the index table above.
6. When a decision is superseded: set its `Status` to `Superseded`, fill `Superseded by`, move the file to `deprecated/`, and update the index.

ADRs created retroactively for pre-existing decisions carry a "Retroactively documented" note in their Metadata; their rationale is reconstructed from the codebase, CHANGELOG, and existing docs.
