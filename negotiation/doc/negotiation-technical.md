# Negotiation Module — Technical Documentation

> **See also:** [Data Models](./model.md) | [Policy Enforcement](./policy-enforcement.md) | [Protocol Flows](./negotiation-protocol-flows.md)

## Overview

The Negotiation module implements the **DSP Contract Negotiation Protocol** for the TRUE Connector. It supports both
**Provider** and **Consumer** roles within the same deployment: a running connector can act as a provider (exposing data
under policy-governed terms) and as a consumer (requesting access to data from another connector) simultaneously.

The module stores negotiation state in MongoDB, enforces policy constraints at agreement time, and optionally executes
the full negotiation flow autonomously without human intervention.

---

## DSP Specification Alignment

Reference: [DSP 2025-1 Contract Negotiation Protocol](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/)

Implemented sections:

| DSP Section | Description | Status |
|---|---|---|
| 2.1 `GET /negotiations/:providerPid` | Retrieve negotiation state by provider PID | ✅ Implemented |
| 2.2 `POST /negotiations/request` | Consumer initiates negotiation (ContractRequestMessage) | ✅ Implemented |
| 2.3 `POST /negotiations/:providerPid/request` | Consumer sends counteroffer | ✅ Implemented |
| 2.4 `POST /negotiations/:providerPid/events` | Consumer sends accepted event | ✅ Implemented |
| 2.5 `POST /negotiations/:providerPid/agreement/verification` | Consumer verifies agreement | ✅ Implemented |
| 2.6 `POST /negotiations/:providerPid/termination` | Consumer or provider terminates | ✅ Implemented |
| Consumer callback — `GET /consumer/negotiations/:consumerPid` | Retrieve negotiation by consumer PID | ✅ Implemented |
| Consumer callback — `POST /negotiations/offers` | Provider initiates with offer | ✅ Implemented |
| Consumer callback — `POST /consumer/negotiations/:consumerPid/offers` | Provider sends counteroffer | ✅ Implemented |
| Consumer callback — `POST /consumer/negotiations/:consumerPid/agreement` | Provider sends agreement | ✅ Implemented |
| Consumer callback — `POST /consumer/negotiations/:consumerPid/events` | Provider finalizes | ✅ Implemented |
| Consumer callback — `POST /consumer/negotiations/:consumerPid/termination` | Provider terminates | ✅ Implemented |

