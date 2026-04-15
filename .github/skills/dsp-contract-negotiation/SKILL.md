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

- `GET /negotiations/:providerPid`
- `POST /negotiations/request`
- `POST /negotiations/:providerPid/request`
- `POST /negotiations/:providerPid/events`
- `POST /negotiations/:providerPid/agreement/verification`
- `POST /negotiations/:providerPid/termination`

Important semantics:

- Initiating `POST /negotiations/request` returns HTTP 201 with a `ContractNegotiation` body.
- Successful follow-up state-changing POSTs return HTTP 200.
- Invalid state transitions MUST return HTTP 400 with a `Contract Negotiation Error`.
- Missing negotiations MUST return HTTP 404.
- Unauthorized access MUST return HTTP 404.

# Consumer callback bindings

- `GET /:callback/negotiations/:consumerPid`
- `POST /negotiations/offers`
- `POST /:callback/negotiations/:consumerPid/offers`
- `POST /:callback/negotiations/:consumerPid/agreement`
- `POST /:callback/negotiations/:consumerPid/events`
- `POST /:callback/negotiations/:consumerPid/termination`

Callback rules:

- Callback paths are resolved relative to `callbackAddress`.
- The HTTPS scheme MUST be supported for `callbackAddress`.
- Implementations MAY support additional schemes.
- Implementations should handle callback base URLs with or without a trailing slash.

# Message and decision boundaries

Normative:

- message shapes
- state transitions
- endpoint paths and HTTP semantics
- callback path construction

Implementation-specific:

- offer and counter-offer strategy
- policy evaluation details
- callback retry behavior
- human approval or automatic acceptance policy
- audit and governance behavior beyond the DSP contract flow

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
