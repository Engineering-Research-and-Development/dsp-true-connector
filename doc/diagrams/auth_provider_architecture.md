# Authentication Provider Architecture Diagrams

## System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Application Startup                                 │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  AuthenticationModeResolver                                                  │
│                                                                              │
│  Reads: application.auth.provider = KEYCLOAK | BASIC | DISABLED             │
│         application.auth.dcp.enabled = true | false                         │
│                                                                              │
│  Validates:                                                                  │
│    ✓ DISABLED + dcp.enabled=true → startup error                            │
│    ✓ Missing property → defaults to KEYCLOAK                                │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  ConnectorSecurityConfig — Four Ordered SecurityFilterChain Beans           │
│                                                                              │
│  @Order(0)  Internal chain /internal/**  (loopback calls, ROLE_INTERNAL)    │
│             In-memory-key token in every mode; permitAll() when DISABLED    │
│                                                                              │
│  @Order(1)  Admin chain    /api/**  /actuator/**  /env                      │
│             Always uses auth.provider — never DCP                           │
│                                                                              │
│  @Order(2)  Protocol chain /connector/**  /catalog/**                       │
│                            /negotiations/**  /transfers/**                  │
│             Uses DCP when dcp.enabled=true, otherwise auth.provider         │
│                                                                              │
│  @Order(3)  Default chain  /**   → permitAll()                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                          ┌───────────┴────────────┬───────────────┐
                          ▼                        ▼               ▼
          ┌───────────────────────┐  ┌──────────────────┐  ┌────────────────┐
          │ KEYCLOAK mode         │  │ BASIC mode        │  │ DISABLED mode  │
          │ ─────────────         │  │ ──────────        │  │ ─────────────  │
          │ KeycloakAuthFilter    │  │ HTTP Basic Auth   │  │ permitAll()    │
          │ JWT validation        │  │ UserDetailsService│  │ (no auth)      │
          │ KeycloakRoleConverter │  │ (MongoDB)         │  │                │
          └───────────────────────┘  └──────────────────┘  └────────────────┘
```

---

## Package Structure

```
tools/src/main/java/it/eng/tools/auth/
│
├── 📦 [Root Package]
│   ├── AuthenticationMode.java                (enum: KEYCLOAK, BASIC, DISABLED)
│   ├── AuthenticationModeResolver.java        (resolves provider + dcp.enabled)
│   ├── M2mTokenCache.java                     (outbound token cache, keyed per client and endpoint)
│   └── M2mTokenUnavailableException.java      (outbound token could not be obtained)
│
├── 📦 condition/
│   ├── KeycloakAuthenticationModeCondition.java
│   ├── BasicAuthenticationModeCondition.java
│   ├── BasicOrDisabledAuthenticationModeCondition.java
│   └── DcpEnabledCondition.java
│
├── 📦 internal/
│   ├── InternalCallTokenIssuer.java           (token for loopback calls, in-memory key)
│   └── InternalCallClaims.java                (claim names and constants)
│
└── 📦 keycloak/
    ├── KeycloakAuthenticationService.java     (client-credentials token from the platform discovery document)
    └── KeycloakAuthenticationProperties.java  (@ConfigurationProperties, application.keycloak.backend.*)

connector/src/main/java/it/eng/connector/configuration/
│
├── ConnectorSecurityConfig.java               (unified security config)
├── KeycloakAuthenticationFilter.java          (JWT Bearer validation)
├── KeycloakRealmRoleConverter.java            (realm_access.roles → authorities)
├── DcpAuthenticationFilter.java               (stub — future DCP validation)
└── DataspaceProtocolEndpointsAuthenticationEntryPoint.java
```

---

## Property Resolution Flow

```
┌───────────────────────────────────────────────────────────────┐
│  1. Application Startup                                        │
└───────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌───────────────────────────────────────────────────────────────┐
│  2. AuthenticationModeResolver                                │
│                                                                │
│     Read: application.auth.provider                           │
│                                                                │
│           ├─ "KEYCLOAK" ──────────────────► KEYCLOAK          │
│           ├─ "BASIC"    ──────────────────► BASIC             │
│           ├─ "DISABLED" ──────────────────► DISABLED          │
│           └─ not set   ───────────────────► KEYCLOAK (default)│
│                                                                │
│     Read: application.auth.dcp.enabled                        │
│           ├─ true + DISABLED ─────────────► startup error     │
│           ├─ true  ────────────────────────► dcpEnabled=true  │
│           └─ false / not set ──────────────► dcpEnabled=false │
└───────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌───────────────────────────────────────────────────────────────┐
│  3. Conditional Bean Creation                                 │
│                                                                │
│     @Conditional(KeycloakAuthenticationModeCondition)         │
│       → KeycloakAuthenticationFilter                          │
│       → KeycloakRealmRoleConverter                            │
│       → TrustedIssuerRegistry + issuer-selecting JwtDecoder   │
│       → KeycloakAuthenticationService (+ M2mTokenCache)       │
│                                                                │
│     @Conditional(BasicAuthenticationModeCondition)            │
│       → DaoAuthenticationProvider                             │
│       → AuthenticationManager                                 │
│                                                                │
│     @Conditional(BasicOrDisabledAuthenticationModeCondition)  │
│       → UserService  (UserDetailsService, MongoDB-backed)     │
│       → UserApiController  (/api/v1/users active)             │
│                                                                │
│     @Conditional(DcpEnabledCondition)                         │
│       → DcpAuthenticationFilter (stub)                        │
└───────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌───────────────────────────────────────────────────────────────┐
│  4. ConnectorSecurityConfig builds filter chains              │
│     and application is ready                                  │
└───────────────────────────────────────────────────────────────┘
```

---

## Security Filter Chain Architecture

```
                    ConnectorSecurityConfig
                    ┌───────────────────────────────────────────┐
                    │  Four SecurityFilterChain beans           │
                    │                                           │
                    │  @Order(0) internalFilterChain            │
                    │    matcher: /internal/**                  │
                    │    auth:    internal-call token           │
                    │             loopback peer only, all modes │
                    │                                           │
                    │  @Order(1) adminFilterChain               │
                    │    matcher: /api/** /actuator/** /env      │
                    │    auth:    always auth.provider           │
                    │                                           │
                    │  @Order(2) protocolFilterChain            │
                    │    matcher: /connector/** /catalog/**      │
                    │             /negotiations/** /transfers/** │
                    │    auth:    DCP (if dcp.enabled=true)     │
                    │             else auth.provider            │
                    │                                           │
                    │  @Order(3) defaultFilterChain             │
                    │    matcher: /**                           │
                    │    auth:    permitAll()                   │
                    └───────────────────────────────────────────┘
                                        │
              ┌─────────────────────────┼─────────────────────────┐
              ▼                         ▼                         ▼
┌─────────────────────┐   ┌──────────────────────┐   ┌─────────────────────┐
│ KEYCLOAK mode       │   │ BASIC mode            │   │ DISABLED mode       │
│ ─────────────────── │   │ ──────────────────    │   │ ─────────────────── │
│ anonymous disabled  │   │ anonymous disabled    │   │ permitAll()         │
│ KeycloakAuthFilter  │   │ httpBasic(...)        │   │ (all chains)        │
│   ↓ JWT decode      │   │   ↓ UserDetailsService│   │                     │
│   ↓ role conversion │   │   ↓ MongoDB lookup    │   │ /api/v1/users       │
│   ↓ SecurityContext │   │   ↓ SecurityContext   │   │ still active        │
│ anyRequest()        │   │ anyRequest()          │   │                     │
│   hasRole(ADMIN)    │   │   hasRole(ADMIN)      │   │                     │
│   hasRole(CONNECTOR)│   │   hasRole(CONNECTOR)  │   │                     │
│                     │   │                       │   │                     │
│ 401 → DSP-format    │   │ 401 → DSP-format      │   │                     │
│   JSON error body   │   │   JSON error body     │   │                     │
└─────────────────────┘   └──────────────────────┘   └─────────────────────┘
```

---

## Request Flow — KEYCLOAK Mode

```
┌─────────────────────────────────────────────────────────────────┐
│                    Incoming HTTP Request                         │
│               Authorization: Bearer <jwt>                       │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
               ┌───────────────────────────────┐
               │ 1. CORS Filter                │
               │    Check origin, add headers  │
               └───────────────────────────────┘
                               │
                               ▼
               ┌───────────────────────────────┐
               │ 2. Security Headers Filter    │
               │    X-Content-Type-Options     │
               │    X-XSS-Protection, HSTS     │
               └───────────────────────────────┘
                               │
                               ▼
               ┌───────────────────────────────┐
               │ 3. KeycloakAuthFilter         │
               │    Extract Bearer token       │
               │    Registry selects issuer    │
               │    from unverified iss, then  │
               │    decode + validate sig/exp  │
               │    Protocol chain: platform   │
               │    only (aud, azp, CONNECTOR) │
               │    Admin chain: tenant,       │
               │    connector-admin, platform  │
               │    (per-issuer aud/azp/role/  │
               │    tenant_id rules; a role of │
               │    another realm: 401 + audit)│
               └───────────────────────────────┘
                               │
                               ▼
               ┌───────────────────────────────┐
               │ 4. KeycloakRealmRoleConverter │
               │    Extract realm_access.roles │
               │    Allow-list per issuer type │
               │    Tenant: ROLE_ADMIN         │
               │    connector-admin:           │
               │      ROLE_SUPER_ADMIN         │
               │    Platform: ROLE_CONNECTOR   │
               │    only (so 403 on /api/**)   │
               │    other roles are dropped    │
               └───────────────────────────────┘
                               │
                               ▼
               ┌───────────────────────────────┐
               │ 4b. ApiTenantContextFilter    │
               │    (/api/**, both modes)      │
               │    tenant = tenant_id claim   │
               │    X-Tenant-Id: super-admin   │
               │    only (400 unknown/disabled,│
               │    403 any other principal)   │
               └───────────────────────────────┘
                               │
                               ▼
               ┌───────────────────────────────┐
               │ 5. Authorization Check        │
               │    /api/**      → ROLE_ADMIN  │
               │    /connector/**→ROLE_CONNECTOR│
               │    /catalog/**  → ROLE_CONNECTOR│
               └───────────────────────────────┘
                              │
               ┌──────────────┴──────────────┐
               ▼ Authorized                  ▼ Unauthorized
┌─────────────────────────┐   ┌──────────────────────────────┐
│ Controller Method       │   │ AuthenticationEntryPoint     │
│ Handle request          │   │ → DataspaceProtocol          │
│ Return response         │   │   ExceptionHandler           │
└─────────────────────────┘   │ → DSP-format 401 JSON body   │
                              └──────────────────────────────┘
```

---

## Request Flow — BASIC Mode

```
┌─────────────────────────────────────────────────────────────────┐
│                    Incoming HTTP Request                         │
│          Authorization: Basic <base64(user:password)>           │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
               ┌───────────────────────────────┐
               │ 1. CORS / Security Headers    │
               └───────────────────────────────┘
                               │
                               ▼
               ┌───────────────────────────────┐
               │ 2. BasicAuthenticationFilter  │
               │    Decode Base64 credentials  │
               │    UserDetailsService lookup  │
               │    (MongoDB via UserService)  │
               │    Password validation        │
               │    Build SecurityContext      │
               └───────────────────────────────┘
                               │
                               ▼
               ┌───────────────────────────────┐
               │ 3. Authorization Check        │
               │    /api/**      → ROLE_ADMIN  │
               │    Protocol/**  → ROLE_CONNECTOR│
               └───────────────────────────────┘
                               │
               ┌──────────────┴──────────────┐
               ▼ Authorized                  ▼ Unauthorized
┌─────────────────────────┐   ┌──────────────────────────────┐
│ Controller Method       │   │ AuthenticationEntryPoint     │
└─────────────────────────┘   │ → DSP-format 401 JSON body  │
                               └──────────────────────────────┘
```

---

## Authentication Matrix

```
┌──────────────────┬──────────────┬──────────────────────────┬─────────────────────────────────┐
│ auth.provider    │ dcp.enabled  │ /api/** /actuator/**     │ Protocol endpoints¹             │
├──────────────────┼──────────────┼──────────────────────────┼─────────────────────────────────┤
│ KEYCLOAK         │ false        │ Keycloak JWT → ROLE_ADMIN│ Keycloak JWT → ROLE_CONNECTOR   │
│ KEYCLOAK         │ true         │ Keycloak JWT → ROLE_ADMIN│ DCP stub → ROLE_CONNECTOR       │
│ BASIC            │ false        │ HTTP Basic → ROLE_ADMIN  │ HTTP Basic → ROLE_CONNECTOR     │
│ BASIC            │ true         │ HTTP Basic → ROLE_ADMIN  │ DCP stub → ROLE_CONNECTOR       │
│ DISABLED         │ false        │ permitAll()              │ permitAll()                     │
│ DISABLED         │ true         │ startup error ❌         │ startup error ❌                │
└──────────────────┴──────────────┴──────────────────────────┴─────────────────────────────────┘
¹ /connector/** /catalog/** /negotiations/** /transfers/**
```

---

## Deployment Scenarios

### Scenario 1: Keycloak Authentication

```
┌─────────────────────────────────────────────────────────────┐
│                     Docker Compose / Kubernetes             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────┐       ┌──────────────────────┐   │
│  │  Keycloak            │       │  DSP Connector       │   │
│  │  Port: 8180          │◄──────┤  Port: 8080          │   │
│  │  Realms:             │  JWT  │                      │   │
│  │   platform           │ Tokens│  application.        │   │
│  │   connector-admin    │       │  auth.provider=      │   │
│  │   tenant-<name>      │       │  KEYCLOAK            │   │
│  │                      │       │                      │   │
│  │  Tenant admin        │       │  keycloak.base-url=  │   │
│  │    → ROLE_ADMIN      │       │  http://keycloak:8180│   │
│  │  platform client     │       │  (issuer registry)   │   │
│  │    → ROLE_CONNECTOR  │       └──────────────────────┘   │
│  └──────────────────────┘                                   │
│                                                              │
│  Flow: User → Keycloak Login → JWT Token → Connector API   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

Instead of one fixed issuer, the connector keeps a trusted-issuer registry:

```
Bearer token ──► read unverified iss (selects only, never fetched)
                      │
                      ▼
        ┌───────────────────────────────────────────────┐
        │ TrustedIssuerRegistry                         │
        │  static : <base-url>/realms/platform          │
        │           <base-url>/realms/connector-admin   │
        │  dynamic: <base-url>/realms/<tenant.realm>    │
        │           (enabled tenants, looked up on      │
        │            every request, no cache)           │
        └───────────────────────────────────────────────┘
          unknown issuer ──► 401, no network request
                      │ known
                      ▼
        per-issuer cached decoder (loaded on first use):
          GET <issuer>/.well-known/openid-configuration
          GET jwks_uri  (cached 5 min, refetch on unknown kid)
          verify signature, exp, nbf, exact iss
```

Each chain accepts a different set of issuers (see [security.md](../security.md#trusted-issuers)):

| Chain | Trusted issuers | Extra checks |
|-------|-----------------|--------------|
| Protocol (`/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**`) | `platform` only | `aud`, `azp` equals `application.keycloak.platform-client-id`, realm role `CONNECTOR`; anything else, including tenant and super-admin tokens, gets 401 |
| Admin (`/api/**`, `/actuator/**`, `/env`) | tenant realms, `connector-admin` and `platform` | Per issuer type, see the next table. A `platform` token is converted to `ROLE_CONNECTOR` only, so it gets 403 |
| Internal (`/internal/**`) | none from Keycloak | Internal-call token signed with an in-memory key, `ROLE_INTERNAL`, loopback peer |

Rules the admin chain applies per issuer type (the same ones, with `platform` fixed to `CONNECTOR`, guard the protocol chain):

```
                       aud        azp                 allowed role   tenant_id
 tenant realm        ─ audience ─ ui-client-id ────── ADMIN        ─ required, = tenant of the realm
 connector-admin     ─ audience ─ ui-client-id ────── SUPER_ADMIN  ─ absent
 platform            ─ audience ─ platform-client-id─ CONNECTOR    ─ absent

 privileged role of ANOTHER realm in the token ─► 401 + SECURITY_REALM_ROLE_VIOLATION audit
 (role names alone are never trusted; the issuing realm decides)
```

Tenant context and delegation (`ApiTenantContextFilter`, `/api/**`, both modes):

```
 token tenant_id ───────────────► tenant context (tenant users)
 super-admin, no X-Tenant-Id ───► no tenant (cross-tenant view)
 super-admin + X-Tenant-Id ─────► enabled tenant: delegated, TENANT_DELEGATION audit
                                  unknown/disabled tenant: 400
 any other principal + X-Tenant-Id ► 403
```

Users (`KEYCLOAK` mode): the connector has no user API (`/api/v1/users/**` answers 404 for a
super-admin). Super-admins live in `connector-admin`, tenant users in their tenant's realm with a
`tenant_id` equal to the tenant `_id`; both are managed in Keycloak.

See [D-ARC-006](../decisions/architecture/D-ARC-006-per-realm-role-allow-list-and-per-tenant-realm-credentials.md).

The UI signs in to Keycloak directly (authorization code + PKCE) and logs out through Keycloak. The
connector only validates the resulting tokens: in `KEYCLOAK` mode it has no login, refresh, logout
or password-change route (see [security.md](../security.md#getting-tokens)).

### Scenario 2: Basic Authentication (Standalone)

```
┌─────────────────────────────────────────────────────────────┐
│                     Docker Compose / Kubernetes             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────┐       ┌──────────────────────┐   │
│  │  MongoDB             │       │  DSP Connector       │   │
│  │  Port: 27017         │◄──────┤  Port: 8080          │   │
│  │                      │ User  │                      │   │
│  │  Collections:        │ Lookup│  application.        │   │
│  │  - users             │       │  auth.provider=      │   │
│  │    (email, password, │       │  BASIC               │   │
│  │     roles)           │       │                      │   │
│  └──────────────────────┘       │  /api/v1/users       │   │
│                                 │  (user management)   │   │
│                                 └──────────────────────┘   │
│                                                              │
│  Flow: User → Basic Auth Header → MongoDB Lookup →         │
│        Connector API                                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Scenario 3: Two Connectors with Keycloak

```
┌─────────────────────────────────────────────────────────────────┐
│                     Production Environment                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────┐                   │
│  │  Keycloak  (Port: 8180)                  │                   │
│  │  Realm: platform                         │                   │
│  │  Client: dsp-connector-backend           │                   │
│  │  (tenant realms and connector-admin      │                   │
│  │   serve the UI and are not used here)    │                   │
│  └──────────┬──────────────────────┬────────┘                   │
│             │ client credentials   │ client credentials         │
│             │ token (CONNECTOR)    │ token (CONNECTOR)          │
│             ▼                      ▼                            │
│  ┌─────────────────────┐  ┌─────────────────────┐              │
│  │  Consumer Connector │  │  Provider Connector │              │
│  │  Port: 8080         │  │  Port: 8090         │              │
│  │                     │  │                     │              │
│  │  auth.provider=     │  │  auth.provider=     │              │
│  │  KEYCLOAK           │  │  KEYCLOAK           │              │
│  │                     │  │                     │              │
│  │                     │  │                     │              │
│  └──────────┬──────────┘  └─────────────────────┘              │
│             │                       ▲                           │
│             │  DSP Protocol         │                           │
│             │  (Bearer token from   │                           │
│             │   client credentials) │                           │
│             └───────────────────────┘                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

Each connector requests its token from the token endpoint in the discovery document of the `platform`
realm, caches it until 30 seconds before it expires, and sends it as the Bearer token. The receiving
connector accepts it only when `aud`, `azp` and the `CONNECTOR` role match (see
[security.md](../security.md#protocol-trust-in-keycloak-mode)). If the token request fails, the protocol
call is not sent (see [security.md](../security.md#outbound-connector-to-connector-token)).

### Scenario 4: Loopback calls on `/internal`

```
┌────────────────────────────── one connector JVM ────────────────────────────┐
│                                                                              │
│  negotiation / data-transfer service                                         │
│    InternalCallTokenIssuer: new token per call                               │
│      HMAC-SHA256, key from SecureRandom at startup, memory only              │
│      sub=internal-call  roles=[ROLE_INTERNAL]  tenant_id  exp=+60 s          │
│        │                                                                     │
│        │  HTTP to localhost, Authorization: Bearer <internal-call token>     │
│        ▼                                                                     │
│  @Order(0) internal chain  /internal/**                                      │
│    verify signature, iss, exp (5 s skew)      → 401 if not an internal token │
│    require ROLE_INTERNAL                      → 403 if missing               │
│    require loopback TCP peer (not X-Forwarded-For) → 403 otherwise           │
│    tenant context from the token's tenant_id (X-Tenant-Id is never read)     │
│        │                                                                     │
│        ▼                                                                     │
│  /internal/v1/offers/validate  /internal/v1/agreements/{id}/enforce          │
│  /internal/v1/datasets/{id}/formats  /internal/v1/datasets/{id}/artifact     │
└──────────────────────────────────────────────────────────────────────────────┘
```

The zone works the same in `INTERNAL` and `KEYCLOAK` mode and needs no configuration. See
[D-ARC-005](../decisions/architecture/D-ARC-005-internal-call-zone-with-in-memory-key.md).

### Scenario 5: Outbound protocol token in `INTERNAL` mode

```
┌─ connector A ──────────────────────────┐          ┌─ connector B ─────────────────────────┐
│ CredentialUtils.getConnectorCredentials│          │ INTERNAL protocol chain               │
│   M2mTokenCache slot "connector-m2m"   │          │   InternalJwtAuthenticationFilter     │
│     miss → InternalProtocolTokenIssuer │          │     verify HS256 with its own         │
│       JwtService.issueProtocolToken()  │          │     application.security.jwt.secret   │
│       HS256, application.security.     │  HTTP    │     (must equal A's)                  │
│       jwt.secret                       │ ───────▶ │   require ROLE_CONNECTOR              │
│       sub=connector                    │ Bearer   │     → 401 invalid, 403 other role     │
│       realm_access.roles=[CONNECTOR]   │          │   principal name = email, or sub      │
│       iat exp jti (no email, no tenant)│ ◀─────── │                                       │
│   no login, no user lookup             │ 401: A   │                                       │
│                                        │ evicts   │                                       │
│                                        │ the slot │                                       │
│                                        │ and      │                                       │
│                                        │ retries  │                                       │
│                                        │ once     │                                       │
└────────────────────────────────────────┘          └───────────────────────────────────────┘
```

The token is not tied to a user, so no seeded connector account is needed. It differs from the
internal-call token of Scenario 4 in key, authority, audience and caching; see
[security.md](../security.md#protocol-token-vs-internal-call-token).

---

---

## Configuration Reference

```properties
# Required — KEYCLOAK | BASIC | DISABLED  (default: KEYCLOAK)
application.auth.provider=KEYCLOAK

# Optional DCP override for protocol endpoints (/connector/** /catalog/** etc.)
# Cannot be combined with DISABLED.
# application.auth.dcp.enabled=false
```

### KEYCLOAK Mode

```properties
application.auth.provider=KEYCLOAK

application.keycloak.base-url=http://localhost:8180
application.keycloak.audience=dsp-true-connector
application.keycloak.platform-realm=platform
application.keycloak.admin-realm=connector-admin

# Only accepted on protocol routes: the azp of the platform token
application.keycloak.platform-client-id=dsp-connector-backend

# The azp of tenant and connector-admin tokens (the UI's public client)
application.keycloak.ui-client-id=keycloak-api

# Outbound client (platform realm); the token endpoint comes from the realm's discovery document
application.keycloak.backend.client-id=dsp-connector-backend
application.keycloak.backend.client-secret=dsp-connector-backend-secret
application.keycloak.backend.token-caching=true
```

The connector exposes no user API in this mode (`/api/v1/users/**` answers 404); users are created and changed in Keycloak.

### BASIC Mode

```properties
application.auth.provider=BASIC
```

Users are stored in MongoDB and seeded from `initial_data.json` on startup.
`/api/v1/users` endpoints are active for user management.

### DISABLED Mode

```properties
application.auth.provider=DISABLED
```

All endpoints open — `permitAll()`. **Do not use in production.**

---

## What Changed from Previous Architecture

### Removed

| Removed | Reason |
|---------|--------|
| `WebSecurityConfig` | Replaced by `ConnectorSecurityConfig` |
| `KeycloakSecurityConfig` | Replaced by `ConnectorSecurityConfig` |
| `DapsAuthenticationService` | DAPS authentication removed |
| `DapsAuthenticationProperties` | DAPS authentication removed |
| `DapsCertificateProvider` | DAPS authentication removed |
| `JwtAuthenticationFilter` | Replaced by `KeycloakAuthenticationFilter` |
| `JwtAuthenticationProvider` | Replaced by `KeycloakAuthenticationFilter` |
| `JwtAuthenticationToken` | Replaced by Spring's `JwtAuthenticationToken` |
| `DataspaceProtocolEndpointsAuthenticationFilter` | Replaced by filter chain URL matching |
| `LegacyAuthenticationModeCondition` | LEGACY mode removed |
| `NonKeycloakAuthenticationModeCondition` | Replaced by `BasicOrDisabledAuthenticationModeCondition` |
| DAPS SSL bundle properties | No longer needed |

### Added

| Added | Description |
|-------|-------------|
| `ConnectorSecurityConfig` | Unified config with 4 ordered `SecurityFilterChain` beans (internal, admin, protocol, default) |
| `AuthenticationMode.BASIC` | New auth mode enum value |
| `BasicAuthenticationModeCondition` | Condition: active when `provider=BASIC` |
| `BasicOrDisabledAuthenticationModeCondition` | Condition: active when `provider=BASIC` or `DISABLED` |
| `DcpEnabledCondition` | Condition: active when `dcp.enabled=true` |
| `DcpAuthenticationFilter` | Pass-through stub for future DCP JWT validation |

---

*For full authentication documentation see [doc/security.md](../security.md) and
[KEYCLOAK_INTEGRATION_COMPLETE_SUMMARY.md](../../KEYCLOAK_INTEGRATION_COMPLETE_SUMMARY.md).*
