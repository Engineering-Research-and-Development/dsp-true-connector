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
| **Transfer type** | Protocol used for data movement — `HttpData-PULL` or `HttpData-PUSH` |
| **DP Registration** | A CP record describing where a DP lives and what transfer types it supports |

---

## Deployment

The connector ships two ready-made Data Plane images:

| Image | Transfer type | Default port |
|---|---|---|
| `data-plane-http-pull` | `HttpData-PULL` | 9090 |
| `data-plane-http-push` | `HttpData-PUSH` | 9091 |

Both are included in the Docker Compose file at `ci/docker/docker-compose.yml`.

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

The push DP needs direct S3 access to generate presigned URLs for the provider's artifact:

```properties
s3.endpoint=http://minio:9000
s3.accessKey=minioadmin
s3.secretKey=minioadmin
s3.region=us-east-1
s3.bucketName=my-provider-bucket
# Required when MinIO runs behind Docker NAT — use the host-accessible address:
s3.externalPresignedEndpoint=http://172.17.0.1:9000
```

Leave `s3.endpoint` blank when using AWS S3 (the SDK resolves the correct endpoint automatically).

---

## How Transfers Work

### HTTP-PULL — consumer fetches the artifact

1. **Consumer** requests a transfer (`HttpData-PULL`).
2. **Provider admin** calls *Start transfer* — the CP asks the registered pull DP to generate
   a presigned download URL for the artifact. That URL is sent to the consumer inside
   `TransferStartMessage`.
3. **Consumer admin** calls *Download data* — the consumer-side pull DP downloads the artifact
   from the presigned URL and stores it in the consumer's S3 bucket.
4. The DP notifies the CP when done; both sides move to `COMPLETED`.
5. **Consumer admin** can call *View data* to get a fresh presigned URL for the stored artifact.

### HTTP-PUSH — provider pushes the artifact to the consumer

1. **Consumer admin** calls *Request transfer* (`HttpData-PUSH`).
   The CP automatically creates a temporary S3 user with write-only access to the consumer's
   bucket and includes those credentials in the transfer request to the provider.
2. **Provider admin** calls *Start transfer* — the CP forwards the consumer's S3 credentials
   to the provider's transfer process. No data moves yet.
3. **Provider admin** calls *Push data* (the download endpoint on the provider side) — this
   triggers the push DP to download the artifact from the provider's S3 bucket and upload it
   directly to the consumer's S3 bucket using the temporary credentials.
4. The DP notifies both CPs when done; both sides move to `COMPLETED`.
5. **Consumer admin** can call *View data* to get a presigned URL for the received artifact.

> **Note**: Temporary push credentials are automatically cleaned up after the transfer
> completes or is terminated.

### Viewing downloaded data

After a transfer reaches `COMPLETED`, call:

```
GET /api/v1/transfers/{transferProcessId}/view
```

The CP delegates to the registered DP to generate a presigned S3 GET URL for the artifact
(stored under `objectKey = transferProcessId` in the consumer's bucket). The URL is valid
for 7 days.

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

Each Data Plane is a stateless Spring Boot application. Run multiple instances and register
each separately:

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

Requests are distributed in round-robin order across registered DPs of the same transfer type.

---

## Adding a Custom Data Plane

If you have a custom DP implementation that follows the Dataplane Signaling API spec,
register it the same way using `POST /api/v1/dataplanes`. Your DP must expose:

- `POST /dataflows/start` — begin a transfer (async)
- `POST /dataflows/prepare` — prepare without transferring (returns metadata such as a presigned URL)
- `DELETE /dataflows/{id}` — abort a transfer
- Callback to CP: `POST {callbackAddress}/api/v1/dataflows/complete` — notify CP of completion

See `doc/data-plane-signaling-technical.md` for the full message schemas.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Transfer stays in `REQUESTED` | No DP registered for that transfer type | Register a DP via admin API |
| DP logs "CP registration failed" on startup | CP not reachable or wrong endpoint | Check `dataplane.control-plane-admin-endpoint` |
| CP rejects DP callbacks with HTTP 401 | API key mismatch | Verify `dataplane.api-key` matches the key stored in `DataPlaneRegistration` on the CP |
| Transfer stuck in `STARTED` after push | DP completed but CP callback failed | Check DP logs; verify the CP callback URL (`dataplane.control-plane-admin-endpoint`) is reachable from the DP container |
| `400 Cannot suspend while data transfer is in progress` | `downloadData()` was already called | Suspend is only valid before the actual data movement starts |
| Push DP generates presigned URL with wrong host | `s3.externalPresignedEndpoint` not set | Set `s3.externalPresignedEndpoint` to the host-accessible MinIO address (e.g. `http://172.17.0.1:9000`) |
| `viewData` returns 400 | Transfer not yet COMPLETED or artifact not downloaded | Ensure the transfer is COMPLETED and `isDownloaded = true` |
