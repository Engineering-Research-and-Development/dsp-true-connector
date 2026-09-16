# D-TEC-008 — Tenant-aware dashboard metrics API

## Metadata
- Status: Accepted
- Date: 2026-09-02
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: dashboard, metrics, monitoring, mongodb, multi-tenancy, api
- Risk Level: Low

## Context

The connector exposes negotiation, transfer, and audit-event data through separate modules and
collections. A management UI needs a stable, compact API for displaying current business-state
statistics, historical activity, and connector process health without duplicating aggregation
logic in the UI or querying MongoDB directly.

Dashboard data must preserve the connector's multi-tenant access model. Tenant administrators must
only see data from their active tenant context. Super-admins need a complete cross-tenant view and
an optional per-tenant breakdown, while retaining correctly summed totals for every metric key.
Runtime data, in contrast, describes the shared connector JVM and cannot be meaningfully scoped to
an individual tenant.

The resulting API also needs to remain within the management API boundary: it is not a Dataspace
Protocol surface and must use the existing `GenericApiResponse` response convention.

## Decision

Provide a dedicated, admin-protected dashboard API at `/api/v1/dashboard`, with a consolidated
`summary` resource and focused resources for runtime, negotiations, transfers, and events.

Collect business metrics in their owning modules using MongoDB aggregations, compose the summary in
the `connector` module, and expose shared dashboard contracts from `tools`. Pass the active tenant
context to every business-metric query. For unscoped super-admin requests, aggregate matching keys
across tenants and additionally return a single-level `byTenant` breakdown; tenant-scoped requests
return only their tenant's metrics and set `byTenant` to `null`.

## Alternatives Considered

- **Expose the existing entity APIs and aggregate in the UI** → rejected because it would require
  multiple paginated entity requests, duplicate business-state calculations in every UI client, and
  expose a less stable contract for dashboard use.
- **Provide only one dashboard summary endpoint** → rejected because detail views would repeatedly
  retrieve unrelated data, including process-wide runtime metrics, and could not refresh individual
  dashboard panels efficiently.
- **Let the UI query MongoDB or an operational metrics backend directly** → rejected because it
  bypasses management authorization and tenant isolation, couples the UI to persistence schemas,
  and is not appropriate for a deployed connector.
- **Return raw per-tenant rows to super-admins without a top-level aggregate** → rejected because
  repeated state, role/state, event-type, and format keys would force consumers to implement their
  own aggregation and can lead to misleading totals.

## Rationale

The dedicated controller establishes a stable management contract while keeping domain aggregation
close to the collections it understands: `NegotiationMetricsService` queries
`contract_negotiations`, `TransferMetricsService` queries `transfer_process`, and
`AuditEventMetricsService` queries `audit_events`. `DashboardMetricsService` only validates the
time window and composes those independent results with the process-wide runtime snapshot, so the
`connector` module remains the application wiring layer rather than a cross-module data-access
owner.

The five resources support both efficient dashboard-panel refreshes and a one-request overview:
`/summary`, `/runtime`, `/negotiations`, `/transfers`, and `/events`. Event history accepts an
ISO-8601 `from`/`to` window and `hour` or `day` bucket. The default is the preceding 24 hours,
ending at the current UTC time, grouped hourly.

MongoDB aggregation retains `tenantId` in intermediate groups. This permits correctly summed
super-admin totals and deterministic per-tenant submetrics from the same source data. Listing every
registered tenant in `byTenant`, including tenants with no matching records, gives the UI a stable
zero-filled breakdown. Nested metric objects always set `byTenant` to `null`, preventing recursive
payloads.

## Consequences

### Positive
- UI and monitoring clients receive purpose-built, versioned management payloads rather than
  persistence documents or client-side aggregates.
- Tenant-scoped requests preserve the active authorization context across negotiations, transfers,
  and audit events.
- Super-admins receive both an aggregate suitable for headline statistics and transparent
  per-tenant details, with duplicate keys summed before they are returned.
- Event history supports a bounded, validated time window and UTC-aligned hourly or daily charts.
- Runtime metrics are explicitly documented as shared JVM/process data, avoiding a false
  implication of tenant-level resource accounting.
- The controller and service tests cover endpoint response shapes, window validation, tenant
  filtering, aggregate totals, and per-tenant breakdown behavior.

### Negative
- A dashboard summary executes independent aggregation queries for negotiations, transfers, and
  audit events, so it is a read-only reporting endpoint rather than a cached real-time telemetry
  stream.
- Unscoped super-admin requests load all registered tenants to create zero-filled `byTenant`
  entries; response size grows with the number of tenants.
- Dashboard metrics describe data currently persisted in MongoDB and audit events recorded in the
  selected window; they do not replace an external observability system.

### Risks
- **Large historical event windows can increase aggregation cost.** Mitigation: defaults limit the
  window to 24 hours, allowed bucket sizes are constrained to hour or day, and consumers should
  request only the time span needed for a chart.
- **A future metric service might omit tenant-aware grouping for an unscoped query.** Mitigation:
  preserve `tenantId` through aggregation, aggregate top-level matching keys explicitly, and keep
  the existing super-admin and tenant-scoped metric tests.
- **Consumers could interpret runtime values as container or tenant quotas.** Mitigation:
  `/runtime` remains separate from business metrics and its documentation defines it as a
  process-wide JVM snapshot.

## Related
- Decisions: [D-TEC-001](D-TEC-001-mongodb-persistence.md) — MongoDB as persistence layer;
  [D-TEC-006](D-TEC-006-dbref-tenant-filter-mitigation.md) — @DBRef tenant-filter limitation and
  service-layer mitigation
- Docs: [Dashboard Metrics API](../../dashboard-metrics.md);
  [Dashboard UI Handoff](../../dashboard-ui-handoff.md);
  [Architecture overview](../../architecture.md)
- Implementation: `DashboardMetricsController`, `DashboardMetricsService`,
  `NegotiationMetricsService`, `TransferMetricsService`, and `AuditEventMetricsService`
