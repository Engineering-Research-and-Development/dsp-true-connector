# DSP Protocol Reference

> Quick reference for Dataspace Protocol concepts as implemented in TRUE Connector.
> For usage details and curl examples, see the [DSP Implementation Guide](./dsp-implementation-guide.md).

## Specification

| Attribute | Value |
|-----------|-------|
| **Name** | Dataspace Protocol (DSP) |
| **Version** | 2025-1 |
| **Full Spec** | https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/ |
| **JSON-LD Context** | `https://w3id.org/dspace/2025/1/context.jsonld` |

---

## Protocol Modules

| Module | DSP Section | TRUE Connector Module | Purpose |
|--------|------------|----------------------|---------|
| Catalog Protocol | Catalog | `catalog/` | Data discovery |
| Contract Negotiation | Contract Negotiation | `negotiation/` | Usage agreement |
| Transfer Process | Transfer Process | `data-transfer/` | Data movement |

---

## Glossary

### Catalog Protocol Terms

**Catalog**
Top-level container published by a Provider. A connector has one primary catalog. Contains all datasets, distributions, and services it offers. Corresponds to DCAT `Catalog`.

**Dataset**
A single data offering within a catalog. Has one or more policies (offers), one or more distributions, and metadata (title, description, keywords). Corresponds to DCAT `Dataset`.

**Distribution**
Describes one way a dataset can be accessed. Specifies the transfer format (`HttpData-PULL`, `HttpData-PUSH`) and links to a DataService. A dataset can have multiple distributions. Corresponds to DCAT `Distribution`.

**DataService**
The service endpoint that serves a distribution. The most important field is `endpointURL` — the base URL of the connector. Corresponds to DCAT `DataService`.

**Offer**
A policy offer attached to a dataset. Defines what the consumer can do (action: `use`, `anonymize`) and under what constraints (count limits, time windows, etc.). Based on ODRL. The offer ID and dataset ID are needed to start a negotiation.

**CatalogRequestMessage**
DSP message sent by Consumer to request the provider's full catalog.

**DatasetRequestMessage**
DSP message pattern for retrieving a single dataset by ID (`GET /catalog/datasets/{id}`).

---

### Contract Negotiation Terms

**ContractNegotiation**
The negotiation session between a consumer and provider. Tracks which side this connector is (role: `PROVIDER` or `CONSUMER`) and the current state.

**Agreement**
The final, binding result of a successful negotiation. References a specific dataset, policy, and both participant IDs. Required before data transfer can begin.

**providerPid**
Provider-assigned UUID identifying a specific negotiation on the provider's side.

**consumerPid**
Consumer-assigned UUID identifying the same negotiation on the consumer's side.

**ContractRequestMessage**
DSP message sent by Consumer to initiate a negotiation or send a counter-offer.

**ContractOfferMessage**
DSP message sent by Provider with a specific policy offer (used for counter-offers or provider-initiated negotiations).

**ContractAgreementMessage**
DSP message sent by Provider to formally agree to the contract terms. Triggers transition to `AGREED` on consumer.

**ContractAgreementVerificationMessage**
DSP message sent by Consumer to verify it received the agreement. Triggers transition to `VERIFIED` on provider.

**ContractNegotiationEventMessage**
DSP message carrying a lifecycle event (`ACCEPTED` or `FINALIZED`).

**ContractNegotiationTerminationMessage**
DSP message sent by either party to terminate the negotiation.

**Negotiation States**

| State | Set By | Meaning |
|-------|--------|---------|
| `REQUESTED` | Provider | Consumer sent a contract request; provider reviewing |
| `OFFERED` | Consumer | Provider made a counter-offer; consumer reviewing |
| `ACCEPTED` | Provider | Consumer accepted the current offer |
| `AGREED` | Consumer | Provider formally agreed; agreement document created |
| `VERIFIED` | Provider | Consumer confirmed receipt of the agreement |
| `FINALIZED` | Consumer | Negotiation complete; data transfer allowed |
| `TERMINATED` | Either | Negotiation ended without agreement (terminal) |

**Valid State Transitions**

| From | Valid Next States |
|------|------------------|
| `REQUESTED` | `OFFERED`, `AGREED`, `TERMINATED` |
| `OFFERED` | `REQUESTED`, `ACCEPTED`, `TERMINATED` |
| `ACCEPTED` | `AGREED`, `TERMINATED` |
| `AGREED` | `VERIFIED`, `TERMINATED` |
| `VERIFIED` | `FINALIZED`, `TERMINATED` |
| `FINALIZED` | _(terminal)_ |
| `TERMINATED` | _(terminal)_ |

---

### Transfer Process Terms

**TransferProcess**
The session tracking a single data transfer. Created when the consumer sends a `TransferRequestMessage`.

**DataAddress**
Describes where and how to access the transferred data. For HTTP-PULL it contains a presigned URL. For HTTP-PUSH it contains S3 credentials (bucket name, region, access key, secret key, endpoint override).

**TransferRequestMessage**
DSP message from Consumer to initiate a transfer. Must reference a `FINALIZED` agreement ID.

**TransferStartMessage**
DSP message from Provider indicating the transfer has started. Contains the `dataAddress` the consumer uses to retrieve data.

**TransferCompletionMessage**
DSP message from either party indicating the transfer is complete.

**TransferTerminationMessage**
DSP message from either party to terminate a transfer.

**TransferSuspensionMessage**
DSP message to temporarily suspend a transfer. Can be resumed.

**Transfer States**

