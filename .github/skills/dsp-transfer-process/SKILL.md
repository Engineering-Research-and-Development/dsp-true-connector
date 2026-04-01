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

# Provider-side HTTPS bindings

- `GET /transfers/:providerPid`
- `POST /transfers/request`
- `POST /transfers/:providerPid/start`
- `POST /transfers/:providerPid/completion`
- `POST /transfers/:providerPid/termination`
- `POST /transfers/:providerPid/suspension`

Important semantics:

- Initiating `POST /transfers/request` returns HTTP 201 with a `TransferProcess` body.
- Successful follow-up state-changing POSTs return HTTP 200.
- Callback URLs in transfer messages MUST support HTTPS.

# Consumer callback bindings

- `POST /:callback/transfers/:consumerPid/start`
- `POST /:callback/transfers/:consumerPid/completion`
- `POST /:callback/transfers/:consumerPid/termination`
- `POST /:callback/transfers/:consumerPid/suspension`

Callback rules:

- Callback paths are resolved relative to `callbackAddress`.
- Implementations MAY choose the `:callback` segment freely.
- Implementations should handle callback base URLs with or without a trailing slash.

# Critical message rules

- `TransferRequestMessage` includes `consumerPid`, `agreementId`, `format`, and `callbackAddress`.
- In a transfer request, `dataAddress` MUST only be provided if the format requires a push transfer.
- In a transfer start message for a pull transfer, `dataAddress` MUST be provided and its endpoint information is interpreted according to the transfer format or profile.
- Providers SHOULD implement idempotent behavior for transfer requests keyed by `consumerPid`.
- The idempotency retention window is implementation-specific.

# Implementation-specific decisions

- Supported transfer formats and endpoint types.
- Push versus pull selection logic.
- Callback retry and timeout policy.
- Callback URL validation and SSRF protections.
- Idempotency retention duration and archival strategy.
- Handling of non-finite transfers and suspension or restart policy.

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
