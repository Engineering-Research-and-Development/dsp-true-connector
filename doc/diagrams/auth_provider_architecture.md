# Authentication Provider Architecture Diagrams

## System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Application Startup                                 │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Spring Boot Auto-Configuration                                             │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  AuthenticationProperties                                             │  │
│  │  Reads: application.auth.provider=KEYCLOAK|DAPS|DCP                  │  │
│  │  Falls back to: application.keycloak.enable (deprecated)             │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                      │                                       │
│                                      ▼                                       │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  AuthenticationAutoConfiguration                                      │  │
│  │                                                                        │  │
│  │  IF provider == KEYCLOAK:                                            │  │
│  │    ✓ KeycloakAuthenticationService                                   │  │
│  │    ✓ KeycloakSecurityConfigProvider                                  │  │
│  │                                                                        │  │
│  │  IF provider == DAPS:                                                │  │
│  │    ✓ DapsAuthenticationService                                       │  │
│  │    ✓ DapsSecurityConfigProvider                                      │  │
│  │                                                                        │  │
│  │  IF provider == DCP:                                                 │  │
│  │    ✓ DcpAuthenticationService                                        │  │
│  │    ✓ DcpSecurityConfigProvider                                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Connector Security Configuration                                           │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  SecurityConfig                                                       │  │
│  │                                                                        │  │
│  │  Injects: SecurityConfigProvider (selected by property)              │  │
│  │                                                                        │  │
│  │  Creates:                                                             │  │
│  │    • SecurityCommonConfig (CORS, authz rules, headers)               │  │
│  │    • Delegates to provider.configureSecurityChain(http, common)      │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Runtime Security Filter Chain                                              │
│                                                                              │
│  IF Keycloak:                     IF DAPS:                                  │
│  ┌─────────────────────────┐      ┌─────────────────────────────────────┐  │
│  │ KeycloakAuthFilter      │      │ ProtocolEndpointsFilter             │  │
│  │         ↓               │      │         ↓                           │  │
│  │ JWT Validation          │      │ JwtAuthenticationFilter             │  │
│  │         ↓               │      │         ↓                           │  │
│  │ Role Conversion         │      │ BasicAuthenticationFilter           │  │
│  │         ↓               │      │         ↓                           │  │
│  │ SecurityContext         │      │ UserDetailsService (MongoDB)        │  │
│  └─────────────────────────┘      └─────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Package Structure

```
tools/src/main/java/it/eng/tools/auth/
│
├── 📦 [Root Package]
│   ├── AuthProvider.java                     (interface - token fetch/validate)
│   └── AuthenticationCache.java              (caching utility)
│
├── 📦 core/                                   [NEW - Core Abstractions]
│   ├── AuthProviderType.java                 (enum: KEYCLOAK, DAPS, DCP)
│   ├── AuthenticationProperties.java         (@ConfigurationProperties)
│   ├── SecurityConfigProvider.java           (strategy interface)
│   └── SecurityCommonConfig.java             (shared config DTO)
│
├── 📦 config/                                 [NEW - Auto-Configuration]
│   ├── AuthenticationAutoConfiguration.java  (provider selection)
│   └── SecurityAutoConfiguration.java        (security setup)
│
├── 📦 keycloak/                               [Keycloak Implementation]
│   ├── KeycloakAuthenticationService.java    (implements AuthProvider)
│   ├── KeycloakAuthenticationProperties.java (@ConfigurationProperties)
│   └── KeycloakSecurityConfigProvider.java   [NEW] (implements SecurityConfigProvider)
│
├── 📦 daps/                                   [DAPS Implementation]
│   ├── DapsAuthenticationService.java        (implements AuthProvider)
│   ├── DapsAuthenticationProperties.java     (@ConfigurationProperties)
│   ├── DapsCertificateProvider.java          (certificate management)
│   └── DapsSecurityConfigProvider.java       [NEW] (implements SecurityConfigProvider)
│
└── 📦 dcp/                                    [DCP Implementation - FUTURE]
    ├── DcpAuthenticationService.java         (implements AuthProvider)
    ├── DcpAuthenticationProperties.java      (@ConfigurationProperties)
    └── DcpSecurityConfigProvider.java        (implements SecurityConfigProvider)

connector/src/main/java/it/eng/connector/configuration/
│
├── SecurityConfig.java                        [NEW - Unified Config]
├── CorsConfigProperties.java                  [NEW - Extracted CORS]
│
├── KeycloakSecurityConfig.java                [DEPRECATED - Backward Compat]
├── WebSecurityConfig.java                     [DEPRECATED - Backward Compat]
│
└── [Filters - Keep, used by providers]
    ├── KeycloakAuthenticationFilter.java
    ├── KeycloakRealmRoleConverter.java
    ├── JwtAuthenticationFilter.java
    ├── JwtAuthenticationProvider.java
    └── DataspaceProtocolEndpointsAuthenticationFilter.java
```

