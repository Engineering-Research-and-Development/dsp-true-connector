---
name: dsp-contract-negotiation
description: Contract Negotiation Protocol and HTTPS Binding guidance for Dataspace Protocol 2025-1. Use when implementing or reviewing offers, agreements, callbacks, negotiation state transitions, or /negotiations endpoints.
---

# DSP Contract Negotiation

# Purpose

Use this skill for DSP 2025-1 contract negotiation behavior, including request and callback flows.

Load `dsp-foundations` together with this skill when possible.

# Use this skill when

- A task touches `/negotiations` endpoints.
- A task changes contract offers, agreements, or negotiation event handling.
- A task involves callback URLs, asynchronous negotiation flow, or negotiation state transitions.
- A task reviews negotiation-related DSP compliance.

# Instructions for Copilot

1. Treat Sections 7 and 8 of DSP 2025-1 as the main reference, plus Section 4 for common requirements.
2. Preserve the negotiation state machine exactly unless the task is explicitly about fixing non-compliance.
3. Separate normative negotiation flow from implementation-specific offer evaluation and policy decisions.
4. Never invent new negotiation states or alternate callback paths.

# State model

Predefined contract negotiation states are:

- REQUESTED
- OFFERED
- ACCEPTED
- AGREED
- VERIFIED
- FINALIZED
- TERMINATED

# Provider-side HTTPS bindings

| Method | Path                                                | HTTP response          |
|--------|-----------------------------------------------------|------------------------|
| GET    | `/negotiations/:providerPid`                        | 200 ContractNegotiation|
| POST   | `/negotiations/request`                             | 201 ContractNegotiation|
| POST   | `/negotiations/:providerPid/request`                | 200                    |
| POST   | `/negotiations/:providerPid/events`                 | 200                    |
| POST   | `/negotiations/:providerPid/agreement/verification` | 200                    |
| POST   | `/negotiations/:providerPid/termination`            | 200                    |

Important semantics:

- Initiating `POST /negotiations/request` returns HTTP 201 with a `ContractNegotiation` body.
- Successful follow-up state-changing POSTs return HTTP 200.
- Invalid state transitions MUST return HTTP 400 with a `Contract Negotiation Error`.
- Missing negotiations MUST return HTTP 404.
- Unauthorized access MUST return HTTP 404.

# Consumer callback bindings

| Method | Path                                               | HTTP response          |
|--------|----------------------------------------------------|------------------------|
| GET    | `/:callback/negotiations/:consumerPid`             | 200 ContractNegotiation|
| POST   | `/negotiations/offers`                             | 201 ContractNegotiation|
| POST   | `/:callback/negotiations/:consumerPid/offers`      | 200                    |
| POST   | `/:callback/negotiations/:consumerPid/agreement`   | 200                    |
| POST   | `/:callback/negotiations/:consumerPid/events`      | 200                    |
| POST   | `/:callback/negotiations/:consumerPid/termination` | 200                    |

Callback rules:

- Callback paths are resolved relative to `callbackAddress`.
- The HTTPS scheme MUST be supported for `callbackAddress`.
- Implementations MAY support additional schemes.
- Implementations should handle callback base URLs with or without a trailing slash.
- `POST /negotiations/offers` is a Provider-initiated negotiation start; the Consumer returns HTTP 201.

# Message and decision boundaries

## Normative message rules

### ContractRequestMessage (sent by Consumer)

- MUST include an `offer` property, which MUST have an `@id`.
- `offer.@id` MUST generally refer to an Offer contained in a Catalog. If the Provider is not aware of it, it MUST return an error.
- If the message does NOT include `providerPid`, a new Contract Negotiation MUST be created and the Provider selects `providerPid`.
- If the message DOES include `providerPid`, it MUST be associated with an existing negotiation.
- `offer.obligation` and `offer.permission` express the terms at which the Consumer would accept the Offer.
- The Offer inside `ContractRequestMessage` MUST have a `target` attribute (the Dataset identifier).
- Rules inside the Offer (permissions, prohibitions, obligations) MUST NOT have a `target` attribute — this prevents inconsistencies with ODRL compact policy inferencing.
- `callbackAddress` MUST be a URL. If the address is not understood, the Provider MUST return an **unrecoverable** error.

### ContractOfferMessage (sent by Provider)

- If the message does NOT include `consumerPid`, a new Contract Negotiation MUST be created on Consumer side.
- If the message DOES include `consumerPid`, it MUST be associated with an existing negotiation.
- The Offer inside `ContractOfferMessage` MUST have a `target` attribute. Rules inside it MUST NOT.
- If the message initiates a Contract Negotiation (no `consumerPid`), it MUST contain a `callbackAddress` property.
  If the address is not understood, the Consumer MUST return an **unrecoverable** error.

### ContractAgreementMessage (sent by Provider)

- MUST contain a `consumerPid` and a `providerPid`.
- MUST contain a complete `agreement` object, which MUST include:
  - `timestamp` — XSD DateTime type.
  - `assigner` — dataspace-specific unique identifier of the Provider party.
  - `assignee` — dataspace-specific unique identifier of the Consumer party.
  - `target` — the Dataset identifier. Rules inside the Agreement MUST NOT have a `target` attribute.

### ContractAgreementVerificationMessage (sent by Consumer)

- MUST contain a `consumerPid` and a `providerPid`.
- A Provider MUST respond with an error if the Agreement cannot be validated or is incorrect.

### ContractNegotiationEventMessage (sent by Consumer or Provider)

- MUST contain a `consumerPid` and a `providerPid`.
- When sent by Provider with `eventType: FINALIZED` → state transitions to `FINALIZED`; Dataset is now accessible.
- When sent by Consumer with `eventType: ACCEPTED` → state transitions to `ACCEPTED`.
- **It is an error for a Consumer to send `eventType: FINALIZED` to the Provider.**
- **It is an error for a Provider to send `eventType: ACCEPTED` to the Consumer.**
- Neither party MUST send an event after the state machine has entered a terminal state.

### ContractNegotiationTerminationMessage (sent by Consumer or Provider)

- MUST contain a `consumerPid` and a `providerPid`.
- MAY be sent at any non-terminal state without providing a reason.
- MAY include a description to help the receiver.
- If the receiver responds with an error, the sender MAY choose to ignore it.

## Normative (spec-mandated)

- Message shapes and required properties (see above).
- State transitions.
- Endpoint paths and HTTP semantics.
- Callback path construction.

## Implementation-specific

- Offer and counter-offer strategy.
- Policy evaluation details.
- Callback retry behavior.
- Human approval or automatic acceptance policy.
- Audit and governance behavior beyond the DSP contract flow.

# Repository hints for TRUE Connector

- Primary module: `negotiation`
- Likely touchpoints:
  - `rest/protocol` negotiation controllers
  - `NegotiationSerializer`
  - negotiation models, services, and policy evaluation code
- Keep protocol payload handling inside the serializer and protocol layer instead of ad hoc JSON mapping.

# Output expectations

When using this skill:

1. Name the exact negotiation state or endpoint involved.
2. Explain whether the change affects protocol compliance or only implementation policy.
3. Point to negotiation module files likely involved.
4. Recommend tests for initiation, callback flow, invalid transition handling, unauthorized access, and termination.
