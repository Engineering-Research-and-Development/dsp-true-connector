# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- **TB1 — Tenant Bucket Credential Request Contract & Verification**
  - `TenantBucketCredentialsRequest` (`tools`) — a request-only carrier for optional `bucketName`/`accessKey`/`secretKey`/`verifyConnection` fields, never persisted and never returned from any controller.
  - `BucketProvisioningMode` (`AUTOMATIC`/`EXISTING_BUCKET`/`EXTERNAL_CREDENTIALS`) and `BucketProvisioningModeResolver` (`tools`) — classify the optional fields above into one of the three valid provisioning modes, or reject invalid combinations with `IllegalArgumentException`.
  - `BucketConnectionVerificationService` (`tools`) — verifies candidate external bucket credentials against S3/MinIO via a `HeadBucket` probe, with no persistence side effects, backing the opt-in `verifyConnection` pre-flight check. See [tools/doc/tenant-s3-provisioning.md](tools/doc/tenant-s3-provisioning.md#bring-your-own-bucket-foundation-tb1).
- **TB2 — Tenant Creation Bring-Your-Own-Bucket Onboarding**
  - `POST /api/v1/tenants` now accepts optional `bucketName`/`accessKey`/`secretKey`/`verifyConnection` fields via `TenantCreateRequest`, and routes to `TenantService.saveTenant(Tenant, TenantBucketCredentialsRequest)`.
  - `TenantService.saveTenant(...)` now resolves `BucketProvisioningMode`:
    - `AUTOMATIC`: keeps existing `dsp-{tenantId}` derivation and `ensureBucketCredentials` provisioning.
    - `EXISTING_BUCKET`: uses supplied `bucketName` and reuses `ensureBucketCredentials(bucketName)`.
    - `EXTERNAL_CREDENTIALS`: persists supplied credentials with `BucketCredentialsService` and skips auto-provisioning.
  - `verifyConnection=true` in external-credentials mode now enforces a pre-persistence bucket connectivity check through `BucketConnectionVerificationService`; failed verification rejects creation with HTTP 400 and no tenant/credentials persistence.
  - Added unit coverage for all three modes, conflict handling, and both `verifyConnection` outcomes (`TenantServiceTest`, `TenantCreateRequestTest`, `TenantAPIControllerTest`), plus end-to-end `TenantAPIIT` coverage for automatic, existing-bucket, external-credentials, verification success/failure, and bucket-conflict scenarios.

## [0.7.0] - 10.07.2026 - Multi-Tenant Support

- **Updated java from 17 to 21**

### Added
- **MT1 — Tenant & User Lifecycle Foundation**
  - `TenantService.saveTenant()` now auto-generates a **UUID** as the tenant ID; any caller-supplied `id` is ignored. `callbackAddress` is derived programmatically as `${application.callback.address}/{id}` — any caller-supplied `callbackAddress` is also ignored.
  - `UserDTO` has a new `tenantId` field. When provided, `UserService.createUser()` validates that the referenced tenant exists and is enabled before persisting the user; users are stored with their `tenantId` linked.
  - `ROLE_SUPER_ADMIN` users are exempt from tenant-existence validation and may be created without a `tenantId`.
  - **Keycloak mode user registration** — `KeycloakUserService` and `KeycloakUserApiController` added. When `application.auth.provider=KEYCLOAK`, `POST /api/v1/users` delegates to the Keycloak Admin REST API (`POST /admin/realms/{realm}/users`) using the service account client credentials. Requires `application.keycloak.admin.server-url` and `application.keycloak.admin.realm` properties.
  - ADR [D-TEC-001](doc/decisions/technical/D-TEC-001-keycloak-user-registration.md) — documents the Keycloak user-registration design rationale.
- **MT2 — Security & Access Control Hardening**
  - `ROLE_SUPER_ADMIN` is now required for `/api/v1/users/**` and `/api/v1/properties/**` in both `BASIC` and `KEYCLOAK` authentication modes. Previously only `/api/v1/tenants/**` was restricted to `ROLE_SUPER_ADMIN`; `ROLE_ADMIN` users could access user and property management endpoints, creating a privilege-escalation gap.
  - Integration tests added covering SUPER_ADMIN access (200) and ROLE_ADMIN denial (403) for all three restricted endpoint prefixes in both auth modes. DISABLED mode is explicitly verified as permit-all for all three endpoints.
  - **ROLE_ADMIN self-service user management** — `ROLE_ADMIN` users may now call `GET /api/v1/users/me` (own profile), `PUT /api/v1/users/{id}/update` (own name), and `PUT /api/v1/users/{id}/password` (own password) without requiring `ROLE_SUPER_ADMIN`. All other user-management endpoints remain restricted to `ROLE_SUPER_ADMIN`.
  - **Role enum cleanup** — `Role` enum values renamed from `ROLE_ADMIN`/`ROLE_USER`/`ROLE_CONNECTOR`/`ROLE_SUPER_ADMIN` to `ADMIN`/`USER`/`CONNECTOR`/`SUPER_ADMIN`. `authorityName()` helper added to produce the Spring Security `ROLE_`-prefixed authority string. `User.getAuthorities()` now calls `role.authorityName()`. All inline `"ROLE_*"` string literals removed from production and test code.
  - **Tenant.participantId immutability** — `TenantService.updateTenant()` no longer accepts a new `participantId` from the request body; any supplied value is silently ignored and the stored `participantId` is always preserved after tenant creation.
- **MT3 — Per-Tenant Data Isolation**
  - **S3 bucket auto-derivation** — `TenantService.saveTenant()` now auto-derives the S3 bucket name as `dsp-{tenantId}` when `bucketName` is not supplied in the request body. The derived bucket is provisioned via `S3BucketProvisionService.ensureBucketCredentials()` before the tenant document is persisted. See [tools/doc/tenant-s3-provisioning.md](tools/doc/tenant-s3-provisioning.md).
  - **MongoDB compound startup indexes** — seven collections (`catalogs`, `datasets`, `contract_negotiations`, `transfer_process`, `agreements`, `audit_events`, `application_properties`) receive `(tenantId, _id)` compound indexes at application startup via `InitialDataLoader.createCompoundIndexes()`. Index creation is idempotent. See ADR [D-TEC-005](doc/decisions/technical/D-TEC-005-programmatic-startup-indexes.md).
  - **Cross-tenant isolation integration test** — `CrossTenantIsolationIT` verifies that catalog, dataset, and data-service resources owned by one tenant are not accessible to a different tenant.
  - ADR [D-TEC-005](doc/decisions/technical/D-TEC-005-programmatic-startup-indexes.md) — programmatic startup index creation rationale.
  - ADR [D-TEC-006](doc/decisions/technical/D-TEC-006-dbref-tenant-filter-mitigation.md) — @DBRef tenant-filter limitation and service-layer mitigation.
- **MT4 — Cross-Tenant Transfer & Integration**
  - **Async tenant context propagation** — `TenantContextTaskDecorator` (`tools`) propagates `TenantContextHolder` from the submitting thread to worker threads on every executor/scheduler that runs outside the HTTP request thread: the async Spring event executor, the negotiation retry scheduler, and the data-transfer `httpPullTransferExecutor`, `httpPushTransferExecutor`, and `transferTaskScheduler` beans. See [data-transfer/doc/data-transfer.md](data-transfer/doc/data-transfer.md#async-tenant-context-propagation).
  - `CrossTenantTransferIT` — end-to-end integration test proving that a single connector instance running one tenant as provider and another as consumer completes automatic contract negotiation plus an HTTP-PULL transfer, using the `/{tenantId}/` protocol routing introduced in MT2.
  - DSP TCK compliance re-verified at 65/65 with `/{tenantId}/` protocol routing in place.
  - ADR [D-TEC-007](doc/decisions/technical/D-TEC-007-s3-admin-key-http-push-temp-user.md) — documents the accepted risk of using the S3 admin key for HTTP-PUSH temporary MinIO IAM user creation, since MinIO does not support delegated IAM user creation.
- **AUTH1-4 — Unified backend-mediated authentication & JWT login (#281)**
  - **Unified `/api/v1/auth/*` contract** — `AuthController` exposes `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, and `POST /api/v1/auth/logout` for both `KEYCLOAK` and `INTERNAL` authentication modes, so no client ever calls Keycloak or mints credentials directly. In `KEYCLOAK` mode, `KeycloakAuthServiceImpl` proxies the request to Keycloak's token endpoint using the public UI client (`application.keycloak.login.*`, resource-owner-password-grant). In `INTERNAL` mode, `InternalAuthServiceImpl` issues/refreshes/revokes self-signed JWTs backed by MongoDB users and `RefreshTokenStore`. Both modes return the same `LoginResponse` shape.
  - **`INTERNAL` mode end-to-end JWT** — admin (`/api/**`) and protocol (`/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**`) endpoints in `INTERNAL` mode are now authenticated via `InternalJwtAuthenticationFilter` (Bearer JWT), not raw HTTP Basic.
  - **Connector-to-connector M2M** — `ConnectorCredentialProvider` / `ConnectorCredentialProviderImpl` (`connector`) obtain an M2M JWT for the seeded `ROLE_CONNECTOR` service account via `AuthService.login(...)`, used by `CredentialUtils.getConnectorCredentials()` when running in `INTERNAL` mode.
  - **Internal-service M2M** — new `InternalServiceTokenIssuer` (`tools`) replaces `InternalAuthenticationService`, minting a correctly-scoped `ROLE_ADMIN` JWT for inter-module calls without embedding `application.security.jwt.secret` in any JWT claim.
  - **`M2mTokenCache`** (`tools`) — shared cache for both M2M token flows above; proactively refreshes ~30s before expiry and is invalidated automatically on an HTTP 401 retry in `OkHttpRestClient`.
- **Expanded audit event coverage — authentication, user management, and catalog admin CRUD**
  - **22 new + 2 reactivated `AuditEventType` constants** — `APPLICATION_LOGIN`/`APPLICATION_LOGOUT` are reactivated and paired with new `APPLICATION_LOGIN_FAILED`/`APPLICATION_LOGOUT_FAILED`/`APPLICATION_TOKEN_REFRESHED`/`APPLICATION_TOKEN_REFRESH_FAILED`/`M2M_TOKEN_ISSUED`/`M2M_TOKEN_ISSUE_FAILED` events; `USER_CREATED`/`USER_UPDATED`/`USER_PASSWORD_CHANGED`; and 13 catalog-admin CRUD events across Catalog, Dataset, Artifact, DataService, and Distribution (`*_CREATED`/`*_UPDATED`/`*_DELETED`/`ARTIFACT_UPLOADED`).
  - **Authentication events** — `AuthController` now publishes an audit event for every `login`/`refresh`/`logout` call, both on success and on `AuthenticationException` failure, in both `INTERNAL` and `KEYCLOAK` modes; the failure path always rethrows the original exception unchanged so existing 401/500 error mapping is untouched. Events record the active `application.auth.provider` and, for login, the attempted email.
  - **M2M token events** — `ConnectorCredentialProviderImpl.issueConnectorToken()` publishes `M2M_TOKEN_ISSUED`/`M2M_TOKEN_ISSUE_FAILED`, surfacing a previously-silent connector-to-connector authentication failure mode.
  - **User CRUD events** — `UserService.createUser()`/`updateUser()`/`updatePassword()` publish `USER_CREATED`/`USER_UPDATED`/`USER_PASSWORD_CHANGED` on success.
  - **Catalog admin CRUD events** — `CatalogService.updateCatalog()`/`deleteCatalog()`, `DatasetService.saveDataset()`/`updateDataset()`/`deleteDataset()`, `ArtifactService.uploadArtifact()`/`deleteArtifact()`, `DataServiceService.saveDataService()`/`updateDataService()`/`deleteDataService()`, and `DistributionService.saveDistribution()`/`updateDistribution()`/`deleteDistribution()` now publish the corresponding audit event via `AuditEventPublisher`. `CatalogService.saveCatalog()` is deliberately not audited since it is an internal helper reused by every dataset/data-service/distribution mutation.

### Changed
- **MT1** — `POST /api/v1/tenants`: `id` and `callbackAddress` in the request body are now ignored (server-generated). See [connector/documentation/users.md](connector/documentation/users.md) for the updated API reference.
- **MT1** — `POST /api/v1/users`: `tenantId` field added to the request body.
- **MT2** — `ConnectorSecurityConfig`: hardcoded role strings `"SUPER_ADMIN"`, `"ADMIN"`, `"CONNECTOR"` replaced with constants derived from the `it.eng.connector.model.Role` enum. This makes the Role enum the single source of truth for all role names in the security configuration.
- **MT2** — `TenantService.updateTenant()`: `participantId` is now immutable — any value in the request body is silently ignored. The stored `participantId` is always preserved after tenant creation.
- **MT3** — `POST /api/v1/tenants`: `bucketName` is now optional; the server auto-derives `dsp-{tenantId}` when absent. See [tools/doc/tenant-s3-provisioning.md](tools/doc/tenant-s3-provisioning.md).
- **MT3** — `CatalogService`: three update-helper methods (`updateCatalogDatasetAfterSave`, `updateCatalogDataServiceAfterSave`, `updateCatalogDistributionAfterSave`) now assert tenantId consistency before writing cross-document references, rejecting cross-tenant links with HTTP 404.
- **MT3** — `CatalogRepository`: `findCatalogByDatasetId` and `findCatalogByDataServiceId` are supplemented with tenantId-scoped variants (`findCatalogByDatasetIdAndTenantId`, `findCatalogByDataServiceIdAndTenantId`) used by the service-layer guards.
- Updated policy enforcement and contract negotiation to use the tenant-scoped `AgreementRepository.findAgreementByIdAndTenantId` instead of the non-tenant-scoped `findAgreementById` to prevent cross-tenant agreement lookups.
- **AUTH1-4** — `AuthenticationMode.BASIC` renamed to `AuthenticationMode.INTERNAL`; `application.auth.provider=BASIC` is now `application.auth.provider=INTERNAL`. All `BASIC`-mode behavior (MongoDB-backed users, `/api/v1/users/**` availability, password strength enforcement) is preserved under the new name — only credential presentation changed, from HTTP Basic to a self-issued Bearer JWT obtained via `/api/v1/auth/login`.
- **AUTH1-4** — `terraform/app-resources/connector_a_resources/application.properties` and `connector_b_resources/application.properties`: `application.security.jwt.secret` default values are now identical across both files (previously mismatched, which silently broke cross-connector JWT trust when both connectors ran in `INTERNAL` mode via Terraform-provisioned deployments).

### Security

- **MT2 — SUPER_ADMIN access control** — Restricted `/api/v1/users/**` and `/api/v1/properties/**` to `ROLE_SUPER_ADMIN` in both `BASIC` and `KEYCLOAK` authentication modes. `ROLE_ADMIN` users now receive HTTP 403 on these endpoints, closing a privilege-escalation path where tenant admins could previously manage users or modify global runtime properties across all tenants. Only `/api/v1/tenants/**` was previously restricted; this hardens the full management surface.
- **AUTH1-4 — Internal-service JWT secret leak (#281 / #297)** — The previous `InternalAuthenticationService` passed the raw `application.security.jwt.secret` value into the JWT `email` claim when minting internal-service M2M tokens. Since JWT payloads are base64url-encoded, not encrypted, every internal-service token leaked the shared secret in cleartext to any recipient. Fixed by replacing it with `InternalServiceTokenIssuer`, which mints a token with `email="internal-service"` and never embeds `application.security.jwt.secret` in any claim. Covered by a regression test asserting the minted token's `email` claim is never the configured secret.
- **Multi-tenant foundation** — `Tenant` model, `TenantRepository`, `TenantService`, `TenantContextHolder` (ThreadLocal + MDC), `TenantAPIController` for full tenant lifecycle management via `/api/v1/tenants`.
- `TenantAwareProtocolController` — abstract base class for all DSP protocol controllers; resolves the `{tenantId}` path variable and sets `TenantContextHolder` before any request processing.
- `ApiTenantContextFilter` — sets tenant scope for management API requests from the authenticated user's `tenantId`; super-admins may override with `X-Tenant-Id` header.
- `TenantContextClearingInterceptor` — clears `TenantContextHolder` after each request to prevent ThreadLocal leaks.
- `ROLE_SUPER_ADMIN` — cross-tenant access role; users without a tenant restriction see data across all tenants.
- `InitialDataLoader.ensureEngineeringTenant()` — bootstraps a default `engineering` tenant on startup; connector refuses to start if no enabled tenant exists.
- Audit event types `TENANT_CREATED`, `TENANT_DELETED`, `TENANT_ENABLED`, `TENANT_DISABLED`, `TENANT_NOT_FOUND`.
- `tenantId` field added to `Catalog`, `Dataset`, `Distribution`, `DataService`, `Artifact`, `ContractNegotiation`, `Agreement`, and `TransferProcess` models; field is `@JsonIgnore` so it is transparent to DSP protocol messages.
- Tenant-scoped repository query methods (`findByIdAndTenantId`, `findAllByTenantId`, etc.) for all tenant-aware collections.
- **DSP protocol endpoint URL prefix** — all protocol endpoints now require `/{tenantId}/` prefix:
  - Catalog: `/{tenantId}/catalog/request`
  - Negotiations: `/{tenantId}/negotiations`, `/{tenantId}/consumer/negotiations/...`
  - Transfers: `/{tenantId}/transfers/request`, `/{tenantId}/consumer/transfers/...`
- `tenantId` propagation through the async `InitializeTransferProcess` event so data transfer initialization on worker threads receives the correct tenant context.
- MDC `tenantId` field in all log output (all `logback.xml` files updated).
- `doc/multi-tenant-user-guide.md` — operator and user manual.
- `doc/multi-tenant-technical.md` — developer and architecture reference.

### Changed
- All DSP protocol controllers now extend `TenantAwareProtocolController` and include `tenantId` as first path variable and method parameter.
- `ConnectorSecurityConfig` security matchers updated to cover `/{tenantId}/` prefixed patterns.
- `initial_data.json` — all seed catalog, dataset, distribution, data-service, and artifact entries include `"tenantId": "engineering"`.

### Fixed
- **#277 — Agreement `_id` collision across tenants** — `Agreement` now has its own tenant-independent MongoDB technical primary key (`technicalId`, `@Id`), separate from the shared DSP protocol `id`. Previously `Agreement.id` (which is legitimately shared between a provider's and a consumer's local copies of the same agreement) was used directly as the MongoDB `_id`, so when both connectors ran against the same MongoDB instance the second party's save silently overwrote the first party's document.
  - `agreements` collection now has a unique compound index on `(tenantId, id)` instead of `(tenantId, _id)`.
  - `AgreementRepository.findAgreementById(String id)` added for non-tenant-scoped lookups by the protocol `id` (distinct from the inherited `findById`, which now queries the technical id).
  - `AgreementAPIService.enforceAgreement()` and `PolicyEnforcementPoint.enforcePolicy()` updated to resolve `Agreement`/`ContractNegotiation` references using the appropriate id (protocol `id` vs. technical id) instead of assuming the two always matched.
  - New integration test `AgreementCrossTenantIT` covers persisting and enforcing agreements that share the same protocol `id` across two tenants.

### Removed
- Removed redundant PathVariable name from controllers
- 
## [0.6.12-SNAPSHOT] - 25.06.2026.

### Added
- Added files for agentic development approach

## [0.6.11-SNAPSHOT] - 16.04.2026.

### Added
- `TransferProcess.isDownloadInProgress` — new boolean field persisted to MongoDB and exposed in plain API responses, enabling the frontend to drive a download spinner. Includes crash recovery via `@PostConstruct resetStaleDownloadingFlags()` in `DataTransferAPIService` that resets stale `isDownloadInProgress=true` records left behind by a previous crash or restart.

### Changed
- `DataTransferAPIService.downloadData()` — replaced the in-memory `ConcurrentHashMap<String, CompletableFuture<Void>> activeTransfers` concurrent-download guard with the new DB-backed `isDownloadInProgress` flag. The flag is now visible to the frontend and survives server restarts; concurrent requests are rejected via Spring Data MongoDB `@Version` optimistic locking (`OptimisticLockingFailureException`).
- `TemporaryBucketUserService.deleteTemporaryUser()` — IAM user is now deleted before the policy; Minio rejects policy deletion with `XMinioIAMPolicyInUse` when the policy is still attached to a user, so removing the user first releases the attachment and allows the subsequent policy deletion to succeed.

## [0.6.10-SNAPSHOT] - 02.04.2026.

### Added
- `ConnectorSecurityConfig` — unified Spring Security configuration with three ordered `SecurityFilterChain` beans (admin / protocol / default), replacing the previous dual-config approach (`WebSecurityConfig` + `KeycloakSecurityConfig`).
- `BASIC` authentication mode — HTTP Basic Auth backed by MongoDB `UserService`; active when `application.auth.provider=BASIC`.
- `BasicAuthenticationModeCondition`, `BasicOrDisabledAuthenticationModeCondition`, `DcpEnabledCondition` — new condition classes for conditional bean loading.
- `DcpAuthenticationFilter` — pass-through stub for future Decentralized Claims Protocol (DCP) integration; activated via `application.auth.dcp.enabled=true`.

### Changed
- `AuthenticationMode` enum now contains `KEYCLOAK | BASIC | DISABLED`; LEGACY mode removed.
- `AuthenticationModeResolver` — added BASIC mode resolution, `isDcpEnabled()`, startup validation that rejects `DISABLED + dcp.enabled=true`; defaults to `KEYCLOAK` when no property is set.
- `AuthenticationCache` — removed DAPS dependencies, fixed thread-safety (volatile fields, return inside synchronized block).
- `UserService` and `UserApiController` — switched condition from `NonKeycloakAuthenticationModeCondition` to `BasicOrDisabledAuthenticationModeCondition`; active in BASIC and DISABLED modes only.
- Protocol endpoint authentication failures now return DSP-compliant JSON error responses in all auth modes (via `DataspaceProtocolEndpointsAuthenticationEntryPoint` hooked into `httpBasic`).
- `doc/security.md` and `KEYCLOAK_INTEGRATION_COMPLETE_SUMMARY.md` updated to reflect current architecture.

### Removed
- `WebSecurityConfig` and `KeycloakSecurityConfig` — replaced by `ConnectorSecurityConfig`.
- All DAPS authentication classes: `DapsAuthenticationService`, `DapsAuthenticationProperties`, `DapsCertificateProvider`.
- LEGACY authentication mode and associated filter classes: `JwtAuthenticationFilter`, `JwtAuthenticationProvider`, `JwtAuthenticationToken`, `DataspaceProtocolEndpointsAuthenticationFilter`.
- `LegacyAuthenticationModeCondition`, `NonKeycloakAuthenticationModeCondition`.
- DAPS SSL bundle configuration (`spring.ssl.bundle.jks.daps.*`) from all property files (consumer, provider, CI, TCK, Terraform).
- Removed md files for authentication plan, breakdown and implementation steps.

## [0.6.9-SNAPSHOT] - 27.03.2026.

### Added
- Integration and unit tests for `TemporaryBucketUserService` covering bucket creation, user credential lifecycle, and policy attachment.
- Unit tests (`InitialDataLoaderTest`) and integration tests (`InitialDataLoaderIT`) for `InitialDataLoader`, covering seed data loading, duplicate skipping, missing-file graceful skip, MongoDB/S3 failure resilience, and `seedDataLoaded` flag tracking.
- `DataTransferConfiguration` — new Spring `@Configuration` class providing bounded `ThreadPoolTaskExecutor` beans for concurrent HTTP-PUSH (`httpPushTransferExecutor`) and HTTP-PULL (`httpPullTransferExecutor`) transfers; core/max pool size of 8, queue capacity of 50, graceful shutdown on Spring context close.
- `NegotiationConfiguration` — new Spring `@Configuration` class in the negotiation module providing the `negotiationTaskScheduler` bean (`ThreadPoolTaskScheduler`, pool size 5, thread prefix `negotiation-retry-`) for non-blocking retry scheduling in `AutomaticNegotiationService`; pool size tunable via `application.negotiation.scheduler.pool-size`.
- `DataTransferConfigurationTest` and `NegotiationConfigurationTest` — unit tests covering the new scheduler beans.

### Changed
- `downloadData()` endpoint now correctly returns HTTP 400 when the transfer process is not in `STARTED` state, was already downloaded, or a download is already in progress. Previously, the async refactor caused all validation failures to be silently swallowed and always return HTTP 202.
- Resolved 11 bugs identified in a deep-scan audit of `DataTransferAPIService` and related classes:
  - Temporary IAM user leak on `requestTransfer()` failure — `deleteTemporaryUser()` is now always called in a `finally` block.
  - Blocking `.join()` removed from `DataTransferAPIController.downloadData()`; replaced with fire-and-forget `+` `.exceptionally()` logging; controller now always returns HTTP 202 Accepted for the async download path.
  - `terminateTransfer()` — temporary IAM user was not cleaned up on the success path; `deleteTemporaryUser()` now called.
  - Audit event calls used `Map.of()` which throws `NullPointerException` on null `consumerPid`/`providerPid`; replaced with a private `auditMap()` helper that silently skips null entries.
  - `DataTransferAPIController.getTransferProcesses()` — array index out of bounds on `sort[1]` when only one sort field is provided; added length guard.
  - `policyCheck()` — replaced bare `assert` on response with an explicit null check and meaningful `DataTransferAPIException`.
  - State-transition methods (`startTransfer`, `completeTransfer`, `terminateTransfer`, `suspendTransfer`) — added null guard on callback address before sending protocol messages.
  - `startTransfer()` — `findArtifact()` was called unconditionally at method start; moved inside the `ROLE_PROVIDER + HTTP_PULL` block where it is actually needed.
  - `HttpPullTransferStrategy` and `HttpPushTransferStrategy` — blocking `.join()` and `HttpURLConnection` leak fixed using `AtomicReference` + `thenCompose` pattern; connection is now disconnected via `whenComplete` on all paths.
  - `S3AsyncUploadStrategy.uploadParts()` — blocking `.join()` on executor thread replaced with `runAsync` + `thenCompose(allOf(...).thenApply(...))`.
- Applied 7 additional code-quality fixes from the same deep-scan:
  - Corrected `suspendTransfer` Javadoc (incorrectly stated `COMPLETED` instead of `SUSPENDED`).
  - `suspendTransfer` and `terminateTransfer` audit events now publish the post-transition object instead of the stale pre-transition reference.
  - `viewData()` — added `isDownloaded` pre-check; presigned URL is only generated when the process is both `COMPLETED` and downloaded.
  - `viewData()` exception message: added missing separator and null guard on `getLocalizedMessage()`.
  - `DataTransferAPIService` — `ObjectMapper` is now injected as a Spring-managed bean via constructor instead of being instantiated as a field.
  - `S3UploadStrategyFactory.getStrategy()` — added null-check on `uploadMode` property.
  - `S3TransferStrategy` — marked `@Deprecated` with explanatory Javadoc; removed unused `s3ClientService` field.
- `InitialDataLoader` no longer aborts application startup when the seed data JSON file is missing from the classpath; the loader now logs an info message and returns cleanly. Any I/O or parse error during loading is also caught and logged without re-throwing.
- `TemporaryBucketUserService.createTemporaryUser()` — added compensating `deleteUser()` call in a `catch` block to prevent orphaned IAM users when policy attachment or MongoDB persistence fails after the IAM user has already been created.
- `TemporaryBucketUserService.deleteTemporaryUser()` — policy is now revoked before the IAM user is deleted so the user loses access immediately, not after.
- Using temporary user for S3 upload in HTTP-PUSH transfer strategy, with policy scoped to single object key and cleanup after transfer completion.
- S3 multipart upload default chunk size reduced from 50 MB to 10 MB (10,485,760 bytes); updated in `S3Properties`, all `application*.properties` files (ci, connector, terraform), and upload strategy unit tests.
- Default `s3.upload-mode` changed from `ASYNC` to `SYNC` in `application-consumer.properties` and `application-provider.properties`.
- `InitialDataLoader.loadMockData()` now skips S3 upload entirely when no new MongoDB seed documents were inserted (missing file, all duplicates, or Mongo failure); `seedDataLoaded` flag tracks this across the `CommandLineRunner` → `ApplicationReadyEvent` lifecycle.
- `AbstractDataTransferService` constructor extended with `DataTransferProperties`; cascaded to `DataTransferService` and `TCKDataTransferService`. Automatic transfer triggers wired in: Provider fires `AutoTransferStartEvent` after storing `REQUESTED`; Consumer fires `AutoTransferDownloadEvent` after storing `STARTED` (HTTP_PULL only). `retryCount` is now preserved across the `REQUESTED → STARTED` state transition.
- `AbstractDataTransferService` — encrypts the `secretKey` in `DataAddress` endpoint properties before persisting the `REQUESTED` transfer process to MongoDB for HTTP_PUSH transfers; `FieldEncryptionService` injected via constructor.
- `HttpPushTransferStrategy` — decrypts the `secretKey` from `DataAddress` endpoint properties when building the destination S3 config map; `FieldEncryptionService` injected via constructor.
- `HttpPullTransferStrategy` — replaced static `Executors.newFixedThreadPool(8)` and two-constructor workaround with a single `@Autowired` constructor injecting the Spring-managed `httpPullTransferExecutor` bean via `@Qualifier`; ensures graceful shutdown and named threads (`http-pull-transfer-N`) consistent with `HttpPushTransferStrategy`.
- `AsynchronousSpringEventsConfig` — removed `taskScheduler()` bean, `schedulerPoolSize` field, and `@EnableScheduling`; class is now purely event-dispatch infrastructure (`taskExecutor` + `applicationEventMulticaster`).
- `AutomaticNegotiationService` — constructor parameter `TaskScheduler` qualified with `@Qualifier("negotiationTaskScheduler")`; now resolved from `NegotiationConfiguration` instead of `AsynchronousSpringEventsConfig`.
- `AutomaticDataTransferService` — constructor parameter `TaskScheduler` qualified with `@Qualifier("transferTaskScheduler")`; resolved from `DataTransferConfiguration` with thread prefix `transfer-retry-`; pool size tunable via `application.transfer.scheduler.pool-size`.

## [0.6.8-SNAPSHOT] - 23.03.2026.

### Added
- Automatic data transfer across the full happy-path state machine for both Provider and Consumer roles, covering HTTP_PULL and HTTP_PUSH formats.
- New `AutomaticDataTransferService` — encapsulates retry scheduling and `TERMINATED` fallback for all automatic transfer transitions; the retry loop uses `Thread.sleep` on the already-async listener thread so no HTTP thread is blocked during inter-retry delays.
  - `processStart(id)` — retries `apiService.startTransfer(id)` (sends `TransferStartMessage`); for HTTP_PUSH on the Provider side, automatically chains `processDownload(id)` after a successful start so that the artifact is pushed to the Consumer's S3 endpoint without any additional trigger.
  - `processDownload(id)` — retries `apiService.downloadData(id).join()`; `downloadData` already chains `completeTransfer` on success, so no separate COMPLETED trigger is needed.
- New `AutomaticDataTransferListener` — dedicated async `@EventListener` component that delegates each auto-transfer event to `AutomaticDataTransferService`.
- New domain events (Java Records) in `it.eng.datatransfer.event`:
  - `AutoTransferStartEvent` — fired by the Provider after storing `REQUESTED` state; triggers `TransferStartMessage`.
  - `AutoTransferDownloadEvent` — fired by the Consumer after storing `STARTED` state (HTTP_PULL only); triggers data download and auto-completion.
- `retryCount` field added to `TransferProcess` model — persisted to MongoDB (`@JsonIgnore`, internal bookkeeping); preserved across state transitions and application restarts.
- `withRetryCount(int)` helper method on `TransferProcess` — creates a new instance with only the retry counter updated, mirroring the same helper on `ContractNegotiation`.
- New configuration properties for automatic transfer retry behaviour:
  - `application.automatic.transfer=false` — master switch; mirrors `application.automatic.negotiation`.
  - `application.automatic.transfer.retry.max=3` — maximum retry attempts before transitioning to `TERMINATED`.
  - `application.automatic.transfer.retry.delay.ms=2000` — delay in milliseconds between retry attempts.
- Force-terminate fallback: if the graceful `TransferTerminationMessage` also fails, the `TransferProcess` is force-set to `TERMINATED` locally and a `PROTOCOL_TRANSFER_TERMINATED` audit event is published.
- Integration test `AutomaticDataTransferIT` — standalone two-instance Spring Boot test using Testcontainers (shared MongoDB, per-instance MinIO) and WireMock, mirroring the structure of `AutomaticNegotiationIT`:
  - `automaticDataTransfer_httpPull_reachesCompletedOnBothSides` — full HTTP_PULL happy-path; both Consumer and Provider reach `COMPLETED`; artifact verified in Consumer MinIO.
  - `automaticDataTransfer_httpPush_reachesCompletedOnBothSides` — full HTTP_PUSH happy-path; Provider auto-pushes artifact to Consumer MinIO after `TransferStartMessage` is acknowledged; both sides reach `COMPLETED`.
  - `automaticDataTransfer_providerCannotSendStartMessage_bothReachTerminated` — WireMock intercepts `TransferStartMessage` with 500 on all attempts; retry budget exhausted; both Consumer and Provider reach `TERMINATED`.
- Unit tests: `AutomaticDataTransferServiceTest` and `AutomaticDataTransferListenerTest`.

### Changed
- `AbstractDataTransferService` — added `DataTransferProperties` constructor parameter (cascades to `DataTransferService` and `TCKDataTransferService`); added auto-trigger after `initiateDataTransfer` stores `REQUESTED` (Provider — fires `AutoTransferStartEvent`); added auto-trigger after `startDataTransfer` stores `STARTED` (Consumer + HTTP_PULL only — fires `AutoTransferDownloadEvent`; format guard ensures SFTP and HTTP_PUSH are excluded).
- `AutomaticDataTransferService.processStart` — after `startTransfer` succeeds, detects HTTP_PUSH + Provider role and chains `processDownload` automatically (no new events or listener changes required; the push phase shares the same retry budget as the start phase).
- `DataTransferProperties` — added `automaticTransfer`, `maxRetryAttempts`, and `retryDelayMs` fields bound via `@Value`.
- All `application*.properties` files — added `application.automatic.transfer`, `application.automatic.transfer.retry.max`, and `application.automatic.transfer.retry.delay.ms` keys across provider, consumer, TCK, test, CI Docker, and Terraform configurations.

## [0.6.7-SNAPSHOT] - 18.03.2026.

### Added
- Automatic contract negotiation across the full happy-path state machine for both Provider and Consumer roles.
- New `AutomaticNegotiationService` — encapsulates retry scheduling and `TERMINATED` fallback for all automatic transitions; retries are dispatched via `TaskScheduler` so no thread is blocked during the inter-retry delay.
- New `AutomaticNegotiationListener` — dedicated async `@EventListener` component that delegates each auto-negotiation event to `AutomaticNegotiationService`.
- New domain events (Java Records) in `it.eng.negotiation.event`:
  - `AutoNegotiationAgreedEvent` — fired by Provider after storing `REQUESTED` or `ACCEPTED`; triggers `ContractAgreementMessage`.
  - `AutoNegotiationFinalizeEvent` — fired by Provider after storing `VERIFIED`; triggers `ContractNegotiationEventMessage:finalized`.
  - `AutoNegotiationAcceptedEvent` — fired by Consumer after storing `OFFERED` (initial offer only); triggers `ContractNegotiationEventMessage:accepted`.
  - `AutoNegotiationVerifyEvent` — fired by Consumer after storing `AGREED`; triggers `ContractAgreementVerificationMessage`.
- `retryCount` field added to `ContractNegotiation` model — persisted to MongoDB, preserved across state transitions and app restarts.
- `withRetryCount(int)` helper method on `ContractNegotiation` — creates a new instance with only the retry counter updated.
- New configuration properties for automatic negotiation retry behaviour:
  - `application.automatic.negotiation.retry.max=3` — maximum retry attempts before transitioning to `TERMINATED`.
  - `application.automatic.negotiation.retry.delay.ms=2000` — delay in milliseconds between retry attempts.
- Force-terminate fallback: if the graceful `ContractNegotiationTerminationMessage` also fails, the CN is force-set to `TERMINATED` locally and a `PROTOCOL_NEGOTIATION_TERMINATED` audit event is published.
- Integration test `AutomaticNegotiationIT` — two-instance Spring Boot test using Testcontainers (MongoDB, MinIO) and WireMock:
  - `automaticNegotiation_consumerInitiated_reachesFinalizedOnBothSides` — full happy-path, both sides reach `FINALIZED`.
  - `automaticNegotiation_providerUnreachable_consumerReachesTerminated` — WireMock intercepts `ContractAgreementMessage` with 500, proxies termination; both sides reach `TERMINATED`.
  - `automaticNegotiation_consumerUnreachable_providerReachesTerminated` — WireMock intercepts `ContractAgreementVerificationMessage` with 500, proxies termination; both sides reach `TERMINATED`.
- Unit tests: `AutomaticNegotiationServiceTest` and `AutomaticNegotiationListenerTest`.

### Changed
- `AsynchronousSpringEventsConfig` — replaced `SimpleAsyncTaskExecutor` (unbounded, one thread per task) with a bounded `ThreadPoolTaskExecutor` for the event multicaster; added a `ThreadPoolTaskScheduler` bean (`taskScheduler`) for non-blocking retry scheduling in `AutomaticNegotiationService`. Pool sizes are tunable via `application.events.executor.*` and `application.events.scheduler.pool-size` properties.
- `ContractNegotiationProviderService.handleContractRequestMessage` — replaced deprecated `ContractNegotationOfferRequestEvent` with `AutoNegotiationAgreedEvent`; added auto-trigger after `ACCEPTED` and `VERIFIED` states.
- `ContractNegotiationConsumerService.handleContractAgreementMessage` — replaced commented-out TODO block with `AutoNegotiationVerifyEvent`; added auto-trigger after `OFFERED` state.
- `ContractNegotiationProperties` — added `maxRetryAttempts` and `retryDelayMs` fields bound via `@Value`.

### Removed
- Deprecated `ContractNegotationOfferRequestEvent` publish call from `ContractNegotiationProviderService.handleContractRequestMessage`.
- Deprecated `handleContractNegotiationOfferResponse` event listener from `ContractNegotiationListener`.
- Deprecated `handleContractNegotiationOfferResponse` method from `ContractNegotiationEventHandlerService`.
- Deprecated `validateOffer(ContractNegotationOfferRequestEvent)` method from `CatalogService`.

## [0.6.5-SNAPSHOT] - 04.03.2026.

### Security

- Upgraded Spring Boot from `3.1.2` to `3.5.11`, resolving multiple CVEs in Spring Framework and Spring Security:
  - CVE-2023-34053, CVE-2024-38816, CVE-2024-38819, CVE-2025-41242 (Spring WebMVC path traversal / DoS)
  - CVE-2024-22243, CVE-2024-22259, CVE-2024-22262, CVE-2024-38809, CVE-2024-38820 (Spring Web SSRF / open redirect / DoS)
  - CVE-2024-22234, CVE-2024-22257, CVE-2024-38827 (Spring Security broken access control)
  - CVE-2024-38821 (Spring Security static resource authorization bypass)
- Upgraded `tomcat-embed-core` from `10.1.34` to `10.1.50` (BOM override), resolving 13 CVEs including:
  - CVE-2025-24813 (CRITICAL – partial PUT RCE / information disclosure)
  - CVE-2025-48988, CVE-2025-48989 (HIGH – DoS in multipart upload / made-you-reset)
  - CVE-2025-31650, CVE-2025-49125, CVE-2025-66614 and others (MEDIUM/LOW)
- Upgraded `jackson-core`, `jackson-databind`, `jackson-annotations`, `jackson-datatype-jsr310` from `2.17.1` to `2.18.6` (BOM override):
  - GHSA-72hv-8253-57qq (HIGH – async parser number length constraint bypass leading to DoS)
- Upgraded `netty-codec-http`, `netty-codec-http2` and full Netty suite from `4.1.119.Final` to `4.2.8.Final` (BOM override):
  - CVE-2025-58056 (LOW – request smuggling via chunk extension LF parsing)
  - CVE-2025-55163 (HIGH – HTTP/2 MadeYouReset DDoS)
- Upgraded `commons-lang3` from `3.17.0` to `3.18.0` (BOM override):
  - CVE-2025-48924 (MEDIUM – uncontrolled recursion / StackOverflowError DoS)
- Upgraded `org.apache.mina:mina-core` from `2.2.3` to `2.2.4`:
  - CVE-2024-52046 (CRITICAL – deserialization RCE via ObjectSerializationDecoder)
- Upgraded `commons-io:commons-io` from `2.11.0` to `2.14.0`:
  - CVE-2024-47554 (HIGH – XmlStreamReader CPU exhaustion DoS)
- Upgraded `org.apache.httpcomponents.client5:httpclient5` from `5.4.1` to `5.4.3` (BOM override):
  - CVE-2025-27820 (HIGH – PSL validation bug disables domain checks for cookies and host name verification)
- Upgraded `net.minidev:json-smart` from `2.5.1` to `2.5.2` (BOM override):
  - CVE-2024-57699 (HIGH – uncontrolled recursion / stack exhaustion DoS on deeply nested JSON)
- Upgraded `com.nimbusds:nimbus-jose-jwt` from `9.47` to `10.0.2` (BOM override):
  - CVE-2025-53864 (MEDIUM – uncontrolled recursion DoS via deeply nested JSON in JWT claim set)
- Upgraded `io.minio:minio-admin` (and all MinIO artifacts) from `8.5.7` to `8.6.0`:
  - CVE-2025-59952 (HIGH – XML tag value substitution exposes system properties and environment variables)
- Upgraded `org.apache.sshd` suite from `2.11.0` to `2.14.0` (latest stable, resolves vulnerability warnings reported by IntelliJ IDEA)
- Upgraded `org.assertj:assertj-core` from `3.26.3` to `3.27.7` (BOM override):
  - CVE-2026-24400 (HIGH – XXE vulnerability in `isXmlEqualTo`/`XmlStringPrettyFormatter` allows arbitrary file read, SSRF and DoS via untrusted XML input)
- Added spotbugs plugin configuration to parent `pom.xml` with default parameters and suppression file
  
### Changed

- Extracted all hardcoded dependency versions into properties in the parent `pom.xml`
- Moved all third-party dependency version definitions into `<dependencyManagement>` section in parent `pom.xml`
- Added `okhttp.version` property (`4.12.0`) and explicit `dependencyManagement` entry for `com.squareup.okhttp3:okhttp`
- Split testcontainers versioning into two properties:
  - `testcontainers.version=2.0.3` – core artifact (required for Docker Desktop compatibility)
- Added `spring.boot.version`, `tomcat.version`, `jackson.version`, `netty.version`, `commons-lang3.version` properties for BOM override transparency
- Added explicit `value` attribute to all `@PathVariable` annotations across all controllers — required by Spring Framework 6.x which no longer infers parameter names from bytecode without the `-parameters` compiler flag
- Removed unused `org.json:json`, `org.eclipse.parsson:parsson` and `jakarta.json-api` dependencies — no source code imports any of them; the entire codebase uses only Jackson (`com.fasterxml.jackson`) for JSON handling; removing `org.json` also eliminates the duplicate class warning at startup
- Updated release procedure

## [0.6.4-SNAPSHOT] - 24.12.2025.

### Changed

- Enhanced S3 configuration to support both MinIO and AWS S3 endpoints
- S3ClientProvider now handles AWS-specific client creation and caching
- BucketCredentials management adapted for AWS IAM users
- S3BucketProvisionService updated to work with both MinIO and AWS S3

## [0.6.3-SNAPSHOT] - 01.12.2025.

### Added

- Logic for deciding which S3 client to use - synchronous or asynchronous based on s3.upload.mode property 
or ApplicationProperty in Mongo
- New S3UploadStrategy and implementation classes for synchronous and asynchronous upload

### Changed

- Conditional GlobalSSLConfiguration creation based on ssl.enabled property (FTP impact)
- Improved logic for async S3 Multipart upload - now using CompletableFuture.supplyAsync for each part upload
- testcontainers version bumped to 2.0.3 (docker 4.53.0 compliance)

## [0.6.0-SNAPSHOT] - 28.11.2025.

### Added

- Support for new Dataspace Protocol version 2025-1
- New GitHub Action (manually triggered) to run TCK tests against running connector
- Documentation how to run TCK tests using CMD and GitHub Action
- Terraform for deploying connector to Kubernetes cluster locally
- GitHub Action tests for Contract Negotiation with counteroffer flow

### Changed

- Renamed integration test classes to have IT suffix
- Configured maven surefire and failsafe plugins to have different includes for unit and integration tests

## [0.5.6-SNAPSHOT] - 24.11.2025.

### Added

- Support for TLS communication (enabling TLS for OkHttpClient and S3 clients)
- Scripts for generating self-signed certificates for connector
- Documentation for setting up TLS communication between connectors

## [0.5.5-SNAPSHOT] - 31.10.2025.

### Changed

- GitHub Copilot suggestions before release

## [0.5.4-SNAPSHOT] - 24.09.2025.

### Added

- GHA Build update, branches can also have fix/ prefix

### Changed

- Fixed serializers to have correct error message in case of validation error (missing dspace prefix in some cases)
- Serializers now throw ValidationException instead of e.printStackTrace()

## [0.5.3-SNAPSHOT] - 18.09.2025.

### Added

- Logic for static serving /.well-known/dspace-version document
- New properties for DSpace version document; stored in database; logic for retrieving and parsing properties
- New set of GitHub Action that will be used to test Connector related features

## [0.5.2-SNAPSHOT] - 17.09.2025.

### Added

- Added HTTP-Push transfer format
- Added HTTP-Push transfer diagram

### Changed

- Since the consumer won't know when the data will be uploaded, the COMPLETED state will be used as the indicator.
  After successfully finishing the upload, the provider will send a TransferProcessCompleted message.
  The isDownloaded flag will remain in the code for the moment but might be removed in the future.
- Changed HTTP-Pull transfer diagram

## [0.5.1-SNAPSHOT] - 08.08.2025.

### Changed

- Updated release GitHub action to use new versioning scheme

## [0.4.11-SNAPSHOT] - 04.08.2025.

### Added

- Created validateProtocol methods for Catalog model classes
- Added additional checks for Catalog protocol endpoint to ensure that all required properties are set

## [0.4.10-SNAPSHOT] - 29.07.2025.

### Changed

- Added check if bucket already exists, does have proper credentials, and create ones that are missing
- Updated junit tests

## [0.4.9-SNAPSHOT] - 25.07.2025.

### Added

- Added pagination for API endpoints (AuditEvent, ContractNegotiation, TransferProcess)

### Changed

- Refactored default Spring publisher, using AuditEventPublisher instead
- Updated junit tests
- Updated integration tests for pagination
- Postman collection updated with new endpoints and pagination

## [0.4.8-SNAPSHOT] - 16.07.2025.

### Added

- Added new API endpoint to fetch all AuditEventTypes
- Proper handling for GenericFilter timestamp query parameters

## [0.4.7-SNAPSHOT] - 11.07.2025.

### Added

- AuditEvent document and CRUD classes for storing audit events in MongoDB
- Covered Contract Negotiation flow with AuditEvent
- Covered Data Transfer flow with AuditEvent

### Changed

- Added publish AuditEvent for DataTransfer events and PolicyDecision
- GenericDynamicFilterRepository for repositories that require dynamic filtering

## [0.4.6-SNAPSHOT] - 08.08.2025.

### Added

- TransferProcessRepositoryCustom and TransferProcessRepositoryCustomImpl for custom queries for additional filtering
- GenericFilterBuilder for building custom filters directly from request

### Changed

- Updated TransferProcessApiController and TransferProcessService to use TransferProcessRepositoryCustom and additional
  filtering
- Updated tests and add new ones related to new implementation

## [0.4.5-SNAPSHOT] - 04.07.2025.

### Added

- New event and logic for logging DataTransfer events

### Changed

- DataTransferAPIController.downloadData is now async - response with code 202 is returned and download is done in
  background
- Refactored DataTransferStrategy and implementing classes to return CompletableFuture<Void> for transfer method
- GeneratePresignURL uses BucketCredentials
- Renamed DataTranferMockObjectUtil to DataTransferMockObjectUtil
- Updated TransferProcessApiController and TransferProcessService to use TransferProcessRepositoryCustom and additional
  filtering
- Updated tests and Postman collection

## [0.4.4-SNAPSHOT] - 03.07.2025.

### Added

- When creating S3 bucket, a policy for restricting access will be created
- S3 client providers, with caching, for creating clients with different configurations
- IAM user management for S3 bucket access
- New dependency - io.minio:minio-admin:8.5.7 for managing IAM users for MiniIO
- Encrypting secretKey in BucketCredentialsEntity
- Created S3ServerException

### Changed

- MinIO version minio/minio:RELEASE.2025-04-22T22-12-26Z - has extended UI interface
- Move deleteBucket to S3BucketProvisionService
- Updated tests

## [0.4.3-SNAPSHOT] - 20.06.2025.

### Added

- 3 new fields in ApplicationProperty, needed for UI

## [0.4.2-SNAPSHOT] - 19.06.2025.

### Added

- added try-catch block to handle exceptions in DataTransferApiService, when creating DataAddress with presigned URL
- On Catalog request, filter out datasets serving files still uploading

## [0.4.1-SNAPSHOT] - 18.06.2025.

### Changed

- Refactored HttpPullTransferStrategy to use S3ClientService.uploadFile
- S3ClientService.uploadFile now closes stream after upload
- Updated junit tests

## [0.3.2-SNAPSHOT] - 10.06.2025.

### Added

- Support for MinIO as an external storage for artifacts
- New properties for S3 storage configuration:
    - `s3.endpoint=http://localhost:9000`
    - `s3.accessKey=minioadmin`
    - `s3.secretKey=minioadmin`
    - `s3.region=us-east-1`
    - `s3.bucketName=dsp-true-connector-provider`
    - `s3.externalPresignedEndpoint=http://localhost:9000`
- Service for S3 storage operations
- Artifact as files are uploaded into S3 bucket and dataset points to the S3 file
- DataAddress contains S3 presigned URL for download
- Consumer transfer uses chunked download and upload from S3 via presigned URL

### Changed

- MongoDataLoader renamed to InitialDataLoader

### Removed

- Removed old artifact logic, now using S3 for file storage

## [0.1.6-SNAPSHOT] - 24.04.2025.

### Added

- Created and Modifed date fields set to Instant.now on creation

### Changed

- Removed PDF and JSON as distributions and using HTTP Data PULL

## [0.1.5-SNAPSHOT] - 23.04.2025.

### Changed

- Updated documentation, fixed typos and added missing information

## [0.1.4-SNAPSHOT] - 02.04.2025.

### Added

- New policy related classes in Negotiation module (domain, evaluators, service classes; PIP, PDP, PEP, PAP)
- New Location and Purpose service
- Added 2 new constraints: SPATIAL and PURPOSE (for now simple evaluation from property file; should be changed with
  claims)
- 2 new properties for location and purpose

```
application.usagecontrol.constraint.location=EU
application.usagecontrol.constraint.purpose=demo
```

- Added audit fields on ContractNegotiation and TransferProcess

### Changed

- Rewired policy evaluation to newly added logic

### Removed

- Old services and validators for count and dateTime

## [0.1.3-SNAPSHOT] - 28-03-2025

### Changed

- fixed issue when Content-Disposition header is not present in the response

## [0.1.2-SNAPSHOT] - 20-03-2025

### Added

- New GitHub Action script to trigger develop merge and increase version number
- New GitHub Action script for doing release (set release version, create tag, set next develop version, create dev PR)

## [0.1.1] - 14-03-2025

### Changed

- switched from embedded mongodb to dockerized mongodb with test containers for integration tests

## [0.1.1] - 14-03-2025

### Added

- OCSP logic for validating TLS certificate
- OCSP documentation and how to
- TLS properties for connector; default connector setup is http
- New connector-a, connector-b and truststore files

## [0.1.1] - 10-03-2025

### Changed

- separated execution of integration tests (use mvn clean verify
  for [building](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html) and testing)

## [0.1.1] - 03-03-2025

### Added

- Added integration tests for distribution and dataservice API
- Added multiple junit tests for Tools module

## [0.1.1] - 27-02-2025

### Changed

- Updated build sequence, enforce checkstyle on build, fails if checkstyle is not OK
- Updated javadoc to pass checkstyle
- Bumped version of embedded mongodb to 7.0.12 (same as Dockerized)

## [0.1.1] - 26-02-2025

### Added

- ApplicationPropertyChangedEvent and listener

### Changed

- Moved daps properties from property file and using only from Mongo
- Moved protocol.authentication.enabled from property file and using it from Mongo
- Update property expects List of properties
- Postman collection updated

### Removed

- ApplicationProperties endpoint for single property

## [0.1.1] - 21-02-2025

### Changed

- Removed catalog data from test scope initial_data.json
- All IT inserts data before using it/verify logic and does cleanup after test
- Force buffer flush for get external data and transfer file

## [0.1.1] - 19-02-2025

### Added

- Added authorization for external artifact

## [0.1.1] - 10-02-2025

### Added

- Added download and view endpoints to Data Transfer API

## [0.1.1] - 28-01-2025

### Changed

- Deleting the dataset now deletes also the artifact

## [0.1.1] - 27-01-2025

### Changed

- Artifact logic is directly tied to dataset (you can't insert a dataset without an artifact)

## [0.1.1] - 15-01-2025

### Added

- Added delete artifact

## [0.1.1] - 09-01-2025

### Changed

- Rename serializers to match module they belong to (Tools/Catalog/Negotiation/TransferSerializer)
- ToolsSerializer does not use JacksonAnnotationIntrospector
- Moved InstantSerializer and InstantDeserializer to tools module
- Serializer must have following ordering for modules - .addModules(new JavaTimeModule(), instantConverterModule)

## [0.1.1] - 08-01-2025

### Added

- Spring profile for reading initial data

### Changed

- Refactor integration tests, split into separate classes
- Excluded *.json files from target jar archive

## [0.1.1] - 31-12-2024

### Added

- Added external data download

### Changed

- Now using artifact object for storing file and external metadata and using it on data download

## [0.1.1] - 27-12-2024

### Added

- New password strength validation properties
- Logic for reading password validation properties and configuring validator
- Tools Serializer - deserializePlain(String jsonStringPlain, TypeReference<T> typeRef)

## [0.1.1] - 20-12-2024

### Added

- User CRUD endpoints and logic (create user, find, update user and update password)
- Tools module - ExceptionApiAdvice handler
- org.passay:passay:1.6.6 and password strength check library
- GHA API user test cases

## [0.1.1] - 28-11-2024

### Added

- Added API Data Transfer GHA tests

### Changed

- Added role check on Data Transfer start message protocol and API endpoints to prevent consumer from sending start
  message after request message instead of provider

### Removed

- Removed protocol Data Transfer GHA tests

## [0.1.1] - 27-11-2024

### Changed

- Moved integration tests into packages
- ContractNegotiationAPIException accepts ContractNegotiationErrorMessage as parameter
- ContractNegotiationAPIService when error happens, create ContractNegotiationErrorMessage
- Dependency changed from de.flapdoodle.embed.mongo.spring30x to de.flapdoodle.embed.mongo.spring3x
- DataTransferAPIException accepts TransferError as parameter
- DataTransferApiService when error happens, create TransferError (requestTransfer)
- TransferProcess and ContractNegotiation sets correct id when deserialized from plain string

### Added

- Wiremock to simulate provider in integration requests
- DataTransferApi tests

## [0.1.1] - 22-11-2024

### Changed

- ContractNegotiation.callbackAddress on consumer side value set to provider address
- Terminate negotiation API request - based on role, it sends to provider protocol address or consumer callback address

### Added

- Provider handleTermination request

## [0.1.1] - 21-11-2024

### Added

- Added role filter to GET Transport Process API endpoint

### Changed

- Initiate transfer process API endpoint now uses initialized transfer processes
- Download now uses data from DB (not the hardcoded John Doe)

## [0.1.1] - 19-11-2024

### Changed

- Changed proxy requests to be POST instead GET (because of mandatory body)
- GenericApiResponse.timestamp LocalDateTime to ZonedDateTime
- Updated postman collection

## [0.1.1] - 12-11-2024

### Added

- API proxy endpoints to fetch remote catalog and dct:format for dataset

### Changed

- Updated postman collection

## [0.1.1] - 07-11-2024

### Added

- TransferRequest initiate - protocol endpoint check if provided dct:format is supported by negotiated dataset
- OkHttpClient.sendInternalRequest

### Changed

- DataTransferFormat.HttpData-PULL (was before HTTP_PULL)
- GenericApiResponse.timestamp added @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
- Serializers added InstantSerializer, InstantDeserializer and JavaTimeModule

## [0.1.1] - 07-11-2024

### Added

- New property *application.usagecontrol.enabled=true*
- Logic for optional usageControl; feature can be turned on or off by setting the property

## [0.1.1] - 06-11-2024

### Changed

- Initiate transfer process protocol endpoint now uses initialized transfer processes

## [0.1.1] - 28-10-2024

### Added

- Added new INITIALIZE state for Transfer Processes
- Added logic for creating Transfer Process in INITIALIZE state
- Added API endpoints to get fileId and dct:format

## [0.1.1] - 24-10-2024

### Added

- Offer validation - check if offer.target == dataset.id

## [0.1.1] - 23-10-2024

### Added

- Upload and list artifacts that will be shared as dataset
- When verifying agreement additional check (policyEnforcement for agreement exists) added
- New CatalogErrorAPIException that translates to HTTP 400 response

## [0.1.1] - 20-10-2024

### Added

- Added logic for DataTransfer API

### Changed

- DataTransfer now checks that the Agreement exists and that it's linked to a FINALIZED Contract Negotiation

## [0.1.1] - 09-10-2024

### Added

- Initial logic for policy enforcement (count and dateTime as left operands)
- PolicyEnforcement model, repo and service classes that holds count for agreement
- PolicyManager - class that gets access count and update counter when artifact will be accessed
- AgreementAPI - enforceAgreement logic
- Event when accessing resource, used to increase count for agreementId

### Changed

- Operator TERM_LTEQ to LTEQ

## [0.1.1] - 04-10-2024

### Added

- New mandatory property application.protocol.authentication.enabled=true
- DataspaceProtocolEndpointsExceptionHandler - returns valid protocol error based on resource accessed
- ProtocolEndpointsAuthenticationFilter - filter that creates dummy authorization if security for protocol endpoints is
  disabled
- DataspaceProtocolEndpointsAuthenticationEntryPoint - custom authentication class to handle Spring Security errors in
  protocol way

## [0.1.0] - 13-09-2024

### Added

- Setup project structure (multimodule maven project:tools, catalog, negotiation, dataTransfer)
- Catalog protocol and API logic implementation (controller, service, model; junit and integration tests)
- Negotiation protocol and API logic implementation (controller, service, model; junit and integration tests) -
  Agreement enforcement is currently checking only if agreement is present, it does not check for constraints
- Data Transfer protocol logic implementation (controller, service, model; junit and integration tests) - REST pull
  implementation without authorization, with hardcoded value/artifact
- Postman collection for testing endpoints
- Configured GitHub actions to run tests

## [0.0.1] - 12-09-2024

### Added

- GHA test for API endpoints
- Distribution - format as reference

### Changed

- DataService is connector
- Plain serializers returns '@id'
- Postman collection updated
- generated identifiers have 'urn:uuid' as prefix (catalog, negotiation and dataTransfer)

### Removed

- removed servesDataset from DataService as per
  protocol (https://docs.internationaldataspaces.org/ids-knowledgebase/v/dataspace-protocol/catalog/catalog.protocol#id-1.1.3-data-service)

## [0.0.1] - 27-08-2024

### Added

- Added role (consumer or provider) to Contract Negotiation
- Added Agreement reference to to Contract Negotiation

### Removed

- Removed consumerPid and providerPid from Offer and Agreement

## [0.0.1] - xx-08-2024

### Added

- Junit tests to cover Catalog module classes java->String->java2 java.equals(java2)

### Changed

- Plain Serializer - JacksonAnnotationIntrospector to skip JsonProperty annotation
- Model classes implements Serializable
- Enum classes JsonCreator - create enum from String (plain and protocol string)
- Builder creates 'id' in "urn:uuid" + UUID.randomUUID() format if 'id' not present
- Collections reverted to Set

## [0.0.1] - 07-08-2024

### Added

- TransferTerminationMessage message provider and consumer callback logic (plus junit and integration tests)
- DataTransferConsumerCallbackTest - integration test class for consumer callback logic
- DataTransferApiTest - integration test for API logic

### Changed

- Agreement service (filter download url) in data transfer module sends request to check if agreement is valid

## [0.0.1] - 06-08-2024

### Added

- TransferSuspensionMessage message provider and consumer callback logic (plus junit and integration tests)
- Added TransferProcessChangeEvent and listener to log transition change
- DataTransferEventListener - placeholder logic for manipulating data transfers (start/stop/suspend)
- DataTransferFormat enum

### Changed

- Updated GitHub action to include suspend message in transfer artifact
- SFTP server starting on event published (TransferStartMessage from TransferRequestMessage.foramt=example:SFTP

## [0.0.1] - 02-08-2024

### Added

- New catalog API exceptions

### Changed

- Catalog API exceptions now wrapped in GenericApiResponse

### Removed

- Status code from GenericApiResponse

## [0.0.1] - 30-07-2024

### Added

- TransferCompletionMessage message provider and consumer callback logic (plus junit and integration tests)
- GitHub action to test request transfer, start, download artifact and send completion message

### Changed

- ROLE_CONNECTOR to fix authorization for protocol endpoints using jwt

## [0.0.1] - 2024-07-24

### Added

- Added CORS configuration

### Changed

- Moved some common service logic to BaseService
- Renamed APIs to be REST compliant

### Removed

- Removed transformers from Negotiation module

## [0.0.1] - 2024-07-22

### Added

- TransferStartMessage logic for provider and consumer callback (controller and service layer)
- DataTransfer API controller, service, junit and integration tests (get TransferProcess, by state and all)
- Negotiation module - API endpoint for agreement check (valid or not)
- AbstractTransferMessage implements Serializable

## Updated

- Code coverage (junit and integration)
- TransferRequestMessage - call to negotiation for agreement validity check before proceeding
- Negotiation module - renamed ModelUtil to MockObjectUtil (aligned with other modules)
- postman collection

## [0.0.1] - 2024-07-12

### Added

- provider endpoint and logic for initiating data transfer
- junit and integration tests
- GHA for data transfer request

### Changed

- updated Postman collection for /transfers/request

## [0.0.1] - 2024-07-10

### Added

- dockerized the application
- added integration tests to GHA

### Changed

- certificate private key password now used from application.properties

## [0.0.1] - 2024-07-09

### Added

- Added API endpoints for accepting and declining negotiation from provider side
- Added API endpoint for finding contract negotiations by state or all

### Changed

- Reviewed negotiation flow
- Updated postman collection

## [0.0.1] - 2024-06-28

### Changed

- updated verified and finalized states in negotiation module
- separated consumer and provider callback addresses in negotiation module

## [0.0.1] - 2024-06-25

### Added

- model, service and repository to manage Application Properties
- API controller to expose services
- Configuration @Component class to insert application.properties entries in Mongodb at startup

### Changed

- Postman collection and enviroment (with new API)
- initial_data.json (adding application_properties)
-

## [0.0.1] - 2024-06-25

### Added

- DataTransfer Consumer callback controller and junit tests

## [0.0.1] - 2024-06-21

### Added

- dataTransfer module, POC for REST pull artifact
- duplicate TransferProcess and ContractNegotiation with new status
- DataService, update method
- DataTransfer exception advice

### Removed
