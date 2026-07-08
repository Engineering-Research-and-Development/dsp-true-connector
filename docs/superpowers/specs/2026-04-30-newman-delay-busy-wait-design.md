# Design: Replace postman-echo delay with synchronous busy-wait

**Date:** 2026-04-30  
**Status:** Approved

## Problem

Two Newman test collections use `https://postman-echo.com/delay/2` to introduce a 2-second pause inside a polling loop. This creates:

1. **External internet dependency** — CI runners must reach postman-echo.com, which breaks in air-gapped or restricted environments.
2. **Reliability risk** — if postman-echo.com is unavailable or throttled, the tests fail for reasons unrelated to the connector under test.

A secondary issue: both collections also use `setTimeout(() => {}, 2000)` in a pre-request script as a one-shot delay. This is a **no-op** in Newman's sandbox (async callbacks are not awaited), so no delay is currently applied there.

## Solution

Replace the external HTTP call with a **synchronous busy-wait** in Newman's pre-request script. Newman's sandbox is synchronous; a spin-loop on `Date.getTime()` genuinely blocks for the requested duration without any network call.

```javascript
var start = new Date().getTime();
while (new Date().getTime() - start < 2000) {}
```

## Affected Files

- `ci/docker/test-cases/datatransfer-api-http-pull-tests/datatransfer-api-http-pull-tests.json`
- `ci/docker/test-cases/datatransfer-api-http-push-tests/datatransfer-api-http-push-tests.json`

No changes to `docker-compose.yml`, workflow files, or any other file.

## Changes Per Collection (identical in both)

### 1. Polling loop — `[C] Poll delay` step (item 8)

**Remove** the `[C] Poll delay` step entirely. It currently sends `GET https://postman-echo.com/delay/2` and then redirects back to the poll step.

**Add** the busy-wait to the **pre-request script** of `[C] Wait for download to complete`:

```javascript
// pre-request script — [C] Wait for download to complete
var start = new Date().getTime();
while (new Date().getTime() - start < 2000) {}
```

**Update** the `setNextRequest` call in that step's test script:

```javascript
// Before
pm.execution.setNextRequest('[C] Poll delay');

// After
pm.execution.setNextRequest('[C] Wait for download to complete');
```

The step now loops to itself. The 2-second wait fires in the pre-request phase before each poll.

> **Behavioural note:** The first poll attempt will now wait 2 seconds (previously it was immediate). This is acceptable in CI and may reduce flakiness on slow-starting transfers.

### 2. One-shot delay — `[C] View data` pre-request script (item 9)

**Replace** the no-op `setTimeout`:

```javascript
// Before (no-op in Newman)
setTimeout(() => {}, 2000);

// After (actually blocks for 2s)
var start = new Date().getTime();
while (new Date().getTime() - start < 2000) {}
```

## Trade-offs

| | Busy-wait (chosen) | Self-hosted httpbin |
|---|---|---|
| External dependency | None | None (self-hosted) |
| Extra containers | None | +1 (go-httpbin) |
| Collection changes | Moderate (remove step, update scripts) | Minimal (URL swap only) |
| CPU during wait | Briefly busy (~2s per poll cycle) | Idle |
| Accuracy | ~2s (sufficient for CI) | Exact HTTP timeout |

Busy-wait is preferred here because it requires no infrastructure changes and the CPU cost is negligible for CI.

## Acceptance Criteria

- [ ] Neither collection references `postman-echo.com` after the change
- [ ] The `[C] Poll delay` step is removed from both collections
- [ ] `setTimeout` in `[C] View data` pre-request is replaced with busy-wait
- [ ] `setNextRequest` in `[C] Wait for download to complete` points to itself
- [ ] Both Newman collections run successfully end-to-end in CI without internet access to postman-echo.com
