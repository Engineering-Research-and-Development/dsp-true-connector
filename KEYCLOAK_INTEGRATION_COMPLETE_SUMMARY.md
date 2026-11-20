# Security Architecture — Keycloak Integration Summary

**Date**: April 2026  
**Status**: ✅ Complete

> **⚠️ Historical snapshot.** This document describes the security architecture as of the
> `BASIC`-mode PR (April 2026). Since then, `BASIC` mode has been renamed **`INTERNAL`** mode,
> `/api/v1/auth/login|refresh|logout` exists only in `INTERNAL` mode (in `KEYCLOAK` mode the UI
> signs in to Keycloak directly with authorization code + PKCE; the connector-side login proxy and
> `application.keycloak.login.*` were removed), and
> connector-to-connector calls in `KEYCLOAK` mode use a client-credentials token from the `platform`
> realm, and the connector's calls to itself use the `/internal` zone, rather than the flows described below. **[doc/security.md](doc/security.md) is the current, authoritative source** —
> treat everything below as historical context only.

---

## Overview

The connector uses a single unified `ConnectorSecurityConfig` with four ordered Spring Security
filter chains (the table below lists the three of the time of writing; `/internal/**` was added). It supports three authentication providers — **Keycloak**, **Basic Auth**, and
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

Four `SecurityFilterChain` beans matched by URL prefix:

| Order | URL matcher | Auth enforced |
|-------|-------------|---------------|
| 0 | `/internal/**` | Internal-call token signed with an in-memory key, `ROLE_INTERNAL`, loopback peer only |
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
| `M2mTokenCache` | Cache for outbound tokens, keyed per client and token endpoint |
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

# Keycloak trust: issuers are derived as <base-url>/realms/<realm>
# base-url must equal the scheme/host/port of the token "iss" (see KC_HOSTNAME)
application.keycloak.base-url=http://localhost:8180
application.keycloak.audience=dsp-true-connector
application.keycloak.platform-realm=platform
application.keycloak.admin-realm=connector-admin

# Protocol routes accept platform tokens whose azp is this client
application.keycloak.platform-client-id=dsp-connector-backend

# azp of tenant and connector-admin tokens
application.keycloak.ui-client-id=keycloak-api

# Outbound authentication (connector-to-connector client credentials flow, platform realm).
# The token endpoint comes from the platform realm's discovery document.
application.keycloak.backend.client-id=dsp-connector-backend
application.keycloak.backend.client-secret=dsp-connector-backend-secret
application.keycloak.backend.token-caching=true
```

### Bundled Realms

Five realm exports in `ci/docker/keycloak_resources/`, one realm per file and one client per realm, imported on first start by the Keycloak in `connector/docker-compose.yml` (profile `keycloak`). The Keycloak admin console login is `admin` / `admin`. Keycloak URL: `http://localhost:8180`. The exports contain no signing keys; Keycloak generates them on import.

| Realm (file) | Purpose | Client | Credentials | Realm role |
|------|---------|--------|-------------|------------|
| `platform` (`platform-realm.json`) | Connector-to-connector (machine-to-machine) | `dsp-connector-backend` (confidential, client credentials grant; the one client every connector deployment uses) | client secret `dsp-connector-backend-secret` | `CONNECTOR` (service account) |
| `connector-admin` (`connector-admin-realm.json`) | Super-admins (tenant and user management) | `keycloak-api` (public) | `superadmin@mail.com` / `password` | `SUPER_ADMIN` |
| `tenant-EngineeringA` (`tenant-EngineeringA-realm.json`) | Tenant admins of the `EngineeringA` tenant (`engineering-a`) | `keycloak-api` (public) | `admin` (`admin@mail.com`) / `password` | `ADMIN` |
| `tenant-EngineeringB` (`tenant-EngineeringB-realm.json`) | Tenant admins of the `EngineeringB` tenant (`engineering-b`) | `keycloak-api` (public) | `adminB` (`adminB@mail.com`, Keycloak stores the username as `adminb`) / `password` | `ADMIN` |
| `tenant-acme` (`tenant-acme-realm.json`) | Tenant admins of the demo `acme` tenant (`tenant_id` `acme`; seeded only in `connector/src/main/resources/initial_data.json`, not used in tests) | `keycloak-api` (public) | `coyote` / `coyote`, `roadrunner` / `roadrunner` | `ADMIN` |

