# Tools Module — Technical Documentation

> **See also:** [Implementation Guide](./tools-implementation.md) | [Application Properties API](./application-property.md) | [Generic Filtering](./generic-filtering.md)

## Overview

The `tools` module provides shared utilities, services, and infrastructure used by all other modules in the TRUE Connector. It is **not** a DSP protocol module — it provides implementation-specific operational utilities: S3 storage support, TLS/OCSP certificate validation, application property management, artifact management, audit event infrastructure, field encryption, and generic query filtering.

> **Note:** DAPS (Dynamic Attribute Provisioning Service) classes exist in the `tools/daps/` package but are being removed and are excluded from this documentation.

---

## Architecture

### Package Structure

| Package | Role |
|---|---|
| `s3/` | S3 / object-storage utilities, client provisioning, bucket credential management |
| `s3/configuration/` | AWS SDK client factory and MinIO admin client |
| `s3/model/` | S3-specific models (`BucketCredentialsEntity`, `S3ClientRequest`, `S3UploadMode`) |
| `s3/properties/` | `S3Properties` — typed Spring `@ConfigurationProperties` |
| `s3/repository/` | `BucketCredentialsRepository` — MongoDB repository |
| `s3/service/` | S3 business logic: credential management, upload strategies, user management |
| `ssl/ocsp/` | Online Certificate Status Protocol (OCSP) validation chain |
| `configuration/` | Spring infrastructure configuration: global SSL, OkHttp client, async events, HATEOAS |
| `service/` | Shared application services: property management, audit events, field encryption, filter building, request info |
| `repository/` | Shared MongoDB repositories: properties, artifacts, audit events, generic dynamic filter |
| `model/` | Shared domain models: `ApplicationProperty`, `Artifact`, `ArtifactType`, `RequestInfo`, constants |
| `event/` | Cross-module Spring application events: audit, property change, negotiation, transfer, policy enforcement |
| `rest/api/` | REST controllers for properties API and audit events API |
| `filter/` | Servlet filter for populating per-request context |
| `util/` | Lightweight utilities: `CredentialUtils`, `CSVUtil`, `ToolsUtil` |
| `controller/` | `ApiEndpoints` — central registry of all module URL paths |
| `property/` | `ApplicationPropertyKeys`, `ConnectorProperties` |
| `exception/` | Shared exception types and `ExceptionAPIAdvice` |
| `response/` | `GenericApiResponse` — standardised API response wrapper |

---

## S3 Storage Utilities

### S3Utils

`it.eng.tools.s3.util.S3Utils` is a marker interface that defines the well-known key names used when constructing `DataAddress` maps for S3-backed data transfers. No methods — only `public static final String` constants.

| Constant | Value | Purpose |
|---|---|---|
| `BUCKET_NAME` | `"bucketName"` | Name of the target S3 bucket |
| `REGION` | `"region"` | AWS/MinIO region |
| `OBJECT_KEY` | `"objectKey"` | Path/key of the object inside the bucket |
| `ACCESS_KEY` | `"accessKey"` | S3 access key identifier |
| `SECRET_KEY` | `"secretKey"` | S3 secret key |
| `ENDPOINT_OVERRIDE` | `"endpointOverride"` | Custom endpoint URL (for MinIO or non-AWS S3) |

These constants are used by data-transfer logic to build the `DataAddress` property map that is exchanged between provider and consumer during HTTP-PUSH transfers.

### BucketCredentialsRepository

`it.eng.tools.s3.repository.BucketCredentialsRepository` is a Spring Data MongoDB repository for `BucketCredentialsEntity` (stored in the `bucket_credentials` collection).

```
MongoRepository<BucketCredentialsEntity, String>
  + findByBucketName(String bucketName): Optional<BucketCredentialsEntity>
```

The `bucketName` field doubles as the MongoDB `@Id`. The `secretKey` field is marked with `@Encrypted` and is stored encrypted using `FieldEncryptionService`.

### BucketCredentialsService

`it.eng.tools.s3.service.BucketCredentialsService` wraps the repository, transparently encrypting on save and decrypting on retrieval using `FieldEncryptionService`:

