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
- **Audit** — append-only audit events with generic filtering API ([generic_filtering.md](../tools/doc/generic_filtering.md))
- **Runtime configuration** — database-backed application properties updatable at runtime ([update_properties.md](update_properties.md))

## Deployment

- **Local:** two IDE-launched instances (provider + consumer profiles) against local/Docker MongoDB
- **Docker:** `eclipse-temurin:17-jre-alpine`-based image, built by CI; dockerized e2e environment in `ci/`
- **Kubernetes:** Terraform definitions in [`terraform/`](../terraform/terraform.md)
