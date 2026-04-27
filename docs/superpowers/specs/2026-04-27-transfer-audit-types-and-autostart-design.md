# Transfer Audit Types and HTTP_PUSH Auto-Start Design

**Date:** 2026-04-27  
**Status:** Approved

## Problem

Two related issues exist in the data-transfer module:

1. **Audit type misuse**: `DataTransferAPIService` (UI-triggered API layer) publishes `PROTOCOL_TRANSFER_*` audit events. These should only appear in `AbstractDataTransferService` (DSP protocol layer), which receives messages from the opposite party. The wrong prefixes make the audit log misleading and break the protocol/API boundary contract.

2. **HTTP_PUSH initial-start gap**: When the provider sends the initial `TransferStartMessage` for an HTTP_PUSH transfer, the provider does not auto-trigger its upload to the consumer's S3. Resume (from SUSPENDED) already auto-triggers correctly; the initial start (from REQUESTED) does not.

3. **Single misuse in protocol layer**: `AbstractDataTransferService.suspendDataTransfer()` uses `TRANSFER_PAUSED` instead of `PROTOCOL_TRANSFER_SUSPENDED`.

## Scope

Files in scope:
- `tools/src/main/java/it/eng/tools/event/AuditEventType.java`
- `data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java`
- `data-transfer/src/main/java/it/eng/datatransfer/service/AbstractDataTransferService.java`

## Layer Boundary Rule

| Layer | Class | Audit prefix |
|---|---|---|
| DSP protocol (receives DSP messages from peer) | `AbstractDataTransferService` | `PROTOCOL_TRANSFER_*` |
| API / UI-initiated (sends messages to peer) | `DataTransferAPIService` | `TRANSFER_*` (non-protocol) |
| Internal download events | `DataTransferAPIService.downloadData()` | `TRANSFER_*` (already correct) |

## Change 1: New `TRANSFER_*` AuditEventType Values

Add to `AuditEventType.java`, grouped with the existing non-protocol transfer types:

```java
TRANSFER_NOT_FOUND("Transfer not found"),
TRANSFER_STATE_TRANSITION_ERROR("State transition invalid"),
TRANSFER_REQUESTED("Transfer requested"),
TRANSFER_STARTED("Transfer started"),
TRANSFER_SUSPENDED("Transfer suspended"),
TRANSFER_TERMINATED("Transfer terminated"),
```

Existing types reused without change:
- `TRANSFER_COMPLETED` — for both internal download completion and `completeTransfer()` success
- `TRANSFER_FAILED` — for `completeTransfer()` failure and generic download failures

## Change 2: `DataTransferAPIService` — Audit Type Replacements

| Method | Old type | New type |
|---|---|---|
| `findTransferProcessById()` | `PROTOCOL_TRANSFER_NOT_FOUND` | `TRANSFER_NOT_FOUND` |
| `stateTransitionCheck()` (private) | `PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR` | `TRANSFER_STATE_TRANSITION_ERROR` |
| `requestTransfer()` success | `PROTOCOL_TRANSFER_REQUESTED` | `TRANSFER_REQUESTED` |
| `requestTransfer()` failure | `PROTOCOL_TRANSFER_REQUESTED` | `TRANSFER_REQUESTED` |
| `startTransfer()` success | `PROTOCOL_TRANSFER_STARTED` | `TRANSFER_STARTED` |
| `startTransfer()` failure | `PROTOCOL_TRANSFER_STARTED` | `TRANSFER_STARTED` |
| `completeTransfer()` success | `PROTOCOL_TRANSFER_COMPLETED` | `TRANSFER_COMPLETED` |
| `completeTransfer()` failure | `PROTOCOL_TRANSFER_COMPLETED` | `TRANSFER_FAILED` |
| `suspendTransfer()` success | `PROTOCOL_TRANSFER_SUSPENDED` | `TRANSFER_SUSPENDED` |
| `suspendTransfer()` failure | `PROTOCOL_TRANSFER_SUSPENDED` | `TRANSFER_SUSPENDED` |
| `terminateTransfer()` success | `PROTOCOL_TRANSFER_TERMINATED` | `TRANSFER_TERMINATED` |
| `terminateTransfer()` failure | `PROTOCOL_TRANSFER_TERMINATED` | `TRANSFER_TERMINATED` |
| `terminateTransferWithReason()` | `PROTOCOL_TRANSFER_TERMINATED` | `TRANSFER_TERMINATED` |
| `downloadData()` generic failure branch | role: `ROLE_PROTOCOL` | role: `ROLE_API` |