- `getBucketCredentials(bucketName)` — retrieves and decrypts credentials; throws `S3ServerException` if not found.
- `saveBucketCredentials(entity)` — encrypts the secret key before persisting.
- `bucketCredentialsExist(bucketName)` — existence check without decryption.

### S3 Configuration

`S3Properties` (`it.eng.tools.s3.properties.S3Properties`) is bound with prefix `s3`:

| Property | Type | Default | Description |
|---|---|---|---|
| `s3.endpoint` | String | — | S3 / MinIO endpoint URL |
| `s3.accessKey` | String | — | S3 access key |
| `s3.secretKey` | String | — | S3 secret key |
| `s3.region` | String | — | Storage region |
| `s3.bucketName` | String | — | Default bucket name |
| `s3.externalPresignedEndpoint` | String | — | External URL used for generating pre-signed URLs |
| `s3.uploadMode` | String | `SYNC` | Upload strategy: `SYNC` or `ASYNC` |

`S3Configuration` (`it.eng.tools.s3.configuration.S3Configuration`) creates a `MinioAdminClient` bean. The bean is created only when `s3.endpoint` is set **and** the endpoint does not contain `amazonaws.com`, ensuring that MinIO-specific admin APIs are not attempted against AWS S3.

`S3ClientProvider` (`it.eng.tools.s3.configuration.S3ClientProvider`) manages pools of `S3Client` (synchronous) and `S3AsyncClient` (asynchronous) instances using `ConcurrentHashMap` caches. Clients are keyed by region and endpoint, reducing the overhead of client creation per request.

#### S3 Upload Modes

`S3UploadMode` controls which upload strategy is used:

- **`SYNC`** — uses `S3Client` (sequential multipart upload). More compatible with reverse proxies such as Caddy or NGINX. This is the default.
- **`ASYNC`** — uses `S3AsyncClient` (parallel multipart upload). Faster for large objects but may have compatibility issues with some reverse-proxy configurations.

---

## TLS / HTTPS Client Configuration

### GlobalSSLConfiguration

`it.eng.tools.configuration.GlobalSSLConfiguration` runs at startup (`@PostConstruct`) and configures the global `HttpsURLConnection` defaults.

- When `server.ssl.enabled=true`: loads the Spring SSL bundle named `"connector"` (defined via `spring.ssl.bundle.jks.connector.*`) and sets a strict `SSLSocketFactory` with hostname verification.
- When `server.ssl.enabled=false`: installs a trust-all `X509TrustManager` and a permissive `HostnameVerifier`. **Only for development.**

It also exposes the connector's `PublicKey`, `PrivateKey`, and `KeyPair` via getters so other components can sign or verify tokens without re-loading the keystore.

### OkHttpClientConfiguration

`it.eng.tools.configuration.OkHttpClientConfiguration` produces the application-wide `OkHttpClient` bean used for all outbound HTTP calls (connector-to-connector, connector-to-S3, etc.).

- When `server.ssl.enabled=true`: creates a secure client backed by OCSP-enabled trust managers from `OcspTrustManagerFactory`, with `MODERN_TLS` connection spec, 60-second timeouts, and strict hostname verification.
- When `server.ssl.enabled=false`: creates an insecure client that accepts all certificates. **Only for development.**

SSL bundle configuration (`spring.ssl.bundle.jks.connector.*`) drives the truststore and keystore used by both `GlobalSSLConfiguration` and `OcspTrustManagerFactory`.

---

## OCSP Certificate Validation

OCSP (Online Certificate Status Protocol) is a real-time mechanism for checking whether an X.509 certificate has been revoked by its Certificate Authority. The connector can validate the revocation status of TLS peer certificates on every outbound connection.

### OcspValidator

`it.eng.tools.ssl.ocsp.OcspValidator` is the core, stateless validator. For a given `(certificate, issuerCertificate)` pair it:

1. Extracts the OCSP responder URL from the certificate's `AuthorityInfoAccess` extension.
2. Builds a `OCSPReq` using the Bouncy Castle library.
3. Issues an HTTP POST to the OCSP responder (`application/ocsp-request`) with a 10-second timeout via `java.net.http.HttpClient`.
4. Parses the `OCSPResp` and maps it to `OcspValidationResult`.

