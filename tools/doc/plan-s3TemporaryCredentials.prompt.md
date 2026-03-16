# Plan: Scoped Temporary S3 Credentials per HTTP-PUSH Transfer

The existing `BucketCredentialsEntity` / `BucketCredentialsService` / `BucketCredentialsRepository` stack is left completely untouched. A parallel, transfer-scoped set of classes is introduced: `TemporaryBucketUser` (model), `TemporaryBucketUserRepository`, and `TemporaryBucketUserService`. On every HTTP-PUSH `requestTransfer` call a temporary Minio user is created with a `GetObject`-only policy scoped to the exact object key, and its credentials are sent to the consumer. Optionally the user is deleted on `completeTransfer`.

### Steps

1. **Create `TemporaryBucketUser` model** as a new file `tools/src/main/java/it/eng/tools/s3/model/TemporaryBucketUser.java` — a `@Document(collection = "temporary_bucket_users")` class mirroring the auditing fields of [`BucketCredentialsEntity`](../src/main/java/it/eng/tools/s3/model/BucketCredentialsEntity.java):
   - `@Id private String transferProcessId` — the transfer process ID is the natural unique key.
   - `private String accessKey` — generated as `"tp-" + transferProcessId.substring(0, 8)` (consistent with the `"GetBucketUser-"` prefix pattern in [`S3BucketProvisionService`](../src/main/java/it/eng/tools/s3/service/S3BucketProvisionService.java)).
   - `@Encrypted private String secretKey` — a random UUID, encrypted at rest using the same [`@Encrypted`](../src/main/java/it/eng/tools/s3/encrypt/Encrypted.java) annotation and [`FieldEncryptionService`](../src/main/java/it/eng/tools/service/FieldEncryptionService.java) as `BucketCredentialsEntity`.
   - `private String bucketName` and `private String objectKey` — for policy scoping and cleanup.
   - Auditing fields: `@CreatedDate Instant issued`, `@LastModifiedDate Instant modified`, `@CreatedBy String createdBy`, `@LastModifiedBy String lastModifiedBy`, `@Version Long version` — identical pattern to `BucketCredentialsEntity`.
   - Inner static `Builder` class following the exact same builder pattern as `BucketCredentialsEntity.Builder`.

2. **Create `TemporaryBucketUserRepository`** as a new file `tools/src/main/java/it/eng/tools/s3/repository/TemporaryBucketUserRepository.java` — a `MongoRepository<TemporaryBucketUser, String>`, mirroring [`BucketCredentialsRepository`](../src/main/java/it/eng/tools/s3/repository/BucketCredentialsRepository.java). No extra query methods needed since the `@Id` is `transferProcessId` and Spring Data's `findById` suffices.

3. **Extend `IamUserManagementService` and `MinioUserManagementService` with delete operations** — add `void deleteUser(String accessKey)` and `void deletePolicy(String policyName)` to [`IamUserManagementService`](../src/main/java/it/eng/tools/s3/service/IamUserManagementService.java) and implement them in [`MinioUserManagementService`](../src/main/java/it/eng/tools/s3/service/MinioUserManagementService.java) using `minioAdminClient.deleteUser(accessKey)` and `minioAdminClient.removeCannedPolicy(policyName)`.

4. **Create `TemporaryBucketUserService`** as a new file `tools/src/main/java/it/eng/tools/s3/service/TemporaryBucketUserService.java` — a `@Service` injecting `IamUserManagementService`, `TemporaryBucketUserRepository`, and `FieldEncryptionService`. Three methods:
   - `TemporaryBucketUser createTemporaryUser(String transferProcessId, String bucketName, String objectKey)`: generates credentials, calls `iamUserManagementService.createUser(...)`, creates and attaches a policy named `"tp-policy-" + transferProcessId` with a private `createTemporaryUserPolicy(bucketName, objectKey)` that allows **only** `s3:GetObject` on the single resource `arn:aws:s3:::<bucketName>/<objectKey>` (no wildcards, no `PutObject/DeleteObject/ListBucket`), encrypts the secret key, saves to MongoDB, and returns the entity with the plain secret key for immediate use.
   - `TemporaryBucketUser getTemporaryUser(String transferProcessId)`: loads by ID, throws `S3ServerException` if absent, returns entity with `fieldEncryptionService.decrypt(secretKey)` — same pattern as `BucketCredentialsService.getBucketCredentials`.
   - `void deleteTemporaryUser(String transferProcessId)`: loads the entity, calls `deleteUser(accessKey)` and `deletePolicy("tp-policy-" + transferProcessId)`, then removes the MongoDB document.

5. **Wire `TemporaryBucketUserService` into `requestTransfer` (Phase 2)** in [`DataTransferAPIService`](../../data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java):
   - Add `TemporaryBucketUserService temporaryBucketUserService` as a new constructor-injected field.
   - In the `HTTP_PUSH` branch, replace the `bucketCredentialsService.getBucketCredentials(s3Properties.getBucketName())` call with `temporaryBucketUserService.createTemporaryUser(transferProcessInitialized.getId(), s3Properties.getBucketName(), transferProcessInitialized.getId())`.
   - Use the returned entity's `accessKey` and plain `secretKey` to populate the same six `EndpointProperty` entries (`BUCKET_NAME`, `REGION`, `OBJECT_KEY`, `ACCESS_KEY`, `SECRET_KEY`, `ENDPOINT_OVERRIDE`) as today.

6. **Wire `deleteTemporaryUser` into `completeTransfer` (Phase 3, optional)** in [`DataTransferAPIService`](../../data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java):
   - After `transferProcessRepository.save(transferProcessCompleted)` succeeds, call `temporaryBucketUserService.deleteTemporaryUser(transferProcessId)` wrapped in a try/catch so a cleanup failure does not fail the completion response.
   - Apply the same cleanup call in `suspendTransfer` for non-happy-path credential expiry.

### Further Considerations

1. **`IamUserManagementService` adapter for `TemporaryBucketUser`**: The existing interface takes a `BucketCredentialsEntity`. Either overload the interface with plain `(String accessKey, String secretKey)` string parameters, or have `TemporaryBucketUserService` construct a throwaway `BucketCredentialsEntity` just for the `createUser` call — pick whichever is less invasive to the existing interface contract.
2. **Policy scoping isolation**: Do **not** modify the existing `createUserPolicy` in `MinioUserManagementService` — add the new tighter `createTemporaryUserPolicy(bucketName, objectKey)` private method inside `TemporaryBucketUserService` only, keeping the two concerns fully separated.
3. **Bucket policy not touched**: The existing `updateBucketPolicy` in [`S3BucketProvisionService`](../src/main/java/it/eng/tools/s3/service/S3BucketProvisionService.java) is **not** called for temporary users — IAM user policy alone is sufficient in Minio, avoiding bucket-level policy bloat from short-lived transfers.

