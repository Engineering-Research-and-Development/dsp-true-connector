# Authentication Provider Centralization - Design Brainstorm

## Current State Analysis

### Problems with Current Approach
1. **Scattered `@ConditionalOnProperty`**: Currently using `application.keycloak.enable` throughout the codebase
   - Found in 13 locations across security configs, services, and properties
   - DAPS uses inverted logic: `havingValue = "false", matchIfMissing = true`
   - Difficult to maintain and extend for a 3rd provider (DCP)

2. **Tight Coupling**: Security configurations (KeycloakSecurityConfig, WebSecurityConfig) are duplicated
3. **No Single Source of Truth**: Auth provider choice is implicit based on keycloak.enable flag
4. **Hard to Test**: Each provider needs complex conditional logic

---

## Proposed Solutions

### 🎯 **Solution 1: Enum-based Provider Selection (RECOMMENDED)**

**Concept**: Single property to select active auth provider

#### Configuration
```properties
# Single property to rule them all
application.auth.provider=KEYCLOAK  # Options: KEYCLOAK, DAPS, DCP

# Provider-specific configs (always present, only loaded if selected)
application.auth.keycloak.backend.client-id=...
application.auth.keycloak.backend.client-secret=...
application.auth.keycloak.backend.token-url=...

application.auth.daps.daps-url=...
application.auth.daps.keystore.location=...

application.auth.dcp.token-url=...
application.auth.dcp.verify-url=...
```

#### Implementation Structure
```
it.eng.tools.auth/
├── AuthProvider.java (interface - already exists)
├── AuthenticationCache.java (already exists)
├── AuthProviderType.java (NEW - enum)
├── AuthenticationProperties.java (NEW - central config)
├── config/
│   ├── AuthenticationAutoConfiguration.java (NEW)
│   └── SecurityConfigurationProvider.java (NEW - interface)
├── keycloak/
│   ├── KeycloakAuthenticationService.java (@ConditionalOnProperty(name="application.auth.provider", havingValue="KEYCLOAK"))
│   ├── KeycloakAuthenticationProperties.java
│   └── KeycloakSecurityConfigProvider.java (NEW)
├── daps/
│   ├── DapsAuthenticationService.java (@ConditionalOnProperty(name="application.auth.provider", havingValue="DAPS"))
│   ├── DapsAuthenticationProperties.java
│   ├── DapsCertificateProvider.java
│   └── DapsSecurityConfigProvider.java (NEW)
└── dcp/
    ├── DcpAuthenticationService.java (FUTURE)
    ├── DcpAuthenticationProperties.java (FUTURE)
    └── DcpSecurityConfigProvider.java (FUTURE)
```

#### Key Components

**AuthProviderType.java**
```java
public enum AuthProviderType {
    KEYCLOAK("keycloak"),
    DAPS("daps"),
    DCP("dcp");
    
    private final String value;
    // constructor, getters
}
```

**AuthenticationProperties.java**
```java
@ConfigurationProperties(prefix = "application.auth")
public class AuthenticationProperties {
    private AuthProviderType provider = AuthProviderType.DAPS; // default
    // getters/setters
}
```

**SecurityConfigurationProvider.java** (Strategy Pattern)
```java
public interface SecurityConfigurationProvider {
    SecurityFilterChain configure(HttpSecurity http) throws Exception;
    JwtDecoder jwtDecoder();
    AuthenticationManager authenticationManager();
}
```

**AuthenticationAutoConfiguration.java**
```java
@Configuration
public class AuthenticationAutoConfiguration {
    
    @Bean
    public AuthProvider authProvider(
            Optional<KeycloakAuthenticationService> keycloak,
            Optional<DapsAuthenticationService> daps,
            Optional<DcpAuthenticationService> dcp,
            AuthenticationProperties authProps) {
        
        return switch (authProps.getProvider()) {
            case KEYCLOAK -> keycloak.orElseThrow();
            case DAPS -> daps.orElseThrow();
            case DCP -> dcp.orElseThrow();
        };
    }
}
```

#### Advantages ✅
- **Single source of truth**: One property controls everything
- **Easy to extend**: Add new provider by creating new package + annotation
- **Clear intent**: `application.auth.provider=BASIC` is self-documenting
- **No inverted logic**: Each provider uses `havingValue="PROVIDER_NAME"`
- **Type-safe**: Enum prevents typos
- **Fail-fast**: Invalid provider name fails at startup, missing provider also fails
- **Testable**: Easy to mock/override in tests
- **Four authentication options**: BASIC (MongoDB users), KEYCLOAK (OAuth2/OIDC), DAPS (IDS), DCP (future)
- **Clean architecture**: No legacy compatibility code to maintain

