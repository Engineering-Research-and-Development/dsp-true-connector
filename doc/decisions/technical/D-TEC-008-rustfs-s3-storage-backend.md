# D-TEC-008 — RustFS as the Self-Hosted S3 Storage Backend

## Metadata
- Status: Accepted
- Date: 2026-09-14
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: High
- Supersedes: [D-TEC-007](D-TEC-007-s3-admin-key-http-push-temp-user.md)
- Superseded by: —
- Tags: s3, rustfs, storage, iam, http-push, multi-tenancy
- Risk Level: Medium

## Context

MinIO is no longer available as a supported self-hosted object-storage backend for TRUE
Connector. The connector requires an S3-compatible backend for artifact storage, multipart
uploads, presigned GET URLs, bucket policies, and IAM users with policies scoped to an exact
HTTP-PUSH destination object.

The current implementation uses the AWS SDK for S3 operations and the MinIO-compatible
administration API through `IamUserManagementService`. RustFS provides S3 SigV4 compatibility,
path-style endpoints, IAM users and policies, bucket policies, and a compatible
`/minio/admin/v3` administration alias.

## Decision

Use RustFS as the supported self-hosted S3-compatible storage backend. Continue supporting AWS
S3 as the managed-cloud backend.

For self-hosted deployments, configure a non-blank `s3.endpoint` for RustFS and set
`s3.externalPresignedEndpoint` to the address reachable by presigned-URL recipients.

## Alternatives Considered

- **Retain MinIO** → rejected because it is no longer an available supported backend.
- **Garage** → rejected because its bucket-level key permissions cannot express the
  exact-object `s3:PutObject` authorization required by the existing direct HTTP-PUSH flow.
- **AWS S3 only** → rejected because self-hosted and air-gapped deployments require an
  S3-compatible backend under operator control.

## Rationale

RustFS preserves the existing data-plane design without changing DSP messages or requiring a
consumer upload gateway. Its S3 and administration compatibility allows the connector to retain
tenant bucket provisioning, temporary exact-object credentials, presigned downloads, and the
current AWS SDK integration.

## Consequences

### Positive
- Self-hosted deployments retain direct HTTP-PULL and HTTP-PUSH transfer behavior.
- Existing S3 and IAM abstractions remain usable for RustFS and AWS S3.
- RustFS supports least-privilege temporary `PutObject` policies for HTTP-PUSH.

### Negative
- Deployment manifests, operational documentation, certificates, and integration-test
  infrastructure must use RustFS configuration and endpoints.
- RustFS is an additional backend compatibility surface that must be validated on upgrades.

### Risks
- **Backend compatibility drift**: RustFS may diverge from MinIO-compatible administration
  semantics. Mitigation: pin tested RustFS images and run S3, IAM, policy, multipart-upload,
  and presigned-URL integration coverage before upgrades.
- **Administrative credential exposure**: HTTP-PUSH temporary-user provisioning still requires
  S3 administrative credentials. Mitigation: store credentials in a secrets manager, limit
  network access to RustFS administration endpoints, and rotate credentials regularly.

## Related
- Decisions: [D-TEC-007](D-TEC-007-s3-admin-key-http-push-temp-user.md)
- Docs: [S3 configuration](../../s3_configuration.md), [tenant S3 provisioning](../../../tools/doc/tenant-s3-provisioning.md)
- Tickets: —
