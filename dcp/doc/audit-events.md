# DCP Audit Events

This document is the reference guide for all audit events emitted by the DCP modules.
It describes every event type, which module and service emits it, when it fires, and
what contextual data is included in the event payload.

---

## Overview

The DCP audit subsystem is built around three classes in `dcp-common`:

| Class | Package | Role |
|---|---|---|
| `DcpAuditEventType` | `it.eng.dcp.common.audit` | Enum of all event types |
| `DcpAuditEvent` | `it.eng.dcp.common.audit` | Immutable event document (Builder pattern) |
| `DcpAuditEventPublisher` | `it.eng.dcp.common.service.audit` | Spring service — wraps `ApplicationEventPublisher` |
| `DcpAuditEventListener` | `it.eng.dcp.common.service.audit` | Async Spring listener — persists to MongoDB |
| `DcpAuditProperties` | `it.eng.dcp.common.audit` | Configuration properties (`dcp.audit.*`) |

### Event document fields

Every `DcpAuditEvent` stored in MongoDB contains these fields:

| Field | Type | Description |
|---|---|---|
| `id` | `String` | MongoDB document ID (auto-assigned) |
| `eventType` | `DcpAuditEventType` | The event type label (human-readable JSON value) |
| `timestamp` | `Instant` | UTC timestamp, set automatically at build time |
| `description` | `String` | Human-readable summary of what happened |
| `source` | `String` | Emitting module: `"common"`, `"verifier"` (and future: `"issuer"`, `"holder"`) |
| `holderDid` | `String` | DID of the credential holder (when applicable) |
| `issuerDid` | `String` | DID of the credential issuer (when applicable) |
| `credentialTypes` | `List<String>` | Credential type IDs involved (e.g. `["MembershipCredential"]`) |
| `requestId` | `String` | Correlation ID — maps to `issuerPid` / `holderPid` |
| `details` | `Map<String,Object>` | Additional key/value data specific to the event |

### Configuration

```yaml
dcp:
  audit:
    enabled: true                  # default: true — set to false to disable all audit writes
    collection-name: dcp_audit_events   # default: "dcp_audit_events"
```

To merge DCP audit events into the same MongoDB collection as connector events
(so a frontend can show a unified timeline), set:

```yaml
dcp:
  audit:
    collection-name: audit_events
```

---

## Event Type Reference

### Credential Issuance — Issuer-side ✅ Implemented

Emitted by **`IssuerService`**, **`IssuerAPIService`**, and **`CredentialDeliveryService`** (`dcp-issuer`, source = `"issuer"`).
All audit events are published from the **service layer**; `IssuerController` and `IssuerKeyRotationAPIController` contain no audit publishing logic (`KEY_ROTATED` is published by `KeyService` in `dcp-common`).

| Enum constant | JSON label | When fired | Notable `details` keys |
|---|---|---|---|
| `CREDENTIAL_REQUEST_RECEIVED` | `"Credential request received"` | `IssuerService.createCredentialRequest()` — request persisted to DB | `holderDid`, `credentialIds`, `issuerPid` |
| `CREDENTIAL_APPROVED` | `"Credential approved"` | `IssuerAPIService.approveAndDeliverCredentials()` — delivery succeeded | `requestId`, `credentialsCount`, `credentialTypes` |
| `CREDENTIAL_DENIED` | `"Credential denied"` | `CredentialDeliveryService.rejectCredentialRequest()` — rejection notification sent | `issuerPid`, `holderDid`, `rejectionReason` |
| `CREDENTIAL_DELIVERED` | `"Credential delivered"` | `CredentialDeliveryService.deliverCredentials()` — credentials sent, holder responded 2xx | `issuerPid`, `holderDid`, `credentialsCount`, `credentialTypes` |
| `CREDENTIAL_DELIVERY_FAILED` | `"Credential delivery failed"` | `CredentialDeliveryService.deliverCredentials()` — no response or exception | `issuerPid`, `holderDid`, `reason` |
| `TOKEN_VALIDATION_FAILED` | `"Token validation failed"` | `IssuerService.authorizeRequest()` — bearer token is null/blank | `reason` |

---

### Credential Lifecycle — Holder-side ✅ Implemented

Emitted by **`HolderService`** and **`CredentialIssuanceClient`** (`dcp-holder`, source = `"holder"`).
All audit events are published from the **service layer**; neither `DCPAPIController` nor `DcpController` contain any audit publishing logic.

