# Dashboard Seed Fixtures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add realistic, non-uniformly timestamped dashboard seed data to both consumer and provider runtime profiles.

**Architecture:** Keep the existing JSON-based `InitialDataLoader` unchanged. Generate profile-specific static JSON records for 100 negotiations, 100 transfers, and 100 audit events, with 100 supporting agreements and policy-enforcement documents per profile. Preserve each profile's existing tenant and endpoint configuration.

**Tech Stack:** JSON, Python standard library for one-off deterministic fixture generation, existing Spring Boot/MongoDB seed loader.

**Spec:** `docs/superpowers/specs/2026-08-31-dashboard-seed-fixtures-design.md`

## Global Constraints

- Generate data only in `connector/src/main/resources/initial_data-consumer.json` and `connector/src/main/resources/initial_data-provider.json`.
- Keep exactly 100 `contract_negotiations`, 100 `transfer_process`, and 100 `audit_events` per profile.
- Keep exactly 100 supporting `agreements` and `policy_enforcements` per profile.
- Timestamp values must remain within `2025-08-31T00:00:00Z` through `2026-08-31T23:59:59Z`.
- Timestamp selection must be random but non-uniform, weighted toward recent activity with irregular clusters and gaps.
- Runtime JVM metrics are not seeded.
- Preserve profile-specific tenant IDs, callback URLs, participant settings, and existing catalog data.
- Do not modify unrelated seed files.

---

### Task 1: Generate dashboard fixture records

**Files:**
- Modify: `connector/src/main/resources/initial_data-consumer.json`
- Modify: `connector/src/main/resources/initial_data-provider.json`

**Interfaces:**
- Consumes: Existing profile JSON objects and tenant/catalog/dataset identifiers.
- Produces: Valid MongoDB seed collections consumed by `InitialDataLoader`.

- [ ] **Step 1: Capture each profile's existing identifiers**

Read both JSON files and retain:

```text
consumer tenantId = engineering
provider tenantId = engineering-provider
datasetId = urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5
existing callback addresses and profile-specific catalog fields
```

- [ ] **Step 2: Generate non-uniform timestamps**

Use a fixed random seed for reproducibility and choose timestamps from weighted
periods rather than a uniform year-wide distribution:

```python
rng = random.Random(20260831)
periods = [
    (date(2025, 8, 31), date(2025, 11, 30), 0.10),
    (date(2025, 12, 1), date(2026, 5, 31), 0.25),
    (date(2026, 6, 1), date(2026, 8, 15), 0.30),
    (date(2026, 8, 16), date(2026, 8, 31), 0.35),
]
```

Within each period, add irregular day/hour offsets and occasional nearby
timestamps for related negotiation, transfer, and audit records. Clamp all
results to the inclusive one-year window.

- [ ] **Step 3: Generate 100 supporting agreements and policy records**

For each index `001` through `100`, create unique profile-prefixed IDs:

```json
{
  "_id": "dashboard-consumer-agreement-001",
  "_class": "it.eng.negotiation.model.Agreement",
  "id": "urn:uuid:dashboard-consumer-agreement-001",
  "target": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
  "timestamp": "2026-08-31T12:00:00Z",
  "assigner": "urn:connector:engineering:provider",
  "assignee": "urn:connector:engineering:consumer",
  "permission": [{"action": "use"}],
  "tenantId": "engineering"
}
```

Create one matching `policy_enforcements` record with the agreement ID and an
integer count between `0` and `5`.

- [ ] **Step 4: Generate 100 contract negotiations**

Use valid `ContractNegotiationState` values and both `consumer` and `provider`
roles. Every record must have:

```json
{
  "_id": "dashboard-consumer-negotiation-001",
  "consumerPid": "urn:uuid:dashboard-consumer-consumer-pid-001",
  "providerPid": "urn:uuid:dashboard-consumer-provider-pid-001",
  "agreement": {
    "$ref": "agreements",
    "$id": "urn:uuid:dashboard-consumer-agreement-001"
  },
  "state": "FINALIZED",
  "role": "consumer",
  "tenantId": "engineering"
}
```

