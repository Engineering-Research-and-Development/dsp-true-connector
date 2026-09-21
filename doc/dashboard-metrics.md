# Dashboard Metrics API

## Purpose

The dashboard metrics API exposes backend-only admin metrics for connector dashboards and monitoring clients.
It provides tenant-scoped negotiation, transfer, and audit-event aggregates plus process-wide runtime metrics.

## Base path

All endpoints are exposed under:

```text
/api/v1/dashboard
```

Responses use the normal `GenericApiResponse` wrapper.

## Endpoints

| Endpoint | Purpose | Query params |
| --- | --- | --- |
| `GET /api/v1/dashboard/summary` | Returns the aggregated dashboard payload with `negotiations`, `transfers`, `events`, and `runtime` sections. | `from`, `to`, `bucket` |
| `GET /api/v1/dashboard/runtime` | Returns runtime-only process and JVM metrics. | none |
| `GET /api/v1/dashboard/negotiations` | Returns the current negotiation snapshot grouped by state and by role/state. | none |
| `GET /api/v1/dashboard/transfers` | Returns the current transfer snapshot grouped by state, role/state, format, and download flags. | none |
| `GET /api/v1/dashboard/events` | Returns historical audit-event aggregates for the selected window. | `from`, `to`, `bucket` |

## Query parameters

The `summary` and `events` endpoints accept the same optional window parameters:

| Name | Type | Meaning |
| --- | --- | --- |
| `from` | ISO-8601 instant | Inclusive start of the time window. |
| `to` | ISO-8601 instant | Inclusive end of the time window. |
| `bucket` | `hour` or `day` | Aggregation granularity for historical event buckets. |

Behavior:

- If `to` is omitted, the current UTC time is used.
- If `from` is omitted, the API uses `to - 24h`.
- If `bucket` is omitted, the default is `hour`.
- Invalid timestamps or unsupported bucket values return a client error.

## Security expectations

- These are management API endpoints and require admin authentication.
- Tenant admins access data within their own tenant scope.
- Super-admin requests can read cross-tenant data, or can scope a single request with the `X-Tenant-Id` header.
- Connector protocol credentials are not valid for these endpoints.

## Tenant behavior

- `negotiations`, `transfers`, and `events` are filtered by the active tenant context when one is present.
- A tenant admin automatically uses the tenant attached to the authenticated user.
- A super-admin without `X-Tenant-Id` receives cross-tenant aggregates.
- The `runtime` endpoint is process-wide and is not partitioned by tenant.
- The `summary` endpoint combines tenant-scoped business metrics with the same process-wide runtime snapshot.

### Per-tenant breakdown (`byTenant`)

When a superadmin requests dashboard data without a tenant scope (blank or missing `X-Tenant-Id` header), each of:

- `NegotiationSnapshotMetrics` (`/api/v1/dashboard/summary` and `/api/v1/dashboard/negotiations`)
- `TransferSnapshotMetrics` (`/api/v1/dashboard/summary` and `/api/v1/dashboard/transfers`)
- `HistoricalEventMetrics` (`/api/v1/dashboard/summary` and `/api/v1/dashboard/events`)

includes an additional `byTenant` field: a list of `{ tenantId, tenantName, metrics }` entries, one per registered tenant (including disabled tenants), zero-filled for tenants with no matching data. The nested `metrics` object has the same shape as its parent but with its own `byTenant` always `null` (no further recursion).

When an admin requests data (tenant-scoped JWT with valid `X-Tenant-Id` header) or a superadmin scopes a request via `X-Tenant-Id`, the `byTenant` field is `null` on every affected object.

Example: superadmin requests `/api/v1/dashboard/negotiations` (no tenant scope):