## Class Interaction Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        <<interface>>                                    │
│                     SecurityConfigProvider                              │
│  ────────────────────────────────────────────────────────────────────  │
│  + configureSecurityChain(http, commonConfig): SecurityFilterChain     │
│  + jwtDecoder(): JwtDecoder                                            │
└────────────────────────────────────────────────────────────────────────┘
                                    △
                                    │ implements
                ┌───────────────────┼───────────────────┐
                │                   │                   │
┌───────────────┴────────────┐  ┌──┴──────────────┐  ┌─┴───────────────┐
│ KeycloakSecurityConfig     │  │ DapsSecurity    │  │ DcpSecurity     │
│ Provider                   │  │ ConfigProvider  │  │ ConfigProvider  │
├────────────────────────────┤  ├─────────────────┤  ├─────────────────┤
│ - keycloakProperties       │  │ - dapsProps     │  │ - dcpProps      │
│ - roleConverter            │  │ - jwtProvider   │  │ - ...           │
│                            │  │ - userRepo      │  │                 │
├────────────────────────────┤  ├─────────────────┤  ├─────────────────┤
│ + configureSecurityChain() │  │ + configure()   │  │ + configure()   │
│ + jwtDecoder()             │  │ + jwtDecoder()  │  │ + jwtDecoder()  │
│                            │  │   returns null  │  │                 │
└────────────────────────────┘  └─────────────────┘  └─────────────────┘
                │                        │                      │
                │ creates                │ creates              │ creates
                ▼                        ▼                      ▼
┌───────────────────────────┐  ┌──────────────────┐  ┌────────────────┐
│ KeycloakAuthFilter        │  │ JwtAuthFilter    │  │ DcpAuthFilter  │
│ JwtDecoder                │  │ BasicAuthFilter  │  │ ...            │
│ KeycloakRealmRoleConverter│  │ UserDetailsService│  │                │
└───────────────────────────┘  └──────────────────┘  └────────────────┘


┌────────────────────────────────────────────────────────────────────────┐
│                          SecurityConfig                                 │
│                       (Connector Module)                                │
├────────────────────────────────────────────────────────────────────────┤
│ - securityConfigProvider: SecurityConfigProvider  [INJECTED]           │
│ - corsConfigProperties: CorsConfigProperties                           │
├────────────────────────────────────────────────────────────────────────┤
│ + securityFilterChain(http): SecurityFilterChain                       │
│     1. Build SecurityCommonConfig (CORS, authz rules, headers)         │
│     2. Delegate to provider.configureSecurityChain(http, common)       │
│     3. Return configured SecurityFilterChain                           │
└────────────────────────────────────────────────────────────────────────┘
                                    │ uses
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      SecurityCommonConfig                               │
│                           (DTO)                                         │
├────────────────────────────────────────────────────────────────────────┤
│ - corsConfigurationSource: CorsConfigurationSource                     │
│ - adminEndpoints: List<RequestMatcher>                                │
│ - connectorEndpoints: List<RequestMatcher>                            │
│ - apiEndpoints: List<RequestMatcher>                                  │
└────────────────────────────────────────────────────────────────────────┘
```

## Property Resolution Flow

```
┌───────────────────────────────────────────────────────────────┐
│  1. Application Startup                                        │
└───────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌───────────────────────────────────────────────────────────────┐
│  2. AuthenticationProperties Initialization                   │
│                                                                │
│     Read: application.auth.provider                           │
│                                                                │
│     ┌─────────────────────────────────────────────┐          │
│     │ Property Value?                             │          │
│     └─────────────────────────────────────────────┘          │
│           │                                                    │
│           ├─ Set? ──────────────────────────────┐            │
│           │                                      │            │
│           │                                      ▼            │
│           │                          Use value (KEYCLOAK,    │
│           │                          DAPS, or DCP)           │
│           │                                      │            │
│           └─ Not Set? ──────┐                   │            │
│                              │                   │            │
│                              ▼                   │            │
│                   Check legacy property          │            │
│                   application.keycloak.enable    │            │
│                              │                   │            │
│                              ├─ true? ──────────────────┐    │
│                              │             Set to       │    │
│                              │             KEYCLOAK     │    │
│                              │                          │    │
│                              ├─ false? ─────────────────┼──┐ │
│                              │             Set to       │  │ │
│                              │             DAPS         │  │ │
│                              │                          │  │ │
│                              └─ Not Set? ───────────────┼──┤ │
│                                          Default to     │  │ │
│                                          DAPS           │  │ │
│                                                         │  │ │
│                                     ┌───────────────────┘  │ │
│                                     ▼                      │ │
└─────────────────────────────────────┼──────────────────────┼─┘
                                      │                      │
                          ┌───────────┴──────────┬──────────┘
                          │                      │
                          ▼                      ▼
            ┌─────────────────────┐  ┌─────────────────────┐
            │ Log Deprecation     │  │ Provider Selected   │
            │ Warning (if using   │  │ Final Value:        │
            │ legacy property)    │  │ - KEYCLOAK          │
            └─────────────────────┘  │ - DAPS              │
                                      │ - DCP               │
                                      └─────────────────────┘
                                               │
                                               ▼
