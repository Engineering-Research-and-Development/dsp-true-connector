---
applyTo: "**"
---

# S3 Architecture — Reference for Copilot Implementations

> **Critical compatibility requirement**: every S3 feature MUST work with **both MinIO and real AWS S3**.
> The code detects the target by checking whether `s3.endpoint` is blank or contains `.amazonaws.com`/`.aws.`.
> MinIO uses path-style URLs and IAM-compatible user management via its Admin API.
> AWS S3 uses virtual-hosted-style URLs and real IAM. Never add code that works for only one of them.

---

## 1. Configuration Properties (`S3Properties`)

Class: `tools/src/main/java/it/eng/tools/s3/properties/S3Properties.java`  
Spring prefix: `s3`

| Property | Key | Description |
|---|---|---|
| `endpoint` | `s3.endpoint` | Base URL of the S3 server. **Blank or AWS URL = AWS mode; non-blank local URL = MinIO mode.** |
| `accessKey` | `s3.accessKey` | Admin access key (`minioadmin` for MinIO, IAM key for AWS). |
| `secretKey` | `s3.secretKey` | Admin secret key. |
| `region` | `s3.region` | AWS/MinIO region, e.g. `us-east-1`. |
| `bucketName` | `s3.bucketName` | **Global fallback** bucket name. Used when a tenant has no per-tenant bucket. |
| `externalPresignedEndpoint` | `s3.externalPresignedEndpoint` | Public-facing endpoint embedded into presigned GET URLs. Required when MinIO is behind a Docker/NAT boundary (e.g. `http://172.17.0.1:9000`). Leave blank for AWS. |
| `uploadMode` | `s3.upload-mode` | `SYNC` (default, `S3Client`) or `ASYNC` (`S3AsyncClient`). Overridable at runtime via MongoDB. |
| `chunkSize` | `s3.chunkSize` | Multipart chunk size in bytes. Default 10 MB. |

### Real deployment examples

Connector A (provider, `ci/docker/connector_a_resources/application.properties`):
```properties
s3.endpoint=http://minio:9000
s3.accessKey=minioadmin
s3.secretKey=minioadmin
s3.region=us-east-1
s3.bucketName=dsp-true-connector-a
s3.externalPresignedEndpoint=http://172.17.0.1:9000
s3.upload-mode=SYNC
```

For AWS, `s3.endpoint` is left **blank** (SDK uses the default AWS endpoint) and `s3.externalPresignedEndpoint` is also blank (SDK generates the correct URL).

---

## 2. Admin Client vs Bucket-Scoped Client

Class: `tools/src/main/java/it/eng/tools/s3/configuration/S3ClientProvider.java`

Two distinct `S3Client` instances are used:

### Admin client (`adminS3Client()`)
- Uses `s3.accessKey` / `s3.secretKey` from `S3Properties`.
- Has full administrative privileges (create/delete buckets, set bucket policies, list all objects).
- Used by: `S3BucketProvisionService`, `S3ClientServiceImpl.listFiles()`.
- Thread-local cache (one per thread).

### Bucket-scoped client (per-bucket cache, keyed by bucket name)
- Uses the per-bucket credentials stored in MongoDB (`bucket_credentials` collection).
- Retrieved via `BucketCredentialsService.getBucketCredentials(bucketName)` which **decrypts** the stored secret key.
- Used by: `S3ClientServiceImpl.downloadFile()`, `S3ClientServiceImpl.deleteFile()`, `S3ClientServiceImpl.fileExists()`.
- Concurrent `ConcurrentHashMap` cache. Cleared by `S3ClientProvider.clearBucketCache(bucketName)` after credential rotation.

---

## 3. Bucket Credentials (`BucketCredentialsEntity`)

MongoDB collection: `bucket_credentials`  
Class: `tools/src/main/java/it/eng/tools/s3/model/BucketCredentialsEntity.java`

- `bucketName` — document `@Id`.
- `accessKey` — plain text.
- `secretKey` — **encrypted at rest** using `@Encrypted` annotation + `FieldEncryptionService`.
- `BucketCredentialsService.saveBucketCredentials()` always **encrypts** before saving.
- `BucketCredentialsService.getBucketCredentials()` always **decrypts** before returning.

> **Never** use the return value of `saveBucketCredentials()` to make S3 calls — the secret key in the returned entity is encrypted. Always use `getBucketCredentials()` to get decrypted credentials.

---

## 4. Tenant-to-Bucket Mapping (`TenantBucketResolver`)

Class: `tools/src/main/java/it/eng/tools/service/TenantBucketResolver.java`

