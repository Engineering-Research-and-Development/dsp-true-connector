# Dataplane S3 Decoupling Impact Analysis

**Date:** 2026-06-09  
**Scope:** Phase 1 impact analysis only  
**Status:** Draft for team review

## Objective

Assess the architectural impact of making the Data Plane fully agnostic of S3 configuration at rest, with all required S3 connection and credential details provided dynamically by the Control Plane through DPS message metadata.

This analysis covers current TRUE Connector behavior in the active branch and proposes the recommended direction for refactoring. It does **not** include implementation steps.

Bootstrap `s3.accessKey` / `s3.secretKey` from `application.properties` are treated here as provisioning-only credentials: they create or reconcile persisted tenant-scoped `BucketCredentialsEntity` records, after which runtime S3 operations use the persisted credentials instead of the property values.

> **Current MinIO implementation note:** custom delegated policies for `BucketCredentialsEntity` users are not working in the tested MinIO environment. The active fallback therefore uses bootstrap `application.properties` credentials for HTTP-PUSH temp-user create/delete, while presigned URL generation still uses persisted `BucketCredentialsEntity` credentials.

## Scope classification

This is **planned DPS evolution in current TRUE Connector**, not a DSP 2025-1 wire-contract change.

- **DSP** remains unchanged on the connector-to-connector boundary.
- **DPS internal CP↔DP messaging** is the surface that changes.
- The main architectural goal is to make CP↔DP **metadata** the single source of truth for S3 details.

## Current problem

The repository already passes some S3 information from the Control Plane to the Data Plane, but the flow is incomplete and inconsistent:

- some dataplanes still rely on local `s3.*` properties
- some flows use `metadata`
- some flows still derive S3 details from `dataAddress`
- some protocol classes still assume local fallback for bucket, region, endpoint, or temporary-user creation

This means the Data Plane is not yet fully decoupled from local S3 knowledge.

## Approaches considered

### 1. Per-flow patching

Keep separate metadata contracts for HTTP-PULL, HTTP-PUSH, VIEW, gRPC, and Kafka, and patch each gap independently.

**Pros**
- lowest immediate effort
- smallest short-term code changes

**Cons**
- preserves architectural inconsistency
- keeps duplicated S3 contract logic across CP and DPs
- makes future transports harder to maintain

### 2. Canonical metadata only for `prepare`

Standardize `prepare` metadata, but leave start-time S3 information partly encoded in `dataAddress`.

**Pros**
- improves VIEW and provider-side prepare flows
- smaller refactor than a full contract unification

**Cons**
- DP remains partly S3-aware through start-time `dataAddress`
- split ownership remains confusing
- transport address and storage credentials stay mixed

### 3. Canonical metadata for both `prepare` and `start` (**recommended**)

Adopt one shared metadata contract across VIEW, HTTP-PULL, HTTP-PUSH, gRPC, and Kafka. Remove all Data Plane fallback to local `s3.*` values and reserve `dataAddress` for transport-facing endpoint information only.

**Pros**
- fully matches the target architecture
- separates transport coordinates from storage credentials
- scales cleanly to future dataplanes and storage backends
- reduces per-flow special cases

**Cons**
- broader refactor touching CP contract builders, DP protocol handlers, and tests
- requires an explicit decision about HTTP-PUSH temporary-user ownership

## Recommended target contract

Use one canonical metadata structure for all DPS flows:

```json
{
  "source": {
    "sourceType": "s3",
    "finite": true,
    "s3": {
      "bucketName": "...",
      "objectKey": "...",
      "region": "...",
      "accessKey": "...",
      "secretKey": "...",
      "endpointOverride": "..."
    }
  },
  "sink": {
    "sinkType": "s3",
    "mode": "TRANSFER|VIEW",
    "s3": {
      "bucketName": "...",
      "objectKey": "...",
      "region": "...",
      "accessKey": "...",
      "secretKey": "...",
      "endpointOverride": "..."
    }
  }
}
```

Interpretation of that contract:

- `source.s3.*` and `sink.s3.*` normally carry persisted tenant-scoped runtime credentials loaded from `BucketCredentialsEntity`
- `application.properties` `s3.*` values are not a runtime fallback for dataplanes
- bootstrap property credentials are used only by provisioning/reconciliation code that creates or repairs `BucketCredentialsEntity`

Current MinIO exception:

- HTTP-PUSH `prepare` passes bootstrap `s3.accessKey` / `s3.secretKey` to the consumer DP as temporary management credentials until delegated bucket-manager policy support works end-to-end
- VIEW and provider-side presigned URL generation still use `BucketCredentialsEntity` credentials

## Contract usage by flow