Returns `OcspValidationResult` with one of four statuses: `GOOD`, `REVOKED`, `UNKNOWN`, or `ERROR`.

### CachedOcspValidator

`it.eng.tools.ssl.ocsp.CachedOcspValidator` (@Component) wraps `OcspValidator` with a Caffeine cache to avoid redundant OCSP round-trips.

Cache behaviour:
- **Maximum size:** 1 000 entries.
- **Hard expiry:** 24 hours (safety cap).
- **Soft expiry:** uses `nextUpdate` from the OCSP response. Falls back to `defaultCacheDurationMinutes` (configurable) when `nextUpdate` is absent.
- **Error results are not cached** — a failed lookup is retried on the next request.

If `softFail=true` (configurable), any validation failure or exception returns `true`, allowing the connection to proceed.

### OcspProperties

`it.eng.tools.ssl.ocsp.OcspProperties` is bound with prefix `application.ocsp.validation`:

| Property | Type | Description |
|---|---|---|
| `application.ocsp.validation.enabled` | boolean | Enable OCSP validation |
| `application.ocsp.validation.softFail` | boolean | Allow connections even if OCSP fails |
| `application.ocsp.validation.defaultCacheDurationMinutes` | long | Cache TTL when OCSP response has no `nextUpdate` |
| `application.ocsp.validation.timeoutSeconds` | int | HTTP timeout for OCSP responder requests |

### OcspTrustManagerFactory / OcspX509TrustManager

`OcspTrustManagerFactory` (@Component) creates arrays of `TrustManager[]` that wrap the standard trust managers from the `"connector"` SSL bundle with `OcspX509TrustManager`.

`OcspX509TrustManager` extends `X509ExtendedTrustManager` and delegates standard certificate chain validation to the wrapped `delegate`. After delegation succeeds, it calls `CachedOcspValidator.validate()` for each certificate in the chain except the root CA. If validation fails and `softFail=false`, a `CertificateException` is thrown and the TLS handshake is rejected.

The factory is consumed by `OkHttpClientConfiguration` to build the application-wide `OkHttpClient`.

### OcspValidationResult / OcspValidationStatus

`OcspValidationResult` is a value object returned by `OcspValidator.validate()`:

| Field | Type | Description |
|---|---|---|
| `status` | `OcspValidationStatus` | `GOOD`, `REVOKED`, `UNKNOWN`, or `ERROR` |
| `thisUpdate` | `Date` | When the OCSP response was issued |
| `nextUpdate` | `Date` | When a fresh OCSP response should be fetched |
| `errorMessage` | `String` | Non-null only for `ERROR` status |

`OcspValidationStatus` values:

| Value | Meaning |
|---|---|
| `GOOD` | Certificate is valid and not revoked |
| `REVOKED` | Certificate has been explicitly revoked by the CA |
| `UNKNOWN` | CA has no information about the certificate |
| `ERROR` | An I/O or parsing error occurred during the OCSP check |

---

## Application Properties Management

### ApplicationProperty Model

`it.eng.tools.model.ApplicationProperty` is a MongoDB document (collection `application_properties`). The `key` field is the `@Id`.

| Field | JSON key | Description |
|---|---|---|
| `key` | `key` | Unique property key (e.g. `application.automatic.negotiation`) |
| `value` | `value` | Current property value (always a String) |
| `label` | `label` | Human-readable label for UI display |
| `group` | `group` | Grouping for UI organisation |
| `tooltip` | `tooltip` | UI tooltip text |
| `sampleValue` | `sampleValue` | Example value shown in UI |
| `mandatory` | `mandatory` | Whether the property is required |
| `issued` | *(auto)* | Creation timestamp (`@CreatedDate`) |
| `modified` | *(auto)* | Last modification timestamp (`@LastModifiedDate`) |

`createdBy`, `lastModifiedBy`, and `version` are stored in MongoDB but excluded from JSON responses (`@JsonIgnore`). The builder validates `@NotNull` constraints before constructing an instance.

### ApplicationPropertiesService

`it.eng.tools.service.ApplicationPropertiesService` is the primary service for property CRUD.

