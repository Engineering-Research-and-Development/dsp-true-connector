# D-TEC-006 — @DBRef tenant-filter limitation and service-layer mitigation

## Metadata
- Status: Accepted
- Date: 2026-06-30
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: mongodb, multi-tenancy, dbref, security, catalog
- Risk Level: Medium

## Context

The `Catalog`, `Dataset`, and `Distribution` domain model classes in the `catalog` module use
Spring Data MongoDB `@DBRef` annotations to store cross-document object references. When
Spring Data resolves a `@DBRef`, it performs a raw OID-based lookup in MongoDB without any
application-level tenant filter. In a multi-tenant deployment, this means that a `Catalog`
document owned by tenant A could — if a code path is not careful — resolve a `@DBRef` pointing
to a `Dataset` owned by tenant B.

The risk manifests in service-layer methods that update a catalog after a related entity is
modified. For example, `CatalogService.updateCatalogDatasetAfterSave()` looks up the catalog
that contains a given dataset ID. Before MT3, the lookup used only the dataset ID, not the
tenant. A cross-tenant reference would pass the lookup without any tenant check.

## Decision

The mitigation is a service-layer **tenantId consistency assertion** (`assertSameTenant()`)
applied at every point where a cross-document reference is written or updated. Specifically:

- `CatalogRepository` exposes tenantId-scoped variants of all cross-document lookup queries:
  `findCatalogByDatasetIdAndTenantId` and `findCatalogByDataServiceIdAndTenantId`.
- `CatalogService.assertSameTenant()` is called before writing any `@DBRef` link, and throws
  `CatalogErrorException` (→ HTTP 404) if the referenced entity belongs to a different tenant.
- This guard applies in `updateCatalogDatasetAfterSave`, `updateCatalogDataServiceAfterSave`,
  and `updateCatalogDistributionAfterSave`.

`@DBRef` annotations are **not** removed from the model classes. Removal would be a breaking
persistence migration and is outside the MT3 scope.

## Alternatives Considered

- **Remove `@DBRef` and embed documents instead** → rejected for MT3 scope; requires a full
  data migration and changes the protocol serialization. Left as a future option.
- **Override `@DBRef` resolution with a custom tenant-filtered resolver** → rejected; Spring
  Data does not expose a clean extension point for per-tenant `@DBRef` resolution without
  significant framework coupling.
- **Add a tenantId field to every embedded reference and assert at resolution time** → rejected;
  requires model changes across all three affected classes and is equivalent in safety to
  the chosen service-layer guard at higher implementation cost.

## Rationale

The service-layer guard is the narrowest, safest mitigation: it operates at the moment of
reference creation (not resolution), it uses tenantId-scoped repository queries so the
database enforces the filter, and it maps to an already-tested code path (`CatalogErrorException`
→ HTTP 404). The fix is verified by `CrossTenantIsolationIT`, which directly asserts that
cross-tenant catalog access returns HTTP 404.

## Consequences

### Positive
- Cross-tenant reference writes are rejected at service layer before persistence.
- Existing `@DBRef` model structure is unchanged — no data migration required.
- Pattern is reusable: any future service method writing a cross-document reference should
  call `assertSameTenant()` before persisting.

### Negative
- `@DBRef` resolution itself remains unfiltered; read paths that resolve stored references
  do not re-check tenantId. This is acceptable because `assertSameTenant()` prevents
  cross-tenant references from ever being written.
- The guard must be added manually to every future write path — it is not automatic.

### Risks
- A future developer adds a service method that writes a `@DBRef` without calling
  `assertSameTenant()`. Mitigated by: (a) this ADR documenting the required pattern,
  (b) `CrossTenantIsolationIT` catching regression for the three guarded write paths.

## Related
- Decisions: [D-TEC-001](D-TEC-001-mongodb-persistence.md)
- Docs: [catalog/doc/catalog.md](../../../catalog/doc/catalog.md) — "Tenant isolation and @DBRef cascade safety"
- Tickets: #264 (T264), #266 (T266), #250 (MT3 slice)
