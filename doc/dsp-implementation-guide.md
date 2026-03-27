# DSP Implementation Guide

> This guide explains how the TRUE Connector implements the [Dataspace Protocol (DSP) 2025-1](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/) and how to use each protocol feature. You do not need to read the full specification to use this guide.

## Introduction

### What is the Dataspace Protocol?

The Dataspace Protocol (DSP) is an open standard for secure, governed data exchange between organizations. It defines three steps connectors must follow: discover what data is available (Catalog Protocol), agree on usage terms (Contract Negotiation Protocol), and then move the actual data (Transfer Process Protocol). By implementing this shared protocol, TRUE Connector can interoperate with any other DSP-compliant connector.

### How the TRUE Connector Implements DSP

The TRUE Connector implements all three DSP protocol modules in dedicated Maven modules:

| Module | Java Package | DSP Protocol |
|--------|-------------|--------------|
| `catalog/` | `it.eng.catalog` | Catalog Protocol |
| `negotiation/` | `it.eng.negotiation` | Contract Negotiation Protocol |
| `data-transfer/` | `it.eng.datatransfer` | Transfer Process Protocol |

All three modules are loaded by the main `connector/` Spring Boot application and share a single MongoDB database.

### Prerequisites

- Connector running and reachable (see [Terraform Deployment Guide](../terraform/terraform-deployment-guide.md))
- Admin credentials (default: `admin@mail.com` / `password` — change on first start)
- Basic understanding of HTTP/REST APIs and JSON

### Base URLs

```
Provider connector: http://localhost:8090   (or your provider host/port)
Consumer connector: http://localhost:8080   (or your consumer host/port)
```

> **Note:** These are default development port values. In a real deployment the ports and hostnames are configured via Terraform or your orchestration platform. When both provider and consumer run on the same machine for testing, use different ports.

### Authentication

All **management API** calls (`/api/v1/*`) use HTTP Basic Authentication:

```
Authorization: Basic <base64(email:password)>
```

The default admin account is `admin@mail.com` with password `password`. Use `-u admin@mail.com:password` with curl.

