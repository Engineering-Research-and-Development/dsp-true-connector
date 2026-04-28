---
name: dsp-foundations
description: Common requirements and cross-protocol foundations for Dataspace Protocol 2025-1. Use when implementing or reviewing DSP 2025-1 authorization, schemas, JSON-LD contexts, version discovery, service discovery, and conformance rules.
---

# DSP Foundations

# Purpose

Use this skill as the default DSP 2025-1 reference before working on catalog, contract negotiation, or transfer behavior.

Treat `Dataspace.txt` in this repository as the local normative source for DSP 2025-1. The document states that version 2025-1 is considered stable and that further changes shall not affect conformity.

# Use this skill when

- A task mentions DSP 2025-1 generally.
- A task touches multiple DSP areas.
- A task involves `/.well-known/dspace-version`, service discovery, JSON-LD, JSON Schema, authorization headers, or conformance.
- You need to separate what the specification mandates from what the implementation may choose.

# Deployment context

## Two-connector model

The TRUE Connector is deployed as **two separate runtime instances** that together form a dataspace node:

- **Provider connector** — runs on port `8090` (Spring profile `provider`). It hosts datasets, exposes the catalog, evaluates usage policies, and drives the contract negotiation and transfer lifecycle from the data-owner side.
- **Consumer connector** — runs on port `8080` (Spring profile `consumer`). It discovers datasets by querying the Provider catalog, initiates contract negotiations, and requests data transfers.

Both instances are full connectors: each exposes both a management API (`/api/v1/...`) and DSP protocol endpoints. The difference is behavioural — which side initiates which messages — not architectural.

## Role responsibilities in the DSP flow

### Consumer side

1. Sends `CatalogRequestMessage` to Provider to discover available datasets and their Offers.
2. Sends `ContractRequestMessage` to Provider to start a contract negotiation.
3. Receives callbacks from Provider during negotiation (e.g., `ContractAgreementMessage`).
4. Sends `ContractAgreementVerificationMessage` back to Provider.
5. Sends `TransferRequestMessage` to Provider once a FINALIZED agreement exists.
6. Receives `TransferStartMessage` from Provider (including `dataAddress` for pull transfers).
7. Sends or receives `TransferCompletionMessage` / `TransferTerminationMessage`.

### Provider side

1. Responds to `CatalogRequestMessage` with a `dcat:Catalog` containing datasets and Offers.
2. Receives `ContractRequestMessage`; creates a negotiation; may counter-offer or agree.
3. Sends `ContractAgreementMessage` to Consumer callback when the terms are accepted.
4. Sends `ContractNegotiationEventMessage` (`FINALIZED`) to Consumer callback after verification.
5. Receives `TransferRequestMessage` from Consumer; creates a transfer process.
6. Sends `TransferStartMessage` to Consumer callback (with `dataAddress` for pull transfers).
7. Sends or receives `TransferCompletionMessage` / `TransferTerminationMessage`.

## Message flow summary

```
Consumer                              Provider
   |                                     |
   |-- CatalogRequestMessage ----------->|
   |<- dcat:Catalog (Datasets+Offers) ---|
   |                                     |
   |-- ContractRequestMessage ---------->|  (new negotiation → REQUESTED)
   |<- ContractNegotiation (201) --------|
   |<- ContractAgreementMessage ---------|  (via callback → AGREED)
   |-- ContractAgreementVerification --->|
   |<- ContractNegotiationEvent(FINAL) --|  (via callback → FINALIZED)
   |                                     |
   |-- TransferRequestMessage ---------->|  (using agreementId → REQUESTED)
   |<- TransferProcess (201) ------------|
   |<- TransferStartMessage -------------|  (P only: via callback → STARTED, with dataAddress for pull)
   |-- [data plane: pull or push] -------|
   |                                     |
   |   -- suspension (P or C) ---------- |  (→ SUSPENDED; either party may send)
   |   -- restart   (P or C) ----------- |  (→ STARTED; either party may send)
   |                                     |
   |-- TransferCompletionMessage ------->|  (P or C → COMPLETED)
```

State machine key (`C` = Consumer initiates, `P` = Provider initiates):
- `REQUESTED → STARTED` : **P only** (initial start always from Provider)
- `SUSPENDED → STARTED` : **P/C** (either party may restart a suspended transfer)
- All other transitions from STARTED and SUSPENDED : P/C

