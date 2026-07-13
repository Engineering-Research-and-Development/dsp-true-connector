# Architecture Overview

High-level map of the DSP TRUE Connector. This document links to deep-dive docs rather than duplicating them — see the [documentation index](README.md).

## What the Connector Is

The TRUE Connector implements the [Dataspace Protocol (DSP) 2025-1](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/): a standard for sovereign data sharing between organizations in a dataspace. A connector publishes datasets in a catalog, negotiates usage contracts, and transfers data — always under policy control. The implementation passes 100% of the DSP Technical Compliance Kit (TCK) test suite (see [tck/tck_compliancy.md](tck/tck_compliancy.md)).

## Module Map

Multi-module Maven project. Each protocol concern lives in its own module; `connector` wires everything together.

```
                    ┌─────────────┐
                    │  connector   │   application wrapper: Spring Boot entry point,
                    │              │   user management, integration tests
                    └──────┬───────┘
           ┌───────────┬───┴────────────┐
   ┌───────┴─────┐ ┌────┴────────┐ ┌────┴───────────┐
   │   catalog   │ │ negotiation │ │ data-transfer  │   one module per DSP
   └───────┬─────┘ └────┬────────┘ └────┬───────────┘   protocol concern
           └───────────┬┴───────────────┘
                  ┌────┴─────┐
                  │  tools   │   shared utilities: audit, runtime properties,
                  └──────────┘   serialization, generic filtering
```

| Module | Owns | Key docs |
|---|---|---|
| `catalog` | DCAT-AP catalog documents, datasets, distributions, artifact storage | [catalog.md](../catalog/doc/catalog.md), [artifact-upload.md](../catalog/doc/artifact-upload.md) |
| `negotiation` | Contract negotiation state machine, ODRL policy enforcement, agreements | [policy_enforcement.md](../negotiation/doc/policy_enforcement.md), [model.md](../negotiation/doc/model.md) |
| `data-transfer` | Transfer process lifecycle, transfer channels (HTTPS pull, S3, SFTP) | [data-transfer.md](../data-transfer/doc/data-transfer.md), [sftp.md](../data-transfer/doc/sftp.md) |
| `tools` | Audit events, database-backed application properties, shared serialization | [generic_filtering.md](../tools/doc/generic_filtering.md), [application_property.md](../tools/doc/application_property.md) |
| `connector` | Spring Boot application, user/role management, agreement scheduler, all integration tests | [users.md](../connector/documentation/users.md), [negotiation.md](../connector/documentation/negotiation.md), [transfer.md](../connector/documentation/transfer.md) |

**Dependency rule:** `catalog`, `negotiation`, and `data-transfer` are independent of one another and share code only through `tools`. `connector` depends on all of them. No cross-module reach-ins.

## Layering

Standard Spring layering within each module:

```
Controller (protocol + management REST endpoints)
    → Service (business logic, state transitions, policy checks)
        → Repository (Spring Data MongoDB)
            → MongoDB
```

Protocol message classes (JSON-LD documents) follow a strict builder/validation pattern — see [model.md](../negotiation/doc/model.md) and [.github/instructions/model-class-guidelines.instructions.md](../.github/instructions/model-class-guidelines.instructions.md).

## Protocol Flows

The three DSP concerns map directly to modules:

1. **Catalog** — a consumer requests the provider's catalog (DCAT-AP); datasets carry ODRL offers describing usage conditions.
2. **Contract negotiation** — consumer and provider exchange negotiation messages until an **Agreement** is reached (or the negotiation is terminated). Constraints (COUNT, DATE_TIME, PURPOSE, SPATIAL) are evaluated by the policy enforcement components — see [policy_enforcement.md](../negotiation/doc/policy_enforcement.md).
3. **Transfer process** — with a valid agreement, the consumer requests a transfer (e.g. `HttpData-PULL`); the provider validates the agreement and the transfer moves through DSP transfer states. See [data-transfer.md](../data-transfer/doc/data-transfer.md) and [transfer.md](../connector/documentation/transfer.md).

State machines and message formats follow the DSP 2025-1 specification; the TCK suite verifies them (65 tests across metadata, catalog, negotiation, and transfer).

## Runtime Roles: Provider vs Consumer

A single codebase serves both roles, selected via Spring profile (see [profiles.md](profiles.md)):

- `application-provider.properties` — provider role (port 8090)
- `application-consumer.properties` — consumer role (different port and MongoDB connection, so both can run locally)
- `initial_data-{profile}.json` — seeds MongoDB with connector metadata, users, and properties per role
- Containerized deployments set `SPRING_PROFILES_ACTIVE` instead

A third profile configuration (`application-tck.properties`) exists for DSP TCK compliance runs.

## Cross-Cutting Concerns

