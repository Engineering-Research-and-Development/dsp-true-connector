# Data Plane Signaling — Technical Reference

## Overview

TRUE Connector implements the
[Eclipse Dataplane Signaling Protocol (DPS)](https://github.com/eclipse-dataplane-signaling/dataplane-signaling).
The Control Plane (CP) and each Data Plane (DP) communicate over REST, with the CP
acting as the orchestrator and each DP as an independent service.

---

## Architecture

```
Consumer CP                Provider CP               Consumer DP
    |                          |                          |
    |-- TransferRequestMsg --->|                          |
    |                          | (generate presigned URL  |
    |                          |  via S3ClientService)    |
    |<-- TransferStartMsg(url)--|                          |
    |                          |                          |
    | (admin triggers download) |                          |
    |                          |                          |
    |<-- POST /dataflows/start  (HTTP-PULL: download artifact from presigned URL)
    |-- artifact --> consumer S3
    |-- POST /api/v1/transfers/{id}/dataflow/completed --> Consumer CP
    |                          |
    Consumer CP → COMPLETED    |
```

For HTTP-PUSH the flow is different — see the [HTTP-PUSH Transfer Flow](#http-push) section.

### Modules

| Module | Role | Artifact |
|---|---|---|
| `data-plane-api` | SPI interfaces + DPS message models | Library JAR |
| `data-plane-core` | Shared runtime: registration, routing, CP client | Library JAR |
| `data-plane-http-pull` | HTTP-PULL standalone service (default port 9090) | Spring Boot fat JAR |
| `data-plane-http-push` | HTTP-PUSH standalone service (default port 9091) | Spring Boot fat JAR |
| `data-plane-grpc` | gRPC streaming standalone service (REST 9094, gRPC 9095) | Spring Boot fat JAR |
| `data-plane-kafka` | Kafka-backed streaming standalone service (REST 9098) | Spring Boot fat JAR |

### Control Plane additions (`data-transfer` module)

| Component | Package | Purpose |
|---|---|---|
| `DataPlaneRegistration` | `it.eng.datatransfer.model` | Persisted DP registration record (MongoDB) |
| `DataPlaneRegistrationService` | `it.eng.datatransfer.service` | CRUD + routing lookup |
| `DataPlaneRouter` | `it.eng.datatransfer.router` | Selects DP by transfer type and transport profile (sticky for prepared streaming sessions) |
| `DataPlaneClient` | `it.eng.datatransfer.client` | CP → DP HTTP calls (`prepare`, `start`, `terminate`, `suspend`, `resume`) |
| `DataFlowCallbackController` | `it.eng.datatransfer.rest.api` | Receives canonical and legacy DP callbacks |
| `DataFlowCallbackService` | `it.eng.datatransfer.service` | Centralizes DP callback handling; persists internal DP state before triggering DSP transitions |
| `DataPlaneRegistrationController` | `it.eng.datatransfer.rest.api` | Admin CRUD for DP registrations |

---

## Data Plane Registration

A DP registers itself with the CP at startup:

```
POST /api/v1/dataplanes
{
  "endpoint":               "http://dp-http-pull:9090",
  "supportedTransferTypes": ["HttpData-PULL"],
  "apiKey":                 "shared-secret"
}
```

`ControlPlaneRegistrationBean` (in `data-plane-core`) performs this call with exponential-backoff
retry (5 attempts, base delay 2 s, max 16 s). If `dataplane.control-plane-admin-endpoint` is blank,
registration is skipped (useful for development without a CP).

The CP stores the registration in MongoDB collection `data_plane_registrations`.

Registration side effects on the CP:

- `DataPlaneRegistrationService.register(...)` is idempotent by endpoint. A DP restart updates the
  existing record instead of creating a duplicate entry.
- The CP publishes audit events for register, update, deregister, and deregister-not-found cases.
- The CP also publishes `DataPlaneRegistrationChangedEvent` on register, update, and deregister.
  `CatalogDataPlaneFormatSyncListener` consumes that event and triggers
  `CatalogDataPlaneFormatSyncService.reconcileCatalogDistributions()` so dataset distributions remain
  aligned with the currently registered dataplane formats.

### Catalog / distribution synchronization

Catalog publication remains DSP-compliant on the wire, but the concrete set of advertised transfer
 formats is an implementation-specific capability derived from the dataplane registry.

Current repository behavior:

- `CatalogDataPlaneFormatSyncService.resolveSupportedFormats()` takes the union of
  `DataPlaneRegistration.supportedTransferTypes` across all registered DPs.
- Each dataset is reconciled to keep one distribution per active format.
- If no dataplane formats are registered, the dataset is normalized to exactly one *template*
  distribution with `format = null` instead of losing its distribution entirely.
- Historical shared distribution documents are cloned per dataset before replacement so reconciliation
  does not leave multiple datasets coupled to the same mutable distribution entity.
- Dataset and distribution CRUD operations publish `CatalogStructureChangedEvent.fullReconcile(...)`,
  so admin-side changes are also fed back through the same reconciliation path.

Operational consequence:

- Updating a distribution through the admin API changes the stored template fields
  (`title`, `description`, `accessService`, policy references, timestamps, and similar metadata).
- The final advertised `distribution.format` set is still re-materialized from the active dataplane
  registrations. In other words, **manual distribution edits do not override the runtime capability
  set advertised by registered dataplanes**.

---

## Transfer Flows

### HTTP-PULL

The artifact lives in the **provider's S3 bucket**. The consumer downloads it into the
**consumer's S3 bucket** via the consumer-side pull DP.

```
Consumer CP                 Provider CP
     |                           |
     |--- TransferRequestMsg --->|
     |    (format=HttpData-PULL) |
     |                           |
     |            [provider admin calls startTransfer()]
     |                           |
     |                           | (generates presigned GET URL directly
     |                           |  via S3ClientService — no DP call)
     |                           |
     |<-- TransferStartMessage --|
     |    dataAddress.endpoint   |
     |    = presignedUrl         |
     |                           |
     |    [consumer admin calls downloadData()]
Consumer-side Pull DP            |
     |<-- POST /dataflows/start -|
     |    DataFlowStartMessage   |
     |    dataAddress.endpoint   |
     |    = presignedUrl         |
     |                           |
     |--- GET presignedUrl --------------------------------------------> Provider S3
     |<-- artifact stream ------------------------------------------------|
     |--- PUT artifact -------> Consumer S3 (key = transferProcessId)
     |                           |
     |-- POST /api/v1/transfers/{id}/dataflow/completed --> Consumer CP
     |                           |
Consumer CP → COMPLETED          |
```

Key points:
- The provider CP generates the presigned GET URL **directly via `S3ClientService`** — it does
  **not** need a pull DP registered on the provider side. The pull DP is a **consumer-side only**
  component.
- `POST /dataflows/start` is sent to the **consumer's** registered pull DP when the consumer
  admin triggers the download (e.g. via `GET /api/v1/transfers/{id}/download`).
- The artifact is stored in the **consumer's S3 bucket** with `objectKey = transferProcessId`.
- After COMPLETED, the consumer can retrieve a new presigned URL via `viewData`.

### HTTP-PUSH

The artifact lives in the **provider's S3 bucket**. The provider-side push DP downloads it and
uploads it directly to the **consumer's S3 bucket** using temporary credentials.

```
Consumer CP                  Provider CP               Provider-side Push DP
     |                            |                             |
     |  [consumer admin calls requestTransfer()]                |
     |  Consumer CP resolves tenant bucket via TenantBucketResolver
     |  and sends consumer bucket coordinates plus temporary    |
     |  MinIO management credentials to the consumer Push DP    |
     |                            |                             |
     |---- TransferRequestMsg --->|                             |
     |     dataAddress = {        |                             |
     |       bucketName,          |                             |
     |       objectKey (=transferProcessId),                    |
     |       accessKey, secretKey (temp),                       |
     |       endpointOverride (=s3.endpoint, internal)          |
     |     }                      |                             |
     |                            |                             |
     |            [provider admin calls startTransfer()]        |
     |<-- TransferStartMessage ---|                             |
     |    (dataAddress forwarded) |                             |
     |                            |                             |
     |            [provider admin calls downloadData()]         |
     |                            |-- POST /dataflows/start --->|
     |                            |   DataFlowStartMessage:     |
     |                            |   dataAddress contains      |
     |                            |   source.* (provider S3     |
     |                            |   credentials, CP-resolved) |
     |                            |   sink.* (consumer temp     |
     |                            |   credentials, forwarded)   |
     |                            |                             |
     |                            |  Push DP opens provider S3  |
     |                            |  via S3SourceReader using   |
     |                            |  source.* from dataAddress  |
     |                            |  Streams artifact directly  |
     |                            |  to consumer S3 using       |
     |                            |  sink.* credentials     --->|-- Consumer S3
     |                            |                             |
     |<-- POST /api/v1/transfers/{id}/dataflow/completed ← Provider CP <-------|
     |                            |                             |
Both CPs → COMPLETED             |                             |
```

Key points:
- The consumer CP resolves the tenant bucket via `TenantBucketResolver`, then calls the consumer
  push DP `prepare` endpoint. The DP creates temporary credentials via
  `TemporaryBucketUserService`. In the current MinIO fallback, the CP passes bootstrap
  `application.properties` credentials as management credentials for that DP-side temp-user
  create/delete path until bucket-scoped delegated policies work for `BucketCredentialsEntity`.
  The temp user grants only `s3:PutObject` on the exact `objectKey = transferProcessId`.
- The CP embeds the internal S3 endpoint (`s3.endpoint`) as `endpointOverride` in the consumer
  dataAddress so the provider DP can reach MinIO from within the Docker network.
  `s3.externalPresignedEndpoint` is only for the public host embedded into presigned URLs delivered
  to external consumers. Dataplane server-side S3 access continues to use `s3.endpoint` (or the
  CP-provided internal endpoint override in DPS metadata) and must not be switched to the public host.
- `POST /dataflows/start` is sent to the **provider's** registered push DP only when the provider
  admin triggers the push (e.g. via `GET /api/v1/transfers/{id}/download`). It is **not** sent
  automatically on `startTransfer()`.
- `DataFlowStartMessage.dataAddress` carries both `source.*` properties (provider's own S3
  credentials, resolved by the CP from per-bucket credentials and `TenantBucketResolver`) and
  `sink.*` properties (the consumer's temporary credentials forwarded from the
  `TransferRequestMessage`). The push DP uses `S3SourceReader` with these `source.*` entries to
  open the provider artifact — it does **not** generate a presigned GET URL for the provider side.
- The pushed artifact is stored in the **consumer's S3 bucket** with `objectKey = transferProcessId`.
- After COMPLETED, the consumer can retrieve a presigned URL via `viewData`.
- Temporary credentials are cleaned up by `TemporaryBucketUserService.deleteTemporaryUser()`
  after transfer completion or termination.

### gRPC streaming

The streaming gRPC slice splits responsibilities across both dataplanes:

- **Provider-side gRPC DP** handles DPS `prepare`, allocates a session, and exposes a gRPC
  chunk stream backed by `SourceReader` (`S3SourceReader` first).
- **Consumer-side gRPC DP** handles DPS `start`, connects to the provider gRPC endpoint, and
  writes chunks through `SinkWriter` (`S3SinkWriter` first).

```text
Consumer CP                  Provider CP                Provider gRPC DP
     |                            |                            |
     |--- TransferRequestMsg ---->|                            |
     |    format=stream:grpc      |                            |
     |                            | [provider admin start]     |
     |                            |-- POST /dataflows/prepare->|
     |                            |<-- {host,port,sessionId} --|
     |<-- TransferStartMessage ---|                            |
     |    dataAddress = gRPC meta |                            |
     |
     | [consumer admin download]
     v
Consumer gRPC DP -----------------------------------------------------------> Provider gRPC DP
POST /dataflows/start                                                       gRPC Stream(sessionId)
     |                                                                             |
     |--------------------------- streamed chunks -------------------------------->|
     |--- write to consumer S3 (key = transferProcessId)
     |--- POST /api/v1/transfers/{id}/dataflow/completed|errored --> Consumer CP
```

Key points:
- Built-in HTTP-PULL and HTTP-PUSH still do not call DPS `prepare`; built-in `stream:grpc` does.
- The provider CP persists both `transportProfile=stream:grpc` and `assignedDataplaneEndpoint`
  so later terminate/suspend calls reach the same prepared DP instance.
- Provider-side source hints such as `sourceType` and `finite` from the original
  `TransferRequestMessage.dataAddress` are forwarded in `DataFlowPrepareMessage.metadata`
  under the `source` section. The CP also adds the resolved S3 access details (bucket name,
  credentials, region, and internal endpoint) as a nested `source.s3` map within that same
  metadata section. `DataFlowPrepareMessage` does **not** carry a top-level `dataAddress`
  field; attempting to set one is rejected at build time.
- If the consumer peer rejects `TransferStartMessage`, the provider CP rolls the transfer back
  to `REQUESTED` and best-effort terminates the prepared gRPC session to avoid leaks.
- Finite streams complete on EOF; unexpected EOF on a non-finite stream is surfaced through the
  DPS `errored` callback instead of leaving the data flow stuck in `STARTED`.

### Kafka streaming

The Kafka streaming slice uses the same CP orchestration pattern as gRPC, but the wire transport is
broker-backed instead of direct point-to-point:

- **Provider-side Kafka DP** handles DPS `prepare`, allocates a Kafka topic and transport metadata,
  and asynchronously publishes the provider source stream into Kafka.
- **Consumer-side Kafka DP** handles DPS `start`, subscribes to the prepared topic, and writes
  consumed records through `SinkWriter` (`S3SinkWriter` first).

```text
Consumer CP                  Provider CP                 Provider Kafka DP
     |                            |                              |
     |--- TransferRequestMsg ---->|                              |
     |    format=stream:kafka     |                              |
     |                            | [provider admin start]       |
     |                            |-- POST /dataflows/prepare -->|
     |                            |<-- {bootstrapServers,topic,groupId,mode}
     |<-- TransferStartMessage ---|                              |
     |    dataAddress = Kafka meta|                              |
     |
     | [consumer admin download]
     v
Consumer Kafka DP -----------------------------------------------> Kafka broker/topic
POST /dataflows/start                                              publish / consume
     |                                                                  |
     |--------------------------- consumed records ----------------------|
     |--- write to consumer S3 (key = transferProcessId)
     |--- POST /api/v1/transfers/{id}/dataflow/completed|errored --> Consumer CP
```

Key points:

- The provider DP returns `endpointType=kafka` with `bootstrapServers`, `topic`, `groupId`, and
  `mode` in `dataAddress.endpointProperties`.
- Topic names are normalized into Kafka-safe identifiers by replacing unsupported characters from
  the transfer ID (for example `urn:uuid:...` becomes `stream-topic-urn_uuid_...`).
- The current built-in implementation is **finite S3-backed streaming**: the provider publishes an
  EOF marker after the source stream is exhausted, and the consumer completes when that marker is
  received.
- `suspend` and `resume` currently return a failure result for `stream:kafka`; operationally, use
  terminate and recreate the transfer instead.
- Kafka transport metadata is prepared on the provider DP and then consumed only after the consumer
  admin explicitly triggers `downloadData()`, matching the current TRUE Connector admin-driven
  transfer model.

### viewData

After a transfer reaches `COMPLETED` and `isDownloaded = true`, the consumer can call
`GET /api/v1/transfers/{id}/view` to receive a presigned S3 GET URL for the stored artifact.

The CP generates the presigned URL **directly via `S3ClientService`** using the consumer's
own S3 bucket (key = `transferProcessId`). No DP call is made — the CP owns the S3 client
and can generate the URL itself.

### `prepare` usage by transfer type

The upstream DPS spec allows `prepare` and `start`, but TRUE Connector currently uses them
selectively:

| Transfer type | Uses DPS `prepare`? | Current repository reason |
|---|---|---|
| `HttpData-PULL` | No | Provider CP can generate the presigned URL directly and consumer DP can start immediately |
| `HttpData-PUSH` | No in the built-in CP flow | Consumer CP prepares sink credentials directly; provider DP gets all source/sink details in `start` |
| `stream:grpc` | Yes | Provider DP must allocate a prepared stream session and return transport metadata before consumer start |
| `stream:kafka` | Yes | Provider DP must allocate topic and transport metadata before consumer subscription |

This selective use of `prepare` is a **TRUE Connector implementation choice**, not a normative DSP
or DPS rule.

### Transfer Lifecycle and Suspend Semantics

```
REQUESTED → STARTED → COMPLETED  (normal path)
                    → SUSPENDED → STARTED  (suspend/resume)
                    → TERMINATED
         → TERMINATED
```

**Suspend limitation**: `suspendTransfer()` is rejected with HTTP 400 if a data plane transfer
is already in progress (`isDownloadInProgress = true`). HTTP-PULL and HTTP-PUSH are fire-and-forget
operations — once the DP starts moving data, the CP cannot pause it. Suspending mid-transfer would
leave the state machine permanently broken (the DP's subsequent COMPLETED callback would be
rejected by an invalid SUSPENDED→COMPLETED transition).

Suspend is safe and allowed when:
- The transfer is in `STARTED` state **and** no `downloadData()` call has been made yet.
- This corresponds to the window between `startTransfer()` and `downloadData()`.

---

## DPS API Endpoints (on each DP)

### Canonical lifecycle endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/dataflows/start` | Begin a data transfer (async) |
| `POST` | `/dataflows/prepare` | Prepare resources before start (used by the built-in CP for `stream:grpc` and `stream:kafka`; not called for HTTP-PULL or HTTP-PUSH) |
| `POST` or `DELETE` | `/dataflows/{id}/terminate` | Terminate/abort a data flow |
| `POST` | `/dataflows/{id}/suspend` | Suspend an active transfer |
| `POST` | `/dataflows/{id}/resume` | Resume a suspended transfer |
| `GET` | `/dataflows/{id}/status` | Query the current state of a data flow |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/api/v1/audit` | List audit events (paginated, filterable) |
| `GET` | `/api/v1/audit/{id}` | Fetch a single audit event by ID |
| `GET` | `/api/v1/audit/types` | List all supported audit event types |

### Compatibility aliases (retained for backward compatibility)

| Method | Path | Delegates to |
|---|---|---|
| `POST` | `/dataflows/terminate/{id}` | `POST /dataflows/{id}/terminate` |
| `POST` | `/dataflows/suspend/{id}` | `POST /dataflows/{id}/suspend` |

All endpoints require the `X-Api-Key` header except `/actuator/health`.

---

## CP Callback Endpoints (on the CP)

The Data Plane sends these callbacks to the Control Plane after each lifecycle event.

### Canonical per-transfer endpoints (preferred)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/transfers/{processId}/dataflow/prepared` | DP reports resources prepared |
| `POST` | `/api/v1/transfers/{processId}/dataflow/started` | DP reports transfer started |
| `POST` | `/api/v1/transfers/{processId}/dataflow/completed` | DP reports transfer completed |
| `POST` | `/api/v1/transfers/{processId}/dataflow/errored` | DP reports transfer failed |

### Legacy endpoints (preserved for backward compatibility)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/dataflows/complete` | Legacy completion callback (body carries `processId`) |
| `POST` | `/api/v1/dataflows/error` | Legacy error callback (body carries `processId`) |

All callback endpoints require the `X-Api-Key` header with the DP's registered API key.

---

## API Key Authentication

All CP → DP calls carry an `X-Api-Key` header (value = `DataPlaneRegistration.apiKey`).  
All DP → CP callbacks carry an `X-Api-Key` header (value = `dataplane.api-key` property).

On each side, `ApiKeyAuthFilter` validates the header. Missing or mismatched key → HTTP 401.

---

## Concurrency Model

Each DP uses Java 21 virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`).
Each transfer runs on its own virtual thread. There is no fixed pool ceiling.

---

## HTTP Clients

### OkHttpClient — CP↔DP management calls

Both DP apps component-scan `it.eng.tools`, which auto-configures `OkHttpClient`:
- `server.ssl.enabled=true` → TLS client with custom truststore (OCSP-validated)
- `server.ssl.enabled=false` → plain HTTP (development only)

`OkHttpClient` is used **only** for the CP↔DP management REST calls (registration, prepare, start,
terminate, audit). See `doc/security.md` for truststore configuration details.

### java.net.http.HttpClient — artifact downloads

`HttpPullTransferProtocol` uses the JDK's built-in `java.net.http.HttpClient` for actual artifact
downloads from provider-presigned URLs. This client:

- **Negotiates HTTP/2** via ALPN on TLS connections (AWS S3, production MinIO with TLS) and
  **falls back to HTTP/1.1** transparently for plain HTTP (development MinIO without TLS).
- Is a **Spring `@Bean`** (`dataPlaneHttpClient`) defined in `DataPlaneHttpClientConfiguration`
  and injected into the pull protocol — thread-safe and shared across concurrent transfers.
- **Mirrors the SSL posture of the connector's `OkHttpClientConfiguration`** (in `tools`):
  `server.ssl.enabled=false` → trust-all `SSLContext` (development only);
  `server.ssl.enabled=true` → `SSLContext` from the `connector` SSL bundle (custom keystore +
  truststore configured via `spring.ssl.bundle.jks.connector.*` in the DP's
  `application.properties`). `java.net.http.HttpClient` does **not** use the
  `HttpsURLConnection` JVM-level defaults set by `GlobalSSLConfiguration`, so the `SSLContext`
  must be supplied explicitly.
- Uses a fixed **30-minute request timeout** for all artifact transfers (set before `send()` is
  called, because `java.net.http.HttpClient` does not allow changing the timeout mid-flight).
- No `AtomicReference` wrapper is needed — the response `InputStream` is closed in a
  `whenComplete` handler attached to the upload future.

`HttpPushTransferProtocol` does **not** use `java.net.http.HttpClient` for provider artifact reads.
It uses `S3SourceReader` (AWS SDK based) with CP-supplied `source.*` credentials from
`DataFlowStartMessage.dataAddress`.

---

## CP and DP Responsibilities for S3

### Control Plane (authoritative)

| Responsibility | How |
|---|---|
| Tenant bucket selection | `TenantBucketResolver.resolveBucketName(tenantId)` — checks `Tenant.bucketName`, falls back to `s3.bucketName` |
| Bucket and credential provisioning at startup | `InitialDataLoader` calls `S3BucketProvisionService.ensureBucketCredentials()` per tenant |
| Per-bucket S3 credentials | Stored encrypted in MongoDB `bucket_credentials`; loaded via `BucketCredentialsService` |
| Temporary push credentials | `TemporaryBucketUserService.createTemporaryUser()` — creates a scoped IAM user with `s3:PutObject` only |
| Presigned GET URL generation for HTTP-PULL | `S3ClientService.generateGetPresignedUrl()` directly, no DP call |
| Source credentials in CP↔DP messages | CP embeds `source.*` S3 properties (bucket, key, region, accessKey, secretKey, endpointOverride) in `DataFlowStartMessage.dataAddress` |
| Prepare metadata for streaming DPs | CP builds `metadata.source.s3` in `DataFlowPrepareMessage.metadata`; never via a top-level `dataAddress` on prepare |

### Data Plane

| Responsibility | How |
|---|---|
| Artifact read for push | `S3SourceReader` with `source.*` credentials supplied by the CP in `DataFlowStartMessage.dataAddress` |
| Artifact write to consumer | `S3ClientService.uploadFile()` with `sink.*` credentials supplied by the CP |
| Bucket provisioning at startup | **None** — `DataPlaneS3StartupBean.ensureBucketCredentials()` is a deliberate no-op |
| Consumer-side prepare (push DP, optional direct DPS path) | If the push DP's `prepare()` endpoint is invoked directly, it creates a temporary IAM user using local `s3.accessKey`/`s3.secretKey`/`s3.bucketName`; the current implementation prefers `s3.externalPresignedEndpoint` for returned `endpointOverride`, falling back to `s3.endpoint` when the external value is blank |
| viewData presigned URL (built-in flow) | Generated directly by the CP through `S3ClientService.generateGetPresignedUrl()`; no DP call is made |

### Internal vs external S3 endpoints

Two distinct endpoint values exist in the S3 configuration:

| Property | Value | Used for |
|---|---|---|
| `s3.endpoint` | Container-reachable URL, e.g. `http://minio:9000` | CP↔DP messages (`endpointOverride`); DP-to-S3 direct access within Docker network |
| `s3.externalPresignedEndpoint` | Host-accessible URL, e.g. `http://172.17.0.1:9000` | Presigned GET URLs embedded in `TransferStartMessage.dataAddress` for external consumers |

The CP always uses `s3.endpoint` when building `endpointOverride` values for DP messages so
that the receiving DP can reach MinIO over the internal Docker network. The
`s3.externalPresignedEndpoint` is **only** for presigned URLs handed to external consumers
(HTTP-PULL, viewData). Both properties are blank when using AWS S3 (the SDK resolves the
correct endpoint automatically).

---

## MongoDB Collections

| Collection | Model | Owner | Description |
|---|---|---|---|
| `data_plane_registrations` | `DataPlaneRegistration` | CP (`data-transfer`) | Registered DP endpoints |
| `dp_audit_events` | `DataPlaneAuditEvent` | Each DP (`data-plane-core`) | DP lifecycle audit trail |
| `audit_events` | `AuditEvent` | CP (`tools`) | CP audit trail (incl. DP registration events) |

The DP's `dp_audit_events` collection is **per-DP instance** — each pull and push DP has its own
collection in its own MongoDB. It is kept separate from the CP's `audit_events` collection because
each DP is a fully independent application with its own database connection.

### CP audit events for DP registration

The CP publishes to `audit_events` whenever a DP registers or deregisters:

| Event type | When |
|---|---|
| `DATAPLANE_REGISTERED` | New DP endpoint registered (`POST /api/v1/dataplanes`) |
| `DATAPLANE_REGISTRATION_UPDATED` | Existing endpoint re-registered (DP restart) |
| `DATAPLANE_DEREGISTERED` | DP removed (`DELETE /api/v1/dataplanes/{id}`) |
| `DATAPLANE_REGISTRATION_NOT_FOUND` | Delete attempted on unknown ID |

### DP audit event types

| Event type | When |
|---|---|
| `DATAFLOW_STARTED` | `POST /dataflows/start` received and persisted |
| `DATAFLOW_PREPARE_REQUESTED` | `POST /dataflows/prepare` received (endpoint available but not called by the built-in CP for HTTP-PULL or HTTP-PUSH) |
| `DATAFLOW_COMPLETED` | Transfer completed successfully |
| `DATAFLOW_FAILED` | Transfer failed (error propagated to CP) |
| `DATAFLOW_TERMINATED` | `POST /dataflows/{id}/terminate` (or `DELETE`) received |
| `DATAFLOW_SUSPENDED` | `POST /dataflows/{id}/suspend` received |
| `DP_REGISTRATION_SUCCESS` | CP registration succeeded at startup |
| `DP_REGISTRATION_FAILED` | CP registration failed after all retries |

---

## Key Configuration Properties

### Data Plane (`application.properties` in each DP)

| Property | Description | Example |
|---|---|---|
| `dataplane.endpoint` | Public URL of this DP (reachable from CP) | `http://dp-http-pull:9090` |
| `dataplane.control-plane-admin-endpoint` | CP admin base URL | `http://connector:8080` |
| `dataplane.api-key` | Shared secret for DP↔CP auth | `dp-secret-key` |
| `server.port` | Listening port | `9090` (pull) / `9091` (push) |
| `server.ssl.enabled` | Enable TLS | `true` / `false` |

#### Profile-specific overrides

Each standalone Data Plane module now also includes:

- `application-consumer.properties`
- `application-provider.properties`

Spring Boot loads the base `application.properties` first and then applies the active
profile-specific file as an override. In this repository, those profile files redefine only the
role-specific keys, for example:

- `server.port`
- `spring.application.name`
- `spring.data.mongodb.uri`
- `dataplane.id`
- `dataplane.endpoint`
- `dataplane.control-plane-admin-endpoint`
- `s3.bucketName`
- `application.encryption.key`
- `grpc.server.port` for the gRPC Data Plane

The remaining shared settings continue to come from `application.properties`, including
properties such as `dataplane.api-key`, `dataplane.control-plane-admin-secret`, `s3.endpoint`,
`s3.accessKey`, `s3.secretKey`, `s3.region`, and TLS defaults.

As a result, the repository-provided profile files are not intended to be used alone. When a
deployment activates `consumer` or `provider`, the effective configuration is:

- `application.properties` + `application-consumer.properties`, or
- `application.properties` + `application-provider.properties`

If an operator does not want to use Spring profiles, a single complete `application.properties`
file is also valid.

### S3 properties required by the push DP

The push DP's local S3 config is relevant only for its own optional direct-DPS `prepare()` flow.
In the built-in TRUE Connector flow, the consumer CP creates temporary IAM users directly during
`requestTransfer()`, and the consumer CP generates `viewData` presigned URLs directly through
`S3ClientService`. The provider-side push DP's artifact access does **not** depend on local S3
config — it receives provider S3 credentials from the CP in `DataFlowStartMessage.dataAddress`
(`source.*` properties) and uses `S3SourceReader` to read the artifact directly.

| Property | Description |
|---|---|
| `s3.endpoint` | S3/MinIO endpoint (blank = AWS). In the push DP's optional direct `prepare()` path, this is the fallback `endpointOverride` when `s3.externalPresignedEndpoint` is blank. |
| `s3.accessKey` | Admin access key (used for IAM user management) |
| `s3.secretKey` | Admin secret key |
| `s3.region` | S3 region |
| `s3.bucketName` | Default bucket name (fallback when no per-tenant bucket is configured) |
| `s3.externalPresignedEndpoint` | Public-facing endpoint embedded in presigned URLs for **external consumers** (e.g. MinIO behind Docker NAT). Not used for DP-to-DP server-side transfers. |

> **MinIO vs AWS (push DP local config)**: In the push DP's optional direct `prepare()` path,
> the current implementation prefers `s3.externalPresignedEndpoint` for returned
> `endpointOverride`, falling back to the internal `s3.endpoint` (for example
> `http://minio:9000`) when the external value is blank. In the built-in TRUE Connector flow,
> the consumer CP handles temp-user creation directly and embeds its own `s3.endpoint` into the
> credentials it forwards to the provider DP. Leave both properties blank when using AWS S3.

---

## Extending with a New Data Plane Type

1. Create a new Spring Boot module (e.g., `data-plane-mqtt`) depending on `data-plane-core`.
2. Implement the `DataTransferProtocol` SPI interface and annotate it with `@Component`.
3. Declare the supported transfer type in `DataPlaneProperties.supportedTransferTypes`.
4. On startup, `ControlPlaneRegistrationBean` automatically registers the DP with the CP.
5. No CP code changes needed — `DataPlaneRouter` selects the correct DP by transfer type.
