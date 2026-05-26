# Handoff: Suspend/Resume Transfer – Implementation on Fresh Base

**Date:** 2026-05-25  
**From branch:** `feature/suspend-resume-transfers` (99 commits ahead of `main` @ `0.6.6`)  
**Related PR:** [#241](https://github.com/Engineering-Research-and-Development/dsp-true-connector/pull/241)  
**Status:** Design complete, zero implementation done. Branch halted intentionally for rebase.

---

## What the Next Session Must Do

The design is fully approved and committed. The next session's job is:

1. Create a **new branch from the latest `main`** (or rebase only the suspend/resume commits off the current branch — the branch has 99 mixed commits including unrelated security, Keycloak, and CI work)
2. Read the spec in full (path below)
3. Invoke the `writing-plans` skill to create the implementation plan
4. Implement, test, verify

---

## Primary Artefact – the Spec

> **`docs/superpowers/specs/2026-05-25-resume-integrity-fixes-design.md`**  
> Commit: `ca69984a` on `feature/suspend-resume-transfers`

This is the single source of truth for what to build. Read it before touching any code. It covers four sections: suspend path, resume path, checkpoint model, and housekeeping fixes.

The existing implementation plan series (plans in `docs/superpowers/plans/`) covers earlier phases of the feature; the spec above is specifically the integrity-fix layer on top.

---

## Background: What This Feature Is

HTTP-PULL and HTTP-PUSH data transfers can be **suspended** (consumer-requested pause) and **resumed**. The mechanism:

- On suspend: a `cancellationToken` AtomicBoolean is set; upload strategies detect it and throw `TransferCancelledException`
- A `CheckpointCallbackImpl` persists progress to MongoDB (`TransferArtifactState`) as parts complete
- On resume: `HttpPullTransferStrategy` / `HttpPushTransferStrategy` read the checkpoint and restart from the saved byte offset
- Upload is done via S3 multipart upload (`S3AsyncUploadStrategy` / `S3SyncUploadStrategy`)

The feature code ships in these modules: `data-transfer`, `tools`.

---

## The Bugs That Were Found (All Still Present, None Fixed Yet)

### 🔴 Critical: Data Corruption on Resume

**Root cause:** On suspend, both strategies call `abortMultipartUpload` inside the `TransferCancelledException` catch block. This destroys all uploaded S3 parts. On resume, `rangeStart = checkpoint.getDownloadedBytes()` (e.g., 80 MB), but a brand-new multipart upload is started — the first 80 MB are never uploaded. The S3 object is permanently truncated.

**Fix:** Remove `abortMultipartUpload` from the `TransferCancelledException` branch in both strategies. The general-error path keeps the abort.

### 🔴 Critical: `uploadId` Nulled on Every Resume

**File:** `HttpPullTransferStrategy.java`, line ~93  
```java
if (rangeStart > 0) {
    checkpoint.setUploadId(null);  // BUG: destroys resumable upload
}
```
This explicit null prevents the true-resume path from ever working, even though `TransferArtifactState` already has an `uploadId` field.

### 🔴 Critical: ETags Never Persisted

`CheckpointCallbackImpl.onPartCompleted` never calls `addEtag()`. The `etags` list in `TransferArtifactState` is always empty. No ETag data is available for `completeMultipartUpload` on true resume. The model was designed for true resume but the wiring was never completed.

### 🟠 High: Async Cursor is Unreliable Under Parallel Uploads

`onPartCompleted` receives `totalBytesUploaded` (a cumulative sum). Under parallel uploads, part 3 may complete before part 2, causing the cursor to advance past unconfirmed data. If the process crashes at that point, resume starts from a position that skips bytes. **Fix:** Change the third parameter to `partBytes` (per-part actual size); compute cursor from the highest *contiguous* confirmed part boundary.

### 🟠 High: No Pre-Complete Cancellation Check (Async)

In `S3AsyncUploadStrategy`, there is a narrow race window: a suspend signal can arrive after the last part future completes but before `completeMultipartUpload` is called. The upload would complete and the transfer would be marked done instead of suspended.

### 🟡 Medium: Checkpoint Never Deleted on Terminal Paths

`TransferArtifactState.deleteById` is never called. After successful download completion and after `PresignedUrlExpiredException` (both terminal paths), stale checkpoints are left in MongoDB. A future resume attempt would pick up a stale checkpoint.

### 🟡 Medium: Presigned URL Logged at INFO

`HttpPullTransferStrategy` line ~189: `log.info("Presigned URL: {}", presignedUrl)`. Presigned URLs are time-limited credentials — always visible in production logs. Change to `log.debug`.

### 🟡 Medium: `PresignedUrlExpiredException` Passes Full URL in Push

`HttpPushTransferStrategy` line ~182: `throw new PresignedUrlExpiredException(presignedUrl)`. Pull passes the transfer key (not the URL). Push should match for consistency and to avoid leaking credentials in exception messages and audit events.

---

## Key Design Decisions Made

| Decision | Choice | Rationale |
|---|---|---|
| Resume strategy | True resume (continue existing multipart upload) | Avoids re-downloading already-transferred bytes |
| Async cursor fix | Fix properly with `partBytes` per-part tracking | Correct-by-construction, not a workaround |
| Expired uploadId fallback | Restart from byte 0 + `log.info` | Simple, safe, covers environments with lifecycle rules |
| In-flight part overlap (async) | Accept overlap (re-upload orphaned parts by number) | S3 allows overwriting by part number; result is correct; simpler than waiting on futures |

---

## AWS Facts — Verified Against Documentation

**Multipart uploads have NO default expiry on AWS or MinIO.** Parts persist indefinitely unless a bucket `AbortIncompleteMultipartUpload` lifecycle rule is configured. An earlier claim of "24h default" in the session was incorrect.

**`listParts` must NOT be used as input to `completeMultipartUpload`.** AWS documentation explicitly warns against this. Use `listParts` only as a liveness probe: `NoSuchUploadException` reliably signals that the upload was aborted or expired. Always use the persisted `completedParts` map for the actual complete call.

**Suspended transfers consume zero connections and threads.** S3/MinIO holds only the uploaded part bytes (billed as storage). Practically unlimited transfers may be suspended concurrently without resource concern.

---

## Model: What Already Exists vs. What Needs Changing

`TransferArtifactState` already has `uploadId`, `partNumber`, `etags`, `destBucket`, `destObject` fields — it was designed for true resume. The wiring was incomplete.

**What to replace:**

| Remove | Add |
|---|---|
| `List<String> etags` | `Map<Integer, String> completedParts` (partNumber → ETag) |
| `int partNumber` + `incrementPartNumber()` + `addEtag()` | `addCompletedPart(int, String)`, `clearCompletedParts()`, `getLastPartNumber()` |

**Why Map over List:** Under async parallel uploads, parts can complete out of order. A Map keyed by part number is unambiguous regardless of insertion order. A List with implicit index ordering breaks if part 3 is appended before part 2.

---

## Files to Change (Implementation Scope)

| File | Change |
|---|---|
| `data-transfer/…/model/TransferArtifactState.java` | Replace etags List + partNumber with `completedParts` Map |
| `tools/…/s3/service/upload/UploadCheckpointCallback.java` | Change `onPartCompleted` third param: `totalBytesUploaded` → `partBytes` |
| `data-transfer/…/strategy/CheckpointCallbackImpl.java` | Cursor logic, ETag persistence, `getExistingUploadId()` helper, `partSizes` map |
| `tools/…/s3/service/S3ClientService.java` (interface + impl) | Add two `listParts` overloads (local and remote client) |
| `tools/…/s3/service/upload/S3SyncUploadStrategy.java` | Remove abort from cancel branch; resume path; update `onPartCompleted` call site |
| `tools/…/s3/service/upload/S3AsyncUploadStrategy.java` | Remove abort from cancel branch; pre-complete cancel check; resume path; update call site |
| `data-transfer/…/strategy/HttpPullTransferStrategy.java` | Remove `setUploadId(null)`; add `listParts` validity check; `log.info` → `log.debug` |
| `data-transfer/…/strategy/HttpPushTransferStrategy.java` | Remove `setUploadId(null)`; add `listParts` validity check (remote client); fix exception |
| `data-transfer/…/service/api/DataTransferAPIService.java` | Call `deleteById` on success and on URL expiry |

Full change details for each file are in the spec.

---

## Open Questions / Blockers for the Next Session

1. **Push `listParts` client:** The `listParts` check in `HttpPushTransferStrategy` needs the remote destination S3 credentials. `TransferArtifactState.destBucket` and `destObject` exist. Confirm that `destinationS3Properties` (the credential map) is available at the point where the validity check runs in `HttpPushTransferStrategy`.

2. **Callers of removed model methods:** Before removing `etags` / `incrementPartNumber()` / `addEtag()` from `TransferArtifactState`, do a workspace-wide search:
   ```
   grep -r "addEtag\|incrementPartNumber\|\.etags" --include="*.java"
   ```
   Ensure no callers outside the strategies are missed (serialization, tests, etc.).

3. **`CheckpointCallbackImpl` constructor:** The current constructor takes a `chunkSize` parameter used to compute the cursor. After the change to `partBytes`-based tracking, `chunkSize` is no longer needed. Verify no other constructor callers pass or depend on it.

---

## Lessons Learned (Don't Repeat These Mistakes)

- **Verify AWS docs before making claims about defaults.** The "24h expiry" claim was wrong. When unsure, fetch the actual AWS documentation page.
- **Read the full model before designing.** `TransferArtifactState` already had `uploadId`, `etags`, etc. Half-knowing the model led to designing something the model already partially supported, wasting a design iteration.
- **The `List<String> etags` field was a red herring.** It exists in the model but was never populated. Don't assume a field is in use just because it exists — check call sites.
- **Check what `onPartCompleted` actually does** before assuming the checkpoint cursor is correct. The cumulative `totalBytesUploaded` parameter was semantically wrong under async; the bug was invisible until the design was examined carefully.

---

## Suggested Skills

Invoke these skills at the start of the new session:

| Skill | When |
|---|---|
| `writing-plans` | Immediately after reading the spec — convert it into a step-by-step implementation plan |
| `model-class-guidelines` | Before touching `TransferArtifactState` — the repo has a specific builder + validation pattern |
| `junit-5-tests` | Before writing any new test — the repo has specific JUnit 5 / Mockito conventions |
| `java-development` | When unsure about Javadoc or Checkstyle requirements (both `validate` and `checkstyle:check` must pass) |
| `dsp-transfer-process` | If questions arise about DSP transfer state machine expectations |
| `verification-before-completion` | Before declaring the task done — run `mvn clean verify` (requires Docker for Testcontainers) |

---

## Build and Test Reference

```bash
# Full verification (requires Docker for MongoDB + MinIO Testcontainers)
mvn clean verify

# Unit tests only (no Docker needed)
mvn test

# Single module
mvn -pl data-transfer -am test
mvn -pl tools -am test

# Single test class
mvn -pl data-transfer -am -Dtest=CheckpointCallbackImplTest test
```

CHANGELOG is excluded — the user will update it manually.
