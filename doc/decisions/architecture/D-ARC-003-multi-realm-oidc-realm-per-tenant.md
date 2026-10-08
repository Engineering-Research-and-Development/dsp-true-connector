# D-ARC-003 — Multi-realm OIDC with one identity-provider realm per tenant

## Metadata
- Status: Proposed
- Date: 2026-10-05
- Owner: TRUE Connector team
- Reviewers: —
- Confidence: Medium
- Supersedes: —
- Superseded by: —
- Tags: security, keycloak, oidc, multi-tenancy, authentication, authorization
- Risk Level: High

## Context

The connector runs as a single, centrally deployed, multi-tenant instance. Tenants are MongoDB
`Tenant` documents (`tools/src/main/java/it/eng/tools/model/Tenant.java`). DSP protocol endpoints
are already tenant-scoped by path (`/{tenantId}/catalog`, `/{tenantId}/negotiations`,
`/{tenantId}/transfers`, resolved by `TenantAwareProtocolController`). Tenant-to-tenant DSP traffic
can happen inside the same instance.

The target environments run an externally operated Keycloak with **one realm per tenant** and one
**platform realm** for cross-tenant services. Integrators get one confidential client per realm. They
don't get Keycloak admin access by default, but the Keycloak instance can be tailored to the
connector's needs (roles, claims).

The existing `KEYCLOAK` mode can't work against that setup:

- **Single issuer.** It trusts exactly one issuer (`spring.security.oauth2.resourceserver.jwt.issuer-uri`
  in `ConnectorSecurityConfig`) and doesn't validate `aud`.
- **Tenant from a custom claim.** The tenant comes from a non-standard `tenantId` JWT claim
  (`ApiTenantContextFilter`).
- **One static client.** Outbound tokens come from a single hand-written `client_credentials` call
  (`KeycloakAuthenticationService` + `AuthenticationCache`) against one static token URL.
- **Single-realm login proxy.** Login is proxied through `/api/v1/auth/login` to one realm with the
  password grant (`KeycloakAuthServiceImpl`).
- **Assumes admin rights.** User administration needs realm admin rights in one realm
  ([D-TEC-004](../technical/D-TEC-004-keycloak-user-registration.md)).

Constraints:

- **DSP 2025-1 compliance must not regress.** The TCK, CI and Newman suites run in `INTERNAL` mode.
- **Tenant isolation is a security boundary.** A token from one tenant's realm must never act as
  another tenant or as super-admin.
- **The backend login proxy (`/api/v1/auth/*`) must stay,** because the UI is optional and other
  clients rely on it.
- **No environment-specific names or values** may appear in code, default configuration or seed data.

## Decision

In `KEYCLOAK` mode the connector trusts several realms of one Keycloak server. Each enabled
`Tenant` is bound to exactly one realm through a new nullable `Tenant.realm` field, and one
configured platform realm represents tenant-less super-admin/system identities. The tenant of a
request is derived from the verified token issuer, never from a custom claim.

Concretely:

1. **Inbound validation.**
   - Spring Security's `JwtIssuerAuthenticationManagerResolver` checks the issuer against an
     allowlist made of `{base-url}/realms/{Tenant.realm}` for enabled tenants plus
     `{base-url}/realms/{platform-realm}`.
   - Unknown issuers are rejected before any discovery or JWKS request.
   - Each trusted issuer gets its own lazily created decoder that validates signature, issuer,
     expiry and a configurable audience (`aud`).
2. **Tenant resolution.** The issuer is mapped to `Tenant.id`. Platform-realm tokens carry no tenant
   and keep the existing `X-Tenant-Id` delegation for super-admins.
3. **Authorization.**
   - Keycloak is configured to emit a top-level `roles` claim whose values are already the connector's
     authorities (`ROLE_ADMIN`, `ROLE_CONNECTOR`, `ROLE_SUPER_ADMIN`). Spring's stock
     `JwtGrantedAuthoritiesConverter` reads it directly, so the custom `KeycloakRealmRoleConverter`
     and any role-mapping configuration are removed.
   - There is no extra guard restricting `ROLE_SUPER_ADMIN` to the platform realm: the identity
     provider is trusted to emit authorities correctly. Tenant-header delegation stays limited to
     tenant-less (platform-realm) tokens, and tenant-realm tokens stay pinned to their tenant.
