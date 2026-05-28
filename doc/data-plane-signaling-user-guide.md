# Data Plane Signaling — User Guide

## Overview

TRUE Connector uses the **Dataplane Signaling Protocol** to separate orchestration logic
(Control Plane) from actual data movement (Data Plane). You can deploy one or more Data Plane
services independently and scale them as needed.

---

## Concepts

| Term | Description |
|---|---|
| **Control Plane (CP)** | The main connector application that manages negotiations and transfer lifecycle |
| **Data Plane (DP)** | A lightweight service responsible for the actual data transfer |
| **Transfer type** | Protocol used for data movement — `HttpData-PULL`, `HttpData-PUSH`, `stream:grpc`, or `stream:kafka` |
| **DP Registration** | A CP record describing where a DP lives and what transfer types it supports |

---

## Deployment

The connector ships four ready-made Data Plane images:

| Image | Transfer type | Default port |
|---|---|---|
| `data-plane-http-pull` | `HttpData-PULL` | 9090 |
| `data-plane-http-push` | `HttpData-PUSH` | 9091 |
| `data-plane-grpc` | `stream:grpc` | REST 9094, gRPC 9095 |
| `data-plane-kafka` | `stream:kafka` | REST 9098 |

All four are wired in `ci/docker/docker-compose.yml`:

- `--profile grpc` starts the consumer/provider gRPC dataplanes
- `--profile kafka` starts the consumer/provider Kafka dataplanes plus the shared Kafka broker

### Minimum required configuration (each DP)

```properties
# Which CP to register with (must be reachable from the DP container)
dataplane.control-plane-admin-endpoint=http://connector:8080

# Public URL of this DP (must be reachable from the CP)
dataplane.endpoint=http://dp-http-pull:9090

# Shared secret — must match what is stored on the CP for this DP
dataplane.api-key=change-me-in-production
```

### Additional configuration for the HTTP-PUSH DP

For the **current built-in TRUE Connector flow**, the CP handles the two operator-facing S3 steps
itself:

1. **Request transfer (`HttpData-PUSH`)** — the consumer CP creates the temporary IAM user directly.
2. **`viewData` after completion** — the consumer CP generates the presigned GET URL directly.

The provider-side push DP also does **not** use local S3 config to access the provider artifact.
Instead, the CP resolves the provider's tenant bucket and per-bucket credentials and passes them
as `source.*` properties in `DataFlowStartMessage.dataAddress`. The push DP reads the artifact
via `S3SourceReader` with those CP-supplied credentials.

The push DP's local S3 config remains relevant only for its own optional DPS `prepare()` capability
(including `mode=VIEW`) if that DP endpoint is invoked directly. In that DP-local path, the current
implementation prefers `s3.externalPresignedEndpoint` for returned `endpointOverride`, falling back
to `s3.endpoint` when the external value is blank.

```properties
s3.endpoint=http://minio:9000
s3.accessKey=minioadmin
s3.secretKey=minioadmin
s3.region=us-east-1
# Relevant only for the optional consumer-side DPS prepare() path described above.
s3.bucketName=my-consumer-bucket
# For MinIO behind Docker NAT: used only in the optional direct DPS prepare(mode=VIEW) path.
# Leave blank for AWS or when MinIO is directly accessible at s3.endpoint.
s3.externalPresignedEndpoint=http://172.17.0.1:9000
```

### Additional configuration for the Kafka DP

The Kafka dataplane still uses S3-backed `SourceReader` / `SinkWriter`, but the transport itself is
broker-backed:

```properties
server.port=9098
dataplane.endpoint=http://dp-kafka:9098

# Kafka broker used by both provider and consumer Kafka dataplanes
dataplane.kafka.bootstrap-servers=kafka-broker:9092

# Prefix used when the provider DP allocates transport topics
dataplane.kafka.topic-prefix=stream-topic-
```

In the local compose stack the consumer Kafka DP is exposed on host port `9098` and the provider
Kafka DP on host port `9099` (container port `9098` in both cases).

---

## How Transfers Work

> **Bucket and credential ownership**: The Control Plane is authoritative for tenant bucket
> selection and S3 credential provisioning. Bucket creation and per-bucket credential setup
> happen at CP startup (`InitialDataLoader`) — **DP startup does not provision buckets**. The
> CP resolves the correct bucket for each tenant and includes the necessary S3 credentials in
> every CP↔DP message, so DPs do not need independent access to the credential store.

### HTTP-PULL — consumer fetches the artifact

