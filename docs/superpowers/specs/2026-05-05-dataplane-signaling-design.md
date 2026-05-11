# Dataplane Signaling Protocol (DPS) — Implementation Design

**Date**: 2026-05-05  
**Specification**: [eclipse-dataplane-signaling/dataplane-signaling](https://github.com/eclipse-dataplane-signaling/dataplane-signaling)  
**Related DSP Spec**: [DataspaceProtocol 2025-1](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/)

---

## 1. Problem Statement

TRUE Connector currently runs as a monolith: the DSP Control Plane (catalog, negotiation, transfer process management) and the Data Plane (actual wire-protocol data transfer via HTTP pull/push or S3) are tightly coupled in a single Spring Boot application. Transfer strategies are invoked directly from the control plane service layer.

The [Dataplane Signaling Protocol (DPS)](https://github.com/eclipse-dataplane-signaling/dataplane-signaling/blob/main/docs/signaling.md) defines a standard HTTP API for communication between a Control Plane and a Data Plane, enabling:
- Decoupled, independently deployable control and data plane services
- A pluggable data plane ecosystem (any DPS-compliant data plane can be paired with any DPS-compliant control plane)
- A clearly separated `DataFlow` state machine (physical transfer lifecycle) from the DSP `TransferProcess` state machine (negotiation lifecycle)

This document designs the implementation of DPS as a **full microservice split** of the TRUE Connector into two standalone Spring Boot applications. The Data Plane is always a separate, independently deployable service. Multiple Data Plane instances — including third-party DPS-compliant implementations — can be registered with the Control Plane simultaneously, each serving different transfer types and scaling independently.

---

## 2. Deployment Model

The Data Plane is **always deployed as a separate service**. There is no embedded mode. This clean boundary:
- Honours the DPS spec's HTTP API in full — all communication between CP and DP is real REST over the network.
- Allows each Data Plane instance to scale independently (e.g., run N HTTP-PULL replicas behind a load balancer).
- Allows any DPS-compliant third-party Data Plane to be registered and used without code changes.

```
┌──────────────────────────────────────────────────────────────────────────┐
│ CONTROL PLANE  (connector, ports 8080/8090)                              │
│                                                                          │
│  DataPlaneRegistry:                                                      │
│    "HttpData-PULL" → [http://dp-http-1:9090, http://dp-http-2:9090]     │
│    "HttpData-PUSH" → [http://dp-http-1:9090, http://dp-http-2:9090]     │
│    "MQTTData-PUSH" → [http://dp-mqtt-1:9092]                            │
│                                                                          │
│  DataPlaneRouter: selects instance for a given transferType              │
│  DataPlaneClient: sends DPS messages to the selected instance            │
└──────────────────────────────────────────────────────────────────────────┘
          │ HTTP DPS                   │ HTTP DPS              │ HTTP DPS
          ▼                            ▼                        ▼
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────────┐
│ data-plane-http  │     │ data-plane-http   │     │ data-plane-mqtt      │
│ instance 1 :9090 │     │ instance 2 :9090  │     │ instance 1 :9092     │
│ HttpData-PULL    │     │ HttpData-PULL     │     │ MQTTData-PUSH        │
│ HttpData-PUSH    │     │ HttpData-PUSH     │     │ MQTTData-PULL        │
└──────────────────┘     └──────────────────┘     └──────────────────────┘
```

**Getting started**: the repository ships a Docker Compose file with `connector` + `data-plane-http` pre-wired. No extra setup is needed for local development — run `docker compose up` to get a working two-service connector.

**Third-party interoperability**: any service that implements the DPS `/dataflows/*` endpoints can self-register with the Control Plane via `PUT /dataplanes`. The Control Plane treats it identically to its own `data-plane-core` instances.

---

## 3. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│ CONTROL PLANE (connector module, ports 8080/8090)               │
│                                                                  │
│  Modules loaded: catalog + negotiation + data-transfer          │
│                                                                  │
│  DSP Endpoints:  /{tenantId}/catalog                            │
│                  /{tenantId}/negotiations                        │
│                  /{tenantId}/transfers                           │
│  Admin Endpoints: /api/v1/...                                   │
│  DPS CP Callbacks: /transfers/{id}/dataflow/prepared            │
│                    /transfers/{id}/dataflow/started             │
│                    /transfers/{id}/dataflow/completed           │
│                    /transfers/{id}/dataflow/errored             │
│                    GET /transfers/{id}/agreement                │
│  Data Plane Registration: PUT/DELETE /dataplanes                │
│                                                                  │
│  DataPlaneRegistry (by transferType) + DataPlaneRouter                  │
│  DataPlaneClient ──────────HTTP DPS calls────────────────────┐  │
└─────────────────────────────────────────────────────────────┼──┘
                                                              │
                                              async callbacks │
                                                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ DATA PLANE (data-plane module, port 9090)                        │
│                                                                  │
│  DPS Endpoints:  POST /dataflows/prepare                        │
│                  POST /dataflows/start                          │
│                  POST /dataflows/{id}/started                   │
│                  POST /dataflows/{id}/suspend                   │
│                  POST /dataflows/{id}/resume                    │
│                  POST /dataflows/{id}/terminate                 │
│                  POST /dataflows/{id}/completed                 │
│                  GET  /dataflows/{id}/status                    │
│  CP Registration: PUT/DELETE /controlplanes                     │
│                                                                  │
│  DataFlowService + DataFlowRepository (MongoDB)                 │
│  DataTransferProtocolRegistry (collects all protocol beans)     │
│                                                                  │
│  Protocol plugins (JARs on classpath — see Section 4):          │
│    data-plane-http  → "HttpData-PULL", "HttpData-PUSH"          │
│    data-plane-s3    → "S3Data-PUSH" (future)                    │
│    data-plane-mqtt  → "MQTTData-PUSH" (future)                  │
│    data-plane-kafka → "KafkaData-PUSH" (future)                 │
│                                                                  │
│  ControlPlaneClient ──────HTTP callbacks──► Control Plane       │
└─────────────────────────────────────────────────────────────────┘
```

**Shared**: The `tools` module is a dependency of both applications. The `catalog`, `negotiation`, and `data-transfer` modules are dependencies of the Control Plane only.

---

## 4. Data Flow State Machine

The `DataFlow` entity represents the physical transfer lifecycle managed by the Data Plane. It is separate from the DSP `TransferProcess`.

### States

| State | Description |
|---|---|
| `INITIALIZED` | Data flow created, not yet active |
| `PREPARING` | Async preparation in progress (e.g., provisioning resources) |
| `PREPARED` | Consumer data plane is ready; `DataAddress` available |
| `STARTING` | Async start in progress |
| `STARTED` | Data transfer is actively running |
| `SUSPENDED` | Transfer paused; no data is being sent |
| `COMPLETED` | Transfer completed normally (terminal) |
| `TERMINATED` | Transfer stopped before completion (terminal) |

### Valid Transitions

| From | Signal | To |
|---|---|---|
| INITIALIZED | `prepare` | PREPARING (async) or PREPARED (sync) |
| INITIALIZED | `start` | STARTING (async) or STARTED (sync) |
| PREPARED | `start` | STARTING (async) or STARTED (sync) |
| PREPARING | internal | PREPARED |
| STARTING | internal | STARTED |
| STARTED | `suspend` | SUSPENDED |
| STARTED | `completed` | COMPLETED |
| SUSPENDED | `resume` | STARTED |
| any non-terminal | `terminate` | TERMINATED |

Terminal states (COMPLETED, TERMINATED) MUST NOT transition further.

### DataFlow Model

```java
// data-plane-core/src/main/java/it/eng/dataplane/model/DataFlow.java
public class DataFlow {
    String dataFlowId;          // Data Plane internal ID
    String processId;           // TransferProcess ID from Control Plane (correlation)
    String agreementId;
    String datasetId;
    String transferType;        // free-form string e.g. "HttpData-PULL", "MQTTData-PUSH"
    String callbackAddress;     // Control Plane DPS callback base URL
    DataFlowState state;
    DataAddress dataAddress;    // endpoint info
    String tenantId;
    String participantId;
    String counterPartyId;
    String error;               // populated on ERRORED signal
    Instant createdAt;
    Instant updatedAt;
}
```

---

## 5. Data Plane Application Architecture (Extensibility)

Each transfer type is a **completely independent, standalone Spring Boot application**. Adding a new wire protocol (MQTT, Kafka, SFTP, S3) means creating a new module — no changes to any existing module.

### Module Layout

```
data-plane-api/            ← Interface-only, no Spring, no implementations
                               DataTransferProtocol interface
                               DPS message models (DataFlowStartMessage, etc.)
                               DataFlow, DataFlowState, DataFlowResult models

data-plane-core/           ← Shared DPS runtime (library JAR, NOT a Spring Boot app)
                               DataFlowController, DataFlowService, DataFlowRepository
                               DataTransferProtocolRegistry
                               ControlPlaneClient, DataPlaneProperties
                               DataPlaneSecurityConfig
                               Used as a dependency by every protocol app

data-plane-http-pull/      ← Standalone Spring Boot app (port 9090)
                               ApplicationHttpPullDataPlane.java (@SpringBootApplication)
                               HttpPullTransferProtocol implements DataTransferProtocol
                               Announces: "HttpData-PULL"

data-plane-http-push/      ← Standalone Spring Boot app (port 9091)
                               ApplicationHttpPushDataPlane.java (@SpringBootApplication)
                               HttpPushTransferProtocol implements DataTransferProtocol
                               Announces: "HttpData-PUSH"

data-plane-s3/             ← Standalone Spring Boot app (future)
                               Announces: "S3Data-PUSH"

data-plane-mqtt/           ← Standalone Spring Boot app (future)
                               Announces: "MQTTData-PUSH", "MQTTData-PULL"
```

Each protocol app depends on `data-plane-api` + `data-plane-core` (library) + its own transfer implementation. `data-plane-core` provides the DPS endpoint framework; the protocol app provides the `@SpringBootApplication` entry point and the `DataTransferProtocol` bean.

**Third-party DPS-compliant data planes** (written in any language, by any vendor) can be registered with the Control Plane via `PUT /dataplanes` and used identically — they need not use `data-plane-core` at all.

### `DataTransferProtocol` Interface

```java
// data-plane-api/src/main/java/it/eng/dataplane/api/DataTransferProtocol.java
public interface DataTransferProtocol {

    /** The transfer type string this protocol handles, e.g. "HttpData-PULL". */
    String transferType();

    /** Execute the data flow. Called by DataFlowService on start/prepare. */
    CompletableFuture<DataFlowResult> execute(DataFlow dataFlow);

    /** Called on suspend; implementations may pause or buffer. */
    void suspend(DataFlow dataFlow);

    /** Called on resume. */
    void resume(DataFlow dataFlow);

    /** Called on terminate; implementations must clean up resources. */
    void terminate(DataFlow dataFlow);
}
```

Transfer types are **strings**, not an enum. No enum to extend when adding a new protocol. The `DataTransferFormat` enum in `data-transfer` is removed.

### `DataTransferProtocolRegistry`

```java
// data-plane-core — each protocol app has exactly one DataTransferProtocol bean
@Service
public class DataTransferProtocolRegistry {

    private final Map<String, DataTransferProtocol> protocols;

    public DataTransferProtocolRegistry(List<DataTransferProtocol> protocols) {
        this.protocols = protocols.stream()
            .collect(Collectors.toMap(DataTransferProtocol::transferType, p -> p));
    }

    public DataTransferProtocol get(String transferType) {
        DataTransferProtocol protocol = protocols.get(transferType);
        if (protocol == null) {
            throw new DataFlowException("No protocol registered for transfer type: " + transferType);
        }
        return protocol;
    }

    /** Used by startup registration to announce supported types to Control Plane. */
    public Set<String> supportedTransferTypes() {
        return protocols.keySet();
    }
}
```

### Standalone application per protocol

Each protocol module has its own `@SpringBootApplication` entry point and registers its `DataTransferProtocol` bean as a regular Spring `@Service`. No auto-configuration machinery is needed — each app contains exactly one protocol implementation.

```java
// data-plane-http-pull/src/main/java/it/eng/dataplane/httppull/ApplicationHttpPullDataPlane.java
@SpringBootApplication(scanBasePackages = {"it.eng.dataplane", "it.eng.tools"})
public class ApplicationHttpPullDataPlane {
    public static void main(String[] args) {
        SpringApplication.run(ApplicationHttpPullDataPlane.class, args);
    }
}

// data-plane-http-pull/src/main/java/it/eng/dataplane/httppull/HttpPullTransferProtocol.java
@Service
public class HttpPullTransferProtocol implements DataTransferProtocol {

    @Override
    public String transferType() { return "HttpData-PULL"; }

    @Override
    public CompletableFuture<DataFlowResult> execute(DataFlow dataFlow) { /* ... */ }
    // suspend, resume, terminate ...
}
```

### Adding a new protocol (e.g., MQTT)

1. Create Maven module `data-plane-mqtt`
2. Depend on `data-plane-api` + `data-plane-core`
3. Implement `DataTransferProtocol` for MQTT as a `@Service`
4. Add `@SpringBootApplication` entry point
5. **No changes to any existing module**

On startup, each protocol app sends `PUT /dataplanes` to the Control Plane with its `transferType` from `DataTransferProtocolRegistry.supportedTransferTypes()`. The Control Plane records the endpoint and supported type, and routes `DataFlowStartMessage`/`DataFlowPrepareMessage` to the correct instance.

### `data-plane-core` Module Structure (shared library)

```
data-plane-core/
├── pom.xml  (library JAR — spring-boot-starter-web, data-mongodb, security, oauth2)
└── src/
    ├── main/
    │   ├── java/it/eng/dataplane/
    │   │   ├── model/
    │   │   │   ├── DataFlow.java
    │   │   │   ├── DataFlowState.java
    │   │   │   ├── DataFlowResult.java
    │   │   │   └── ControlPlaneRegistration.java
    │   │   ├── rest/
    │   │   │   ├── DataFlowController.java
    │   │   │   └── ControlPlaneRegistrationController.java
    │   │   ├── service/
    │   │   │   ├── DataFlowService.java
    │   │   │   ├── DataTransferProtocolRegistry.java
    │   │   │   └── ControlPlaneClient.java
    │   │   ├── repository/
    │   │   │   ├── DataFlowRepository.java
    │   │   │   └── ControlPlaneRegistrationRepository.java
    │   │   └── configuration/
    │   │       ├── DataPlaneSecurityConfig.java
    │   │       └── DataPlaneProperties.java
    │   └── resources/
    │       └── application.properties  (defaults only)
    └── test/
        └── java/it/eng/dataplane/
            ├── service/DataFlowServiceTest.java
            └── rest/DataFlowControllerTest.java
```

### `data-plane-http-pull` Module Structure (standalone app)

```
data-plane-http-pull/
├── pom.xml  (depends on data-plane-core + data-plane-api + tools)
└── src/
    ├── main/
    │   ├── java/it/eng/dataplane/httppull/
    │   │   ├── ApplicationHttpPullDataPlane.java
    │   │   ├── HttpPullTransferProtocol.java
    │   │   └── configuration/HttpPullConfiguration.java  (thread pool bean)
    │   └── resources/
    │       └── application.properties  (server.port=9090, S3 config, etc.)
    └── test/
        └── java/it/eng/dataplane/httppull/
            ├── HttpPullTransferProtocolTest.java
            └── integration/HttpPullDataFlowIT.java
```

`data-plane-http-push` follows the same structure on port `9091`.

### Maven `pom.xml` Dependencies for a protocol app
- `it.eng.dataplane:data-plane-api`
- `it.eng.dataplane:data-plane-core`
- `it.eng.tools:tools`
- `spring-boot-starter-web`
- `spring-boot-starter-data-mongodb`
- `spring-boot-starter-security`
- `spring-boot-starter-oauth2-resource-server`
- `okhttp3` (HTTP-based apps)

---

## 6. Changes to `data-transfer` Module (Control Plane Side)

### New classes

| Class | Location | Purpose |
|---|---|---|
| `DataPlaneClient` | `service/` | HTTP client sending DPS messages to Data Plane (`/dataflows/...`) |
| `DataPlaneRouter` | `service/` | Selects the right `DataPlaneRegistration` by `transferType`; round-robins across same-type instances |
| `DataFlowCallbackController` | `rest/protocol/` | Receives DPS callbacks: `POST /transfers/{id}/dataflow/{state}` |
| `DataPlaneRegistrationController` | `rest/api/` | Admin: `PUT/DELETE /dataplanes` (called by Data Plane on startup) |
| `DataPlaneRegistration` | `model/` | Entity: `id`, `endpoint`, `supportedTransferTypes`, `authConfig`, `lastHeartbeat` |
| `DataPlaneRegistrationRepository` | `repository/` | MongoDB persistence for data plane registrations |
| `DataPlaneRegistrationService` | `service/` | CRUD + lookup by transferType |

### Modified classes

| Class | Change |
|---|---|
| `DataTransferAPIService.startTransfer()` | Instead of calling strategy directly, calls `DataPlaneClient.start(...)` with a `DataFlowStartMessage` |
| `DataTransferAPIService.requestTransfer()` | For HTTP-PUSH, calls `DataPlaneClient.prepare(...)` with a `DataFlowPrepareMessage` |
| `AbstractDataTransferService` | Remove direct strategy injection; strategies move to `data-plane-http-pull` and `data-plane-http-push` modules |

### New DPS Control Plane callback endpoint

```java
// data-transfer/src/main/java/it/eng/datatransfer/rest/protocol/DataFlowCallbackController.java

@RestController
@RequestMapping("/{tenantId}/transfers")
public class DataFlowCallbackController {

    @PostMapping("/{transferId}/dataflow/prepared")
    public ResponseEntity<Void> onPrepared(@PathVariable String tenantId,
                                           @PathVariable String transferId,
                                           @RequestBody DataFlowStatusMessage msg) { ... }

    @PostMapping("/{transferId}/dataflow/started")
    public ResponseEntity<Void> onStarted(@PathVariable String tenantId,
                                          @PathVariable String transferId,
                                          @RequestBody DataFlowStatusMessage msg) { ... }

    @PostMapping("/{transferId}/dataflow/completed")
    public ResponseEntity<Void> onCompleted(@PathVariable String tenantId,
                                            @PathVariable String transferId,
                                            @RequestBody DataFlowStatusMessage msg) { ... }

    @PostMapping("/{transferId}/dataflow/errored")
    public ResponseEntity<Void> onErrored(@PathVariable String tenantId,
                                          @PathVariable String transferId,
                                          @RequestBody DataFlowStatusMessage msg) { ... }

    @GetMapping("/{transferId}/agreement")
    public ResponseEntity<JsonNode> getAgreement(@PathVariable String tenantId,
                                                 @PathVariable String transferId) { ... }
}
```

---

## 7. DPS API Messages

### `DataFlowStartMessage` (Control Plane → Provider Data Plane)

```json
{
  "messageId": "b1d5f9e2-3c4b-4f7a-9c3e-2f1e5d6c7b8a",
  "participantId": "provider-participant-id",
  "counterPartyId": "consumer-participant-id",
  "dataspaceContext": "test-dataspace-context",
  "processId": "transfer-process-id",
  "agreementId": "agreement-id",
  "datasetId": "dataset-id",
  "callbackAddress": "https://control-plane:8090",
  "transferType": "HTTP_PULL",
  "claims": { "membership": "active" }
}
```

### `DataFlowPrepareMessage` (Control Plane → Consumer Data Plane)

Same structure as `DataFlowStartMessage` but sent to `POST /dataflows/prepare`. For HTTP-PUSH, omit `dataAddress`; the Data Plane generates the consumer endpoint (e.g., a temporary S3 bucket user) and returns it as `DataAddress` in the PREPARED response.

### `DataFlowStatusMessage` (Data Plane → Control Plane callbacks, and sync responses)

```json
{
  "dataFlowId": "dataflow-id",
  "state": "STARTED",
  "dataAddress": {
    "endpointType": "https://w3id.org/idsa/v4.1/HTTP",
    "endpoint": "https://presigned-url...",
    "endpointProperties": []
  },
  "error": ""
}
```

---

## 8. Authorization

### Supported profiles

Both `DataPlaneClient` (in Control Plane) and `ControlPlaneClient` (in Data Plane) support two auth modes, selected via configuration:

| Profile | Config key | Mechanism |
|---|---|---|
| OAuth2 Client Credentials | `application.dps.auth.type=oauth2_client_credentials` | Bearer token from token endpoint, cached with expiry |
| API Key | `application.dps.auth.type=api_key` | `X-Api-Key` header on all DPS calls |

### Registration startup order

Each plane registers with the other independently on startup using the configured endpoint address. There is no required ordering — each side retries if the other is not yet available. Once registered, subsequent calls use the stored endpoint and auth info.

### `callbackAddress` convention

The `callbackAddress` field in `DataFlowStartMessage` and `DataFlowPrepareMessage` is the **base URL** of the Control Plane (e.g. `https://control-plane:8090`). The Data Plane constructs the full callback URL by appending the well-known DPS path:

```
{callbackAddress}/transfers/{processId}/dataflow/prepared
{callbackAddress}/transfers/{processId}/dataflow/started
{callbackAddress}/transfers/{processId}/dataflow/completed
{callbackAddress}/transfers/{processId}/dataflow/errored
```

Where `{processId}` = the `processId` value from the DPS message, which equals `TransferProcess.id` on the Control Plane side.

### `processId` ↔ `transferId` mapping

`DataFlowStartMessage.processId` = `TransferProcess.id` (Control Plane). The Data Plane stores it as `DataFlow.processId`. All callbacks use this value as `transferId` in the URL path so the Control Plane can look up the correct `TransferProcess`.

---

## 9. Configuration Properties

### Control Plane (`connector/src/main/resources/application.properties`)

```properties
# DPS: callback address the Data Plane uses to signal back
application.dps.controlplane.callback-address=http://control-plane:8090

# Data Plane instances are registered dynamically via PUT /dataplanes.
# No static endpoint config is required — the registry is populated at runtime.
```

### Data Plane (`data-plane/src/main/resources/application.properties`)

```properties
server.port=9090
spring.data.mongodb.uri=mongodb://localhost:27017/dataplane

# DPS: Control Plane callback address
application.dps.controlplane.endpoint=http://control-plane:8090
application.dps.controlplane.auth.type=oauth2_client_credentials
application.dps.controlplane.auth.token-endpoint=http://keycloak:8080/realms/connector/protocol/openid-connect/token
application.dps.controlplane.auth.client-id=data-plane
application.dps.controlplane.auth.client-secret=secret

# S3/MinIO (same as current connector)
s3.endpoint=http://minio:9000
s3.accessKey=minioadmin
s3.secretKey=minioadmin
s3.region=us-east-1
s3.bucketName=dsp-true-connector-a
```

---

## 10. Request/Response Sequences

### HTTP-PULL (Provider Data Plane generates presigned URL)

```
Admin API         Control Plane          Data Plane (Provider)
    │                   │                       │
    │─ POST /api/v1/datatransfer ─►│             │
    │  (DataTransferRequest)       │─ POST /dataflows/start ─►│
    │                              │  (DataFlowStartMessage)   │─► HttpPullStrategy
    │                              │                           │   generates presigned URL
    │                              │◄─ 200 STARTED + DataAddress ─│
    │                              │                           │
    │                              │─ TransferStartMessage ──► Consumer CP
```

For async case: Data Plane returns 202, then calls back to `/transfers/{id}/dataflow/started`.

### HTTP-PUSH (Consumer Data Plane provides target endpoint)

```
Admin API         Consumer CP            Consumer DP           Provider CP          Provider DP
    │                  │                      │                     │                    │
    │─ POST /datatransfer ►│                  │                     │                    │
    │                  │─ POST /dataflows/prepare ─►│              │                    │
    │                  │◄─ 200 PREPARED + DataAddress ─│           │                    │
    │                  │─ TransferRequestMessage + DataAddress ─────►│                  │
    │                  │                      │                     │─ POST /dataflows/start ─►│
    │                  │                      │                     │◄─ 200 STARTED ────│
    │                  │                      │                     │─ TransferStartMessage ─►│
    │                  │◄─ TransferStartMessage ───────────────────│                    │
    │                  │─ POST /dataflows/{id}/started ─►│         │                    │
    │                  │                      │◄─ 200 OK ─│        │                    │
    │                  │                      │           │◄──── data push ────────────│
    │                  │◄─ callback /dataflow/completed ──│        │                    │
    │                  │─ TransferCompletionMessage ──────────────►│                    │
    │                  │                      │                     │─ POST /dataflows/{id}/completed ─►│
```

### Suspend/Resume

Both sides can initiate suspension. On `TransferSuspensionMessage` (DSP), the Control Plane calls `POST /dataflows/{id}/suspend` on its Data Plane, and simultaneously the counterparty's Control Plane does the same. Resume follows symmetrically via `DataFlowResumeMessage`.

---

## 11. Error Handling

| Scenario | Behavior |
|---|---|
| Data Plane unreachable | `DataPlaneClient` retries with exponential backoff; logs error; leaves `TransferProcess` in REQUESTED/STARTED (no state change) |
| Non-recoverable wire error | Data Plane calls `POST /transfers/{id}/dataflow/errored`; Control Plane logs, does NOT propagate to counterparty; sets internal error flag on `TransferProcess` |
| Invalid state transition | Return HTTP 400 with descriptive message on both CP and DP sides |
| Duplicate `processId` on `/dataflows/start` | HTTP 400; Data Plane MUST reject if a `DataFlow` already exists for that `processId` |
| Missing `processId` | HTTP 404 on DP if no `DataFlow` exists for that `processId` |
| Auth failure | HTTP 401/403; `DataPlaneClient`/`ControlPlaneClient` logs and does not retry on 4xx |

---

## 12. Testing Strategy

### Unit tests (`*Test.java`)

| Test class | Module | Coverage |
|---|---|---|
| `DataFlowServiceTest` | `data-plane-core` | All state machine transitions, valid and invalid |
| `DataFlowControllerTest` | `data-plane-core` | Endpoint request validation, response codes |
| `DataTransferProtocolRegistryTest` | `data-plane-core` | Protocol discovery, unknown transfer type error |
| `HttpPullTransferProtocolTest` | `data-plane-http` | Pull strategy logic with mocked S3/HTTP |
| `HttpPushTransferProtocolTest` | `data-plane-http` | Push strategy logic with mocked S3/HTTP |
| `ControlPlaneClientTest` | `data-plane-core` | HTTP call construction, auth header injection (mocked HTTP) |
| `DataPlaneClientTest` | `data-transfer` | HTTP call construction, message serialization |
| `DataFlowCallbackControllerTest` | `data-transfer` | Callback reception, state update delegation |

### Integration tests (`*IT.java`)

| Test class | Module | Coverage |
|---|---|---|
| `DataFlowIT` | `data-plane-core` | Full HTTP-PULL flow: CP sends `/dataflows/start` → DP processes → callback received |
| `DataFlowPushIT` | `data-plane-core` | Full HTTP-PUSH flow: CP sends `/dataflows/prepare` → DP prepares → CP initiates |
| `DataPlaneRegistrationIT` | `data-plane-core` | PUT registration → verify stored; DELETE → verify removed |
| `DataFlowSuspendResumeIT` | `data-plane-core` | STARTED → suspend → SUSPENDED → resume → STARTED |
| `DataFlowErrorIT` | `data-plane-core` | DP calls `/dataflow/errored` → CP records error, does not transition to TERMINATED |
| `HttpPullDataFlowIT` | `data-plane-http-pull` | Full Spring Boot app startup; verifies `HttpPullTransferProtocol` registers and handles a mock flow |
| `HttpPushDataFlowIT` | `data-plane-http-push` | Same for HTTP-PUSH |

### Existing tests
All existing `connector` integration tests must continue to pass. A compatibility shim or test profile ensures the transition period does not break current behavior.

---

## 13. Scaling

### Horizontal scaling (multiple requests in parallel)

Each `DataFlow` is fully self-contained: the protocol app stores its state in its own MongoDB collection and posts callbacks independently. This makes horizontal scaling straightforward:

```
K8s HPA detects CPU/memory pressure on data-plane-http-pull pods
  → scales from 1 to N replicas
  → each new replica calls PUT /dataplanes on startup
  → Control Plane adds it to the registry for "HttpData-PULL"
  → DataPlaneRouter round-robins new DataFlowStartMessage requests
     across all registered instances
```

No sticky sessions, no shared in-memory state between replicas. Each `DataFlow` lives on the replica that received the `/dataflows/start` request; that same replica posts the callback when done.

### Within a single instance: concurrent transfer pool

Each protocol app has its own `ThreadPoolTaskExecutor`:
- `httpPullTransferExecutor` — core 8, max 8, queue 50 (mirroring current connector config)
- `httpPushTransferExecutor` — core 8, max 8, queue 50

Pool size is configurable via `application.transfer.pull.pool-size` and `application.transfer.push.pool-size`. Each thread holds an open `HttpURLConnection` for the duration of its download+upload, so pool sizing must account for available memory (~50 MB per transfer for buffering).

**Virtual threads**: The project targets Java 21. Each Data Plane app uses `Executors.newVirtualThreadPerTaskExecutor()` instead of `ThreadPoolTaskExecutor`, removing the fixed pool ceiling entirely. Blocked I/O on a virtual thread is cheap (no OS thread parked), so thousands of concurrent transfers are practical without tuning thread pool sizes. This is a one-line change in each protocol app's executor configuration bean.

### Single large file: parallel range download (future enhancement)

A single file transfer is currently a sequential stream: one `HttpURLConnection` GET piped to S3 multipart upload. The S3 upload side already uses multipart (the SDK splits large files into parts), but the **download side is a single TCP stream** — it cannot be parallelised without range requests.

Presigned S3 GET URLs already support `Range` headers (the `Range` header is not included in `X-Amz-SignedHeaders` so it does not invalidate the signature). A future enhancement to `HttpPullTransferProtocol`:

1. `HEAD` the presigned URL to get `Content-Length`
2. Split into N ranges (e.g., 8 × `Content-Length/8`)
3. Issue N parallel `GET Range:` requests on the `transferExecutor`
4. Pipe each range to a separate S3 multipart upload part
5. Complete the multipart upload once all parts land

This is out of scope for the initial DPS implementation but fits cleanly inside `HttpPullTransferProtocol.execute()` without any protocol or interface changes.

---

## 14. Migration Strategy

The implementation proceeds in phases to avoid breaking existing functionality:

1. **Phase 1**: Create `data-plane-api` module with `DataTransferProtocol` interface and DPS message models. Create `data-plane-http-pull` and `data-plane-http-push` as standalone Spring Boot apps depending on `data-plane-core`.
2. **Phase 2**: Create `data-plane-core` (library) with all DPS Data Plane endpoints, `DataFlowService`, `DataTransferProtocolRegistry`, and startup registration. Move HTTP strategies from `data-transfer` into `data-plane-http-pull` and `data-plane-http-push`. Both apps start as standalone services.
3. **Phase 3**: Add DPS callback endpoints and `DataPlaneClient` to `data-transfer` (Control Plane side). Update `DataTransferAPIService` to call Data Plane via DPS instead of invoking strategies directly.
4. **Phase 4**: Add Registration endpoints to both sides (`PUT/DELETE /dataplanes` and `/controlplanes`). Wire up startup registration with retry.
5. **Phase 5**: Add OAuth2 + API Key auth to DPS communication. Update Docker Compose for dual-service deployment.
6. **Phase 6**: Remove old strategy invocation from Control Plane. Remove `DataTransferFormat` enum. Clean up.

---

## 15. Out of Scope

- S3, MQTT, Kafka protocol applications (each is a separate `data-plane-{protocol}` standalone app following the pattern in Section 5)
- DSP Transfer Process protocol changes (those remain in `data-transfer` unchanged)
- Changes to catalog or negotiation modules
- Data Plane health/heartbeat monitoring (liveness check beyond initial registration)