---

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Negotiation Module                        │
│                                                                  │
│  ┌─────────────────────┐    ┌──────────────────────────────┐    │
│  │  Protocol Layer     │    │  Management API Layer         │    │
│  │  (DSP wire protocol)│    │  (/api/v1/*)                  │    │
│  │                     │    │                               │    │
│  │  Provider           │    │  ContractNegotiationAPI       │    │
│  │  Controller         │    │  Controller                   │    │
│  │  (/negotiations/*)  │    │  (/api/v1/negotiations)       │    │
│  │                     │    │                               │    │
│  │  Consumer Callback  │    │  AgreementAPIController       │    │
│  │  Controller         │    │  (/api/v1/agreements)         │    │
│  │  (/consumer/*)      │    │                               │    │
│  └──────────┬──────────┘    └────────────┬─────────────────┘    │
│             │                            │                       │
│  ┌──────────▼────────────────────────────▼─────────────────┐    │
│  │                   Service Layer                          │    │
│  │                                                          │    │
│  │  ContractNegotiationProviderService  (abstract)          │    │
│  │    └── DSPContractNegotiationProviderService (default)   │    │
│  │                                                          │    │
│  │  ContractNegotiationConsumerService  (abstract)          │    │
│  │    └── DSPContractNegotiationConsumerService (default)   │    │
│  │                                                          │    │
│  │  ContractNegotiationAPIService                           │    │
│  │  ContractNegotiationEventHandlerService                  │    │
│  │  AutomaticNegotiationService                             │    │
│  │  AgreementAPIService                                     │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  Persistence (MongoDB)                                   │    │
│  │  contract_negotiations | agreements | offers             │    │
│  └──────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### Services Pattern

Two distinct service tiers exist:

| Tier | Classes | Purpose |
|---|---|---|
| **Protocol-level** | `ContractNegotiationProviderService`, `ContractNegotiationConsumerService` | Handle inbound DSP protocol messages; enforce state machine transitions; emit audit events |
| **Management (API)** | `ContractNegotiationAPIService`, `AgreementAPIService` | Drive outbound DSP calls; called by management endpoints and `AutomaticNegotiationService` |
| **Event handler** | `ContractNegotiationEventHandlerService` | Handles specific lifecycle events (termination, verification); extends `BaseProtocolService` |
| **Automation** | `AutomaticNegotiationService` | Drives the negotiation to completion without human intervention when enabled |

### Provider vs Consumer Roles

**Provider role** — the connector that owns data and evaluates incoming requests:
- Receives `ContractRequestMessage` and validates the offer against its catalog.
- Transitions: `REQUESTED → AGREED → FINALIZED` (with verification step from consumer).
- Sends outbound: `ContractAgreementMessage`, `ContractNegotiationEventMessage(FINALIZED)`.

**Consumer role** — the connector that requests access to data:
- Initiates negotiation by sending `ContractRequestMessage` to provider.
- Transitions: `OFFERED → ACCEPTED → AGREED → VERIFIED → FINALIZED`.
- Sends outbound: `ContractNegotiationEventMessage(ACCEPTED)`, `ContractAgreementVerificationMessage`.

The `role` field on `ContractNegotiation` (values: `"PROVIDER"` or `"CONSUMER"`) records which role this connector
holds for each negotiation record.

`DSPContractNegotiationProviderService` and `DSPContractNegotiationConsumerService` are the default (non-TCK) concrete
implementations. They are activated with `@Profile("!tck")`.

---

## Contract Negotiation State Machine

### States

| State | Description |
|---|---|
| `REQUESTED` | Consumer has sent a contract request; provider received it |
| `OFFERED` | Provider sent a contract offer to the consumer |
| `ACCEPTED` | Consumer accepted the provider's offer |
| `AGREED` | Provider has sent a formal agreement to the consumer |
| `VERIFIED` | Consumer has verified the agreement by sending a verification message to the provider |
| `FINALIZED` | Provider has finalized; negotiation complete — data transfer can begin |
| `TERMINATED` | Negotiation ended without agreement; either party may terminate |

### Valid Transitions

| From | To (valid next states) | Triggered by |
|---|---|---|
| `REQUESTED` | `OFFERED`, `AGREED`, `TERMINATED` | Provider responds with offer, directly agrees, or terminates |
| `OFFERED` | `REQUESTED`, `ACCEPTED`, `TERMINATED` | Consumer sends counteroffer, accepts, or terminates |
| `ACCEPTED` | `AGREED`, `TERMINATED` | Provider agrees or terminates |
| `AGREED` | `VERIFIED`, `TERMINATED` | Consumer verifies or terminates |
| `VERIFIED` | `FINALIZED`, `TERMINATED` | Provider finalizes or terminates |
| `FINALIZED` | _(none)_ | Terminal state |
| `TERMINATED` | _(none)_ | Terminal state |

### Text Diagram

```
        [Consumer initiates]         [Provider initiates]
               │                            │
               ▼                            ▼
          REQUESTED ◄────────────────── OFFERED
               │                            │
               │ (provider agrees directly) │ (consumer accepts)
               ▼                            ▼
            AGREED ◄──────────────── ACCEPTED
               │
               │ (consumer verifies)
               ▼
           VERIFIED
               │
               │ (provider finalizes)
               ▼
           FINALIZED

  Any state ──► TERMINATED  (either party may terminate)
```

Transition enforcement is implemented in `ContractNegotiationState.canTransitTo()`. All service methods call
`BaseProtocolService.stateTransitionCheck()` before persisting a new state.

---

## REST API — Protocol Endpoints (DSP)

All protocol endpoints exchange JSON-LD messages per the DSP specification. Authentication is via connector credentials
(token-based, handled by `OkHttpRestClient`).

### Provider Endpoints

Base path: `/negotiations`

#### `GET /negotiations/{providerPid}`

Retrieve a negotiation by provider PID.

- **DSP message:** ContractNegotiation (response body)
- **Response:** `200 OK` — ContractNegotiation JSON-LD object

```json
{
  "@context": ["https://w3id.org/dspace/2025/1/context.jsonld"],
  "@type": "ContractNegotiation",
  "providerPid": "urn:uuid:a1b2c3d4-...",
  "consumerPid": "urn:uuid:e5f6...",
  "state": "REQUESTED"
}
```

---

#### `POST /negotiations/request`

Consumer initiates a new contract negotiation.

- **DSP message:** `ContractRequestMessage`
- **Response:** `201 Created` — ContractNegotiation in `REQUESTED` state
- **Location header:** `/negotiations/{providerPid}`
- **Error conditions:** `400` if offer invalid or negotiation already exists

**Request body:**
```json
{
  "@context": ["https://w3id.org/dspace/2025/1/context.jsonld"],
  "@type": "ContractRequestMessage",
  "consumerPid": "urn:uuid:consumer-123",
  "callbackAddress": "https://consumer.example.com/consumer",
  "offer": {
    "target": "urn:uuid:dataset-456",
    "action": "USE",
    "constraint": [{ "leftOperand": "COUNT", "operator": "LTEQ", "rightOperand": "5" }]
  }
}
```

---

#### `POST /negotiations/{providerPid}/request`

Consumer sends a counteroffer in an existing negotiation.

- **DSP message:** `ContractRequestMessage`
- **Response:** `200 OK` — updated ContractNegotiation
- **Error conditions:** `404` if negotiation not found; `400` if offer ID or target does not match

---

#### `POST /negotiations/{providerPid}/events`

Consumer sends an event message (accepted).

- **DSP message:** `ContractNegotiationEventMessage` with `eventType: ACCEPTED`
- **Response:** `200 OK`

---

#### `POST /negotiations/{providerPid}/agreement/verification`

Consumer verifies the agreement.

- **DSP message:** `ContractAgreementVerificationMessage`
- **Response:** `200 OK` (no body)

---

#### `POST /negotiations/{providerPid}/termination`

Either party terminates a negotiation.

- **DSP message:** `ContractNegotiationTerminationMessage`
- **Response:** `200 OK` (no body)

---

### Consumer Callback Endpoints

These endpoints are hosted by the consumer connector at its `callbackAddress`. The provider calls these after the
consumer registers its callback URL.

Base path: `/consumer/negotiations` (and `/negotiations/offers` for initial offers)

#### `GET /consumer/negotiations/{consumerPid}`

Retrieve a negotiation by consumer PID.

- **Response:** `200 OK` — ContractNegotiation JSON-LD

---

#### `POST /negotiations/offers`

Provider initiates negotiation by sending an offer to the consumer.

- **DSP message:** `ContractOfferMessage`
- **Response:** `201 Created` — ContractNegotiation in `OFFERED` state

**Request body:**
```json
{
  "@context": ["https://w3id.org/dspace/2025/1/context.jsonld"],
  "@type": "ContractOfferMessage",
  "providerPid": "urn:uuid:provider-abc",
  "callbackAddress": "https://provider.example.com",
  "offer": {
    "target": "urn:uuid:dataset-456",
    "action": "USE",
    "constraint": []
  }
}
```

---

#### `POST /consumer/negotiations/{consumerPid}/offers`

Provider sends a counteroffer.

- **DSP message:** `ContractOfferMessage`
- **Response:** `200 OK`

---

#### `POST /consumer/negotiations/{consumerPid}/agreement`

Provider sends a formal agreement to the consumer.

- **DSP message:** `ContractAgreementMessage`
- **Response:** `200 OK` (no body)

---

#### `POST /consumer/negotiations/{consumerPid}/events`

Provider sends finalization event.

- **DSP message:** `ContractNegotiationEventMessage` with `eventType: FINALIZED`
- **Response:** `200 OK` (no body)
- **Side effect:** Creates policy enforcement record; publishes `InitializeTransferProcess` event

---

#### `POST /consumer/negotiations/{consumerPid}/termination`

Provider terminates from the consumer side.

- **DSP message:** `ContractNegotiationTerminationMessage`
- **Response:** `200 OK`

---

#### `POST /consumer/negotiations/tck`

TCK-specific endpoint for test compatibility. Not for production use.

---

## REST API — Management Endpoints

Management endpoints require **Basic Auth with `ROLE_ADMIN`**. All paths are under `/api/v1/`.

### Contract Negotiation Management

Base path: `/api/v1/negotiations`  
Content-Type: `application/json`

#### `GET /api/v1/negotiations/{contractNegotiationId}`

Retrieve a single negotiation by its internal MongoDB ID.

**Response:**
```json
{
  "success": true,
  "message": "Contract negotiation with id abc found",
  "data": { ... }
}
```

---

#### `GET /api/v1/negotiations`

List negotiations with pagination and dynamic filtering.

**Query parameters:**
- `page` (default: `0`)
- `size` (default: `20`)
- `sort` (default: `timestamp,desc`)
- Any field filter (e.g., `state=REQUESTED`, `role=PROVIDER`)

**Response:** Paged HATEOAS model with metadata (page, total pages, filters applied).

---

#### `POST /api/v1/negotiations/request` _(Consumer)_

Start a new negotiation as the consumer. Sends a `ContractRequestMessage` to the provider.

**Request body:**
```json
{
  "Forward-To": "https://provider.example.com",
  "offer": {
    "target": "urn:uuid:dataset-456",
    "action": "USE",
    "assigner": "provider-connector-id",
    "constraint": []
  }
}
```

**Response:** Created `ContractNegotiation` object.

---

#### `PUT /api/v1/negotiations/{contractNegotiationId}/request` _(Consumer)_

Send a counteroffer as consumer. Sends `ContractRequestMessage` to the existing negotiation's provider.

**Request body:** Offer JSON object.

---

#### `PUT /api/v1/negotiations/{contractNegotiationId}/accept` _(Consumer)_

Accept the current offer. Sends `ContractNegotiationEventMessage(ACCEPTED)` to provider.

---

#### `PUT /api/v1/negotiations/{contractNegotiationId}/verify` _(Consumer)_

Verify an agreement. Sends `ContractAgreementVerificationMessage` to provider.

---

#### `POST /api/v1/negotiations/offer` _(Provider)_

Start a new negotiation as the provider. Sends `ContractOfferMessage` to the consumer.

**Request body:**
```json
{
  "Forward-To": "https://consumer.example.com",
  "offer": {
    "target": "urn:uuid:dataset-456",
    "action": "USE",
    "assigner": "provider-connector-id",
    "constraint": []
  }
}
```

---

#### `PUT /api/v1/negotiations/{contractNegotiationId}/offer` _(Provider)_

Send a counteroffer as provider. Sends `ContractOfferMessage` to existing negotiation's consumer.

**Request body:** Offer JSON object.

---

#### `PUT /api/v1/negotiations/{contractNegotiationId}/agree` _(Provider)_

Agree to the negotiation. Sends `ContractAgreementMessage` to consumer.

---

#### `PUT /api/v1/negotiations/{contractNegotiationId}/finalize` _(Provider)_

Finalize the negotiation. Sends `ContractNegotiationEventMessage(FINALIZED)` to consumer.

---

#### `PUT /api/v1/negotiations/{contractNegotiationId}/terminate`

Terminate a negotiation. Sends `ContractNegotiationTerminationMessage` to the peer connector.

---

### Agreement Management

Base path: `/api/v1/agreements`

#### `POST /api/v1/agreements/{agreementId}/enforce`

Enforce an agreement (check all policy constraints are currently satisfied).

**Response:**
```json
{
  "success": true,
  "message": "Agreement enforcement is ok",
  "data": "Agreement enforcement is valid"
}
```

**Error:** `409` / exception if policy evaluation fails.

---

## DSP Message Types

### `ContractRequestMessage`

Sent by the consumer to initiate a negotiation or submit a counteroffer.

| Field | Required | Description |
|---|---|---|
| `@context` | Yes | DSP JSON-LD context |
| `@type` | Yes | `"ContractRequestMessage"` |
| `consumerPid` | Yes | Consumer's unique process ID |
| `callbackAddress` | Yes* | Consumer's callback URL (initial request only) |
| `providerPid` | Yes* | Provider PID (counteroffer only) |
| `offer` | Yes | The ODRL-based offer |

*Either `callbackAddress` or `providerPid` must be present (but not both).

---

### `ContractOfferMessage`

Sent by the provider to initiate a negotiation or submit a counteroffer.

| Field | Required | Description |
|---|---|---|
| `@context` | Yes | DSP JSON-LD context |
| `@type` | Yes | `"ContractOfferMessage"` |
| `providerPid` | Yes | Provider's unique process ID |
| `callbackAddress` | Yes* | Provider's callback URL (initial offer only) |
| `consumerPid` | Yes* | Consumer PID (counteroffer only) |
| `offer` | Yes | The ODRL-based offer |

*Either `callbackAddress` or `consumerPid` must be present (but not both).

---

### `ContractAgreementMessage`

Sent by the provider when it agrees to the negotiation.

| Field | Required | Description |
|---|---|---|
| `@context` | Yes | DSP JSON-LD context |
| `@type` | Yes | `"ContractAgreementMessage"` |
| `providerPid` | Yes | Provider PID |
| `consumerPid` | Yes | Consumer PID |
| `agreement` | Yes | The formal `Agreement` object |

**Agreement object fields:** `id`, `assigner`, `assignee`, `target`, `timestamp`, `permission[]`

**Example:**
```json
{
  "@context": ["https://w3id.org/dspace/2025/1/context.jsonld"],
  "@type": "ContractAgreementMessage",
  "providerPid": "urn:uuid:provider-abc",
  "consumerPid": "urn:uuid:consumer-123",
  "agreement": {
    "@id": "urn:uuid:agreement-789",
    "assigner": "provider-connector-id",
    "assignee": "TRUEConnector v2",
    "target": "urn:uuid:dataset-456",
    "timestamp": "2025-01-15T10:30:00+00:00",
    "permission": [{ "action": "USE", "constraint": [] }]
  }
}
```

---

### `ContractAgreementVerificationMessage`

Sent by the consumer to confirm receipt and acceptance of the agreement.

| Field | Required | Description |
|---|---|---|
| `@context` | Yes | DSP JSON-LD context |
| `@type` | Yes | `"ContractAgreementVerificationMessage"` |
| `providerPid` | Yes | Provider PID |
| `consumerPid` | Yes | Consumer PID |

---

### `ContractNegotiationTerminationMessage`

Sent by either party to terminate a negotiation.

| Field | Required | Description |
|---|---|---|
| `@context` | Yes | DSP JSON-LD context |
| `@type` | Yes | `"ContractNegotiationTerminationMessage"` |
| `providerPid` | Yes | Provider PID |
| `consumerPid` | Yes | Consumer PID |
| `code` | No | Error or termination code |
| `reason` | No | List of reason strings |

---

## Automatic Negotiation

`AutomaticNegotiationService` drives the negotiation to completion without human intervention.

### What It Does

When `application.automatic.negotiation=true`, the service listens for Spring application events published at key
lifecycle transitions and automatically advances the negotiation:

| Event | Role | Action taken |
|---|---|---|
| `AutoNegotiationAgreedEvent` | Provider | Sends `ContractAgreementMessage` to consumer |
| `AutoNegotiationFinalizeEvent` | Provider | Sends `ContractNegotiationEventMessage(FINALIZED)` to consumer |
| `AutoNegotiationAcceptedEvent` | Consumer | Sends `ContractNegotiationEventMessage(ACCEPTED)` to provider |
| `AutoNegotiationVerifyEvent` | Consumer | Sends `ContractAgreementVerificationMessage` to provider |

### Retry Semantics

If a step fails (e.g., the peer is temporarily unavailable), the service retries via `TaskScheduler`:

- `retryCount` is persisted on the `ContractNegotiation` document.
- Total attempts = `maxRetries + 1` (one initial attempt plus up to `maxRetries` retries).
- Setting `maxRetries = 0` means no retries — one attempt, then `TERMINATED`.
- Retries are scheduled asynchronously; no thread is blocked during the inter-retry delay.
- When retries are exhausted, `sendContractNegotiationTerminationMessage` is called. If that also fails, the local state
  is forced to `TERMINATED`.

### Configuration

```properties
application.automatic.negotiation=true
application.automatic.negotiation.retry.max=3
application.automatic.negotiation.retry.delay.ms=2000
```

### Event Flow (Consumer-Initiated, Automatic)

```
Consumer API  ──POST /api/v1/negotiations/request──►  Provider
                                                          │
                                              [AutoNegotiationAgreedEvent]
                                                          │
                                                ◄──ContractAgreementMessage
                                                          │
Consumer  [AutoNegotiationVerifyEvent]
    │
    └──►  ContractAgreementVerificationMessage ──►  Provider
                                                          │
                                              [AutoNegotiationFinalizeEvent]
                                                          │
Consumer  ◄──  ContractNegotiationEventMessage(FINALIZED)
    │
[FINALIZED — policy enforcement created, transfer initialized]
```

---

## Policy Enforcement Integration

After FINALIZED, `ContractNegotiationConsumerService.handleContractNegotiationEventMessageFinalize()` calls
`PolicyAdministrationPoint.createPolicyEnforcement()` to register the agreement for ongoing enforcement.

`AgreementAPIService.enforceAgreement()` can be called explicitly (via `/api/v1/agreements/{id}/enforce`) to check
all policy constraints against current runtime context.

> **See also:** [Policy Enforcement Documentation](./policy-enforcement.md)

---

## Key Services

### `ContractNegotiationProviderService` (abstract)

Handles inbound DSP protocol messages from the consumer's perspective (provider role).

| Method | Responsibility |
|---|---|
| `getNegotiationByProviderPid(providerPid)` | Look up negotiation by provider PID |
| `handleContractRequestMessage(crm)` | Validate offer against catalog; create negotiation in `REQUESTED`; fire auto-negotiation event if enabled |
| `handleContractRequestMessageAsCounteroffer(providerPid, crm)` | Validate and update existing negotiation with new offer terms |
| `handleContractNegotiationEventMessageAccepted(providerPid, event)` | Transition to `ACCEPTED`; fire auto-negotiation event |
| `handleContractAgreementVerificationMessage(providerPid, cavm)` | Transition to `VERIFIED`; fire finalize event |
| `handleContractNegotiationTerminationMessage(providerPid, msg)` | Transition to `TERMINATED` |

---

### `ContractNegotiationConsumerService` (abstract)

Handles inbound DSP protocol messages at the consumer's callback endpoints.

| Method | Responsibility |
|---|---|
| `getNegotiationByConsumerPid(consumerPid)` | Look up negotiation by consumer PID |
| `handleContractOfferMessage(msg)` | Create negotiation in `OFFERED`; fire auto-accepted event |
| `handleContractOfferMessageAsCounteroffer(consumerPid, msg)` | Update negotiation with new offer; transition to `OFFERED` |
| `handleContractAgreementMessage(consumerPid, msg)` | Transition to `AGREED`; save agreement; fire auto-verify event |
| `handleContractNegotiationEventMessageFinalize(consumerPid, msg)` | Transition to `FINALIZED`; create policy enforcement; publish transfer init event |
| `handleContractNegotiationTerminationMessage(consumerPid, msg)` | Transition to `TERMINATED` |

---

### `ContractNegotiationAPIService`

Management service for driving outbound calls and querying state.

| Method | Responsibility |
|---|---|
| `findContractNegotiationById(id)` | Look up by internal MongoDB ID |
| `findContractNegotiations(filters, pageable)` | Dynamic filtered/paged query |
| `sendContractRequestMessage(request)` | Consumer: initiates negotiation with provider |
| `sendContractRequestMessageAsCounteroffer(id, offer)` | Consumer: sends counteroffer |
| `sendContractNegotiationEventMessageAccepted(id)` | Consumer: sends accepted event |
| `sendContractAgreementVerificationMessage(id)` | Consumer: sends verification |
| `sendContractOfferMessage(request)` | Provider: initiates offer-based negotiation |
| `sendContractOfferMessageAsCounteroffer(id, offer)` | Provider: sends counteroffer |
| `sendContractAgreementMessage(id)` | Provider: sends formal agreement |
| `sendContractNegotiationEventMessageFinalize(id)` | Provider: sends finalize event |
| `sendContractNegotiationTerminationMessage(id)` | Either: terminates negotiation |

---

### `ContractNegotiationEventHandlerService`

Handles domain events and side effects not covered by the core protocol services.

| Method | Responsibility |
|---|---|
| `handleContractNegotiationTerminated(id)` | Send termination message to consumer; update state |
| `verifyNegotiation(consumerPid, providerPid)` | Send `ContractAgreementVerificationMessage` to provider |
| `artifactConsumedEvent(event)` | Increment access count for policy enforcement |

---

### `AgreementAPIService`

Management service for agreement enforcement.

| Method | Responsibility |
|---|---|
| `enforceAgreement(agreementId)` | Evaluate all policy constraints on the agreement; throw if invalid |

---

### `AutomaticNegotiationService`

Retry-aware automation service for hands-free negotiation.

| Method | Responsibility |
|---|---|
| `processAgreed(id)` | Provider: send agreement (triggered by REQUESTED/ACCEPTED) |
| `processFinalize(id)` | Provider: send finalize (triggered by VERIFIED) |
| `processAccepted(id)` | Consumer: send accepted event (triggered by OFFERED) |
| `processVerify(id)` | Consumer: send verification (triggered by AGREED) |

---

## Configuration

Defined in `ContractNegotiationProperties`:

| Property | Type | Description |
|---|---|---|
| `application.callback.address` | `String` | Base callback URL for this connector. Consumer callback = `{callbackAddress}/consumer` |
| `application.automatic.negotiation` | `boolean` | Enable automatic negotiation mode |
| `application.automatic.negotiation.retry.max` | `int` | Max retries after initial attempt (default: `3`) |
| `application.automatic.negotiation.retry.delay.ms` | `long` | Delay between retries in milliseconds (default: `2000`) |
| `server.port` | `String` | Server port (used internally) |

**Example `application.properties`:**
```properties
application.callback.address=https://my-connector.example.com
application.automatic.negotiation=true
application.automatic.negotiation.retry.max=3
application.automatic.negotiation.retry.delay.ms=2000
```

---

## See Also

- [Data Models](./model.md) — ContractNegotiation, Agreement, Offer, and message builder patterns
- [Policy Enforcement](./policy-enforcement.md) — PolicyEnforcementPoint, PolicyDecisionPoint, supported constraint types
- [Protocol Flows](./negotiation-protocol-flows.md) — User-friendly guide to how negotiation works
- [DSP Specification 2025-1](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/) — Upstream protocol spec