1. **Consumer** requests a transfer (`HttpData-PULL`).
2. **Provider admin** calls *Start transfer* — the CP resolves the tenant bucket via
   `TenantBucketResolver` and generates a presigned S3 download URL **directly via its own
   S3 client** (no pull DP is registered or needed on the provider side). That URL is sent
   to the consumer inside `TransferStartMessage`.
3. **Consumer admin** calls *Download data* — the consumer-side pull DP downloads the artifact
   from the presigned URL and stores it in the consumer's S3 bucket.
4. The DP notifies the CP when done; both sides move to `COMPLETED`.
5. **Consumer admin** can call *View data* to get a fresh presigned URL for the stored artifact.

> **Note (current built-in behavior)**: The provider CP generates the presigned GET URL
> directly using its own S3 client. A provider-side pull DP is not part of the current
> built-in HTTP-PULL flow.

### HTTP-PUSH — provider pushes the artifact to the consumer

1. **Consumer admin** calls *Request transfer* (`HttpData-PUSH`).
   The consumer CP resolves the tenant bucket via `TenantBucketResolver`, creates a temporary
   S3 user with write-only access to that bucket, and includes those credentials in the
   transfer request to the provider. The CP uses `s3.endpoint` (internal Docker URL) as
   `endpointOverride` so the provider DP can reach MinIO from within the network.
2. **Provider admin** calls *Start transfer* — the provider CP adds the provider's own S3
   credentials (`source.*` properties, resolved from per-bucket credentials) alongside the
   consumer's temporary credentials (`sink.*` properties) and forwards them to the transfer
   process. No data moves yet.
3. **Provider admin** calls *Push data* (the download endpoint on the provider side) — the
   provider CP routes `POST /dataflows/start` to the push DP with a `DataFlowStartMessage`
   containing both `source.*` (provider S3, CP-resolved) and `sink.*` (consumer temp
   credentials). The push DP reads the artifact via `S3SourceReader` using those
   `source.*` properties and uploads it directly to the consumer's S3 bucket using the
   `sink.*` credentials.
4. The DP notifies both CPs when done; both sides move to `COMPLETED`.
5. **Consumer admin** can call *View data* to get a presigned URL for the received artifact.

> **Note**: Temporary push credentials are automatically cleaned up after the transfer
> completes or is terminated.

### gRPC streaming — provider prepares, consumer streams

1. **Consumer admin** calls *Request transfer* with `format=stream:grpc`.
   Optional source hints such as `sourceType=s3` and `finite=true|false` can be passed
   in the request `dataAddress`.
2. **Provider admin** calls *Start transfer*.
   The provider CP calls DPS `POST /dataflows/prepare` on the provider gRPC DP. The CP
   forwards any source hints from the original `dataAddress` along with the resolved S3
   access details (bucket, credentials, region, internal endpoint) in
   `DataFlowPrepareMessage.metadata` under the `source` / `source.s3` sections. The DP
   allocates a stream session and returns gRPC endpoint metadata (`host`, `port`, `sessionId`,
   `mode`).
3. The provider CP sends a standard `TransferStartMessage` to the consumer, embedding those
   transport details inside `dataAddress`.
4. **Consumer admin** calls *Download data*.
   The consumer CP routes `POST /dataflows/start` to the consumer gRPC DP, which opens the gRPC
   stream and writes received chunks into the consumer bucket.
5. Finite streams notify `COMPLETED` on EOF. Non-finite streams stay `STARTED` until explicitly
   terminated; if the provider unexpectedly closes a non-finite stream, the DP reports `errored`.

The provider-side prepared session is sticky-routed to one DP instance and is cleaned up on
rollback or termination.

### Kafka streaming — provider prepares topic, consumer drains broker

1. **Consumer admin** calls *Request transfer* with `format=stream:kafka`.
   The current built-in scenario is an S3-backed finite stream, even though the transport metadata
   still carries a `mode` field.
2. **Provider admin** calls *Start transfer*.
   The provider CP calls DPS `POST /dataflows/prepare` on the provider Kafka DP, which allocates
   transport metadata (`bootstrapServers`, `topic`, `groupId`, `mode`) and starts publishing the
   provider source stream into that topic.
3. The provider CP sends a standard `TransferStartMessage` to the consumer, embedding those Kafka
   transport details inside `dataAddress`.
4. **Consumer admin** calls *Download data*.
   The consumer CP routes `POST /dataflows/start` to the consumer Kafka DP, which subscribes to the
   prepared topic and writes consumed records into the consumer bucket.
5. Finite transfers complete when the consumer sees the EOF marker published by the provider DP.
   `viewData` then returns a normal presigned URL for the stored consumer-side artifact.

Current implementation notes:

- Topic names are normalized from transfer IDs into Kafka-safe names such as
  `stream-topic-urn_uuid_...`.