#### Migration Path - Clean Break (No Legacy Support)
1. Add new enum and properties classes (with BASIC, KEYCLOAK, DAPS, DCP)
2. Update existing `@ConditionalOnProperty` to use new property name
3. **Remove all references to `application.keycloak.enable`** - no backward compatibility
4. Update all documentation and configuration files
5. Require explicit `application.auth.provider` in all deployments
6. Migration requires config change at deployment time (breaking change)

---

### 🎯 **Solution 2: Spring Profiles**

**Concept**: Use Spring profiles to activate auth providers

#### Configuration
```properties
# Activate via profile
spring.profiles.active=keycloak  # or daps, or dcp

# application-keycloak.properties
application.auth.keycloak.backend.client-id=...

# application-daps.properties
application.auth.daps.daps-url=...
```

#### Implementation
```java
@Configuration
@Profile("keycloak")
public class KeycloakAuthConfiguration { ... }

@Configuration
@Profile("daps")
public class DapsAuthConfiguration { ... }
```

#### Advantages ✅
- Native Spring feature
- Clean separation
- Easy profile composition (consumer + keycloak)

#### Disadvantages ❌
- Profiles often used for environment (dev, prod)
- Mixing concerns (environment vs auth provider)
- Less explicit than enum approach

---

### 🎯 **Solution 3: Factory Pattern with Registry**

**Concept**: Register all providers and select at runtime

#### Implementation
```java
@Component
public class AuthProviderRegistry {
    private final Map<String, AuthProvider> providers = new ConcurrentHashMap<>();
    
    public void register(String name, AuthProvider provider) {
        providers.put(name, provider);
    }
    
    public AuthProvider getActive() {
        String activeProvider = environment.getProperty("application.auth.provider");
        return providers.get(activeProvider);
    }
}

@Configuration
public class AuthProviderConfiguration {
    
    @Bean
    @ConditionalOnProperty(name = "application.auth.provider", havingValue = "keycloak")
    public AuthProvider keycloakProvider(...) {
        return new KeycloakAuthenticationService(...);
    }
    
    @Bean
    @ConditionalOnProperty(name = "application.auth.provider", havingValue = "daps")
    public AuthProvider dapsProvider(...) {
        return new DapsAuthenticationService(...);
    }
}
```

#### Advantages ✅
- Dynamic provider selection
- Runtime provider switching (if needed)
- Good for plugin architecture

#### Disadvantages ❌
- More complex than needed for this use case
- Runtime errors if provider not found
- Overhead of registry management

---

### 🎯 **Solution 4: Composite Configuration with Strategy**

**Concept**: Separate auth service selection from security configuration

#### Structure
```java
// Core abstraction
public interface AuthenticationStrategy {
    AuthProvider authProvider();
    SecurityFilterChain securityFilterChain(HttpSecurity http);
    JwtDecoder jwtDecoder();
}

@Configuration
public class AuthenticationStrategyConfiguration {
    
    @Bean
    @ConditionalOnProperty(name = "application.auth.provider", havingValue = "keycloak")
    public AuthenticationStrategy keycloakStrategy(...) {
        return new KeycloakAuthenticationStrategy(...);
    }
    
    @Bean
    @ConditionalOnProperty(name = "application.auth.provider", havingValue = "daps")
    public AuthenticationStrategy dapsStrategy(...) {
        return new DapsAuthenticationStrategy(...);
    }
}

// Each strategy encapsulates ALL provider-specific logic
public class KeycloakAuthenticationStrategy implements AuthenticationStrategy {
    
    @Override
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        // Keycloak-specific security configuration
    }
    
    @Override
    public AuthProvider authProvider() {
        return new KeycloakAuthenticationService(...);
    }
    
    @Override
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }
}
```

#### Advantages ✅
- Complete encapsulation per provider
- Single strategy bean injected everywhere
- No scattered conditional logic
- Easy to test individual strategies

#### Disadvantages ❌
- More classes to maintain
- Might be over-engineering for simple use case

---

## Comparison Matrix

| Aspect | Enum-based | Profiles | Factory Registry | Strategy Composite |
|--------|-----------|----------|------------------|-------------------|
| Simplicity | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |
| Type Safety | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| Extensibility | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| Testability | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| Maintainability | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| Migration Effort | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |
| Documentation | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ |

