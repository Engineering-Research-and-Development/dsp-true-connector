# ENDURANCE Keycloak integration — decision log & open questions

Prep notes for the ENDURANCE call. Status: grilling in progress (Q1–Q2b answered).

## Current state of the code (summary)

| Area | Today | Gap vs ENDURANCE |
|---|---|---|
| Issuers | One static issuer (`spring.security.oauth2.resourceserver.jwt.issuer-uri`), POC realm `dsp-connector` | Need several trusted issuers (`endurance-platform` and `endurance-<tenant>`) |
| Audience | No `aud` validation | ENDURANCE §5 requires checking `aud` |
| Roles | `KeycloakRealmRoleConverter` reads only `realm_access.roles` and expects `ADMIN`/`CONNECTOR`/`SUPER_ADMIN` | ENDURANCE roles are `reader`/`writer`/`executor`/`admin` and may be client roles |
| Tenant | Custom `tenantId` JWT claim (`ApiTenantContextFilter`) | Tenant should come from the realm in `iss` |
| Outbound M2M | Hand-written `client_credentials` call, one client, one token URL | One client per realm; pick the realm by acting tenant |
| Human login | Backend proxies the **password grant** (ROPC) in `KeycloakAuthServiceImpl` | ENDURANCE offers **auth-code** flows; ROPC is unlikely to be enabled |
| User admin | `KeycloakUserService` calls Keycloak Admin REST API (`/admin/realms/...`) | ENDURANCE grants **no admin access** |
| Consumer-identity policy | None (only DATE_TIME, SPATIAL, PURPOSE, COUNT evaluators) | Needed for per-tenant offer access |

## Decisions taken

| # | Question | Decision |
|---|---|---|
| Q1 | How do tenants map to connector deployments? | **One multi-tenant connector trusting several issuers.** Tenant = realm from `iss` (`endurance-eydap` → `eydap`). Platform-realm tokens = super-admin/service. Use standard ENDURANCE items only; no custom `tenantId` mapper. |
| Q2 | Who are the DSP counterparties? | **All inside ENDURANCE**; they use ENDURANCE service-account tokens. DCP stays an independent toggle. |
| Q2b | Which realm issues outbound protocol tokens? | **The acting tenant's realm** (fallback: `endurance-platform` for tenant-less calls). Lets the provider know the consumer tenant. |
| Q2b | Consumer-identity policy evaluator | **Deferred / out of scope.** See `keycloak-endurance-consumer-policy.md`. |
| Q3 | Role mapping | **Configurable mapping** (claim path + role → authority). Default: client roles on `dsp-true-connector` (`admin`→`ROLE_ADMIN`, `connector`→`ROLE_CONNECTOR`, platform `super-admin`→`ROLE_SUPER_ADMIN`). No read-only role. |
| Q5 | Human login | **Keep the backend login proxy** (`/api/v1/auth/login`, password grant) with an explicit `tenant` field (absent = `endurance-platform`). Refresh/logout are routed to the same realm. The UI is optional: any client can log in through the proxy or bring its own token from a trusted realm. **One confidential client `dsp-true-connector` per realm** (Direct Access Grants + service account + audience mapper). |
| Q6 | User admin in Keycloak mode | **Keep it, behind the flag `application.keycloak.user-admin.enabled`** (default `false`). Needs `view-users`/`manage-users` from ENDURANCE before it's enabled. |
| Q8 | Realm ↔ Tenant mapping | **New `Tenant.realm` field** (peer decision). Trusted issuers = `{keycloak-base-url}/realms/{realm}` of enabled tenants + `endurance-platform` (= tenant-less super-admin). Check `iss` against the allowlist *before* any JWKS fetch; one lazily loaded decoder per issuer. Seeds: no ENDURANCE values. |
| Q8c | `Tenant.realm` details | Realm **name** (issuer = `application.keycloak.base-url` + `/realms/{realm}`). Nullable, sparse unique index; null = not trusted for Keycloak. Platform realm = property `application.keycloak.platform-realm`. Seeds: `realm` null, except the Keycloak IT/docker setups (local realm per seed tenant). Login `tenant` → `Tenant.realm`; a missing realm or disabled tenant is rejected. |
| Q11 | Outbound tokens & secrets | **Spring `OAuth2AuthorizedClientManager`** with a custom `ClientRegistrationRepository` (one registration per enabled tenant realm + platform realm, built lazily from discovery); also used by the login proxy. Retire the hand-rolled client + `AuthenticationCache` in Keycloak mode. **Secrets via properties/env** (`application.keycloak.realms.<realm>.client-secret`) from one K8s Secret; onboarding = add secret + restart. Non-ENDURANCE environments provision realms/clients with IaC (Keycloak Terraform provider); the TC never holds Keycloak admin credentials for provisioning. |
| Q12 | Credentials for TC self-calls (`sendInternalRequest`) & protocol calls | Self-calls: **platform realm service account** (`super-admin` client role, no tenant) + `X-Tenant-Id`. Protocol calls: **acting tenant's realm service account** (`connector` role). In-process refactor tracked as tech debt. |
| Q13 | Configuration shape | `application.keycloak.{base-url, platform-realm, client-id (default dsp-true-connector), audience (default dsp-true-connector), realms.<realm>.client-secret, roles.claim, roles.mapping.<role>=<ROLE_X>, user-admin.enabled=false}` bound to a validated `@ConfigurationProperties` record. Old `backend.*`/`login.*`/`admin.*` + single `issuer-uri` removed. No Keycloak call at startup (lazy discovery/JWKS). `super-admin` honoured only from platform realm. WARN if a tenant realm has no secret. **No ENDURANCE-specific names/values in code, default config or seeds; use generic wording. ENDURANCE values only in deployment env/K8s Secret.** |
| Q14/15 | Modes & test environments | INTERNAL/DISABLED kept; CI, Newman, TCK stay INTERNAL. POC realm replaced by a generic local seed (`platform`, `tenant-a`, `tenant-b`, `dsp-true-connector` client, client roles, audience mapper, test users). Keycloak ITs rewritten for multi-realm. No Keycloak Newman CI job now. Manual smoke test against the ENDURANCE sandbox before go-live. |
| Q17 | ADR | **D-ARC-003** (Proposed) written: `doc/decisions/architecture/D-ARC-003-multi-realm-oidc-realm-per-tenant.md`. Next: functional slice + decomposition. |
| Q16 | DCP | Independent protocol-auth toggle; unchanged. |
| Q8b | Current-user info | **Keep `/me`**: reuse the existing `GET /api/v1/users/me` (today INTERNAL only), now also in KEYCLOAK mode, independent of the user-admin flag; as the source of truth: OIDC name/email claims + `tenantId` + `tenantName` (`Tenant.name`) + mapped roles + `superAdmin`. Same shape in INTERNAL mode. The UI may read only `exp` from the JWT. |
| Q4 | Audience | **Validate `aud` contains a configurable value** (default `dsp-true-connector`). Ask ENDURANCE for an audience mapper in every trusted realm. |

