# D-ARC-001 — Multi-module Maven structure by protocol concern

## Metadata
- Status: Accepted
- Date: Retroactively documented 2026-06-12 (decision predates ADR practice)
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: maven, modules, structure, dsp
- Risk Level: Low

## Context
The Dataspace Protocol defines three largely independent concerns: catalog exposure, contract negotiation, and data transfer. The codebase needed a structure that keeps these concerns separable, testable in isolation, and evolvable as the DSP specification advances, while still shipping as a single deployable application.

## Decision
The project is a multi-module Maven build with one module per DSP protocol concern (`catalog`, `negotiation`, `data-transfer`), a shared utility module (`tools`), and a wrapper module (`connector`) that wires everything into a runnable Spring Boot application and hosts the integration tests.

## Alternatives Considered
- **Single monolithic module** → rejected because protocol concerns would inevitably entangle, making independent evolution and focused testing harder as the codebase grows.
- **Separate deployable services per concern (microservices)** → rejected because a dataspace connector is deployed as one participant endpoint; splitting it multiplies operational burden (deployment, TLS, identity) without a scaling need that justifies it.

## Rationale
Module boundaries that mirror the DSP specification keep each state machine and message model in one place, make ownership obvious, and let the spec's evolution map cleanly onto the codebase. A single deployable preserves operational simplicity. Shared code is forced through `tools`, preventing ad-hoc cross-dependencies between protocol modules.

## Consequences

### Positive
- Each protocol concern can be developed and unit-tested independently.
- The dependency rule (protocol modules depend only on `tools`; `connector` depends on all) is mechanically enforceable by Maven.
- Clear home for integration tests: `connector`, where the full application context exists.

### Negative
- More build configuration to maintain (parent POM plus five module POMs).
- Cross-cutting changes (e.g. a shared model change in `tools`) touch multiple modules.

### Risks
- Module boundaries erode if shared logic is duplicated instead of moved to `tools`. Mitigated by the module-boundary constraint in [AGENTS.md](../../../AGENTS.md).

## Related
- Decisions: [D-ARC-002](D-ARC-002-provider-consumer-spring-profiles.md)
- Docs: [architecture.md](../../architecture.md)
- Tickets: —
