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
| Resume access material | Reuse the existing presigned URL or push credentials already stored on the suspended transfer; if they are no longer valid, terminate instead of minting fresh ones |
| Startup crash recovery | Crash-interrupted local executions reconcile to `SUSPENDED` when resumable; otherwise they transition to `TERMINATED` with reason `unrecoverable error, start a new data transfer` |
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

### Cross-branch repository scope

Implementation scope also includes porting the relevant repository-level changes already prepared on `feature/suspend-resume-transfers`, but adapting them to the current branch rather than copying them blindly.

| Area | Required outcome |
| --- | --- |
| `.github/skills/update-changelog/SKILL.md` | Add the new `update-changelog` skill from `feature/suspend-resume-transfers` |
| `.github/skills/github-actions-ci-cd-best-practices/SKILL.md` | Sync the updated CI/CD skill content from `feature/suspend-resume-transfers`, including the expanded TRUE Connector Newman/E2E guidance |
| `.github/instructions/github-actions-ci-cd-best-practices.instructions.md` | Sync the matching instruction update so workflow guidance and the skill stay aligned |
| `.github/workflows/build.yml`, `develop.yml`, `release.yml`, `security.yml` | Port the relevant workflow changes from `feature/suspend-resume-transfers`, but reconcile them against this branch's current behavior and repository state instead of treating the other branch as authoritative line-for-line |

The workflow port must be **adapted** to this branch:

- keep this branch's Java 21 reality in mind instead of mechanically copying any older workflow runtime settings from the reference branch
- preserve the suspend/resume behavior defined in this spec
- preserve this branch's current architectural direction and only copy the workflow ideas that still fit

For GitHub Actions / Newman coverage specifically, the implementation plan must include updating the existing workflow-based API/E2E checks so they reflect the suspend/resume work on this branch. Based on the reference branch, that includes:

- using generated large test data where timing-sensitive suspend/resume coverage depends on transfers lasting long enough
- updating workflow jobs that exercise transfer flows to cover the suspend/resume and expiry scenarios added on this branch
- carrying over improved failure diagnostics and cleanup behavior where still relevant
- carrying over workflow-side verification of presigned URL downloads where still relevant
- reviewing the workflow set as a whole (`build`, `develop`, `release`, and removal/retention of `security`) with the current branch in mind

This is still design scope only: the implementation should study `feature/suspend-resume-transfers` as the reference, then re-apply the intent of those skill/workflow/test changes on top of the current branch.

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
5. Once the `STARTED` transition succeeds, the control plane automatically kicks off the local dataplane work; there is no separate manual download step in the suspend/resume flow.
6. For that automatic dataplane kickoff, the control plane reuses the existing access material already stored on the suspended transfer:
   - HTTP-PULL reuses the existing presigned URL already present in the transfer's data address
   - HTTP-PUSH reuses the existing destination credentials already present in the transfer's data address
   - no fresh presigned URL or new push credentials are minted as part of resume
7. The control plane then chooses:
   - `dataPlaneClient.start(...)` for a fresh execution
   - `dataPlaneClient.resume(...)` when the dataplane mirror shows a resumable suspended flow
8. The dataplane resumes from checkpoint state if the saved multipart upload is still valid.
9. Existing dataplane callbacks (`started`, `completed`, `errored`) remain the only dataplane-to-control-plane callbacks used in this iteration.

### Where the start-vs-resume decision happens

The choice is **not** made by adding a new public resume endpoint or a new `resume=true` flag to `startTransfer(...)`. It is made in the existing automatic control-plane execution handoff that runs immediately after the transfer successfully reaches `STARTED`.

- **HTTP-PULL:** the decision happens in the consumer-side automatic handoff triggered after `AbstractDataTransferService.startDataTransfer(...)` successfully persists `STARTED` and publishes `AutoTransferDownloadEvent`
- **HTTP-PUSH:** the decision happens in the provider-side automatic start-then-download chain in `AutomaticDataTransferService.processStart(...)`, which calls `processDownload(...)`, which then ends up in the `DataTransferAPIService.downloadData(...)` dataplane handoff after `STARTED` has succeeded

At that handoff point, the control plane inspects the suspended transfer and decides whether to call:

- `dataPlaneClient.start(...)` for a fresh local execution
- `dataPlaneClient.resume(...)` for a suspended local execution that is allowed to continue

