# Tenant S3 Provisioning

This document describes how S3 buckets are provisioned for tenants in TRUE Connector,
including the bucket auto-derivation rule added in the MT3 slice.

## Bucket auto-derivation

When a tenant is created via `POST /api/v1/tenants` and the request body omits `bucketName`,
the service automatically derives one using the rule:

```
bucketName = "dsp-" + tenantId.toLowerCase()
```

**Example**: a tenant with `id = "acme-corp"` receives bucket name `"dsp-acme-corp"`.

The auto-derived name is immediately provisioned in S3/MinIO before the tenant document
is persisted. If the derived name is already owned by a different tenant, `saveTenant()`
throws `IllegalArgumentException` and returns HTTP 409.

### Providing an explicit bucket name

You may still supply an explicit `bucketName` in the request body:

```json
{
  "name": "ACME Corporation",
  "participantId": "urn:connector:acme",
  "bucketName": "my-custom-bucket"
}
```

When a `bucketName` is present it is used as-is, and `S3BucketProvisionService.ensureBucketCredentials()`
is called with the supplied name.

### Bucket name validation

Bucket names must match: `^[a-z0-9][a-z0-9\-]{1,61}[a-z0-9]$`.
Validation is enforced inside `S3BucketProvisionService` before any S3 operation.

## Tenant update

`PUT /api/v1/tenants/{tenantId}` (via `TenantService.updateTenant()`) does **not** auto-derive
a bucket name. If `bucketName` is omitted from the update body, the existing bucket name is
preserved. A new bucket is provisioned only when `bucketName` is explicitly included in the
request body and its value differs from the current one. The old bucket is **not** deleted
automatically — clean it up manually if it is no longer needed.

## Per-tenant bucket isolation

Each tenant gets its own S3 bucket. The `TenantBucketResolver` service resolves the effective
bucket name at request time:

| Condition | Effective bucket name |
|---|---|
| `Tenant.bucketName` is non-null | `Tenant.bucketName` |
| `Tenant.bucketName` is null | `s3.bucketName` property (global fallback) |
| No tenant context (super-admin sync path) | `s3.bucketName` property + WARN log |

In async contexts such as transfer strategy threads, always resolve the bucket with an explicit
`tenantId` — do not rely on the thread-local `TenantContextHolder`, which may be empty:

```java
// Correct: pass tenantId explicitly in async code
tenantBucketResolver.resolveBucketName(transferProcess.getTenantId());

// Incorrect in async threads: TenantContextHolder may be empty
tenantBucketResolver.resolveBucketName();
```

## MinIO vs AWS S3

The bucket provisioning logic differentiates between MinIO and AWS S3 based on `s3.endpoint`:

- **Blank or AWS URL** → AWS mode; reuses the admin access/secret key for per-bucket credentials.
- **Non-blank local URL** → MinIO mode; creates a dedicated IAM user (`GetBucketUser-<uuid>`)
  and attaches a bucket-scoped policy before saving credentials to MongoDB.

See the S3 Architecture reference instruction (`.github/instructions/s3-architecture.instructions.md`)
for the full admin-client vs bucket-scoped-client distinction and for the `BucketCredentialsEntity`
encryption details.

## Related

- [s3_configuration.md](../../doc/s3_configuration.md) — S3 property reference
- [doc/architecture.md](../../doc/architecture.md) — Multi-tenancy runtime model
- ADR [D-TEC-005](../../doc/decisions/technical/D-TEC-005-programmatic-startup-indexes.md) — startup index creation
- ADR [D-TEC-006](../../doc/decisions/technical/D-TEC-006-dbref-tenant-filter-mitigation.md) — @DBRef isolation strategy