| Method | Description |
|---|---|
| `getProperties(key_prefix)` | Returns all properties (sorted by group, then key), or filtered by prefix using `findByKeyStartsWith` |
| `getPropertyByKey(key)` | Returns a single property by key; if absent from MongoDB, falls back to `Environment` and persists it |
| `updateProperty(property, oldOne)` | Updates the value of an existing property and triggers an audit event |
| `updateProperties(List)` | Batch update; fires `ApplicationPropertyChangeEvent` for each changed property |
| `addPropertyOnEnv(key, value, env)` | Injects a key/value pair into the live Spring `Environment` as the highest-priority source (`storedApplicationProperties`) |
| `copyApplicationPropertiesToEnvironment(env)` | Reloads all MongoDB properties into the Spring `Environment` at startup |
| `get(key)` | Thin wrapper over `Environment.getProperty()` |

### ApplicationPropertiesRepository

`it.eng.tools.repository.ApplicationPropertiesRepository` extends `MongoRepository<ApplicationProperty, String>`.

| Method | Description |
|---|---|
| `findById(id)` | Lookup by key |
| `findByKeyStartsWith(key_prefix, sort)` | Prefix-filtered list, with caller-supplied sort order |

### Property Change Events

When a property value is updated via `updateProperties()`:

1. `ApplicationPropertiesService` compares old and new values; if they differ it publishes an `ApplicationPropertyChangeEvent` (via `AuditEventPublisher`).
2. `ApplicationPropertiesEventListener` (@Component) handles `@EventListener` and logs the change (old value → new value).
3. The new value is also pushed into the live Spring `Environment` via `addPropertyOnEnv()`, so any subsequent calls to `Environment.getProperty()` return the new value immediately — without a restart.

`ApplicationPropertyChangeEvent` carries:
- `oldValue` — the previous `ApplicationProperty`
- `newValue` — the updated `ApplicationProperty`
- `authentication` — the Spring Security `Authentication` of the user who made the change

---

## Artifact Management

### Artifact Model

`it.eng.tools.model.Artifact` is a MongoDB document (collection `artifacts`).

| Field | Type | Description |
|---|---|---|
| `id` | String | Auto-generated `urn:uuid:…` if not provided |
| `artifactType` | `ArtifactType` | `FILE` or `EXTERNAL` |
| `filename` | String | Original filename (for `FILE` type) |
| `value` | String | Depends on type: GridFS file ID (`FILE`) or URL (`EXTERNAL`) |
| `authorization` | String | Optional authorisation header value for external resource access |
| `contentType` | String | MIME type (`Content-Type` header) |
| `created` | Instant | Creation timestamp |
| `lastModifiedDate` | Instant | Last modification timestamp |

### ArtifactType Enum

`it.eng.tools.model.ArtifactType`

| Enum value | String value | Meaning |
|---|---|---|
| `FILE` | `"file"` | Binary file stored in the database (GridFS or equivalent) |
| `EXTERNAL` | `"external"` | URL pointing to an externally hosted resource |

### ArtifactRepository

`it.eng.tools.repository.ArtifactRepository` extends `MongoRepository<Artifact, String>`. Currently exposes only the standard CRUD operations from `MongoRepository`; no custom query methods.

---

## Audit Events

### AuditEvent Model

`it.eng.tools.event.AuditEvent` is a MongoDB document (collection `audit_events`).

| Field | Type | Description |
|---|---|---|
| `id` | String | Auto-generated MongoDB ID |
| `eventType` | `AuditEventType` | Category of the event |
| `username` | String | User who triggered the event |
| `timestamp` | `LocalDateTime` | Time of the event (defaults to `LocalDateTime.now()`) |
| `description` | String | Human-readable description |
| `details` | `Map<String, Object>` | Flexible additional data (e.g. negotiation state, contract ID) |
| `source` | String | Component/module or remote host that originated the event |
| `ipAddress` | String | Client IP address |

### AuditEventType Enum

`it.eng.tools.event.AuditEventType` — all values with their display labels:

