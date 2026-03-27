# Catalog Module — Technical Documentation

> **See also:** [Catalog User Guide](./catalog.md) | [Artifact Upload](./artifact-upload.md) | [Documentation Index](../../doc/README.md#catalog--artifacts)

---

## Overview

The Catalog module implements the **Dataspace Protocol (DSP) Catalog Protocol**, enabling data providers to publish descriptions of their data offerings and allowing consumers to discover them. It is a Spring Boot module persisting data in MongoDB and exposing two distinct API surfaces:

- **Protocol API** (`/catalog/*`) — implements the DSP wire protocol, consumed by remote connector peers.
- **Management API** (`/api/v1/*`) — used by operators to manage catalog content; requires authentication.

---

## DSP Specification Alignment

**Reference:** [DSP 2025-1 Catalog Protocol](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/)

The following DSP Catalog Protocol sections are implemented:

| DSP Section | Implementation |
|---|---|
| Catalog Request Message | `POST /catalog/request` — `CatalogRequestMessage` deserialized and validated |
| Catalog | `Catalog` model returned as DSP-compliant JSON-LD |
| Dataset Request Message | `GET /catalog/datasets/{id}` — `DatasetRequestMessage` pattern; dataset returned |
| CatalogError | `CatalogError` model returned on protocol-layer errors |
| Offer / Policy | `Offer`, `Permission`, `Constraint` aligned with ODRL profile used by DSP |

The `@context` header `https://w3id.org/dspace/2025/1/context.jsonld` is used for protocol serialisation. Internally, objects are stored and managed using a plain JSON format and converted to protocol JSON-LD on outbound responses via `CatalogSerializer`.

---

## Architecture

### Component Overview

```
HTTP Request
    │
    ├── /catalog/*          → CatalogController (DSP protocol)
    │       │
    │       └── CatalogService / DatasetService
    │               │
    │               └── CatalogRepository / DatasetRepository (MongoDB)
    │
    └── /api/v1/*           → *APIController classes (management)
            │
            ├── CatalogAPIController     → CatalogService
            ├── DatasetAPIController     → DatasetService
            ├── DistributionAPIController → DistributionService
            ├── DataServiceAPIController  → DataServiceService
            ├── OfferAPIController        → CatalogService.validateOffer()
            ├── ArtifactAPIController     → ArtifactService
            └── ProxyAPIController        → ProxyAPIService
```

- **Controllers** handle HTTP request/response, deserialization, and log entry.
- **Services** contain business logic, cross-entity coordination, validation, and exception translation.
- **Repositories** are Spring Data MongoDB repositories (one per root aggregate: Catalog, Dataset, Distribution, DataService, Artifact).
- **Models** are immutable, builder-constructed domain objects with Jakarta Validation annotations.
- **CatalogSerializer** handles bidirectional conversion between plain JSON (internal) and DSP protocol JSON-LD (wire format).

### Package Structure

| Package | Role |
|---|---|
| `it.eng.catalog.rest.protocol` | DSP protocol-facing controller |
| `it.eng.catalog.rest.api` | Management API controllers |
| `it.eng.catalog.service` | Business logic services |
| `it.eng.catalog.repository` | Spring Data MongoDB repositories |
| `it.eng.catalog.model` | Domain model classes and DSP message types |
| `it.eng.catalog.serializer` | JSON / JSON-LD serialization utilities |
| `it.eng.catalog.exceptions` | Custom exception types and Spring `@RestControllerAdvice` handlers |
| `it.eng.catalog.event` | Catalog-related application event listeners |

---

## REST API — Protocol Endpoints (DSP)

These endpoints implement the DSP Catalog Protocol. They are intended for consumption by remote connector peers.  
**Authentication:** The `authorization` header is accepted but not required (marked `required = false`).  
**Base path:** `/catalog`

---

### `POST /catalog/request` — Get Catalog

Handles a DSP `CatalogRequestMessage`. Validates the request, then returns the full catalog serialized as DSP-compliant JSON-LD.

**Protocol validation** is applied before returning: the catalog must have at least one Dataset, one Distribution, and one DataService. Datasets with a FILE artifact that is missing from S3 are silently filtered out. If validation fails, a `CatalogError` is returned.

**Request body:**

```json
{
  "@context": ["https://w3id.org/dspace/2025/1/context.jsonld"],
  "@type": "CatalogRequestMessage"
}
```

**Response — `200 OK`:**

```json
{
  "@context": ["https://w3id.org/dspace/2025/1/context.jsonld"],
  "@type": "Catalog",
  "@id": "urn:uuid:1dc45797-3333-4955-8baf-ab7fd66ac4d5",
  "title": "My Catalog",
  "participantId": "urn:example:DataProviderA",
  "dataset": [ { "..." : "..." } ],
  "distribution": [ { "..." : "..." } ],
  "service": [ { "..." : "..." } ]
}
```

**Error responses:**

| HTTP Status | Condition |
|---|---|
| `404 Not Found` | Catalog not found or fails protocol validation |
| `400 Bad Request` | Request body fails deserialization or model validation |

Error body follows DSP `CatalogError` format:

```json
{
  "@type": "CatalogError",
  "code": "Not Found",
  "reason": ["Catalog not available at the moment"]
}
```

---

### `GET /catalog/datasets/{id}` — Get Dataset

Returns a single dataset by ID, serialized as DSP-compliant JSON-LD.

**Protocol validation** is applied: the dataset must have at least one Offer and one Distribution. If the artifact is of type FILE and is missing from S3, the dataset is treated as not found.

**Response — `200 OK`:**

```json
{
  "@context": ["https://w3id.org/dspace/2025/1/context.jsonld"],
  "@type": "Dataset",
  "@id": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
  "title": "My Dataset",
  "hasPolicy": [ { "..." : "..." } ],
  "distribution": [ { "..." : "..." } ]
}
```

**Error responses:**

| HTTP Status | Condition |
|---|---|
| `404 Not Found` | Dataset not found or fails protocol validation |

---

## REST API — Management Endpoints (`/api/v1/*`)

These endpoints are used by operators to manage catalog content. All responses are wrapped in `GenericApiResponse<T>`.

**Authentication:** Basic Auth with `ROLE_ADMIN`.

**Generic success response structure:**

```json
{
  "success": true,
  "data": { "..." : "..." },
  "message": "Human-readable status message"
}
```

**Generic error response structure:**

```json
{
  "success": false,
  "message": "Error description"
}
```

---

### Catalog — `/api/v1/catalogs`

#### `GET /api/v1/catalogs` — Get default catalog

Returns the first catalog found. Throws `404` if none exists.

#### `GET /api/v1/catalogs/{id}` — Get catalog by ID

Returns the catalog with the given ID.

#### `POST /api/v1/catalogs` — Create catalog

Creates a new catalog. The request body is plain JSON (not JSON-LD).

**Minimal request body:**

```json
{
  "title": "My Catalog",
  "participantId": "urn:example:DataProviderA"
}
```

#### `PUT /api/v1/catalogs/{id}` — Update catalog

Performs a partial update: fields not present in the request body retain their existing values.

#### `DELETE /api/v1/catalogs/{id}` — Delete catalog

Deletes the catalog with the given ID.

---

### Dataset — `/api/v1/datasets`

Dataset creation uses **multipart form data** because an artifact (file or URL) must be uploaded at the same time.

#### `GET /api/v1/datasets` — List all datasets

#### `GET /api/v1/datasets/{id}` — Get dataset by ID

#### `GET /api/v1/datasets/{id}/formats` — Get distribution formats

Returns a list of `dct:format` strings from all distributions linked to the dataset. Used when creating Transfer Processes.

**Response data example:** `["HttpData-PULL", "HttpData-PUSH"]`

#### `GET /api/v1/datasets/{id}/artifact` — Get artifact for dataset

Returns the artifact (metadata) associated with a dataset.

#### `POST /api/v1/datasets` — Create dataset

**Content-Type:** `multipart/form-data`

| Part | Required | Description |
|---|---|---|
| `dataset` | Yes | JSON string with dataset metadata |
| `file` | No* | File to store in S3 |
| `url` | No* | URL of externally hosted data |
| `authorization` | No | Authorization header for external URL access |

\* One of `file` or `url` must be provided.

**`dataset` part example:**

```json
{
  "title": "Employee Records Dataset",
  "keyword": ["HR", "Employee"],
  "hasPolicy": [
    {
      "@type": "Offer",
      "permission": [
        {
          "action": "use",
          "constraint": [
            {
              "leftOperand": "count",
              "operator": "lteq",
              "rightOperand": "5"
            }
          ]
        }
      ]
    }
  ],
  "distribution": [
    { "@id": "urn:uuid:<distribution-id>" }
  ]
}
```

#### `PUT /api/v1/datasets/{id}` — Update dataset

Same multipart format as `POST`. If only the artifact is being updated, the `dataset` part may be omitted.

#### `DELETE /api/v1/datasets/{id}` — Delete dataset

Removes the dataset and its associated artifact (including from S3 if applicable).

---

### Distribution — `/api/v1/distributions`

#### `GET /api/v1/distributions` — List all distributions

#### `GET /api/v1/distributions/{id}` — Get distribution by ID

#### `POST /api/v1/distributions` — Create distribution

After saving, the new distribution is automatically added to the catalog's distribution set.

**Request body:**

```json
{
  "format": "HttpData-PULL",
  "title": "HTTP Pull Distribution",
  "accessService": {
    "@id": "urn:uuid:<dataservice-id>"
  }
}
```

The `accessService` field is **required** (`@NotNull`). It must reference an existing `DataService` by ID.

#### `PUT /api/v1/distributions/{id}` — Update distribution

#### `DELETE /api/v1/distributions/{id}` — Delete distribution

After deletion, the distribution reference is removed from the catalog.

---

### DataService — `/api/v1/dataservices`

#### `GET /api/v1/dataservices` — List all data services

#### `GET /api/v1/dataservices/{id}` — Get data service by ID

#### `POST /api/v1/dataservices` — Create data service

After saving, the new data service is automatically added to the catalog's `service` set.

**Request body:**

```json
{
  "title": "DSP TRUE Connector",
  "endpointURL": "http://provider-host:8080/",
  "endpointDescription": "connector",
  "conformsTo": "conformsToSomething",
  "creator": "Engineering Informatica S.p.A."
}
```

The `endpointURL` field is required for protocol validation.

#### `PUT /api/v1/dataservices/{id}` — Update data service

#### `DELETE /api/v1/dataservices/{id}` — Delete data service

---

### Offer — `/api/v1/offers`

#### `POST /api/v1/offers/validate` — Validate offer

Checks whether a given offer matches an offer in the catalog for the specified dataset target. Used during the negotiation flow.

**Request body:**

```json
{
  "@type": "Offer",
  "@id": "urn:uuid:fdc45798-a123-4955-8baf-ab7fd66ac4d5",
  "target": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
  "permission": [
    {
      "action": "use"
    }
  ]
}
```

**Response — `200 OK`** (valid): `{ "success": true, "data": "Offer is valid", "message": "Offer is valid" }`

**Response — `400 Bad Request`** (invalid): `{ "success": false, "message": "Offer not valid" }`

---

### Artifact — `/api/v1/artifacts`

#### `GET /api/v1/artifacts` — List all artifacts

#### `GET /api/v1/artifacts/{artifact}` — Get artifact by ID

---

### Proxy — `/api/v1/proxy`

The proxy endpoints forward requests to a remote provider connector and return the results. They are used by consumer-side tooling to introspect provider catalogs without implementing the DSP protocol directly.

#### `POST /api/v1/proxy/catalogs` — Get remote catalog

Sends a DSP `CatalogRequestMessage` to a remote provider and returns the parsed catalog.

**Request body:**

```json
{
  "Forward-To": "http://provider-host:8080"
}
```

#### `POST /api/v1/proxy/datasets/{id}/formats` — Get dataset formats from remote provider

**Request body:**

```json
{
  "Forward-To": "http://provider-host:8080"
}
```

---

## Data Models

### Catalog

MongoDB collection: `catalogs`

| Field | Type | Required | Description |
|---|---|---|---|
| `@id` | `String` | Auto-generated (URN) | Unique catalog identifier |
| `title` | `String` | No | Human-readable title |
| `description` | `Set<Multilanguage>` | No | Multi-language descriptions |
| `participantId` | `String` | No | DSP participant identifier of the provider |
| `keyword` | `Set<String>` | No | Discovery keywords |
| `theme` | `Set<String>` | No | Subject categories |
| `conformsTo` | `String` | No | Standard or profile conformed to |
| `creator` | `String` | No | Organization that created the catalog |
| `identifier` | `String` | No | Additional identifier |
| `issued` | `Instant` | Auto-set | Creation timestamp |
| `modified` | `Instant` | Auto-set | Last modification timestamp |
| `dataset` | `Set<Dataset>` | Protocol: required | Referenced datasets (`@DBRef`) |
| `distribution` | `Set<Distribution>` | Protocol: required | Top-level distributions (`@DBRef`) |
| `service` | `Set<DataService>` | Protocol: required | Data services (`@DBRef`) |
| `hasPolicy` | `Set<Offer>` | No | Catalog-level usage policies |

**Protocol constraint:** For DSP responses, the catalog must have at least one Dataset, one Distribution, and one DataService.

---

### Dataset

MongoDB collection: `datasets`

| Field | Type | Required | Description |
|---|---|---|---|
| `@id` | `String` | Auto-generated (URN) | Unique dataset identifier |
| `title` | `String` | No | Human-readable title |
| `description` | `Set<Multilanguage>` | No | Multi-language descriptions |
| `keyword` | `Set<String>` | No | Discovery keywords |
| `theme` | `Set<String>` | No | Subject categories |
| `conformsTo` | `String` | No | Standard or profile conformed to |
| `creator` | `String` | No | Creator organization |
| `identifier` | `String` | No | Additional identifier |
| `issued` | `Instant` | Auto-set | Creation timestamp |
| `modified` | `Instant` | Auto-set | Last modification timestamp |
| `hasPolicy` | `Set<Offer>` | **Required** (`@NotNull`) | Usage policies (Offers) |
| `distribution` | `Set<Distribution>` | Protocol: required | Access distributions (`@DBRef`) |
| `artifact` | `Artifact` | Required for upload | Actual data artifact — not exposed in JSON responses (`@JsonIgnore`) |

---

### Distribution

MongoDB collection: `distributions`

| Field | Type | Required | Description |
|---|---|---|---|
| `@id` | `String` | Auto-generated (URN) | Unique distribution identifier |
| `format` | `String` | No | Transfer format (e.g., `HttpData-PULL`, `HttpData-PUSH`) |
| `title` | `String` | No | Human-readable title |
| `description` | `Set<Multilanguage>` | No | Multi-language descriptions |
| `issued` | `Instant` | Auto-set | Creation timestamp |
| `modified` | `Instant` | Auto-set | Last modification timestamp |
| `accessService` | `DataService` | **Required** (`@NotNull`) | The endpoint providing access (`@DBRef`) |
| `hasPolicy` | `Set<Offer>` | No | Distribution-level policies |

---

### DataService

MongoDB collection: `dataservices`

| Field | Type | Required | Description |
|---|---|---|---|
| `@id` | `String` | Auto-generated (URN) | Unique data service identifier |
| `title` | `String` | No | Human-readable title |
| `endpointURL` | `String` | Protocol: required | The connector endpoint URL |
| `endpointDescription` | `String` | No | Description of the endpoint |
| `conformsTo` | `String` | No | Protocol/profile conformed to |
| `creator` | `String` | No | Creator organization |
| `identifier` | `String` | No | Additional identifier |
| `keyword` | `Set<String>` | No | Keywords |
| `theme` | `Set<String>` | No | Subject categories |
| `issued` | `Instant` | Auto-set | Creation timestamp |
| `modified` | `Instant` | Auto-set | Last modification timestamp |

**Protocol constraint:** `endpointURL` must be non-null for protocol validation.

---

### Offer

Embedded in Dataset and Distribution (not a standalone MongoDB document).

| Field | Type | Required | Description |
|---|---|---|---|
| `@id` | `String` | Auto-generated (URN) | Unique offer identifier |
| `target` | `String` | No | ID of the target dataset (required in negotiation flow) |
| `assigner` | `String` | No | Party granting the permission |
| `assignee` | `String` | No | Party receiving the permission |
| `permission` | `Set<Permission>` | **Required** (`@NotNull`) | The permissions granted |

---

### Permission

Embedded in Offer.

| Field | Type | Required | Description |
|---|---|---|---|
| `action` | `Action` (enum) | **Required** | The permitted action |
| `constraint` | `Set<Constraint>` | No | Constraints limiting the action |
| `assigner` | `String` | No | Party granting permission |
| `assignee` | `String` | No | Party receiving permission |
| `target` | `String` | No | Target of the permission |

**Supported `Action` values:** `use`, `anonymize`

---

### Constraint

Embedded in Permission.

| Field | Type | Description |
|---|---|---|
| `leftOperand` | `LeftOperand` (enum) | The attribute being constrained |
| `operator` | `Operator` (enum) | The comparison operator |
| `rightOperand` | `String` | The value to compare against |

**Supported `LeftOperand` values:** `count`, `dateTime`

**Supported `Operator` values:** `eq`, `gt`, `gteq`, `hasPart`, `isA`, `isAllOf`, `isAnyOf`, `isNoneOf`, `isPartOf`, `lt`, `lteq`, `neq`

---

### Multilanguage

Embedded string value with a language tag.

```json
{ "value": "A description in English", "@language": "en" }
```

---

## Key Services

### `CatalogService`

- **`getCatalog()`** — Protocol method. Fetches the catalog, filters out datasets with missing S3 files, runs full protocol validation (`validateProtocol()`), and throws `CatalogErrorException` on failure. Publishes audit events on validation failure.
- **`getCatalogForApi()`** — API method. Returns the first catalog without protocol validation; throws `ResourceNotFoundAPIException` if absent.
- **`saveCatalog(Catalog)`** — Persists a catalog to MongoDB.
- **`getCatalogById(String)`** / **`deleteCatalog(String)`** / **`updateCatalog(String, Catalog)`** — Standard CRUD.
- **`updateCatalogDatasetAfterSave(Dataset)`** / **`updateCatalogDatasetAfterDelete(Dataset)`** — Called by `DatasetService` to keep the catalog's dataset references consistent.
- **`updateCatalogDistributionAfterSave(Distribution)`** / **`updateCatalogDistributionAfterDelete(Distribution)`** — Called by `DistributionService`.
- **`updateCatalogDataServiceAfterSave(DataService)`** / **`updateCatalogDataServiceAfterDelete(DataService)`** — Called by `DataServiceService`.
- **`validateOffer(Offer)`** — Looks up the offer by ID within the dataset matching `offer.target`. Returns `true` if found, `false` otherwise. Used by the negotiation module.

---

### `DatasetService`

- **`getDatasetById(String)`** — Protocol method. Validates S3 file presence and runs `dataset.validateProtocol()`. Throws `CatalogErrorException` on failure.
- **`getDatasetByIdForApi(String)`** — API method without protocol validation.
- **`getAllDatasets()`** — Returns all datasets.
- **`saveDataset(Dataset, MultipartFile, String, String)`** — Uploads the artifact (file or URL) via `ArtifactService`, attaches it to the dataset, persists, and updates the catalog reference.
- **`updateDataset(String, Dataset, MultipartFile, String, String)`** — Updates dataset metadata and optionally replaces the artifact.
- **`deleteDataset(String)`** — Deletes dataset and its artifact; removes catalog reference.
- **`getFormatsFromDataset(String)`** — Returns list of `format` values from the dataset's distributions.
- **`getArtifactFromDataset(String)`** — Returns the artifact associated with a dataset.

---

### `DistributionService`

- **`saveDistribution(Distribution)`** — Persists distribution and calls `CatalogService.updateCatalogDistributionAfterSave()`.
- **`deleteDistribution(String)`** — Deletes distribution and calls `CatalogService.updateCatalogDistributionAfterDelete()`.
- **`updateDistribution(String, Distribution)`** — Partial update preserving unchanged fields.
- **`getDistributionById(String)`** / **`getAllDistributions()`** — Read operations.

---

### `DataServiceService`

- **`saveDataService(DataService)`** — Persists data service and calls `CatalogService.updateCatalogDataServiceAfterSave()`.
- **`deleteDataService(String)`** — Deletes data service and calls `CatalogService.updateCatalogDataServiceAfterDelete()`.
- **`updateDataService(String, DataService)`** — Partial update.
- **`getDataServiceById(String)`** / **`getAllDataServices()`** — Read operations.

---

### `ArtifactService`

- **`uploadArtifact(String, MultipartFile, String, String)`** — Handles artifact upload. If `file` is provided, stores it in S3 and records an `ArtifactType.FILE` artifact. If `externalURL` is provided, records an `ArtifactType.EXTERNAL` artifact. Throws `CatalogErrorAPIException` if neither is provided.
- **`getArtifacts(String)`** — Returns one or all artifacts.
- **`deleteArtifact(Artifact)`** — Deletes artifact from MongoDB and, for FILE type, removes the file from S3.

---

### `ProxyAPIService`

- **`getCatalog(String forwardTo)`** — Sends a DSP `CatalogRequestMessage` to `{forwardTo}/catalog/request` and deserializes the response.
- **`getFormatsFromDataset(String, String)`** — Fetches the remote catalog and extracts formats for the specified dataset.

---

## Error Handling

Two `@RestControllerAdvice` classes handle exceptions:

### `CatalogExceptionAdvice` (applies to protocol controller)

Returns DSP-compliant `CatalogError` JSON-LD bodies.

| Exception | HTTP Status |
|---|---|
| `CatalogErrorException` | `404 Not Found` |
| `ValidationException` | `400 Bad Request` |

### `CatalogAPIExceptionAdvice` (applies to management API controllers)

Returns `GenericApiResponse` error bodies.

| Exception | HTTP Status |
|---|---|
| `ResourceNotFoundAPIException` | `404 Not Found` |
| `CatalogErrorAPIException` | `400 Bad Request` |
| `InternalServerErrorAPIException` | `500 Internal Server Error` |
| `ValidationException` | `400 Bad Request` |

---

## DSP Message Types

| Class | DSP Type | Direction | Usage |
|---|---|---|---|
| `CatalogRequestMessage` | `CatalogRequestMessage` | Inbound | Received on `POST /catalog/request` |
| `Catalog` | `Catalog` | Outbound | Returned from `POST /catalog/request` |
| `Dataset` | `Dataset` | Outbound | Returned from `GET /catalog/datasets/{id}` |
| `CatalogError` | `CatalogError` | Outbound | Returned on protocol-layer errors |
| `DatasetRequestMessage` | `DatasetRequestMessage` | Model only | Deserialized but not currently used in routing |
