# Dataplane S3 Decoupling Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple all dataplanes from local S3 configuration at rest, move S3 knowledge entirely into DPS metadata, and make the HTTP-PUSH dataplane own temporary-user creation, storage, response, and cleanup.

**Architecture:** The Control Plane becomes the sole source of storage coordinates and tenant-scoped runtime S3 credentials. Bootstrap `s3.accessKey` / `s3.secretKey` from `application.properties` are used only to create or reconcile persisted `BucketCredentialsEntity` records, which now act as tenant-scoped management credentials even if the class name stays unchanged. Every DPS `prepare` and `start` request carries structured `source` and `sink` metadata built from those persisted credentials, while `dataAddress` is reduced to transport-facing information. The HTTP-PUSH consumer dataplane creates and stores temporary upload users during `prepare`, using `metadata.sink.s3` management credentials, returns only temporary upload credentials to the Control Plane for the DSP request, and deletes them locally when the dataplane flow reaches `COMPLETED`.

> **Current MinIO fallback:** the intended bucket-scoped delegated-management policy for `BucketCredentialsEntity` is not working in the tested MinIO environment. Until that policy model is fixed, HTTP-PUSH `prepare` passes bootstrap `s3.accessKey` / `s3.secretKey` from `application.properties` to the dataplane as temporary management credentials for temp-user create/delete. Presigned URL generation still uses persisted `BucketCredentialsEntity` credentials.

**Tech Stack:** Java 21, Spring Boot 3.x, Maven multi-module build, MongoDB, AWS SDK v2 / MinIO Admin API, OkHttp, JUnit 5, Mockito

---

## File Map

### Control Plane

- `data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java` — build canonical DPS metadata for `prepare` and `start`, stop mixing runtime S3 fields into `dataAddress`
- `data-transfer/src/test/java/it/eng/datatransfer/service/api/DataTransferAPIServiceTest.java` — CP contract coverage for HTTP-PULL, HTTP-PUSH, VIEW, and streaming

### Shared DPS contract

- `data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/DataPlaneConstants.java` — canonical metadata keys
- `data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowPrepareMessage.java` — keep metadata as the authoritative prepare contract
- `data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowStartMessage.java` — metadata survives into runtime start handling
- `data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/model/DataFlow.java` — persist structured metadata through dataplane runtime
- `data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/spi/DataTransferProtocol.java` — add a completion cleanup hook
- `data-plane/data-plane-api/src/test/java/it/eng/dataplane/api/message/DataFlowMessageSchemaContractTest.java` — schema-level contract tests

### Dataplane runtime

- `data-plane/data-plane-core/src/main/java/it/eng/dataplane/core/controller/DataFlowController.java` — capture `metadata` on start requests
- `data-plane/data-plane-core/src/main/java/it/eng/dataplane/core/model/DataFlowEntity.java` — persist structured metadata
- `data-plane/data-plane-core/src/main/java/it/eng/dataplane/core/service/DataFlowService.java` — invoke protocol completion cleanup before marking `COMPLETED`
- `data-plane/data-plane-core/src/test/java/it/eng/dataplane/core/service/DataFlowServiceTest.java` — runtime cleanup behavior

### HTTP dataplanes

- `data-plane/data-plane-http-pull/src/main/java/it/eng/dataplane/httppull/HttpPullTransferProtocol.java` — require metadata, remove local S3 fallback
- `data-plane/data-plane-http-push/src/main/java/it/eng/dataplane/httppush/HttpPushTransferProtocol.java` — create temp user from CP metadata, store it in DP, return temp credentials, clean up on completion
- `data-plane/data-plane-http-pull/src/test/java/it/eng/dataplane/httppull/HttpPullTransferProtocolTest.java`
- `data-plane/data-plane-http-push/src/test/java/it/eng/dataplane/httppush/HttpPushTransferProtocolTest.java`

### Streaming dataplanes

- `data-plane/data-plane-grpc/src/main/java/it/eng/dataplane/grpc/GrpcStreamTransferProtocol.java` — consume canonical source/sink metadata only
- `data-plane/data-plane-kafka/src/main/java/it/eng/dataplane/kafka/KafkaStreamTransferProtocol.java` — consume canonical source/sink metadata only
- `data-plane/data-plane-grpc/src/test/java/it/eng/dataplane/grpc/GrpcStreamTransferProtocolTest.java`
- `data-plane/data-plane-kafka/src/test/java/it/eng/dataplane/kafka/KafkaStreamTransferProtocolTest.java`

### S3 support

- `s3-support/src/main/java/it/eng/tools/s3/service/S3BucketProvisionService.java` — use bootstrap property credentials only to create or reconcile persisted tenant-scoped `BucketCredentialsEntity` records
- `s3-support/src/main/java/it/eng/tools/s3/service/BucketCredentialsService.java` — preserve decrypted credentials for CP metadata assembly and runtime S3 client creation
- `s3-support/src/main/java/it/eng/tools/s3/service/IamUserManagementService.java` — expose explicit reconciliation and temp-user CRUD methods that accept tenant management credentials
- `s3-support/src/main/java/it/eng/tools/s3/service/MinioUserManagementService.java` — reconcile MinIO tenant management policy and execute temp-user CRUD through tenant management credentials
- `s3-support/src/main/java/it/eng/tools/s3/service/AwsUserManagementService.java` — replace current no-op behavior with IAM reconciliation and temp-user CRUD through tenant management credentials
- `s3-support/src/main/java/it/eng/tools/s3/service/TemporaryBucketUserService.java` — create temporary upload users from tenant management credentials instead of global property credentials
- `s3-support/src/main/java/it/eng/tools/s3/configuration/S3Configuration.java` — limit bootstrap property credentials to initial admin client creation only
- `s3-support/src/main/java/it/eng/tools/s3/configuration/S3ClientProvider.java` — keep runtime S3 clients sourced from `BucketCredentialsEntity`
- `s3-support/src/test/java/it/eng/tools/s3/service/S3BucketProvisionServiceTest.java`
- `s3-support/src/test/java/it/eng/tools/s3/service/MinioUserManagementServiceTest.java`
- `s3-support/src/test/java/it/eng/tools/s3/service/AwsUserManagementServiceTest.java`
- `s3-support/src/test/java/it/eng/tools/s3/service/TemporaryBucketUserServiceTest.java`

