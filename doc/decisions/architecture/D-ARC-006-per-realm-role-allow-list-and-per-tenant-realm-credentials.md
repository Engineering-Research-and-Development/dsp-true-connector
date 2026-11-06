# D-ARC-006 — Per-realm role allow-list with tenant binding, and per-tenant realm credentials

## Metadata
- Status: Accepted
- Date: 2026-11-03
- Owner: Security / authentication
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: security, keycloak, multi-tenant, jwt, secrets
- Risk Level: High
- Note: Retroactively documented (decided and built in K6, #11). Part (b) was withdrawn on 2026-11-06, see below.

> **Part (b) withdrawn (2026-11-06).** The Keycloak user listing it served (`GET /api/v1/users` in `KEYCLOAK`
> mode, `KeycloakUserService`, `KeycloakUserApiController`) was removed, and with it the per-tenant realm credentials
> (`PUT`/`DELETE /api/v1/tenants/{id}/realm-credentials`, `Tenant.clientSecret`, `credentialsConfigured`,
> `RealmCredentialsValidator`, `RealmCredentialsChangedEvent`), the `application.keycloak.user-management*` properties
> and the `connector-user-reader` seed clients. Users are managed in Keycloak only; the connector stores no Keycloak
> client secret per tenant. Part (a) stays in force. The text below is kept for history.

## Context
In `KEYCLOAK` mode the admin API accepts tokens from three kinds of issuer: tenant realms (tenant admins),
`connector-admin` (super-admins) and `platform` (machine-to-machine). Realms are administered
independently, and tenant realms are created at runtime, so the operator of one realm is not necessarily
trusted with the rights of another. Two earlier shortcuts do not hold up. Mapping every `realm_access.roles`
entry to `ROLE_<name>` lets anyone who can create a role in a tenant realm mint `SUPER_ADMIN` or `CONNECTOR`.
Reading the tenant from a token claim alone lets a token from one realm name another tenant's data.

Keycloak user listing has a second problem: it used the connector's machine-to-machine client
(`dsp-connector-backend`) and a broad admin account. That credential would have to be a super-user of every
tenant realm. The per-tenant secret it needs has to change at runtime when a tenant rotates it, without a
restart of the connector, and has to be checked before it is trusted.

## Decision
(a) Each issuer type has its own rule set, and the issuing realm, not the role name, decides which role
counts. A tenant realm may grant `ADMIN` and its `tenant_id` must equal the tenant that owns the realm;
`connector-admin` may grant `SUPER_ADMIN` and carries no `tenant_id`; `platform` may grant `CONNECTOR` and carries
no `tenant_id`. A token that holds a privileged role (`ADMIN`, `SUPER_ADMIN`, `CONNECTOR`, `INTERNAL`) of another
realm is rejected as a whole with 401 and a `SECURITY_REALM_ROLE_VIOLATION` audit event; other roles are dropped by a
per-issuer allow-list converter. The tenant context comes from the `tenant_id` claim in both modes, and
`X-Tenant-Id` is limited to super-admins without a tenant.

(b) *(Withdrawn, see the note above.)* The client secret of the per-realm user-listing client (`connector-user-reader`) is stored encrypted on the
`Tenant` record, set through `PUT /api/v1/tenants/{id}/realm-credentials` after a real client-credentials request
has proven it, and used by a dedicated token cache in `KeycloakUserService`.

## Alternatives Considered
- **Generic mapping of every realm role to `ROLE_<name>`** → rejected because a role named `SUPER_ADMIN` or
  `CONNECTOR` created in any realm would grant that authority on routes of another realm.
- **Silently drop a wrong-realm privileged role and accept the token** → rejected because a realm that issues a
  privileged role it must not grant is misconfigured or compromised; the operator has to see it. A rejection with an
  audit event surfaces it, and a dropped role would hide it.
- **Trust the `tenant_id` claim alone** → rejected because the claim is only meaningful together with the issuer;
  the validator compares it to the tenant that owns the verified `iss`.
- **One connector-wide client with `view-users` rights in all tenant realms** (the earlier use of
  `dsp-connector-backend`) → rejected because it needs cross-realm rights and a leak exposes every tenant. A per-realm
  client limits a leak to one realm.
- **Store the realm client secrets in configuration (properties or environment)** → rejected because tenants are
  created and rotated at runtime and a configuration secret is read at startup, so each change would need a restart
  and a config edit per tenant. This is acceptable for the single `platform` client (see
  [security.md](../../security.md#m2m-client-secret-delivery-and-rotation)) but not for a per-tenant secret.
- **Do not validate the secret on save** → rejected because a typo would show up only on the first user listing.

## Rationale
Verifying the signature first and then applying rules per issuer type keeps the trust decision with operator-controlled
data (`base-url`, realm names, the tenant registry): `IssuerTokenValidators` is the single place for `aud`, `azp`, the
privileged-role check and the `tenant_id` binding, and `KeycloakRealmRoleConverter` keeps the allow-list should a decoder
ever lack those checks. The audit event is written only for a token with a verified signature and `iss`, so an
attacker cannot flood the log with forged tokens, and its details hold realm, issuer type, subject and path only.

Keeping the secret on `Tenant` with `FieldEncryptionService` (as for the S3 credentials) makes rotation a single API
call, applied at once by a `RealmCredentialsChangedEvent` that drops the cached token. The save is refused unless the
realm issues a token for the secret, so a stored secret is known to work at the time it is stored, and the secret is
never returned (`credentialsConfigured` only) or logged. The token endpoint comes from the discovery document of the realm
built from `base-url` and the realm name, and the users URL likewise, never from the tenant record or a token.
See `IssuerTokenValidators`, `KeycloakRealmRoleConverter`, `RealmRoleViolationAuditor`, `ApiTenantContextFilter`,
`TenantService`, `RealmCredentialsValidator` and `KeycloakUserService`. Verified by `KeycloakMultiIssuerApiIT`,
`KeycloakRealmRoleViolationIT`, `KeycloakTenantIsolationIT`, `TenantRealmCredentialsIT` and `KeycloakUserManagementIT`.

## Consequences

### Positive
- A role created in one realm can't open the routes of another; tampering is visible in the audit log.
- Tenant A's token can't act as tenant B, in either mode.
- A leaked `connector-user-reader` secret exposes the users of one realm, read-only.
- Rotating the realm secret needs no connector restart and no configuration change.

### Negative
- Realm roles of privileged names are reserved: a tenant realm that grants `CONNECTOR`, `INTERNAL` or `SUPER_ADMIN` to a
  user makes that user's tokens unusable (401).
- Each tenant realm must contain the `connector-user-reader` client (with `view-users` and a client scope mapping when
  `fullScopeAllowed` is false), and an operator has to store its secret per tenant before users can be listed (409 until
  then).
- Clients that send `X-Tenant-Id` must stop sending it for tenant users (403 now, also in `INTERNAL` mode).

### Risks
- A legitimate token is rejected after a realm role change: the audit event names the realm and issuer type.
- The encryption key of `FieldEncryptionService` is lost or changed: stored realm secrets can't be decrypted and user
  listing answers 409 until they are set again.
- An event that arrives while a token request is in flight may re-cache the old token until it expires (about 5 minutes);
  accepted.
- Realm role violations are audited only for tokens with a valid signature, so key compromise of a realm is not detectable
  from this event alone; the realm's own Keycloak event log remains the source for that.

## Related
- Decisions: [D-ARC-004](D-ARC-004-trusted-issuers-from-config-and-tenant-registry.md), [D-ARC-005](D-ARC-005-internal-call-zone-with-in-memory-key.md), [D-ARC-003](D-ARC-003-no-login-proxy-in-keycloak-mode.md)
- Docs: [doc/security.md](../../security.md#trust-scope-per-chain), [doc/security.md – User management](../../security.md#user-management), [doc/multi-tenant-user-guide.md](../../multi-tenant-user-guide.md#users-in-keycloak-mode)
- Tickets: #11, #51, #52, #53, #54, #55, #56
