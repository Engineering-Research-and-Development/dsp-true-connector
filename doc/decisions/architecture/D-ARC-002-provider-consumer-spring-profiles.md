# D-ARC-002 — Provider/consumer roles via Spring profiles

## Metadata
- Status: Accepted
- Date: Retroactively documented 2026-06-12 (decision predates ADR practice)
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: spring, profiles, provider, consumer, configuration
- Risk Level: Low

## Context
A DSP connector acts as a provider (offering data) or a consumer (requesting data) — and in practice every participant needs both behaviors. Development and testing also require running both roles side by side on one machine to exercise full catalog → negotiation → transfer flows locally.

## Decision
A single codebase serves both roles. The role is selected at startup via Spring profile (`provider` or `consumer`), each with its own property file (`application-provider.properties` on port 8090, `application-consumer.properties` on a different port and MongoDB connection) and its own seed file (`initial_data-{profile}.json`). Containerized deployments select the role with `SPRING_PROFILES_ACTIVE`.

## Alternatives Considered
- **Separate provider and consumer codebases/artifacts** → rejected because the roles share nearly all protocol logic; two codebases would duplicate it and drift apart.
- **Runtime role switching within one instance** → rejected as unnecessary complexity; deployments know their role at startup, and local testing simply runs two instances.

## Rationale
Profiles are the idiomatic Spring mechanism for environment-specific configuration. Differing only in port, database, and seed data keeps the role distinction purely configurational, guaranteeing both roles run identical protocol logic — which is also what the DSP TCK verifies. Two locally running instances give developers a complete dataspace interaction without extra infrastructure.

## Consequences

### Positive
- One artifact to build, test, version, and release.
- Full provider↔consumer flows testable on a developer machine from the IDE.
- A third profile configuration (TCK) slots into the same mechanism for compliance runs.

### Negative
- Profile-suffixed property and seed files must be kept in sync when configuration keys change.

### Risks
- Role-specific behavior creeping into code via profile checks instead of configuration. Mitigated by keeping differences confined to property/seed files.

## Related
- Decisions: [D-ARC-001](D-ARC-001-multi-module-maven-structure.md)
- Docs: [profiles.md](../../profiles.md), [architecture.md](../../architecture.md)
- Tickets: —
