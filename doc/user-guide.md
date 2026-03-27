# User Guide — Getting Started with TRUE Connector

> **See also:** [DSP Implementation Guide](./dsp-implementation-guide.md) — Complete curl examples and protocol workflows

## What is TRUE Connector?

TRUE Connector is a secure data-sharing application built on the [Dataspace Protocol (DSP)](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/) standard. It lets organizations publish data offerings, discover data from other connectors, negotiate the legal terms of use, and then transfer data — all in a governed, auditable way. Because TRUE Connector implements the open DSP standard, it works with any other DSP-compliant connector regardless of vendor.

**Key capabilities:**

- Publish data for others to discover (Catalog)
- Discover and access data from other connectors
- Negotiate usage terms automatically or manually
- Transfer data securely via HTTP or S3 object storage
- Enforce usage policies on every data access
- Maintain a complete audit trail of all operations

---

## Who This Guide Is For

This guide is for **operators** who want to use the connector — not developers building or extending it. You need basic familiarity with running HTTP commands (curl or Postman), but you do not need to understand the underlying protocol or read the DSP specification.

> **Developers** extending the connector should start with the [Developer Guide](./developer-guide.md).
>
> **DevOps / Operators** deploying to production should see the [Implementation Reference](./implementation-reference.md).

---

## Quick Start: I Want to…

### Publish Data (as a Provider)

You want other connectors to discover and access your data.

1. **Start the connector** — see [Terraform Deployment Guide](../terraform/terraform-deployment-guide.md).
2. **Upload your data file** (artifact) — see [Artifact Upload](../catalog/doc/artifact-upload.md).
3. **Create a DataService** describing your connector's endpoint URL.
4. **Create a Distribution** linking a transfer format to the DataService.
5. **Create a Dataset** in the catalog with your data, usage policy, and distribution.
6. **Wait** — consumers can now discover your catalog and initiate negotiation and transfer.

> **See also:** [Catalog User Guide](../catalog/doc/catalog.md) for step-by-step management API commands.

### Access Data (as a Consumer)

You want to discover and download data from another connector.

1. **Obtain the provider's connector URL** from your dataspace operator or partner.
2. **Request their catalog** using the proxy endpoint on your connector.
3. **Identify the dataset** you need (note its ID and offer ID).
4. **Initiate contract negotiation** — agree on the usage terms.
5. **Once the negotiation is `FINALIZED`**, initiate data transfer.
6. **Download the data** once the transfer is in `STARTED` state.

> **See also:** [DSP Implementation Guide](./dsp-implementation-guide.md) for the full walkthrough with curl commands.

### Understand the Negotiation Process

Before data can move, both connectors must formally agree on how the data may be used. The negotiation follows a defined sequence of steps:

- Consumer sends a contract request → state `REQUESTED`
- Provider agrees → state `AGREED`
- Consumer verifies → state `VERIFIED`
- Provider finalizes → state `FINALIZED`
- Transfer can now begin

You can check the current state of any negotiation:

```bash
curl http://localhost:8080/api/v1/negotiations/{id} \
  -u admin@mail.com:password
```

> **See also:** [Negotiation Protocol Flows](../negotiation/doc/negotiation-protocol-flows.md) for step-by-step commands and examples.

### Transfer Data

After a `FINALIZED` agreement exists, the consumer initiates transfer. Choose the right method for your situation:

| Method | When to Use |
|--------|-------------|
| **HTTP-PULL** | Consumer pulls data from provider's S3 bucket on demand |
| **HTTP-PUSH** | Provider pushes data into consumer's S3 bucket automatically |
| **External REST** | Data is not stored in S3; provider streams it from an external source |

> **See also:** [Data Transfer User Guide](../data-transfer/doc/data-transfer.md) for step-by-step instructions.

---

## Key Concepts (in Plain Language)

**Catalog**
The published list of data a provider makes available. Think of it as a product catalogue. A consumer browses the catalog to discover what data exists before requesting access. No actual data is transferred at this stage — only descriptions.