---

## Hybrid Recommendation: Enum-based with Strategy Elements

**Best of both worlds approach:**

1. **Use Enum for provider selection** (Solution 1)
   - Clear, simple, type-safe
   - Single property: `application.auth.provider=KEYCLOAK|DAPS|DCP`

2. **Encapsulate security configuration** (from Solution 4)
   - Each provider has its own `SecurityConfigProvider` implementation
   - Reduces duplication between KeycloakSecurityConfig and WebSecurityConfig

3. **Structured package organization**
   ```
   tools/src/main/java/it/eng/tools/auth/
   ├── core/                           # Shared interfaces and enums
   │   ├── AuthProvider.java
   │   ├── AuthProviderType.java
   │   ├── AuthenticationProperties.java
   │   └── SecurityConfigProvider.java
   ├── config/                         # Auto-configuration
   │   ├── AuthenticationAutoConfiguration.java
   │   └── SecurityAutoConfiguration.java
   ├── keycloak/
   │   ├── KeycloakAuthenticationService.java
   │   ├── KeycloakAuthenticationProperties.java
   │   └── KeycloakSecurityConfigProvider.java
   ├── daps/
   │   ├── DapsAuthenticationService.java
   │   ├── DapsAuthenticationProperties.java
   │   ├── DapsCertificateProvider.java
   │   └── DapsSecurityConfigProvider.java
   └── dcp/                            # Future
       ├── DcpAuthenticationService.java
       ├── DcpAuthenticationProperties.java
       └── DcpSecurityConfigProvider.java
   ```

4. **Connector module security configuration**
   ```
   connector/src/main/java/it/eng/connector/configuration/
   ├── SecurityConfig.java             # NEW - Single unified security config
   ├── CorsConfiguration.java          # EXTRACTED - Shared CORS logic
   ├── KeycloakSecurityConfig.java     # DEPRECATED - Will be removed
   ├── WebSecurityConfig.java          # DEPRECATED - Will be removed
   ├── KeycloakAuthenticationFilter.java  # Stays (used by KeycloakSecurityConfigProvider in tools)
   ├── KeycloakRealmRoleConverter.java    # Stays (used by KeycloakSecurityConfigProvider in tools)
   ├── JwtAuthenticationFilter.java       # Stays (used by DapsSecurityConfigProvider in tools)
   ├── JwtAuthenticationProvider.java     # Stays (used by DapsSecurityConfigProvider in tools)
   └── DataspaceProtocolEndpointsAuthenticationFilter.java  # Stays (shared by all)
   ```

---

## Connector Security Architecture

### Current Issues in Connector Module

**Problem 1: Complete Duplication**
- `KeycloakSecurityConfig.java` (137 lines) and `WebSecurityConfig.java` (202 lines)
- Both configure identical CORS logic (exact duplicate ~50 lines)
- Both configure identical authorization rules (admin/connector/api endpoints)
- Both configure identical security headers
- Only difference: authentication filters and JWT handling

**Problem 2: Conditional Configuration Classes**
```java
@Configuration
@ConditionalOnProperty(value = "application.keycloak.enable", havingValue = "true")
public class KeycloakSecurityConfig { ... }

@Configuration
@ConditionalOnProperty(value = "application.keycloak.enable", havingValue = "false", matchIfMissing = true)
public class WebSecurityConfig { ... }
```
This creates two completely separate security configurations when they should share most logic.

### Proposed Solution: Unified Security Configuration

#### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│  connector module                                            │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ SecurityConfig.java                                 │    │
│  │ (Single @Configuration, no conditionals)            │    │
│  │                                                      │    │
│  │  @Bean                                               │    │
│  │  SecurityFilterChain securityFilterChain(           │    │
│  │      HttpSecurity http,                             │    │
│  │      SecurityConfigProvider provider) {  ◄──────────┼────┼─── Injected from tools
│  │                                                      │    │
│  │      return provider.configureSecurityChain(http,   │    │
│  │          commonConfig());                           │    │
│  │  }                                                   │    │
│  │                                                      │    │
│  │  private SecurityCommonConfig commonConfig() {      │    │
│  │      // CORS, headers, authorization rules          │    │
│  │  }                                                   │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ CorsConfigProperties.java                           │    │
│  │ - Centralized CORS configuration                    │    │
│  │ - Shared by all auth providers                      │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  Filters (used by providers, no conditionals):              │
│  ├── KeycloakAuthenticationFilter.java                      │
│  ├── KeycloakRealmRoleConverter.java                        │
│  ├── JwtAuthenticationFilter.java                           │
│  ├── JwtAuthenticationProvider.java                         │
│  └── DataspaceProtocolEndpointsAuthenticationFilter.java    │
└──────────────────────────────────────────────────────────────┘
                              ▲
                              │ depends on
                              │
