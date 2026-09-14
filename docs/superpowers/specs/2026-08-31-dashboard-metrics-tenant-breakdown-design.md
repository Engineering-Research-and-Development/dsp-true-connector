# Design: Per-Tenant Breakdown for Superadmin Dashboard Metrics

## Context

`DashboardMetricsController` (`/api/v1/dashboard/**`) already scopes its metrics by
`tenantId` resolved via `TenantContextHolder`:

- If the authenticated principal is an `ADMIN`, `TenantContextHolder.getTenantId()`
  resolves to that admin's own tenant, and all metrics (negotiations, transfers, audit
  events, summary) are filtered to that tenant.
- If the authenticated principal is a `SUPER_ADMIN` and does not supply the
  `X-Tenant-Id` header, `TenantContextHolder.getTenantId()` resolves to `null`, and all
  metrics services (`NegotiationMetricsService`, `TransferMetricsService`,
  `AuditEventMetricsService`) already treat a blank `tenantId` as "aggregate across all
  tenants" (see `StringUtils.hasText(tenantId)` checks in their `buildCriteria`
  methods). This cross-tenant aggregation is correct today.
- If a `SUPER_ADMIN` supplies `X-Tenant-Id`, they act on behalf of that single tenant
  (same code path as an `ADMIN`).

**Gap**: when a superadmin requests cross-tenant data (no header), the response is a
single flat aggregate with no tenant dimension. A superadmin-facing UI that wants to
let a user browse "all tenants" and then filter down to one specific tenant has no way
to do so from a single API response — it would need N+1 calls (one per tenant, using
`X-Tenant-Id`).

## Goal

Add an optional **per-tenant breakdown** to the existing tenant-scoped dashboard
endpoints, populated only when the request is in cross-tenant (superadmin, no header)
scope, so a single API call gives the superadmin UI everything needed to render both an
"all tenants" view and a per-tenant filtered view without additional requests.

## Scope

Affected endpoints: `GET /summary`, `GET /negotiations`, `GET /transfers`,
`GET /events`.

Not affected: `GET /runtime` — process-level JVM/CPU/heap metrics, not tenant-scoped.

Not affected: DSP/DCAT protocol endpoints. These are plain admin JSON APIs
(`/api/v1/...`), not protocol-facing, so there is no DSP 2025-1 / TCK compliance impact.

## Trigger Rule

`byTenant` is populated **only** when the resolved `TenantContextHolder.getTenantId()`
for the current request is blank (i.e. a superadmin request with no `X-Tenant-Id`
header). For any single-tenant-scoped request — an `ADMIN`'s own tenant, or a
`SUPER_ADMIN` acting on behalf of one tenant via `X-Tenant-Id` — `byTenant` is `null`.

Client-side (UI) tenant filtering happens against the data already returned in this one
response; no additional API requests are made when the user switches the tenant filter
in the UI.

## Response Shape

### New shared DTO

`tools/src/main/java/it/eng/tools/model/dashboard/TenantMetrics.java`:

```java
public record TenantMetrics<T>(String tenantId, String tenantName, T metrics) {
}
```

A generic wrapper pairing a tenant identity with a metrics payload of type `T`.

### Extending existing response records

Each existing metrics record gains one new nullable field, reusing the record's own
type for the per-tenant entries (the nested entries always have `byTenant == null`, so
there is no unbounded recursion — this is the same pattern as a `Comment` record
containing a `List<Comment> replies`):

- `NegotiationSnapshotMetrics` gains `List<TenantMetrics<NegotiationSnapshotMetrics>> byTenant`
- `TransferSnapshotMetrics` gains `List<TenantMetrics<TransferSnapshotMetrics>> byTenant`
- `HistoricalEventMetrics` gains `List<TenantMetrics<HistoricalEventMetrics>> byTenant`

`DashboardSummaryResponse` requires **no changes** — it already embeds the three
records above, so `/summary` automatically surfaces all three `byTenant` breakdowns
without any restructuring of the summary DTO.

### Tenant roster inclusion

`byTenant` includes **every** tenant returned by `TenantRepository.findAll()`,
regardless of `enabled` status, zero-filled where a tenant has no matching data in the
requested window. Each entry includes both `tenantId` and `tenantName` (avoids an extra
UI round trip to resolve names).

## Computation Strategy

No N+1 per-tenant queries. Each service's existing Mongo aggregation pipeline is
changed to *always* include `tenantId` in its `$group` key, in addition to whatever
dimension it already grouped by (state, role, format, etc.). Grouping by an extra
dimension and then re-summing across it to produce the existing aggregate figures does
not change those aggregate results — so the existing aggregate-computation helper
methods in each service are reused unchanged.

New logic added to each service:

1. If the request's own `tenantId` parameter is non-blank, skip all per-tenant work and
   return `byTenant = null` (no roster lookup, no extra partitioning).
2. If blank (cross-tenant scope): partition the already-fetched grouped rows by
   `tenantId` in Java, load the tenant roster via `TenantRepository.findAll()`, and for
   each tenant build a `TenantMetrics<...>` entry by re-running the *same* aggregate
   helper methods against that tenant's subset of rows — zero-filling tenants absent
   from the grouped rows.

### Per-service notes

- **`NegotiationMetricsService`**: single simple grouped pipeline
  (`{tenantId, role, state}` group key). Lowest complexity — extend
  `GroupedNegotiationCount` with a `tenantId` component, partition by it.
- **`AuditEventMetricsService`**: three aggregations (by event type, by role, over time
  buckets) plus a total — each needs `tenantId` added to its group key, then results
  partitioned per tenant using the same approach.
- **`TransferMetricsService`**: highest complexity. Its aggregation uses a `$facet`
  with 5 parallel sub-pipelines (state, role+state, format, downloaded flag,
  download-in-progress flag) plus a total count. Each sub-pipeline's group key needs
  `tenantId` added, and then — because `$facet` branches run independently — the 5
  resulting per-branch arrays must be correlated back together **per tenant** in Java
  before a `TransferSnapshotMetrics` can be built per tenant. This is the riskiest and
  most time-consuming part of the implementation and should be built and tested
  incrementally.

Each of `NegotiationMetricsService` (negotiation module) and `TransferMetricsService`
(data-transfer module) gains a new constructor dependency on `TenantRepository`
(already in the shared `tools` module — no module-boundary violation, `tools` is
already a dependency of both).

## Testing Plan

- **Unit tests** (per service): `byTenant` is `null` when the tenantId parameter is
  non-blank; correct zero-filled per-tenant breakdown (including disabled tenants) when
  blank, using the same Mongo test setup style already used by each service's existing
  tests.
- **Controller tests**: extend `DashboardMetricsControllerTest` to assert `byTenant`
  presence/absence based on `TenantContextHolder` state, for `/summary`,
  `/negotiations`, `/transfers`, and `/events`.
- No DSP/TCK regression risk — confirmed non-protocol-facing endpoints.

## Documentation

Update `doc/dashboard-ui-handoff.md` with:
- The new `byTenant` field on each affected response shape.
- The trigger rule (populated only for superadmin cross-tenant requests).
- Guidance that UI-side tenant filtering should use the returned `byTenant` data
  directly rather than issuing additional per-tenant API calls.

## Out of Scope

- Changes to `/runtime`.
- Changes to authentication/authorization rules — existing `ROLE_ADMIN` /
  `ROLE_SUPER_ADMIN` access rules on `/api/**` already cover these endpoints correctly.
- Any new dedicated "by-tenant" endpoints (rejected in favor of extending existing
  endpoints).
