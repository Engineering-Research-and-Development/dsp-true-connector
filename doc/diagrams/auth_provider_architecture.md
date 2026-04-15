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
│  ConnectorSecurityConfig — Three Ordered SecurityFilterChain Beans          │
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
│   ├── AuthProvider.java                      (interface — outbound token fetch)
│   ├── AuthenticationMode.java                (enum: KEYCLOAK, BASIC, DISABLED)
│   ├── AuthenticationModeResolver.java        (resolves provider + dcp.enabled)
│   └── AuthenticationCache.java               (thread-safe outbound token cache)
│
├── 📦 condition/
│   ├── KeycloakAuthenticationModeCondition.java
│   ├── BasicAuthenticationModeCondition.java
│   ├── BasicOrDisabledAuthenticationModeCondition.java
│   └── DcpEnabledCondition.java
│
└── 📦 keycloak/
    ├── KeycloakAuthenticationService.java     (implements AuthProvider)
    └── KeycloakAuthenticationProperties.java  (@ConfigurationProperties)

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
│       → JwtDecoder                                            │
│       → KeycloakAuthenticationService                         │
│       → AuthenticationCache                                   │
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
                    │  Three SecurityFilterChain beans          │
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
               │    JwtDecoder.decode()        │
               │    Validate signature/expiry  │
               └───────────────────────────────┘
                               │
                               ▼
               ┌───────────────────────────────┐
               │ 4. KeycloakRealmRoleConverter │
               │    Extract realm_access.roles │
               │    Map to ROLE_ADMIN /         │
               │    ROLE_CONNECTOR authorities │
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
└─────────────────────────┘   │ → DSP-format 401 JSON body  │
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
│  │  Realm: dsp-connector│  JWT  │                      │   │
│  │                      │ Tokens│  application.        │   │
│  │  Users:              │       │  auth.provider=      │   │
│  │  admin@test.com      │       │  KEYCLOAK            │   │
│  │    → ROLE_ADMIN      │       │                      │   │
│  │  connector@test.com  │       │  jwt.issuer-uri=     │   │
│  │    → ROLE_CONNECTOR  │       │  http://keycloak:    │   │
│  └──────────────────────┘       │  8180/realms/        │   │
│                                 │  dsp-connector       │   │
│                                 └──────────────────────┘   │
│                                                              │
│  Flow: User → Keycloak Login → JWT Token → Connector API   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

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
│  │  Realm: dsp-connector                    │                   │
│  │  Clients: consumer-backend,              │                   │
│  │           provider-backend               │                   │
│  └──────────┬──────────────────────┬────────┘                   │
│             │ JWT (ROLE_ADMIN/     │ JWT (ROLE_CONNECTOR)       │
│             │  ROLE_CONNECTOR)     │                            │
│             ▼                      ▼                            │
│  ┌─────────────────────┐  ┌─────────────────────┐              │
│  │  Consumer Connector │  │  Provider Connector │              │
│  │  Port: 8080         │  │  Port: 8090         │              │
│  │                     │  │                     │              │
│  │  auth.provider=     │  │  auth.provider=     │              │
│  │  KEYCLOAK           │  │  KEYCLOAK           │              │
│  │                     │  │                     │              │
│  │  isconsumer=true    │  │  isconsumer=false   │              │
│  └──────────┬──────────┘  └─────────────────────┘              │
│             │                       ▲                           │
│             │  DSP Protocol         │                           │
│             │  (Bearer token from   │                           │
│             │   client credentials) │                           │
│             └───────────────────────┘                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

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

spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/dsp-connector
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8180/realms/dsp-connector/protocol/openid-connect/certs

keycloak.auth-server-url=http://localhost:8180
keycloak.realm=dsp-connector
keycloak.resource=dsp-connector-consumer-backend
keycloak.credentials.secret=dsp-connector-consumer-secret
```

`/api/v1/users` returns **404** in this mode — user management is handled in the Keycloak Admin Console.

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
| `ConnectorSecurityConfig` | Unified config with 3 ordered `SecurityFilterChain` beans |
| `AuthenticationMode.BASIC` | New auth mode enum value |
| `BasicAuthenticationModeCondition` | Condition: active when `provider=BASIC` |
| `BasicOrDisabledAuthenticationModeCondition` | Condition: active when `provider=BASIC` or `DISABLED` |
| `DcpEnabledCondition` | Condition: active when `dcp.enabled=true` |
| `DcpAuthenticationFilter` | Pass-through stub for future DCP JWT validation |

---

*For full authentication documentation see [doc/security.md](../security.md) and
[KEYCLOAK_INTEGRATION_COMPLETE_SUMMARY.md](../../KEYCLOAK_INTEGRATION_COMPLETE_SUMMARY.md).*