### Runtime properties and docs

- `data-plane/data-plane-http-pull/src/main/resources/application.properties`
- `data-plane/data-plane-http-push/src/main/resources/application.properties`
- `data-plane/data-plane-grpc/src/main/resources/application.properties`
- `data-plane/data-plane-kafka/src/main/resources/application.properties`
- `doc/data-plane-signaling-technical.md`
- `doc/data-plane-signaling-user-guide.md`
- `docs/superpowers/specs/2026-06-09-dataplane-s3-decoupling-impact-analysis.md`

---

### Task 1: Reconcile `BucketCredentialsEntity` as tenant-scoped management credentials

**Files:**
- Modify: `s3-support/src/main/java/it/eng/tools/s3/service/S3BucketProvisionService.java`
- Modify: `s3-support/src/main/java/it/eng/tools/s3/service/IamUserManagementService.java`
- Modify: `s3-support/src/main/java/it/eng/tools/s3/service/MinioUserManagementService.java`
- Modify: `s3-support/src/main/java/it/eng/tools/s3/service/AwsUserManagementService.java`
- Modify: `s3-support/src/main/java/it/eng/tools/s3/service/TemporaryBucketUserService.java`
- Modify: `s3-support/src/main/java/it/eng/tools/s3/configuration/S3Configuration.java`
- Test: `s3-support/src/test/java/it/eng/tools/s3/service/S3BucketProvisionServiceTest.java`
- Test: `s3-support/src/test/java/it/eng/tools/s3/service/MinioUserManagementServiceTest.java`
- Test: `s3-support/src/test/java/it/eng/tools/s3/service/AwsUserManagementServiceTest.java`
- Test: `s3-support/src/test/java/it/eng/tools/s3/service/TemporaryBucketUserServiceTest.java`

- [ ] **Step 1: Write the failing reconciliation tests**

```java
@Test
void ensureBucketCredentials_whenCredentialsAlreadyExist_reconcilesManagementPolicy() {
    BucketCredentialsEntity existing = BucketCredentialsEntity.Builder.newInstance()
            .bucketName("tenant-bucket")
            .accessKey("tenant-manager")
            .secretKey("tenant-manager-secret")
            .build();
    when(bucketCredentialsService.bucketCredentialsExist("tenant-bucket")).thenReturn(true);
    when(bucketCredentialsService.getBucketCredentials("tenant-bucket")).thenReturn(existing);

    BucketCredentialsEntity result = s3BucketProvisionService.ensureBucketCredentials("tenant-bucket");

    assertThat(result).isSameAs(existing);
    verify(iamUserManagementService).createUser(existing);
    verify(iamUserManagementService).attachPolicyToUser(existing);
}

@Test
void createTemporaryUser_usesTenantManagementCredentialsInsteadOfBootstrapProperties() {
    BucketCredentialsEntity manager = BucketCredentialsEntity.Builder.newInstance()
            .bucketName("tenant-bucket")
            .accessKey("tenant-manager")
            .secretKey("tenant-manager-secret")
            .build();

    temporaryBucketUserService.createTemporaryUser("tp-1", manager, "tenant-bucket", "tp-1");

    verify(iamUserManagementService).createUser(argThat(tempUser ->
            tempUser.getAccessKey().startsWith("TempUser-")));
    verify(iamUserManagementService).attachTemporaryPolicy(eq(manager), anyString(), anyString(),
            contains("s3:PutObject"));
}

@Test
void attachPolicyToUser_minioPolicyIncludesBucketAccessAndTemporaryUserCrudPermissions() {
    service.attachPolicyToUser(creds("tenant-bucket"));

    verify(minioAdminClient).addCannedPolicy(eq("policy-tenant-bucket"),
            argThat(policy -> policy.contains("s3:GetObject")
                    && policy.contains("s3:PutObject")
                    && policy.contains("admin:CreateUser")
                    && policy.contains("admin:DeleteUser")));
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```bash
mvn -pl s3-support -am -Dtest=S3BucketProvisionServiceTest,MinioUserManagementServiceTest,AwsUserManagementServiceTest,TemporaryBucketUserServiceTest test
```

Expected: `BUILD FAILURE` because existing bucket credentials are returned without reconciliation, temp-user creation still uses the old global-admin path, and AWS IAM management is still a no-op.

- [ ] **Step 3: Reconcile existing credentials and implement IAM-capable policy attachment**

```java
public BucketCredentialsEntity ensureBucketCredentials(String bucketName) {
    validateBucketName(bucketName);
    if (bucketCredentialsService.bucketCredentialsExist(bucketName)) {
        BucketCredentialsEntity existing = bucketCredentialsService.getBucketCredentials(bucketName);
        iamUserManagementService.createUser(existing);
        iamUserManagementService.attachPolicyToUser(existing);
        updateBucketPolicy(bucketName, existing.getAccessKey());
        s3ClientProvider.clearBucketCache(bucketName);
        return existing;
    }
    if (bucketExists(bucketName)) {
        return createBucketCredentials(bucketName);
    }
    return createSecureBucket(bucketName);
}
```

```java
public interface IamUserManagementService {

