---
name: dsp-transfer-process
description: Transfer Process Protocol and HTTPS Binding guidance for Dataspace Protocol 2025-1. Use when implementing or reviewing transfer requests, start or completion flow, push or pull handling, callback delivery, idempotency, or /transfers endpoints.
---

# DSP Transfer Process

# Purpose

Use this skill for DSP 2025-1 transfer lifecycle behavior.

Load `dsp-foundations` together with this skill when possible.

# Use this skill when

- A task touches `/transfers` endpoints.
- A task changes transfer lifecycle state handling.
- A task involves push or pull transfers, finite or non-finite data, callback delivery, or `dataAddress`.
- A task reviews transfer-related DSP compliance.

# Instructions for Copilot

1. Treat Sections 9 and 10 of DSP 2025-1 as the main reference, plus Section 4 for common requirements.
2. Keep the distinction between control plane and data plane explicit. DSP governs transfer process control, not the actual data protocol on the wire.
3. Do not invent new transfer states, paths, or message fields.
4. Call out implementation-specific choices like transfer format support, idempotency retention, retry policy, and callback validation.

# Transfer concepts

- Push transfer: the Provider sends data to a Consumer endpoint.
- Pull transfer: the Consumer retrieves data from a Provider endpoint.
- Finite data completes.
- Non-finite data may continue until explicitly terminated.

# State model

Predefined transfer process states are:

- REQUESTED
- STARTED
- SUSPENDED
- COMPLETED
- TERMINATED

## State machine transitions (normative)

From the DSP state machine (`transfer-process-state-machine.puml`). `C` = Consumer initiates, `P` = Provider initiates.

| From | To | Initiator | Message |
|---|---|---|---|
| _(initial)_ | REQUESTED | C | `TransferRequestMessage` |
| REQUESTED | STARTED | **P only** | `TransferStartMessage` |
| REQUESTED | TERMINATED | C/P | `TransferTerminationMessage` |
| STARTED | COMPLETED | P/C | `TransferCompletionMessage` |
| STARTED | SUSPENDED | P/C | `TransferSuspensionMessage` |
| STARTED | TERMINATED | P/C | `TransferTerminationMessage` |
| SUSPENDED | STARTED | **P/C** | `TransferStartMessage` |
| SUSPENDED | TERMINATED | P/C | `TransferTerminationMessage` |

Key rules:
- The **initial start** (`REQUESTED → STARTED`) is **Provider-only**. Consumer cannot send it.
- **Restart from SUSPENDED** (`SUSPENDED → STARTED`) can be initiated by **either party**.
- All other multi-party transitions (COMPLETED, SUSPENDED, TERMINATED from STARTED) allow either party.

# Provider-side HTTPS bindings

| Method | Path                                  | HTTP response       |
|--------|---------------------------------------|---------------------|
| GET    | `/transfers/:providerPid`             | 200 TransferProcess |
| POST   | `/transfers/request`                  | 201 TransferProcess |
| POST   | `/transfers/:providerPid/start`       | 200                 |
| POST   | `/transfers/:providerPid/completion`  | 200                 |
| POST   | `/transfers/:providerPid/termination` | 200                 |
| POST   | `/transfers/:providerPid/suspension`  | 200                 |

Important semantics:

- Initiating `POST /transfers/request` returns HTTP 201 with a `TransferProcess` body.
- Successful follow-up state-changing POSTs return HTTP 200.
- Invalid state transitions MUST return HTTP 400 with a `TransferError` body.
- Missing transfer processes MUST return HTTP 404.
- Unauthorized access MUST return HTTP 404.
- Callback URLs in transfer messages MUST support HTTPS.
- `POST /transfers/:providerPid/start` is used by a **Consumer** to restart a previously suspended
  transfer. The Provider sends the **initial** start via the Consumer callback.
- `POST /:callback/transfers/:consumerPid/start` is used by the **Provider** to indicate the start of
  a Transfer Process — both the initial start and a Provider-initiated restart from `SUSPENDED`.
- Either party MAY initiate a restart from `SUSPENDED`: Consumer via the Provider-side `/start` endpoint,
  Provider via the Consumer callback `/start` endpoint.

