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
| **DP Registration** | A CP record describing where a DP lives and what it supports |

---

## Deployment

The connector ships two ready-made Data Plane images:

| Image | Transfer type | Default port |
|---|---|---|
| `data-plane-http-pull` | `HttpData-PULL` | 9090 |
| `data-plane-http-push` | `HttpData-PUSH` | 9091 |

Both are included in the default Docker Compose at `ci/docker/docker-compose.yml`.

### Minimum required configuration (each DP)

```properties
# Which CP to register with
dataplane.control-plane-admin-endpoint=http://connector:8080

# Public URL of this DP (reachable from CP)
dataplane.endpoint=http://dp-http-pull:9090

# Shared secret — must match what the CP stores for this DP
dataplane.api-key=change-me-in-production
```

---

## Registering a Data Plane Manually

If automatic startup registration is disabled or fails, register a DP via the CP admin API:

```bash
curl -X POST http://localhost:8080/api/v1/dataplanes \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic ..." \
  -d '{
    "endpoint": "http://my-dp:9090",
    "supportedTransferTypes": ["HttpData-PULL"],
    "apiKey": "my-secret"
  }'
```

### View registered Data Planes

```bash
curl http://localhost:8080/api/v1/dataplanes \
  -H "Authorization: Basic ..."
```

### Remove a Data Plane

```bash
curl -X DELETE http://localhost:8080/api/v1/dataplanes/{id} \
  -H "Authorization: Basic ..."
```

---

## Running a Transfer

Transfers work the same as before — initiate via the standard DSP Transfer Request API.
The connector automatically routes the request to the appropriate Data Plane based on the
requested transfer type.

**Consumer side** — request a transfer with the desired transfer type:
```json
{
  "@context": "https://w3id.org/dspace/2025/1/context.json",
  "@type": "dspace:TransferRequestMessage",
  "dspace:agreementId": "...",
  "dcat:format": "HttpData-PULL",
  "dspace:dataAddress": {}
}
```

---

## Scaling

Each Data Plane is a stateless Spring Boot application. You can run multiple instances of the
same DP type and register each separately with the CP:

```bash
# Register replica 1
curl -X POST http://localhost:8080/api/v1/dataplanes \
  -d '{"endpoint":"http://dp-pull-1:9090","supportedTransferTypes":["HttpData-PULL"],"apiKey":"..."}'

# Register replica 2
curl -X POST http://localhost:8080/api/v1/dataplanes \
  -d '{"endpoint":"http://dp-pull-2:9090","supportedTransferTypes":["HttpData-PULL"],"apiKey":"..."}'
```

`DataPlaneRouter` selects a DP in round-robin order among registered DPs for the requested
transfer type.

---

## Adding a Custom Data Plane

If you have a custom DP implementation compliant with the Dataplane Signaling API spec,
register it the same way as the built-in DPs using `POST /api/v1/dataplanes`.
Your DP must implement:

- `POST /dataflows/start` — start a transfer
- `POST /dataflows/{id}/stop` — stop a transfer
- Callback to CP: `POST {callbackAddress}` — send status updates to CP

See `doc/data-plane-signaling-technical.md` for the full API contract.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Transfer stays in `REQUESTED` state | No DP registered for that transfer type | Register a DP via admin API |
| DP logs "CP registration failed" on startup | CP not reachable or wrong endpoint configured | Check `dataplane.control-plane-admin-endpoint` |
| CP rejects DP callbacks with HTTP 401 | API key mismatch | Verify `dataplane.api-key` matches `DataPlaneRegistration.apiKey` on CP |
| Transfer stuck in `STARTED` state | DP completed but callback not received | Check DP logs for errors; verify CP callback URL is reachable from DP |