┌─────────────────────────────┴───────────────────────────────┐
│  tools module                                                │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ SecurityConfigProvider interface                    │    │
│  │                                                      │    │
│  │  SecurityFilterChain configureSecurityChain(        │    │
│  │      HttpSecurity http,                             │    │
│  │      SecurityCommonConfig commonConfig);            │    │
│  │                                                      │    │
│  │  JwtDecoder jwtDecoder();                           │    │
│  │  // ... other provider-specific beans               │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  Implementations (one per auth provider):                    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ BasicSecurityConfigProvider                         │    │
│  │ @ConditionalOnProperty(                             │    │
│  │     name="application.auth.provider",               │    │
│  │     havingValue="BASIC")                            │    │
│  │                                                      │    │
│  │  - Creates JwtAuthenticationFilter                  │    │
│  │  - Creates BasicAuthenticationFilter                │    │
│  │  - Creates UserDetailsService (MongoDB users)       │    │
│  │  - Configures form login + Basic auth               │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ KeycloakSecurityConfigProvider                      │    │
│  │ @ConditionalOnProperty(                             │    │
│  │     name="application.auth.provider",               │    │
│  │     havingValue="KEYCLOAK")                         │    │
│  │                                                      │    │
│  │  - Creates KeycloakAuthenticationFilter             │    │
│  │  - Creates JwtDecoder for Keycloak                  │    │
│  │  - Configures OAuth2 resource server                │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ DapsSecurityConfigProvider                          │    │
│  │ @ConditionalOnProperty(                             │    │
│  │     name="application.auth.provider",               │    │
│  │     havingValue="DAPS")                             │    │
│  │                                                      │    │
│  │  - Creates JwtAuthenticationFilter                  │    │
│  │  - Creates BasicAuthenticationFilter                │    │
│  │  - Creates UserDetailsService (MongoDB users)       │    │
│  │  - Configures form login + JWT                      │    │
│  └────────────────────────────────────────────────────┘    │
│  │     name="application.auth.provider",               │    │
│  │     havingValue="DAPS")                             │    │
│  │                                                      │    │
│  │  - Creates JwtAuthenticationFilter                  │    │
│  │  - Creates BasicAuthenticationFilter                │    │
│  │  - Creates UserDetailsService (MongoDB users)       │    │
│  │  - Configures form login + JWT                      │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ DcpSecurityConfigProvider (FUTURE)                  │    │
│  │ @ConditionalOnProperty(                             │    │
│  │     name="application.auth.provider",               │    │
│  │     havingValue="DCP")                              │    │
│  │                                                      │    │
│  │  - DCP-specific authentication logic                │    │
│  └────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### Implementation Details

#### 1. Tools Module: SecurityConfigProvider Interface

**Location**: `tools/src/main/java/it/eng/tools/auth/core/SecurityConfigProvider.java`

```java
package it.eng.tools.auth.core;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Strategy interface for provider-specific security configuration.
 * Each authentication provider (Keycloak, DAPS, DCP) implements this
 * to provide its specific security setup.
 */
public interface SecurityConfigProvider {

    /**
     * Configures the security filter chain with provider-specific authentication.
     * 
     * @param http Spring Security HttpSecurity builder
     * @param commonConfig Common configuration shared across all providers (CORS, headers, authz rules)
     * @return Configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    SecurityFilterChain configureSecurityChain(HttpSecurity http, SecurityCommonConfig commonConfig) throws Exception;
    
    /**
     * Provides a JwtDecoder for validating incoming tokens.
     * Implementation depends on the auth provider.
     * 
     * @return JwtDecoder instance, or null if provider doesn't use JWT validation
     */
    JwtDecoder jwtDecoder();
}
```

#### 2. Tools Module: SecurityCommonConfig (DTO)

**Location**: `tools/src/main/java/it/eng/tools/auth/core/SecurityCommonConfig.java`

```java
package it.eng.tools.auth.core;

import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;
import java.util.List;

/**
 * Common security configuration shared by all authentication providers.
 * Contains CORS settings, security headers, and authorization rules.
 */
public class SecurityCommonConfig {
    private final CorsConfigurationSource corsConfigurationSource;
    private final List<RequestMatcher> adminEndpoints;
    private final List<RequestMatcher> connectorEndpoints;
    private final List<RequestMatcher> apiEndpoints;
    
    // constructor, getters
}
```