```json
{
  "success": true,
  "data": {
    "totalCount": 25,
    "byState": [
      { "key": "REQUESTED", "count": 10 },
      { "key": "AGREED", "count": 15 }
    ],
    "byRoleAndState": [ /* ... */ ],
    "byTenant": [
      {
        "tenantId": "tenant-a",
        "tenantName": "Tenant A",
        "metrics": {
          "totalCount": 15,
          "byState": [
            { "key": "REQUESTED", "count": 6 },
            { "key": "AGREED", "count": 9 }
          ],
          "byRoleAndState": [ /* ... */ ],
          "byTenant": null
        }
      },
      {
        "tenantId": "tenant-b",
        "tenantName": "Tenant B",
        "metrics": {
          "totalCount": 10,
          "byState": [
            { "key": "REQUESTED", "count": 4 },
            { "key": "AGREED", "count": 6 }
          ],
          "byRoleAndState": [ /* ... */ ],
          "byTenant": null
        }
      }
    ]
  }
}
```

Same superadmin with `X-Tenant-Id: tenant-a` header:

```json
{
  "success": true,
  "data": {
    "totalCount": 15,
    "byState": [
      { "key": "REQUESTED", "count": 6 },
      { "key": "AGREED", "count": 9 }
    ],
    "byRoleAndState": [ /* ... */ ],
    "byTenant": null
  }
}
```

The `runtime` endpoint is not affected: it is always process-wide and never includes `byTenant`.

## Metrics Aggregation Pattern

### Overview

Dashboard metrics for negotiations, transfers, and events follow a consistent aggregation pattern across all three services:

- **Per-tenant requests** (tenantId provided): Return metrics specific to that tenant only. No aggregation is needed.
- **Super-admin requests** (tenantId=null): Aggregate metrics across all tenants by summing counts for identical keys, ensuring no duplicate keys appear in the output.

### Implementation

All three metrics services implement the same pattern:

1. **Retrieve raw data with tenant ID preserved** — MongoDB aggregation groups by (field, tenantId), preserving tenant context.
2. **Convert to KeyCount objects** — Extract (key, count) pairs for post-processing.
3. **Aggregate for super-admin scope** — When tenantId is null, use `Collectors.groupingBy()` to sum counts for identical keys.
4. **Preserve per-tenant breakdown** — Store the same raw data in a separate `byTenant` sub-structure for transparency.

### Code Example

```java
private List<KeyCount> getCounts(Document snapshot, String fieldName, String tenantId) {
    List<KeyCount> counts = extractRawCounts(snapshot, fieldName);
    
    // When tenantId is null (super-admin scope), aggregate counts by summing identical keys
    if (!StringUtils.hasText(tenantId)) {
        return counts.stream()
                .collect(Collectors.groupingBy(
                        KeyCount::key,
                        Collectors.summingLong(KeyCount::count)
                ))
                .entrySet()
                .stream()
                .map(entry -> new KeyCount(entry.getKey(), entry.getValue()))
                .sorted(KEY_COUNT_COMPARATOR)
                .toList();
    }
    
    return counts;
}
```

### Affected Metrics

This pattern is applied to:

| Service | Fields Affected |
|---------|-----------------|
| `NegotiationMetricsService` | `byRoleAndState` |
| `TransferMetricsService` | `byRoleAndState`, `byFormat` |
| `AuditEventMetricsService` | `byEventType`, `byRole` (already implemented) |

### Why This Matters

Without aggregation, super-admin requests return duplicate keys with per-tenant counts that are difficult to interpret. For example:

```json
{
  "byRoleAndState": [
    { "key": "consumer:TERMINATED", "count": 4 },
    { "key": "consumer:TERMINATED", "count": 3 },
    { "key": "consumer:TERMINATED", "count": 2 }
  ]
}
```

With aggregation, the same data is correctly summarized:

```json
{
  "byRoleAndState": [
    { "key": "consumer:TERMINATED", "count": 9 }
  ]
}
```

While per-tenant details are preserved in the `byTenant` sub-structure.

## Runtime metric semantics

Runtime data describes the Java process that runs the connector:

- process CPU usage
- system CPU usage
- JVM heap used and max bytes
- JVM non-heap used bytes
- live JVM thread count
- JVM uptime in milliseconds

These values are taken from JVM/process metrics and Micrometer gauges when available.
They do not represent container quotas, pod limits, or host-level capacity reservations.