| Enum constant | Display label |
|---|---|
| `APPLICATION_START` | Application start |
| `APPLICATION_STOP` | Application stop |
| `APPLICATION_LOGIN` | Login |
| `APPLICATION_LOGOUT` | Logout |
| `PROTOCOL_CATALOG_CATALOG_NOT_FOUND` | Catalog not found |
| `PROTOCOL_CATALOG_DATASET_NOT_FOUND` | Dataset not found |
| `PROTOCOL_NEGOTIATION_CONTRACT_NEGOTIATION` | Contract negotiation |
| `PROTOCOL_NEGOTIATION_NOT_FOUND` | Contract negotiation not found |
| `PROTOCOL_NEGOTIATION_STATE_TRANSITION_ERROR` | State transition invalid |
| `PROTOCOL_NEGOTIATION_REQUESTED` | Protocol negotiation requested |
| `PROTOCOL_NEGOTIATION_OFFERED` | Protocol negotiation offered |
| `PROTOCOL_NEGOTIATION_ACCEPTED` | Protocol negotiation accepted |
| `PROTOCOL_NEGOTIATION_AGREED` | Protocol negotiation agreed |
| `PROTOCOL_NEGOTIATION_VERIFIED` | Protocol negotiation verified |
| `PROTOCOL_NEGOTIATION_FINALIZED` | Protocol negotiation finalized |
| `PROTOCOL_NEGOTIATION_TERMINATED` | Protocol negotiation terminated |
| `PROTOCOL_NEGOTIATION_REJECTED` | Protocol negotiation rejected |
| `PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DISABLED` | Policy evaluation disabled |
| `PROTOCOL_NEGOTIATION_POLICY_EVALUATION_APPROVE` | Policy evaluation approved |
| `PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED` | Policy evaluation denied |
| `PROTOCOL_NEGOTIATION_INVALID_OFFER` | Protocol negotiation offer not valid |
| `PROTOCOL_TRANSFER_NOT_FOUND` | Transfer not found |
| `PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR` | State transition invalid |
| `PROTOCOL_TRANSFER_REQUESTED` | Transfer requested |
| `PROTOCOL_TRANSFER_STARTED` | Transfer started |
| `PROTOCOL_TRANSFER_COMPLETED` | Transfer completed |
| `PROTOCOL_TRANSFER_SUSPENDED` | Transfer suspended |
| `PROTOCOL_TRANSFER_TERMINATED` | Transfer terminated |
| `TRANSFER_VIEW` | Transfer completed |
| `TRANSFER_COMPLETED` | Transfer completed |
| `TRANSFER_FAILED` | Transfer failed |
| `NEGOTIATION_ACCESS_COUNT_INCREASE` | Access count increase |

### AuditEventService / AuditEventPublisher

`AuditEventPublisher` (@Service) is the single entry point for recording events. Callers (any service in any module) invoke:

```java
auditEventPublisher.publishEvent(eventType, description, details);
```

The publisher:
1. Resolves the current `RequestInfo` (IP address, username, remote host) from `RequestContextHolder`.
2. Builds an `AuditEvent` populated with event type, description, details, and request metadata.
3. Publishes it via Spring's `ApplicationEventPublisher`.

`AuditEventListener` (@Component with `@EventListener`) receives the event and persists it to MongoDB through `AuditEventRepository`.

`AuditEventService` provides query access:
- `getAuditEvents(filters, pageable)` — paginated, dynamically filtered list.
- `getAuditEventById(id)` — single event lookup.
- `getAuditEventTypes()` — returns all `AuditEventType` values as DTOs for API consumers.

---

## Utility Classes

### CredentialUtils

`it.eng.tools.util.CredentialUtils` (@Component) provides HTTP Basic `Authorization` header values for internal use:

- `getConnectorCredentials()` — returns Basic credentials for connector-to-connector calls.
- `getAPICredentials()` — returns Basic credentials for API calls.

> **Note:** These are placeholder credentials pending full authentication integration.

### CSVUtil

`it.eng.tools.util.CSVUtil` — static utility:

- `toListString(csvInput)` — splits a comma-separated string into a `List<String>`, preserving empty tokens; returns `null` for blank input.

### ToolsUtil

`it.eng.tools.util.ToolsUtil` — static utility:

