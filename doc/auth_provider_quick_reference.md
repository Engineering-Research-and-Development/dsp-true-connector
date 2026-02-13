# Authentication Provider Centralization - Quick Reference

> **Quick reference for developers implementing the authentication provider centralization**

---

## 📋 Overview

**Goal:** Centralize authentication provider logic using enum-based selection with Strategy pattern.

**Timeline:** 6 weeks  
**Key Property:** `application.auth.provider=KEYCLOAK|DAPS|DCP`

---

## 🗂️ Project Structure

```
tools/src/main/java/it/eng/tools/auth/
├── core/                    [NEW]
│   ├── AuthProviderType.java
│   ├── AuthenticationProperties.java
│   ├── SecurityConfigProvider.java
│   └── SecurityCommonConfig.java
├── config/                  [NEW]
│   ├── AuthenticationAutoConfiguration.java
│   └── SecurityAutoConfiguration.java
├── keycloak/
│   └── KeycloakSecurityConfigProvider.java  [NEW]
└── daps/
    └── DapsSecurityConfigProvider.java      [NEW]

connector/src/main/java/it/eng/connector/configuration/
├── SecurityConfig.java           [NEW - replaces KeycloakSecurityConfig + WebSecurityConfig]
├── CorsConfigProperties.java     [NEW - extracted CORS logic]
├── KeycloakSecurityConfig.java   [DEPRECATED]
└── WebSecurityConfig.java        [DEPRECATED]
```

---

## 🔧 Key Interfaces

### SecurityConfigProvider (Strategy Interface)

```java
public interface SecurityConfigProvider {
    SecurityFilterChain configureSecurityChain(
        HttpSecurity http, 
        SecurityCommonConfig commonConfig
    ) throws Exception;
    
    JwtDecoder jwtDecoder();  // nullable for DAPS
}
```

**Implementations:**
- `KeycloakSecurityConfigProvider` - Keycloak OAuth2/OIDC authentication
- `DapsSecurityConfigProvider` - DAPS JWT + Basic authentication
- `DcpSecurityConfigProvider` - Future DCP implementation

---

## 📝 Configuration Changes

### Before (Current)

```properties
# Old property
application.keycloak.enable=true

# Keycloak config
application.keycloak.backend.client-id=dsp-connector
application.keycloak.backend.client-secret=secret
application.keycloak.backend.token-url=http://keycloak:8080/realms/dsp/protocol/openid-connect/token
```

### After (New)

```properties
# New property (preferred)
application.auth.provider=KEYCLOAK

# Keycloak config (new prefix)
application.auth.keycloak.backend.client-id=dsp-connector
application.auth.keycloak.backend.client-secret=secret
application.auth.keycloak.backend.token-url=http://keycloak:8080/realms/dsp/protocol/openid-connect/token

# Old prefix still works (deprecated)
application.keycloak.backend.client-id=dsp-connector
```

### Backward Compatibility (Transition Period)

```properties
# Option 1: New property only (recommended)
application.auth.provider=KEYCLOAK

# Option 2: Old property only (deprecated, shows warning)
application.keycloak.enable=true

# Option 3: Both (shows deprecation warning)
application.auth.provider=KEYCLOAK
application.keycloak.enable=true  # Deprecated
```

---

## 🎯 Implementation Checklist

### Phase 1: Core Abstractions ✅
- [ ] `AuthProviderType` enum (KEYCLOAK, DAPS, DCP)
- [ ] `AuthenticationProperties` (@ConfigurationProperties)
- [ ] `SecurityConfigProvider` interface
- [ ] `SecurityCommonConfig` DTO
- [ ] Unit tests for all

### Phase 2: Provider Implementations ✅
- [ ] `KeycloakSecurityConfigProvider` implementation
- [ ] `DapsSecurityConfigProvider` implementation
- [ ] Update existing service @ConditionalOnProperty annotations
- [ ] `AuthenticationAutoConfiguration` (bean selection)
- [ ] `SecurityAutoConfiguration` (validation)
- [ ] spring.factories registration

### Phase 3: Connector Security ✅
- [ ] `CorsConfigProperties` (extract CORS logic)
- [ ] `SecurityConfig` (unified configuration)
- [ ] Deprecate `KeycloakSecurityConfig`
- [ ] Deprecate `WebSecurityConfig`

### Phase 4: Migration Support ✅
- [ ] `LegacyPropertyMigrationListener` (warnings)
- [ ] Update all application.properties files
- [ ] Update documentation (README, security.md, migration guide)

### Phase 5: Testing ✅
- [ ] Provider switching tests
- [ ] Security filter chain tests
- [ ] Authentication tests
- [ ] Property compatibility tests
- [ ] End-to-end tests
- [ ] TCK tests

---

## 🧪 Testing Commands

### Run Unit Tests
```powershell
mvn test -pl tools
mvn test -pl connector
```

### Run Integration Tests
```powershell
mvn verify -pl connector -Dtest=*IntegrationTest
```

### Run TCK Tests
```powershell
mvn verify -pl connector -Ptck
```

