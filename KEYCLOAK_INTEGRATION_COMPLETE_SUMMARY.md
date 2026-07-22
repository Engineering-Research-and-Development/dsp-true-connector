# Security Architecture — Keycloak Integration Summary

**Date**: April 2026  
**Status**: ✅ Complete

---

## Overview

The connector uses a single unified `ConnectorSecurityConfig` with three ordered Spring Security
filter chains. It supports three authentication providers — **Keycloak**, **Basic Auth**, and
**Disabled** — plus an optional DCP override for protocol endpoints.

Authentication behaviour is governed by two properties:

```properties
# Primary authentication provider: KEYCLOAK | BASIC | DISABLED  (default: KEYCLOAK)
application.auth.provider=KEYCLOAK

# Optional: route protocol endpoints through DCP instead of the provider above.
# Cannot be combined with provider=DISABLED.
# application.auth.dcp.enabled=false
```

---

## Authentication Matrix

| `auth.provider` | `dcp.enabled` | `/api/**` `/actuator/**` | Protocol endpoints¹ |
|-----------------|---------------|--------------------------|----------------------|
| `KEYCLOAK` | `false` | Keycloak JWT → `ROLE_ADMIN` | Keycloak JWT → `ROLE_CONNECTOR` |
| `KEYCLOAK` | `true`  | Keycloak JWT → `ROLE_ADMIN` | DCP → `ROLE_CONNECTOR` |
| `BASIC`    | `false` | HTTP Basic → `ROLE_ADMIN`  | HTTP Basic → `ROLE_CONNECTOR` |
| `BASIC`    | `true`  | HTTP Basic → `ROLE_ADMIN`  | DCP → `ROLE_CONNECTOR` |
| `DISABLED` | `false` | `permitAll()` | `permitAll()` |
| `DISABLED` | `true`  | ❌ startup error | ❌ startup error |

¹ Protocol endpoints: `/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**`

> `DISABLED + dcp.enabled=true` is rejected at startup with an `IllegalStateException`.

---

## Filter Chain Architecture

Three `SecurityFilterChain` beans matched by URL prefix:

| Order | URL matcher | Auth enforced |
|-------|-------------|---------------|
| 1 | `/api/**`, `/actuator/**`, `/env` | Always uses `auth.provider` — admin zone, never DCP |
| 2 | `/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**` | Uses DCP when `dcp.enabled=true`, otherwise `auth.provider` |
| 3 | `/**` | `permitAll()` |

Authentication failures on protocol endpoints return DSP-compliant JSON error bodies
(`CatalogError`, `ContractNegotiationErrorMessage`, `TransferError`) via
`DataspaceProtocolEndpointsExceptionHandler`.

---

## Key Components

| Component | Role |
|-----------|------|
| `ConnectorSecurityConfig` | Unified security config — replaces old `WebSecurityConfig` + `KeycloakSecurityConfig` |
| `KeycloakAuthenticationFilter` | Validates Keycloak JWT Bearer tokens (active in `KEYCLOAK` mode) |
| `KeycloakRealmRoleConverter` | Maps `realm_access.roles` JWT claim to Spring Security authorities |
| `KeycloakAuthenticationService` | Acquires outbound client-credentials tokens for connector-to-connector calls |
| `AuthenticationCache` | Thread-safe cache for outbound tokens |
| `DcpAuthenticationFilter` | Pass-through stub for future DCP JWT validation |
| `UserService` | MongoDB-backed `UserDetailsService` — active in `BASIC` and `DISABLED` modes |
| `AuthenticationModeResolver` | Resolves `auth.provider` and `dcp.enabled` from the Spring environment |
| `DataspaceProtocolEndpointsAuthenticationEntryPoint` | Delegates 401s to Spring MVC for DSP-format error responses |
| `AuthenticationMode` | Enum: `KEYCLOAK \| BASIC \| DISABLED` |

---

## KEYCLOAK Mode

### Properties

