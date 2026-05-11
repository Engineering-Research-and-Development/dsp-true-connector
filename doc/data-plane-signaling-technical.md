# Data Plane Signaling — Technical Reference

## Overview

TRUE Connector implements the
[Eclipse Dataplane Signaling Protocol (DPS)](https://github.com/eclipse-dataplane-signaling/dataplane-signaling).
The Control Plane (CP) and each Data Plane (DP) communicate over REST, with the CP
acting as the orchestrator and each DP as an independent service.

---

## Architecture

```
Consumer CP              Provider CP              Provider DP (HTTP-PULL or HTTP-PUSH)
    |                        |                              |
    |--- TransferRequest ---->|                              |
    |                        |--- POST /dataflows/start --->|
    |                        |                              |--- executes transfer
    |                        |<-- POST /{cpCallback} ------|
    |<-- TransferStartMsg ----|                              |
```

### Modules

| Module | Role | Artifact |
|---|---|---|
| `data-plane-api` | SPI interfaces + DSP message models | Library JAR |
| `data-plane-core` | Shared runtime: registration, routing, client | Library JAR |
| `data-plane-http-pull` | HTTP-PULL standalone service (port 9090) | Spring Boot fat JAR |
| `data-plane-http-push` | HTTP-PUSH standalone service (port 9091) | Spring Boot fat JAR |

### Control Plane additions

| Component | Package | Purpose |
|---|---|---|
| `DataPlaneRegistration` | `it.eng.datatransfer.model` | Persisted DP registration record |
| `DataPlaneRegistrationService` | `it.eng.datatransfer.service` | CRUD + routing logic |
| `DataPlaneRouter` | `it.eng.datatransfer.router` | Selects DP by transfer type (round-robin) |
| `DataPlaneClient` | `it.eng.datatransfer.client` | CP → DP HTTP calls |
| `DataFlowCallbackController` | `it.eng.datatransfer.rest.api` | Receives DP status callbacks |
| `DataPlaneRegistrationController` | `it.eng.datatransfer.rest.api` | Admin CRUD for DP registrations |

---

## Data Plane Registration

A DP registers itself with the CP at startup by calling:

```
POST /api/v1/dataplanes
{
  "endpoint":              "http://dp-http-pull:9090",
  "supportedTransferTypes": ["HttpData-PULL"],
  "apiKey": "shared-secret"
}
```

`ControlPlaneRegistrationBean` (in `data-plane-core`) performs this call with exponential-backoff
retry (5 attempts, base delay 2 s). If `dataplane.control-plane-admin-endpoint` is not set,
registration is skipped (useful for local development without a CP).

The CP stores the registration in MongoDB and uses it to route `DataFlowStartMessage` requests.

---

## Transfer Flow

### HTTP-PULL

1. CP receives `TransferRequestMessage` from consumer with `transferType = HttpData-PULL`.
2. CP calls `POST /dataflows/start` on the registered HTTP-PULL DP with a `DataFlowStartMessage`.
3. DP generates a presigned S3 GET URL and sends `TransferStartMessage` back to the consumer.
4. Consumer downloads the artifact directly from the presigned URL.
5. DP posts a completion callback to the CP's `DataFlowCallbackController`.
6. CP transitions the transfer process to `COMPLETED`.

### HTTP-PUSH

1. CP routes `TransferRequestMessage` (type `HttpData-PUSH`) to the HTTP-PUSH DP.
2. CP sends a `DataFlowStartMessage` to the DP with consumer S3 credentials in `dataAddress`.
3. DP downloads the artifact from the provider's S3 bucket using a presigned GET URL.
4. DP uploads the artifact directly to the consumer's S3 bucket using the provided credentials.
5. DP posts a completion callback to the CP.
6. CP transitions the transfer process to `COMPLETED`.

---

## API Key Authentication

All CP → DP calls carry an `X-Api-Key` header (value: `DataPlaneRegistration.apiKey`).
All DP → CP callbacks carry an `X-Api-Key` header (value: `DataPlaneProperties.apiKey`).

On each side, `ApiKeyAuthFilter` validates the header against the stored value. Requests with a
missing or mismatched key receive HTTP 401.

Set API keys in properties:
- CP: stored in `DataPlaneRegistration.apiKey` (written at registration time)
- DP: `dataplane.api-key=<secret>` in `application.properties`

---

## Concurrency Model

Each DP app uses Java 21 virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`).
Each transfer runs on its own virtual thread. There is no fixed pool ceiling — thousands of
concurrent transfers are practical on a single DP instance.

---

## OkHttpClient / TLS

Both DP apps component-scan `it.eng.tools`, which auto-configures `OkHttpClient` via
`OkHttpClientConfiguration`:
- `server.ssl.enabled=true` → TLS client with custom truststore (OCSP-validated)
- `server.ssl.enabled=false` → insecure noop client (development only)

See `doc/security.md` for truststore configuration details.

---

## MongoDB Collections

| Collection | Model | Owner |
|---|---|---|
| `data_plane_registrations` | `DataPlaneRegistration` | CP (`data-transfer` module) |

---

## Key Configuration Properties

### Data Plane (`application.properties` in each DP app)

| Property | Description | Example |
|---|---|---|
| `dataplane.endpoint` | Public URL of this DP | `http://dp-http-pull:9090` |
| `dataplane.control-plane-admin-endpoint` | CP admin base URL | `http://connector:8080` |
| `dataplane.api-key` | Shared secret for DP↔CP auth | `dp-secret-key` |
| `server.port` | Listening port | `9090` (pull) / `9091` (push) |
| `server.ssl.enabled` | Enable TLS | `true` / `false` |

---

## Extending with a New Data Plane Type

1. Create a new Spring Boot module (e.g., `data-plane-mqtt`) that depends on `data-plane-core`.
2. Implement the `DataTransferProtocol` SPI interface and annotate with `@Component`.
3. The DP app self-registers with the CP on startup via `ControlPlaneRegistrationBean`.
4. Register the supported transfer type(s) in `DataPlaneProperties.supportedTransferTypes`.
5. No CP code changes needed — `DataPlaneRouter` selects the correct DP by transfer type.