DSP **protocol endpoints** (`/catalog/*`, `/negotiations/*`, `/transfers/*`, `/consumer/*`) require a JWT Bearer token issued by a DAPS service when `application.protocol.authentication.enabled` is `true`. For development/testing you can disable this requirement — see [Disabling Protocol Authentication](#disabling-protocol-authentication).

---

## Part 1: Catalog Protocol

### What is the Catalog Protocol?

The Catalog Protocol is how a Provider publishes what data it offers and how a Consumer discovers it. Think of it as a shop window: the Provider stocks the shelves (datasets with usage policies); the Consumer browses before deciding what to negotiate for.

No actual data is transferred during catalog discovery — only metadata describing available datasets.

> **DSP Spec reference:** [Catalog Protocol](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/)

### DSP Concepts

| Concept | Description |
|---------|-------------|
| **Catalog** | Top-level container published by a Provider; holds all datasets and services |
| **Dataset** | A single data offering with metadata, policies, and distributions |
| **Distribution** | One way to access a dataset (e.g. `HttpData-PULL`, `HttpData-PUSH`) |
| **DataService** | The technical endpoint serving a distribution; carries the `endpointURL` |
| **Offer / Policy** | ODRL-based usage rules attached to a dataset (count limits, time windows, etc.) |

### Connector Implementation

#### 1. Request a Catalog (Consumer → Provider)

**DSP Message:** `CatalogRequestMessage`

A consumer sends a catalog request to the provider's DSP protocol endpoint to receive the full catalog JSON-LD document.

**DSP protocol endpoint (direct connector-to-connector call):**

```bash
curl -X POST http://provider:8090/catalog/request \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <jwt-token>" \
  -d '{
    "@context": "https://w3id.org/dspace/2025/1/context.jsonld",
    "@type": "CatalogRequestMessage"
  }'
```

**Management proxy endpoint (recommended for operators — uses Basic Auth on the consumer's own connector):**

```bash
curl -X POST http://localhost:8080/api/v1/proxy/catalogs \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '{ "Forward-To": "http://provider:8090" }'
```

**Response** — a full catalog JSON-LD document containing all datasets:

```json
{
  "@context": ["https://w3id.org/dspace/2025/1/context.jsonld"],
  "@id": "urn:uuid:1dc45797-3333-4955-8baf-ab7fd66ac4d5",
  "@type": "Catalog",
  "title": "Testcatalog - TRUEConnector team information",
  "participantId": "urn:example:DataProviderA",
  "dataset": [
    {
      "@id": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
      "@type": "Dataset",
      "title": "TRUEConnector team information dataset",
      "hasPolicy": [
        {
          "@type": "Offer",
          "@id": "urn:uuid:fdc45798-a123-4955-8baf-ab7fd66ac4d5",
          "permission": [
            {
              "action": "use",
              "constraint": [
                { "leftOperand": "count", "operator": "lteq", "rightOperand": "5" }
              ]
            }
          ]
        }
      ],
      "distribution": [
        {
          "@type": "Distribution",
          "format": "HttpData-PULL",
          "accessService": {
            "@type": "DataService",
            "@id": "urn:uuid:1dc45797-4444-conn-8baf-ab7fd66ac4d5",
            "endpointURL": "http://provider:8090/"
          }
        }
      ]
    }
  ]
}
```

From this response, note the dataset `@id` and the offer `@id` — you will need both to start a negotiation.

#### 2. Request a Specific Dataset

**DSP Message:** `DatasetRequestMessage`

```bash
curl -X GET http://provider:8090/catalog/datasets/fdc45798-a222-4955-8baf-ab7fd66ac4d5 \
  -H "Authorization: Bearer <jwt-token>"
```

Response: a single `Dataset` JSON-LD object.

#### 3. Managing the Catalog (Provider Admin)

The Provider uses the management API to populate the catalog with datasets.

**Typical setup sequence:**

1. Create a `DataService` (describes the connector endpoint):

```bash
curl -X POST http://localhost:8090/api/v1/dataservices \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '{
    "title": "TRUE Connector DSP Service",
    "endpointURL": "http://provider:8090/",
    "endpointDescription": "connector",
    "creator": "My Organisation"
  }'
```

Save the returned `@id` (e.g. `urn:uuid:1dc45797-4444-conn-8baf-ab7fd66ac4d5`).

2. Create a `Distribution` linking a transfer format to the DataService:

```bash
curl -X POST http://localhost:8090/api/v1/distributions \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '{
    "format": "HttpData-PULL",
    "title": "HTTP Pull access",
    "accessService": { "@id": "urn:uuid:1dc45797-4444-conn-8baf-ab7fd66ac4d5" }
  }'
```

3. Create a `Dataset` (with a usage policy and an artifact):

```bash
curl -X POST http://localhost:8090/api/v1/datasets \
  -u admin@mail.com:password \
  -F 'dataset={
    "title": "My Dataset",
    "keyword": ["example"],
    "hasPolicy": [{"@type": "Offer", "permission": [{"action": "use"}]}],
    "distribution": [{"@id": "urn:uuid:<distribution-id>"}]
  };type=application/json' \
  -F 'file=@/path/to/data.csv'
```

> **See also:** [Catalog User Guide](../catalog/doc/catalog.md) | [Artifact Upload](../catalog/doc/artifact-upload.md) | [Catalog Technical Docs](../catalog/doc/catalog-technical.md)

Key management endpoints:

| Operation | Method | Path |
|-----------|--------|------|
| List catalog | `GET` | `/api/v1/catalogs` |
| Create DataService | `POST` | `/api/v1/dataservices` |
| Create Distribution | `POST` | `/api/v1/distributions` |
| Create Dataset | `POST` | `/api/v1/datasets` |
| Validate Offer | `POST` | `/api/v1/offers/validate` |
| Delete Dataset | `DELETE` | `/api/v1/datasets/{id}` |

#### Catalog Workflow Summary

1. **Provider** creates DataService, Distribution, and Dataset (with policy and artifact).
2. **Consumer** requests the catalog (`POST /catalog/request` or proxy endpoint).
3. **Consumer** identifies the desired dataset ID and offer ID from the catalog response.
4. **Consumer** initiates contract negotiation (Part 2 below).

---

## Part 2: Contract Negotiation Protocol

### What is the Contract Negotiation Protocol?

Before accessing data, the consumer and provider must formally agree on the usage terms. This negotiation creates a binding agreement stored by both parties. Only after the agreement is in `FINALIZED` state can the consumer request a data transfer.

Think of it like signing a contract: the consumer proposes terms, the provider reviews and agrees (or counter-proposes), and once both sides formally confirm, the agreement is recorded.

> **DSP Spec reference:** [Contract Negotiation Protocol](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/)

### DSP Concepts

| Concept | Description |
|---------|-------------|
| **ContractRequest** | Consumer's request to access data under specified terms |
| **ContractOffer** | Provider's proposed terms (or counter-proposal) |
| **Agreement** | Mutually accepted terms — created when both sides formally agree |
| **Policy / Constraint** | ODRL-based usage rules: count limits, time ranges, purpose, location |
| **providerPid** | Provider-assigned UUID identifying the negotiation |
| **consumerPid** | Consumer-assigned UUID identifying the negotiation |

### Negotiation State Machine

```
[Consumer initiates]              [Provider initiates]
        │                                 │
        ▼                                 ▼
   REQUESTED ◄──────────────────── OFFERED
        │                                 │
        │ (provider agrees directly)      │ (consumer accepts)
        ▼                                 ▼
     AGREED ◄────────────────────── ACCEPTED
        │
        │ (consumer verifies)
        ▼
    VERIFIED
        │
        │ (provider finalizes)
        ▼
    FINALIZED ✓  ← data transfer can now begin

    Any active state ──► TERMINATED (either party)
```

| State | Set by | Meaning |
|-------|--------|---------|
| `REQUESTED` | Provider | Consumer sent a contract request; provider is reviewing |
| `OFFERED` | Consumer | Provider made a counter-offer; consumer is reviewing |
| `ACCEPTED` | Provider | Consumer accepted the current offer terms |
| `AGREED` | Consumer | Provider formally agreed; agreement document stored |
| `VERIFIED` | Provider | Consumer confirmed receipt of the agreement |
| `FINALIZED` | Consumer | Negotiation complete; data transfer allowed |
| `TERMINATED` | Either | Negotiation ended without agreement |

### Connector Implementation

#### 1. Consumer Initiates Negotiation

**DSP Message:** `ContractRequestMessage`

**Prerequisites:** You have the provider URL, the dataset `@id`, and the offer `@id` from the catalog.

Use the management API on your consumer connector to start the negotiation. The connector constructs and sends the DSP `ContractRequestMessage` to the provider on your behalf.

```bash
curl -X POST http://localhost:8080/api/v1/negotiations/request \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '{
    "Forward-To": "http://provider:8090",
    "offer": {
      "target": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
      "action": "USE",
      "assigner": "provider-connector-id",
      "constraint": []
    }
  }'
```

**Response** — `ContractNegotiation` in `REQUESTED` state:

```json
{
  "success": true,
  "message": "Contract negotiation initiated",
  "data": {
    "id": "abc-123-internal-id",
    "consumerPid": "urn:uuid:consumer-generated-id",
    "providerPid": "urn:uuid:provider-generated-id",
    "state": "REQUESTED",
    "role": "CONSUMER"
  }
}
```

Save the `id` — you will need it for follow-up actions.

#### 2. Checking Negotiation Status

```bash
# Get a specific negotiation
curl http://localhost:8080/api/v1/negotiations/abc-123-internal-id \
  -u admin@mail.com:password

# List all negotiations in a given state
curl "http://localhost:8080/api/v1/negotiations?state=AGREED" \
  -u admin@mail.com:password

# List as provider, filter by role
curl "http://localhost:8090/api/v1/negotiations?state=REQUESTED&role=PROVIDER" \
  -u admin@mail.com:password
```

#### 3. Provider Agrees to a Request

**Option A — Automatic negotiation (recommended for most deployments)**

Set the `application.automatic.negotiation` property to `true` and the provider auto-agrees to all incoming `REQUESTED` negotiations without manual intervention. See [Enabling Automatic Negotiation](#enabling-automatic-negotiation).

**Option B — Manual agreement**

```bash
curl -X PUT http://localhost:8090/api/v1/negotiations/abc-123-internal-id/agree \
  -u admin@mail.com:password
```

#### 4. Provider Sends a Counter-Offer

If the provider wants different terms, it can send a counter-offer:

```bash
curl -X PUT http://localhost:8090/api/v1/negotiations/abc-123-internal-id/offer \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '{
    "target": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
    "action": "USE",
    "constraint": [
      { "leftOperand": "COUNT", "operator": "LTEQ", "rightOperand": "3" }
    ]
  }'
```

#### 5. Consumer Accepts a Counter-Offer

When the state on the consumer side is `OFFERED`:

```bash
curl -X PUT http://localhost:8080/api/v1/negotiations/abc-123-internal-id/accept \
  -u admin@mail.com:password
```

#### 6. Consumer Verifies the Agreement

Once state becomes `AGREED`, the consumer must verify:

```bash
curl -X PUT http://localhost:8080/api/v1/negotiations/abc-123-internal-id/verify \
  -u admin@mail.com:password
```

#### 7. Provider Finalizes

After the consumer verifies (state `VERIFIED`), the provider finalizes:

```bash
curl -X PUT http://localhost:8090/api/v1/negotiations/abc-123-internal-id/finalize \
  -u admin@mail.com:password
```

State becomes `FINALIZED` on both connectors. Data transfer can now begin.

#### 8. Terminating a Negotiation

Either party can terminate at any active state:

```bash
curl -X PUT http://localhost:8080/api/v1/negotiations/abc-123-internal-id/terminate \
  -u admin@mail.com:password
```

#### Enabling Automatic Negotiation

When automatic negotiation is enabled, the provider auto-agrees to all incoming requests and the consumer auto-verifies agreements — the full negotiation completes without manual steps.

```bash
curl -X PUT http://localhost:8090/api/v1/properties/ \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '[{
    "key": "application.automatic.negotiation",
    "value": "true",
    "mandatory": false,
    "type": "ApplicationProperty"
  }]'
```

> **See also:** [Negotiation Protocol Flows](../negotiation/doc/negotiation-protocol-flows.md) | [Negotiation Technical Docs](../negotiation/doc/negotiation-technical.md) | [Policy Enforcement](../negotiation/doc/policy-enforcement.md)

#### Negotiation Workflow Summary

1. Consumer calls `/api/v1/negotiations/request` → state `REQUESTED`
2. Provider agrees (auto or manual) → state `AGREED` on consumer
3. Consumer verifies (auto or manual) → state `VERIFIED` on provider
4. Provider finalizes (auto or manual) → state `FINALIZED` on both
5. Negotiation complete — proceed to transfer

---

## Part 3: Transfer Process Protocol

### What is the Transfer Process Protocol?

After an agreement is reached, data transfer must be initiated and managed. The Transfer Process tracks the lifecycle of each data transfer session, ensuring the agreement is honoured and data moves securely between connectors.

> **DSP Spec reference:** [Transfer Process Protocol](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/)

### DSP Concepts

| Concept | Description |
|---------|-------------|
| **TransferProcess** | Tracks one data transfer session end-to-end |
| **DataAddress** | Where and how to access the data (presigned URL, S3 credentials) |
| **Transfer format** | How data moves: `HttpData-PULL`, `HttpData-PUSH` |
| **agreementId** | The ID of the FINALIZED agreement that authorizes this transfer |

### Transfer State Machine

```
INITIALIZED → REQUESTED → STARTED → COMPLETED
                  │            │
                  ▼            ▼
             TERMINATED   SUSPENDED → STARTED (resume)
```

| State | Meaning |
|-------|---------|
| `INITIALIZED` | Created internally by the provider before request |
| `REQUESTED` | Consumer sent `TransferRequestMessage`; provider acknowledged |
| `STARTED` | Provider sent `TransferStartMessage` with DataAddress |
| `SUSPENDED` | Transfer temporarily paused |
| `COMPLETED` | Transfer finished successfully (terminal) |
| `TERMINATED` | Transfer ended prematurely (terminal) |

### Transfer Methods

#### HTTP-PULL

The provider generates a time-limited presigned download URL for the artifact in its S3 bucket. The URL is included in the `TransferStartMessage`. The consumer downloads from that URL directly and stores data in its own S3 bucket.

**Best for:** Consumer pulling data from the provider's S3 on demand.

#### HTTP-PUSH

The consumer provides its own S3 bucket credentials in the transfer request. The provider downloads the artifact from its S3 and uploads it to the consumer's S3 bucket using those credentials.

**Best for:** Provider pushing data directly into consumer-controlled storage.

#### External REST

For artifacts in an external system (not S3), the provider exposes a REST artifact endpoint (`GET /artifacts/{transactionId}`). When the consumer accesses this endpoint, the provider validates the transfer and agreement, then streams data from the external source.

**Best for:** Data not stored in S3.

### Connector Implementation

#### 1. Consumer Initiates Transfer

**DSP Message:** `TransferRequestMessage`

**Prerequisites:** A `FINALIZED` agreement must exist. Note the agreement ID from the negotiation.

**Option A — Direct DSP protocol call to the provider:**

```bash
curl -X POST http://provider:8090/transfers/request \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <jwt-token>" \
  -d '{
    "@context": "https://w3id.org/dspace/2024/1/context.json",
    "@type": "TransferRequestMessage",
    "consumerPid": "urn:uuid:CONSUMER_PID_TRANSFER",
    "agreementId": "urn:uuid:AGREEMENT_ID",
    "dct:format": "HttpData-PULL",
    "callbackAddress": "http://consumer:8080/consumer"
  }'
```

**Response (201 Created):**

```json
{
  "@context": "https://w3id.org/dspace/2024/1/context.json",
  "@type": "TransferProcess",
  "consumerPid": "urn:uuid:CONSUMER_PID_TRANSFER",
  "providerPid": "urn:uuid:PROVIDER_PID_TRANSFER",
  "state": "REQUESTED"
}
```

**Option B — Management API on the consumer connector (recommended for operators):**

```bash
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '{
    "transferProcessId": "internal-id-of-the-transfer-process",
    "format": "HttpData-PULL"
  }'
```

#### 2. Provider Starts Transfer

**Option A — Automatic transfer (enabled by property)**

Set `application.automatic.transfer=true` and the provider automatically sends `TransferStartMessage` after receiving the request.

**Option B — Manual start**

```bash
curl -X PUT http://localhost:8090/api/v1/transfers/{transferProcessId}/start \
  -u admin@mail.com:password
```

When starting, the provider generates a presigned URL (for HTTP-PULL) and sends it to the consumer in `TransferStartMessage.dataAddress`.

#### 3. HTTP-PUSH: Consumer Provides S3 Credentials

For `HttpData-PUSH` transfers, the consumer's S3 credentials must be registered in the consumer connector's bucket credentials store. The management API uses these automatically when initiating the transfer. The DataAddress sent to the provider contains:

```json
"dataAddress" : {
  "endpointProperties": [
    { "name": "bucketName", "value": "consumer-bucket" },
    { "name": "region",     "value": "us-east-1" },
    { "name": "accessKey",  "value": "ACCESS_KEY" },
    { "name": "secretKey",  "value": "SECRET_KEY" },
    { "name": "endpointOverride", "value": "http://minio:9000" }
  ]
}
```

#### 4. Checking Transfer Status

```bash
# List all transfer processes
curl http://localhost:8090/api/v1/transfers \
  -u admin@mail.com:password

# Get a specific transfer process
curl http://localhost:8090/api/v1/transfers/{transferProcessId} \
  -u admin@mail.com:password
```

#### 5. Consumer Downloads the Data

After the transfer is in `STARTED` state, the consumer can trigger the actual download:

```bash
curl http://localhost:8080/api/v1/transfers/{transferProcessId}/download \
  -u admin@mail.com:password
```

Returns `202 Accepted` immediately; download and completion happen asynchronously.

#### 6. Viewing a Completed Transfer

Once `COMPLETED`, generate a presigned view URL:

```bash
curl http://localhost:8080/api/v1/transfers/{transferProcessId}/view \
  -u admin@mail.com:password
```

#### 7. Completing or Terminating a Transfer

```bash
# Complete (sends TransferCompletionMessage to peer)
curl -X PUT http://localhost:8090/api/v1/transfers/{transferProcessId}/complete \
  -u admin@mail.com:password

# Terminate
curl -X PUT http://localhost:8090/api/v1/transfers/{transferProcessId}/terminate \
  -u admin@mail.com:password

# Suspend
curl -X PUT http://localhost:8090/api/v1/transfers/{transferProcessId}/suspend \
  -u admin@mail.com:password
```

#### Enabling Automatic Data Transfer

```bash
curl -X PUT http://localhost:8090/api/v1/properties/ \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '[{
    "key": "application.automatic.transfer",
    "value": "true",
    "mandatory": false,
    "type": "ApplicationProperty"
  }]'
```

> **See also:** [Data Transfer User Guide](../data-transfer/doc/data-transfer.md) | [Data Transfer Technical Docs](../data-transfer/doc/data-transfer-technical.md)

---

## End-to-End Workflow: Discover → Negotiate → Transfer

This walkthrough uses consistent placeholder UUIDs throughout all steps.

**Assumptions:**
- Provider connector: `http://provider:8090`
- Consumer connector: `http://localhost:8080`
- Automatic negotiation and transfer are **disabled** (manual steps shown)

### Step 1 — Discover: Request the Catalog

```bash
curl -X POST http://localhost:8080/api/v1/proxy/catalogs \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '{ "Forward-To": "http://provider:8090" }'
```

From the response, extract:
- **Dataset ID:** `urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5`
- **Offer ID:** `urn:uuid:fdc45798-a123-4955-8baf-ab7fd66ac4d5`

### Step 2 — Negotiate: Send Contract Request

```bash
curl -X POST http://localhost:8080/api/v1/negotiations/request \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '{
    "Forward-To": "http://provider:8090",
    "offer": {
      "target": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
      "action": "USE",
      "assigner": "provider-connector-id",
      "constraint": []
    }
  }'
```

Save the returned `id` (e.g. `neg-abc-123`). State: `REQUESTED`.

### Step 3 — Negotiate: Provider Agrees (manual)

```bash
curl -X PUT http://provider:8090/api/v1/negotiations/neg-abc-123/agree \
  -u admin@mail.com:password
```

State on consumer: `AGREED`.

### Step 4 — Negotiate: Consumer Verifies

```bash
curl -X PUT http://localhost:8080/api/v1/negotiations/neg-abc-123/verify \
  -u admin@mail.com:password
```

State: `VERIFIED` on provider.

### Step 5 — Negotiate: Provider Finalizes

```bash
curl -X PUT http://provider:8090/api/v1/negotiations/neg-abc-123/finalize \
  -u admin@mail.com:password
```

State: `FINALIZED` on both connectors. Extract the `agreementId` from the negotiation record.

### Step 6 — Transfer: Consumer Initiates

```bash
curl -X POST http://provider:8090/transfers/request \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <jwt-token>" \
  -d '{
    "@context": "https://w3id.org/dspace/2024/1/context.json",
    "@type": "TransferRequestMessage",
    "consumerPid": "urn:uuid:a1b2c3d4-transfer-consumer",
    "agreementId": "urn:uuid:AGREEMENT_ID_FROM_STEP_5",
    "dct:format": "HttpData-PULL",
    "callbackAddress": "http://localhost:8080/consumer"
  }'
```

State: `REQUESTED`. Save the `providerPid` from the response.

### Step 7 — Transfer: Provider Starts

```bash
curl -X PUT http://provider:8090/api/v1/transfers/{transferProcessId}/start \
  -u admin@mail.com:password
```

The provider generates a presigned URL and sends `TransferStartMessage` to the consumer. State: `STARTED`.

### Step 8 — Transfer: Consumer Downloads

```bash
curl http://localhost:8080/api/v1/transfers/{transferProcessId}/download \
  -u admin@mail.com:password
```

State transitions to `COMPLETED` after the download and completion message exchange.

---

## Disabling Protocol Authentication

For development and testing, you can disable JWT authentication on DSP protocol endpoints:

```bash
curl -X PUT http://localhost:8080/api/v1/properties/ \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '[{
    "key": "application.protocol.authentication.enabled",
    "value": "false",
    "mandatory": false,
    "type": "ApplicationProperty"
  }]'
```

> ⚠️ **Never disable protocol authentication in production.** This is for local testing only.

---

## Using the Postman Collection

The connector ships with a pre-configured Postman collection:

- **Collection:** `True_connector_DSP.postman_collection.json`
- **Environment:** `True_connector_DSP.environment.json`

**To use:**

1. Open Postman and click **Import**.
2. Import both the collection file and the environment file.
3. Select the `True_connector_DSP` environment from the top-right environment dropdown.
4. Update the environment variables (`providerUrl`, `consumerUrl`, credentials) to match your deployment.
5. Requests are organized by protocol: Catalog, Negotiation, Transfer.

---

## Troubleshooting

| Symptom | Likely Cause | Solution |
|---------|-------------|----------|
| `401 Unauthorized` on management API | Wrong Basic Auth credentials | Verify email/password; use `-u admin@mail.com:password` |
| `401 Unauthorized` on protocol endpoints | Missing or invalid JWT | Set `application.protocol.authentication.enabled=false` for testing, or provide a valid DAPS token |
| `404` on negotiation by providerPid | Wrong PID or connector role mismatch | Verify you are querying the right connector (provider vs consumer) |
| Agreement not found during transfer | Negotiation not `FINALIZED` | Check negotiation state with `GET /api/v1/negotiations/{id}` |
| Transfer `503` or policy error | Policy constraint violated (count exceeded, time expired) | Check the policy attached to the dataset and the agreement |
| S3 upload fails (HTTP-PUSH) | Wrong S3 credentials in DataAddress | Verify bucket name, region, access key, secret key, and endpoint override |
| Catalog request returns empty | No datasets in the provider catalog | Create at least one dataset via `POST /api/v1/datasets` |

---

## See Also

- [DSP Protocol Reference](./dsp-protocol-reference.md) — Concepts glossary and message types
- [User Guide](./user-guide.md) — Getting started for operators
- [Implementation Reference](./implementation-reference.md) — Production configuration
- [Catalog Technical Docs](../catalog/doc/catalog-technical.md)
- [Negotiation Technical Docs](../negotiation/doc/negotiation-technical.md)
- [Data Transfer Technical Docs](../data-transfer/doc/data-transfer-technical.md)
- [DSP Specification 2025-1](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/)