### VIEW

- use `sink.mode = VIEW`
- use `sink.s3.*` only
- Data Plane generates a presigned URL from metadata-provided tenant credentials

### HTTP-PULL provider prepare

- use `source.s3.*`
- Data Plane generates the presigned URL
- returned `dataAddress.endpoint` remains the transport-facing pull URL

### HTTP-PULL consumer start

- keep presigned URL in `dataAddress.endpoint`
- use metadata `sink.s3.*` for the consumer upload destination

### HTTP-PUSH consumer prepare

- target design: use `sink.s3.*` to carry the tenant bucket's persisted management credentials
- consumer dataplane creates and stores the temporary upload user locally during DPS `prepare`
- dataplane returns only temporary upload credentials to the Control Plane for the DSP request
- dataplane deletes the temporary user during DPS completion cleanup

Current MinIO fallback:

- consumer CP passes bootstrap `application.properties` management credentials in `sink.s3.accessKey` / `sink.s3.secretKey`
- consumer DP uses those bootstrap credentials for temp-user create/delete until bucket-scoped delegated policies are working

### HTTP-PUSH provider start

- use `source.s3.*` for provider-side read
- use `sink.s3.*` for consumer-side upload
- stop carrying S3 credentials in `dataAddress`

### gRPC and Kafka

- keep transport coordinates in `dataAddress`
- keep source and sink storage details in metadata

## Component impact

### Shared DPS contract

These files define or normalize the internal CP↔DP contract and will need review:

- `data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/DataPlaneConstants.java`
- `data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowPrepareMessage.java`
- `data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowStartMessage.java`
- `data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowPrepareMetadata.java`
- `data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowPrepareMetadataSection.java`

### Control Plane

The Control Plane is the main source of S3 metadata and will carry most of the contract assembly logic:

- `data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java`
  - currently builds `prepare` metadata
  - currently still mixes metadata and `dataAddress`
  - currently injects source/sink S3 properties differently per flow
  - must explicitly load persisted `BucketCredentialsEntity` values and pass them in canonical metadata
- `data-transfer/src/main/java/it/eng/datatransfer/client/DataPlaneClient.java`
  - transport routing remains the same
  - payload shapes and expectations change
- `data-transfer/src/main/java/it/eng/datatransfer/service/AbstractDataTransferService.java`
  - may need cleanup/lifecycle updates depending on who owns HTTP-PUSH temp-user lifecycle
- `data-transfer/src/main/java/it/eng/datatransfer/service/AutomaticDataTransferService.java`
  - review for any prepare/start assumptions
- `data-transfer/src/main/java/it/eng/datatransfer/model/TransferProcess.java`
  - may need persistence adjustments if start-time metadata or ownership hints must be retained
- `data-transfer/src/main/java/it/eng/datatransfer/rest/api/DataFlowCallbackController.java`
  - no direct contract change required, but must be reviewed because callbacks may now indirectly depend on secret-bearing prepared sessions

### Data Plane runtime

The shared DP runtime must be reviewed for how it stores and reuses start/prepare information:

- `data-plane/data-plane-core/src/main/java/it/eng/dataplane/core/controller/DataFlowController.java`
- `data-plane/data-plane-core/src/main/java/it/eng/dataplane/core/service/DataFlowService.java`
- related `DataFlow` / `DataFlowEntity` classes if metadata must be preserved across lifecycle transitions

### Protocol implementations

#### HTTP-PULL

- `data-plane/data-plane-http-pull/src/main/java/it/eng/dataplane/httppull/HttpPullTransferProtocol.java`

Current issues:
- still injects `S3Properties`
- still falls back to local bucket configuration
- uses metadata for some flows but not as a strict requirement

#### HTTP-PUSH

- `data-plane/data-plane-http-push/src/main/java/it/eng/dataplane/httppush/HttpPushTransferProtocol.java`

Current issues:
- still injects `S3Properties`
- still assumes local region/endpoint fallback
- still creates temporary users in the DP
- mixes consumer sink preparation with local S3/IAM assumptions instead of tenant-scoped management credentials from metadata

#### gRPC

- `data-plane/data-plane-grpc/src/main/java/it/eng/dataplane/grpc/GrpcStreamTransferProtocol.java`

Current state:
- already close to the target architecture
- source properties are metadata-driven
- mainly needs alignment to the canonical contract and fallback removal

#### Kafka

- `data-plane/data-plane-kafka/src/main/java/it/eng/dataplane/kafka/KafkaStreamTransferProtocol.java`

Current state:
- already close on source-side behavior
- mainly needs canonical contract alignment

### S3 abstraction layer

These shared classes are important because they determine whether explicit runtime credentials are already supported:

