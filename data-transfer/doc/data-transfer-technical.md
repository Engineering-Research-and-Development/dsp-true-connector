# Data Transfer Module — Technical Documentation

> **See also:** [User Guide](./data-transfer.md) | [SFTP Configuration](./sftp.md) | [Contract Negotiation](../../connector/doc/negotiation.md) | [Data Transfer Plane](../../connector/doc/transfer.md)

## Overview

The `data-transfer` module implements the [Dataspace Protocol (DSP) Transfer Process Protocol](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/). It handles the full lifecycle of data transfers between connectors, supporting both **Provider** and **Consumer** roles within the same codebase.

Responsibilities:
- Receiving and validating `TransferRequestMessage` from consumers (Provider role)
- Sending `TransferRequestMessage` to providers (Consumer role)
- Managing the `TransferProcess` state machine (INITIALIZED → REQUESTED → STARTED → COMPLETED/TERMINATED/SUSPENDED)
- Executing data transfer via pluggable strategies (HTTP-PULL, HTTP-PUSH)
- Serving artifacts via the REST artifact endpoint (External Storage flow)
- Enforcing agreement-based access control before data access
- Automatic transfer execution with retry logic

A completed contract negotiation is a prerequisite. The module verifies that a valid agreement exists before processing any transfer request.

---

## DSP Specification Alignment

This module implements the **Transfer Process Protocol** from DSP 2025-1:
- Specification: https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/
- All DSP message types are supported: `TransferRequestMessage`, `TransferStartMessage`, `TransferCompletionMessage`, `TransferTerminationMessage`, `TransferSuspensionMessage`
- State machine follows the DSP-defined valid transitions
- JSON-LD serialization uses the `https://w3id.org/dspace/2024/1/context.json` context

---

## Architecture

### Transfer Strategy Pattern

Data transfer execution is decoupled from protocol handling via the `DataTransferStrategy` interface. The `DataTransferStrategyFactory` maps a format string to the appropriate strategy at runtime.

```
DataTransferAPIService
    └── DataTransferStrategyFactory.getStrategy(format)
            ├── "HttpData-PULL" → HttpPullTransferStrategy
            └── "HttpData-PUSH" → HttpPushTransferStrategy
```

`DataTransferStrategy` interface (single method):
```java
CompletableFuture<Void> transfer(TransferProcess transferProcess);
```

All strategies return a `CompletableFuture<Void>`, allowing non-blocking execution. After successful transfer, `DataTransferAPIService.downloadData()` automatically sends a `TransferCompletionMessage` to the peer.

`S3TransferStrategy` exists in the codebase but is not registered in the factory (not yet implemented).

### Transfer Types Implemented

#### HttpData-PULL
The provider generates a presigned download URL pointing to the artifact in its S3 bucket. This URL is included in the `TransferStartMessage.dataAddress.endpoint` field sent to the consumer. The consumer then downloads from that URL directly, then stores the data in its own S3 bucket.

- Strategy: `HttpPullTransferStrategy`
- Consumer action: Downloads from the presigned URL and uploads to its local S3
- Optional: `dataAddress.endpointProperties` may contain `authType` and `authorization` tokens to be sent as the `Authorization` header when fetching the URL

#### HttpData-PUSH
The consumer provides its S3 credentials in `TransferRequestMessage.dataAddress` (sent to the provider). The provider generates a presigned GET URL for its artifact, downloads it, and uploads to the consumer-specified S3 bucket.

- Strategy: `HttpPushTransferStrategy`
- Provider action: Downloads from provider S3, uploads to consumer S3 using the credentials from `DataAddress.endpointProperties`
- After start is acknowledged, the provider automatically chains `processDownload` (via `AutomaticDataTransferService`)

#### External REST (Artifact Endpoint)
For artifacts stored in an external system (not S3), the provider exposes a REST endpoint. The provider sends the artifact URL (encoded with `consumerPid|providerPid`) in the `TransferStartMessage`. When the consumer accesses this endpoint, the provider validates the transfer state and agreement, then proxies the data from the external source.

- Endpoint: `GET /artifacts/{transactionId}` (where `transactionId` is Base64url-encoded `consumerPid|providerPid`)
- Handled by: `RestArtifactController` → `RestArtifactService` → `ArtifactTransferService`
- Agreement enforcement occurs before data is served

---

## Transfer Process State Machine

States are defined in `TransferState` enum (backed by `DSpaceConstants.DataTransferStates`):