┌───────────────────────────────────────────────────────────────┐
│  3. Conditional Bean Creation                                 │
│                                                                │
│     IF provider == KEYCLOAK:                                  │
│       @ConditionalOnProperty(                                 │
│         name="application.auth.provider",                     │
│         havingValue="KEYCLOAK")                               │
│       → Create KeycloakAuthenticationService                  │
│       → Create KeycloakSecurityConfigProvider                 │
│                                                                │
│     IF provider == DAPS:                                      │
│       @ConditionalOnProperty(                                 │
│         name="application.auth.provider",                     │
│         havingValue="DAPS",                                   │
│         matchIfMissing=true)                                  │
│       → Create DapsAuthenticationService                      │
│       → Create DapsSecurityConfigProvider                     │
│                                                                │
│     IF provider == DCP:                                       │
│       @ConditionalOnProperty(                                 │
│         name="application.auth.provider",                     │
│         havingValue="DCP")                                    │
│       → Create DcpAuthenticationService                       │
│       → Create DcpSecurityConfigProvider                      │
└───────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌───────────────────────────────────────────────────────────────┐
│  4. AuthProvider Bean Selection                               │
│                                                                │
│     AuthenticationAutoConfiguration:                          │
│       - Inject Optional<KeycloakAuthenticationService>        │
│       - Inject Optional<DapsAuthenticationService>            │
│       - Inject Optional<DcpAuthenticationService>             │
│                                                                │
│       switch(provider) {                                      │
│         KEYCLOAK -> return keycloak.orElseThrow()            │
│         DAPS     -> return daps.orElseThrow()                │
│         DCP      -> return dcp.orElseThrow()                 │
│       }                                                        │
└───────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌───────────────────────────────────────────────────────────────┐
│  5. Security Configuration                                    │
│                                                                │
│     SecurityConfig (connector module):                        │
│       - Inject SecurityConfigProvider (selected by property)  │
│       - Create SecurityCommonConfig                           │
│       - Call provider.configureSecurityChain(http, common)    │
│       - Return configured SecurityFilterChain                 │
└───────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌───────────────────────────────────────────────────────────────┐
│  6. Application Ready                                         │
│                                                                │
│     Log:                                                       │
│       "Authentication Provider: KEYCLOAK"                     │
│       "Security Filter Chain: Configured"                     │
│       "Application Started Successfully"                      │
└───────────────────────────────────────────────────────────────┘
```

## Security Filter Chain Comparison

### Before (Duplicated Configuration)

```
KeycloakSecurityConfig                    WebSecurityConfig
(@ConditionalOnProperty                   (@ConditionalOnProperty
  keycloak.enable=true)                     keycloak.enable=false)
┌───────────────────────┐                ┌───────────────────────┐
│ CORS Configuration    │ ◄── DUPLICATE ─┤ CORS Configuration    │
│   50 lines            │                │   50 lines            │
├───────────────────────┤                ├───────────────────────┤
│ Security Headers      │ ◄── DUPLICATE ─┤ Security Headers      │
│   20 lines            │                │   20 lines            │
├───────────────────────┤                ├───────────────────────┤
│ Authorization Rules   │ ◄── DUPLICATE ─┤ Authorization Rules   │
│   30 lines            │                │   30 lines            │
├───────────────────────┤                ├───────────────────────┤
│ Keycloak Auth Filter  │                │ JWT Auth Filter       │
│ JWT Decoder           │                │ Basic Auth Filter     │
│ Role Converter        │                │ UserDetailsService    │
│   37 lines            │                │   102 lines           │
└───────────────────────┘                └───────────────────────┘
      137 lines total                          202 lines total
                   ╲                          ╱
                    ╲                        ╱
                     ╲                      ╱
                      ╲                    ╱
                   ~100 lines duplicated
