# Security Architecture Refactoring — Summary

**Date**: April 2026  
**Status**: ✅ Complete

---

## Overview

This document summarises the security architecture refactoring that replaced the dual-config
approach (`WebSecurityConfig` + `KeycloakSecurityConfig`) with a single unified
`ConnectorSecurityConfig`.

---

## What Changed

### Removed
- `WebSecurityConfig` — old non-Keycloak security config
- `KeycloakSecurityConfig` — separate Keycloak-only config
- All DAPS authentication classes (`DapsAuthenticationService`, `DapsAuthenticationProperties`,
  `DapsCertificateProvider`)
- LEGACY authentication mode
- `LegacyAuthenticationModeCondition`, `NonKeycloakAuthenticationModeCondition`
- `JwtAuthenticationFilter`, `JwtAuthenticationProvider`, `JwtAuthenticationToken`
- `DataspaceProtocolEndpointsAuthenticationFilter`

### Added
- `ConnectorSecurityConfig` — single unified config with three ordered `SecurityFilterChain` beans
- `BASIC` authentication mode — HTTP Basic Auth backed by MongoDB `UserService`
- `BasicAuthenticationModeCondition`, `BasicOrDisabledAuthenticationModeCondition`,
  `DcpEnabledCondition`
- `DcpAuthenticationFilter` — pass-through stub for future DCP integration

### Updated
- `AuthenticationMode` enum: now `KEYCLOAK | BASIC | DISABLED` (LEGACY removed)
- `AuthenticationModeResolver`: added BASIC, added `isDcpEnabled()`, validates illegal
  combinations, defaults to `KEYCLOAK`
- `AuthenticationCache`: removed DAPS references, fixed thread-safety
- `UserService` / `UserApiController`: now use `BasicOrDisabledAuthenticationModeCondition`

---

## Authentication Matrix

| `auth.provider` | `dcp.enabled` | `/api/**` | Protocol endpoints |
|-----------------|---------------|-----------|-------------------|
| `KEYCLOAK` | `false` | Keycloak JWT → ROLE_ADMIN | Keycloak JWT → ROLE_CONNECTOR |
| `KEYCLOAK` | `true`  | Keycloak JWT → ROLE_ADMIN | DCP stub → ROLE_CONNECTOR |
| `BASIC`    | `false` | HTTP Basic → ROLE_ADMIN  | HTTP Basic → ROLE_CONNECTOR |
| `BASIC`    | `true`  | HTTP Basic → ROLE_ADMIN  | DCP stub → ROLE_CONNECTOR |
| `DISABLED` | `false` | permitAll() | permitAll() |
| `DISABLED` | `true`  | ❌ startup error | ❌ startup error |

Protocol endpoints: `/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**`

---

## Configuration

```properties
# application.auth.provider: KEYCLOAK | BASIC | DISABLED  (default: KEYCLOAK)
application.auth.provider=KEYCLOAK

# Optional DCP override for protocol endpoints (cannot combine with DISABLED)
# application.auth.dcp.enabled=false
```

For full details see [doc/security.md](doc/security.md).