| State        | Description                                         | Valid Next States                        | Terminal? |
|--------------|-----------------------------------------------------|------------------------------------------|-----------|
| `INITIALIZED`| Created by the provider side before any request     | `REQUESTED`                              | No        |
| `REQUESTED`  | Consumer sent a `TransferRequestMessage`; provider acknowledged | `STARTED`, `TERMINATED`      | No        |
| `STARTED`    | Transfer is active; `TransferStartMessage` acknowledged | `SUSPENDED`, `COMPLETED`, `TERMINATED` | No        |
| `SUSPENDED`  | Transfer is paused                                  | `STARTED`, `TERMINATED`                  | No        |
| `COMPLETED`  | Transfer finished successfully                      | _(none)_                                 | **Yes**   |
| `TERMINATED` | Transfer was stopped (by either party)              | _(none)_                                 | **Yes**   |

**State transition triggers:**

| Transition                   | Triggered by                                               |
|------------------------------|------------------------------------------------------------|
| INITIALIZED → REQUESTED      | Consumer sends `TransferRequestMessage` to provider        |
| REQUESTED → STARTED          | Provider sends `TransferStartMessage` to consumer callback |
| STARTED → COMPLETED          | Either party sends `TransferCompletionMessage`             |
| STARTED → SUSPENDED          | Either party sends `TransferSuspensionMessage`             |
| SUSPENDED → STARTED          | Either party sends `TransferStartMessage`                  |
| Any → TERMINATED             | Either party sends `TransferTerminationMessage`            |

**Important constraint:** Only the consumer (not the provider) can transition a `TransferProcess` from `REQUESTED` to `STARTED`. If a provider-role process attempts this, the service throws `TransferProcessInvalidStateException`.

---

## REST API — Protocol Endpoints (DSP)

### Provider Endpoints

Base path: `/transfers`

#### `GET /transfers/{providerPid}`

Retrieves a `TransferProcess` by its `providerPid`.

**Response (200 OK):**
```json
{
  "@context": "https://w3id.org/dspace/2024/1/context.json",
  "@type": "TransferProcess",
  "consumerPid": "urn:uuid:CONSUMER_PID",
  "providerPid": "urn:uuid:PROVIDER_PID",
  "state": "STARTED"
}
```

#### `POST /transfers/request`

Consumer initiates a transfer. Provider validates the agreement, creates a `TransferProcess` in `REQUESTED` state, and returns it.

**Request body:**
```json
{
  "@context": "https://w3id.org/dspace/2024/1/context.json",
  "@type": "TransferRequestMessage",
  "consumerPid": "urn:uuid:CONSUMER_PID_TRANSFER",
  "agreementId": "urn:uuid:AGREEMENT_ID",
  "dct:format": "HttpData-PULL",
  "callbackAddress": "https://consumer.callback.url"
}
```