#### 3. Tools Module: KeycloakSecurityConfigProvider

**Location**: `tools/src/main/java/it/eng/tools/auth/keycloak/KeycloakSecurityConfigProvider.java`

```java
package it.eng.tools.auth.keycloak;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

import it.eng.connector.configuration.KeycloakAuthenticationFilter;
import it.eng.connector.configuration.KeycloakRealmRoleConverter;
import it.eng.tools.auth.core.SecurityCommonConfig;
import it.eng.tools.auth.core.SecurityConfigProvider;

@Component
@ConditionalOnProperty(name = "application.auth.provider", havingValue = "KEYCLOAK")
public class KeycloakSecurityConfigProvider implements SecurityConfigProvider {

    private final KeycloakAuthenticationProperties properties;
    private final KeycloakRealmRoleConverter roleConverter;
    
    // constructor injection
    
    @Override
    public SecurityFilterChain configureSecurityChain(HttpSecurity http, SecurityCommonConfig commonConfig) throws Exception {
        JwtDecoder decoder = jwtDecoder();
        KeycloakAuthenticationFilter keycloakFilter = 
            new KeycloakAuthenticationFilter(decoder, roleConverter);
        
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(commonConfig.getCorsConfigurationSource()))
            .headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .xssProtection(Customizer.withDefaults())
                .cacheControl(Customizer.withDefaults())
                .httpStrictTransportSecurity(Customizer.withDefaults())
                .frameOptions(frame -> frame.sameOrigin())
            )
            .sessionManagement(sm -> sm.disable())
            .anonymous(anonymus -> anonymus.disable())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(commonConfig.getAdminEndpoints().toArray(new RequestMatcher[0])).hasRole("ADMIN")
                .requestMatchers(commonConfig.getConnectorEndpoints().toArray(new RequestMatcher[0])).hasRole("CONNECTOR")
                .requestMatchers(commonConfig.getApiEndpoints().toArray(new RequestMatcher[0])).hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .addFilterBefore(keycloakFilter, UsernamePasswordAuthenticationFilter.class);
            
        return http.build();
    }
    
    @Override
    public JwtDecoder jwtDecoder() {
        if (StringUtils.isNotBlank(properties.getIssuerUri())) {
            return JwtDecoders.fromIssuerLocation(properties.getIssuerUri());
        }
        if (StringUtils.isNotBlank(properties.getJwkSetUri())) {
            return NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        }
        throw new IllegalStateException("Keycloak issuer-uri or jwk-set-uri must be configured.");
    }
}
```

#### 4. Tools Module: DapsSecurityConfigProvider

**Location**: `tools/src/main/java/it/eng/tools/auth/daps/DapsSecurityConfigProvider.java`

```java
package it.eng.tools.auth.daps;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

import it.eng.connector.configuration.JwtAuthenticationFilter;
import it.eng.connector.configuration.JwtAuthenticationProvider;
import it.eng.connector.repository.UserRepository;
import it.eng.tools.auth.core.SecurityCommonConfig;
import it.eng.tools.auth.core.SecurityConfigProvider;

@Component
@ConditionalOnProperty(name = "application.auth.provider", havingValue = "DAPS", matchIfMissing = true)
public class DapsSecurityConfigProvider implements SecurityConfigProvider {

    private final JwtAuthenticationProvider jwtAuthProvider;
    private final UserRepository userRepository;
    private final ApplicationPropertiesService applicationPropertiesService;
    
    // constructor injection
    
    @Override
    public SecurityFilterChain configureSecurityChain(HttpSecurity http, SecurityCommonConfig commonConfig) throws Exception {
        AuthenticationManager authManager = authenticationManager();
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(authManager);
        BasicAuthenticationFilter basicFilter = new BasicAuthenticationFilter(authManager);
        DataspaceProtocolEndpointsAuthenticationFilter protocolFilter = 
            new DataspaceProtocolEndpointsAuthenticationFilter(applicationPropertiesService);
        
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(commonConfig.getCorsConfigurationSource()))
            .headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .xssProtection(Customizer.withDefaults())
                .cacheControl(Customizer.withDefaults())
                .httpStrictTransportSecurity(Customizer.withDefaults())
                .frameOptions(frame -> frame.sameOrigin())
            )
            .sessionManagement(sm -> sm.disable())
            .anonymous(anonymus -> anonymus.disable())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(commonConfig.getAdminEndpoints().toArray(new RequestMatcher[0])).hasRole("ADMIN")
                .requestMatchers(commonConfig.getConnectorEndpoints().toArray(new RequestMatcher[0])).hasRole("CONNECTOR")
                .requestMatchers(commonConfig.getApiEndpoints().toArray(new RequestMatcher[0])).hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .addFilterBefore(protocolFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(basicFilter, JwtAuthenticationFilter.class)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint));
            
        return http.build();
    }
    
    @Override
    public JwtDecoder jwtDecoder() {
        return null; // DAPS handles JWT differently (via JwtAuthenticationProvider)
    }
    
    private AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider daoProvider = new DaoAuthenticationProvider();
        daoProvider.setUserDetailsService(userDetailsService());
        daoProvider.setPasswordEncoder(new BCryptPasswordEncoder());
        return new ProviderManager(jwtAuthProvider, daoProvider);
    }
    
    private UserDetailsService userDetailsService() {
        return username -> userRepository.findByEmail(username)
            .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
    }
}
```