4. **Outbound tokens.**
   - Spring `OAuth2AuthorizedClientManager` handles token acquisition, with one client registration
     per trusted realm (same client id, per-realm secret).
   - DSP protocol calls use the acting tenant's realm.
   - The connector's calls to its own `/api/**` endpoints (`OkHttpRestClient.sendInternalRequest`)
     use the platform realm.
5. **Login proxy.** `/api/v1/auth/login|refresh|logout` stay. The request carries an explicit
   `tenant` field that selects `Tenant.realm`; when it's absent, the platform realm is used.
   Every login or refresh failure (unknown, disabled or realm-less tenant, missing or wrong
   credentials) returns the same generic 401, so tenant existence and status are never disclosed.
   Causes are logged server-side only.
   Tokens obtained directly from a trusted realm are accepted the same way as tokens obtained
   through the proxy.
6. **Client secrets.**
   - The platform realm secret stays in configuration/environment
     (`application.keycloak.platform-client-secret`) so the connector can bootstrap.
   - Tenant realm secrets are stored encrypted in MongoDB (`realm_credentials`, keyed by tenant id,
     using the existing `@Encrypted`/`FieldEncryptionService` mechanism). They are set through
     `PUT /api/v1/tenants/{id}/realm-credentials` (super-admin only), validated with a real
     client-credentials request, write-only, and exposed only as `credentialsConfigured`. Setting
     them evicts the realm's cached client registration and tokens. Changing `Tenant.realm` or
     deleting the tenant deletes the credentials. This allows onboarding and rotation without a
     restart.
7. **User administration** through the Keycloak Admin REST API stays available behind
   `application.keycloak.user-admin.enabled` (default `false`). `GET /api/v1/users/me` is always
   available and shared by both auth modes (one controller and DTO behind a `CurrentUserProvider`
   interface with an `INTERNAL` and a `KEYCLOAK` implementation). It returns only non-sensitive data:
   `username`, `firstName`, `lastName`, `email`, `tenantId` and `Tenant.name`. It returns no roles
   (the UI decodes them from the JWT). Tenant-less identities get `super-admin` as tenant id and name,
   and that tenant id is reserved.
8. **Unchanged:** `INTERNAL` and `DISABLED` modes, and DCP as an independent protocol-auth toggle.

## Alternatives Considered

- **One connector deployment per tenant realm** → rejected: the target is a single central
  multi-tenant instance. Per-tenant deployments would duplicate infrastructure and make the
  existing tenant model and per-tenant S3 isolation pointless.
- **A single platform realm, with the tenant carried in a custom claim, group or client role** →
  rejected: the identity provider doesn't add custom claims, the tenant would depend on
  per-user mapper configuration instead of a cryptographic boundary, and consumer-tenant identity
  on DSP calls would be lost.
- **Tenant derived from the realm name by naming convention (`<prefix><tenantId>`)** → rejected:
  it couples tenant ids to identity-provider naming and leaks the realm format into tenant ids.
  An explicit `Tenant.realm` field is clearer and allows arbitrary names.
- **Static list of trusted realms in properties** → rejected: it duplicates tenant data and lets
  the realm allowlist drift from tenant enablement.
- **Tenant client secrets in configuration/environment** → rejected: static properties can't change at
  runtime, so every onboarding or rotation would need a restart. Only the platform secret stays there.
- **Client secrets stored encrypted on the `Tenant` document** → rejected: a separate
  `realm_credentials` collection keeps secrets out of tenant payloads, audit diffs and normal reads.
- **Configurable role claim and role-name mapping, or a `ROLE_SUPER_ADMIN` platform-only guard** →
  rejected: Keycloak can be tailored to emit final authorities, so the extra configuration and
  converter are unnecessary.
- **Replacing the password-grant proxy with authorization code + PKCE only** → rejected: non-UI
  clients depend on the backend-mediated login contract documented in `doc/security.md`. Tokens
  from authorization-code flows are still accepted, so the option stays open.
