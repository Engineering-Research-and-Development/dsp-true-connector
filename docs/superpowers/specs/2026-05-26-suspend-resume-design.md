# Suspend / Resume Transfer Design

**Date:** 2026-05-26  
**Branch baseline:** `feature/suspend-resume`  
**Related PR / source material:** [#241](https://github.com/Engineering-Research-and-Development/dsp-true-connector/pull/241), `docs/superpowers/handoffs/2026-05-25-suspend-resume-implementation-handoff.md` on `feature/suspend-resume-transfers`

## Problem

The older suspend/resume handoff assumes the transfer logic still runs inside the control-plane modules and that multipart checkpoint state lives in `data-transfer`. On the current branch, that is no longer true: the control plane owns DSP `TransferProcess` lifecycle, but the physical HTTP-PULL / HTTP-PUSH transfer execution already lives in the dataplane modules and `s3-support`.

The feature we want is still the same:

1. keep the existing public control-plane behavior where a suspended transfer is resumed through `startTransfer(...)`
2. support true mid-flight suspend/resume for HTTP-PULL and HTTP-PUSH
3. resume from saved multipart state instead of re-downloading everything from byte 0
4. allow resume only from the same party that initiated the current suspension

The design therefore needs to translate the earlier multipart-resume ideas into the current dataplane architecture instead of reintroducing them into the control plane.

## Goals

- Preserve the current public control-plane contract:
  - suspend via `suspendTransfer(...)`
  - resume via the existing `startTransfer(...)` path
- Add real dataplane suspend/resume underneath that contract for HTTP-PULL and HTTP-PUSH
- Persist enough multipart-upload state to resume safely after intentional suspension
- Avoid data corruption, skipped bytes, or false-success resumes
- Enforce that only the party who initiated suspend may resume the transfer
- Keep control-plane and dataplane responsibilities clearly separated

## Non-goals

- Add a new public `resumeTransfer(...)` control-plane/admin API
- Add new dataplane-to-control-plane `suspended` or `resumed` callback endpoints in this iteration
- Auto-resume transfers after application restart
- Rebuild the design around `TransferArtifactState` in `data-transfer`
- Broad cleanup of unrelated legacy code

## Decisions

| Question | Decision |
| --- | --- |
| Public resume action | Keep resume exposed through the existing control-plane `startTransfer(...)` flow |
| Where checkpoint state lives | Dataplane-owned persistence, not `data-transfer` |
| Multipart implementation ownership | `s3-support` owns multipart upload/resume mechanics; dataplane owns checkpoint/session lifecycle |
| Suspend completion signal | Dataplane suspend endpoint must only return success after checkpoint is durable and new multipart work is quiesced |
| Resume authority | Reuse the existing `consumer` / `provider` role values to persist who initiated suspend, and allow resume only from that side while the transfer is `SUSPENDED` |
| Resume fallback when saved upload is gone | Clear checkpoint state and restart from byte 0 inside the dataplane |
| Source endpoint reuse | Never trust stale source URLs from checkpoint state; obtain fresh source data from the normal control-plane start flow |
| Java platform usage | Java 21 standard features are available; virtual threads are allowed where they simplify blocking orchestration, but preview features are out of scope |

## Architecture Overview

### Responsibility split

**Control plane**

- Owns DSP `TransferProcess` lifecycle and public admin/API behavior
- Continues to expose:
  - `suspendTransfer(...)` as the public pause action
  - `startTransfer(...)` as the public resume action
- Owns peer-to-peer DSP protocol messages (`TransferStartMessage`, `TransferSuspensionMessage`, `TransferCompletionMessage`, `TransferTerminationMessage`)
- Tracks dataplane lifecycle mirror state in `TransferProcess.dataFlowState`
- Tracks resume ownership in `TransferProcess.suspendedBy`, which stores one of the existing role values already used by `TransferProcess.role`
- Keeps `isDownloadInProgress` as a local execution/UX flag, not as the source of resume truth

**Dataplane**

- Owns physical HTTP-PULL / HTTP-PUSH execution
- Owns resumable multipart checkpoint/session state
- Implements true suspend/resume behavior for running transfers
- Decides whether a resume request can continue an existing multipart upload or must restart cleanly

### Module scope

| Area | Responsibility in this design |
| --- | --- |
| `data-transfer` | Route suspend to dataplane pause; keep public resume via `startTransfer(...)`; persist and enforce `suspendedBy` using the existing role values; decide `dataPlaneClient.start(...)` vs `dataPlaneClient.resume(...)` during actual download kickoff |
| `data-plane/data-plane-core` | Persist resumable dataplane session/checkpoint records and enforce lifecycle semantics |
| `data-plane/data-plane-http-pull` | Implement true suspend/resume for consumer-side pull transfers |
| `data-plane/data-plane-http-push` | Implement true suspend/resume for provider-side push transfers |
| `s3-support` | Provide checkpoint-aware multipart upload/resume primitives shared by both dataplane protocols |

## State and Flow

The design keeps DSP transfer state and dataplane execution state related but distinct.

- `TransferProcess.state` remains the DSP lifecycle (`REQUESTED`, `STARTED`, `SUSPENDED`, `COMPLETED`, `TERMINATED`)
- `TransferProcess.dataFlowState` remains the control-plane mirror of dataplane execution (`STARTED`, `SUSPENDED`, `COMPLETED`, `TERMINATED`)
- `TransferProcess.suspendedBy` records which existing DSP role value (`consumer` or `provider`) initiated the current suspension

### Resume authority

Resume ownership is tracked explicitly on `TransferProcess`.

- No new role type or enum is introduced; the design reuses the same `consumer` / `provider` values already stored in `TransferProcess.role`
- On the connector that initiates suspend via `DataTransferAPIService.suspendTransfer(...)`, set `suspendedBy = transferProcess.role`
- On the peer connector that receives the protocol suspension in `AbstractDataTransferService.suspendDataTransfer(...)`, set the same logical initiator role without changing the DSP message schema:
  - if local role is `consumer`, the initiator was `provider`
  - if local role is `provider`, the initiator was `consumer`
- `suspendedBy` stays populated while the transfer remains `SUSPENDED`
- Clear `suspendedBy` after a successful resume transition back to `STARTED`, and on terminal transitions

This keeps the rule local and explicit: the initiator is the only side allowed to emit the resume-start action, while the other side may only receive it.

### Suspend flow

1. A user calls control-plane `suspendTransfer(...)`.
2. The control plane no longer rejects the request just because `isDownloadInProgress=true`.
3. The control plane identifies the active dataplane flow and calls `dataPlaneClient.suspend(...)`.
4. The dataplane pauses the running transfer, flushes checkpoint state, and only then returns success.
5. The control plane records `suspendedBy` for the current suspension and updates its local dataplane mirror state to `SUSPENDED`.
6. The control plane sends the normal DSP `TransferSuspensionMessage` to the peer.
7. On the receiving side, `AbstractDataTransferService.suspendDataTransfer(...)` persists the same logical initiator role on its local `TransferProcess`.
8. Once the peer accepts, both sides remain aligned on `TransferProcess.state = SUSPENDED` and on who is allowed to resume.

### Resume flow

1. A user resumes the transfer through the existing control-plane `startTransfer(...)` path.
2. `DataTransferAPIService.startTransfer(...)` checks `suspendedBy` when the current state is `SUSPENDED`; if the local role is not the recorded initiator, the request is rejected and the transfer stays suspended.
3. DSP state handling remains unchanged at the public API level.
4. When the peer receives the resulting `TransferStartMessage`, `AbstractDataTransferService.startDataTransfer(...)` applies the symmetric rule: for a `SUSPENDED` transfer, it only accepts the message when the local role is **not** `suspendedBy`, because the peer sender must be the recorded initiator.
5. When the actual local data movement is about to begin, the control plane chooses:
   - `dataPlaneClient.start(...)` for a fresh execution
   - `dataPlaneClient.resume(...)` when the dataplane mirror shows a resumable suspended flow
6. The dataplane resumes from checkpoint state if the saved multipart upload is still valid.
7. Existing dataplane callbacks (`started`, `completed`, `errored`) remain the only dataplane-to-control-plane callbacks used in this iteration.

### Where the start-vs-resume decision happens

The start-vs-resume decision belongs in the existing control-plane execution trigger, not in the public API surface:

- consumer HTTP-PULL auto/manual download path
- provider HTTP-PUSH start-then-download path

That keeps `startTransfer(...)` as the public contract while letting the implementation switch between dataplane `start(...)` and `resume(...)` internally.

## Java 21 Considerations

The repository root now builds with Java 21, so the implementation may use Java 21 **standard** features where they simplify the work.

- Virtual threads are allowed for blocking orchestration paths if they make the suspend/resume implementation easier to reason about
- This is most relevant for blocking HTTP/S3 coordination code, retry/rollback orchestration, or places where the current design would otherwise need dedicated platform-thread pools just to wait on blocking I/O
- Virtual threads are optional, not mandatory: use them only where they simplify control flow or cancellation semantics
- Preview features are out of scope for this iteration; no `--enable-preview` changes should be introduced as part of this work

This keeps the implementation free to use Java 21 concurrency improvements without turning the suspend/resume feature into a broader platform-modernization task.

## Checkpoint and Persistence Model

### Dataplane checkpoint entity

Create a dataplane-owned MongoDB entity named `DataFlowCheckpoint` in `data-plane-core`. It is keyed by `processId` and stores only the state required to resume the destination multipart upload safely.

| Field | Purpose |
| --- | --- |
| `processId` | Correlates checkpoint state to the control-plane `TransferProcess` |
| `dataFlowId` | Correlates checkpoint state to the dataplane `DataFlowEntity` |
| `transferType` | Distinguishes HTTP-PULL vs HTTP-PUSH handling |
| `tenantId` | Preserves tenant bucket resolution context |
| `uploadId` | Existing multipart upload identifier to continue on resume |
| `destinationBucket` | Bucket that owns the multipart upload |
| `destinationObjectKey` | Object key being assembled |
| `completedParts` | `Map<Integer, String>` of confirmed part number to ETag |
| `partSizes` | `Map<Integer, Long>` of confirmed part number to actual uploaded byte count |
| `confirmedBytes` | Highest contiguous confirmed byte cursor |
| `createdAt`, `updatedAt` | Audit/debug fields |

### Ownership rules

- `data-plane-core` owns persistence, retrieval, cleanup, and lifecycle of `DataFlowCheckpoint`
- `s3-support` owns multipart-upload algorithms and checkpoint callback hooks
- `data-plane-http-pull` and `data-plane-http-push` pass dataplane context plus source/destination inputs into shared checkpoint-aware upload logic

### Multipart resume rules

The checkpoint tracks only **destination multipart-upload progress**. It must not store or replay stale source URLs.

On resume:

1. the control plane provides fresh source transfer inputs through the normal `startTransfer(...)` / `downloadData(...)` flow
2. the dataplane loads `DataFlowCheckpoint`
3. the dataplane checks whether `uploadId` is still valid
4. if valid, multipart upload continues from the saved checkpoint
5. if invalid or expired, the dataplane clears multipart state and restarts from byte 0

### Cursor correctness

The resume cursor is computed from the highest **contiguous confirmed** part boundary, not from completion order. This is required because async multipart uploads can finish out of order.

The saved state therefore uses:

- `completedParts` for exact part-to-ETag mapping
- `partSizes` for exact byte accounting
- `confirmedBytes` as the derived contiguous byte cursor

This replaces any design that assumes append-order ETag lists or cumulative uploaded-byte callbacks are reliable under parallel completion.

### Relationship to existing `TransferArtifactState`

This design does **not** extend `TransferArtifactState` in `data-transfer`. That model is no longer on the active execution path for HTTP-PULL / HTTP-PUSH on the current branch. Any cleanup or removal of that legacy model is optional follow-up work and not part of the functional suspend/resume implementation.

## Error Handling and Rollback

### Suspend errors

Suspend must behave as:

1. pause local dataplane first
2. publish DSP suspension second

If dataplane suspend fails before the checkpoint is durable, the control plane:

- returns an error
- keeps `TransferProcess.state` in `STARTED`
- keeps `TransferProcess.dataFlowState` in `STARTED`

If dataplane suspend succeeds locally but the outbound DSP `TransferSuspensionMessage` fails, the control plane must:

1. attempt immediate dataplane resume
2. roll local mirror state back to `STARTED`
3. surface an error to the caller

If that rollback resume also fails, the system must persist the divergence clearly and surface manual intervention rather than reporting a false success.

### Resume errors

Resume failure handling is split into three cases:

| Case | Behavior |
| --- | --- |
| Resume requested by the non-initiator | Reject the request and keep the transfer `SUSPENDED` |
| Valid checkpoint and live `uploadId` | Continue the multipart upload |
| Checkpoint exists but `uploadId` is missing/expired | Clear multipart state and restart from byte 0 |
| Unrecoverable transfer error | Let dataplane emit the existing `errored` callback so control plane follows its normal termination path |

### Cleanup rules

Dataplane checkpoint/session state is deleted on:

- `COMPLETED`
- `TERMINATED`

Checkpoint/session state is retained only while the transfer is:

- actively `STARTED`
- intentionally `SUSPENDED`

### `isDownloadInProgress`

`isDownloadInProgress` remains a local control-plane execution/UX flag:

- it is set when a local dataplane start or resume is kicked off
- it is cleared immediately after a successful local dataplane suspend returns, and on dataplane `COMPLETED` / `TERMINATED`
- it is set again if suspend rollback must trigger an immediate local dataplane resume
- it is cleared when dataplane start/resume fails immediately

It must no longer be used as the reason to reject a suspend request.

### Startup behavior

This iteration stays conservative on crash recovery:

- keep the existing control-plane startup reset for stale `isDownloadInProgress=true`
- keep dataplane checkpoints if they still exist
- do not auto-resume transfers on startup

Resumption after restart remains an explicit operator/user action through the existing flow.

## Testing and Verification

### Unit tests

**`s3-support`**

- contiguous-byte cursor calculation with out-of-order async part completion
- resume using an existing multipart upload ID
- intentional cancellation does not abort multipart state needed for resume
- expired/missing upload ID forces a clean restart path

**`data-plane-http-pull` and `data-plane-http-push`**

- suspend while transfer is in flight
- resume from saved checkpoint
- restart from byte 0 when saved multipart upload is gone
- checkpoint cleanup on completion and termination

**`data-plane-core`**

- `STARTED -> SUSPENDED -> STARTED` lifecycle handling
- checkpoint repository/service behavior
- rollback behavior when suspend succeeded locally but DSP suspension failed

**`data-transfer`**

- suspend while `isDownloadInProgress=true` now routes to dataplane pause instead of being rejected
- `DataTransferAPIService.startTransfer(...)` rejects resume attempts when local role is not `suspendedBy`
- `AbstractDataTransferService.startDataTransfer(...)` rejects incoming resume attempts when local role equals `suspendedBy`
- `DataTransferAPIService.suspendTransfer(...)` and `AbstractDataTransferService.suspendDataTransfer(...)` persist the same logical initiator role on both connectors
- resume via public `startTransfer(...)` ultimately chooses dataplane `resume(...)` when the dataplane mirror is `SUSPENDED`
- fresh transfers still choose dataplane `start(...)`

### Integration coverage

Add at least these end-to-end scenarios:

1. **HTTP-PULL mid-flight suspend/resume**
   - start a large consumer download
   - suspend while bytes are moving
   - resume through the existing control-plane `startTransfer(...)`
   - verify artifact integrity and correct final state
2. **HTTP-PUSH mid-flight suspend/resume**
   - start a large provider push
   - suspend while bytes are moving
   - resume through the existing control-plane flow
   - verify artifact integrity and cleanup of temporary state
3. **Negative rollback case**
   - dataplane suspend succeeds locally
   - peer DSP suspension fails
   - control plane attempts rollback resume
   - final persisted state reflects success or explicit intervention-needed failure, never false success
4. **Expired multipart upload fallback**
   - checkpoint exists
   - saved `uploadId` is no longer valid
   - resumed transfer restarts cleanly from byte 0 and completes correctly

### Verification command

The canonical repository verification remains:

```bash
mvn clean verify
```

Focused development should add or update tests in the affected modules before running the full repository verification.

## Out of Scope

- New public resume API on the control plane
- New dataplane `suspended` / `resumed` control-plane callback endpoints
- Automatic resume after restart
- Cross-transfer batching or scheduling changes
- Removal of all legacy checkpoint-related code outside the new dataplane path

## Summary

The key design move is simple: keep the public control-plane contract exactly where the current branch expects it, but move true suspend/resume correctness fully into the dataplane execution path. That preserves the current DSP-facing behavior while making mid-flight pause/resume real, resumable, and safe for both HTTP-PULL and HTTP-PUSH.