```
Tenant.bucketName (non-null)  →  use that bucket
Tenant.bucketName == null     →  fallback: s3.bucketName property
No tenant context (super-admin) →  fallback: s3.bucketName property (+ WARN log)
```

Two overloads:

| Method | When to use |
|---|---|
| `resolveBucketName()` | Synchronous request thread — reads `TenantContextHolder` automatically. |
| `resolveBucketName(String tenantId)` | **Async contexts** (e.g. transfer strategy threads) where `TenantContextHolder` is empty — pass `transferProcess.getTenantId()` explicitly. |

The `Tenant` model (`tools/src/main/java/it/eng/tools/model/Tenant.java`) has a `bucketName` field. When a tenant has per-tenant bucket isolation, set this field during tenant onboarding.

---

## 5. Bucket Lifecycle (`S3BucketProvisionService`)

Class: `tools/src/main/java/it/eng/tools/s3/service/S3BucketProvisionService.java`

### `ensureBucketCredentials(bucketName)` — idempotent startup entry point
```
credentials exist?  →  return existing
bucket exists?      →  createBucketCredentials(bucketName)
neither?            →  createSecureBucket(bucketName) = createBucket() + createBucketCredentials()
```

Used in `InitialDataLoader` on startup: iterates all tenants, resolves the effective bucket name, and ensures credentials exist.

### `createSecureBucket(bucketName)` — full provisioning
1. `createBucket()` — creates the bucket in S3/MinIO.
   - AWS non-`us-east-1`: adds `LocationConstraint`.
   - `BucketAlreadyExistsException` / `BucketAlreadyOwnedByYouException` → silently ignored (idempotent).
2. `createBucketCredentials(bucketName)` — generates IAM credentials and stores them.

### `createBucketCredentials(bucketName)` — AWS vs MinIO divergence

**AWS mode** (`s3.endpoint` is blank or contains `.amazonaws.com`):
- Reuses `s3.accessKey` / `s3.secretKey` (admin key) — IAM user creation is skipped.
- Saves those credentials under the bucket name in MongoDB.

**MinIO mode** (all other endpoints):
- Generates `accessKey = "GetBucketUser-<8-char-uuid>"` and `secretKey = UUID`.
- Calls `IamUserManagementService.createUser()` — creates a MinIO IAM user.
- Calls `IamUserManagementService.attachPolicyToUser()` — attaches a user-level policy.
- Calls `updateBucketPolicy(bucketName, accessKey)` — appends an `Allow` statement to the bucket policy granting `s3:GetObject`, `s3:PutObject` to the new user. Handles merging with existing statements.
- Saves credentials to MongoDB (secret key encrypted).
- Calls `S3ClientProvider.clearBucketCache(bucketName)` to force fresh client creation.

---

## 6. Presigned URL Generation (`S3ClientServiceImpl.generateGetPresignedUrl`)

File: `tools/src/main/java/it/eng/tools/s3/service/S3ClientServiceImpl.java`

```java
String url = s3ClientService.generateGetPresignedUrl(bucketName, objectKey, Duration.ofDays(7L));
```

Steps:
1. Validates bucket name format.
2. Loads bucket credentials from MongoDB (decrypted secret key).
3. Resolves the external endpoint for URL embedding:
   - If `s3.externalPresignedEndpoint` is set → use it (MinIO Docker scenario).
   - Otherwise use `s3.endpoint` (or null for AWS).
4. Builds an `S3Presigner` using the bucket-scoped credentials.
   - MinIO: `pathStyleAccessEnabled(true)` + `endpointOverride(externalEndpoint)`.
   - AWS: no `endpointOverride`, no path-style flag.
5. Creates a `GetObjectPresignRequest` with the expiration duration.
6. Returns the presigned URL string.

**Important**: AWS presigned URLs expire in max 7 days. MinIO supports up to 7 days by default. Keep expiration ≤ 7 days.

**Range headers on presigned GET URLs**: The `Range` header is safe to add to requests against a presigned URL because it is NOT included in `X-Amz-SignedHeaders`. Adding `Range: bytes=N-` does not invalidate the signature — MinIO and AWS both return HTTP 206 (verified by `MinioPresignedUrlRangeIT`).

---

## 7. File Upload (`S3ClientServiceImpl.uploadFile`)

```java
CompletableFuture<String> etag = s3ClientService.uploadFile(inputStream, destinationS3Properties, contentType, contentDisposition);
```

`destinationS3Properties` keys (all from `S3Utils` constants):