### Run with Specific Provider
```powershell
# Keycloak
mvn spring-boot:run -pl connector -Dspring-boot.run.arguments="--application.auth.provider=KEYCLOAK"

# DAPS
mvn spring-boot:run -pl connector -Dspring-boot.run.arguments="--application.auth.provider=DAPS"
```

### Docker Compose Test
```powershell
docker-compose -f docker-compose-multi-connector.yml up
```

---

## 🐛 Common Issues & Solutions

### Issue: "No matching bean of type SecurityConfigProvider"

**Cause:** Provider property not set or invalid value.

**Solution:**
```properties
# Set valid provider
application.auth.provider=KEYCLOAK  # or DAPS
```

### Issue: "Conflicting authentication provider properties"

**Cause:** Old and new properties set with different values.

**Solution:** Remove old property:
```properties
# Remove this:
# application.keycloak.enable=false

# Keep this:
application.auth.provider=KEYCLOAK
```

### Issue: Application starts but authentication doesn't work

**Cause:** Wrong provider selected or missing provider-specific properties.

**Solution:** Check logs for provider selection:
```
INFO: Authentication Provider: KEYCLOAK
INFO: Security Filter Chain: Configured
```

Verify provider-specific properties are set:
```properties
# For Keycloak
spring.security.oauth2.resourceserver.jwt.issuer-uri=...

# For DAPS
spring.ssl.bundle.jks.daps.keystore.location=...
```

### Issue: CORS errors after migration

**Cause:** CORS configuration not properly extracted.

**Solution:** Verify CORS properties:
```properties
application.cors.allowed.origins=http://localhost:4200
application.cors.allowed.methods=GET,POST,PUT,DELETE,OPTIONS
application.cors.allowed.headers=Authorization,Content-Type
application.cors.allowed.credentials=true
```

### Issue: Tests failing with wrong provider

**Cause:** Test properties not set correctly.

**Solution:** Add to test configuration:
```properties
# src/test/resources/application.properties
application.auth.provider=DAPS  # or KEYCLOAK
```

Or use @TestPropertySource:
```java
@SpringBootTest
@TestPropertySource(properties = {
    "application.auth.provider=KEYCLOAK"
})
public class MyTest { ... }
```

---

## 📦 Code Templates

### Template: New Authentication Provider

```java
// 1. Create provider service
@Service
@ConditionalOnProperty(name = "application.auth.provider", havingValue = "CUSTOM")
public class CustomAuthenticationService implements AuthProvider {
    @Override
    public String fetchToken() { /* implementation */ }
    
    @Override
    public boolean validateToken(String token) { /* implementation */ }
}

// 2. Create properties
@Component
@ConfigurationProperties(prefix = "application.auth.custom")
@ConditionalOnProperty(name = "application.auth.provider", havingValue = "CUSTOM")
public class CustomAuthenticationProperties {
    private String serverUrl;
    // getters/setters
}

// 3. Create security config provider
@Component
@ConditionalOnProperty(name = "application.auth.provider", havingValue = "CUSTOM")
public class CustomSecurityConfigProvider implements SecurityConfigProvider {
    
    private final CustomAuthenticationProperties properties;
    
    @Override
    public SecurityFilterChain configureSecurityChain(
            HttpSecurity http, 
            SecurityCommonConfig commonConfig) throws Exception {
        
        // Create custom filter
        CustomAuthenticationFilter customFilter = new CustomAuthenticationFilter();
        
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(commonConfig.getCorsConfigurationSource()))
            .headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .xssProtection(Customizer.withDefaults())
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(commonConfig.getAdminEndpoints()).hasRole("ADMIN")
                .requestMatchers(commonConfig.getConnectorEndpoints()).hasRole("CONNECTOR")
                .anyRequest().permitAll()
            )
            .addFilterBefore(customFilter, UsernamePasswordAuthenticationFilter.class);
            
        return http.build();
    }
    
    @Override
    public JwtDecoder jwtDecoder() {
        return new CustomJwtDecoder(properties.getServerUrl());
    }
}

// 4. Add to AuthProviderType enum
public enum AuthProviderType {
    KEYCLOAK("keycloak"),
    DAPS("daps"),
    DCP("dcp"),
    CUSTOM("custom");  // Add new value
    // ...
}

// 5. Update AuthenticationAutoConfiguration
@Bean
public AuthProvider authProvider(
        Optional<KeycloakAuthenticationService> keycloak,
        Optional<DapsAuthenticationService> daps,
        Optional<DcpAuthenticationService> dcp,
        Optional<CustomAuthenticationService> custom,  // Add
        AuthenticationProperties authProps) {
    
    return switch (authProps.getProvider()) {
        case KEYCLOAK -> keycloak.orElseThrow();
        case DAPS -> daps.orElseThrow();
        case DCP -> dcp.orElseThrow();
        case CUSTOM -> custom.orElseThrow();  // Add
    };
}
```

### Template: Integration Test

