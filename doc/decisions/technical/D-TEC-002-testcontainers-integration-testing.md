# D-TEC-002 — Testcontainers for integration testing

## Metadata
- Status: Accepted
- Date: Retroactively documented 2026-06-12 (decision predates ADR practice)
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: testing, testcontainers, mongodb, integration
- Risk Level: Low

## Context
Integration tests need a real MongoDB: the persistence layer stores nested JSON-LD documents whose behavior an in-memory fake cannot reproduce faithfully. Tests must also run identically on developer machines and in GitHub Actions, without requiring a manually managed database instance, and the database version under test must match production (MongoDB 7.0.12).

## Decision
Integration tests use Testcontainers to start a real, version-pinned MongoDB container per test run. A shared abstract base class in the `connector` module starts the container and wires its host/port into Spring via `@DynamicPropertySource`; integration tests extend it and use MockMvc against the full application context. Integration tests run in the Maven `verify` phase, so `mvn clean verify` requires Docker.

## Alternatives Considered
- **Embedded/in-memory MongoDB (e.g. Flapdoodle)** → rejected because it lags real MongoDB versions and diverges in behavior, undermining the point of integration testing against the production database version.
- **Shared external test database** → rejected because it introduces state bleed between runs, environment setup burden, and CI/developer parity problems.
- **Mocking the repository layer in integration tests** → rejected because it would test wiring without persistence semantics — the riskiest part of document-store usage.

## Rationale
Testcontainers gives every test run a clean, disposable MongoDB at the exact production version (`mongo:7.0.12`, matched with the CI docker-compose), with zero manual setup beyond a running Docker daemon. Centralizing container lifecycle in one base class keeps individual tests free of infrastructure code. Running in the `verify` phase makes the DoD criterion "all tests pass via `mvn clean verify`" cover real persistence behavior.

## Consequences

### Positive
- Integration tests exercise real MongoDB semantics at the production version.
- No shared state between test runs; CI and local runs behave identically.
- One base class owns container setup; tests stay focused on behavior.

### Negative
- Docker is a hard prerequisite for the full build; `mvn clean verify` fails without it.
- Container startup adds time to the build.

### Risks
- Version drift between the Testcontainers image and the CI/production MongoDB version. Mitigated by the documented rule to keep the image tag in the base class matched with `ci/docker/docker-compose.yml`.

## Related
- Decisions: [D-TEC-001](D-TEC-001-mongodb-persistence.md)
- Docs: [test_containers_starting_guide.md](../../test_containers_starting_guide.md)
- Tickets: —
