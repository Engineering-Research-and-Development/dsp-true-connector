# Architecture Decision Records

This directory captures architecturally significant decisions: choices that shape module structure, technology selection, protocols, or cross-cutting behavior. Per [AGENTS.md](../../AGENTS.md), such decisions require an ADR **before implementation**.

## Index

| ID                                                                       | Title | Status | Category |
|--------------------------------------------------------------------------|---|---|---|
| [D-ARC-001](architecture/D-ARC-001-multi-module-maven-structure.md)      | Multi-module Maven structure by protocol concern | Accepted | Architecture |
| [D-ARC-002](architecture/D-ARC-002-provider-consumer-spring-profiles.md) | Provider/consumer roles via Spring profiles | Accepted | Architecture |
| [D-TEC-001](technical/D-TEC-001-mongodb-persistence.md)                  | MongoDB as the persistence layer | Accepted | Technical |
| [D-TEC-002](technical/D-TEC-002-testcontainers-integration-testing.md)   | Testcontainers for integration testing | Accepted | Technical |
| [D-TEC-003](technical/D-TEC-003-async-s3-multipart-upload.md)            | Asynchronous parallel S3 multipart upload | Accepted | Technical |
| [D-TEC-004](technical/D-TEC-004-keycloak-user-registration.md)           | Keycloak user registration via Admin REST API | Accepted | Technical |
| [D-TEC-005](technical/D-TEC-005-programmatic-startup-indexes.md)         | Programmatic startup index creation via MongoTemplate | Accepted | Technical |
| [D-TEC-006](technical/D-TEC-006-dbref-tenant-filter-mitigation.md)       | @DBRef tenant-filter limitation and service-layer mitigation | Accepted | Technical |
| [D-TEC-007](technical/D-TEC-007-s3-admin-key-http-push-temp-user.md)    | S3 admin key for HTTP-PUSH temporary user creation — accepted risk | Accepted | Technical |

## Structure

```
decisions/
  architecture/   D-ARC-NNN — system structure, module boundaries, runtime topology
  technical/      D-TEC-NNN — technology choices, implementation strategies
  deprecated/     superseded ADRs (kept for history, status updated)
```

## How to Add an ADR

1. Copy [`template.md`](template.md) into the right category folder.
2. Name it `D-{ARC|TEC}-NNN-short-kebab-title.md` with the next free number in that category.
3. Fill in all sections — *Alternatives Considered* and *Consequences* are mandatory, not optional.
4. Open it for review in the same PR as (or before) the implementation.
5. Add a row to the index table above.
6. When a decision is superseded: set its `Status` to `Superseded`, fill `Superseded by`, move the file to `deprecated/`, and update the index.

ADRs created retroactively for pre-existing decisions carry a "Retroactively documented" note in their Metadata; their rationale is reconstructed from the codebase, CHANGELOG, and existing docs.