| Key constant | String value | Required |
|---|---|---|
| `S3Utils.BUCKET_NAME` | `"bucketName"` | Yes |
| `S3Utils.OBJECT_KEY` | `"objectKey"` | Yes |
| `S3Utils.ACCESS_KEY` | `"accessKey"` | Yes |
| `S3Utils.SECRET_KEY` | `"secretKey"` | Yes |
| `S3Utils.ENDPOINT_OVERRIDE` | `"endpointOverride"` | MinIO only |
| `S3Utils.REGION` | `"region"` | Yes |

`uploadFile()` builds a `BucketCredentialsEntity` from the map properties (without saving to MongoDB) and dispatches to the configured `S3UploadStrategy` (`SYNC` or `ASYNC`).

---

## 8. HTTP-PULL Transfer — Presigned URL Flow

**Role**: Provider generates a presigned GET URL and sends it to Consumer via `TransferStartMessage.dataAddress.endpoint`.

### Provider side (`DataTransferAPIService.startTransfer`)
1. Retrieves `Artifact` from the dataset associated with the `TransferProcess`.
2. If `ArtifactType.FILE`:
   - Calls `s3ClientService.generateGetPresignedUrl(tenantBucketResolver.resolveBucketName(transferProcess.getTenantId()), transferProcess.getDatasetId(), Duration.ofDays(7L))`.
   - The object key is the **dataset ID** (artifacts are stored in S3 with the dataset ID as the key).
3. Embeds the URL in `DataAddress.endpoint` inside `TransferStartMessage`.
4. Sends `TransferStartMessage` to consumer callback.

### Consumer side (`HttpPullTransferStrategy.transfer`)
1. Receives `TransferStartMessage` with `dataAddress.endpoint` = presigned URL.
2. Resolves the consumer's bucket: `tenantBucketResolver.resolveBucketName(transferProcess.getTenantId())`.
3. Opens `HttpURLConnection` to the presigned URL (GET).
4. Dynamic read timeout: `ceil(contentLength × 1.1 / 1_048_576) × 1000` ms.
5. Streams response body into `S3ClientService.uploadFile()` using **consumer's own S3 credentials** (`s3.accessKey`, `s3.secretKey`, `s3.endpoint`).
6. Object stored in consumer bucket with key = `transferProcess.getId()`.

```
Provider MinIO                Consumer Connector         Consumer MinIO
   |                               |                          |
   |<-- GET presigned URL ---------|                          |
   |--- 200 + artifact stream ---->|                          |
                                   |--- uploadFile(stream)--->|
                                   |                  key=transferProcessId
```

---

## 9. HTTP-PUSH Transfer — Temporary User Flow

**Role**: Consumer creates a temporary IAM user and passes credentials to Provider, who pushes the artifact directly to the Consumer's S3 bucket.

### Consumer side — creating temp user (`DataTransferAPIService.requestTransfer`)
1. Resolves consumer bucket: `tenantBucketResolver.resolveBucketName(transferProcess.getTenantId())`.
2. Calls `TemporaryBucketUserService.createTemporaryUser(transferProcessId, bucketName, objectKey)`:
   - Generates `accessKey = "TempUser-<8-char-uuid>"`, `secretKey = UUID`.
   - Calls `IamUserManagementService.createUser(adapter)`.
   - Attaches a minimal policy allowing **only `s3:PutObject` on the exact object key**:
     ```json
     {
       "Effect": "Allow",
       "Action": ["s3:PutObject"],
       "Resource": ["arn:aws:s3:::<bucketName>/<objectKey>"]
     }
     ```
   - Saves to MongoDB (`temporary_bucket_users` collection) with encrypted secret key.
   - Returns entity with **plain** (unencrypted) secret key for immediate use.
3. Builds `DataAddress.endpointProperties` with:
   - `bucketName` — consumer bucket
   - `region` — `s3.region`
   - `objectKey` — `transferProcessId`
   - `accessKey` — temp user's access key
   - `secretKey` — temp user's **plain** secret key (will be stored encrypted in MongoDB via the TransferProcess)
   - `endpointOverride` — `s3.externalPresignedEndpoint` (MinIO external URL) or `s3.endpoint`
4. Sends `TransferRequestMessage` with `dataAddress` to the Provider.

> **AWS note**: `IamUserManagementService` must use the AWS IAM SDK on AWS. On MinIO it uses the MinIO Admin Client. The interface (`createUser`, `attachPolicyToUser`, `attachTemporaryPolicy`, `deleteUser`, `deletePolicy`) abstracts this. Both implementations must be maintained.