```

### After (Centralized Configuration)

```
                SecurityConfig (Connector Module)
                ┌─────────────────────────────────┐
                │ Common Configuration            │
                │ ─────────────────────────        │
                │ • CORS Configuration            │
                │ • Security Headers              │
                │ • Authorization Rules           │
                │                                 │
                │ Delegates to:                   │
                │ SecurityConfigProvider          │
                └────────────┬────────────────────┘
                             │ Strategy Pattern
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐  ┌──────────────────┐  ┌──────────────┐
│ Keycloak      │  │ DAPS             │  │ DCP          │
│ SecurityConfig│  │ SecurityConfig   │  │ SecurityConfig│
│ Provider      │  │ Provider         │  │ Provider     │
├───────────────┤  ├──────────────────┤  ├──────────────┤
│ Keycloak Auth │  │ JWT Auth Filter  │  │ DCP Auth     │
│ Filter        │  │ Basic Auth       │  │ Logic        │
│ JWT Decoder   │  │ UserDetails      │  │              │
│ Role Conv.    │  │                  │  │              │
└───────────────┘  └──────────────────┘  └──────────────┘
  150 lines           200 lines             150 lines
  (tools module)      (tools module)        (tools module)

   Single SecurityConfig in connector: ~100 lines
   No duplication of CORS, headers, authorization rules
```

## Request Flow Diagram

### Keycloak Provider Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    Incoming HTTP Request                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Spring Security Filter Chain (Keycloak Mode)                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ 1. CORS Filter                │
              │    - Check origin             │
              │    - Add CORS headers         │
              └───────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ 2. Security Headers Filter    │
              │    - X-Content-Type-Options   │
              │    - X-XSS-Protection         │
              │    - HSTS                     │
              └───────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ 3. KeycloakAuthFilter         │
              │    - Extract JWT from header  │
              │    - Validate JWT signature   │
              │    - Validate issuer          │
              │    - Validate expiry          │
              └───────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ 4. JwtDecoder                 │
              │    - Decode JWT claims        │
              │    - Extract user info        │
              │    - Extract realm_access     │
              └───────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ 5. KeycloakRealmRoleConverter │
              │    - Extract roles from JWT   │
              │    - Convert to authorities   │
              │    - ROLE_ADMIN               │
              │    - ROLE_CONNECTOR           │
              └───────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ 6. SecurityContext            │
              │    - Set authentication       │
              │    - Set authorities          │
              └───────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ 7. Authorization Check        │
              │    - /admin/** → ROLE_ADMIN   │
              │    - /connector/** →          │
              │      ROLE_CONNECTOR           │
              │    - /api/** → ROLE_ADMIN     │
              └───────────────────────────────┘
                              │
                              ▼ Authorized
              ┌───────────────────────────────┐
              │ 8. Controller Method          │
              │    - Handle request           │
              │    - Return response          │
              └───────────────────────────────┘
```

### DAPS Provider Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    Incoming HTTP Request                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Spring Security Filter Chain (DAPS Mode)                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ 1. CORS Filter                │
              │    - Same as Keycloak         │
              └───────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ 2. Security Headers Filter    │
              │    - Same as Keycloak         │
              └───────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ 3. DataspaceProtocol          │
              │    EndpointsAuthFilter        │
              │    - Check if DSP endpoint    │
              │    - Allow unsigned requests  │
              │      for catalog queries      │
              └───────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ 4. JwtAuthenticationFilter    │
              │    - Check for JWT in header  │
              │    - If present:              │
              │      • Validate with          │
              │        JwtAuthenticationProvider│
              │      • Check DAPS signature   │
              └───────────────────────────────┘
                              │
                              ├─ JWT valid? ─────┐
                              │                   │
                              │ No                │ Yes
                              ▼                   ▼
              ┌───────────────────────────────┐  │
              │ 5. BasicAuthenticationFilter  │  │
              │    - Check for Basic Auth     │  │
              │    - Extract username/password│  │
              │    - Query MongoDB users      │  │
              │    - Validate password        │  │
              │    - Create authorities       │  │
              └───────────────────────────────┘  │
                              │                   │
                              ├───────────────────┘
                              ▼
              ┌───────────────────────────────┐
              │ 6. SecurityContext            │
              │    - Set authentication       │
              │    - Set authorities          │
              └───────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ 7. Authorization Check        │
              │    - Same rules as Keycloak   │
              └───────────────────────────────┘
                              │
                              ▼ Authorized
              ┌───────────────────────────────┐
              │ 8. Controller Method          │
              │    - Handle request           │
              │    - Return response          │
              └───────────────────────────────┘
