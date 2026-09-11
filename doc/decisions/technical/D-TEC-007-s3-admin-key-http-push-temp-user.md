# D-TEC-007 — S3 Admin Key for HTTP-PUSH Temporary User Creation

## Metadata
- Status: Accepted
- Date: 2026-07-02
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: s3, minio, http-push, security, iam, multi-tenancy
- Risk Level: Medium

## Context

The HTTP-PUSH data transfer flow in `HttpPushTransferStrategy` requires creating a temporary MinIO
IAM user on the consumer side so that the provider can push an artifact directly into the consumer's
S3 bucket. The temporary user is created with a minimal `s3:PutObject` policy scoped to the exact
object key, and is deleted after the transfer completes (see `TemporaryBucketUserService`).

Creating a MinIO IAM user requires an API call to the MinIO Admin REST API
(`/minio/admin/v3/add-user`). This operation is implemented in `IamUserManagementService.createUser()`
and currently uses the S3 admin credentials (`s3.accessKey` / `s3.secretKey` from `S3Properties`).
These are full administrative credentials with unrestricted MinIO privileges.

This was flagged as a security risk in #247: using admin credentials for a routine transfer
operation means that if those credentials were somehow exposed at runtime, an attacker would have
full MinIO administrative access.

The question investigated in this ADR is whether the per-tenant bucket credentials (stored in
`BucketCredentialsEntity`, encrypted at rest) can replace the admin key for temp-user creation,
so that each tenant's transfer operations use a least-privilege credential set.

## Decision

Retain the S3 admin key for temporary MinIO IAM user creation in HTTP-PUSH transfers. Full
replacement with per-tenant bucket credentials is not feasible under the current MinIO IAM model.

## Alternatives Considered

- **Per-bucket credentials with `admin:CreateUser` policy attached** → rejected. MinIO denies
  `/minio/admin/v3/add-user` for users with an `admin:CreateUser` policy statement even when
  the policy is attached at creation time by an admin user. Evidence:
  `MinioTenantManagementCredentialsIT#subAdminPolicyAttachedAtCreation_stillCannotCreateDelegatedUsers`
  confirmed `AccessDenied` on the add-user endpoint.

- **Combined `s3:*` + `admin:CreateUser` + `admin:AttachUserOrGroupPolicy` user policy** → also
  rejected. A policy combining full bucket-scoped S3 access with the two admin actions still
  receives `AccessDenied` on both the add-user call and on `s3:PutObject` for the target bucket.
  Evidence: `MinioTenantManagementCredentialsIT#isolatedDelegatedPolicy_stillDoesNotGrantOwnBucketAccessOrDelegatedTempUserCreation`.
  MinIO evaluates admin actions exclusively against its built-in admin RBAC; user-level IAM
  policy statements for admin actions are ignored.

- **MinIO service accounts (access-key aliases) as a scoped admin substitute** → rejected.
  MinIO service accounts inherit their parent user's permissions and cannot create other users.
  They would reduce the blast radius of a credential leak but do not solve the delegation
  problem for temp-user creation.

## Rationale

MinIO's IAM implementation does not expose a delegatable admin action for user creation. The
`/minio/admin/v3/add-user` endpoint is gated by MinIO's internal admin RBAC, not by IAM policy
evaluation. All three alternatives investigated either still require admin-level credentials or
are structurally incapable of delegating user-creation authority. The admin key is therefore
the minimum viable credential for this operation under the current MinIO server model.

The risk is partially mitigated by the following existing controls:
- The admin key is stored in application properties and never persisted to MongoDB.
- Temporary users are created and deleted within the same transfer lifecycle; their window of
  exposure is bounded by transfer duration.
- `TemporaryBucketUserService` deletes the IAM user and the associated policy after transfer
  completion (`deleteTemporaryUser`), regardless of transfer outcome.

## Consequences

### Positive
- No change to the existing HTTP-PUSH transfer implementation is required.
- The risk is explicitly documented and bounded; future contributors know the accepted trade-off.
- Temporary user policies are already narrowly scoped: `s3:PutObject` on a single object key,
  for the duration of one transfer.

### Negative
- The S3 admin key must be present in `s3.accessKey` / `s3.secretKey` for HTTP-PUSH to function.
  If those properties are misconfigured or rotated without updating the application, HTTP-PUSH
  will fail at temp-user creation time.
- A compromise of the admin key would grant full MinIO administrative access (create/delete buckets,
  users, policies). This risk is shared with the existing bucket-provisioning code path and is
  not unique to HTTP-PUSH.

### Risks
- **Admin key compromise**: If `s3.accessKey` / `s3.secretKey` are exposed, all MinIO data
  and IAM configuration are at risk. Mitigation: store credentials in a secrets manager (e.g.
  Kubernetes Secret, Vault) rather than plain `application.properties` in production; rotate
  credentials regularly; ensure MinIO is not exposed to untrusted networks.
- **Stale temporary users**: If the connector is stopped abruptly during an HTTP-PUSH transfer,
  the temporary IAM user and its policy may not be cleaned up. Mitigation: `InitialDataLoader`
  can be extended in a future release to scan for and remove orphaned temporary users on startup.

## Related
- Decisions: [D-TEC-001](D-TEC-001-mongodb-persistence.md) — MongoDB as persistence layer
- Docs: [data-transfer/doc/data-transfer.md](../../../data-transfer/doc/data-transfer.md) — HTTP-PUSH transfer strategy
- Tickets: #247 (Multitenant changes), #250 (MT3 — S3 bucket provisioning), #251 (MT4 — this investigation)
- Evidence tests: `MinioTenantManagementCredentialsIT#subAdminPolicyAttachedAtCreation_stillCannotCreateDelegatedUsers`,
  `MinioTenantManagementCredentialsIT#isolatedDelegatedPolicy_stillDoesNotGrantOwnBucketAccessOrDelegatedTempUserCreation`