| Enum constant | JSON label | When fired | Notable `details` keys |
|---|---|---|---|
| `CREDENTIAL_REQUESTED` | `"Credential requested"` | `CredentialIssuanceClient.requestCredential()` — issuer accepted the request (2xx) | `credentialIds`, `statusUrl`, `requestId` |
| `CREDENTIAL_REQUEST_FAILED` | `"Credential request failed"` | `CredentialIssuanceClient.requestCredential()` — non-2xx response or network/IO exception | `credentialIds`, `requestId`, `httpStatus`, `reason` |
| `ISSUER_METADATA_FETCHED` | `"Issuer metadata fetched"` | `CredentialIssuanceClient.getPersonalIssuerMetadata()` — metadata successfully retrieved | `issuerDid`, `metadataUrl` |
| `CREDENTIAL_OFFER_RECEIVED` | `"Credential offer received"` | `HolderService.processCredentialOffer()` — offer accepted | `issuerDid`, `offeredCredentialCount`, `sparseCredentialsResolved` |
| `CREDENTIAL_SAVED` | `"Credential saved"` | `HolderService.processIssuedCredentials()` — credential persisted | `credentialType` |
| `CREDENTIALS_PROCESSED` | `"Credentials processed"` | `HolderService.processIssuedCredentials()` — batch complete (fired once regardless of partial failures) | `savedCount`, `skippedCount`, `totalCount`, `issuerDid` |
| `CREDENTIAL_UNTRUSTED_ISSUER` | `"Credential from untrusted issuer skipped"` | `HolderService.processIssuedCredentials()` — issuer not in trusted list | `credentialType`, `issuerDid` |
| `CREDENTIAL_REJECTED_BY_ISSUER` | `"Credential rejected by issuer"` | `HolderService.processRejectedCredentials()` | `issuerPid`, `rejectionReason` |
| `CREDENTIAL_MESSAGE_RECEIVED` | `"Credential message received"` | `HolderService.authorizeIssuer()` — issuer identity verified, credential delivery message authenticated | `issuerDid` |

### Presentation Exchange — Holder-side ✅ Implemented

Emitted by **`HolderService`** (`dcp-holder`, source = `"holder"`).
All audit events are published from the **service layer**; the controller contains no audit publishing logic.

| Enum constant | JSON label | When fired | Notable `details` keys |
|---|---|---|---|
| `PRESENTATION_QUERY_RECEIVED` | `"Presentation query received"` | `HolderService.authorizePresentationQuery()` — bearer token validated successfully | `holderDid` |
| `PRESENTATION_CREATED` | `"Presentation created"` | `HolderService.createPresentation()` — VP built and returned | `scopes` |
| `TOKEN_VALIDATION_FAILED` | `"Token validation failed"` | `HolderService.authorizePresentationQuery()` — null/blank bearer token | `reason` |

---

### Presentation Exchange — Verifier-side ✅ Implemented

Emitted by **`VerifierService`** (`dcp-verifier`, source = `"verifier"`).

| Enum constant | JSON label | When fired | Notable `details` keys |
|---|---|---|---|
| `SELF_ISSUED_TOKEN_VALIDATED` | `"Self-issued ID token validated"` | Step 3a succeeds — self-issued ID token is valid and access token is extracted | `holderDid` |
| `TOKEN_VALIDATION_FAILED` | `"Token validation failed"` | Step 3a fails — bearer token is null/blank, `token` claim is missing/unparseable, or `validateToken` throws | `reason` |
| `PRESENTATION_QUERY_SENT` | `"Presentation query sent"` | Step 4 — presentation query dispatched to holder's Credential Service | `credentialServiceUrl`, `scopes` |
| `PRESENTATION_VERIFIED` | `"Presentation verified"` | Step 5 succeeds — all presentation JWTs and embedded credentials validated | `presentationCount` |
| `PRESENTATION_INVALID` | `"Presentation validation failed"` | Step 5 fails — signature mismatch, expired credential, untrusted issuer, holder DID mismatch, etc. | `reason` |

**Verifier flow — event sequence (happy path):**

```
SELF_ISSUED_TOKEN_VALIDATED  (Step 3a — token validated, access token extracted)
      ↓
PRESENTATION_QUERY_SENT      (Step 4  — query dispatched to holder)
      ↓
PRESENTATION_VERIFIED        (Step 5  — presentations and credentials valid)
```

**Verifier flow — event sequence (failure paths):**

