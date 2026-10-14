# D-TEC-010 — Property-gated springdoc OpenAPI documentation

## Metadata
- Status: Accepted
- Date: 2026-10-14
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: openapi, springdoc, security, api-documentation
- Risk Level: Medium

## Context

The connector needs interactive and machine-readable API documentation for both management
endpoints and tenant-scoped Dataspace Protocol (DSP) endpoints. The two surfaces have different
audiences, authorization roles, request formats, and serialization rules: management operations
use plain JSON, while DSP messages use protocol JSON-LD. Documentation and examples must not
confuse these contracts or become a source of unprotected API access.

The existing connector runs in multiple profiles and deployment configurations, including the
TCK profile. Documentation endpoints should therefore be opt-in for deployments, and enabling
documentation must not weaken the authorization on management or protocol operations. This
decision records the already-adopted implementation retroactively.

## Decision

Use springdoc-openapi with separate `management-api` and `dsp-protocol` groups. Keep operation
descriptions in per-module `openapi/*-docs.yaml` resources and generate JSON examples during the
build from existing fixtures and the appropriate plain or protocol serializer. Keep Swagger UI
and OpenAPI JSON disabled in normal and TCK configurations; demo deployments may explicitly
enable them. When enabled, the documentation endpoints are public, but invoking documented
operations continues to require the existing authentication and authorization.

## Alternatives Considered

- **Add springdoc annotations to controllers and request models** → rejected because this
  distributes descriptions through business code, mixes protocol documentation with plain-JSON
  APIs, and can duplicate serializer-defined behavior.
- **Publish one combined API document** → rejected because it obscures the boundary between
  management JSON and tenant-scoped DSP JSON-LD operations and their different roles.
- **Maintain request examples as hand-written JSON** → rejected because examples can drift from
  runtime serialization; build-time generation reuses the existing fixtures and serializers.
- **Enable documentation endpoints by default** → rejected because public documentation exposes
  endpoint shapes and demo examples unnecessarily in production deployments.

## Rationale

`OpenApiConfig` creates the two groups and applies bearer-auth metadata, while
`OpenApiDocsLoader` loads module YAML and generated examples without adding documentation
annotations to controllers. The example generator reuses `serializePlain` for management
requests and `serializeProtocol` for DSP messages, preserving JSON-LD context and type metadata.
`OpenApiDocsDriftIT` and protocol documentation tests verify that documented operations, tags,
and generated examples remain aligned with the implementation.

The security boundary is unchanged: the OpenAPI bearer scheme helps clients supply credentials,
but the existing Spring Security chains authorize each request. The public documentation routes
are descriptions and examples, not a proxy for the operations they describe.

## Consequences

### Positive
- Operators and API clients can discover management and DSP endpoints separately.
- YAML keeps descriptions reviewable and separated from controller implementation.
- Generated examples reflect the serializers used at runtime.
- Documentation can be enabled for demo environments without changing operation authorization.

### Negative
- YAML and example fixtures must be updated when documented controller behavior or payloads
  change.
- Publicly enabled documentation reveals endpoint structure and contains seeded demo examples;
  it is not intended for production exposure.
- The `ROLE_CONNECTOR` claim is not bound to a specific tenant. For protocol calls, the tenant
  path selects the enabled tenant whose data is returned; a connector token can address another
  enabled tenant route. Deployments must not treat that token as tenant-specific authorization.

### Risks
- **Documentation may be enabled in a production deployment.** Mitigation: keep both Springdoc
  properties disabled in standard and TCK configurations, enable them only in demos, and follow
  the exposure guidance in [the security guide](../../security.md).
- **Generated examples may become stale if fixtures or YAML references are not updated.**
  Mitigation: retain the OpenAPI drift and serializer-based example tests in CI.
- **A deployment may assume that a connector token limits access to its tenant.** Mitigation:
  document the observed path-selected tenant behavior and require a separate explicit tenant
  binding or deployment boundary wherever tenant-specific authorization is required.

## Related
- Decisions: —
- Docs: [OpenAPI usage and maintenance](../../openapi.md); [Security guide](../../security.md);
  [OpenAPI documentation instructions](../../../.github/instructions/openapi-docs.instructions.md)
- Implementation: [`OpenApiConfig`](../../../connector/src/main/java/it/eng/connector/configuration/OpenApiConfig.java),
  [`OpenApiDocsLoader`](../../../connector/src/main/java/it/eng/connector/configuration/OpenApiDocsLoader.java),
  and [`OpenApiExamplesGenerator`](../../../connector/src/test/java/it/eng/connector/openapi/OpenApiExamplesGenerator.java)
- Tests: [`OpenApiDocsDriftIT`](../../../connector/src/test/java/it/eng/connector/configuration/OpenApiDocsDriftIT.java),
  [`CrossTenantIsolationIT`](../../../connector/src/test/java/it/eng/connector/integration/multitenant/CrossTenantIsolationIT.java)
- Tickets: #351, #360, #361