### Provider side — pushing artifact (`HttpPushTransferStrategy.transfer`)
1. Receives `TransferRequestMessage` with consumer's `dataAddress.endpointProperties`.
2. Decrypts `secretKey` from endpoint properties using `FieldEncryptionService.decrypt()` (the consumer stored it encrypted in its own MongoDB).
3. Builds `destinationS3Properties` map from endpoint properties.
4. Generates a presigned GET URL for the **provider's own artifact**:
   ```java
   s3ClientService.generateGetPresignedUrl(
       tenantBucketResolver.resolveBucketName(transferProcess.getTenantId()),
       transferProcess.getDatasetId(),
       Duration.ofDays(1L))
   ```
5. Downloads artifact via the presigned GET URL (`HttpURLConnection GET`).
6. Uploads to consumer's S3 via `s3ClientService.uploadFile(stream, destinationS3Properties, ...)` using the **temporary consumer credentials**.

```
Provider MinIO    Provider Connector     Consumer MinIO
   |                    |                    |
   |<-- GET presigned - |                    |
   |--- artifact ------>|                    |
                        |--- PUT (temp key)->|
                        |              key=transferProcessId
```

### Cleanup after push
`TemporaryBucketUserService.deleteTemporaryUser(transferProcessId)`:
1. Calls `IamUserManagementService.deleteUser(accessKey)` first (releases policy attachment).
2. Calls `IamUserManagementService.deletePolicy(policyName)` where `policyName = "temp-tp-policy-" + transferProcessId`.
3. Removes MongoDB document from `temporary_bucket_users`.

> Cleanup is best-effort: errors during IAM user/policy deletion are logged but not propagated. The MongoDB record is always removed.

---

## 10. Catalog-to-S3 Consistency Check

`CatalogService.getCatalog()` filters out datasets whose artifact file is not present in S3:

```java
List<String> files = s3ClientService.listFiles(tenantBucketResolver.resolveBucketName());
allCatalogs.forEach(catalog -> catalog.getDataset().removeIf(
    dataset -> dataset.getArtifact().getArtifactType() == ArtifactType.FILE
            && !files.contains(dataset.getId())));
```

- Object keys in S3 match dataset IDs.
- `ArtifactType.EXTERNAL` datasets are never filtered (external URLs cannot be checked server-side).
- If the resulting catalog has zero datasets, `validateCatalog()` throws `CatalogErrorException`.
- This check uses `listFiles()` which uses the **admin client**, not the bucket-scoped client.

---

## 11. MongoDB Collections Summary

| Collection | Class | Purpose |
|---|---|---|
| `bucket_credentials` | `BucketCredentialsEntity` | Per-bucket access/secret key pairs (secret encrypted). Keyed by `bucketName`. |
| `temporary_bucket_users` | `TemporaryBucketUser` | Short-lived HTTP-PUSH credentials. Keyed by `transferProcessId`. |
| `tenants` | `Tenant` | Tenant records including optional `bucketName` field for per-tenant bucket isolation. |

---

## 12. Key Design Rules for Future Implementations

1. **Admin key vs bucket key**: Use `s3ClientProvider.adminS3Client()` only for administrative operations (bucket creation, policy updates, listing all files). Use per-bucket credentials from `BucketCredentialsService` for object-level operations (get, put, delete, presign).

2. **Tenant bucket resolution in async code**: Always call `tenantBucketResolver.resolveBucketName(tenantId)` with an explicit `tenantId` in async transfer strategies, not `resolveBucketName()` (which reads the thread-local `TenantContextHolder` that may be empty on a different thread).

3. **Secret key encryption**: The `@Encrypted` annotation on `BucketCredentialsEntity.secretKey` and `TemporaryBucketUser.secretKey` causes automatic transparent encryption at persistence and decryption at read. Never manually encrypt/decrypt in service code — use the repository + service abstraction.

4. **External presigned endpoint**: When MinIO runs behind Docker NAT, `s3.endpoint` uses the Docker network hostname (e.g. `http://minio:9000`) but presigned URL recipients need the host-accessible URL (e.g. `http://172.17.0.1:9000`). Always use `s3.externalPresignedEndpoint` when embedding URLs in DSP protocol messages. On AWS this is blank and the SDK generates the correct public URL.

5. **AWS vs MinIO bucket policy format**: Both use `"Version": "2012-10-17"` IAM policy JSON. On AWS, `Principal.AWS` ARNs reference real IAM users. On MinIO, the same format works because MinIO supports the AWS IAM policy model. The code uses `"arn:aws:iam::*:user/<accessKey>"` format which is compatible with both.

6. **Bucket name validation**: Names must match `^[a-z0-9][a-z0-9\-]{1,61}[a-z0-9]$`. Enforce this before any S3 operation to avoid cryptic SDK errors.

7. **Presigned URL max expiration**: 7 days for both AWS and MinIO. Never exceed this; the SDK will throw or the URL will be silently invalid.
