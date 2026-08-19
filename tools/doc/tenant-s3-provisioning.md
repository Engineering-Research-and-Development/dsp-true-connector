# Tenant S3 Provisioning

This document describes how S3 buckets are provisioned for tenants in TRUE Connector.

## Tenant creation bucket modes (`POST /api/v1/tenants`)

Tenant creation supports three input modes resolved from the optional
`bucketName`/`accessKey`/`secretKey` fields:

| Input fields | Resolved mode | Behavior |
|---|---|---|
| no bucket fields supplied | `AUTOMATIC` | Bucket is auto-derived as `dsp-{tenantId}` and provisioned via `ensureBucketCredentials` |
| `bucketName` only | `EXISTING_BUCKET` | Supplied bucket name is used; credentials are ensured/generated via `ensureBucketCredentials(bucketName)` |
| `bucketName` + `accessKey` + `secretKey` | `EXTERNAL_CREDENTIALS` | Supplied credentials are persisted as-is via `BucketCredentialsService.saveBucketCredentials`; auto-provisioning is skipped |

For `EXTERNAL_CREDENTIALS`, `verifyConnection=true` enforces a pre-flight `HeadBucket`
check through `BucketConnectionVerificationService` before any credentials or tenant data
are persisted. If verification fails, tenant creation is rejected with HTTP 400 and no
tenant or bucket-credentials record is created.

In `AUTOMATIC` mode, the bucket name derivation rule remains:

```
bucketName = "dsp-" + tenantId.toLowerCase()
```

**Example**: a tenant with `id = "acme-corp"` receives bucket name `"dsp-acme-corp"`.

### Bucket name validation

Bucket names must match: `^[a-z0-9][a-z0-9\-]{1,61}[a-z0-9]$`.
Validation is enforced inside `S3BucketProvisionService` before any S3 operation.

## Tenant update

`PUT /api/v1/tenants/{tenantId}` (via `TenantService.updateTenant()`) allows updating
name, description, automaticNegotiation, and automaticTransfer.

The `bucketName` is **immutable** after tenant creation. Any `bucketName` value in the
update body is silently ignored and the existing bucket name is always preserved.
The `participantId` and `enabled` state are similarly immutable via this endpoint.

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

## Bring-Your-Own-Bucket Foundation (TB1)

> **Status**:
> - `POST /api/v1/tenants` wiring is implemented (TB2).
> - `PUT /api/v1/tenants/{id}` wiring is tracked separately by TB3 under [#322](https://github.com/Engineering-Research-and-Development/dsp-true-connector/issues/322).

To let an admin optionally supply an existing bucket and/or external credentials instead of
always relying on automatic provisioning, the following shared contract exists in `tools`:

- **`TenantBucketCredentialsRequest`** (`it.eng.tools.model`) — a request-only carrier for
  `bucketName`, `accessKey`, `secretKey`, and `verifyConnection` (defaults to `false`). It has
  no Spring Data annotations and is **never** persisted or returned from any controller —
  responses and the persisted `Tenant` document never carry raw credentials.
- **`BucketProvisioningMode`** (`it.eng.tools.model`) — an enum describing the resolved intent:
  `AUTOMATIC`, `EXISTING_BUCKET`, or `EXTERNAL_CREDENTIALS`.
- **`BucketProvisioningModeResolver`** (`it.eng.tools.service`) — classifies a
  `TenantBucketCredentialsRequest` into exactly one `BucketProvisioningMode`, using this
  partial-input matrix:

  | `bucketName` | `accessKey` | `secretKey` | Resolved mode |
  |---|---|---|---|
  | absent | absent | absent | `AUTOMATIC` |
  | present | absent | absent | `EXISTING_BUCKET` |
  | present | present | present | `EXTERNAL_CREDENTIALS` |
  | any other combination | | | throws `IllegalArgumentException` |

- **`BucketConnectionVerificationService`** (`it.eng.tools.s3.service`) — an opt-in
  (`verifyConnection=true`) pre-flight check that probes a candidate `bucketName` +
  `accessKey` + `secretKey` with a `HeadBucket` request via `S3ClientProvider`, returning
  `true`/`false` without ever calling `BucketCredentialsService.saveBucketCredentials()`.
  The ad-hoc client used for the probe is evicted from `S3ClientProvider`'s cache
  (`clearBucketCache`) after use, on both the success and failure paths, so a
  rejected/candidate credential set can never linger and be reused by a later call for the
  same bucket name.

These two services compose directly: `BucketProvisioningModeResolver.resolve()` output for
`EXTERNAL_CREDENTIALS` mode feeds the same `bucketName`/`accessKey`/`secretKey` values
straight into `BucketConnectionVerificationService.verify(...)`, with no adapter needed.
Invalid combinations are rejected by the resolver before any S3 call would ever be attempted.

**Reusable pattern**: separating a request-only "intent" carrier
(`TenantBucketCredentialsRequest`) from the persisted/response domain model (`Tenant`) is the
safe way to accept sensitive optional input without touching the existing Jackson-serialized
response shape. Future "bring your own X" admin-input features should follow this same
separation.

## Related

- [s3_configuration.md](../../doc/s3_configuration.md) — S3 property reference
- [doc/architecture.md](../../doc/architecture.md) — Multi-tenancy runtime model
- ADR [D-TEC-005](../../doc/decisions/technical/D-TEC-005-programmatic-startup-indexes.md) — startup index creation
- ADR [D-TEC-006](../../doc/decisions/technical/D-TEC-006-dbref-tenant-filter-mitigation.md) — @DBRef isolation strategy