# Consumer callback bindings

| Method | Path                                            | HTTP response |
|--------|-------------------------------------------------|---------------|
| POST   | `/:callback/transfers/:consumerPid/start`       | 200           |
| POST   | `/:callback/transfers/:consumerPid/completion`  | 200           |
| POST   | `/:callback/transfers/:consumerPid/termination` | 200           |
| POST   | `/:callback/transfers/:consumerPid/suspension`  | 200           |

Callback rules:

- Callback paths are resolved relative to `callbackAddress`.
- Implementations MAY choose the `:callback` segment freely.
- Implementations should handle callback base URLs with or without a trailing slash.

# Critical message rules

## TransferRequestMessage (sent by Consumer)

- `consumerPid` — MUST refer to the Consumer-side transfer identifier.
- `agreementId` — MUST refer to an existing Agreement between Consumer and Provider.
- `format` — MUST be a format specified by a Distribution in the Provider's Catalog for the Dataset.
- `dataAddress` — MUST only be provided if the `format` requires a **push** transfer.
  - MUST contain transport-specific endpoint properties for pushing data.
  - MAY include an `endpoint` and temporary authorization via `endpointProperties`.
- `callbackAddress` — MUST be a URI. If not understood, the Provider MUST return an **unrecoverable** error.
- Providers SHOULD implement idempotent behavior keyed by `consumerPid`.
  - If a request for the given `consumerPid` was already received from the same Consumer, the Provider SHOULD respond
    with an appropriate `TransferStartMessage`.

## TransferStartMessage (sent by Provider; Consumer uses Provider `/start` endpoint to signal restart)

- The protocol spec lists `TransferStartMessage` as **sent by Provider**. In the HTTPS binding:
  - Provider sends it to the **Consumer callback** (`POST /:callback/transfers/:consumerPid/start`)
    for the initial start and for Provider-initiated restarts from `SUSPENDED`.
  - Consumer sends it to the **Provider endpoint** (`POST /transfers/:providerPid/start`)
    to signal a Consumer-initiated restart from `SUSPENDED`. The Provider processes it and
    transitions `SUSPENDED → STARTED`.
- `dataAddress` MUST be provided when the transfer is a **pull** transfer.
  - Contains a transport-specific endpoint address for the Consumer to retrieve data.
  - `endpointType` property signals the kind of transport and determines required `endpointProperties`.
- `endpointProperties` MAY contain:
  - `authorization` — opaque authorization token the client MAY present when accessing the endpoint.
  - `authType` — auth token type, e.g., `"bearer"`. If present, MAY be used with transport rules for presenting the token.

## TransferSuspensionMessage (sent by Consumer or Provider)

- Either party MAY send this to temporarily suspend the Transfer Process.
- MAY include a reason description.

## TransferCompletionMessage (sent by Consumer or Provider)

- Either party MAY send this when data transfer is complete.
- Some implementations MAY optimize by signalling completion at the wire protocol level, in which case this message
  need not be sent.

## TransferTerminationMessage (sent by Consumer or Provider)

- MAY be sent at any non-terminal state.
- If termination is due to an error, the sender MAY include error information.

# Implementation-specific decisions

- Supported transfer formats and endpoint types.
- Push versus pull selection logic.
- Callback retry and timeout policy.
- Callback URL validation and SSRF protections.
- Idempotency retention duration and archival strategy.
- Handling of non-finite transfers and suspension or restart policy.
- `endpointProperties` fields beyond `authorization` and `authType`.

# Repository hints for TRUE Connector

- Primary module: `data-transfer`
- Likely touchpoints:
  - protocol controllers in the transfer module
  - `TransferSerializer`
  - `DataTransferStrategyFactory`
  - transfer process models and strategy implementations
- The repository currently centralizes strategy resolution in `DataTransferStrategyFactory`.

# Output expectations

When using this skill:

1. Explain whether the change affects request shape, state transition handling, callback flow, or data-plane integration points.
2. Point to likely files in `data-transfer` and related modules.
3. Identify any implementation-specific decision that still needs to be made.
4. Recommend tests for request initiation, callback delivery, invalid transitions, idempotency, and push or pull behavior.