    void createUser(BucketCredentialsEntity bucketCredentials);

    void attachPolicyToUser(BucketCredentialsEntity bucketCredentials);

    void attachTemporaryPolicy(BucketCredentialsEntity managementCredentials, String accessKey, String policyName,
                               String policyJson);

    void deleteUser(BucketCredentialsEntity managementCredentials, String accessKey);

    void deletePolicy(BucketCredentialsEntity managementCredentials, String policyName);
}
```

```java
@Override
public void attachPolicyToUser(BucketCredentialsEntity bucketCredentials) {
    String policyName = "policy-" + bucketCredentials.getBucketName();
    String policyJson = createTenantManagerPolicy(bucketCredentials.getBucketName());
    minioAdminClient.addCannedPolicy(policyName, policyJson);
    minioAdminClient.setPolicy(bucketCredentials.getAccessKey(), false, policyName);
}
```

```java
@Override
public void attachPolicyToUser(BucketCredentialsEntity bucketCredentials) {
    String userName = bucketCredentials.getAccessKey();
    String policyArn = ensureTenantManagementPolicy(bucketCredentials.getBucketName());
    iamClient.attachUserPolicy(builder -> builder.userName(userName).policyArn(policyArn));
}
```

```java
public TemporaryBucketUser createTemporaryUser(String transferProcessId,
                                               BucketCredentialsEntity managementCredentials,
                                               String bucketName,
                                               String objectKey) {
    TemporaryBucketUser temporaryUser = buildTemporaryUser(transferProcessId, bucketName, objectKey);
    iamUserManagementService.createUser(toBucketCredentials(temporaryUser));
    iamUserManagementService.attachTemporaryPolicy(managementCredentials,
            temporaryUser.getAccessKey(),
            buildPolicyName(transferProcessId),
            createTemporaryPolicy(bucketName, objectKey));
    return repository.save(temporaryUser);
}
```

> **Implementation note (current branch):** keep the interface shape above, but for MinIO use bootstrap `application.properties` credentials as the effective management credentials for HTTP-PUSH temp-user create/delete until a working delegated `BucketCredentialsEntity` policy is available. Keep presigned URL generation on `BucketCredentialsEntity` credentials.

```java
@Bean
public MinioAdminClient minioAdminClient() {
    return MinioAdminClient.builder()
            .endpoint(s3Properties.getEndpoint())
            .credentials(s3Properties.getAccessKey(), s3Properties.getSecretKey())
            .httpClient(okHttpClient)
            .build();
}
```

- [ ] **Step 4: Re-run the tests**

Run:

```bash
mvn -pl s3-support -am -Dtest=S3BucketProvisionServiceTest,MinioUserManagementServiceTest,AwsUserManagementServiceTest,TemporaryBucketUserServiceTest test
```

Expected: `BUILD SUCCESS` and the reconciliation tests pass.

- [ ] **Step 5: Commit**

```bash
git add \
  s3-support/src/main/java/it/eng/tools/s3/service/S3BucketProvisionService.java \
  s3-support/src/main/java/it/eng/tools/s3/service/IamUserManagementService.java \
  s3-support/src/main/java/it/eng/tools/s3/service/MinioUserManagementService.java \
  s3-support/src/main/java/it/eng/tools/s3/service/AwsUserManagementService.java \
  s3-support/src/main/java/it/eng/tools/s3/service/TemporaryBucketUserService.java \
  s3-support/src/main/java/it/eng/tools/s3/configuration/S3Configuration.java \
  s3-support/src/test/java/it/eng/tools/s3/service/S3BucketProvisionServiceTest.java \
  s3-support/src/test/java/it/eng/tools/s3/service/MinioUserManagementServiceTest.java \
  s3-support/src/test/java/it/eng/tools/s3/service/AwsUserManagementServiceTest.java \
  s3-support/src/test/java/it/eng/tools/s3/service/TemporaryBucketUserServiceTest.java
git commit -m "feat(s3-support): use tenant management credentials for temp-user flow"
```

---

### Task 2: Make the Control Plane build one canonical DPS metadata contract

**Files:**
- Modify: `data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/DataPlaneConstants.java`
- Modify: `data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java`
- Test: `data-transfer/src/test/java/it/eng/datatransfer/service/api/DataTransferAPIServiceTest.java`
- Test: `data-plane/data-plane-api/src/test/java/it/eng/dataplane/api/message/DataFlowMessageSchemaContractTest.java`

- [ ] **Step 1: Write the failing CP contract tests**

```java
@Test
void requestTransfer_httpPushPrepare_usesCanonicalSinkMetadataWithTenantManagementCredentials() {
    TransferProcess transferProcess = providerTransfer("tp-http-push", "tenant-a", DataTransferFormat.HTTP_PUSH.format());
    BucketCredentialsEntity bucketCredentials = BucketCredentialsEntity.Builder.newInstance()
            .bucketName("tenant-a-bucket")
            .accessKey("tenant-manager")
            .secretKey("tenant-manager-secret")
            .build();
    when(bucketCredentialsService.getBucketCredentials("tenant-a-bucket")).thenReturn(bucketCredentials);

    Map<String, Object> metadata = service.buildHttpPushPrepareMetadata("tenant-a", "tp-http-push");

    assertThat(metadata)
            .extractingByKey(DataPlaneConstants.METADATA_SECTION_SINK)
            .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
            .containsKey(DataPlaneConstants.METADATA_SECTION_S3);
}

