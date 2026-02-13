# Authentication Provider Architecture - Quick Summary

## TL;DR: What Changes?

### Single Property to Control Everything
```properties
# Instead of this mess:
application.keycloak.enable=true

# You'll have this:
application.auth.provider=KEYCLOAK  # or DAPS or DCP
```

### Module Responsibilities

```
┌─────────────────────────────────────────────────────────────┐
│                       TOOLS MODULE                          │
│  Owns: Auth provider selection & provider-specific logic   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  📦 it.eng.tools.auth.core/                                │
│     ├── AuthProvider.java            (already exists)      │
│     ├── AuthProviderType.java        (NEW - enum)          │
│     ├── AuthenticationProperties.java (NEW)                │
│     └── SecurityConfigProvider.java  (NEW - interface)     │
│                                                             │
│  📦 it.eng.tools.auth.keycloak/                            │
│     ├── KeycloakAuthenticationService.java                 │
│     │   @ConditionalOnProperty("KEYCLOAK")                 │
│     ├── KeycloakAuthenticationProperties.java              │
│     └── KeycloakSecurityConfigProvider.java (NEW)          │
│         • Creates Keycloak filters                          │
│         • Configures OAuth2 JWT                             │
│                                                             │
│  📦 it.eng.tools.auth.daps/                                │
│     ├── DapsAuthenticationService.java                     │
│     │   @ConditionalOnProperty("DAPS")                     │
│     ├── DapsAuthenticationProperties.java                  │
│     ├── DapsCertificateProvider.java                       │
│     └── DapsSecurityConfigProvider.java (NEW)              │
│         • Creates JWT + Basic auth filters                  │
│         • Configures UserDetailsService                     │
│                                                             │
│  📦 it.eng.tools.auth.dcp/ (FUTURE)                        │
│     ├── DcpAuthenticationService.java                      │
│     │   @ConditionalOnProperty("DCP")                      │
│     ├── DcpAuthenticationProperties.java                   │
│     └── DcpSecurityConfigProvider.java                     │
│         • DCP-specific implementation                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ provides SecurityConfigProvider bean
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     CONNECTOR MODULE                         │
│  Owns: Common security concerns (CORS, authz, headers)     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  📦 it.eng.connector.configuration/                        │
│                                                             │
│  ┌───────────────────────────────────────────────┐        │
│  │ SecurityConfig.java (NEW - SINGLE CONFIG)      │        │
│  │ • No @ConditionalOnProperty                    │        │
│  │ • Injects SecurityConfigProvider from tools    │        │
│  │ • Defines common CORS, headers, authz rules    │        │
│  │ • Delegates auth setup to provider             │        │
│  └───────────────────────────────────────────────┘        │
│                                                             │
│  ┌───────────────────────────────────────────────┐        │
│  │ CorsConfigProperties.java (NEW)                │        │
│  │ • Extracted from duplicated CORS code          │        │
│  │ • Shared by all auth providers                 │        │
│  └───────────────────────────────────────────────┘        │
│                                                             │
│  Filters (no @ConditionalOnProperty, used by providers):   │
│  ├── KeycloakAuthenticationFilter.java                     │
│  ├── KeycloakRealmRoleConverter.java                       │
│  ├── JwtAuthenticationFilter.java                          │
│  ├── JwtAuthenticationProvider.java                        │
│  └── DataspaceProtocolEndpointsAuthenticationFilter.java   │
│                                                             │
│  🗑️ TO DELETE (after migration):                          │
│  ├── KeycloakSecurityConfig.java                           │
│  └── WebSecurityConfig.java                                │
└─────────────────────────────────────────────────────────────┘
```

---

## The Problem We're Solving

### Before (Current State) 🔴

```java
// In tools module
@Service
@ConditionalOnProperty(value = "application.keycloak.enable", havingValue = "true")
public class KeycloakAuthenticationService { }

@Service
@ConditionalOnProperty(value = "application.keycloak.enable", havingValue = "false", matchIfMissing = true)
public class DapsAuthenticationService { }

// In connector module
@Configuration
@ConditionalOnProperty(value = "application.keycloak.enable", havingValue = "true")
public class KeycloakSecurityConfig {
    // 137 lines - CORS, headers, authz, Keycloak-specific filters
}

@Configuration
@ConditionalOnProperty(value = "application.keycloak.enable", havingValue = "false", matchIfMissing = true)
public class WebSecurityConfig {
    // 202 lines - CORS, headers, authz, DAPS-specific filters
    // 50+ lines EXACT DUPLICATE of KeycloakSecurityConfig!!!
}
```