```

## Deployment Scenarios

### Scenario 1: Single Connector with Keycloak

```
┌─────────────────────────────────────────────────────────────┐
│                     Docker Compose                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────┐       ┌──────────────────────┐   │
│  │  Keycloak            │       │  DSP Connector       │   │
│  │  ──────────          │       │  ─────────────       │   │
│  │  Port: 8080          │◄──────┤  Port: 8080          │   │
│  │  Realm: dsp-connector│  JWT  │                      │   │
│  │                      │ Tokens│  Env:                │   │
│  │  Users:              │       │    auth.provider=    │   │
│  │  - admin (ADMIN)     │       │      KEYCLOAK        │   │
│  │  - connector         │       │                      │   │
│  │    (CONNECTOR)       │       │    jwt.issuer-uri=   │   │
│  └──────────────────────┘       │      http://keycloak:│   │
│                                 │      8080/realms/    │   │
│                                 │      dsp-connector   │   │
│                                 └──────────────────────┘   │
│                                                              │
│  User → Browser → Keycloak Login → JWT Token → Connector   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Scenario 2: Two Connectors with DAPS

```
┌─────────────────────────────────────────────────────────────────┐
│                     Docker Compose                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐                        ┌────────────────┐ │
│  │  DAPS Server     │                        │  Connector A   │ │
│  │  ────────────    │                        │  ────────────  │ │
│  │  Port: 443       │◄───────────────────────┤  Port: 8080    │ │
│  │                  │  Certificate Auth      │                │ │
│  │  Issues:         │  Request DAT Token     │  Env:          │ │
│  │  - DAT Tokens    │                        │    auth.provider=│
│  │                  │                        │      DAPS      │ │
│  └──────────────────┘                        │                │ │
│           ▲                                   │  Cert:         │ │
│           │                                   │    connector-a │ │
│           │                                   │    .jks        │ │
│           │                                   └────────────────┘ │
│           │                                          │           │
│           │                                          │ DSP       │
│           │                                          │ Protocol  │
│           │                                          │           │
│           │                                          ▼           │
│           │                                   ┌────────────────┐ │
│           │                                   │  Connector B   │ │
│           │                                   │  ────────────  │ │
│           └───────────────────────────────────┤  Port: 8081    │ │
│                       Certificate Auth        │                │ │
│                       Request DAT Token       │  Env:          │ │
│                                               │    auth.provider=│
│                                               │      DAPS      │ │
│                                               │                │ │
│                                               │  Cert:         │ │
│                                               │    connector-b │ │
│                                               │    .jks        │ │
│                                               └────────────────┘ │
│                                                                  │
│  Connector A ↔ Connector B: Authenticated via DAT tokens       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Scenario 3: Hybrid Deployment

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Production Environment                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────┐                                               │
│  │  Keycloak        │                                               │
│  │  (Organization   │                                               │
│  │   Internal Auth) │                                               │
│  └────────┬─────────┘                                               │
│           │ JWT                                                      │
│           ▼                                                          │
│  ┌──────────────────────────┐                                       │
│  │  Consumer Connector      │                                       │
│  │  ──────────────────────  │                                       │
│  │  Port: 443               │                                       │
│  │  Env:                    │                                       │
│  │    auth.provider=KEYCLOAK│                                       │
│  │                          │                                       │
│  │  For internal users:     │                                       │
│  │  - UI access             │                                       │
│  │  - API management        │                                       │
│  └────────────┬─────────────┘                                       │
│               │                                                      │
│               │ For connector-to-connector:                         │
│               │ Uses DAPS/DCP (secondary auth)                      │
│               │                                                      │
│               ▼ DSP Protocol                                        │
│  ┌─────────────────────────────────────────┐                        │
│  │  Provider Connector                     │                        │
│  │  ───────────────────────                │                        │
│  │  Port: 443                              │                        │
│  │  Env:                                   │                        │
│  │    auth.provider=DAPS                   │                        │
│  │                                         │                        │
│  │  Uses DAPS for:                         │                        │
│  │  - All authentication                   │                        │
│  │  - Connector-to-connector               │                        │
│  └─────────────────────────────────────────┘                        │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

*This diagram document complements the implementation plan.*  
*Use these diagrams in presentations and architecture reviews.*