**Dataset**
One data offering within the catalog. Each dataset has a title, description, usage policy, and one or more ways to access it (distributions). Datasets are backed by an artifact — the actual data file or external URL.

**Policy**
The rules that govern how the data may be used. A policy might say "this data may be accessed up to 5 times" or "only until 2025-12-31". The consumer must agree to these rules in a formal negotiation before accessing the data.

**Agreement**
The formal record that both sides have agreed to the usage terms. An agreement is stored by both the consumer and provider connectors. Data transfer requires a valid, `FINALIZED` agreement.

**Transfer**
The process of moving the actual data after an agreement is in place. The transfer is tracked as a session with its own lifecycle (states: REQUESTED → STARTED → COMPLETED).

> **Full glossary:** [DSP Protocol Reference](./dsp-protocol-reference.md)

---

## Configuration Basics

### Default Admin Credentials

The connector starts with a default admin account:

| Email | Password |
|-------|----------|
| `admin@mail.com` | `password` |

> ⚠️ **Change the default password immediately after first startup.**
>
> See [Connector Platform Features](../connector/doc/connector-platform-features.md) for how to change passwords.

### Enabling Automatic Negotiation and Transfer

By default, the connector requires manual approval at each negotiation step. For automated workflows:

```bash
# Enable automatic negotiation (provider auto-agrees to all requests)
curl -X PUT http://localhost:8080/api/v1/properties/ \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '[{"key": "application.automatic.negotiation", "value": "true", "mandatory": false, "type": "ApplicationProperty"}]'

# Enable automatic data transfer (provider auto-starts transfer after agreement)
curl -X PUT http://localhost:8080/api/v1/properties/ \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '[{"key": "application.automatic.datatransfer", "value": "true", "mandatory": false, "type": "ApplicationProperty"}]'
```

> **See also:** [Tools Implementation Guide](../tools/doc/tools-implementation.md) for all configurable properties.

### Key Application Properties

| Property | Default | Effect |
|----------|---------|--------|
| `application.automatic.negotiation` | `false` | Auto-accept all incoming negotiation requests |
| `application.automatic.datatransfer` | `false` | Auto-start data transfer after agreement is finalized |
| `application.protocol.authentication.enabled` | `true` | Require JWT token on DSP protocol endpoints |

---

## Managing Users

The connector ships with two built-in accounts. Operators can add more:

```bash
# Create a new user
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '{"email": "operator@example.com", "password": "NewPassword1!", "role": "ROLE_ADMIN"}'
```

> **See also:** [Connector Platform Features](../connector/doc/connector-platform-features.md) for full user management documentation.

---

## Need More Detail?

| Task | Guide |
|------|-------|
| Full curl examples for all DSP flows | [DSP Implementation Guide](./dsp-implementation-guide.md) |
| Catalog and dataset management | [Catalog User Guide](../catalog/doc/catalog.md) |
| Uploading data files | [Artifact Upload](../catalog/doc/artifact-upload.md) |
| Negotiation step-by-step | [Negotiation Protocol Flows](../negotiation/doc/negotiation-protocol-flows.md) |
| Data transfer step-by-step | [Data Transfer User Guide](../data-transfer/doc/data-transfer.md) |
| Policy concepts | [Policy Enforcement](../negotiation/doc/policy-enforcement.md) |
| Protocol concepts glossary | [DSP Protocol Reference](./dsp-protocol-reference.md) |
| Production deployment | [Implementation Reference](./implementation-reference.md) |
| Security configuration | [Security Configuration](./security.md) |
| S3 storage setup | [S3 Configuration](./s3-configuration.md) |
| All properties | [Application Properties](../tools/doc/application-property.md) |
| Kubernetes deployment | [Terraform Deployment Guide](../terraform/terraform-deployment-guide.md) |

---

## See Also

- [Documentation Index](./README.md) — Complete navigation by role and topic
- [Developer Guide](./developer-guide.md) — For developers extending the connector
- [Implementation Reference](./implementation-reference.md) — For DevOps operators in production