Unchanged: `TRANSFER_PAUSED`, `TRANSFER_RESUMED`, `TRANSFER_URL_EXPIRED` in `downloadData().whenComplete()` are already correct.

## Change 3: `AbstractDataTransferService` — Protocol Audit Fix

In `suspendDataTransfer()`:
- Replace `TRANSFER_PAUSED` → `PROTOCOL_TRANSFER_SUSPENDED`

All other `PROTOCOL_TRANSFER_*` usages in this class are already correct and unchanged.

`TRANSFER_PAUSED` remains in use in `DataTransferAPIService.downloadData().whenComplete()` (cancellation signal branch) — this is an internal download event and stays as-is.

## Change 4: HTTP_PUSH Initial-Start Auto-Trigger

In `DataTransferAPIService.startTransfer()`, after a successful response, add an auto-trigger block for the initial start case alongside the existing Case A (resume) block:

```
After successful response:

if (previous state == REQUESTED) {
  if (role == PROVIDER && format == HTTP_PUSH) {
    CompletableFuture.runAsync(() -> {
      try {
        downloadData(transferProcessId)
          .exceptionally(err -> { log.error(...); return null; });
      } catch (Exception e) {
        log.error(...);
      }
    });
    // Audit: log initial upload auto-triggered
  }
}

if (previous state == SUSPENDED) {
  // Existing Case A logic — no changes
}
```

### Why only HTTP_PUSH + PROVIDER?

- **HTTP_PUSH + PROVIDER initial start**: Provider is the uploader. After confirming the consumer accepted the start, the provider begins uploading. No receiver-side trigger handles this.
- **HTTP_PULL + PROVIDER initial start**: Consumer auto-downloads when `AbstractDataTransferService.startDataTransfer()` fires `AutoTransferDownloadEvent` on the consumer (receiver) side. No sender-side trigger needed.
- **SUSPENDED resume**: Already handled by existing Case A logic.

## Change 5: Error Message Clarification

Two misleading error messages are updated to accurately describe the restriction:

**`DataTransferAPIService.startTransfer()`** (line ~307):
- Old: `"State transition aborted, consumer can not transit from REQUESTED to STARTED"`
- New: `"Only the provider can send the initial TransferStartMessage. Consumer role is not allowed to start from REQUESTED state."`

**`AbstractDataTransferService.startDataTransfer()`** (line ~240):
- Old: `"State transition aborted, consumer can not transit from REQUESTED to STARTED"`
- New: `"Only the provider can send the initial TransferStartMessage. Start from REQUESTED state is not allowed when the local role is PROVIDER (sender is CONSUMER)."`

## Testing

Existing test coverage:
- `DataTransferAPIServiceTest` — unit tests for `startTransfer()`, `suspendTransfer()`, etc.; verify audit event type assertions are updated
- `AbstractDataTransferService`-based tests — verify `suspendDataTransfer()` now emits `PROTOCOL_TRANSFER_SUSPENDED`
- `DataTransferSuspendResumeIT` — integration test; no behavioral change expected, audit assertions may need updating

New test scenarios required:
- HTTP_PUSH initial-start auto-trigger: verify `downloadData()` is called when provider sends initial start (REQUESTED state, HTTP_PUSH format)

## Out of Scope

- Negotiation audit types (not touched)
- Catalog audit types (not touched)
- `policyCheck()` uses `PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DISABLED` — this is a cross-domain call and left unchanged