# Interoperability constraint — critical

**The remote peer connector may not be the TRUE Connector implementation.**

The Dataspace Protocol is designed for multi-vendor interoperability. In any real dataspace deployment the other party is likely a different implementation (e.g., Eclipse EDC, Sovity, a third-party connector). This has concrete implications:

- **Do not assume shared internal state.** The remote connector has its own persistence and its own in-memory state. Never infer remote state from local state.
- **Do not assume non-standard fields.** Only fields and properties defined by DSP 2025-1 JSON Schemas may be relied upon. Any additional properties in a message received from a remote connector MUST be treated as unknown and ignored.
- **Do not assume callback format beyond the spec.** The `callbackAddress` provided by a remote connector is opaque — only the DSP-defined path segments may be appended to it.
- **Do not assume policy engine compatibility.** ODRL policy evaluation strategies differ between implementations. Only the presence and structure of ODRL terms in messages are normatively defined; evaluation semantics are implementation-specific.
- **Do not assume error message body format beyond the spec.** The DSP error types (`CatalogError`, `ContractNegotiationError`, `TransferError`) define the required shape; additional properties are optional and may be absent.
- **Handle missing optional fields gracefully.** A conformant remote connector may omit any field that DSP marks as OPTIONAL or SHOULD. Do not fail hard on absent optional data.
- **Test against the TCK.** The TCK (`mvn -pl connector -Ptck verify`) is the authoritative cross-vendor compliance gate and exercises exactly the behaviours that a third-party connector depends on.

# S3 presigned URLs and artifact transfer — implementation constraint

## How presigned GET URLs are used

TRUE Connector uses **presigned GET URLs** from S3-compatible storage (MinIO/AWS S3) as the mechanism
for making artifact data available during a transfer. A presigned GET URL embeds all required
authentication and access parameters in the URL itself and is valid for a configured time window.

### HTTP-PULL transfer

The Provider generates a presigned GET URL for the artifact in its S3 bucket and delivers it to the
Consumer inside `TransferStartMessage.dataAddress.endpoint`. The Consumer opens an HTTP connection to
that URL and streams the artifact bytes, uploading them to the Consumer's own S3 bucket.

### HTTP-PUSH transfer

The Provider generates a presigned GET URL for its own artifact internally (not shared via a DSP
message), opens an HTTP connection to it, and streams the bytes to the Consumer's S3 endpoint whose
credentials arrive in `TransferRequestMessage.dataAddress.endpointProperties`.

## Critical finding: Range header on presigned URLs IS supported

**Verified by `MinioPresignedUrlRangeIT`** (connector module integration test):

AWS Signature V4 only validates headers explicitly listed in `X-Amz-SignedHeaders`. The `Range`
header is **not** included in `X-Amz-SignedHeaders` by default when generating a presigned GET URL.
Therefore, adding `Range: bytes=N-` to an HTTP connection opened from a presigned URL does NOT
invalidate the signature — MinIO (and AWS S3) return HTTP 206 Partial Content.

**The earlier claim that Range headers break presigned URL signatures was incorrect.**

Concrete implications:

- **Range-based resume IS possible for HTTP-PULL.** The Consumer opens the fresh presigned URL
  from `TransferStartMessage.dataAddress` and adds `Range: bytes=downloadedBytes-` to the connection.
  The source stream starts at `downloadedBytes`; the Consumer continues the existing S3 multipart
  upload from the next part — both source and destination savings are achieved.

- **HTTP-PUSH source reads** bypass presigned URLs entirely by using a direct S3 SDK
  `GetObjectRequest` with a native byte-range parameter against the Provider's own bucket.
  This is the cleaner approach since the Provider has SDK credentials and does not need a URL.

- **Presigned URLs expire.** The URL in `TransferStartMessage.dataAddress` has a finite validity
  window. For resume, the Provider MUST send a new `TransferStartMessage` to the Consumer callback
  with a freshly generated presigned URL before `strategy.resume()` is called.

## Summary table

| Transfer type | Source read mechanism | Resume possible? | How |
|---|---|---|---|
| HTTP-PULL | Presigned GET URL (from Provider via DSP `dataAddress`) | **Yes** — Range header on fresh URL | Add `Range: bytes=downloadedBytes-` to `HttpURLConnection`; continue existing S3 multipart upload from next part |
| HTTP-PUSH | Direct S3 SDK `GetObjectRequest` with optional byte-range | **Yes** — native SDK range | Use `getObjectInputStream(bucket, key, fromByteOffset)` on resume; continue existing multipart upload |