Include auditable `created` and `modified` values when supported by the
existing fixture shape, with `modified` not earlier than `created`.

- [ ] **Step 5: Generate 100 transfer processes**

Use valid transfer states and formats already recognized by the dashboard
services, including `HTTP_PULL`, `HTTP_PUSH`, and `SFTP` where supported. Vary
roles and download flags. Each record must reference an existing seeded
agreement and the existing dataset:

```json
{
  "_id": "urn:uuid:dashboard-consumer-transfer-001",
  "_class": "it.eng.datatransfer.model.TransferProcess",
  "consumerPid": "urn:uuid:dashboard-consumer-transfer-consumer-pid-001",
  "providerPid": "urn:uuid:dashboard-consumer-transfer-provider-pid-001",
  "agreementId": "urn:uuid:dashboard-consumer-agreement-001",
  "datasetId": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
  "state": "COMPLETED",
  "role": "consumer",
  "format": "HTTP_PULL",
  "isDownloaded": true,
  "isDownloadInProgress": false,
  "tenantId": "engineering"
}
```

Add valid `created` and `modified` timestamps and preserve profile-specific
callback address conventions.

- [ ] **Step 6: Generate 100 audit events**

Use event types from `AuditEventType`, varied usernames, sources,
descriptions, and the profile tenant:

```json
{
  "_id": "dashboard-consumer-audit-001",
  "_class": "it.eng.tools.event.AuditEvent",
  "eventType": "ACTION",
  "username": "admin@mail.com",
  "timestamp": "2026-08-31T12:00:00Z",
  "description": "Dashboard seed activity 001",
  "source": "negotiation",
  "tenantId": "engineering"
}
```

- [ ] **Step 7: Write only the intended collection replacements**

Replace the existing empty or sparse dashboard-related arrays while preserving
all unrelated JSON collections and profile-specific settings. Do not add a
runtime metrics collection.

- [ ] **Step 8: Commit the fixture changes**

```bash
git add connector/src/main/resources/initial_data-consumer.json \
        connector/src/main/resources/initial_data-provider.json
git commit -m "test: seed dashboard metrics data" \
  -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 2: Validate dashboard fixture integrity

**Files:**
- Test: `connector/src/main/resources/initial_data-consumer.json`
- Test: `connector/src/main/resources/initial_data-provider.json`

**Interfaces:**
- Consumes: The generated JSON files from Task 1.
- Produces: Deterministic validation evidence that seed counts, references,
  timestamps, and JSON syntax are correct.

- [ ] **Step 1: Parse both files as JSON**

```bash
python -m json.tool connector/src/main/resources/initial_data-consumer.json >/dev/null
python -m json.tool connector/src/main/resources/initial_data-provider.json >/dev/null
```

Expected: both commands exit successfully.

- [ ] **Step 2: Validate counts, references, uniqueness, and timestamp bounds**

Run a read-only Python validation script that asserts:

```python
assert len(data["contract_negotiations"]) == 100
assert len(data["agreements"]) == 100
assert len(data["policy_enforcements"]) == 100
assert len(data["transfer_process"]) == 100
assert len(data["audit_events"]) == 100
assert all(START <= timestamp <= END for timestamp in all_timestamps)
assert len(all_generated_ids) == len(set(all_generated_ids))
assert every_negotiation_agreement_resolves
assert every_transfer_agreement_resolves
```

- [ ] **Step 3: Review the final diff**

```bash
git --no-pager diff --check HEAD~1..HEAD
git --no-pager diff --stat HEAD~1..HEAD
```

Expected: only the two requested seed files are changed by the fixture commit,
with no whitespace errors.

- [ ] **Step 4: Run the smallest existing validation**

```bash
mvn -pl connector -am -DskipTests validate
```

Expected: Checkstyle and Maven validation complete successfully without
requiring integration containers.