@Test
void startMessageSchema_allowsCanonicalMetadataAlongsideTransportDataAddress() throws Exception {
    DataFlowStartMessage message = DataFlowStartMessage.Builder.newInstance()
            .processId("tp-1")
            .transferType("HttpData-PUSH")
            .metadata(Map.of(
                    DataPlaneConstants.METADATA_SECTION_SOURCE, Map.of(
                            DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                    DataPlaneConstants.METADATA_S3_BUCKET_NAME, "provider-bucket",
                                    DataPlaneConstants.METADATA_S3_ACCESS_KEY, "provider-access",
                                    DataPlaneConstants.METADATA_S3_SECRET_KEY, "provider-secret",
                                    DataPlaneConstants.METADATA_S3_REGION, "us-east-1")),
                    DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                            DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                    DataPlaneConstants.METADATA_S3_BUCKET_NAME, "consumer-bucket",
                                    DataPlaneConstants.METADATA_S3_ACCESS_KEY, "temp-access",
                                    DataPlaneConstants.METADATA_S3_SECRET_KEY, "temp-secret",
                                    DataPlaneConstants.METADATA_S3_REGION, "us-east-1"))))
            .build();

    assertThat(MAPPER.valueToTree(message).get("metadata")).isNotNull();
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```bash
mvn -pl data-transfer,data-plane/data-plane-api -am -Dtest=DataTransferAPIServiceTest,DataFlowMessageSchemaContractTest test
```

Expected: `BUILD FAILURE` because HTTP-PUSH still assembles start-time S3 fields through `dataAddress`, and CP metadata helpers are flow-specific instead of canonical.

- [ ] **Step 3: Replace per-flow helpers with canonical source/sink metadata builders**

```java
private Map<String, Object> buildCanonicalMetadata(String sourceTenantId,
                                                   String sourceObjectKey,
                                                   String sinkTenantId,
                                                   String sinkObjectKey,
                                                   DataAddress sourceHints,
                                                   String mode) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    if (sourceTenantId != null && sourceObjectKey != null) {
        Map<String, Object> sourceSection = new LinkedHashMap<>(toStructuredSourceMetadata(sourceHints));
        sourceSection.put(DataPlaneConstants.METADATA_SECTION_S3,
                buildControlPlaneS3Metadata(sourceTenantId, sourceObjectKey));
        metadata.put(DataPlaneConstants.METADATA_SECTION_SOURCE, Map.copyOf(sourceSection));
    }
    if (sinkTenantId != null && sinkObjectKey != null) {
        Map<String, Object> sinkSection = new LinkedHashMap<>();
        if (mode != null) {
            sinkSection.put(DataPlaneConstants.METADATA_FIELD_MODE, mode);
        }
        sinkSection.put(DataPlaneConstants.METADATA_SECTION_S3,
                buildControlPlaneS3Metadata(sinkTenantId, sinkObjectKey));
        metadata.put(DataPlaneConstants.METADATA_SECTION_SINK, Map.copyOf(sinkSection));
    }
    return Map.copyOf(metadata);
}
```

```java
private Map<String, Object> buildHttpPushPrepareMetadata(String tenantId, String objectKey) {
    return buildCanonicalMetadata(null, null, tenantId, objectKey, null, null);
}

private Map<String, Object> buildViewPrepareMetadata(String tenantId, String objectKey) {
    return buildCanonicalMetadata(null, null, tenantId, objectKey, null, "VIEW");
}
```

```java
private DataFlowStartMessage.Builder applyCommonDataPlaneFields(DataFlowStartMessage.Builder builder,
                                                                TransferProcess transferProcess,
                                                                String transferType,
                                                                Map<String, Object> metadata) {
    return builder.messageId(UUID.randomUUID().toString())
            .participantId(resolveLocalParticipantId(transferProcess))
            .counterPartyId(resolveRemoteParticipantId(transferProcess))
            .dataspaceContext(DataPlaneConstants.DSPACE_2025_01_CONTEXT)
            .claims(Map.of())
            .transferType(transferType)
            .metadata(metadata);
}
```

```java
private Map<String, Object> buildControlPlaneS3Metadata(String tenantId, String objectKey) {
    String bucketName = tenantBucketResolver.resolveBucketName(tenantId);
    BucketCredentialsEntity credentials = bucketCredentialsService.getBucketCredentials(bucketName);
    return Map.of(
            DataPlaneConstants.METADATA_S3_BUCKET_NAME, bucketName,
            DataPlaneConstants.METADATA_S3_OBJECT_KEY, objectKey,
            DataPlaneConstants.METADATA_S3_REGION, s3Properties.getRegion(),
            DataPlaneConstants.METADATA_S3_ACCESS_KEY, credentials.getAccessKey(),
            DataPlaneConstants.METADATA_S3_SECRET_KEY, credentials.getSecretKey(),
            DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE, resolveEndpointOverride());
}
```

- [ ] **Step 4: Re-run the tests**

Run:

```bash
mvn -pl data-transfer,data-plane/data-plane-api -am -Dtest=DataTransferAPIServiceTest,DataFlowMessageSchemaContractTest test
```

Expected: `BUILD SUCCESS` and the CP now emits one canonical metadata structure for all DPS flows.