**Problems:**
- ❌ 13 `@ConditionalOnProperty` scattered across codebase
- ❌ Inverted logic: DAPS uses `havingValue = "false"`
- ❌ ~50 lines of duplicated CORS/headers/authz code
- ❌ Two complete security configurations (137 + 202 lines)
- ❌ Hard to add 3rd provider (DCP): must update all conditionals
- ❌ Property name `keycloak.enable` doesn't make sense for DAPS

---

### After (Proposed State) ✅

```java
// In tools module - core
public enum AuthProviderType {
    KEYCLOAK, DAPS, DCP
}

@ConfigurationProperties("application.auth")
public class AuthenticationProperties {
    private AuthProviderType provider;
}

// In tools module - providers
@Component
@ConditionalOnProperty(name = "application.auth.provider", havingValue = "KEYCLOAK")
public class KeycloakSecurityConfigProvider implements SecurityConfigProvider {
    // Only Keycloak-specific logic
}

@Component
@ConditionalOnProperty(name = "application.auth.provider", havingValue = "DAPS")
public class DapsSecurityConfigProvider implements SecurityConfigProvider {
    // Only DAPS-specific logic
}

// In connector module - single unified config
@Configuration
public class SecurityConfig {
    private final SecurityConfigProvider provider; // Auto-injected based on property
    
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        // Common config (CORS, headers, authz)
        SecurityCommonConfig commonConfig = buildCommonConfig();
        
        // Delegate provider-specific auth to provider
        return provider.configureSecurityChain(http, commonConfig);
    }
}
```

**Benefits:**
- ✅ Single property: `application.auth.provider=KEYCLOAK|DAPS|DCP`
- ✅ No inverted logic: each provider explicitly named
- ✅ Zero duplication: CORS/headers/authz in one place
- ✅ One security config class (not two conditional ones)
- ✅ Easy to add DCP: just implement `DcpSecurityConfigProvider`
- ✅ Clear separation: tools=auth logic, connector=common security
- ✅ Type-safe enum (no typos)

---

## Configuration Examples

### Keycloak Setup
```properties
# Select provider
application.auth.provider=KEYCLOAK

# Keycloak-specific config
application.auth.keycloak.backend.client-id=dsp-connector-backend
application.auth.keycloak.backend.client-secret=my-secret
application.auth.keycloak.backend.token-url=http://keycloak:8080/realms/dsp/protocol/openid-connect/token
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://keycloak:8080/realms/dsp
```

### DAPS Setup (Basic Auth + JWT)
```properties
# Select provider
application.auth.provider=DAPS

# DAPS-specific config
application.auth.daps.daps-url=https://daps.aisec.fraunhofer.de
application.auth.daps.token-url=https://daps.aisec.fraunhofer.de/token
spring.ssl.bundle.jks.daps.keystore.location=/cert/daps-keystore.p12
spring.ssl.bundle.jks.daps.keystore.password=changeit
```

### DCP Setup (Future)
```properties
# Select provider
application.auth.provider=DCP

# DCP-specific config
application.auth.dcp.token-url=https://dcp.example.com/token
application.auth.dcp.verify-url=https://dcp.example.com/verify
application.auth.dcp.client-id=my-dcp-client
```

---

## How Security Configuration Works

### Flow Diagram

```
Application Startup
       │
       ├─ Read: application.auth.provider=KEYCLOAK
       │
       ├─ Spring conditional bean creation:
       │     @ConditionalOnProperty(name="application.auth.provider", havingValue="KEYCLOAK")
       │     ✓ KeycloakAuthenticationService created
       │     ✓ KeycloakSecurityConfigProvider created
       │     ✗ DapsAuthenticationService NOT created
       │     ✗ DapsSecurityConfigProvider NOT created
       │
       ├─ SecurityConfig needs SecurityConfigProvider bean
       │     └─ Spring injects: KeycloakSecurityConfigProvider
       │
       └─ SecurityFilterChain configured:
             │
             ├─ Common config (from SecurityConfig):
             │     • CORS: corsConfigurationSource()
             │     • Headers: XSS, HSTS, Frame Options
             │     • Authorization: /api/** = ADMIN, /connector/** = CONNECTOR
             │
             └─ Provider-specific auth (from KeycloakSecurityConfigProvider):
                   • OAuth2 Resource Server (JWT validation)
                   • KeycloakAuthenticationFilter
                   • KeycloakRealmRoleConverter (map roles)
```