#### 5. Connector Module: Unified SecurityConfig

**Location**: `connector/src/main/java/it/eng/connector/configuration/SecurityConfig.java`

```java
package it.eng.connector.configuration;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import it.eng.tools.auth.core.SecurityCommonConfig;
import it.eng.tools.auth.core.SecurityConfigProvider;

/**
 * Unified security configuration for the connector.
 * Delegates provider-specific authentication to SecurityConfigProvider implementations.
 * 
 * This replaces both KeycloakSecurityConfig and WebSecurityConfig,
 * eliminating duplication and conditional configuration classes.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CorsConfigProperties corsProperties;
    private final SecurityConfigProvider securityConfigProvider;
    
    public SecurityConfig(CorsConfigProperties corsProperties, 
                         SecurityConfigProvider securityConfigProvider) {
        this.corsProperties = corsProperties;
        this.securityConfigProvider = securityConfigProvider;
    }
    
    /**
     * Main security filter chain.
     * Common configuration (CORS, headers, authorization) is defined here.
     * Provider-specific authentication is delegated to SecurityConfigProvider.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        SecurityCommonConfig commonConfig = commonSecurityConfig();
        return securityConfigProvider.configureSecurityChain(http, commonConfig);
    }
    
    /**
     * Common security configuration shared by all auth providers.
     */
    private SecurityCommonConfig commonSecurityConfig() {
        return SecurityCommonConfig.builder()
            .corsConfigurationSource(corsProperties.corsConfigurationSource())
            .adminEndpoints(Arrays.asList(
                new AntPathRequestMatcher("/env"),
                new AntPathRequestMatcher("/actuator/**")
            ))
            .connectorEndpoints(Arrays.asList(
                new AntPathRequestMatcher("/connector/**"),
                new AntPathRequestMatcher("/negotiations/**"),
                new AntPathRequestMatcher("/catalog/**"),
                new AntPathRequestMatcher("/transfers/**")
            ))
            .apiEndpoints(Arrays.asList(
                new AntPathRequestMatcher("/api/**")
            ))
            .build();
    }
}
```

#### 6. Connector Module: CorsConfigProperties

**Location**: `connector/src/main/java/it/eng/connector/configuration/CorsConfigProperties.java`

