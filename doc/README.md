# Documentation Index

Map of all documentation in this repository. For agent instructions and non-negotiable constraints, see [`AGENTS.md`](../AGENTS.md).

## Process & Contribution

| Document | Purpose |
|---|---|
| [development_procedure.md](development_procedure.md) | Scrum workflow, DoR/DoD, branching strategy, GitHub Actions |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | Prerequisites, build/test commands, PR flow |
| [../CHANGELOG.md](../CHANGELOG.md) | Version history and security upgrade log |

## Architecture & Decisions

| Document | Purpose |
|---|---|
| [architecture.md](architecture.md) | Module map, layering, protocol flows, runtime roles |
| [decisions/](decisions/README.md) | Architecture Decision Records (ADRs) |
| [glossary.md](glossary.md) | DSP and dataspace domain terminology |

## Security & Code Quality

| Document | Purpose |
|---|---|
| [security.md](security.md) | TLS configuration, OCSP validation, certificate requirements, Keycloak trusted issuers and `iss` troubleshooting, per-realm token rules and tenant binding, `X-Tenant-Id`, which realm Keycloak users belong in, protocol trust, connector-to-connector token and M2M secret delivery and rotation, `/internal` call zone |
| [decisions/architecture/D-ARC-005-internal-call-zone-with-in-memory-key.md](decisions/architecture/D-ARC-005-internal-call-zone-with-in-memory-key.md) | Why loopback calls use an `/internal` zone and a token signed with an in-memory key |
| [decisions/architecture/D-ARC-006-per-realm-role-allow-list-and-per-tenant-realm-credentials.md](decisions/architecture/D-ARC-006-per-realm-role-allow-list-and-per-tenant-realm-credentials.md) | Why the issuing realm decides which role counts (wrong-realm role is 401 plus audit); its per-tenant realm credentials part is withdrawn |
| [keycloak-tenant-mapping.md](keycloak-tenant-mapping.md) | How tenants and users map to Keycloak realms: one realm per tenant, `connector-admin` and `platform`, the `tenant_id` binding, request flow, seeded mapping |
| [keycloak-scripts.md](keycloak-scripts.md) | Provisioning scripts in `scripts/keycloak/`: seed all Keycloak realms into a running Keycloak, add tenant realms and users |
| [keycloak-authentication-internals.md](keycloak-authentication-internals.md) | How `KeycloakAuthenticationFilter` works underneath: chain placement, issuer selection, signature check with Nimbus, validators, role mapping, 401 vs 403 |
| [diagrams/auth_provider_architecture.md](diagrams/auth_provider_architecture.md) | Authentication chains, issuer registry and connector-to-connector diagrams |
| [openapi.md](openapi.md) | Swagger UI, OpenAPI groups, authentication walkthrough, and YAML maintenance |
| [certificate/PKI_CERTIFICATE_GUIDE.md](certificate/PKI_CERTIFICATE_GUIDE.md) | Certificate generation, JKS keystore/truststore setup |
| [ocsp/OCSP_GUIDE.md](ocsp/OCSP_GUIDE.md) | OCSP responder setup and certificate revocation checking |
| [spotbugs.md](spotbugs.md) | SpotBugs + Find Security Bugs scanning (scan-only and gate modes) |

## Configuration

| Document | Purpose |
|---|---|
| [profiles.md](profiles.md) | Spring profiles — running as provider vs consumer |
| [update_properties.md](update_properties.md) | Runtime property updates (database-backed properties) |
| [s3_configuration.md](s3_configuration.md) | S3-compatible storage (RustFS / AWS S3) setup |
| [solutions/s3_upload_mode_configuration.md](solutions/s3_upload_mode_configuration.md) | Synchronous vs asynchronous S3 upload strategies |
| [solutions/async_s3_upload_improvements.md](solutions/async_s3_upload_improvements.md) | Async multipart upload design and tuning |

## Identity & Trust

| Document | Purpose |
|---|---|
| [verifiable_credentials.md](verifiable_credentials.md) | DIDs, Verifiable Credentials, triangle of trust |
| [identity_hub.md](identity_hub.md) | Identity Hub integration, participant contexts, VC workflows |

## Testing & Compliance

| Document | Purpose |
|---|---|
| [test_containers_starting_guide.md](test_containers_starting_guide.md) | Testcontainers setup for MongoDB integration tests |
| [tck/tck_compliancy.md](tck/tck_compliancy.md) | DSP TCK compliance testing (profile setup, running the suite) |

## Module Documentation

| Module | Documents |
|---|---|
| `catalog` | [catalog.md](../catalog/doc/catalog.md), [artifact-upload.md](../catalog/doc/artifact-upload.md) |
| `negotiation` | [policy_enforcement.md](../negotiation/doc/policy_enforcement.md), [model.md](../negotiation/doc/model.md) |
| `connector` | [users.md](../connector/documentation/users.md), [negotiation.md](../connector/documentation/negotiation.md), [transfer.md](../connector/documentation/transfer.md) |
| `data-transfer` | [data-transfer.md](../data-transfer/doc/data-transfer.md), [sftp.md](../data-transfer/doc/sftp.md) |
| `tools` | [generic_filtering.md](../tools/doc/generic_filtering.md), [application_property.md](../tools/doc/application_property.md), [tenant-s3-provisioning.md](../tools/doc/tenant-s3-provisioning.md) |

## Deployment

| Document | Purpose |
|---|---|
| [../terraform/terraform.md](../terraform/terraform.md) | Kubernetes deployment via Terraform |
