# D-TEC-001 — MongoDB as the persistence layer

## Metadata
- Status: Accepted
- Date: Retroactively documented 2026-06-12 (decision predates ADR practice)
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: mongodb, persistence, json-ld
- Risk Level: Medium

## Context
The connector's core data — DCAT-AP catalogs, ODRL policies, DSP negotiation and transfer messages — are JSON-LD documents with nested, evolving structures defined by the DSP specification. The persistence layer must store these documents faithfully, tolerate spec-driven schema evolution, and support runtime-updatable application properties and audit events.

## Decision
MongoDB (7.0.12) is the sole persistence layer, accessed through Spring Data MongoDB repositories. Each running instance (provider/consumer) uses its own database, seeded at startup from `initial_data*.json`.

## Alternatives Considered
- **Relational database (PostgreSQL/MySQL + JPA)** → rejected because mapping deeply nested, spec-evolving JSON-LD documents onto relational schemas adds continuous migration overhead with little benefit; the domain has few relational joins.
- **Embedded store (H2/file-based)** → rejected for production unsuitability (no operational tooling, scaling, or independent lifecycle), though it would have simplified local development.

## Rationale
The domain model *is* JSON documents, so a document store eliminates the impedance mismatch: protocol messages and catalog entries persist in essentially their wire format. Spec version bumps (e.g. DSP 2024-1 → 2025-1) change document shapes without relational migrations. Spring Data MongoDB keeps the repository layer idiomatic, and seeding from JSON files makes profile-specific initial state (provider/consumer/TCK) trivial.

## Consequences

### Positive
- Protocol documents stored close to wire format; minimal mapping code.
- Schema evolution driven by the DSP spec does not require database migrations.
- Simple per-profile seeding via `initial_data*.json`.

### Negative
- No cross-document transactions in routine use; consistency between related documents (e.g. agreement ↔ transfer process) is enforced in service logic.
- Operational dependency on running MongoDB even for local development.

### Risks
- Unvalidated document drift across versions. Mitigated by `@NotNull`-validated builder models ([model.md](../../../negotiation/doc/model.md)) and integration tests against a real MongoDB ([D-TEC-002](D-TEC-002-testcontainers-integration-testing.md)).

## Related
- Decisions: [D-TEC-002](D-TEC-002-testcontainers-integration-testing.md)
- Docs: [profiles.md](../../profiles.md), [update_properties.md](../../update_properties.md)
- Tickets: —
