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
