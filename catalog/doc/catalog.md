# Catalog

## Overview

TRUE Connector publishes DSP 2025-1 catalog payloads, but the concrete transfer formats exposed in
each dataset distribution are synchronized with the set of currently registered dataplanes.

Normative DSP behavior:

- `POST <base>/catalog/request` returns a `dcat:Catalog`
- `GET <base>/catalog/datasets/{id}` returns a single `Dataset`
- A dataset must expose at least one `Distribution`
- Each distribution must reference at least one `DataService`

Implementation-specific behavior in this repository:

- the advertised `distribution.format` values come from active dataplane registrations
- distribution CRUD in the admin API updates template metadata, then a reconcile step
  re-materializes the final per-format distributions

## Runtime model

### Template distribution vs materialized distributions

The repository treats a dataset distribution as a template plus runtime capability expansion:

1. `CatalogDataPlaneFormatSyncService.resolveSupportedFormats()` takes the union of
   `DataPlaneRegistration.supportedTransferTypes`
2. the reconcile step keeps one concrete distribution per active format
3. if no dataplane formats are registered, the dataset is normalized to one template distribution
   with `format = null`
4. old shared distribution references are cloned per dataset before replacement

This keeps the catalog stable while preventing stale formats from being advertised after dataplanes
disappear.

### Reconciliation triggers

Catalog reconciliation runs after:

- dataplane register / update / deregister events
- dataset create / update / delete
- distribution create / update / delete

The dataplane-triggered path is:

`DataPlaneRegistrationService` -> `DataPlaneRegistrationChangedEvent` ->
`CatalogDataPlaneFormatSyncListener` -> `CatalogDataPlaneFormatSyncService.reconcileCatalogDistributions()`

The catalog-structure path is triggered through `CatalogStructureChangedEvent.fullReconcile(...)`.

## Admin API behavior

Typical admin endpoints:

- `GET /api/v1/datasets/{id}/formats`
- `GET /api/v1/datasets/{id}`
- `PUT /api/v1/distributions/{id}`
- `POST /api/v1/distributions`

### `GET /api/v1/datasets/{id}/formats`

Returns the currently available transfer formats for a dataset. This is the admin-facing view used
before creating a transfer process and reflects the active dataplane registration set, not just raw
stored distribution documents.

### `PUT /api/v1/distributions/{id}`

Updating a distribution edits the stored template metadata such as:

- title
- description
- access service
- policy references
- issued / modified timestamps

After the update, a full reconcile runs. The final advertised `format` values are re-derived from
the active dataplane set.

Operationally, this means:

- manual admin edits **do** affect the metadata reused for all materialized formats
- manual admin edits **do not** force unsupported transfer formats to stay visible

## Protocol examples

### Dataset response shape

At the DSP layer, the response remains a normal dataset with at least one distribution:

```json
{
  "@context": ["https://w3id.org/dspace/2025/1/context.jsonld"],
  "@type": "Dataset",
  "@id": "urn:uuid:dataset-id",
  "hasPolicy": [
    {
      "@type": "Offer",
      "@id": "urn:uuid:offer-id"
    }
  ],
  "distribution": [
    {
      "@type": "Distribution",
      "@id": "urn:uuid:distribution-id",
      "format": "HttpData-PULL",
      "accessService": {
        "@type": "DataService",
        "endpointURL": "http://connector:8080/engineering"
      }
    }
  ]
}
```

The exact set of `format` values may be:

- `HttpData-PULL`
- `HttpData-PUSH`
- `stream:grpc`
- `stream:kafka`

depending on which dataplanes are currently registered.

## Notes on top-level catalog fields

When datasets exist, top-level catalog `distribution` and `service` fields are treated as derived
views of dataset content. For admin/API flows where a catalog legitimately has no datasets, explicit
top-level `distribution` and `service` values are preserved instead of being overwritten.

## Source of truth in code

Key implementation touchpoints:

- `connector/.../CatalogDataPlaneFormatSyncService`
- `connector/.../CatalogDataPlaneFormatSyncListener`
- `catalog/.../DatasetAPIController`
- `catalog/.../DistributionService`
- `catalog/.../CatalogService`