# Instructions for Copilot

1. Start from the normative DSP text, not from older examples in markdown docs.
2. Distinguish clearly between:
   - normative protocol requirements
   - HTTPS binding requirements
   - implementation-specific decisions
3. Do not invent protocol fields, states, endpoints, or response semantics.
4. When a requirement is implementation-specific, call it out explicitly instead of presenting it as mandated by the spec.
5. When local docs and code disagree, prefer current repository serializers, constants, and protocol controllers over stale examples.
6. Always consider both sides of an interaction: when a Consumer sends a message, think about how the Provider (potentially a different vendor) will receive and validate it, and vice versa.

# Normative anchors

- Requests to HTTPS endpoints SHOULD use the Authorization header. Token semantics are out of scope.
- All protocol messages are normatively defined by JSON Schema.
- JSON-LD 1.1 is used so implementations can interoperate between plain JSON and JSON-LD processing.
- The shared DSP 2025-1 context is `https://w3id.org/dspace/2025/1/context.jsonld`.
- Each Connector MUST expose an unversioned and unauthenticated `/.well-known/dspace-version` endpoint.
- The version response MUST contain at least one item.
- If a Connector cannot identify a matching protocol version, it MUST terminate communication.
- Participants MAY advertise services in DID documents using `CatalogService` or `DataService`.

# Version endpoint — VersionResponse shape

The `/.well-known/dspace-version` endpoint responds with a `VersionResponse` containing a `protocolVersions` array.
Each entry has the following properties:

| Property         | Required | Description                                                                 |
|------------------|----------|-----------------------------------------------------------------------------|
| `version`        | MUST     | Version tag string, e.g., `"2025-1"`.                                       |
| `path`           | MUST     | Absolute URL path segment appended to `<root>` to form `<base>`.           |
| `binding`        | MUST     | Binding identifier, e.g., `"HTTPS"`.                                        |
| `serviceId`      | SHOULD   | Unique identifier for a Data Service; groups endpoints across versions.     |
| `identifierType` | SHOULD   | Type of participant identifier used in protocol communication.              |
| `auth`           | OPTIONAL | Object describing endpoint security (see below).                            |

The `auth` object has:

| Property  | Description                                  |
|-----------|----------------------------------------------|
| `protocol`| Auth protocol name, e.g., `"OAuth2"`.        |
| `version` | Auth protocol version string.                |
| `profile` | Array of profile strings supported.          |

The concatenation of `<root>` and `path` yields `<base>`, which is the prefix for all DSP endpoints at that version.

# Service discovery via DID documents

A Participant MAY advertise its Connectors or Catalog Services via a DID document (`/.well-known/did.json` or equivalent).
When doing so, the Participant MUST add at least one entry to the DID document's `service` array conforming to the
DSP DID service JSON schema. Two service types are defined:

- `CatalogService` — advertises a Catalog Service endpoint.
- `DataService` — advertises a Connector endpoint for contract negotiation and data transfer.

Both entry types MUST include a `serviceEndpoint` property pointing to the connector root URL.

# Implementation-specific decisions to surface

- Authorization protocol and auth profile details.
- Participant identifier types (value of `identifierType` in version response).
- DID resolution and trust verification.
- Support for custom JSON-LD terms and profiles.
- Handling and logging strategy when a remote peer does not support a matching version.
- Presigned URL expiry window for S3 artifact delivery (affects transfer start latency tolerance).
- Whether to use presigned URL or direct SDK reads for Provider-side S3 source reads in HTTP-PUSH.

# Repository hints for TRUE Connector

- Read `.github/copilot-instructions.md` first.
- Respect the repository split between protocol JSON(-LD) and plain internal JSON.
- Use existing serializers when crossing that boundary:
  - `CatalogSerializer`
  - `NegotiationSerializer`
  - `TransferSerializer`
  - `ToolsSerializer`
- Shared behavior commonly lives in `tools`.
- Security and auth changes should be checked against `doc/security.md`.

# Expected output style

When using this skill, structure answers and changes like this:

1. State the exact DSP rule or section that matters.
2. Map that rule to likely repository touchpoints.
3. Identify any implementation-specific decisions that remain.
4. Suggest validation steps or tests for the affected behavior.