That keeps `startTransfer(...)` as the public contract while making the start-vs-resume choice an internal automatic control-plane execution detail right before dataplane invocation.

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

The checkpoint tracks only **destination multipart-upload progress**. It does not own source access material.

On resume:

1. the control plane reuses the existing access material already persisted on the suspended transfer
2. the dataplane loads `DataFlowCheckpoint`
3. the dataplane checks whether `uploadId` is still valid
4. if valid, multipart upload continues from the saved checkpoint
5. if invalid or expired, the dataplane clears multipart state and restarts from byte 0

### Access material validity

Resume must not mint fresh access material.

- For HTTP-PULL, reuse the existing presigned URL already stored on the suspended transfer
- For HTTP-PUSH, reuse the existing destination credentials already stored on the suspended transfer
- If the existing HTTP-PULL URL is expired or the existing HTTP-PUSH credentials are no longer valid, the transfer becomes terminal

That terminal failure is surfaced through the normal control-plane termination path so the connector sends a `TransferTerminationMessage` instead of refreshing the access material and trying again.

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
| Existing HTTP-PULL presigned URL or HTTP-PUSH credentials are invalid/expired | Do not refresh them; terminate the transfer via the normal `TransferTerminationMessage` path |
| Valid checkpoint and live `uploadId` | Continue the multipart upload |
| Checkpoint exists but `uploadId` is missing/expired while the existing access material is still valid | Clear multipart state and restart from byte 0 |
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

Startup no longer just clears stale in-progress flags.

Any transfer found on startup with stale local execution markers (for example `isDownloadInProgress=true`) is treated as a crash-interrupted local execution.

For those crash-interrupted transfers:

1. load the local dataplane checkpoint/session state
2. verify whether the existing access material is still valid
3. if the transfer is resumable, reconcile it to `SUSPENDED`
4. if the transfer is not resumable, reconcile it to `TERMINATED`

### Startup reconciliation to `SUSPENDED`

If a crash-interrupted transfer still has:

- a usable checkpoint/session
- valid existing access material

then startup recovery should:

- clear `isDownloadInProgress`
- move the local `TransferProcess` from `STARTED` to `SUSPENDED`
- set `suspendedBy` to the local role performing the recovery suspend
- send the normal `TransferSuspensionMessage` so the peer converges to `SUSPENDED`

This keeps the post-crash path operator-friendly: the transfer does not silently stay stuck in `STARTED`, and it does not auto-resume. It becomes an explicit suspended transfer that the operator can restart through the normal resume flow.

### Startup reconciliation to `TERMINATED`

If a crash-interrupted transfer cannot be resumed because:

- no usable checkpoint/session exists
- the existing HTTP-PULL presigned URL is expired
- the existing HTTP-PUSH credentials are invalid
- or another unrecoverable restart condition is detected

then startup recovery should:

- clear `isDownloadInProgress`
- move the transfer to `TERMINATED`
- send the normal `TransferTerminationMessage`
- include an explicit reason such as: `unrecoverable error, start a new data transfer`

There is still no auto-resume on startup. After restart, transfers are either:

- safely recoverable and therefore operator-resumable via `SUSPENDED`
- or terminal and clearly terminated with a reason that tells the operator to create a new transfer

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
- resume reuses existing access material instead of minting a new presigned URL or new push credentials
- invalid existing access material terminates the transfer
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
- startup recovery converts crash-interrupted resumable local executions to `SUSPENDED`
- startup recovery converts non-resumable crash-interrupted local executions to `TERMINATED` with reason `unrecoverable error, start a new data transfer`
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
   - existing access material is still valid
   - resumed transfer restarts cleanly from byte 0 and completes correctly
5. **Expired access material is terminal**
   - suspended HTTP-PULL transfer uses an expired presigned URL, or suspended HTTP-PUSH transfer uses invalid destination credentials
   - resume does not mint a replacement URL or credentials
   - transfer terminates through the normal termination-message path
6. **Crash recovery to suspended**
   - a transfer was locally in progress during crash
   - startup finds usable checkpoint/session plus still-valid existing access material
   - startup moves the transfer to `SUSPENDED`
   - operator can later resume it through the normal flow
7. **Crash recovery to terminated**
   - a transfer was locally in progress during crash
   - startup finds missing checkpoint/session or invalid existing access material
   - startup terminates the transfer with reason `unrecoverable error, start a new data transfer`

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