```
TOKEN_VALIDATION_FAILED      (Step 3a — any token validation failure)
PRESENTATION_INVALID         (Step 5  — any presentation / credential validation failure)
```

---

### Token / Identity ✅ Implemented

Emitted by **`SelfIssuedIdTokenService`** (`dcp-common`, source = `"common"`).

| Enum constant | JSON label | When fired | Notable `details` keys |
|---|---|---|---|
| `TOKEN_ISSUED` | `"Self-issued ID token issued"` | `createAndSignToken()` succeeds — self-issued ID token signed and returned | `audience` |
| `TOKEN_ISSUED` | `"Self-issued ID token issued"` | `createStsCompatibleToken()` succeeds — wrapper token signed | `audience` |
| `TOKEN_ISSUED` | `"Self-issued ID token issued"` | `createAccessTokenWithScopes()` succeeds — access token (inner) signed | `verifier` |
| `TOKEN_VALIDATION_FAILED` | `"Token validation failed"` | `createAndSignToken()` fails with `JOSEException` | `audience`, `error` |
| `TOKEN_VALIDATION_FAILED` | `"Token validation failed"` | `createAccessTokenWithScopes()` fails with `JOSEException` | `verifier`, `error` |
| `SELF_ISSUED_TOKEN_VALIDATED` | `"Self-issued ID token validated"` | `validateToken()` succeeds — all claims valid, JTI not replayed | `subject`, `audience` |
| `TOKEN_VALIDATION_FAILED` | `"Token validation failed"` | `validateToken()` fails — any claim check, DID resolution failure, or parse error | `reason` |

> **Note:** `IDENTITY_VERIFIED` and `IDENTITY_VERIFICATION_FAILED` are defined in the enum but not yet emitted by any service.

---

### Key Management ✅ Implemented

Emitted by **`KeyService`** (`dcp-common`, source = `"common"`).

| Enum constant | JSON label | When fired | Notable `details` / `requestId` |
|---|---|---|---|
| `KEY_ROTATED` | `"Signing key rotated"` | `rotateKeyAndUpdateMetadata()` completes — new key persisted and metadata updated | `requestId` = new alias |
| `KEY_ROTATED` | `"Signing key rotated"` | `rotateAndPersistKeyPair()` **fails** — exception during keystore write | `details.error`, `details.success = false` |

---

## Module Implementation Status

| Module | Source tag | Status |
|---|---|---|
| `dcp-common` — `SelfIssuedIdTokenService` | `"common"` | ✅ Implemented |
| `dcp-common` — `KeyService` | `"common"` | ✅ Implemented |
| `dcp-verifier` — `VerifierService` | `"verifier"` | ✅ Implemented |
| `dcp-holder` — `HolderService` | `"holder"` | ✅ Implemented |
| `dcp-holder` — `DcpController` | `"holder"` | ✅ Implemented |
| `dcp-holder` — `DCPAPIController` | `"holder"` | ✅ Implemented |
| `dcp-issuer` — `IssuerService` | `"issuer"` | ✅ Implemented |
| `dcp-issuer` — `IssuerAPIService` | `"issuer"` | ✅ Implemented |
| `dcp-issuer` — `CredentialDeliveryService` | `"issuer"` | ✅ Implemented |

---

## All 26 Event Type Constants

The following table lists every constant in `DcpAuditEventType` in declaration order.