- **Keeping the hand-written `client_credentials` client and `AuthenticationCache`** → rejected:
  it supports only one realm, and Spring's authorized-client manager already provides per-registration
  caching, expiry handling and refresh.

## Rationale

Binding each tenant to its own realm makes the identity provider's realm boundary the tenant
boundary. A token signed by one realm's keys can't claim another realm's issuer, so tenant
resolution relies on signature verification instead of mutable claims. Checking the issuer against
the allowlist before contacting the identity provider stops the dynamic-trust risk that issuer-based
multi-tenancy otherwise has.

Deriving the allowlist from enabled `Tenant` documents keeps MongoDB the single source of truth:
disabling a tenant immediately revokes its realm. Spring Security already provides issuer-based
resolution and an authorized-client manager (`spring-boot-starter-oauth2-resource-server` and
`spring-boot-starter-oauth2-client` are already dependencies in `connector/pom.xml`), so no new
library is needed.

Keeping `INTERNAL` mode for CI, Newman and the TCK isolates DSP compliance from this change.
Keeping the login proxy and `/users/me` contract preserves the client-facing API.

## Consequences

### Positive
- The connector integrates with realm-per-tenant identity providers without custom claims or admin rights.
- Tenant isolation is enforced cryptographically by realm keys, not by a mutable claim.
- Consumer-tenant identity on DSP calls is available from the token issuer, which is the basis for
  future consumer-identity policies.
- `aud` validation and a fixed `roles` claim contract close gaps in the existing Keycloak mode.
- No new third-party dependency; the hand-written token client and `AuthenticationCache` are
  retired in `KEYCLOAK` mode.

### Negative
- `Tenant` gains a field, so all 12 `initial_data*.json` seed files must change together.
- Tenant realm secrets live (encrypted) in MongoDB, so a database compromise together with the
  encryption key exposes them. Onboarding a tenant needs a realm binding and a secret through the
  management API, without a restart.
- The configuration contract changes: the `application.keycloak.backend.*`, `login.*` and
  `admin.*` properties and the single `issuer-uri` are replaced.
- Login requests must name the tenant, and login failures are deliberately generic.
- `GET /api/v1/users/me` loses `roles` and the super-admin flag in both modes, a UI-visible breaking change.
- Keycloak must be configured to emit the top-level `roles` claim with final authority names.
- More moving parts in the security chain (issuer resolver, per-issuer decoders, per-realm client
  registrations), plus a new local multi-realm Keycloak seed and rewritten Keycloak integration tests.

### Risks
- **Allowlist bypass or dynamic-trust bugs could let a foreign issuer in.** Mitigation: exact-match
  issuer allowlist checked before any network call, plus integration tests for unknown issuer,
  disabled tenant, wrong audience and cross-realm role escalation.
- **Identity-provider outage or slow discovery.** Mitigation: lazy, cached per-issuer decoders and no
  identity-provider calls at startup.
- **A misconfigured Keycloak mapper emits `ROLE_SUPER_ADMIN` in a tenant realm.** Accepted trust
  assumption: the connector doesn't second-guess the authorities the identity provider emits.
  Mitigation: audit of mapper configuration with the identity-provider operator. The tenant is
  still derived from the issuer, so delegation stays tenant-less only.
- **A missing realm secret breaks login and outbound calls for a tenant.** Mitigation: startup WARN
  and the `credentialsConfigured` flag for tenants with a realm but no stored secret.
- **Tenant secrets in MongoDB.** Mitigation: field-level encryption, write-only API, a separate
  collection and validation on save.
- **Protocol regression.** Mitigation: the TCK profile and Newman suites keep running in `INTERNAL`
  mode and remain mandatory before merge.

## Related
- Decisions: [D-TEC-004](../technical/D-TEC-004-keycloak-user-registration.md) (user administration
  becomes optional and realm-aware), [D-ARC-002](D-ARC-002-provider-consumer-spring-profiles.md)
- Docs: [`doc/security.md`](../../security.md), [`doc/architecture.md`](../../architecture.md)
- Tickets: —