All tokens carry `aud` `dsp-true-connector` and `realm_access.roles`. Tenant realm tokens also carry `org` and `tenant_id` (for example `EngineeringA` and `engineering-a`; `tenant_id` is the Mongo tenant id, never the realm name); platform and super-admin tokens carry no tenant.

The connector trusts the realm of every enabled tenant (the tenant's `realm` field), looked up per request by the trusted-issuer registry. Protocol routes accept `platform` tokens only (with `aud`, `azp` and the `CONNECTOR` role); tenant and super-admin tokens get 401 there. `/api/**` accepts tenant tokens (`ROLE_ADMIN`) and `connector-admin` tokens (`ROLE_SUPER_ADMIN`) and gives a `platform` token 403. See [doc/security.md](doc/security.md#trusted-issuers).

**Multi-issuer API auth and tenant binding** The admin chain applies per-realm rules: tenant tokens need `aud`, `azp` `keycloak-api` and a `tenant_id` equal to the tenant that owns the realm; `connector-admin` tokens need `aud`, `azp` `keycloak-api`, the `SUPER_ADMIN` role and no `tenant_id`; `platform` tokens must not carry `tenant_id`. A privileged role that the issuing realm may not grant makes the token a 401 and writes a `SECURITY_REALM_ROLE_VIOLATION` audit event; the generic `ROLE_<name>` mapping is gone and `KeycloakRealmRoleConverter` is a per-realm allow-list. The tenant comes from the `tenant_id` claim in both modes; `X-Tenant-Id` is for super-admins only (unknown or disabled tenant 400, any other sender 403, accepted delegation audited as `TENANT_DELEGATION`). `GET /api/v1/users` is a read-only listing that needs `X-Tenant-Id` and uses the tenant realm's own `connector-user-reader` client with a secret stored encrypted on the tenant (`PUT /api/v1/tenants/{id}/realm-credentials`); it is switched with `application.keycloak.user-management`. Each tenant seed realm contains that client. See [doc/security.md](doc/security.md#trust-scope-per-chain), [doc/multi-tenant-user-guide.md](doc/multi-tenant-user-guide.md#keycloak-realm-credentials-for-user-listing) and [D-ARC-006](doc/decisions/architecture/D-ARC-006-per-realm-role-allow-list-and-per-tenant-realm-credentials.md).

### Getting Tokens

**Password grant (tenant admin or super-admin):**
```bash
curl -X POST http://localhost:8180/realms/tenant-EngineeringA/protocol/openid-connect/token \
  -d "client_id=keycloak-api" \
  -d "username=admin" \
  -d "password=password" \
  -d "grant_type=password"
```

**Client credentials grant (machine-to-machine):**
```bash
curl -X POST http://localhost:8180/realms/platform/protocol/openid-connect/token \
  -d "client_id=dsp-connector-backend" \
  -d "client_secret=dsp-connector-backend-secret" \
  -d "grant_type=client_credentials"
```

Use the returned `access_token` as: `Authorization: Bearer <token>`

### User Management in KEYCLOAK Mode

Users are created, listed and changed in Keycloak (`http://localhost:8180`), not in MongoDB. The connector has no
user API in this mode (`/api/v1/users/**` answers 404). Super-admins belong in `connector-admin` (no `tenant_id`),
tenant users in their tenant's realm with `tenant_id` equal to the tenant `_id`; see [doc/security.md](doc/security.md#user-management).

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
- Outbound token cache (now `M2mTokenCache`): DAPS removed, thread-safety fixed
- `UserService` / `UserApiController`: active in `BASIC` and `DISABLED` modes only

---

For full security documentation see [doc/security.md](doc/security.md).
