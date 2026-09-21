# D-TEC-003 — Asynchronous parallel S3 multipart upload

## Metadata
- Status: Accepted
- Date: Retroactively documented 2026-06-12 (decision predates ADR practice; design captured in doc/solutions/)
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: s3, upload, async, performance, artifacts
- Risk Level: Medium

## Context
Artifacts are stored in S3-compatible object storage (MinIO/AWS S3) via multipart upload. The original implementation wrapped `S3AsyncClient` calls but blocked on each part with `.join()`, uploading parts strictly sequentially — for a 10-part file, total time was the sum of all part upload times, wasting the async client and available bandwidth.

## Decision
The multipart upload pipeline is fully asynchronous: parts upload in parallel via `CompletableFuture.allOf()`, stages chain non-blockingly with `thenComposeAsync()` (create upload → upload parts in parallel → complete upload), and an `UploadResult` record carries state between stages. Chunk size is 50 MB per part.

## Alternatives Considered
- **Keep sequential uploads** → rejected: 3–8x slower for multi-part files with no offsetting simplicity benefit, since the async client was already in use.
- **Thread-pool of blocking uploads (sync client + executor)** → rejected: reimplements what the AWS async SDK already provides, with manual thread management and the same memory profile.
- **Streaming upload via AWS Transfer Manager / CRT client** → not adopted at the time; adds a dependency and configuration surface; the CompletableFuture pipeline met the performance need with the existing SDK.

## Rationale
Parallel part uploads bound total time by the slowest part rather than the sum of all parts (~3–8x improvement depending on network and part count). The non-blocking pipeline finally uses `S3AsyncClient` as designed, and the staged structure (with persisted-state hooks like `uploadId` and completed parts) deliberately prepares for future suspend/resume support without implementing it prematurely.

## Consequences

### Positive
- Significantly faster large-artifact uploads; better bandwidth and S3 capacity utilization.
- Clean async stage boundaries; `whenComplete()` guarantees the input stream closes.
- Groundwork laid for suspend/resume (state shape already defined).

### Negative
- Higher memory pressure: multiple 50 MB part buffers can be in flight concurrently.
- Failure handling is more involved; failed uploads leave incomplete multipart uploads in S3.

### Risks
- Memory exhaustion under many concurrent large uploads. Mitigated by the documented tuning guidance (chunk size, backpressure considerations) in the solutions doc.
- Orphaned incomplete uploads accumulating in S3. Mitigated by bucket lifecycle policies until an abort handler is implemented.

## Related
- Decisions: —
- Docs: [async_s3_upload_improvements.md](../../solutions/async_s3_upload_improvements.md), [s3_upload_mode_configuration.md](../../solutions/s3_upload_mode_configuration.md), [s3_configuration.md](../../s3_configuration.md)
- Tickets: —