- `generateUniqueId()` — generates `"urn:uuid:"` + `UUID.randomUUID()`. Used to assign identifiers to `Artifact` and other entities.

### FieldEncryptionService

`it.eng.tools.service.FieldEncryptionService` (@Service) provides symmetric AES-256-CBC encryption for sensitive fields stored in MongoDB (such as S3 secret keys).

- **Algorithm:** AES / CBC / PKCS7 padding (via Bouncy Castle).
- **Key derivation:** SHA-256 digest of `application.encryption.key` (configured property).
- **IV derivation:** MD5 digest of the same key.
- `encrypt(value)` — returns a Base64-encoded ciphertext.
- `decrypt(encrypted)` — decodes Base64, decrypts, and trims the result.

The encryption key must be supplied via `application.encryption.key`. The same key must be used for the lifetime of the database — changing the key will make previously encrypted values unreadable.

### GenericFilterBuilder

`it.eng.tools.service.GenericFilterBuilder` (@Component) converts `HttpServletRequest` query parameters into a typed `Map<String, Object>` suitable for dynamic MongoDB queries.

**Type conversion rules (strict):**

| Input pattern | Converted to | Example |
|---|---|---|
| `true`, `false`, `yes`, `no` | `Boolean` | `?active=true` |
| ISO-8601 with timezone | `Instant` | `?timestamp.from=2025-01-01T00:00:00Z` |
| `YYYY-MM-DD` | `Instant` (start of day UTC) | `?date=2025-01-01` |
| 10–13 digit number | `Instant` (epoch seconds/ms) | — |
| Integer (1–9 digits) | `Long` | `?count=5` |
| Decimal | `Double` | `?score=3.14` |
| Anything else | `String` | `?state=FINALIZED` |

**Range queries:** parameters of the form `field.from` and `field.to` are merged into a range map under `field`.

**Security:** MongoDB operators (`$`-prefixed names), SQL injection patterns, and more than 50 parameters per request are all rejected.

Multiple values for the same parameter name are collected into a `List` (triggers an `$in` query in MongoDB).

### RequestInformationContextFilter

`it.eng.tools.filter.RequestInformationContextFilter` (@Component) is a `OncePerRequestFilter` that:

1. Calls `RequestInfoService.getRequestInfo(request)` to extract method, remote IP (respecting `X-Forwarded-For`), remote host, and username.
2. Stores the result in `RequestContextHolder` (a `ThreadLocal`).
3. Clears the holder in a `finally` block to prevent memory leaks.

Any code that runs within the same request thread can then call `requestInfoService.getCurrentRequestInfo()` to retrieve the request context without requiring it to be passed down explicitly.

`RequestInfo` fields:

| Field | Description |
|---|---|
| `method` | HTTP method (`GET`, `PUT`, etc.) |
| `remoteAddress` | Client IP, resolved from `X-Forwarded-For`, `Proxy-Client-IP`, or `getRemoteAddr()` |
| `remoteHost` | Client hostname from `getRemoteHost()` |
| `username` | Username from `getRemoteUser()` |

---

## Constants

### ApiEndpoints

`it.eng.tools.controller.ApiEndpoints` is an interface of static String constants defining all module URL prefixes:

| Constant | Value |
|---|---|
| `CATALOG_CATALOGS_V1` | `/api/v1/catalogs` |
| `CATALOG_DATA_SERVICES_V1` | `/api/v1/dataservices` |
| `CATALOG_DATASETS_V1` | `/api/v1/datasets` |
| `CATALOG_DISTRIBUTIONS_V1` | `/api/v1/distributions` |
| `CATALOG_OFFERS_V1` | `/api/v1/offers` |
| `CATALOG_ARTIFACT_V1` | `/api/v1/artifacts` |
| `NEGOTIATION_V1` | `/api/v1/negotiations` |
| `NEGOTIATION_AGREEMENTS_V1` | `/api/v1/agreements` |
| `TRANSFER_DATATRANSFER_V1` | `/api/v1/transfers` |
| `PROXY_V1` | `/api/v1/proxy` |
| `USERS_V1` | `/api/v1/users` |
| `PROPERTIES_V1` | `/api/v1/properties` |
| `AUDIT_V1` | `/api/v1/audit` |