## ENDURANCE call outcomes (2026-10-01)

- **Deployment:** in-cluster.
- **Topology:** a **single centralized TC instance** with multiple tenants. There are no other connectors or TC instances, so DSP traffic is tenant ↔ tenant within one instance.
- **Roles:** both standard realm roles and client roles on our own client are possible.
- **Password grant (ROPC):** possible, as is auth-code.
- **Sandbox realms:** available.
- **Audience:** answer was about datasets shared by several tenants. The `aud` claim question is still open (see Q4).

Code check: protocol endpoints are already tenant-scoped (`/{tenantId}/catalog|negotiations|transfers`, `TenantAwareProtocolController`). Provider tenant = path; consumer tenant = token `iss`. Q9 is resolved.

## Questions to ask ENDURANCE on the call

### Intake (their §4)
1. **Deployment location:** in the ENDURANCE K8s cluster or external? *(our answer: TBD; see Q7)*
2. **Scope:** a client in `endurance-platform` **plus** one client in each tenant realm we serve. Which tenant realms? Is the list of 23 stable, and how are new tenants announced?
3. **Integration type:** **both**. We need a service account (`client_credentials`) for connector↔connector calls **and** human login for the connector admin GUI.
4. **Redirect URIs / web origins:** for the GUI (auth-code + PKCE). *(depends on Q5)*
5. **Permissions:** see the role questions below.
6. **Client id:** propose **`dsp-true-connector`**, the same id in every realm. One client per connector instance, or one shared id for all TRUE Connectors? (Shared id means peers can't be told apart by `azp`.)

### Tokens & validation
7. **Audience:** what `aud` value will our tokens carry? Can you add an **audience mapper** so tokens for peer connectors contain our client id (or a shared `dsp-connector` audience)?
8. When Connector-A's service account calls Connector-B, whose audience is in the token? Do we need one audience per peer, or one shared dataspace audience?
9. Token lifetimes (access/refresh), and is a **refresh token** issued for auth-code clients?
10. Is the **Direct Access Grant (password/ROPC)** allowed? (We assume **no**.)
11. Do tokens carry `azp`/`client_id` and `preferred_username`? Any other standard claims we can rely on (email, groups)?
12. Are realm settings standardized across all 23 tenant realms (same mappers, same role names)?

### Roles
13. Are `reader`/`writer`/`executor`/`admin` **realm roles** (`realm_access.roles`) or **client roles** (`resource_access.<client>.roles`)?
14. Can we get **client roles on our own client** (e.g. `connector`, `connector-admin`) instead of reusing the tenant-wide roles? Or should we map the standard roles?
15. Which platform-realm role should mean **super-admin across all tenants** for our connector?
16. Which role should a **peer connector's service account** have so we can grant `ROLE_CONNECTOR`?

### Users & admin
17. We currently list users through the Keycloak Admin API. That is not available to us, correct? Is user management fully handled by ENDURANCE?
18. Is there a **test/sandbox** setup (e.g. `endurance-platform` plus 1–2 test tenant realms with test users) we can use for integration testing and CI?

### Ops
19. **Secret delivery:** K8s Secret (if in-cluster) or a secrets manager? How many secrets (one per realm)? How does rotation work?
20. Logout: is RP-initiated logout (`end_session_endpoint`) enabled, and with which post-logout redirect URIs?
21. Are there **other DSP connectors** in ENDURANCE we will talk to? Who runs them, and what identity provider do they use?

## Questions still to settle internally (upcoming grilling)

| # | Topic | Recommendation (draft) |
|---|---|---|
| Q3 | Role mapping: ENDURANCE roles → `ROLE_ADMIN` / `ROLE_CONNECTOR` / `ROLE_SUPER_ADMIN` | Configurable mapping. Read both `realm_access` and `resource_access.<client-id>`. Tenant `admin` → `ROLE_ADMIN`; platform admin → `ROLE_SUPER_ADMIN`; service-account role → `ROLE_CONNECTOR`. |
| Q4 | Audience validation | Add a `JwtClaimValidator` for `aud` per issuer. Fail closed. |
| Q5 | Human login flow | ~~Drop ROPC~~ → decided: keep the proxy with tenant routing (see Decisions). |
| Q6 | `/api/v1/users` in Keycloak mode | Remove the Keycloak user admin (no admin access). Keep only "who am I" from the token. |
| Q7 | Deployment location (in-cluster vs external) | Needed for intake §4.1. Affects secret delivery and network. |
| Q8 | Realm → `Tenant` entity mapping | Static config list of trusted realms → tenant id (no auto-creation from tokens). Bucket provisioning stays in `InitialDataLoader`. |
| Q9 (resolved: path `/{tenantId}/...`) | Provider-side tenant on inbound protocol calls | Today protocol endpoints have **no tenant context**, so they fall back to the global bucket. Decide how the provider picks *its own* serving tenant (per-tenant protocol base path? catalog-level ownership?). **Must verify in code.** |
| Q10 (deferred, see consumer-policy md) | Consumer-identity policy evaluator design | New `LeftOperand` (e.g. consumer realm / `azp`). Inbound token identity is passed into negotiation policy evaluation. Decide on the ODRL representation and the value format (realm name vs tenant code). |
| Q11 | Outbound token acquisition | Replace the hand-written client with **Spring `OAuth2AuthorizedClientManager`** (`spring-boot-starter-oauth2-client`, already a dependency). One `client_credentials` registration per realm gives built-in caching/refresh. Retire `AuthenticationCache` for Keycloak. |
| Q12 | Multi-issuer validation | Spring `JwtIssuerAuthenticationManagerResolver` with an explicit trusted-issuer allowlist; discovery via issuer URI, loaded lazily so startup doesn't fail if Keycloak is down. |
| Q13 | Configuration shape | `application.endurance.realms[n].{name, tenantId, clientId, clientSecret}`, or standard `spring.security.oauth2.client.registration.<realm>.*`. Secrets from env/K8s Secret. |
| Q14 | Local dev / CI / Newman / TCK | Replace the POC `dsp-connector` realm with an ENDURANCE-like seed (`endurance-platform` + 2 tenant realms, same role names). Update docker-compose, Testcontainers IT, CI, terraform. |
| Q15 | INTERNAL / DISABLED modes | Keep for local/TCK? Or Keycloak-only? |
| Q16 | DCP coexistence | Keep as an independent protocol-auth toggle; out of scope for ENDURANCE. |
| Q17 | Docs / ADR | ADR for "multi-issuer OIDC with realm-as-tenant" + update `doc/security.md`; delete/replace `KEYCLOAK_INTEGRATION_COMPLETE_SUMMARY.md`. |