- **Authentication & users** — JWT-based protocol auth plus Basic auth for human/management API users, with role-based access ([users.md](../connector/documentation/users.md))
- **Transport security** — TLS with optional OCSP revocation checking ([security.md](security.md), [ocsp/OCSP_GUIDE.md](ocsp/OCSP_GUIDE.md)); PKI setup in [certificate/PKI_CERTIFICATE_GUIDE.md](certificate/PKI_CERTIFICATE_GUIDE.md)
- **Decentralized identity** — DIDs and Verifiable Credentials via Identity Hub integration ([verifiable_credentials.md](verifiable_credentials.md), [identity_hub.md](identity_hub.md))
- **Artifact storage** — S3-compatible object storage (MinIO/AWS S3) with sync or async multipart upload ([s3_configuration.md](s3_configuration.md), [solutions/](solutions/))
- **Multi-tenancy** — a single connector instance serves multiple isolated tenants. Each tenant has its own MongoDB documents (partitioned by `tenantId`) and its own S3 bucket. The active tenant is set in the thread-local `TenantContextHolder` on every inbound request. Key guarantees introduced in MT3:
  - **Per-tenant S3 bucket auto-derivation** — when a tenant is created without an explicit `bucketName`, the connector derives one as `dsp-{tenantId}` and provisions it in S3/MinIO before persisting the tenant document. See [tools/doc/tenant-s3-provisioning.md](../tools/doc/tenant-s3-provisioning.md).
  - **Startup compound indexes** — seven MongoDB collections (`catalogs`, `datasets`, `contract_negotiations`, `transfer_process`, `agreements`, `audit_events`, `application_properties`) receive `(tenantId, _id)` compound indexes at application startup via `InitialDataLoader`. Index creation is idempotent. See ADR [D-TEC-005](decisions/technical/D-TEC-005-programmatic-startup-indexes.md).
  - **@DBRef cascade isolation** — `CatalogService` enforces tenantId consistency before writing any cross-document reference. `CatalogRepository` exposes tenantId-scoped query variants. See ADR [D-TEC-006](decisions/technical/D-TEC-006-dbref-tenant-filter-mitigation.md) and [catalog/doc/catalog.md](../catalog/doc/catalog.md).
  - **Async tenant context propagation** — `TenantContextHolder` is a `ThreadLocal`, so it does not automatically survive a hop onto a Spring executor or scheduler worker thread. `TenantContextTaskDecorator` (`tools/src/main/java/it/eng/tools/configuration/TenantContextTaskDecorator.java`) implements Spring's `TaskDecorator` seam: it captures the submitting thread's tenant ID, sets it on the worker thread before the task runs, and clears it afterwards to avoid leaking context across pooled threads. It is installed on every executor/scheduler that runs work outside the HTTP request thread, including the async Spring event executor (`AsynchronousSpringEventsConfig`), the negotiation retry scheduler (`NegotiationConfiguration`), and the data-transfer executors and scheduler described in [data-transfer/doc/data-transfer.md](../data-transfer/doc/data-transfer.md#async-tenant-context-propagation).
  - **`/{tenantId}/` protocol routing** — all DSP protocol endpoints (catalog, negotiations, transfers) are served under a `/{tenantId}/` path prefix, resolved by `TenantAwareProtocolController` before any request processing (introduced in MT2, see the Changed section of the MT4 [CHANGELOG.md](../CHANGELOG.md) entry). MT4 confirmed via `CrossTenantTransferIT` (`connector/src/test/java/it/eng/connector/integration/multitenant/CrossTenantTransferIT.java`) that a single connector instance can run two tenants — one acting as provider, one as consumer — through a full automatic negotiation plus HTTP-PULL transfer cycle using this routing, and re-verified 65/65 DSP TCK compliance against the `/{tenantId}/` routed endpoints.
  - **S3 admin-key accepted risk (ADR D-TEC-007)** — `IamUserManagementService.createUser()`, used to provision the temporary MinIO IAM user for HTTP-PUSH transfers, requires the S3 admin credentials because MinIO does not allow a delegated IAM user to create other IAM users. See ADR [D-TEC-007](decisions/technical/D-TEC-007-s3-admin-key-http-push-temp-user.md) for the accepted-risk rationale and future mitigation path.
- **Audit** — append-only audit events with generic filtering API ([generic_filtering.md](../tools/doc/generic_filtering.md))
- **Runtime configuration** — database-backed application properties updatable at runtime ([update_properties.md](update_properties.md))

## Deployment

- **Local:** two IDE-launched instances (provider + consumer profiles) against local/Docker MongoDB
- **Docker:** `eclipse-temurin:17-jre-alpine`-based image, built by CI; dockerized e2e environment in `ci/`
- **Kubernetes:** Terraform definitions in [`terraform/`](../terraform/terraform.md)