### What Happens in Each Module?

#### Tools Module Responsibilities:
1. **Selects active provider** based on `application.auth.provider`
2. **Creates provider-specific beans**:
   - `KeycloakAuthenticationService` or `DapsAuthenticationService` (or `DcpAuthenticationService`)
   - `KeycloakSecurityConfigProvider` or `DapsSecurityConfigProvider` (or `DcpSecurityConfigProvider`)
3. **Implements authentication logic**:
   - Token fetching (for connector-to-connector)
   - Token validation
4. **Implements security configuration**:
   - Filter chain setup
   - JWT decoder configuration
   - Authentication manager setup

#### Connector Module Responsibilities:
1. **Defines common security concerns**:
   - CORS configuration
   - Security headers
   - Authorization rules (which endpoints need which roles)
2. **Receives provider implementation** via dependency injection
3. **Combines common + provider-specific** configuration
4. **Provides filters** that providers can use:
   - `KeycloakAuthenticationFilter`
   - `JwtAuthenticationFilter`
   - `DataspaceProtocolEndpointsAuthenticationFilter`

---

## Adding a New Provider (DCP Example)

### Step 1: Create DCP classes in tools module

```java
// 1. Authentication service
@Service
@ConditionalOnProperty(name = "application.auth.provider", havingValue = "DCP")
public class DcpAuthenticationService implements AuthProvider {
    @Override
    public String fetchToken() { /* DCP token fetching */ }
    
    @Override
    public boolean validateToken(String token) { /* DCP validation */ }
}

// 2. Properties
@ConfigurationProperties(prefix = "application.auth.dcp")
@ConditionalOnProperty(name = "application.auth.provider", havingValue = "DCP")
public class DcpAuthenticationProperties {
    private String tokenUrl;
    private String verifyUrl;
    private String clientId;
    // getters, setters
}

// 3. Security config provider
@Component
@ConditionalOnProperty(name = "application.auth.provider", havingValue = "DCP")
public class DcpSecurityConfigProvider implements SecurityConfigProvider {
    
    @Override
    public SecurityFilterChain configureSecurityChain(HttpSecurity http, SecurityCommonConfig commonConfig) {
        // DCP-specific filter chain
        DcpAuthenticationFilter dcpFilter = new DcpAuthenticationFilter(...);
        
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(commonConfig.getCorsConfigurationSource()))
            .headers(/* use common config */)
            .authorizeHttpRequests(/* use common config */)
            .addFilterBefore(dcpFilter, UsernamePasswordAuthenticationFilter.class);
            
        return http.build();
    }
    
    @Override
    public JwtDecoder jwtDecoder() {
        return new DcpJwtDecoder(...);
    }
}
```

### Step 2: Update enum

```java
public enum AuthProviderType {
    KEYCLOAK,
    DAPS,
    DCP  // Add this line
}
```

### Step 3: That's it!

No changes needed in:
- ✅ Connector module security config
- ✅ Existing Keycloak provider
- ✅ Existing DAPS provider
- ✅ Any other part of the codebase

Just configure and run:
```properties
application.auth.provider=DCP
application.auth.dcp.token-url=...
```

---

## Testing Strategy

### Unit Tests
```java
@SpringBootTest
@TestPropertySource(properties = {
    "application.auth.provider=KEYCLOAK",
    "application.auth.keycloak.backend.client-id=test-client"
})
class KeycloakSecurityTest {
    
    @Autowired
    private SecurityConfigProvider provider;
    
    @Test
    void shouldInjectKeycloakProvider() {
        assertThat(provider).isInstanceOf(KeycloakSecurityConfigProvider.class);
    }
}
```

### Integration Tests
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "application.auth.provider=DAPS")
class DapsAuthenticationIntegrationTest {
    
