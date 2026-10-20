# OpenAPI and Swagger UI

The connector publishes separate OpenAPI documents for its management API and DSP protocol API.
The interactive Swagger UI and JSON documents are disabled in standard and TCK configurations.
Docker and Terraform demo configurations opt in; do not enable them in production.

## Enable or disable documentation

Set both properties in the connector's active property source:

```properties
springdoc.api-docs.enabled=true
springdoc.swagger-ui.enabled=true
```

Set both to `false` to disable the JSON documents and UI. The OpenAPI configuration is created
only when `springdoc.swagger-ui.enabled=true`; enable both properties to make the UI and grouped
JSON documents available.

When enabled, open Swagger UI at `/swagger-ui/index.html`. The raw documents are:

| Group | Swagger UI document | Purpose |
|---|---|---|
| `management-api` | `/v3/api-docs/management-api` | Admin and management operations under `/api/**`, using plain JSON |
| `dsp-protocol` | `/v3/api-docs/dsp-protocol` | Tenant-scoped catalog, negotiation, and transfer operations, using DSP JSON-LD |

The groups contain YAML-documented operations. The shared OpenAPI `bearerAuth` scheme is used for
protected operations; login endpoints are available without a bearer token.

## Try an authenticated request

Use demo seed data only:

1. Open the `management-api` group and expand `POST /api/v1/auth/login`.
2. Select the generated login example for `admin@mail.com` and use **Try it out**. The seeded
   example credentials are for local/demo environments only.
3. Copy the returned `access_token`, select **Authorize**, and enter the token in the HTTP bearer
   field. The admin token can call management endpoints such as `GET /api/v1/dataservices`.
4. Repeat login with the generated `connector@mail.com` example and authorize with its returned
   token.
5. In `dsp-protocol`, select a tenant-scoped operation such as `POST /{tenantId}/catalog/request`.
   Set `tenantId` to an enabled tenant (for example, `engineering`) and use the generated
   JSON-LD catalog request example. The connector token is required.

Examples are generated at build time from test fixtures and the runtime serializers. Management
examples use plain JSON serialization; DSP examples use protocol serialization, including the
JSON-LD context and type metadata.

## Tenant scope and security

The documentation endpoints may be publicly reachable when enabled. They expose descriptions and
examples only; they do not provide unauthenticated access to the management or DSP operations.
Management calls continue to require their configured admin roles, and protocol calls require
`ROLE_CONNECTOR`.

The protocol tenant is selected by the `{tenantId}` path variable. Current behavior does not bind
a `ROLE_CONNECTOR` token to one tenant: the integration test verifies that a connector token
issued for `engineering` can address other enabled tenant routes, and each route returns data
scoped to the tenant named in that route. Treat connector tokens as connector-wide credentials,
not tenant-specific credentials. If a deployment requires tenant identity binding, enforce it
through an explicit authorization rule or deployment boundary rather than relying on the token
alone.

See [the security guide](security.md) for the authentication matrix and exposure guidance, and
[D-TEC-010](decisions/technical/D-TEC-010-springdoc-openapi-documentation.md) for the rationale
and trade-offs behind this design.

## Maintaining documentation

Descriptions live in each module's `src/main/resources/openapi/<module>-docs.yaml` resource.
Generated example JSON belongs under `target/classes/openapi/examples/` and must not be committed.
When changing a controller or payload, update the corresponding YAML and fixtures together. Use
`serializePlain` for management examples and `serializeProtocol` for DSP examples. Follow the
[OpenAPI documentation instructions](../.github/instructions/openapi-docs.instructions.md) and
run the OpenAPI drift and example tests.
