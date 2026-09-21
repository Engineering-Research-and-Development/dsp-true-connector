---
name: decisions
description: Use when proposing, documenting, reviewing, superseding, or updating an architecture or technical decision record (ADR) in the TRUE Connector repository.
---

# Architecture Decision Records

## Purpose

Use this skill for an architecturally significant choice that affects system structure, module
boundaries, runtime topology, technology selection, cross-cutting behavior, or a durable
implementation strategy. An ADR records the decision, its alternatives, and its trade-offs; it is
not a task plan, release note, or API reference.

## Before Writing

1. Read `doc/decisions/README.md`, `doc/decisions/template.md`, and the closest existing ADRs.
2. Read the relevant implementation, tests, changelog entries, and authoritative module
   documentation. Do not reconstruct a decision from a commit title alone.
3. Check the current highest identifier in both categories and select the next available number in
   the chosen category.
4. If the decision is proposed before implementation, identify the affected modules and validation
   needed. If it documents an existing decision, record `Retroactively documented` in Metadata and
   base the rationale on repository evidence.

## Choose the Category

| Category | Use when the decision changes | Location and identifier |
|---|---|---|
| Architecture | Module responsibilities, dependency direction, runtime roles/topology, protocol ownership, or a system-wide interaction pattern | `doc/decisions/architecture/D-ARC-NNN-short-title.md` |
| Technical | A technology, library, persistence, security, testing, or implementation strategy within the established architecture | `doc/decisions/technical/D-TEC-NNN-short-title.md` |

If the decision does both, choose Architecture when the structural change is the primary durable
outcome; otherwise choose Technical. Do not create duplicate ADRs for one decision. Ask the user
when the boundary remains materially unclear.

Create a new ADR when the selected approach is new or changes the rationale, scope, trade-offs, or
constraints of an existing decision. Supersede the older ADR when the new decision replaces it;
make only a targeted update to an existing ADR for factual corrections or related-link maintenance.

## Write the ADR

Start from `doc/decisions/template.md`. Retain every section and use concise, evidence-based prose:

1. **Metadata** — Status, ISO-8601 date, owner, reviewers, confidence, supersession fields,
   relevant tags, and risk level. Use `Proposed` before approval and `Accepted` for an already
   adopted decision.
2. **Context** — State the decision problem, constraints, and why ordinary implementation detail
   is insufficient. Include relevant DSP compliance, tenant isolation, security, operations, and
   compatibility constraints where applicable.
3. **Decision** — State exactly what is selected, without repeating the rationale.
4. **Alternatives Considered** — Give at least two credible alternatives and why each was rejected.
   Never invent alternatives or evidence.
5. **Rationale** — Explain why the decision best meets the stated constraints, citing concrete
   implementation paths, tests, or repository documents where useful.
6. **Consequences** — Complete all three subsections: positive outcomes, negative trade-offs, and
   named risks with mitigations.
7. **Related** — Link related ADRs and authoritative repository documentation. Include issue or
   ticket references only when known.

Use repository-relative Markdown links. Do not introduce placeholder text such as `TBD`, omit
mandatory sections, or claim validation not supported by evidence.

## Register and Maintain

After creating or changing an ADR:

1. Add or update its row in the index table in `doc/decisions/README.md`.
2. For a superseded ADR, change its status to `Superseded`, set `Superseded by`, move it to
   `doc/decisions/deprecated/`, and update the index link and status.
3. Update an authoritative feature or architecture document only when the ADR changes its
   discoverability or documented behavior; do not duplicate the ADR wholesale.
4. Check relative links, numbering, required headings, and `git diff --check`.
5. Do not update `CHANGELOG.md` solely for an ADR. Update it when the associated product,
   configuration, security, or compatibility change meets the repository changelog convention.

## Common Mistakes

- **Writing a retrospective changelog entry** — describe the durable decision and its trade-offs,
  not an implementation chronology.
- **Using Architecture for any multi-module code change** — category follows the decision's scope,
  not the count of changed modules.
- **Leaving the ADR unindexed** — every ADR must be discoverable from
  `doc/decisions/README.md`.
- **Documenting routine code choices** — create an ADR only when a future contributor needs the
  rationale to avoid architectural drift or repeat a consequential evaluation.