    @Test
    void shouldAuthenticateWithDapsCredentials() {
        // Test DAPS basic auth + JWT
    }
}
```

### Test Profiles
- `application-test.properties`: Default test config
- `application-test-keycloak.properties`: Test with Keycloak
- `application-test-dcp.properties`: Test with DCP

---

## Migration Plan

### Phase 1: Foundation (Backward Compatible) ✅
- Create new enum, properties, interfaces
- Support BOTH old and new properties:
  ```java
  @ConditionalOnProperty(name = {"application.auth.provider", "application.keycloak.enable"})
  ```
- No breaking changes

### Phase 2: Implement Providers ✅
- Create `SecurityConfigProvider` implementations
- Create unified `SecurityConfig` in connector
- Extract `CorsConfigProperties`
- Test with both old and new config

### Phase 3: Migrate Configurations 📝
- Update all `application*.properties` files
- Update Docker Compose files
- Update Terraform configs
- Update documentation

### Phase 4: Deprecate Old Approach 📝
- Add `@Deprecated` to old configs
- Log warnings when old properties used
- Update migration guide

### Phase 5: Clean Up 🗑️
- Remove `application.keycloak.enable` support
- Delete `KeycloakSecurityConfig` and `WebSecurityConfig`
- Remove deprecated code

---

## Benefits Summary

| Aspect | Before | After | Improvement |
|--------|--------|-------|-------------|
| **Configuration properties** | `application.keycloak.enable=true/false` | `application.auth.provider=KEYCLOAK` | ✅ Clear, explicit |
| **Conditionals** | 13 scattered `@ConditionalOnProperty` | 3 provider-specific (1 per provider) | ✅ 77% reduction |
| **Security config classes** | 2 (KeycloakSecurityConfig + WebSecurityConfig) | 1 (SecurityConfig) | ✅ 50% reduction |
| **Duplicated code** | ~50 lines CORS/headers/authz | 0 lines | ✅ Eliminated |
| **Adding new provider** | Update 13+ conditional locations | Create 3 new classes | ✅ Isolated change |
| **Type safety** | String "true"/"false" | Enum KEYCLOAK/DAPS/DCP | ✅ Compile-time checking |
| **Logic clarity** | DAPS uses inverted "false" | Each provider explicitly named | ✅ No confusion |
| **Module separation** | Auth logic mixed in connector | Auth logic in tools, common in connector | ✅ Clear boundaries |

---

## Questions & Answers

### Q: Why move security configuration to tools module?
**A:** Because authentication is provider-specific. Keycloak needs OAuth2 setup, DAPS needs JWT+Basic auth, DCP will need something different. Only common concerns (CORS, authz rules) stay in connector.

### Q: Won't this create circular dependencies? (connector → tools → connector)
**A:** No. Tools module provides interfaces and implementations. Connector provides concrete filters that tools uses. Dependency flow: `connector` → `tools` (one direction).

### Q: Can I still use multiple security filters?
**A:** Yes! Each `SecurityConfigProvider` can add as many filters as needed. DAPS uses 3 filters: `DataspaceProtocolEndpointsAuthenticationFilter`, `JwtAuthenticationFilter`, `BasicAuthenticationFilter`.

### Q: What if I need provider-specific endpoint authorization?
**A:** Add it in the provider's `configureSecurityChain()` method:
```java
http.authorizeHttpRequests(authorize -> {
    // Common rules from commonConfig
    authorize.requestMatchers(commonConfig.getAdminEndpoints())...
    
    // Provider-specific rules
    authorize.requestMatchers("/keycloak-only/**").hasRole("KEYCLOAK_USER");
});
```

### Q: How do I test without a real Keycloak/DAPS?
**A:** Mock the `SecurityConfigProvider` in tests, or use `@TestConfiguration` to provide a test implementation.

### Q: Can I switch providers at runtime?
**A:** No, by design. Auth provider is determined at startup. This is simpler and more secure.

---

## Next Steps

1. ✅ **Review this design** - Discuss any concerns or adjustments needed
2. 📝 **Approve approach** - Confirm this is the direction you want
3. 🛠️ **Start implementation** - Begin with Phase 1 (foundation)
4. 🧪 **Test backward compatibility** - Ensure old configs still work
5. 📚 **Update documentation** - Create migration guide
6. 🚀 **Deploy gradually** - Migrate environments one by one

**Ready to start implementing?** 🚀

