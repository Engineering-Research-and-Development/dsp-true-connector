# D-ARC-004 — Trusted issuers come from configuration and the tenant registry

## Metadata
- Status: Accepted (interim trust scope superseded, see the note below)
- Date: 2026-10-30
- Owner: Security / authentication
- Reviewers: —
- Confidence: High
- Supersedes: —
- Superseded by: —
- Tags: security, keycloak, multi-tenant, jwt
- Risk Level: High

## Context
In `KEYCLOAK` mode the connector used to trust one issuer, configured with
`spring.security.oauth2.resourceserver.jwt.issuer-uri` (the former `dsp-connector` realm). It now has
a `platform` realm for machine-to-machine access, a `connector-admin` realm for super-admins and one
realm per tenant, and tenants are created and disabled at runtime. A fixed issuer can't serve this,
and a restart per tenant change is not acceptable. A common shortcut, a decoder that reads `iss`
from the token and fetches keys from it, would let anyone who can mint a token point the connector
at a server they control. Tenant isolation and the fail-closed behavior depend on who decides what
is trusted.

## Update (2026-11-02)
The interim trust scope (the "Interim trust scope" paragraph in the Decision below) no longer applies. 
trusts the `platform` issuer, with checks: protocol routes accept `platform` tokens only and require
`aud`, `azp` (`application.keycloak.platform-client-id`) and the `CONNECTOR` realm role; the admin
chain trusts tenant and `platform` issuers, and `KeycloakRealmRoleConverter` is issuer-aware, so a
`platform` token gets `ROLE_CONNECTOR` only (403 on `/api/**`) and tenant realms never get it. Tenant
tokens on protocol routes get 401. `connector-admin` is still untrusted until. The rest of
this decision (issuers from configuration and the tenant registry, `iss` only selects, per-issuer
discovery and JWKS, per-request tenant lookup) stands. Current behavior: [doc/security.md](../../security.md#trust-scope-per-chain).

## Update (2026-11-03)
`connector-admin` is trusted on the admin chain as well, and every issuer type now has its own `aud`, `azp`, role
and `tenant_id` rules, with a wrong-realm privileged role answered by 401 and an audit event. The "Interim trust
scope" paragraph and the "Trust `platform` and `connector-admin` immediately" alternative below are history.
See [D-ARC-006](D-ARC-006-per-realm-role-allow-list-and-per-tenant-realm-credentials.md).

## Decision
The connector trusts only issuers derived from its own configuration and tenant registry. A trusted
issuer is `<application.keycloak.base-url>/realms/<realm>` where the realm is the configured
`platform-realm` or `admin-realm`, or the `realm` of an enabled tenant. The `iss` of a token only
selects among these issuers (exact match, or a pattern-checked realm segment that an enabled tenant
holds); it is never used to build a URL, and an unknown issuer is rejected with 401 before any
network request. Tenant realms are names, never URLs, and `Tenant.realm` is validated (pattern,
length, reserved names, uniqueness). Signing keys come from each issuer's own discovery document
and JWKS, cached per issuer. The tenant lookup is made on every request and its result is not cached.

Interim trust scope: the `KEYCLOAK` filter chain trusts tenant issuers only. Tokens from `platform`
and `connector-admin` get 401 until the routes check `aud`, `azp` and a per-realm role allow-list,
because `KeycloakRealmRoleConverter` maps any role from any realm. Tenant tokens
must already carry `aud` `dsp-true-connector`, and a tenant token holding the `SUPER_ADMIN` or
`CONNECTOR` realm role is rejected as a whole.

## Alternatives Considered
- **Trust any issuer named in the token and fetch its keys (dynamic discovery from `iss`)** →
  rejected because it lets a token's author choose the key source, and gives server-side request
  forgery through the discovery and JWKS fetches.
- **Static list of issuers in configuration** → rejected because tenants are created, disabled and
  moved at runtime, and each change would need a restart and a config edit.
- **Cache the issuer resolution for a time** → rejected for now because a disabled tenant would keep
  working for the cache lifetime. The lookup is a single indexed query; only decoders are cached.
- **Trust `platform` and `connector-admin` immediately** → rejected because the role converter would
  accept any role from any realm, so one realm's token could open another realm's routes.

## Rationale
Deriving every fetched URL from `base-url` and a validated realm name keeps the trust decision in
operator-controlled data. Per-request lookup makes tenant lifecycle changes take effect at once with
no staleness window beyond the request. Per-issuer discovery, a 5 minute JWKS cache with
refresh-ahead and a rate-limited refetch on an unknown `kid` handle key rotation without a restart
and without letting bad `kid` values drive traffic to Keycloak. Asymmetric algorithms only,
no redirects and a response size limit narrow what a faulty or hostile endpoint can do. See
`TrustedIssuerRegistry`, `KeycloakDiscoveryClient`, `KeycloakTrustProperties` and `TenantService`
realm validation.

## Consequences

### Positive
- A token can't make the connector contact a server the operator did not configure.
- Tenant create, enable, disable, delete and realm change apply on the next request, with no restart.
- Key rotation is picked up automatically; startup does not need Keycloak to be up.

### Negative
- One tenant lookup per request in `KEYCLOAK` mode.
- `base-url` must equal the host in the token `iss` (`KC_HOSTNAME`), which needs care on k3s.
- Platform (M2M) and super-admin tokens were rejected on all routes (both now trusted, see the updates above).

### Risks
- `base-url` and `KC_HOSTNAME` disagree → every token gets 401; the log names the issuer mismatch and
  `doc/security.md` has the troubleshooting steps.
- Two tenants claiming one realm → prevented by the 409 uniqueness check; seeds bypass validation and
  are covered by a consistency test.
- A later slice trusts `platform` or `connector-admin` without the `aud`/`azp` and role checks →
  the registry's validator hook and the tenant-only scope in `ConnectorSecurityConfig` make this an
  explicit change.

## Related
- Decisions: [D-ARC-006](D-ARC-006-per-realm-role-allow-list-and-per-tenant-realm-credentials.md) (per-realm rules), [D-ARC-003](D-ARC-003-no-login-proxy-in-keycloak-mode.md), [D-TEC-004](../technical/D-TEC-004-keycloak-user-registration.md)
- Docs: [doc/security.md](../../security.md#trusted-issuers), [doc/multi-tenant-technical.md](../../multi-tenant-technical.md)
- Tickets: #26, #27, #28, #29, #30, #31, #32
