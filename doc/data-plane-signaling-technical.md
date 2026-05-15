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
    |-- POST /api/v1/dataflows/complete --> Consumer CP
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

### Control Plane additions (`data-transfer` module)

| Component | Package | Purpose |
|---|---|---|
| `DataPlaneRegistration` | `it.eng.datatransfer.model` | Persisted DP registration record (MongoDB) |
| `DataPlaneRegistrationService` | `it.eng.datatransfer.service` | CRUD + routing lookup |
| `DataPlaneRouter` | `it.eng.datatransfer.router` | Selects DP by transfer type (round-robin) |
| `DataPlaneClient` | `it.eng.datatransfer.client` | CP → DP HTTP calls (`start`, `terminate`) |
| `DataFlowCallbackController` | `it.eng.datatransfer.rest.api` | Receives DP completion/error callbacks |
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
retry (5 attempts, base delay 2 s, max 60 s). If `dataplane.control-plane-admin-endpoint` is blank,
registration is skipped (useful for development without a CP).

The CP stores the registration in MongoDB collection `data_plane_registrations`.

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
     |-- POST /api/v1/dataflows/complete --> Consumer CP
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
     |  Consumer CP creates temp MinIO/IAM user                 |
     |  with PUT-only access to consumer's bucket               |
     |                            |                             |
     |---- TransferRequestMsg --->|                             |
     |     dataAddress = {        |                             |
     |       bucketName,          |                             |
     |       objectKey (=transferProcessId),                    |
     |       accessKey, secretKey (temp),                       |
     |       endpointOverride     |                             |
     |     }                      |                             |
     |                            |                             |
     |            [provider admin calls startTransfer()]        |
     |<-- TransferStartMessage ---|                             |
     |    (dataAddress forwarded) |                             |
     |                            |                             |
     |            [provider admin calls downloadData()]         |
     |                            |-- POST /dataflows/start --->|
     |                            |   DataFlowStartMessage:     |
     |                            |   - dataAddress = consumer  |
     |                            |     S3 credentials          |
     |                            |   - datasetId (artifact key)|
     |                            |                             |
     |                            |  Push DP generates presigned|
     |                            |  GET URL for provider S3    |
     |                            |  Downloads artifact         |
     |                            |  Uploads to consumer S3 --->|-- Consumer S3
     |                            |                             |
     |<-- POST /api/v1/dataflows/complete ← Provider CP <-------|
     |                            |                             |
Both CPs → COMPLETED             |                             |
```

Key points:
- Temporary credentials are created per-transfer by `TemporaryBucketUserService`. They grant
  only `s3:PutObject` on the exact `objectKey = transferProcessId`.
- `POST /dataflows/start` is sent to the **provider's** registered push DP only when the provider
  admin triggers the push (e.g. via `GET /api/v1/transfers/{id}/download`). It is **not** sent
  automatically on `startTransfer()`.
- The pushed artifact is stored in the **consumer's S3 bucket** with `objectKey = transferProcessId`.
- After COMPLETED, the consumer can retrieve a presigned URL via `viewData`.
- Temporary credentials are cleaned up by `TemporaryBucketUserService.deleteTemporaryUser()`
  after transfer completion or termination.

### viewData

After a transfer reaches `COMPLETED` and `isDownloaded = true`, the consumer can call
`GET /api/v1/transfers/{id}/view` to receive a presigned S3 GET URL for the stored artifact.

The CP generates the presigned URL **directly via `S3ClientService`** using the consumer's
own S3 bucket (key = `transferProcessId`). No DP call is made — the CP owns the S3 client
and can generate the URL itself.

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

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/dataflows/start` | Begin a data transfer (async) |
| `POST` | `/dataflows/prepare` | Prepare without transferring (part of DPS spec; not invoked by the built-in CP) |
| `DELETE` | `/dataflows/{id}` | Terminate/abort a data flow |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/api/v1/audit` | List audit events (paginated, filterable) |
| `GET` | `/api/v1/audit/{id}` | Fetch a single audit event by ID |
| `GET` | `/api/v1/audit/types` | List all supported audit event types |

All endpoints require the `X-Api-Key` header except `/actuator/health`.

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

`HttpPullTransferProtocol` and `HttpPushTransferProtocol` use the JDK's built-in
`java.net.http.HttpClient` for actual artifact downloads (presigned URL → S3 upload). This client:

- **Negotiates HTTP/2** via ALPN on TLS connections (AWS S3, production MinIO with TLS) and
  **falls back to HTTP/1.1** transparently for plain HTTP (development MinIO without TLS).
- Is a **Spring `@Bean`** (`dataPlaneHttpClient`) defined in `DataPlaneHttpClientConfiguration`
  and injected into both protocol classes — thread-safe, shared across all concurrent transfers.
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
| `DATAFLOW_PREPARE_REQUESTED` | `POST /dataflows/prepare` received (endpoint available but not called by the built-in CP) |
| `DATAFLOW_COMPLETED` | Transfer completed successfully |
| `DATAFLOW_FAILED` | Transfer failed (error propagated to CP) |
| `DATAFLOW_TERMINATED` | Explicit `DELETE /dataflows/{id}` received |
| `DATAFLOW_SUSPENDED` | `POST /dataflows/suspend/{id}` received |
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

### S3 properties required by the push DP

The push DP needs S3 access to the provider's bucket to generate presigned GET URLs:

| Property | Description |
|---|---|
| `s3.endpoint` | S3/MinIO endpoint (blank = AWS) |
| `s3.accessKey` | Admin access key |
| `s3.secretKey` | Admin secret key |
| `s3.region` | S3 region |
| `s3.bucketName` | Default bucket name |
| `s3.externalPresignedEndpoint` | Public-facing endpoint for presigned URLs (MinIO behind NAT) |

---

## Extending with a New Data Plane Type

1. Create a new Spring Boot module (e.g., `data-plane-mqtt`) depending on `data-plane-core`.
2. Implement the `DataTransferProtocol` SPI interface and annotate it with `@Component`.
3. Declare the supported transfer type in `DataPlaneProperties.supportedTransferTypes`.
4. On startup, `ControlPlaneRegistrationBean` automatically registers the DP with the CP.
5. No CP code changes needed — `DataPlaneRouter` selects the correct DP by transfer type.
