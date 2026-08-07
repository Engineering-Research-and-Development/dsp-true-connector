# Security

## Overview

The DSP True Connector supports multiple authentication and security mechanisms:

1. **TLS/SSL** - Transport layer security for encrypted communication
2. **Keycloak OAuth2/OIDC** - Token-based authentication for production use (`KEYCLOAK` mode)
3. **Internal Authentication** - Username/password backed by MongoDB, with self-issued JWTs (`INTERNAL` mode)
4. **Disabled** - All endpoints open, for local development only (`DISABLED` mode)
5. **DCP** - Decentralized Claims Protocol for protocol endpoints (stub, future integration)
6. **OCSP** - Certificate validation and revocation checking

Both `KEYCLOAK` and `INTERNAL` modes expose the same unified, backend-mediated login contract —
`POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` — so that UI and
other clients never talk to an identity provider directly. See
[Unified Authentication Contract](#unified-authentication-contract-apiv1auth) below.

---

## TLS Configuration

Connector can operate both in http or httpS mode.
To enable https mode, certificates must be provided and following properties needs to be set with correct values:

```properties
## SSL Configuration
spring.ssl.bundle.jks.connector.key.alias = connector-a
spring.ssl.bundle.jks.connector.key.password = password
spring.ssl.bundle.jks.connector.keystore.location = classpath:connector-a.jks
spring.ssl.bundle.jks.connector.keystore.password = password
spring.ssl.bundle.jks.connector.keystore.type = JKS
spring.ssl.bundle.jks.connector.truststore.type=JKS
spring.ssl.bundle.jks.connector.truststore.location=classpath:truststore.jks
spring.ssl.bundle.jks.connector.truststore.password=password

server.ssl.enabled=true
server.ssl.key-alias=connector-a
server.ssl.key-password=password
server.ssl.key-store=classpath:connector-a.jks
server.ssl.key-store-password=password
```

Make sure to update values with correct one, provided keystore files are self signed and should not be used in production.

More information on how to generate keystore and truststore files can be found [here](./certificate/PKI_CERTIFICATE_GUIDE.md).

---

## Authentication Modes

The connector uses a single unified security configuration (`ConnectorSecurityConfig`) with three
ordered Spring Security filter chains — one for admin endpoints, one for protocol endpoints, and
one default chain.  Authentication behaviour is controlled by two properties:

```properties
# Primary authentication provider: KEYCLOAK | INTERNAL | DISABLED
application.auth.provider=KEYCLOAK

# Optional: route protocol endpoints through DCP instead of the provider above.
# Cannot be combined with provider=DISABLED.
# application.auth.dcp.enabled=false
```

### Authentication Matrix

| `auth.provider` | `dcp.enabled` | `/api/**` `/actuator/**` | Protocol endpoints¹ |
|-----------------|---------------|--------------------------|----------------------|
| `KEYCLOAK`      | `false`       | Keycloak-issued JWT (obtained via `/api/v1/auth/login`) → `ROLE_ADMIN` or `ROLE_SUPER_ADMIN`; `ROLE_SUPER_ADMIN` required for `/tenants/**`, most `/users/**`, `/properties/**`; `ROLE_ADMIN` may call `/users/me` and own-account PUT endpoints | Keycloak JWT → `ROLE_CONNECTOR` |
| `KEYCLOAK`      | `true`        | Keycloak-issued JWT (obtained via `/api/v1/auth/login`) → `ROLE_ADMIN` or `ROLE_SUPER_ADMIN`; `ROLE_SUPER_ADMIN` required for `/tenants/**`, most `/users/**`, `/properties/**`; `ROLE_ADMIN` may call `/users/me` and own-account PUT endpoints | DCP → `ROLE_CONNECTOR` |
| `INTERNAL`      | `false`       | Self-issued JWT (obtained via `/api/v1/auth/login`) → `ROLE_ADMIN` or `ROLE_SUPER_ADMIN`; `ROLE_SUPER_ADMIN` required for `/tenants/**`, most `/users/**`, `/properties/**`; `ROLE_ADMIN` may call `/users/me` and own-account PUT endpoints | Self-issued JWT → `ROLE_CONNECTOR` |
| `INTERNAL`      | `true`        | Self-issued JWT (obtained via `/api/v1/auth/login`) → `ROLE_ADMIN` or `ROLE_SUPER_ADMIN`; `ROLE_SUPER_ADMIN` required for `/tenants/**`, most `/users/**`, `/properties/**`; `ROLE_ADMIN` may call `/users/me` and own-account PUT endpoints | DCP → `ROLE_CONNECTOR` |
| `DISABLED`      | `false`       | `permitAll()` — all management endpoints open, not for production | `permitAll()` |
| `DISABLED`      | `true`        | ❌ startup error | ❌ startup error |

¹ Protocol endpoints: `/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**`

> **Note:** `provider=DISABLED` combined with `dcp.enabled=true` is explicitly rejected at startup
> with an `IllegalStateException`.

---

## Unified Authentication Contract (`/api/v1/auth/*`)

Both `KEYCLOAK` and `INTERNAL` modes expose the same three endpoints, all `permitAll()` and served
by `AuthController` (active whenever `application.auth.provider` is `KEYCLOAK` or `INTERNAL`):

| Endpoint | Request body | Response |
|----------|---------------|----------|
| `POST /api/v1/auth/login` | `{"email": "...", "password": "..."}` | `200 OK` with `LoginResponse` |
| `POST /api/v1/auth/refresh` | `{"refresh_token": "..."}` | `200 OK` with `LoginResponse` |
| `POST /api/v1/auth/logout` | `{"refresh_token": "..."}` | `200 OK`, empty body (idempotent) |

`LoginResponse` is a flat, snake_case, token-only JSON body mirroring a typical identity-provider
token response:

```json
{
  "access_token": "<JWT>",
  "refresh_token": "<opaque-or-Keycloak-issued-id>",
  "token_type": "Bearer",
  "expires_in": 900
}
```

**Why backend-mediated**: UI and other API clients never call Keycloak (or any identity provider)
directly. They only ever call the connector's own `/api/v1/auth/*` endpoints. In `KEYCLOAK` mode,
`AuthController` delegates to `KeycloakAuthServiceImpl`, which proxies the request to Keycloak's
token endpoint using the `application.keycloak.login.*` client (see
[Keycloak Authentication Mode](#keycloak-authentication-mode-keycloak) below) and re-shapes
Keycloak's response into the same `LoginResponse` contract. In `INTERNAL` mode, `AuthController`
delegates to `InternalAuthServiceImpl`, which validates credentials against MongoDB and mints a
self-signed JWT (see [Internal Authentication Mode](#internal-authentication-mode-internal)).
Either way, the client-facing contract never changes — swapping `application.auth.provider` between
`KEYCLOAK` and `INTERNAL` requires no client-side changes.

---

## Keycloak Authentication Mode (`KEYCLOAK`)

The recommended mode for production. The connector acts as an OAuth2/OIDC resource server,
validating JWTs issued by Keycloak, and as a backend-mediated proxy for the login/refresh/logout
contract described above.

### Enabling Keycloak

```properties
application.auth.provider=KEYCLOAK
```

### Configuration Properties

> **Two separate Keycloak clients are always used and must never be merged.** They serve
> different purposes, are configured under different property prefixes, and have different
> client types (public vs. confidential):

```properties
# JWT validation - Keycloak as resource server (both admin-zone and protocol-zone tokens
# are validated against this same realm/JWK set)
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/dsp-connector
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8180/realms/dsp-connector/protocol/openid-connect/certs

# --- UI login client: used ONLY by AuthController/KeycloakAuthServiceImpl to proxy
# --- POST /api/v1/auth/login|refresh|logout on behalf of human users (ROLE_ADMIN / ROLE_SUPER_ADMIN).
# --- Public client (resource-owner-password-credentials grant) - no secret required.
application.keycloak.login.client-id=dsp-connector-ui
#application.keycloak.login.client-secret=dsp-connector-ui-secret
application.keycloak.login.token-url=http://localhost:8180/realms/dsp-connector/protocol/openid-connect/token
application.keycloak.login.logout-url=http://localhost:8180/realms/dsp-connector/protocol/openid-connect/logout

# --- Backend service-account client: used ONLY by KeycloakAuthenticationService for
# --- connector-to-connector (M2M) protocol calls (ROLE_CONNECTOR), via client-credentials grant.
# --- Confidential client - requires a client secret.
application.keycloak.backend.client-id=dsp-connector-consumer-backend
application.keycloak.backend.client-secret=dsp-connector-consumer-secret
application.keycloak.backend.token-url=http://localhost:8180/realms/dsp-connector/protocol/openid-connect/token
application.keycloak.backend.token-caching=true
```

`application.keycloak.login.*` and `application.keycloak.backend.*` are independent
`@ConfigurationProperties` beans (`KeycloakLoginProperties` and `KeycloakAuthenticationProperties`)
with no shared state. Rotating the backend client's secret has no effect on UI login, and vice
versa.

### What Happens in Keycloak Mode

**Admin zone (`/api/**`)**:
- Requires `Authorization: Bearer <JWT>` with `ROLE_ADMIN` or `ROLE_SUPER_ADMIN`, obtained via
  `POST /api/v1/auth/login` (see [Unified Authentication Contract](#unified-authentication-contract-apiv1auth))
- Roles extracted from `realm_access.roles` claim in the JWT
- `/api/v1/users` returns **404** - user management is handled in Keycloak Admin Console

**Protocol zone (`/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**`)**:
- Requires `Authorization: Bearer <JWT>` with `ROLE_CONNECTOR`
- On authentication failure, returns a DSP-compliant error JSON (see
  `DataspaceProtocolEndpointsExceptionHandler`)

**Outbound connector-to-connector requests**:
- `AuthenticationCache` (via `KeycloakAuthenticationService`) acquires and caches a
  client-credentials token from the `application.keycloak.backend.*` client for M2M protocol calls

### Getting Tokens

Clients never call Keycloak directly. All tokens are obtained through the connector's own unified
endpoints:

**User login (proxies to the `application.keycloak.login.*` client)**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@test.com", "password": "<password>"}'
```

**Refresh**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token": "<refresh_token from login response>"}'
```

**Logout**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refresh_token": "<refresh_token from login response>"}'
```

Use the returned `access_token` as: `Authorization: Bearer <access_token>`. Connector-to-connector
(M2M) tokens are acquired transparently by `KeycloakAuthenticationService`/`AuthenticationCache`
using the `application.keycloak.backend.*` client - application code never requests these directly.

## Internal Authentication Mode (`INTERNAL`)

Use when Keycloak is not available. Users are stored in MongoDB and managed through
`/api/v1/users`. Both the admin zone and the protocol zone authenticate with a self-signed JWT
obtained via the [unified `/api/v1/auth/*` contract](#unified-authentication-contract-apiv1auth).

### Enabling Internal Mode

```properties
application.auth.provider=INTERNAL
```

### Configuration Properties

```properties
# HMAC-SHA256 secret used to sign/verify all INTERNAL-mode JWTs (admin, protocol, and M2M).
# Can be overridden via APPLICATION_SECURITY_JWT_SECRET environment variable. Must be >= 32 bytes.
application.security.jwt.secret=${APPLICATION_SECURITY_JWT_SECRET:connector-jwt-dev-secret-change-in-prod-min-32-bytes}
application.security.jwt.access-expiration-ms=900000
application.security.jwt.refresh-expiration-ms=604800000
```

> **`application.security.jwt.secret` must be identical across every connector instance that
> needs to trust each other's tokens** (e.g. connector A and connector B in a docker-compose or
> Kubernetes deployment). A mismatched secret causes every cross-connector protocol call to fail
> JWT signature validation with a 401.

### What Happens in Internal Mode

- Both admin and protocol zones require `Authorization: Bearer <JWT>`, obtained via
  `POST /api/v1/auth/login`
- `UserDetailsService` is backed by MongoDB (`UserService`)
- `/api/v1/users` endpoints are **active** — users created here are valid login credentials
- Initial users and data are seeded from `initial_data.json` on startup
- On authentication failure at protocol endpoints, returns a DSP-compliant error JSON
- Refresh tokens are opaque ids tracked server-side by `RefreshTokenStore` and rotated on every
  `POST /api/v1/auth/refresh` call; `POST /api/v1/auth/logout` revokes them idempotently

### Management Endpoint Access Control

The following endpoints are restricted to `ROLE_SUPER_ADMIN` in both `INTERNAL` and `KEYCLOAK` modes, with self-service exceptions for `ROLE_ADMIN`:

| Endpoint | Required role |
|----------|---------------|
| `GET /api/v1/users/me` | `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` — self-service profile retrieval |
| `PUT /api/v1/users/*/update` | `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` — own account update (service layer enforces ownership) |
| `PUT /api/v1/users/*/password` | `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` — own password change (service layer enforces ownership) |
| `* /api/v1/users/**` (all other) | `ROLE_SUPER_ADMIN` |
| `/api/v1/tenants/**` | `ROLE_SUPER_ADMIN` |
| `/api/v1/properties/**` | `ROLE_SUPER_ADMIN` |

All other `/api/**` endpoints require at minimum `ROLE_ADMIN`.

**Convention**: All role names in `ConnectorSecurityConfig` must be derived from the `it.eng.connector.model.Role` enum directly (e.g., `Role.SUPER_ADMIN.name()`, `Role.ADMIN.authorityName()`). Hardcoded role strings are not used.

**DISABLED mode**: The `DISABLED` authentication mode permits all requests including management endpoints. It is intended only for local development and must **not** be used in production.

### User Model (Internal Mode)

Three distinct user roles are used in `initial_data.json` and at runtime:

| Role | Email | `tenantId` | Purpose |
|------|-------|-----------|---------|
| `ROLE_SUPER_ADMIN` | `superadmin@mail.com` | `null` | Manages tenants and cross-tenant operations. No data scope restriction — all tenant data is visible. |
| `ROLE_ADMIN` | `admin@mail.com` | per-tenant (e.g., `engineering`) | Per-tenant admin. Manages catalog, dataset, negotiation, and transfer data for their assigned tenant only. May also call `GET /api/v1/users/me`, `PUT /api/v1/users/{id}/update`, and `PUT /api/v1/users/{id}/password` for their own account. |
| `ROLE_CONNECTOR` | `connector@mail.com` | per-tenant (e.g., `engineering`) | Per-tenant connector user. Authenticates DSP protocol calls (connector-to-connector). |

Each tenant should have its own `ROLE_ADMIN` and `ROLE_CONNECTOR` users with `tenantId` set to that tenant's ID. Multiple tenants cannot share the same email address.

### Internal Service Account (M2M)

Inter-module and connector-to-connector calls (e.g., negotiation → catalog offer validation,
data-transfer → agreement lookup, provider connector → consumer connector protocol callbacks) are
authenticated using self-issued JWTs rather than a regular user login, minted by two small
`tools`-module components wired through `CredentialUtils`:

| Component | Used for | Mints a JWT for |
|-----------|----------|-----------------|
| `InternalServiceTokenIssuer` (`tools`) | Inter-module REST calls within the *same* connector instance (`CredentialUtils.getAPICredentials()`) | subject/email `"internal-service"`, `ROLE_ADMIN`, `tenantId=null` |
| `ConnectorCredentialProviderImpl` (`connector`) | Connector-to-connector DSP protocol calls (`CredentialUtils.getConnectorCredentials()`) | the seeded `connector@mail.com` user (same account the UI/API would use), `ROLE_CONNECTOR` |

Both paths route through `M2mTokenCache`, which caches the minted JWT per logical key and
proactively refreshes it shortly before expiry. `OkHttpRestClient` additionally retries exactly
once on an HTTP 401 response, invalidating the cached token first — this keeps M2M calls resilient
to a secret rotation or a stale cache entry without requiring a restart.

**Configuration:**

```properties
# APPLICATION_SECURITY_JWT_SECRET env var; never commit a real production secret here.
application.security.jwt.secret=${APPLICATION_SECURITY_JWT_SECRET:connector-jwt-dev-secret-change-in-prod-min-32-bytes}
```

> - `application.security.jwt.secret` is the HMAC key that **signs and verifies every JWT** issued
>   in INTERNAL mode (admin login, protocol login, and both M2M paths above). It must be identical
>   across every connector instance that needs to validate each other's tokens.

**In Keycloak mode** (`application.auth.provider=KEYCLOAK`), neither of these two components is
active; `CredentialUtils` instead falls back to the existing Keycloak-backed paths
(`KeycloakAuthenticationService`/`AuthenticationCache` for connector-to-connector calls, using the
`application.keycloak.backend.*` client). Ensure the Keycloak backend service account does **not**
have a `tenantId` claim configured, so `ApiTenantContextFilter` falls back to the `X-Tenant-Id`
header the same way it does for the self-issued M2M tokens above.

### Password Strength Requirements

```properties
application.password.validator.minLength=8
application.password.validator.maxLength=16
application.password.validator.minLowerCase=1
application.password.validator.minUpperCase=1
application.password.validator.minDigit=1
application.password.validator.minSpecial=1
```

---

## Disabled Mode (`DISABLED`)

All endpoints are open with no authentication. Intended for local development only.

```properties
application.auth.provider=DISABLED
```

- All filter chains use `permitAll()`
- `/api/v1/users` endpoints remain active
- No JWT validation, no authentication challenge of any kind
- **Do not use in production**

---

## DCP Authentication (Future — Stub)

When `application.auth.dcp.enabled=true`, the protocol filter chain replaces the provider's
authentication with a `DcpAuthenticationFilter`. The current implementation is a pass-through stub
that will be filled in with Decentralized Claims Protocol JWT validation logic.

This flag is independent of `application.auth.provider` — it can be combined with `KEYCLOAK` or
`INTERNAL` (admin zone always uses the provider, only the protocol zone switches to DCP).

---

## OCSP Certificate Validation

For more information how to verify OCSP certificate and generate new ones, revoke and invalidate, please check following [link.](ocsp/OCSP_GUIDE.md)

Following set of properties will configure OCSP validation for TLS certificate:

```
# OCSP Validation Configuration
# Enable or disable OCSP validation
application.ocsp.validation.enabled=false
# Soft-fail mode: if true, allows connections when OCSP validation fails
# If false, connections will be rejected when OCSP validation fails
application.ocsp.validation.soft-fail=true
# Default cache duration in minutes for OCSP responses without nextUpdate field
application.ocsp.validation.default-cache-duration-minutes=60
# Timeout in seconds for OCSP responder connections
application.ocsp.validation.timeout-seconds=10
```

Current implementation, if OCSP is **DISABLED** (default configuration) will create OkHttpRestClient with truststore that allows ALL certificates. 

If you want to have proper TLS communication, with hostname validation enabled, this can be achieved by setting 

```
application.ocsp.validation.enabled=true
```

This will create proper *OcspX509TrustManager* that will load provided truststore, and perform:

 - hostname validation (PKIX)
 - OCSP check
 
If certificate does not have 

```
Authority Information Access [1]: 
    Access Method: OCSP (1.3.6.1.5.5.7.48.1) 
    Access Location:         URI: http://ocsp-server:8888 

```

then OCSP validation will be skipped. If URL is provided, there must exists at least 2 certificates in chain, for validation to be performed. Otherwise it will be skipped.

To perform strict OCSP validation set following property to 

```
application.ocsp.validation.soft-fail=false
```