| State | Meaning |
|-------|---------|
| `INITIALIZED` | Transfer process created internally by provider |
| `REQUESTED` | Consumer sent `TransferRequestMessage`; provider acknowledged |
| `STARTED` | Provider sent `TransferStartMessage`; `DataAddress` provided |
| `SUSPENDED` | Transfer temporarily paused; can be resumed |
| `COMPLETED` | Transfer finished successfully (terminal) |
| `TERMINATED` | Transfer ended prematurely (terminal) |

**Transfer Formats**

| Format | Description |
|--------|-------------|
| `HttpData-PULL` | Consumer pulls data via a presigned URL generated by the provider's S3 |
| `HttpData-PUSH` | Provider uploads data to the consumer's S3 bucket using consumer-supplied credentials |

---

## Policy / Constraint Types

Policies use [ODRL](https://www.w3.org/TR/odrl-model/) constraints. TRUE Connector supports:

| Left Operand | Operators | Right Operand Example | Description |
|-------------|-----------|----------------------|-------------|
| `count` | `lteq`, `lt` | `5` | Maximum number of accesses |
| `dateTime` | `lt`, `lteq`, `gt`, `gteq` | `2025-12-31T23:59:59Z` | Time-based access window |
| `purpose` (via policy engine) | `eq`, `isAnyOf` | `"research"` | Restrict to a specific purpose |
| `spatial` (via policy engine) | `eq`, `isAnyOf` | `"EU"` | Geographic restriction |

> **See also:** [Policy Enforcement](../negotiation/doc/policy-enforcement.md) for the policy evaluation engine details.

---

## Message Formats

All DSP messages use JSON-LD. The `@context` and `@type` are required fields.

**Catalog Protocol context:**
```json
{
  "@context": "https://w3id.org/dspace/2025/1/context.jsonld",
  "@type": "CatalogRequestMessage"
}
```

**Transfer Protocol context** (note: data-transfer uses the 2024/1 context):
```json
{
  "@context": "https://w3id.org/dspace/2024/1/context.json",
  "@type": "TransferRequestMessage"
}
```

---

## DSP Endpoint Reference

### Catalog Protocol Endpoints

| Method | Path | DSP Message | Role |
|--------|------|-------------|------|
| `POST` | `/catalog/request` | `CatalogRequestMessage` | Provider (receives) |
| `GET` | `/catalog/datasets/{id}` | `DatasetRequestMessage` | Provider (receives) |

### Contract Negotiation Protocol Endpoints

| Method | Path | DSP Message | Role |
|--------|------|-------------|------|
| `GET` | `/negotiations/{providerPid}` | — | Provider (retrieve state) |
| `POST` | `/negotiations/request` | `ContractRequestMessage` | Provider (receives from consumer) |
| `POST` | `/negotiations/{providerPid}/request` | `ContractRequestMessage` | Provider (receives counter-offer) |
| `POST` | `/negotiations/{providerPid}/events` | `ContractNegotiationEventMessage` | Provider (receives ACCEPTED) |
| `POST` | `/negotiations/{providerPid}/agreement/verification` | `ContractAgreementVerificationMessage` | Provider (receives) |
| `POST` | `/negotiations/{providerPid}/termination` | `ContractNegotiationTerminationMessage` | Provider (receives) |
| `GET` | `/consumer/negotiations/{consumerPid}` | — | Consumer callback |
| `POST` | `/negotiations/offers` | `ContractOfferMessage` | Consumer callback (provider-initiated) |
| `POST` | `/consumer/negotiations/{consumerPid}/offers` | `ContractOfferMessage` | Consumer callback |
| `POST` | `/consumer/negotiations/{consumerPid}/agreement` | `ContractAgreementMessage` | Consumer callback |
| `POST` | `/consumer/negotiations/{consumerPid}/events` | `ContractNegotiationEventMessage` | Consumer callback |
| `POST` | `/consumer/negotiations/{consumerPid}/termination` | `ContractNegotiationTerminationMessage` | Consumer callback |

### Transfer Process Protocol Endpoints

| Method | Path | DSP Message | Role |
|--------|------|-------------|------|
| `GET` | `/transfers/{providerPid}` | — | Provider (retrieve state) |
| `POST` | `/transfers/request` | `TransferRequestMessage` | Provider (receives from consumer) |
| `POST` | `/transfers/{providerPid}/start` | `TransferStartMessage` | Provider (receives) |
| `POST` | `/transfers/{providerPid}/completion` | `TransferCompletionMessage` | Provider (receives) |
| `POST` | `/transfers/{providerPid}/termination` | `TransferTerminationMessage` | Provider (receives) |
| `POST` | `/transfers/{providerPid}/suspension` | `TransferSuspensionMessage` | Provider (receives) |
| `GET` | `/consumer/transfers/{consumerPid}` | — | Consumer callback |
| `POST` | `/consumer/transfers/{consumerPid}/start` | `TransferStartMessage` | Consumer callback |
| `POST` | `/consumer/transfers/{consumerPid}/completion` | `TransferCompletionMessage` | Consumer callback |
| `POST` | `/consumer/transfers/{consumerPid}/termination` | `TransferTerminationMessage` | Consumer callback |
| `POST` | `/consumer/transfers/{consumerPid}/suspension` | `TransferSuspensionMessage` | Consumer callback |

---

## See Also

- [DSP Implementation Guide](./dsp-implementation-guide.md) — Endpoints, payloads, complete workflows
- [Negotiation Protocol Flows](../negotiation/doc/negotiation-protocol-flows.md) — Step-by-step negotiation guide
- [Negotiation Technical Docs](../negotiation/doc/negotiation-technical.md) — Architecture and state machine details
- [Data Transfer Technical Docs](../data-transfer/doc/data-transfer-technical.md) — Transfer strategies and architecture
- [DSP Specification 2025-1](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/)
