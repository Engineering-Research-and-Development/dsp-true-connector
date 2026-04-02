# Security Architecture — Complete Summary

**Date**: April 2026  
**Status**: ✅ Complete

---

## Overview

The connector uses a single unified `ConnectorSecurityConfig` with three ordered Spring Security
filter chains. Authentication behaviour is governed by two properties:

```properties
# KEYCLOAK | BASIC | DISABLED  (default: KEYCLOAK)
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

¹ `/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**`

---

## Filter Chain Architecture

Three `SecurityFilterChain` beans matched by URL prefix:

| Order | URL matcher | Auth enforced |
|-------|-------------|---------------|
| 1 | `/api/**`, `/actuator/**`, `/env` | Always uses `auth.provider` (never DCP) |
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
| `KeycloakAuthenticationFilter` | Validates Keycloak JWT Bearer tokens (active in KEYCLOAK mode) |
| `KeycloakRealmRoleConverter` | Maps `realm_access.roles` JWT claim to Spring authorities |
| `KeycloakAuthenticationService` | Acquires outbound client-credentials tokens |
| `AuthenticationCache` | Caches outbound tokens; thread-safe |
| `DcpAuthenticationFilter` | Pass-through stub for future DCP JWT validation |
| `UserService` | MongoDB-backed `UserDetailsService` — active in BASIC and DISABLED modes |
| `AuthenticationModeResolver` | Resolves `auth.provider` and `dcp.enabled` from environment |
| `DataspaceProtocolEndpointsAuthenticationEntryPoint` | Routes 401s to Spring MVC for DSP-format error responses |

---

## KEYCLOAK Mode — Keycloak Configuration

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/dsp-connector
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8180/realms/dsp-connector/protocol/openid-connect/certs

keycloak.auth-server-url=http://localhost:8180
keycloak.realm=dsp-connector
keycloak.resource=dsp-connector-consumer-backend
keycloak.credentials.secret=dsp-connector-consumer-secret
```

Bundled realm: `ci/docker/keycloak_resources/realm-dsp-connector.json`

| Item | Value |
|------|-------|
| Realm | `dsp-connector` |
| Consumer client | `dsp-connector-consumer-backend` / `dsp-connector-consumer-secret` |
| Provider client | `dsp-connector-provider-backend` / `dsp-connector-provider-secret` |
| UI client (public) | `dsp-connector-ui` |
| Admin user | `admin@test.com` → `ROLE_ADMIN` |
| Connector user | `connector@test.com` → `ROLE_CONNECTOR` |

**User management** in KEYCLOAK mode: handled in Keycloak Admin Console — `/api/v1/users` returns 404.

---

## BASIC Mode — User Management

Users stored in MongoDB, seeded from `initial_data.json` on startup.  
`/api/v1/users` endpoints are active for creating and managing users.

---

## DISABLED Mode

All endpoints open — `permitAll()`. For local development only. Do not use in production.

---

For full security documentation see [doc/security.md](doc/security.md).