- Suspend and resume are **not** implemented for `stream:kafka` yet; terminate and re-request the
  transfer instead.

### Viewing downloaded data

After a transfer reaches `COMPLETED`, call:

```
GET /api/v1/transfers/{transferProcessId}/view
```

The CP generates a presigned S3 GET URL for the artifact **directly via its own S3 client**
(stored under `objectKey = transferProcessId` in the consumer's bucket). The URL is valid
for 7 days. No DP call is made.

---

## Audit Events

Both the Control Plane and each Data Plane record audit events to their respective MongoDB
collections.

### Control Plane audit events for DP registration

Query via the CP admin API:

```bash
# All DP registration events
curl "http://localhost:8080/api/v1/audit?eventType=Data+Plane+registered" \
  -u admin@mail.com:password

# All DP audit event types
curl http://localhost:8080/api/v1/audit/types \
  -u admin@mail.com:password
```

| Event type | Trigger |
|---|---|
| `DATAPLANE_REGISTERED` | New DP connected (`POST /api/v1/dataplanes`) |
| `DATAPLANE_REGISTRATION_UPDATED` | DP restarted and re-registered (idempotent update) |
| `DATAPLANE_DEREGISTERED` | DP removed (`DELETE /api/v1/dataplanes/{id}`) |
| `DATAPLANE_REGISTRATION_NOT_FOUND` | Delete attempted on unknown registration ID |

### Data Plane audit events

Each DP exposes its own audit event API at `/api/v1/audit` (protected by `X-Api-Key`):

```bash
# All events for a specific transfer
curl "http://localhost:9090/api/v1/audit?processId=urn:uuid:..." \
  -H "X-Api-Key: dp-secret-key"

# Filter by transfer type
curl "http://localhost:9090/api/v1/audit?transferType=HttpData-PULL" \
  -H "X-Api-Key: dp-secret-key"

# Filter by event type
curl "http://localhost:9090/api/v1/audit?eventType=Data+flow+completed" \
  -H "X-Api-Key: dp-secret-key"

# Supported event types
curl http://localhost:9090/api/v1/audit/types \
  -H "X-Api-Key: dp-secret-key"
```

| Event type | When |
|---|---|
| `DATAFLOW_STARTED` | DP receives `/dataflows/start` |
| `DATAFLOW_PREPARE_REQUESTED` | DP receives `/dataflows/prepare` |
| `DATAFLOW_COMPLETED` | Transfer finishes and CP is notified |
| `DATAFLOW_FAILED` | Transfer fails, CP is notified of error |
| `DATAFLOW_TERMINATED` | Explicit terminate received |
| `DATAFLOW_SUSPENDED` | Suspend received |
| `DP_REGISTRATION_SUCCESS` | DP successfully registered with CP on startup |
| `DP_REGISTRATION_FAILED` | CP registration failed after all retries |

Events are stored in the `dp_audit_events` MongoDB collection on each DP's own MongoDB instance.
This is separate from the CP's `audit_events` collection because the DP is an independent service.

---

## Suspend and Resume

A transfer in `STARTED` state can be suspended **before** data movement has started:

```
PUT /api/v1/transfers/{id}/suspend   → 200 OK  (no download in progress)
PUT /api/v1/transfers/{id}/suspend   → 400     (download already in progress)
```

Once a download is in progress, the data plane transfer cannot be paused. The CP will
reject a suspend request with HTTP 400 in that case.

To resume a suspended transfer, the provider admin calls *Start transfer* again.

---

## Registering a Data Plane Manually

If automatic startup registration is disabled or fails, register a DP via the CP admin API:

```bash
curl -X POST http://localhost:8080/api/v1/dataplanes \
  -H "Content-Type: application/json" \
  -u admin@mail.com:password \
  -d '{
    "endpoint": "http://my-dp:9090",
    "supportedTransferTypes": ["HttpData-PULL"],
    "apiKey": "my-secret"
  }'
```

### View registered Data Planes

```bash
curl http://localhost:8080/api/v1/dataplanes \
  -u admin@mail.com:password
```

### Remove a Data Plane

```bash
curl -X DELETE http://localhost:8080/api/v1/dataplanes/{id} \
  -u admin@mail.com:password
```

---

## Scaling

HTTP-PULL and HTTP-PUSH dataplanes are stateless Spring Boot applications. Run multiple
instances and register each separately:

```bash
# Register replica 1
curl -X POST http://localhost:8080/api/v1/dataplanes \
  -u admin@mail.com:password \
  -d '{"endpoint":"http://dp-pull-1:9090","supportedTransferTypes":["HttpData-PULL"],"apiKey":"..."}'

# Register replica 2
curl -X POST http://localhost:8080/api/v1/dataplanes \
  -u admin@mail.com:password \
  -d '{"endpoint":"http://dp-pull-2:9090","supportedTransferTypes":["HttpData-PULL"],"apiKey":"..."}'
```

For these stateless HTTP dataplanes, requests are distributed in round-robin order across
registered DPs of the same transfer type.

Streaming dataplanes (`stream:grpc`, `stream:kafka`) are different: after `prepare()`, the CP
sticky-routes the transfer to the same DP instance because that instance holds the in-memory
prepared session state. Do not assume equal round-robin distribution for an in-progress streaming
transfer, and avoid terminating the specific DP instance assigned to a prepared or started
streaming session.

---

## Adding a Custom Data Plane

If you have a custom DP implementation that follows the Dataplane Signaling API spec,
register it the same way using `POST /api/v1/dataplanes`. Your DP must expose:

- `POST /dataflows/start` — begin a transfer (async)
- `POST /dataflows/prepare` — prepare resources (DPS spec; not called by the built-in CP for HTTP-PULL or HTTP-PUSH)
- `POST /dataflows/{id}/terminate` — abort a transfer (also accepts `DELETE`)
- `POST /dataflows/{id}/suspend` — suspend a transfer
- `POST /dataflows/{id}/resume` — resume a suspended transfer
- `GET /dataflows/{id}/status` — query the current data flow state

Your DP should send canonical per-transfer callbacks to the CP after each lifecycle event:

- `POST {callbackBaseAddress}/api/v1/transfers/{processId}/dataflow/prepared` — resources prepared; required for DPs that implement `prepare()`
- `POST {callbackBaseAddress}/api/v1/transfers/{processId}/dataflow/started` — transfer started
- `POST {callbackBaseAddress}/api/v1/transfers/{processId}/dataflow/completed` — transfer completed
- `POST {callbackBaseAddress}/api/v1/transfers/{processId}/dataflow/errored` — transfer failed

Each callback must carry the `X-Api-Key` header with the DP's registered API key.

See `doc/data-plane-signaling-technical.md` for the full message schemas.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Transfer stays in `REQUESTED` | No DP registered for that transfer type | Register a DP via admin API |
| `stream:grpc` stays in `REQUESTED` after *Start transfer* | No gRPC DP registered on the provider side or `prepare` failed | Check `/api/v1/dataplanes`, provider DP logs, and gRPC DP registration |
| `stream:grpc` reaches `STARTED` but *Download data* fails immediately | Consumer received incomplete gRPC `dataAddress` metadata | Verify provider `prepare` response contains `host`, `port`, and `sessionId` |
| `stream:kafka` stays in `REQUESTED` after *Start transfer* | No Kafka DP registered on the provider side or the broker-backed `prepare` call failed | Check `/api/v1/dataplanes`, provider DP logs, Kafka broker health, and Kafka DP registration |
| `stream:kafka` reaches `STARTED` but *Download data* fails immediately | Consumer received incomplete Kafka `dataAddress` metadata | Verify provider `prepare` response contains `bootstrapServers`, `topic`, and `groupId` |
| `stream:kafka` start fails with topic creation errors | Transfer ID was mapped to an invalid topic name or the broker is unavailable | Check provider Kafka DP logs for `Failed to create Kafka topic` and verify Kafka broker health |
| Non-finite gRPC stream ends and transfer moves to error | Provider closed a stream that was advertised as non-finite | Check provider source implementation and DP logs; non-finite EOF is treated as an error |
| DP logs "CP registration failed" on startup | CP not reachable or wrong endpoint | Check `dataplane.control-plane-admin-endpoint` |
| CP rejects DP callbacks with HTTP 401 | API key mismatch | Verify `dataplane.api-key` matches the key stored in `DataPlaneRegistration` on the CP |
| Transfer stuck in `STARTED` after push | DP completed but CP callback failed | Check DP logs; verify the CP callback URL (`dataplane.control-plane-admin-endpoint`) is reachable from the DP container |
| `400 Cannot suspend while data transfer is in progress` | `downloadData()` was already called | Suspend is only valid before the actual data movement starts |
| `viewData` returns a presigned URL with the wrong host | `s3.externalPresignedEndpoint` not set on the consumer CP | Set `s3.externalPresignedEndpoint` to the host-accessible MinIO address (e.g. `http://172.17.0.1:9000`) on the consumer connector; the built-in `viewData` flow is handled by the CP, not the DP |
| `viewData` returns 400 | Transfer not yet COMPLETED or artifact not downloaded | Ensure the transfer is COMPLETED and `isDownloaded = true` |