- [ ] **Step 5: Commit**

```bash
git add \
  data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/DataPlaneConstants.java \
  data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java \
  data-transfer/src/test/java/it/eng/datatransfer/service/api/DataTransferAPIServiceTest.java \
  data-plane/data-plane-api/src/test/java/it/eng/dataplane/api/message/DataFlowMessageSchemaContractTest.java
git commit -m "feat(dataplane): canonicalize CP metadata contract"
```

---

### Task 3: Persist start metadata in the dataplane runtime and add a completion cleanup hook

**Files:**
- Modify: `data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/model/DataFlow.java`
- Modify: `data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/spi/DataTransferProtocol.java`
- Modify: `data-plane/data-plane-core/src/main/java/it/eng/dataplane/core/model/DataFlowEntity.java`
- Modify: `data-plane/data-plane-core/src/main/java/it/eng/dataplane/core/controller/DataFlowController.java`
- Modify: `data-plane/data-plane-core/src/main/java/it/eng/dataplane/core/service/DataFlowService.java`
- Test: `data-plane/data-plane-core/src/test/java/it/eng/dataplane/core/service/DataFlowServiceTest.java`

- [ ] **Step 1: Write the failing runtime tests**

```java
@Test
void start_persistsMetadataFromStartMessage() {
    Map<String, Object> metadata = Map.of(
            DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                    DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                            DataPlaneConstants.METADATA_S3_BUCKET_NAME, "consumer-bucket")));
    DataFlow dataFlow = DataFlow.Builder.newInstance()
            .processId("tp-runtime")
            .transferType("HttpData-PUSH")
            .metadata(metadata)
            .build();

    service.start(dataFlow);

    verify(repository).save(argThat(entity -> entity.getMetadata().equals(metadata)));
}

@Test
void handleCompletion_invokesProtocolCleanupBeforeCompletedState() {
    when(protocol.completeTransfer("tp-runtime")).thenReturn(CompletableFuture.completedFuture(DataFlowResult.success()));

    service.handleCompletion("tp-runtime", DataFlowResult.success());

    verify(protocol).completeTransfer("tp-runtime");
    verify(repository).save(argThat(entity -> entity.getState() == DataFlowState.COMPLETED));
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```bash
mvn -pl data-plane/data-plane-core -am -Dtest=DataFlowServiceTest test
```

Expected: `BUILD FAILURE` because `DataFlow` and `DataFlowEntity` do not persist metadata and the protocol SPI has no completion hook.

- [ ] **Step 3: Add `metadata` to the runtime model and invoke cleanup on completion**

```java
public class DataFlow {
    private Map<String, String> dataAddress;
    private Map<String, Object> metadata;

    public static class Builder {
        public Builder metadata(Map<String, Object> metadata) {
            instance.metadata = metadata;
            return this;
        }
    }
}
```

```java
public interface DataTransferProtocol {

    default CompletableFuture<DataFlowResult> completeTransfer(String processId) {
        return CompletableFuture.completedFuture(DataFlowResult.success());
    }
}
```

```java
private void handleCompletion(String processId, DataFlowResult result) {
    if (!result.isSuccess()) {
        handleError(processId, new RuntimeException(result.getErrorMessage()));
        return;
    }
    DataFlowEntity fresh = findRequired(processId);
    requiredProtocol(fresh.getTransferType()).completeTransfer(processId)
            .thenAccept(cleanupResult -> {
                if (!cleanupResult.isSuccess()) {
                    handleError(processId, new RuntimeException(cleanupResult.getErrorMessage()));
                    return;
                }
                stateMachine.assertTransition(fresh.getState(), DataFlowState.COMPLETED);
                repository.save(fresh.withState(DataFlowState.COMPLETED));
            })
            .exceptionally(ex -> {
                handleError(processId, ex);
                return null;
            });
}
```

- [ ] **Step 4: Re-run the tests**

Run:

```bash
mvn -pl data-plane/data-plane-core -am -Dtest=DataFlowServiceTest test
```

Expected: `BUILD SUCCESS` and metadata plus completion cleanup are both persisted correctly.

- [ ] **Step 5: Commit**

```bash
git add \
  data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/model/DataFlow.java \
  data-plane/data-plane-api/src/main/java/it/eng/dataplane/api/spi/DataTransferProtocol.java \
  data-plane/data-plane-core/src/main/java/it/eng/dataplane/core/model/DataFlowEntity.java \
  data-plane/data-plane-core/src/main/java/it/eng/dataplane/core/controller/DataFlowController.java \
  data-plane/data-plane-core/src/main/java/it/eng/dataplane/core/service/DataFlowService.java \
  data-plane/data-plane-core/src/test/java/it/eng/dataplane/core/service/DataFlowServiceTest.java
