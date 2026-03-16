# S3 Transfer Strategy: Approaches & Comparison

## Context

Each instance of the application is deployed on a separate machine with its own Minio/AWS storage.
The application uses chunked multipart upload for large file transfers.

---

## Current Implementation: Temp IAM User (Minio-oriented)

### How It Works
1. Server creates a temporary IAM user scoped to a single S3 object via a policy.
2. Credentials (access key + secret) are handed to the consumer.
3. Consumer uploads directly to Minio using standard S3 multipart upload.
4. Server deletes the temporary IAM user after transfer completes.

### Pros
- Works well with Minio (self-hosted S3-compatible storage).
- Consumer uploads directly — server never proxies data.
- Integrates cleanly with existing multipart upload logic.
- Per-instance deployment model naturally distributes load.

### Cons
- Anti-pattern on real AWS (5,000 IAM user hard limit per account).
- Cleanup risk: orphaned IAM users accumulate if `deleteTemporaryUser` fails.
- Each IAM user is limited to 2 active access keys.
- Long-lived credentials — AWS discourages this pattern.

---

## Option 1: AWS STS AssumeRole (Best AWS Replacement)

### How It Works
1. Server calls `STS AssumeRole` using a pre-existing IAM Role.
2. STS returns short-lived credentials (accessKeyId, secretAccessKey, sessionToken).
3. Consumer uploads directly to S3 using those credentials (supports multipart).
4. Credentials auto-expire — no cleanup needed.

### Example
```java
StsClient stsClient = StsClient.create();

AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
    .roleArn("arn:aws:iam::123456789:role/upload-role")
    .roleSessionName("transfer-" + transferProcess.getId())
    .durationSeconds(3600)
    .policy("""
        {
          "Version": "2012-10-17",
          "Statement": [{
            "Effect": "Allow",
            "Action": ["s3:PutObject", "s3:AbortMultipartUpload",
                       "s3:ListMultipartUploadParts"],
            "Resource": "arn:aws:s3:::bucket/specific-object-key"
          }]
        }
        """)
    .build();

Credentials creds = stsClient.assumeRole(assumeRoleRequest).credentials();
```

### Pros
- AWS best practice — drop-in replacement for temp IAM user approach.
- No cleanup required — credentials auto-expire.
- No IAM user limits.
- Supports existing multipart upload logic unchanged.
- Scoped to exact object.

### Cons
- Minio STS support is limited/partial.
- Requires a pre-existing IAM Role setup.

---

## Option 2: Simple Presigned PUT URL

### How It Works
1. Server generates a time-limited presigned PUT URL for a specific object key.
2. Consumer does a plain HTTP PUT to the URL — no credentials needed.

### Pros
- Very low consumer complexity (plain HTTP PUT).
- No IAM user or role management.
- Auto-expiry — no cleanup.
- Works on both AWS and Minio.

### Cons
- **5 GB object size limit.**
- **High RAM usage** — entire object loaded into memory when object is large.
- Not suitable for large files without chunking.

---

## Option 3: Presigned Multipart Upload URLs

### How It Works
1. Server initiates a multipart upload → receives an `uploadId`.
2. Server generates N presigned URLs — one per part (min 5 MB, max 5 GB per part).
3. Consumer uploads each part directly to S3/Minio using the presigned URLs.
4. Consumer sends the ETag list back to the server.
5. Server calls `CompleteMultipartUpload` with the ETag list.

### Pros
- No RAM pressure — consumer streams chunk by chunk.
- No IAM user created.
- URLs auto-expire.
- Works with both AWS and Minio.
- No 5 GB single-object limit.

### Cons
- Consumer must make an extra callback to server with ETags to complete upload.
- More complex protocol change on both provider and consumer sides.

---

## DDoS / Load Considerations

### Current `HttpPushTransferStrategy` Risk
The current push strategy proxies data through the server:

```
Source Server → downloads from own Minio → re-uploads to Consumer's S3/Minio
```

This makes **your server the bottleneck** under concurrent transfers.

### Credential Delegation Approaches (IAM User, STS, Presigned Multipart)
Consumer uploads **directly** to S3/Minio:

```
Consumer → uploads directly to Source S3/Minio (using delegated credentials)
```

- Server never touches the data stream.
- Eliminates bandwidth and memory pressure on the server.
- Naturally safer from a self-inflicted DDoS perspective.

### Per-Instance Deployment Benefit
Since each instance has its own storage and handles only its own transfers:
- Load is naturally distributed across instances.
- No single server is hammered by all transfers in the system.
- This mitigates the load concern significantly regardless of approach.

### AWS-Specific DDoS Notes
- AWS Shield Standard (free) protects S3 endpoints automatically.
- STS endpoint has a default quota of ~1,400 requests/second — unlikely to be an issue for normal use.

---

## Full Comparison Table

| | Temp IAM User (current) | STS AssumeRole | Simple Presigned PUT | Presigned Multipart |
|---|---|---|---|---|
| **Large file support** | ✅ (chunked) | ✅ (chunked) | ⚠️ 5 GB limit | ✅ (chunked) |
| **RAM pressure** | ✅ low (direct) | ✅ low (direct) | ❌ high | ✅ low |
| **IAM user cleanup** | ✅ required | ❌ not needed | ❌ not needed | ❌ not needed |
| **IAM user limits** | ⚠️ 5,000 cap | ❌ not applicable | ❌ not applicable | ❌ not applicable |
| **AWS best practice** | ⚠️ no | ✅ yes | ✅ yes | ✅ yes |
| **Minio compatible** | ✅ full | ⚠️ partial | ✅ full | ✅ full |
| **Consumer complexity** | Medium | Medium | Low | Medium |
| **Extra callback needed** | ❌ | ❌ | ❌ | ✅ yes |
| **Auto-expiry** | ❌ manual cleanup | ✅ yes | ✅ yes | ✅ yes |
| **Server proxies data** | ❌ (current push does) | ❌ | ❌ | ❌ |
| **Protocol change needed** | ❌ | ❌ minimal | ❌ | ✅ yes |

---

## Recommendations by Environment

| Environment | Recommended Approach |
|---|---|
| **Minio only** | Current temp IAM user — pragmatic, works well, fits per-instance model |
| **AWS only** | STS AssumeRole — drop-in replacement, same flow, AWS best practice |
| **Both (Minio + AWS)** | Abstract behind an interface: IAM user for Minio, STS for AWS |
| **Future-proof / protocol redesign** | Presigned Multipart — cleanest long-term, but requires callback protocol change |

---

## Architectural Note

The current per-instance deployment model (each instance has its own Minio/AWS) is well-suited
to the temp IAM user + multipart approach:

- No cross-instance coordination needed.
- Failures are isolated per instance.
- Scaling is straightforward — add more instances.

If AWS compatibility becomes a requirement, **STS AssumeRole** is the recommended migration path
as it is structurally identical to the current approach and requires minimal code changes.