```java
package it.eng.connector.configuration;

import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Centralized CORS configuration.
 * Extracted from duplicated code in KeycloakSecurityConfig and WebSecurityConfig.
 */
@Component
public class CorsConfigProperties {

    @Value("${application.cors.allowed.origins:}")
    private String allowedOrigins;

    @Value("${application.cors.allowed.methods:}")
    private String allowedMethods;

    @Value("${application.cors.allowed.headers:}")
    private String allowedHeaders;

    @Value("${application.cors.allowed.credentials:}")
    private String allowedCredentials;
    
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addExposedHeader(HttpHeaders.CONTENT_DISPOSITION);

        if (StringUtils.isBlank(allowedOrigins)) {
            configuration.addAllowedOrigin("*");
        } else {
            configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        }

        if (StringUtils.isBlank(allowedMethods)) {
            configuration.addAllowedMethod("*");
        } else {
            configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));
        }

        if (StringUtils.isBlank(allowedHeaders)) {
            configuration.addAllowedHeader("*");
        } else {
            configuration.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        }

        configuration.setAllowCredentials(
            StringUtils.equals(allowedCredentials, "true")
        );

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

### Key Benefits of This Approach

#### ✅ Eliminates Duplication
- CORS configuration: **Was duplicated in 2 places** → Now in 1 place
- Authorization rules: **Was duplicated in 2 places** → Now in 1 place  
- Security headers: **Was duplicated in 2 places** → Now in 1 place
- **Total reduction: ~100 lines of duplicated code**

#### ✅ Single Configuration Class
- **Before**: 2 conditional `@Configuration` classes (KeycloakSecurityConfig, WebSecurityConfig)
- **After**: 1 unified SecurityConfig + provider implementations in tools module

#### ✅ Clean Separation of Concerns
- **Connector module**: Common security concerns (CORS, authz rules, headers)
- **Tools module**: Provider-specific authentication (Keycloak OAuth2, DAPS JWT+Basic, DCP)

#### ✅ Easy to Add DCP
Just create `DcpSecurityConfigProvider` in tools module:
```java
@Component
@ConditionalOnProperty(name = "application.auth.provider", havingValue = "DCP")
public class DcpSecurityConfigProvider implements SecurityConfigProvider {
    // DCP-specific implementation
}
```
No changes needed in connector module!

#### ✅ Testability
- Mock `SecurityConfigProvider` in tests
- Test CORS configuration independently
- Test each provider implementation in isolation

#### ✅ No Inverted Logic
All providers use positive conditions:
```java
havingValue = "KEYCLOAK"  // Not "havingValue = false"
havingValue = "DAPS"      // Clear and explicit
havingValue = "DCP"       // Easy to understand
```

### Migration Strategy for Connector Module

#### Phase 1: Create New Classes (No Breaking Changes)
1. Create `SecurityCommonConfig` in tools
2. Create `SecurityConfigProvider` interface in tools
3. Create `KeycloakSecurityConfigProvider` in tools (migrated from KeycloakSecurityConfig)
4. Create `DapsSecurityConfigProvider` in tools (migrated from WebSecurityConfig)
5. Create `CorsConfigProperties` in connector
6. Create `SecurityConfig` in connector

#### Phase 2: Support Both Old and New
- Keep old configs with `@ConditionalOnMissingBean(SecurityConfigProvider.class)`
- Allow gradual migration of deployments

#### Phase 3: Deprecate Old Configs
- Add `@Deprecated` to KeycloakSecurityConfig and WebSecurityConfig
- Update documentation
- Add warnings in logs if old configs are used

#### Phase 4: Remove Old Configs
- Delete KeycloakSecurityConfig.java
- Delete WebSecurityConfig.java
- Clean up associated tests

### Files to Keep in Connector Module

These classes are used by the SecurityConfigProvider implementations and should stay:

- ✅ `KeycloakAuthenticationFilter.java` - Used by KeycloakSecurityConfigProvider
- ✅ `KeycloakRealmRoleConverter.java` - Used by KeycloakSecurityConfigProvider
- ✅ `JwtAuthenticationFilter.java` - Used by DapsSecurityConfigProvider
- ✅ `JwtAuthenticationProvider.java` - Used by DapsSecurityConfigProvider
- ✅ `JwtAuthenticationToken.java` - Used by JwtAuthenticationProvider
- ✅ `DataspaceProtocolEndpointsAuthenticationFilter.java` - Shared by all providers
- ✅ `DataspaceProtocolEndpointsAuthenticationEntryPoint.java` - Shared by all providers

### Files to Remove in Connector Module

After migration is complete:
- ❌ `KeycloakSecurityConfig.java` - Logic moved to KeycloakSecurityConfigProvider in tools
- ❌ `WebSecurityConfig.java` - Logic moved to DapsSecurityConfigProvider in tools

---

## Implementation Roadmap

### Phase 1: Foundation (No Breaking Changes)
- [ ] Create `AuthProviderType` enum
- [ ] Create `AuthenticationProperties` with `application.auth.provider`
- [ ] Support BOTH old (`application.keycloak.enable`) and new property
- [ ] Add backward compatibility logic

### Phase 2: Refactor Existing Providers
- [ ] Update Keycloak annotations to use new property
- [ ] Update DAPS annotations to use new property
- [ ] Extract common security configuration logic
- [ ] Create `SecurityConfigProvider` interface
- [ ] Implement Keycloak and DAPS security config providers

### Phase 3: DCP Integration
- [ ] Create `dcp` package structure
- [ ] Implement `DcpAuthenticationService`
- [ ] Implement `DcpSecurityConfigProvider`
- [ ] Add DCP-specific properties

### Phase 4: Cleanup
- [ ] Deprecate `application.keycloak.enable`
- [ ] Update all documentation
- [ ] Update docker-compose and terraform configs
- [ ] Add migration guide

---

## Configuration Examples

### BASIC Authentication (MongoDB Users)
```properties
application.auth.provider=BASIC
# No additional properties required
# Uses existing MongoDB user management system
# Provides Basic Auth + form login for UI
```

### Keycloak (OAuth2/OIDC)
```properties
application.auth.provider=KEYCLOAK
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://keycloak:8080/realms/dsp-connector
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://keycloak:8080/realms/dsp-connector/protocol/openid-connect/certs
application.auth.keycloak.backend.client-id=dsp-connector-backend
application.auth.keycloak.backend.client-secret=secret
application.auth.keycloak.backend.token-url=http://keycloak:8080/realms/dsp-connector/protocol/openid-connect/token
application.auth.keycloak.backend.token-caching=true
```

### DAPS (IDS)
```properties
application.auth.provider=DAPS
spring.ssl.bundle.jks.daps.keystore.location=classpath:certs/daps-connector.jks
spring.ssl.bundle.jks.daps.keystore.password=password
spring.ssl.bundle.jks.daps.key.alias=connector-a
spring.ssl.bundle.jks.daps.keystore.type=PKCS12
# ... other DAPS-specific properties
```

### DCP (Future)
```properties
application.auth.provider=DCP
application.auth.dcp.token-url=https://dcp.example.com/token
application.auth.dcp.verify-url=https://dcp.example.com/verify
# ... dcp-specific properties
```

---

## Testing Strategy

### Unit Tests
```java
@TestConfiguration
class TestAuthConfig {
    @Bean
    @Primary
    AuthenticationProperties testAuthProperties() {
        var props = new AuthenticationProperties();
        props.setProvider(AuthProviderType.KEYCLOAK);
        return props;
    }
}
```

### Integration Tests
```java
@SpringBootTest
@TestPropertySource(properties = {
    "application.auth.provider=KEYCLOAK",
    "application.auth.keycloak.backend.client-id=test-client"
})
class KeycloakAuthIntegrationTest { ... }
```

### Test Profiles
- `application-test.properties`: Use DAPS by default (current behavior)
- `application-test-keycloak.properties`: Test with Keycloak
- `application-test-dcp.properties`: Test with DCP

---

## Security Considerations

1. **Fail-fast validation**: If provider is set but required properties missing, fail at startup
2. **Clear error messages**: "Auth provider 'DCP' selected but dcp.token-url is not configured"
3. **No default provider**: Force explicit configuration
4. **Property encryption**: Support encrypted properties for sensitive values
5. **Audit logging**: Log which auth provider is active at startup

---

## Documentation Updates

1. **README.md**: Add section on authentication providers
2. **doc/security.md**: Update with new configuration approach
3. **Migration guide**: Create AUTHENTICATION_MIGRATION.md
4. **Javadoc**: Document all new classes and interfaces
5. **Configuration reference**: List all `application.auth.*` properties

---

## Questions to Consider

1. **Default provider**: Should there be a default, or require explicit configuration?
   - Recommendation: No default - force explicit choice for security clarity

2. **Runtime switching**: Do you need to switch providers without restart?
   - Recommendation: No - simpler and more secure to require restart

3. **Multiple providers**: Could a connector support multiple auth providers simultaneously?
   - Recommendation: No for now - but architecture should allow future extension

4. **Backward compatibility**: How long to support old `application.keycloak.enable`?
   - **Decision: No backward compatibility** - Clean break, requires migration
   - Simplifies codebase, no legacy conditionals to maintain
   - Clear migration path in documentation
   - All deployments must update configuration files

5. **Property naming**: `application.auth.provider` vs `application.auth-provider` vs `application.authentication.provider`?
   - Recommendation: `application.auth.provider` (concise, consistent with existing patterns)

---

## Next Steps

**Choose your preferred approach**, and I can help you implement it! My recommendation is the **Hybrid approach** (Enum-based with Strategy elements) because it offers:

1. ✅ Clear, simple configuration
2. ✅ Easy to extend for DCP
3. ✅ Reduced duplication
4. ✅ Backward compatible
5. ✅ Maintainable long-term

Would you like me to:
1. Start implementing the recommended solution?
2. Create a proof-of-concept for one of the approaches?
3. Discuss any specific concerns or requirements?