| # | Constant | JSON / `toString()` label | Implemented |
|---|---|---|---|
| 1 | `CREDENTIAL_REQUEST_RECEIVED` | `"Credential request received"` | ✅ `IssuerService` |
| 2 | `CREDENTIAL_APPROVED` | `"Credential approved"` | ✅ `IssuerAPIService` |
| 3 | `CREDENTIAL_DENIED` | `"Credential denied"` | ✅ `CredentialDeliveryService` |
| 4 | `CREDENTIAL_DELIVERED` | `"Credential delivered"` | ✅ `CredentialDeliveryService` |
| 5 | `CREDENTIAL_DELIVERY_FAILED` | `"Credential delivery failed"` | ✅ `CredentialDeliveryService` |
| 6 | `CREDENTIAL_REVOKED` | `"Credential revoked"` | ⏳ |
| 7 | `CREDENTIAL_REQUESTED` | `"Credential requested"` | ✅ `CredentialIssuanceClient` |
| 8 | `CREDENTIAL_REQUEST_FAILED` | `"Credential request failed"` | ✅ `CredentialIssuanceClient` |
| 9 | `ISSUER_METADATA_FETCHED` | `"Issuer metadata fetched"` | ✅ `CredentialIssuanceClient` |
| 10 | `CREDENTIAL_OFFER_RECEIVED` | `"Credential offer received"` | ✅ `HolderService` |
| 11 | `CREDENTIAL_SAVED` | `"Credential saved"` | ✅ `HolderService` |
| 12 | `CREDENTIALS_PROCESSED` | `"Credentials processed"` | ✅ `HolderService` |
| 13 | `CREDENTIAL_UNTRUSTED_ISSUER` | `"Credential from untrusted issuer skipped"` | ✅ `HolderService` |
| 14 | `CREDENTIAL_REJECTED_BY_ISSUER` | `"Credential rejected by issuer"` | ✅ `HolderService` |
| 15 | `CREDENTIAL_MESSAGE_RECEIVED` | `"Credential message received"` | ✅ `HolderService` |
| 16 | `PRESENTATION_QUERY_RECEIVED` | `"Presentation query received"` | ✅ `HolderService` |
| 17 | `PRESENTATION_CREATED` | `"Presentation created"` | ✅ `HolderService` |
| 18 | `PRESENTATION_QUERY_SENT` | `"Presentation query sent"` | ✅ `VerifierService` |
| 19 | `PRESENTATION_VERIFIED` | `"Presentation verified"` | ✅ `VerifierService` |
| 20 | `PRESENTATION_INVALID` | `"Presentation validation failed"` | ✅ `VerifierService` |
| 21 | `SELF_ISSUED_TOKEN_VALIDATED` | `"Self-issued ID token validated"` | ✅ `SelfIssuedIdTokenService`, `VerifierService` |
| 22 | `TOKEN_VALIDATION_FAILED` | `"Token validation failed"` | ✅ `SelfIssuedIdTokenService`, `VerifierService`, `HolderService` |
| 23 | `TOKEN_ISSUED` | `"Self-issued ID token issued"` | ✅ `SelfIssuedIdTokenService` |
| 24 | `IDENTITY_VERIFIED` | `"Identity verification succeeded"` | ⏳ |
| 25 | `IDENTITY_VERIFICATION_FAILED` | `"Identity verification failed"` | ⏳ |
| 26 | `KEY_ROTATED` | `"Signing key rotated"` | ✅ `KeyService` |

---

## MongoDB Storage

Events are written asynchronously by `DcpAuditEventListener` using `MongoTemplate.save(event, collectionName)`.
The collection name is resolved at runtime from `DcpAuditProperties#getCollectionName()`.

- **Default collection:** `dcp_audit_events`
- **Shared collection** (combined with connector events): set `dcp.audit.collection-name=audit_events`
- Persistence is guarded: any exception during write is caught and logged — it never disrupts the calling thread.
- The listener is active only when `dcp.audit.enabled=true` (default).

### Example document

```json
{
  "_id": "67d2f1a3b4e5c60012345678",
  "eventType": "Presentation verified",
  "timestamp": "2026-03-13T10:45:00.123Z",
  "description": "Presentation validated: 1 presentation(s) verified",
  "source": "verifier",
  "holderDid": "did:web:holder.example.com",
  "issuerDid": null,
  "credentialTypes": ["MembershipCredential"],
  "requestId": null,
  "details": {
    "presentationCount": 1
  }
}
```

---

## Querying Audit Events

Because the `eventType` field is serialized using its **human-readable label** (via `@JsonValue`),
queries against MongoDB should use the label string, not the enum constant name.

**MongoDB shell examples:**

```javascript
// All verifier events
db.dcp_audit_events.find({ source: "verifier" })

// All token validation failures
db.dcp_audit_events.find({ eventType: "Token validation failed" })

// All key rotation events
db.dcp_audit_events.find({ eventType: "Signing key rotated" })

// Credential batch processing summaries (saved/skipped counts)
db.dcp_audit_events.find({ eventType: "Credentials processed" })

// Presentation activity for a specific holder
db.dcp_audit_events.find({ holderDid: "did:web:holder.example.com" })

// Events from last 24 hours
db.dcp_audit_events.find({
  timestamp: { $gte: new Date(Date.now() - 86400000) }
}).sort({ timestamp: -1 })
```

**`fromLabel()` lookup in Java:**

```java
DcpAuditEventType type = DcpAuditEventType.fromLabel("Presentation verified");
// or by enum name:
DcpAuditEventType type = DcpAuditEventType.fromLabel("PRESENTATION_VERIFIED");
```