```properties
application.auth.provider=KEYCLOAK

# JWT validation against Keycloak
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/dsp-connector
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8180/realms/dsp-connector/protocol/openid-connect/certs

# Outbound authentication (connector-to-connector client credentials flow)
keycloak.auth-server-url=http://localhost:8180
keycloak.realm=dsp-connector
keycloak.resource=dsp-connector-consumer-backend
keycloak.credentials.secret=dsp-connector-consumer-secret
```

### Bundled Realm

Realm export: `ci/docker/keycloak_resources/realm-dsp-connector.json`

| Item | Value |
|------|-------|
| Realm name | `dsp-connector` |
| Keycloak URL | `http://localhost:8180` |
| Consumer client | `dsp-connector-consumer-backend` / `dsp-connector-consumer-secret` |
| Provider client | `dsp-connector-provider-backend` / `dsp-connector-provider-secret` |
| UI client (public) | `dsp-connector-ui` |
| Admin user | `admin@test.com` → `ROLE_ADMIN` |
| Connector user | `connector@test.com` → `ROLE_CONNECTOR` |

### Getting Tokens

**Password grant (interactive login):**
```bash
curl -X POST http://localhost:8180/realms/dsp-connector/protocol/openid-connect/token \
  -d "client_id=dsp-connector-ui" \
  -d "username=admin@test.com" \
  -d "password=admin123" \
  -d "grant_type=password"
```

**Client credentials (service account — consumer):**
```bash
curl -X POST http://localhost:8180/realms/dsp-connector/protocol/openid-connect/token \
  -d "client_id=dsp-connector-consumer-backend" \
  -d "client_secret=dsp-connector-consumer-secret" \
  -d "grant_type=client_credentials"
```

**Client credentials (service account — provider):**
```bash
curl -X POST http://localhost:8180/realms/dsp-connector/protocol/openid-connect/token \
  -d "client_id=dsp-connector-provider-backend" \
  -d "client_secret=dsp-connector-provider-secret" \
  -d "grant_type=client_credentials"
```

Use the returned `access_token` as: `Authorization: Bearer <token>`

### User Management in KEYCLOAK Mode

`/api/v1/users` returns **404** — users are managed in the Keycloak Admin Console
(`http://localhost:8180`), not in MongoDB.

---

## BASIC Mode

```properties
application.auth.provider=BASIC
```

- Credentials sent as `Authorization: Basic <base64(user:password)>`
- Users stored in MongoDB, seeded from `initial_data.json` on startup
- `/api/v1/users` endpoints are **active** for creating and managing users
- Password strength enforced via `application.password.validator.*` properties

---

## DISABLED Mode

```properties
application.auth.provider=DISABLED
```

All endpoints open — `permitAll()`. For local development only. **Do not use in production.**  
`/api/v1/users` endpoints remain available.

---

## What Was Changed from the Previous Architecture

### Removed
- `WebSecurityConfig` and `KeycloakSecurityConfig` — replaced by `ConnectorSecurityConfig`
- All DAPS classes: `DapsAuthenticationService`, `DapsAuthenticationProperties`, `DapsCertificateProvider`
- LEGACY authentication mode
- `LegacyAuthenticationModeCondition`, `NonKeycloakAuthenticationModeCondition`
- `JwtAuthenticationFilter`, `JwtAuthenticationProvider`, `JwtAuthenticationToken`, `DataspaceProtocolEndpointsAuthenticationFilter`
- DAPS SSL bundle properties (`spring.ssl.bundle.jks.daps.*`) from all property files

### Added
- `ConnectorSecurityConfig` with three ordered filter chains
- `BASIC` authentication mode
- `BasicAuthenticationModeCondition`, `BasicOrDisabledAuthenticationModeCondition`, `DcpEnabledCondition`
- `DcpAuthenticationFilter` stub for future DCP integration

### Updated
- `AuthenticationMode` enum: `KEYCLOAK | BASIC | DISABLED` (LEGACY removed)
- `AuthenticationModeResolver`: BASIC support, `isDcpEnabled()`, startup validation, defaults to `KEYCLOAK`
- `AuthenticationCache`: DAPS removed, thread-safety fixed
- `UserService` / `UserApiController`: active in `BASIC` and `DISABLED` modes only

---

For full security documentation see [doc/security.md](doc/security.md).