```java
@SpringBootTest
@TestPropertySource(properties = {
    "application.auth.provider=KEYCLOAK",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/test"
})
public class KeycloakProviderIntegrationTest {
    
    @Autowired
    private ApplicationContext context;
    
    @Test
    public void testKeycloakProviderIsActive() {
        AuthProvider provider = context.getBean(AuthProvider.class);
        assertThat(provider).isInstanceOf(KeycloakAuthenticationService.class);
    }
    
    @Test
    public void testSecurityFilterChainIsConfigured() {
        SecurityFilterChain filterChain = context.getBean(SecurityFilterChain.class);
        assertThat(filterChain).isNotNull();
    }
    
    @Test
    public void testKeycloakBeansArePresent() {
        assertThat(context.containsBean("keycloakAuthenticationService")).isTrue();
        assertThat(context.containsBean("keycloakSecurityConfigProvider")).isTrue();
        assertThat(context.containsBean("keycloakJwtDecoder")).isTrue();
    }
    
    @Test
    public void testDapsBeansAreNotPresent() {
        assertThat(context.containsBean("dapsAuthenticationService")).isFalse();
        assertThat(context.containsBean("dapsSecurityConfigProvider")).isFalse();
    }
}
```

---

## 🔍 Code Review Checklist

### For New Provider Implementation
- [ ] Implements `SecurityConfigProvider` interface
- [ ] Has `@ConditionalOnProperty(name="application.auth.provider", havingValue="...")`
- [ ] Properly implements `configureSecurityChain()`
- [ ] Properly implements `jwtDecoder()` (or returns null)
- [ ] Has comprehensive Javadoc
- [ ] Has unit tests (>80% coverage)
- [ ] Has integration tests
- [ ] Properties are documented

### For Modified Files
- [ ] @ConditionalOnProperty updated to use new property
- [ ] Backward compatibility maintained (if needed)
- [ ] Tests updated to use new property
- [ ] Javadoc updated
- [ ] No breaking changes
- [ ] All tests pass

### For Security Configuration
- [ ] CORS configuration is correct
- [ ] Security headers are applied
- [ ] Authorization rules are enforced
- [ ] Session management is disabled
- [ ] CSRF is properly configured
- [ ] Filter order is correct
- [ ] No security vulnerabilities

---

## 📚 Documentation Links

- **Implementation Plan:** `doc/auth_provider_implementation_plan.md`
- **Architecture Diagrams:** `doc/diagrams/auth_provider_architecture.md`
- **Task Breakdown:** `doc/auth_provider_task_breakdown.md`
- **Migration Guide:** `doc/AUTHENTICATION_MIGRATION.md` (to be created)
- **Security Documentation:** `doc/security.md`
- **Brainstorming Document:** `doc/auth_provider_centralization_brainstorm.md`

---

## 🎓 Key Concepts

### Strategy Pattern
The implementation uses Strategy pattern where:
- **Strategy Interface:** `SecurityConfigProvider`
- **Concrete Strategies:** `KeycloakSecurityConfigProvider`, `DapsSecurityConfigProvider`, `DcpSecurityConfigProvider`
- **Context:** `SecurityConfig` (connector module)

### Conditional Bean Creation
Beans are created conditionally based on `application.auth.provider`:
```java
@ConditionalOnProperty(name = "application.auth.provider", havingValue = "KEYCLOAK")
// Bean only created when provider is KEYCLOAK
```

### Auto-Configuration
Spring Boot auto-configuration selects the active provider:
```java
@Configuration
public class AuthenticationAutoConfiguration {
    @Bean
    public AuthProvider authProvider(...) {
        // Select based on AuthenticationProperties
    }
}
```

### Backward Compatibility
Legacy property support during transition:
```java
@PostConstruct
public void init() {
    if (legacyPropertyPresent && newPropertyAbsent) {
        migrateFromLegacyProperty();
        logDeprecationWarning();
    }
}
```

---

## 🚀 Getting Started

### For New Developers

1. **Read the brainstorming document** to understand the problem and solution
2. **Review the architecture diagrams** to understand the structure
3. **Read the implementation plan** to understand the phases
4. **Check out the task breakdown** to see what needs to be done
5. **Clone the repository** and set up your development environment
6. **Run existing tests** to ensure everything works
7. **Pick a task** from the task breakdown and start implementing

### For Code Reviewers

1. **Understand the goal** of the centralization
2. **Review the architecture** to understand the design
3. **Check the code review checklist** when reviewing PRs
4. **Verify tests** are comprehensive
5. **Ensure documentation** is updated
6. **Look for security issues**
7. **Verify backward compatibility**

### For QA Engineers

1. **Understand provider switching** mechanism
2. **Test all property combinations**
3. **Test with real Keycloak server**
4. **Test with mock DAPS server**
5. **Run TCK tests** for both providers
6. **Perform security testing**
7. **Document any issues** found

---

## 📞 Support

**Questions or Issues?**
- Check the troubleshooting section in this document
- Review the migration guide (when available)
- Check the security documentation
- Ask in team chat
- Create a GitHub issue

**Contributing:**
- Follow the task breakdown
- Write tests for all new code
- Update documentation
- Request code review before merging

---

*Last Updated: February 13, 2026*  
*Version: 1.0*  
*Status: Planning Phase*