For `HttpData-PUSH`, include a `dataAddress` with S3 credentials (see [DataAddress](#dataaddress)).

**Response (201 Created):**
```json
{
  "@context": "https://w3id.org/dspace/2024/1/context.json",
  "@type": "TransferProcess",
  "consumerPid": "urn:uuid:CONSUMER_PID_TRANSFER",
  "providerPid": "urn:uuid:PROVIDER_PID_TRANSFER",
  "state": "REQUESTED"
}
```

**Error (agreement not found):**
```json
{
  "@context": "https://w3id.org/dspace/2024/1/context.json",
  "@type": "TransferError",
  "consumerPid": "urn:uuid:CONSUMER_PID_TRANSFER",
  "providerPid": "urn:uuid:PROVIDER_PID_TRANSFER",
  "code": "...",
  "reason": []
}
```

When the `tck` Spring profile is active, the controller additionally publishes an `ApplicationEvent` for TCK test coordination.

#### `POST /transfers/{providerPid}/start`

Sends a `TransferStartMessage` to the provider (used by consumers via provider callback).

**Request body:**
```json
{
  "@context": "https://w3id.org/dspace/2024/1/context.json",
  "@type": "TransferStartMessage",
  "consumerPid": "urn:uuid:CONSUMER_PID",
  "providerPid": "urn:uuid:PROVIDER_PID",
  "dataAddress": { ... }
}
```

**Response:** `200 OK` (empty body)

#### `POST /transfers/{providerPid}/completion`

Signals transfer completion to the provider.

**Request body:**
```json
{
  "@context": "https://w3id.org/dspace/2024/1/context.json",
  "@type": "TransferCompletionMessage",
  "consumerPid": "urn:uuid:CONSUMER_PID",
  "providerPid": "urn:uuid:PROVIDER_PID"
}
```

**Response:** `200 OK` (empty body)

#### `POST /transfers/{providerPid}/termination`

Terminates the transfer on the provider side.

**Request body:**
```json
{
  "@context": "https://w3id.org/dspace/2024/1/context.json",
  "@type": "TransferTerminationMessage",
  "consumerPid": "urn:uuid:CONSUMER_PID",
  "providerPid": "urn:uuid:PROVIDER_PID",
  "code": "optional-error-code",
  "reason": []
}
```

**Response:** `200 OK` (empty body)

#### `POST /transfers/{providerPid}/suspension`

Suspends an active transfer on the provider side.

**Request body:**
```json
{
  "@context": "https://w3id.org/dspace/2024/1/context.json",
  "@type": "TransferSuspensionMessage",
  "consumerPid": "urn:uuid:CONSUMER_PID",
  "providerPid": "urn:uuid:PROVIDER_PID",
  "code": "optional-code",
  "reason": []
}
```

**Response:** `200 OK` (empty body)

---

### Consumer Callback Endpoints

Base path: `/consumer/transfers`

These endpoints mirror the provider endpoints but are called by the provider to notify the consumer of state changes.

| Method | Path | DSP Message | Description |
|--------|------|-------------|-------------|
| `GET` | `/consumer/transfers/{consumerPid}` | — | Retrieve transfer by `consumerPid` |
| `POST` | `/consumer/transfers/{consumerPid}/start` | `TransferStartMessage` | Provider starts transfer (includes `dataAddress`) |
| `POST` | `/consumer/transfers/{consumerPid}/completion` | `TransferCompletionMessage` | Provider signals completion |
| `POST` | `/consumer/transfers/{consumerPid}/termination` | `TransferTerminationMessage` | Provider terminates transfer |
| `POST` | `/consumer/transfers/{consumerPid}/suspension` | `TransferSuspensionMessage` | Provider suspends transfer |
| `POST` | `/consumer/transfers/tck` | `TCKRequest` | TCK test trigger (initiates a `requestTransfer`) |

---

## REST API — Management Endpoints

Base path: `/api/v1/transfers` (constant: `ApiEndpoints.TRANSFER_DATATRANSFER_V1`)

All management endpoints require authentication.

#### `POST /api/v1/transfers`

**Consumer** — Initiate a transfer request to the provider.

**Request body (`DataTransferRequest`):**
```json
{
  "transferProcessId": "internal-mongo-id",
  "format": "HttpData-PULL"
}
```

For `HttpData-PUSH`, the consumer's S3 credentials are automatically retrieved from `BucketCredentialsService` and included in the `TransferRequestMessage` sent to the provider. No manual `dataAddress` is needed.

**Response (200 OK):**
```json
{
  "success": true,
  "data": { ... },
  "message": "Data transfer requested"
}
```

#### `GET /api/v1/transfers`

List transfer processes with filtering and pagination.

**Query parameters:**
- `page` (default: `0`)
- `size` (default: `20`)
- `sort` (default: `timestamp,desc`)
- Any `TransferProcess` field as a filter parameter (automatic type detection)

**Response:** Paginated HATEOAS response.

#### `GET /api/v1/transfers/{transferProcessId}`

Retrieve a specific transfer process by its internal MongoDB ID.

#### `GET /api/v1/transfers/{transferProcessId}/download`

**Consumer** — Trigger the download of the artifact for a transfer in `STARTED` state. Executes the appropriate `DataTransferStrategy` asynchronously. Returns `202 Accepted` immediately; sends `TransferCompletionMessage` on success.

#### `GET /api/v1/transfers/{transferProcessId}/view`

**Consumer** — Generate a presigned S3 URL for an already-downloaded artifact. The transfer must be in `COMPLETED` state. Enforces usage policy before returning the URL. URL validity: 7 days.

#### `PUT /api/v1/transfers/{transferProcessId}/start`

**Provider** — Send a `TransferStartMessage` to the consumer callback, transitioning the process to `STARTED`. For `HttpData-PULL`, the provider generates a presigned URL for the artifact and includes it in the `DataAddress`.

#### `PUT /api/v1/transfers/{transferProcessId}/complete`

Signal transfer completion. Sends `TransferCompletionMessage` to the peer.

#### `PUT /api/v1/transfers/{transferProcessId}/suspend`

Suspend an active transfer. Sends `TransferSuspensionMessage` to the peer.

#### `PUT /api/v1/transfers/{transferProcessId}/terminate`

Terminate a transfer. Sends `TransferTerminationMessage` to the peer.

---

## Artifact Download Endpoint

### Endpoint

```
GET /artifacts/{transactionId}
```

Handled by `RestArtifactController` → `RestArtifactService`.

### transactionId Construction

The `transactionId` is the **Base64url-safe encoding** (no padding) of `consumerPid|providerPid`:

```java
String encoded = Base64.encodeBase64URLSafeString(
    (consumerPid + "|" + providerPid).getBytes(StandardCharsets.UTF_8)
);
String url = "/artifacts/" + encoded;
```

**Example:**
- `consumerPid` = `urn:uuid:CONSUMER_PID_TRANSFER`
- `providerPid` = `urn:uuid:PROVIDER_PID_TRANSFER`
- Encoded: `dXJuOnV1aWQ6Q09OU1VNRVJfUElEX1RSQU5TRkVSfHVybjp1dWlkOlBST1ZJREVSX1BJRF9UUkFOU0ZFUg`
- Full URL: `http://localhost:8090/artifacts/dXJuOnV1aWQ6Q09OU1VNRVJfUElEX1RSQU5TRkVSfHVybjp1dWlkOlBST1ZJREVSX1BJRF9UUkFOU0ZFUg`

### Authorization Enforcement Flow

1. Decode `transactionId` → split by `|` → `[consumerPid, providerPid]`
2. Look up `TransferProcess` by `(consumerPid, providerPid)`
3. If not found or not in `STARTED` state → HTTP 400/503
4. Look up the `Artifact` from the Catalog module via internal API (`/api/v1/datasets/{datasetId}/artifact`)
5. Route by `artifactType`:
   - `FILE` — download from provider S3 (deprecated path; prefer presigned URL)
   - `EXTERNAL` — fetch from the external URL using configured authorization, then stream response to client
6. Publish `ArtifactConsumedEvent` for usage control tracking

---

## DSP Message Types

### TransferRequestMessage

Sent by the consumer to `POST /transfers/request` to initiate a transfer.

| Field | Required | Description |
|-------|----------|-------------|
| `@context` | Yes | `https://w3id.org/dspace/2024/1/context.json` |
| `@type` | Yes | `TransferRequestMessage` |
| `consumerPid` | Yes | Consumer-side transfer process identifier |
| `agreementId` | Yes | Agreement ID from completed contract negotiation |
| `dct:format` | No | Transfer format: `HttpData-PULL`, `HttpData-PUSH`, `SFTP` |
| `callbackAddress` | Yes | Consumer's callback base URL |
| `dataAddress` | No | Required for `HttpData-PUSH`; contains consumer S3 credentials |

### TransferStartMessage

Sent by the provider to the consumer callback (`POST /consumer/transfers/{consumerPid}/start`). For `HttpData-PULL`, includes the presigned URL or artifact URL in `dataAddress`.

| Field | Required | Description |
|-------|----------|-------------|
| `consumerPid` | Yes | Consumer-side PID |
| `providerPid` | Yes | Provider-side PID |
| `dataAddress` | No | Present for pull transfers; contains `endpoint` (presigned URL or artifact URL) |

### TransferCompletionMessage

Signals that the transfer has completed successfully. Sent to either provider (`/transfers/{providerPid}/completion`) or consumer (`/consumer/transfers/{consumerPid}/completion`).

| Field | Required | Description |
|-------|----------|-------------|
| `consumerPid` | Yes | Consumer-side PID |
| `providerPid` | Yes | Provider-side PID |

### TransferTerminationMessage

Terminates a transfer. Can be sent from either side at any non-terminal state.

| Field | Required | Description |
|-------|----------|-------------|
| `consumerPid` | Yes | Consumer-side PID |
| `providerPid` | Yes | Provider-side PID |
| `code` | No | Machine-readable error code |
| `reason` | No | Array of human-readable reason objects |

### TransferSuspensionMessage

Pauses an active transfer. Shares the same structure as `TransferTerminationMessage` (with `code` and `reason` fields).

---

## Data Models

### TransferProcess

Stored in MongoDB collection `transfer_process`.

| Field | Type | JSON visibility | Description |
|-------|------|-----------------|-------------|
| `id` | `String` | Hidden (internal) | MongoDB document ID; auto-generated as URN UUID |
| `providerPid` | `String` | Visible | Provider-side transfer process identifier |
| `consumerPid` | `String` | Visible (inherited) | Consumer-side transfer process identifier |
| `state` | `TransferState` | Visible | Current FSM state |
| `agreementId` | `String` | Hidden | Links to the completed contract agreement |
| `callbackAddress` | `String` | Hidden | Peer's callback URL (provider stores consumer's, consumer stores provider's) |
| `datasetId` | `String` | Hidden | Dataset being transferred (links to Catalog module) |
| `role` | `String` | Hidden | `"provider"` or `"consumer"` |
| `dataAddress` | `DataAddress` | Hidden | Filled from `TransferStartMessage` on receive, or built for `HttpData-PUSH` on send |
| `format` | `String` | Hidden | Transfer format string (e.g. `HttpData-PULL`) |
| `isDownloaded` | `boolean` | Hidden | `true` after consumer has successfully downloaded data |
| `dataId` | `String` | Hidden | Key under which downloaded data is stored in consumer S3 |
| `retryCount` | `int` | Hidden | Number of automatic retry attempts consumed |
| `created` | `Instant` | Hidden | Audit: creation timestamp |
| `createdBy` | `String` | Hidden | Audit: creator |
| `modified` | `Instant` | Hidden | Audit: last modification timestamp |
| `lastModifiedBy` | `String` | Hidden | Audit: last modifier |
| `version` | `Long` | Hidden | Optimistic lock version |

**Protocol JSON representation** (what is sent to peers):
```json
{
  "@context": "https://w3id.org/dspace/2024/1/context.json",
  "@type": "TransferProcess",
  "consumerPid": "urn:uuid:CONSUMER_PID",
  "providerPid": "urn:uuid:PROVIDER_PID",
  "state": "REQUESTED"
}
```

**Internal MongoDB document:**
```json
{
  "_id": "abc45798-4444-4932-8baf-ab7fd66ql4d5",
  "_class": "it.eng.datatransfer.model.TransferProcess",
  "providerPid": "urn:uuid:PROVIDER_PID_TRANSFER",
  "consumerPid": "urn:uuid:CONSUMER_PID_TRANSFER",
  "callbackAddress": "http://localhost:8080/consumer/transfer/callback/",
  "agreementId": "urn:uuid:AGREEMENT_ID",
  "state": "REQUESTED",
  "role": "provider",
  "format": "HttpData-PULL",
  "datasetId": "urn:uuid:DATASET_ID",
  "retryCount": 0,
  "createdBy": "admin@mail.com",
  "lastModifiedBy": "admin@mail.com",
  "issued": "2024-04-23T16:26:00.000Z",
  "modified": "2024-04-23T16:26:00.000Z",
  "version": 0
}
```

### DataAddress

Carries endpoint information in `TransferStartMessage` (pull flow) or `TransferRequestMessage` (push flow).

| Field | Type | Description |
|-------|------|-------------|
| `endpointType` | `String` | Type hint (e.g. `https://w3id.org/idsa/v4.1/HTTP`) |
| `endpoint` | `String` | Direct URL (presigned URL or artifact endpoint) |
| `endpointProperties` | `List<EndpointProperty>` | Key-value pairs for S3 credentials or auth tokens |

**`EndpointProperty`** has `name` and `value` string fields.

**S3 credentials structure** (used in HTTP-PUSH `TransferRequestMessage`):
```json
{
  "@type": "DataAddress",
  "endpointProperties": [
    { "name": "bucketName",       "value": "consumer-bucket-name" },
    { "name": "region",           "value": "us-east-1" },
    { "name": "objectKey",        "value": "urn:uuid:TRANSFER_PROCESS_ID" },
    { "name": "accessKey",        "value": "access-key-value" },
    { "name": "secretKey",        "value": "secret-key-value" },
    { "name": "endpointOverride", "value": "http://localhost:9000" }
  ]
}
```

**Pull DataAddress** (sent by provider in `TransferStartMessage`):
```json
{
  "@type": "DataAddress",
  "endpointType": "https://w3id.org/idsa/v4.1/HTTP",
  "endpoint": "http://localhost:8090/artifacts/BASE64_ENCODED_PIDS"
}
```

With optional auth properties:
```json
{
  "@type": "DataAddress",
  "endpoint": "https://presigned.url/...",
  "endpointProperties": [
    { "name": "authType",      "value": "Bearer" },
    { "name": "authorization", "value": "token-value" }
  ]
}
```

### TransferArtifactState

Stored in MongoDB collection `transfer_states`. Tracks the progress of multi-part S3 uploads during a transfer.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Document ID |
| `uploadId` | `String` | S3 multipart upload ID |
| `downloadedBytes` | `long` | Bytes downloaded so far |
| `totalBytes` | `long` | Total expected bytes |
| `partNumber` | `int` | Current S3 multipart part number (auto-incremented) |
| `etags` | `List<String>` | ETag values for completed S3 parts |
| `presignURL` | `String` | Source presigned URL |
| `destBucket` | `String` | Destination S3 bucket |
| `destObject` | `String` | Destination S3 object key |
| `issued` | `Instant` | Creation timestamp |
| `modified` | `Instant` | Last modification timestamp |

---

## Key Services

### `AbstractDataTransferService` (and `DataTransferService`)

**Role:** Core protocol-level state machine implementation. `DataTransferService` extends it and is active when the `tck` profile is **not** active.

**Key methods:**

| Method | Description |
|--------|-------------|
| `initiateDataTransfer(TransferRequestMessage)` | Validates agreement, checks format support, transitions to `REQUESTED`. Publishes `AutoTransferStartEvent` if automatic transfer is enabled. |
| `startDataTransfer(TransferStartMessage, consumerPid, providerPid)` | Transitions to `STARTED`. Enforces consumer-only rule for `REQUESTED→STARTED`. Publishes `AutoTransferDownloadEvent` for HTTP-PULL consumer if automatic transfer enabled. |
| `completeDataTransfer(TransferCompletionMessage, ...)` | Transitions to `COMPLETED`, sets `isDownloaded=true`. |
| `terminateDataTransfer(TransferTerminationMessage, ...)` | Transitions to `TERMINATED` from any non-terminal state. |
| `suspendDataTransfer(TransferSuspensionMessage, ...)` | Transitions `STARTED` → `SUSPENDED`. |
| `findTransferProcessByProviderPid(providerPid)` | Lookup by `providerPid`. |
| `findTransferProcessByConsumerPid(consumerPid)` | Lookup by `consumerPid`. |
| `findTransferProcess(consumerPid, providerPid)` | Lookup by both PIDs. |
| `isDataTransferStarted(consumerPid, providerPid)` | Returns `true` if the transfer is in `STARTED` state. |

### `DataTransferAPIService`

**Role:** Management API service. Bridges the HTTP management API with the DSP protocol layer.

**Key methods:**

| Method | Description |
|--------|-------------|
| `requestTransfer(DataTransferRequest)` | Builds and sends `TransferRequestMessage` to provider; updates consumer-side `TransferProcess`. For HTTP-PUSH, auto-populates `DataAddress` with consumer S3 credentials. |
| `startTransfer(transferProcessId)` | Builds and sends `TransferStartMessage`. For HTTP-PULL provider, generates presigned URL or artifact endpoint URL and includes it in `DataAddress`. |
| `completeTransfer(transferProcessId)` | Sends `TransferCompletionMessage` to peer. |
| `suspendTransfer(transferProcessId)` | Sends `TransferSuspensionMessage` to peer. |
| `terminateTransfer(transferProcessId)` | Sends `TransferTerminationMessage` to peer. |
| `downloadData(transferProcessId)` | Delegates to the appropriate `DataTransferStrategy`; marks `isDownloaded=true`; then calls `completeTransfer` on success. Requires `STARTED` state. |
| `viewData(transferProcessId)` | Returns a 7-day presigned S3 URL for a completed download. Requires `COMPLETED` state. Enforces usage policy. |
| `findDataTransfers(filters, pageable)` | Dynamic filter-based pagination of all transfer processes. |

### `AutomaticDataTransferService`

**Role:** Executes transfer phases automatically with retry logic, without blocking the calling thread. Active when `application.automatic.transfer=true`.

**Key methods:**

| Method | Description |
|--------|-------------|
| `processStart(transferProcessId)` | Calls `apiService.startTransfer()`; for HTTP-PUSH provider, chains `processDownload` on success. |
| `processDownload(transferProcessId)` | Calls `apiService.downloadData().join()`. |

**Retry behaviour:**
- Retries are scheduled with `TaskScheduler` (non-blocking)
- Max retries configured by `application.automatic.transfer.retry.max` (default: 3)
- Delay between retries configured by `application.automatic.transfer.retry.delay.ms` (default: 2000 ms)
- After exhausting retries, calls `apiService.terminateTransfer()`. If that also fails, forces local `TERMINATED` state and publishes an audit event.

### `RestArtifactService`

**Role:** Serves data for the External Storage flow via `GET /artifacts/{transactionId}`.

Decodes `transactionId` → looks up `TransferProcess` → fetches `Artifact` metadata from Catalog module → streams data (from S3 or external URL) to the HTTP response. Publishes `ArtifactConsumedEvent` for usage control.

### `ArtifactTransferService`

**Role:** Retrieves `Artifact` metadata from the Catalog module via internal REST call.

Makes a `GET /api/v1/datasets/{datasetId}/artifact` request to the internal catalog API. Returns the `Artifact` object, which contains the artifact type (`FILE` or `EXTERNAL`) and the resource value/URL.

### `DataTransferStrategyFactory`

**Role:** Selects the correct `DataTransferStrategy` for a given format string.

Registered strategies:
- `"HttpData-PULL"` → `HttpPullTransferStrategy`
- `"HttpData-PUSH"` → `HttpPushTransferStrategy`

`S3TransferStrategy` is defined but not yet registered.

### `TCKDataTransferService`

**Role:** Test Compatibility Kit (TCK) variant of the transfer service, active when the `tck` Spring profile is active. Implements `requestTransfer(TCKRequest)` to allow an external test harness to trigger transfers programmatically via `POST /consumer/transfers/tck`.

---

## Automatic Transfer

When `application.automatic.transfer=true`, the connector automatically progresses through transfer phases without manual intervention. The flow mirrors the automatic negotiation pattern.

### Provider side (after receiving `TransferRequestMessage`):
1. `initiateDataTransfer()` detects provider role + automatic flag → publishes `AutoTransferStartEvent`
2. `AutomaticDataTransferService.processStart()` is triggered → calls `apiService.startTransfer()`
3. For `HttpData-PUSH`: after start is acknowledged, `processDownload()` is chained automatically

### Consumer side (after receiving `TransferStartMessage`):
1. `startDataTransfer()` detects consumer role + automatic flag + `HttpData-PULL` format → publishes `AutoTransferDownloadEvent`
2. `AutomaticDataTransferService.processDownload()` is triggered → calls `apiService.downloadData()`
3. On success, `downloadData()` automatically sends `TransferCompletionMessage`

### Retry logic:
- Failures during `START` or `DOWNLOAD` phases are retried up to `maxRetryAttempts` times
- Each retry is scheduled with `retryDelayMs` milliseconds of delay
- After exhaustion, the transfer is terminated gracefully

---

## Configuration

Configured in `DataTransferProperties` (Spring `@Component`):

| Property | Default | Description |
|----------|---------|-------------|
| `application.callback.address` | _(required)_ | This connector's base callback URL, used in `TransferRequestMessage.callbackAddress` |
| `application.automatic.transfer` | `false` | Enable automatic transfer progression |
| `application.automatic.transfer.retry.max` | `3` | Maximum number of retry attempts for automatic transfer |
| `application.automatic.transfer.retry.delay.ms` | `2000` | Milliseconds between retry attempts |

**Callback address derivation:**
- Provider callback: `${application.callback.address}` (as-is)
- Consumer callback: `${application.callback.address}/consumer` (with trailing slash normalized)

**S3 configuration** (from `S3Properties`):
- `s3.bucketName` — provider's S3 bucket
- `s3.region` — S3 region
- `s3.endpoint` — S3 endpoint URL (local MinIO or cloud)
- `s3.accessKey` / `s3.secretKey` — S3 credentials
- `s3.externalPresignedEndpoint` — override for the presigned URL base (used in HTTP-PUSH `DataAddress`)

---

## See Also

- [User Guide](./data-transfer.md)
- [SFTP Configuration](./sftp.md)
- [Pull flow diagram](./diagrams/pull_data_transfer.png)
- [Push flow diagram](./diagrams/push_data_transfer.png)
- [Contract Negotiation](../../connector/doc/negotiation.md)
- [Data Transfer Plane](../../connector/doc/transfer.md)