git commit -m "feat(dataplane-core): persist metadata and clean up on completion"
```

---

### Task 4: Refactor HTTP and streaming dataplanes to consume metadata only

**Files:**
- Modify: `data-plane/data-plane-http-pull/src/main/java/it/eng/dataplane/httppull/HttpPullTransferProtocol.java`
- Modify: `data-plane/data-plane-http-push/src/main/java/it/eng/dataplane/httppush/HttpPushTransferProtocol.java`
- Modify: `data-plane/data-plane-grpc/src/main/java/it/eng/dataplane/grpc/GrpcStreamTransferProtocol.java`
- Modify: `data-plane/data-plane-kafka/src/main/java/it/eng/dataplane/kafka/KafkaStreamTransferProtocol.java`
- Modify: `data-plane/data-plane-http-pull/src/main/resources/application.properties`
- Modify: `data-plane/data-plane-http-push/src/main/resources/application.properties`
- Modify: `data-plane/data-plane-grpc/src/main/resources/application.properties`
- Modify: `data-plane/data-plane-kafka/src/main/resources/application.properties`
- Test: `data-plane/data-plane-http-pull/src/test/java/it/eng/dataplane/httppull/HttpPullTransferProtocolTest.java`
- Test: `data-plane/data-plane-http-push/src/test/java/it/eng/dataplane/httppush/HttpPushTransferProtocolTest.java`
- Test: `data-plane/data-plane-grpc/src/test/java/it/eng/dataplane/grpc/GrpcStreamTransferProtocolTest.java`
- Test: `data-plane/data-plane-kafka/src/test/java/it/eng/dataplane/kafka/KafkaStreamTransferProtocolTest.java`

- [ ] **Step 1: Write the failing protocol tests**

```java
@Test
void prepare_httpPush_createsTemporaryUserFromSinkMetadataAndReturnsOnlyTempCredentials() {
    BucketCredentialsEntity managementCredentials = BucketCredentialsEntity.Builder.newInstance()
            .bucketName("consumer-bucket")
            .accessKey("tenant-manager")
            .secretKey("tenant-manager-secret")
            .build();
    TemporaryBucketUser tempUser = TemporaryBucketUser.Builder.newInstance()
            .transferProcessId("tp-push")
            .bucketName("consumer-bucket")
            .objectKey("tp-push")
            .accessKey("temp-access")
            .secretKey("temp-secret")
            .build();
    when(temporaryBucketUserService.createTemporaryUser("tp-push", managementCredentials, "consumer-bucket", "tp-push"))
            .thenReturn(tempUser);

    DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
            .processId("tp-push")
            .transferType("HttpData-PUSH")
            .metadata(Map.of(
                    DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                            DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                    DataPlaneConstants.METADATA_S3_BUCKET_NAME, "consumer-bucket",
                                    DataPlaneConstants.METADATA_S3_ACCESS_KEY, "tenant-manager",
                                    DataPlaneConstants.METADATA_S3_SECRET_KEY, "tenant-manager-secret",
                                    DataPlaneConstants.METADATA_S3_REGION, "us-east-1"))))
            .build();

    DataFlowPrepareResponse response = protocol.prepare(message);

    assertThat(response.getDataAddress())
            .containsEntry(S3Utils.ACCESS_KEY, "temp-access")
            .containsEntry(S3Utils.SECRET_KEY, "temp-secret")
            .doesNotContainEntry(S3Utils.ACCESS_KEY, "tenant-manager");
}
```

```java
private DataFlowPrepareResponse prepareHttpPush(DataFlowPrepareMessage message) {
    BucketCredentialsEntity managementCredentials = metadataCredentials(message, DataPlaneConstants.METADATA_SECTION_SINK);
    TemporaryBucketUser tempUser = temporaryBucketUserService.createTemporaryUser(
            message.getProcessId(),
            managementCredentials,
            managementCredentials.getBucketName(),
            message.getProcessId());
    return DataFlowPrepareResponse.Builder.newInstance()
            .dataAddress(Map.of(
                    S3Utils.BUCKET_NAME, tempUser.getBucketName(),
                    S3Utils.OBJECT_KEY, tempUser.getObjectKey(),
                    S3Utils.ACCESS_KEY, tempUser.getAccessKey(),
                    S3Utils.SECRET_KEY, tempUser.getSecretKey(),
                    S3Utils.REGION, metadataRegion(message, DataPlaneConstants.METADATA_SECTION_SINK),
                    S3Utils.ENDPOINT_OVERRIDE, metadataEndpoint(message, DataPlaneConstants.METADATA_SECTION_SINK)))
            .build();
}