### DSpaceConstants

`it.eng.tools.model.DSpaceConstants` defines DSP protocol enumerations and JSON-LD field name strings used across all modules:

- **`ContractNegotiationStates`:** `REQUESTED`, `OFFERED`, `ACCEPTED`, `AGREED`, `VERIFIED`, `FINALIZED`, `TERMINATED`
- **`DataTransferStates`:** `INITIALIZED`, `REQUESTED`, `STARTED`, `COMPLETED`, `SUSPENDED`, `TERMINATED`
- **`ContractNegotiationEvent`:** `ACCEPTED`, `FINALIZED`
- **`Operators`:** ODRL policy operators (`EQ`, `GT`, `LT`, `IS_A`, `IS_ANY_OF`, etc.)
- JSON-LD keys: `@context`, `@id`, `@type`, `callbackAddress`, `consumerPid`, `providerPid`, `dataAddress`, `endpointType`, `agreement`, `offer`, `permission`, `constraint`, etc.

### IConstants

`it.eng.tools.model.IConstants` defines internal field name strings and role identifiers:

- Field names: `KEY`, `VALUE`, `MANDATORY`, `SAMPLE_VALUE`, `LABEL`, `GROUP`, `TOOLTIP`, `TYPE`, `ARTIFACT_TYPE`
- Roles: `ROLE_CONSUMER`, `ROLE_PROVIDER`, `ROLE_API`, `ROLE_PROTOCOL`
- Auth types: `AUTH_BEARER`, `AUTH_BASIC`

---

## REST Controllers

Two REST controllers are part of the tools module and are served at application level (not scoped to a DSP protocol path).

### ApplicationPropertiesAPIController

Base path: `/api/v1/properties`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/properties/` | List all properties, or filter by `?key_prefix=…` |
| `PUT` | `/api/v1/properties/` | Batch update: accepts a JSON array of `ApplicationProperty` |

Responses are wrapped in `GenericApiResponse`. See [Application Properties API](./application-property.md) for full curl examples.

### AuditEventController

Base path: `/api/v1/audit`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/audit` | Paginated, dynamically filtered list of audit events |
| `GET` | `/api/v1/audit/{auditEventId}` | Retrieve single audit event by ID |
| `GET` | `/api/v1/audit/types` | List all available `AuditEventType` values |

Pagination defaults: `page=0`, `size=20`, `sort=timestamp,desc`. All query parameters (except `page`, `size`, `sort`) are passed to `GenericFilterBuilder` and used as dynamic MongoDB filters.

---

## Cross-Module Events

The following Spring application events are defined in the tools module and consumed by other modules. They are published using `ApplicationEventPublisher` (synchronous by default; the `AsynchronousSpringEventsConfig` enables async event delivery).

### ContractNegotationOfferRequestEvent

Published by: **Negotiation module** when an incoming offer needs to be evaluated.  
Consumed by: **Catalog module** to compare the received offer against the stored catalog offer.

Fields: `consumerPid`, `providerPid`, `offer` (serialised `JsonNode`).

### ContractNegotiationOfferResponseEvent

Published by: **Catalog module** after evaluating the offer.  
Consumed by: **Negotiation module** to proceed with or reject the negotiation.

Fields: `consumerPid`, `providerPid`, `offerAccepted` (boolean), `offer` (`JsonNode`).

### InitializeTransferProcess

Published by: **Negotiation module** upon successful contract finalisation.  
Consumed by: **Data-Transfer module** to create a new `TransferProcess` with `INITIALIZED` state.

Fields: `callbackAddress`, `agreementId`, `datasetId`, `role`.

### ArtifactConsumedEvent

Published by: **Data-Transfer module** when data has been successfully transferred.  
Consumed by: **Policy enforcement / usage control** to track access counts.

Fields: `agreementId`.

---

## See Also

- [Application Properties API](./application-property.md) — full curl examples for the properties API
- [Generic Filtering](./generic-filtering.md) — audit event filtering examples
- [Implementation Guide](./tools-implementation.md) — operator-oriented guide for configuring the tools module
