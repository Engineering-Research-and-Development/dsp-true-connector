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
| [security.md](security.md) | TLS configuration, OCSP validation, certificate requirements |
| [certificate/PKI_CERTIFICATE_GUIDE.md](certificate/PKI_CERTIFICATE_GUIDE.md) | Certificate generation, JKS keystore/truststore setup |
| [ocsp/OCSP_GUIDE.md](ocsp/OCSP_GUIDE.md) | OCSP responder setup and certificate revocation checking |
| [spotbugs.md](spotbugs.md) | SpotBugs + Find Security Bugs scanning (scan-only and gate modes) |

## Configuration

| Document | Purpose |
|---|---|
| [profiles.md](profiles.md) | Spring profiles — running as provider vs consumer |
| [update_properties.md](update_properties.md) | Runtime property updates (database-backed properties) |
| [s3_configuration.md](s3_configuration.md) | S3-compatible storage (MinIO / AWS S3) setup |
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
| `tools` | [generic_filtering.md](../tools/doc/generic_filtering.md), [application_property.md](../tools/doc/application_property.md) |

## Deployment

| Document | Purpose |
|---|---|
| [../terraform/terraform.md](../terraform/terraform.md) | Kubernetes deployment via Terraform |