@Test
void completeTransfer_httpPush_deletesTemporaryUser() throws Exception {
    DataFlowResult result = protocol.completeTransfer("tp-push").get();

    assertThat(result.isSuccess()).isTrue();
    verify(temporaryBucketUserService).deleteTemporaryUser("tp-push");
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```bash
mvn -pl data-plane/data-plane-http-pull,data-plane/data-plane-http-push,data-plane/data-plane-grpc,data-plane/data-plane-kafka -am -Dtest=HttpPullTransferProtocolTest,HttpPushTransferProtocolTest,GrpcStreamTransferProtocolTest,KafkaStreamTransferProtocolTest test
```

Expected: `BUILD FAILURE` because HTTP-PULL and HTTP-PUSH still depend on local `S3Properties` fallback and HTTP-PUSH cleanup still runs only on terminate.

- [ ] **Step 3: Make every protocol read S3 configuration from metadata and move HTTP-PUSH cleanup to completion**

```java
@Override
public DataFlowPrepareResponse prepare(DataFlowPrepareMessage message) {
    DataFlowPrepareMetadata metadata = DataFlowPrepareMetadata.from(message);
    DataFlowPrepareMetadataSection sinkS3 = metadata.getSinkSection()
            .getSection(DataPlaneConstants.METADATA_SECTION_S3);
    Map<String, String> managementProperties = sinkS3.toScalarMap();
    String bucketName = requireProperty(managementProperties, DataPlaneConstants.METADATA_S3_BUCKET_NAME);
    BucketCredentialsEntity managementCredentials = toBucketCredentials(managementProperties);
    TemporaryBucketUser tempUser = temporaryBucketUserService.createTemporaryUser(
            message.getProcessId(), managementCredentials, bucketName, message.getProcessId());
    return DataFlowPrepareResponse.Builder.newInstance()
            .processId(message.getProcessId())
            .dataAddress(Map.of(
                    S3Utils.BUCKET_NAME, bucketName,
                    S3Utils.OBJECT_KEY, message.getProcessId(),
                    S3Utils.ACCESS_KEY, tempUser.getAccessKey(),
                    S3Utils.SECRET_KEY, tempUser.getSecretKey(),
                    S3Utils.REGION, requireProperty(managementProperties, DataPlaneConstants.METADATA_S3_REGION),
                    S3Utils.ENDPOINT_OVERRIDE, requireProperty(managementProperties, DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE)))
            .build();
}

@Override
public CompletableFuture<DataFlowResult> completeTransfer(String processId) {
    temporaryBucketUserService.deleteTemporaryUser(processId);
    return CompletableFuture.completedFuture(DataFlowResult.success());
}
```

```java
private Map<String, String> buildSourceS3Properties(DataFlowPrepareMetadataSection s3Section) {
    return Map.of(
            S3Utils.BUCKET_NAME, requireProperty(s3Section, DataPlaneConstants.METADATA_S3_BUCKET_NAME),
            S3Utils.OBJECT_KEY, requireProperty(s3Section, DataPlaneConstants.METADATA_S3_OBJECT_KEY),
            S3Utils.ACCESS_KEY, requireProperty(s3Section, DataPlaneConstants.METADATA_S3_ACCESS_KEY),
            S3Utils.SECRET_KEY, requireProperty(s3Section, DataPlaneConstants.METADATA_S3_SECRET_KEY),
            S3Utils.REGION, requireProperty(s3Section, DataPlaneConstants.METADATA_S3_REGION),
            S3Utils.ENDPOINT_OVERRIDE, requireProperty(s3Section, DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));
}
```

```properties
server.port=9091
spring.application.name=data-plane-http-push
spring.data.mongodb.uri=mongodb://localhost:27017/data-plane-push
dataplane.id=${DATAPLANE_ID:dp-http-push-default}
dataplane.endpoint=http://localhost:9091
dataplane.control-plane-admin-endpoint=http://localhost:8090
dataplane.auth-type=API_KEY
dataplane.api-key=changeme
dataplane.control-plane-admin-secret=${DATAPLANE_CONTROL_PLANE_ADMIN_SECRET:internal-service-secret-change-in-prod}
s3.upload-mode=SYNC
application.encryption.key=${APPLICATION_ENCRYPTION_KEY:5m7mlhmu65zsp6x}
server.ssl.enabled=false
```

- [ ] **Step 4: Re-run the tests**

Run:

```bash
mvn -pl data-plane/data-plane-http-pull,data-plane/data-plane-http-push,data-plane/data-plane-grpc,data-plane/data-plane-kafka -am -Dtest=HttpPullTransferProtocolTest,HttpPushTransferProtocolTest,GrpcStreamTransferProtocolTest,KafkaStreamTransferProtocolTest test
```

Expected: `BUILD SUCCESS` and no protocol still depends on local `s3.endpoint`, `s3.accessKey`, `s3.secretKey`, `s3.region`, or `s3.bucketName` for runtime transfer execution. Bootstrap properties remain only for tenant credential provisioning and reconciliation.

- [ ] **Step 5: Commit**

```bash
git add \
  data-plane/data-plane-http-pull/src/main/java/it/eng/dataplane/httppull/HttpPullTransferProtocol.java \
  data-plane/data-plane-http-push/src/main/java/it/eng/dataplane/httppush/HttpPushTransferProtocol.java \
  data-plane/data-plane-grpc/src/main/java/it/eng/dataplane/grpc/GrpcStreamTransferProtocol.java \
  data-plane/data-plane-kafka/src/main/java/it/eng/dataplane/kafka/KafkaStreamTransferProtocol.java \
  data-plane/data-plane-http-pull/src/main/resources/application.properties \
  data-plane/data-plane-http-push/src/main/resources/application.properties \
  data-plane/data-plane-grpc/src/main/resources/application.properties \
  data-plane/data-plane-kafka/src/main/resources/application.properties \
  data-plane/data-plane-http-pull/src/test/java/it/eng/dataplane/httppull/HttpPullTransferProtocolTest.java \
  data-plane/data-plane-http-push/src/test/java/it/eng/dataplane/httppush/HttpPushTransferProtocolTest.java \
  data-plane/data-plane-grpc/src/test/java/it/eng/dataplane/grpc/GrpcStreamTransferProtocolTest.java \
  data-plane/data-plane-kafka/src/test/java/it/eng/dataplane/kafka/KafkaStreamTransferProtocolTest.java
git commit -m "feat(dataplanes): consume S3 metadata at runtime only"
```

---

### Task 5: Resync dataplane documentation, recreate diagrams, and run full verification

**Files:**
- Modify: `doc/data-plane-signaling-technical.md`
- Modify: `doc/data-plane-signaling-user-guide.md`
- Modify: `docs/superpowers/specs/2026-06-09-dataplane-s3-decoupling-impact-analysis.md`

- [ ] **Step 1: Update the technical reference to match the implemented ownership model**

```md
### HTTP-PUSH temporary user ownership

- The consumer-side HTTP-PUSH dataplane creates the temporary S3 user during DPS `prepare`.
- The Control Plane passes persisted tenant management credentials from `BucketCredentialsEntity` in `metadata.sink.s3`.
- The dataplane stores the temporary user locally and returns only temporary upload credentials in the prepare response.
- The dataplane deletes the temporary user during DPS completion cleanup before the local `DataFlow` is marked `COMPLETED`.

### Credential source-of-truth

- `application.properties` `s3.accessKey` / `s3.secretKey` are bootstrap-only credentials.
- `S3BucketProvisionService` uses those bootstrap credentials to create or reconcile one persisted `BucketCredentialsEntity` per tenant bucket.
- After provisioning, runtime S3 clients for artifact upload/download and temp-user creation use persisted `BucketCredentialsEntity` values, not bootstrap property credentials.
- `BucketCredentialsEntity` remains the current class name in code, but the implementation treats it as tenant-scoped management credentials.
```

- [ ] **Step 2: Update the user guide so it no longer contradicts the technical reference**

```md
### HTTP-PUSH operator flow

1. Consumer Control Plane sends DPS `prepare` to the consumer HTTP-PUSH dataplane.
2. The Control Plane loads the tenant bucket's persisted `BucketCredentialsEntity` and passes it in `metadata.sink.s3`.
3. The dataplane creates and stores a temporary upload user using those tenant management credentials.
4. The dataplane returns only temporary upload credentials to the Control Plane.
5. The Control Plane forwards only temporary upload credentials in the DSP `TransferRequestMessage`.
6. After the transfer finishes, the dataplane deletes the temporary user during completion cleanup.
```

- [ ] **Step 3: Recreate the diagrams in both documents to match the new runtime flow**

```text
Consumer CP                  Consumer HTTP-PUSH DP            Provider CP / DP
     |                               |                              |
     |-- DPS prepare --------------->|                              |
     |   metadata.sink.s3 = tenant management credentials          |
     |                               |-- create temp user --------->|
     |                               |<- temp upload credentials ---|
     |<-- prepare response ----------|                              |
     |    dataAddress = temp upload credentials                     |
     |---- DSP TransferRequest ------------------------------------>|
     |                         ... transfer execution ...            |
     |<-- DPS completed callback ----|                              |
     |                               |-- delete temp user --------->|
```

Expected document changes:
- replace outdated HTTP-PUSH and VIEW diagrams in `doc/data-plane-signaling-technical.md`
- replace outdated operator-flow diagrams in `doc/data-plane-signaling-user-guide.md`
- ensure the same prepare/start/completed ownership is shown in both files

- [ ] **Step 4: Update the Phase 1 impact analysis with the resolved HTTP-PUSH decision**

```md
## Resolved Phase 2 ownership decision

- HTTP-PUSH temporary upload users are created by the consumer dataplane.
- The dataplane stores and deletes those users locally.
- The Control Plane passes management-capable bucket credentials from `BucketCredentialsEntity`.
- Bucket credential reconciliation is part of the implementation plan so stored credentials can perform temporary-user CRUD.
```

- [ ] **Step 5: Review the two DPS docs side-by-side after edits**

Run:

```bash
git --no-pager diff -- doc/data-plane-signaling-technical.md doc/data-plane-signaling-user-guide.md
```

Expected:
- both files describe the same HTTP-PUSH prepare/start/completed ownership model
- both files describe dataplanes as metadata-driven for S3 runtime details
- both files contain regenerated diagrams reflecting the implemented flow

- [ ] **Step 6: Run focused regression tests**

Run:

```bash
mvn -pl s3-support,data-transfer,data-plane/data-plane-api,data-plane/data-plane-core,data-plane/data-plane-http-pull,data-plane/data-plane-http-push,data-plane/data-plane-grpc,data-plane/data-plane-kafka -am test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Run full repository verification**

Run:

```bash
mvn clean verify
```

Expected: `BUILD SUCCESS` with Checkstyle, unit tests, integration tests, and JaCoCo passing. If Docker is unavailable, stop here and note that `mvn clean verify` remains the final gate to run in a Docker-enabled environment.

- [ ] **Step 8: Commit**

```bash
git add \
  doc/data-plane-signaling-technical.md \
  doc/data-plane-signaling-user-guide.md \
  docs/superpowers/specs/2026-06-09-dataplane-s3-decoupling-impact-analysis.md
git commit -m "docs(dataplane): resync signaling docs with S3 decoupling flow"
```

---

## Self-review

### Spec coverage

- canonical DPS metadata for `prepare` and `start` — covered in Tasks 2, 3, and 4
- VIEW mode using CP-provided bucket access details — covered in Tasks 2 and 4
- HTTP-PUSH consumer dataplane creating and storing temp users — covered in Task 4
- CP passing persisted tenant management credentials from `BucketCredentialsEntity` — covered in Tasks 1 and 2
- bootstrap `application.properties` credentials being used only for initial tenant credential provisioning — covered in Task 1
- updating existing `BucketCredentialsEntity` users if permissions are insufficient — covered in Task 1
- dataplane-owned cleanup on completion — covered in Tasks 3 and 4
- property-file cleanup and removal of local fallback — covered in Task 4
- docs and verification — covered in Task 5

### Placeholder scan

- No `TODO`, `TBD`, or “similar to previous task” references remain.
- Every task lists exact files, commands, and concrete code snippets.

### Type consistency

- `metadata` is added consistently to `DataFlowStartMessage`, `DataFlow`, and `DataFlowEntity`.
- cleanup is consistently modeled as `completeTransfer(String processId)` on `DataTransferProtocol`.
- the Control Plane keeps passing canonical `source` / `sink` metadata built from persisted `BucketCredentialsEntity` values, while prepare responses still return temporary credentials in `dataAddress`.