- `data-plane/data-plane-s3/src/main/java/it/eng/dataplane/s3/io/S3SourceReader.java`
  - already aligned with explicit CP-provided properties
- `data-plane/data-plane-s3/src/main/java/it/eng/dataplane/s3/io/S3SinkWriter.java`
  - already aligned with explicit CP-provided properties
- `s3-support/src/main/java/it/eng/tools/s3/service/S3ClientService.java`
- `s3-support/src/main/java/it/eng/tools/s3/service/S3ClientServiceImpl.java`
- `s3-support/src/main/java/it/eng/tools/s3/service/TemporaryBucketUserService.java`

Important target split:

- `S3BucketProvisionService` uses bootstrap property credentials only to create or reconcile persisted tenant credentials
- runtime S3 object access, including catalog artifact upload/download, uses `BucketCredentialsEntity`
- HTTP-PUSH temporary upload users are created from tenant management credentials, not directly from property credentials

### Property files impacted

These files currently encode local S3 assumptions in dataplane runtime config and should be reduced or re-scoped:

- `data-plane/data-plane-http-pull/src/main/resources/application.properties`
- `data-plane/data-plane-http-push/src/main/resources/application.properties`
- `data-plane/data-plane-grpc/src/main/resources/application.properties`
- `data-plane/data-plane-kafka/src/main/resources/application.properties`

Under the target architecture, these dataplane files should no longer provide operational S3 connection details for transfer execution.

## Current implementation mismatch to surface explicitly

There is a repository-level mismatch that should be called out before implementation starts:

- `DataTransferProtocol` Javadoc still says built-in HTTP-PULL and HTTP-PUSH are start-driven and do not use `prepare`
- the current branch already uses `prepare` for:
  - VIEW helper flows
  - HTTP-PULL provider presigning
  - HTTP-PUSH preparation
  - gRPC/Kafka streaming prepare

This mismatch should be cleaned up early so the documentation matches current code reality.

## Security implications

### 1. Inline credential sensitivity

Once the canonical contract is adopted, S3 credentials move fully into CP↔DP request payloads. That means:

- request bodies become sensitive material
- debug logging becomes higher risk
- persistence of prepared session state must avoid secret leakage or uncontrolled retention

### 2. TLS requirement

`X-Api-Key` authenticates the caller but does not protect payload confidentiality. If secrets are sent inline, TLS becomes effectively mandatory outside development environments.

### 3. Logging and persistence discipline

The implementation must review:

- controller logging
- error logging
- serialized audit events
- `DataFlowEntity` persistence
- callback troubleshooting output

No path should log raw access keys or secret keys.

### 4. Session cleanup

Prepared sessions become more sensitive because they can temporarily hold enough material to access storage. Cleanup must be reliable for:

- VIEW helper prepares
- HTTP-PULL helper prepares
- failed starts after successful prepare
- retries after rollback
- termination paths

### 5. Temporary user lifecycle

HTTP-PUSH ownership is resolved as follows:

- the **Data Plane** creates temporary bucket users
- the **Control Plane** passes persisted tenant management credentials in `metadata.sink.s3`
- the dataplane stores the temporary user locally and deletes it during completion cleanup
- bootstrap property credentials are never forwarded for runtime transfer execution

## Lifecycle implications

### Prepare/start separation

The design becomes cleaner if:

- `prepare` allocates storage-facing or transport-facing resources
- `start` consumes those prepared resources
- helper-only prepares are explicitly terminated after use

### Retry behavior

Retries must preserve correctness for:

- repeated VIEW requests
- provider prepare followed by failed peer notification
- repeated start calls after rollback
- terminate after already-completed helper flows

### Sticky routing

Current sticky routing logic in `DataPlaneClient` and `DataPlaneRouter` is still needed for prepared/start flows that must land on the same dataplane instance.

## Open decisions

These points should be resolved before Phase 2 implementation planning:

1. **Secret persistence policy**
   - can secrets be persisted in prepared DP records, or must they stay request-scoped only?
2. **Metadata scope on start**
   - should `DataFlowStartMessage.metadata` become mandatory for all S3-backed flows?
3. **Fallback policy**
   - current team direction is **no S3 fallback in dataplanes**
4. **Property cleanup**
   - should DP `application.properties` drop S3 keys entirely or keep them only for local-dev/testing profiles?

## Recommended next step

Proceed to Phase 2 with **approach 3** and make the first planning checkpoint the **tenant credential bootstrap/runtime split**, because it determines how `BucketCredentialsEntity`, temp-user creation, and dataplane runtime cleanup fit together without leaking property-based admin credentials into transfer execution.
