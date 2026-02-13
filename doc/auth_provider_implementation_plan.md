# Authentication Provider Centralization - Implementation Plan

## Executive Summary

This document provides a comprehensive, step-by-step implementation plan for centralizing authentication provider logic in the DSP True Connector. The implementation follows the **Hybrid Recommendation** from the brainstorming document: Enum-based provider selection with Strategy pattern elements.

**Goals:**
- ✅ Eliminate 13 instances of scattered `@ConditionalOnProperty` logic
- ✅ Remove ~100 lines of duplicated security configuration code
- ✅ Simplify adding new auth providers (e.g., DCP)
- ✅ Include BASIC authentication as first-class option (4 providers total: BASIC, KEYCLOAK, DAPS, DCP)
- ✅ Clean break from legacy properties - no backward compatibility
- ✅ Improve testability and maintainability

**Timeline:** 4-5 weeks (simplified without legacy support)

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Detailed Implementation Phases](#detailed-implementation-phases)
3. [File-by-File Changes](#file-by-file-changes)
4. [Testing Strategy](#testing-strategy)
5. [Migration Guide](#migration-guide)
6. [Risk Assessment](#risk-assessment)
7. [Success Metrics](#success-metrics)

---

## Architecture Overview

### Target Structure

```
tools/
└── src/main/java/it/eng/tools/auth/
    ├── AuthProvider.java                          [EXISTS - no changes]
    ├── AuthenticationCache.java                   [EXISTS - no changes]
    │
    ├── core/                                      [NEW PACKAGE]
    │   ├── AuthProviderType.java                  [NEW - enum: BASIC, KEYCLOAK, DAPS, DCP]
    │   ├── AuthenticationProperties.java          [NEW - central config]
    │   ├── SecurityConfigProvider.java            [NEW - strategy interface]
    │   └── SecurityCommonConfig.java              [NEW - shared config DTO]
    │
    ├── config/                                    [NEW PACKAGE]
    │   ├── AuthenticationAutoConfiguration.java   [NEW - bean selection]
    │   └── SecurityAutoConfiguration.java         [NEW - security setup]
    │
    ├── basic/                                     [NEW PACKAGE]
    │   ├── BasicAuthenticationService.java        [NEW - MongoDB user auth]
    │   └── BasicSecurityConfigProvider.java       [NEW - security strategy]
    │
    ├── keycloak/
    │   ├── KeycloakAuthenticationService.java     [MODIFY - update @ConditionalOnProperty]
    │   ├── KeycloakAuthenticationProperties.java  [MODIFY - update @ConditionalOnProperty]
    │   └── KeycloakSecurityConfigProvider.java    [NEW - security strategy]
    │
    ├── daps/
    │   ├── DapsAuthenticationService.java         [MODIFY - update @ConditionalOnProperty]
    │   ├── DapsAuthenticationProperties.java      [MODIFY - update @ConditionalOnProperty]
    │   ├── DapsCertificateProvider.java           [MODIFY - update @ConditionalOnProperty]
    │   └── DapsSecurityConfigProvider.java        [NEW - security strategy]
    │
    └── dcp/                                       [FUTURE - not in initial release]
        ├── DcpAuthenticationService.java
        ├── DcpAuthenticationProperties.java
        └── DcpSecurityConfigProvider.java

connector/
└── src/main/java/it/eng/connector/configuration/
    ├── SecurityConfig.java                        [NEW - unified security config]
    ├── CorsConfigProperties.java                  [NEW - extracted CORS logic]
    │
    ├── KeycloakSecurityConfig.java                [DEPRECATE - keep for backward compat]
    ├── WebSecurityConfig.java                     [DEPRECATE - keep for backward compat]
    │
    ├── KeycloakAuthenticationFilter.java          [KEEP - used by providers]
    ├── KeycloakRealmRoleConverter.java            [KEEP - used by providers]
    ├── JwtAuthenticationFilter.java               [KEEP - used by providers]
    ├── JwtAuthenticationProvider.java             [MODIFY - update @ConditionalOnProperty]
    ├── JwtAuthenticationToken.java                [KEEP - no changes]
    ├── DataspaceProtocolEndpointsAuthenticationFilter.java  [KEEP - shared]
    └── DataspaceProtocolEndpointsAuthenticationEntryPoint.java  [KEEP - shared]

connector/
└── src/main/java/it/eng/connector/
    ├── rest/api/UserApiController.java            [MODIFY - update @ConditionalOnProperty]
    └── service/UserService.java                   [MODIFY - update @ConditionalOnProperty]
```

### Property Changes

**Before (Old - being completely replaced):**
```properties
application.keycloak.enable=true
```

**After (New - clean break, no backward compatibility):**
```properties
# New property (required - no default)
application.auth.provider=BASIC  # Options: BASIC, KEYCLOAK, DAPS, DCP

# Note: Old property application.keycloak.enable is completely removed
```

---

## Detailed Implementation Phases

### Phase 1: Foundation (Week 1)
**Goal:** Create core interfaces and enums without breaking existing code

#### Tasks:

1. **Create Core Package Structure**
   - [ ] Create `tools/src/main/java/it/eng/tools/auth/core/` package
   - [ ] Create `tools/src/main/java/it/eng/tools/auth/config/` package

2. **Create AuthProviderType Enum**
   - [ ] Implement enum with KEYCLOAK, DAPS, DCP values
   - [ ] Add static fromString() method for property parsing
   - [ ] Add validation logic

3. **Create AuthenticationProperties**
   - [ ] Central configuration class with `@ConfigurationProperties`
   - [ ] Default to DAPS for backward compatibility
   - [ ] Support legacy `application.keycloak.enable` detection
   - [ ] Add validation rules

4. **Create SecurityConfigProvider Interface**
   - [ ] Define strategy interface for security configuration
   - [ ] Method: `configureSecurityChain(HttpSecurity, SecurityCommonConfig)`
   - [ ] Method: `jwtDecoder()`
   - [ ] Add comprehensive Javadoc

5. **Create SecurityCommonConfig DTO**
   - [ ] Container for shared security settings
   - [ ] CORS configuration source
   - [ ] Authorization rule matchers
   - [ ] Security headers configuration

**Deliverables:**
- 5 new core classes
- Unit tests for AuthProviderType
- Unit tests for AuthenticationProperties
- Documentation in Javadoc

**Risk:** None (new code only, no impact on existing functionality)

---

### Phase 2: Tools Module Refactoring (Week 2-3)

#### Part A: Keycloak Provider (Week 2)

1. **Create KeycloakSecurityConfigProvider**
   - [ ] Implement SecurityConfigProvider interface
   - [ ] Use `@ConditionalOnProperty(name="application.auth.provider", havingValue="KEYCLOAK")`
   - [ ] Extract security filter chain configuration logic
   - [ ] Extract JwtDecoder configuration
   - [ ] Inject KeycloakAuthenticationProperties
   - [ ] Create KeycloakAuthenticationFilter bean
   - [ ] Wire up KeycloakRealmRoleConverter

2. **Update KeycloakAuthenticationService**
   - [ ] Update `@ConditionalOnProperty` to use new property
   - [ ] Add backward compatibility conditional OR clause
   - [ ] Update documentation

3. **Update KeycloakAuthenticationProperties**
   - [ ] Update `@ConditionalOnProperty` to use new property
   - [ ] Add backward compatibility conditional OR clause
   - [ ] Add nested property prefix: `application.auth.keycloak`
   - [ ] Keep old prefix `application.keycloak` as alias

**Testing:**
- [ ] Unit test KeycloakSecurityConfigProvider in isolation
- [ ] Integration test with new property `application.auth.provider=KEYCLOAK`
- [ ] Integration test with legacy property `application.keycloak.enable=true`

#### Part B: DAPS Provider (Week 3)

1. **Create DapsSecurityConfigProvider**
   - [ ] Implement SecurityConfigProvider interface
   - [ ] Use `@ConditionalOnProperty(name="application.auth.provider", havingValue="DAPS", matchIfMissing=true)`
   - [ ] Extract security filter chain configuration logic
   - [ ] Return null for jwtDecoder() (DAPS uses JwtAuthenticationProvider)
   - [ ] Create AuthenticationManager with JwtAuthenticationProvider + DaoAuthenticationProvider
   - [ ] Create JwtAuthenticationFilter bean
   - [ ] Create BasicAuthenticationFilter bean
   - [ ] Create DataspaceProtocolEndpointsAuthenticationFilter bean
   - [ ] Wire up UserDetailsService from MongoDB

2. **Update DapsAuthenticationService**
   - [ ] Update `@ConditionalOnProperty` to use new property
   - [ ] Add backward compatibility conditional OR clause
   - [ ] Update documentation

3. **Update DapsAuthenticationProperties**
   - [ ] Update `@ConditionalOnProperty` to use new property
   - [ ] Add backward compatibility conditional OR clause
   - [ ] Add nested property prefix: `application.auth.daps`
   - [ ] Keep old prefix for backward compatibility

4. **Update DapsCertificateProvider**
   - [ ] Update `@ConditionalOnProperty` to use new property
   - [ ] Add backward compatibility conditional OR clause

**Testing:**
- [ ] Unit test DapsSecurityConfigProvider in isolation
- [ ] Integration test with new property `application.auth.provider=DAPS`
- [ ] Integration test with legacy property `application.keycloak.enable=false`
- [ ] Integration test with no property (should default to DAPS)

#### Part C: Auto-Configuration (End of Week 3)

1. **Create AuthenticationAutoConfiguration**
   - [ ] Bean factory for selecting active AuthProvider
   - [ ] Uses AuthenticationProperties to determine provider
   - [ ] Inject Optional<KeycloakAuthenticationService>
   - [ ] Inject Optional<DapsAuthenticationService>
   - [ ] Return active provider or throw clear exception
   - [ ] Add startup logging of selected provider

2. **Create SecurityAutoConfiguration**
   - [ ] Conditional bean creation for SecurityConfigProvider
   - [ ] Fail-fast validation if no provider configured
   - [ ] Add startup logging

3. **Add spring.factories**
   - [ ] Register auto-configurations in `META-INF/spring.factories`
   - [ ] Or use `@AutoConfiguration` if using Spring Boot 3.x

**Testing:**
- [ ] Test provider selection logic
- [ ] Test error handling for invalid provider
- [ ] Test error handling for missing provider configuration
- [ ] Test backward compatibility scenarios

**Deliverables:**
- 2 new SecurityConfigProvider implementations
- Updated @ConditionalOnProperty annotations (6 files)
- 2 auto-configuration classes
- Comprehensive test suite
- Updated properties documentation

**Risk:** Medium - requires careful testing of all conditional logic paths

---

### Phase 3: Connector Module Refactoring (Week 4)

#### Part A: Extract Common Configuration

1. **Create CorsConfigProperties**
   - [ ] Extract CORS configuration from both security configs
   - [ ] Use @Component with @Value bindings
   - [ ] Method: `corsConfigurationSource()`
   - [ ] Support all existing CORS properties:
     - `application.cors.allowed.origins`
     - `application.cors.allowed.methods`
     - `application.cors.allowed.headers`
     - `application.cors.allowed.credentials`

2. **Verify Filter Classes**
   - [ ] Ensure KeycloakAuthenticationFilter has public constructor
   - [ ] Ensure KeycloakRealmRoleConverter is accessible
   - [ ] Ensure JwtAuthenticationFilter has public constructor
   - [ ] Ensure JwtAuthenticationProvider is accessible
   - [ ] Ensure DataspaceProtocolEndpointsAuthenticationFilter is accessible

**Testing:**
- [ ] Unit test CorsConfigProperties
- [ ] Test with various CORS configurations
- [ ] Test default values when properties are blank

#### Part B: Create Unified SecurityConfig

1. **Create SecurityConfig**
   - [ ] Single @Configuration class
   - [ ] No @ConditionalOnProperty needed
   - [ ] Inject SecurityConfigProvider
   - [ ] Inject CorsConfigProperties
   - [ ] Bean: `securityFilterChain(HttpSecurity)`
   - [ ] Define commonSecurityConfig() method
   - [ ] Define endpoint matchers (admin, connector, api)
   - [ ] Delegate to securityConfigProvider.configureSecurityChain()

2. **Add @ConditionalOnMissingBean to Legacy Configs**
   - [ ] Add to KeycloakSecurityConfig
   - [ ] Add to WebSecurityConfig
   - [ ] This allows new config to take precedence
   - [ ] Legacy configs only activate if SecurityConfig not present

**Testing:**
- [ ] Test that new SecurityConfig is used when present
- [ ] Test that legacy configs still work in compatibility mode
- [ ] Test CORS configuration is applied correctly
- [ ] Test authorization rules are applied correctly
- [ ] Test with Keycloak provider
- [ ] Test with DAPS provider

**Deliverables:**
- CorsConfigProperties class
- Unified SecurityConfig class
- Updated legacy configs for backward compatibility
- Integration test suite

**Risk:** Medium-High - security configuration is critical

---

### Phase 4: Update Conditional Annotations (Week 4)

Update remaining @ConditionalOnProperty annotations to support both old and new properties.

#### Files to Update:

1. **connector/configuration/JwtAuthenticationProvider.java**
   ```java
   // OLD:
   @ConditionalOnProperty(value = "application.keycloak.enable", havingValue = "false", matchIfMissing = true)
   
   // NEW:
   @ConditionalOnExpression(
       "#{!'${application.auth.provider:DAPS}'.equals('KEYCLOAK') || " +
       "'${application.keycloak.enable:false}' == 'false'}"
   )
   ```

2. **connector/rest/api/UserApiController.java**
   - [ ] Update @ConditionalOnProperty
   - [ ] Add backward compatibility

3. **connector/service/UserService.java**
   - [ ] Update @ConditionalOnProperty
   - [ ] Add backward compatibility

**Note:** For @ConditionalOnExpression, we use Spring Expression Language to check both properties.

**Testing:**
- [ ] Test each updated class with new property
- [ ] Test each updated class with legacy property
- [ ] Test that beans are not created when wrong provider is active

**Deliverables:**
- 3 updated classes
- Unit tests for conditional bean creation

**Risk:** Low - straightforward annotation updates

---

### Phase 5: Property Migration Support (Week 5)

#### Part A: Backward Compatibility Layer

1. **Create LegacyPropertyMigrationListener**
   - [ ] ApplicationListener that runs on startup
   - [ ] Checks if `application.keycloak.enable` is present
   - [ ] Logs deprecation warning
   - [ ] Suggests migration to new property
   - [ ] If new property is missing, auto-translate old property

2. **Update AuthenticationProperties**
   - [ ] Add @PostConstruct validation
   - [ ] Check for conflicting properties
   - [ ] Detect legacy property usage
   - [ ] Auto-migrate if only legacy property present
   - [ ] Log migration actions

**Testing:**
- [ ] Test with only new property (no warnings)
- [ ] Test with only old property (warning + auto-migration)
- [ ] Test with both properties matching (warning only)
- [ ] Test with both properties conflicting (error)

#### Part B: Documentation

1. **Update README.md**
   - [ ] Add "Authentication Providers" section
   - [ ] Document all three providers (Keycloak, DAPS, DCP-future)
   - [ ] Show configuration examples
   - [ ] Link to security.md for details

2. **Update doc/security.md**
   - [ ] Replace old property documentation
   - [ ] Add new property documentation
   - [ ] Explain provider selection
   - [ ] Document each provider's specific properties
   - [ ] Add troubleshooting section

3. **Create doc/AUTHENTICATION_MIGRATION.md**
   - [ ] Step-by-step migration guide
   - [ ] Before/after property examples
   - [ ] Backward compatibility information
   - [ ] Timeline for deprecation
   - [ ] FAQ section

4. **Update application.properties Templates**
   - [ ] Update all application-*.properties files
   - [ ] Comment out old properties with deprecation notice
   - [ ] Add new properties with examples
   - [ ] Files to update:
     - connector/src/main/resources/application-consumer.properties
     - connector/src/main/resources/application-provider.properties
     - ci/docker/connector_a_resources/application.properties
     - ci/docker/connector_b_resources/application.properties
     - ci/docker/keycloak_resources/application-keycloak-a.properties
     - ci/docker/keycloak_resources/application-keycloak-b.properties
     - terraform/app-resources/connector_a_resources/application.properties
     - terraform/app-resources/connector_b_resources/application.properties

**Deliverables:**
- Property migration listener
- 3 updated documentation files
- 8+ updated property files
- Migration guide

**Risk:** Low - documentation and warnings

---

### Phase 6: Testing & Validation (Week 5-6)

#### Integration Testing

1. **Provider Switching Tests**
   - [ ] Test switching from DAPS to Keycloak (property change + restart)
   - [ ] Test switching from Keycloak to DAPS (property change + restart)
   - [ ] Verify correct authentication mechanism is active
   - [ ] Verify correct security filters are applied

2. **End-to-End Tests**
   - [ ] Start connector with DAPS provider
   - [ ] Authenticate with JWT token
   - [ ] Authenticate with Basic Auth
   - [ ] Test catalog endpoint access
   - [ ] Test negotiation endpoint access
   - [ ] Restart with Keycloak provider
   - [ ] Authenticate with Keycloak token
   - [ ] Test same endpoints
   - [ ] Verify role-based access control

3. **Backward Compatibility Tests**
   - [ ] Test with old property only (Keycloak enabled)
   - [ ] Test with old property only (Keycloak disabled)
   - [ ] Test with new property only (KEYCLOAK)
   - [ ] Test with new property only (DAPS)
   - [ ] Test with both properties (consistent values)
   - [ ] Test with both properties (conflicting values - should error)

4. **Docker Compose Tests**
   - [ ] Update docker-compose-multi-connector.yml
   - [ ] Test connector A with Keycloak
   - [ ] Test connector B with Keycloak
   - [ ] Test connector-to-connector communication

5. **TCK Tests**
   - [ ] Run full TCK test suite with DAPS provider
   - [ ] Run full TCK test suite with Keycloak provider
   - [ ] Verify all tests pass

#### Security Validation

1. **CORS Tests**
   - [ ] Verify CORS headers are applied correctly
   - [ ] Test with allowed origins
   - [ ] Test with blocked origins
   - [ ] Test with wildcards

2. **Authorization Tests**
   - [ ] Test /admin endpoints require ADMIN role
   - [ ] Test /connector endpoints require CONNECTOR role
   - [ ] Test /api endpoints require ADMIN role
   - [ ] Test unauthorized access is blocked
   - [ ] Test role conversion (Keycloak realm roles)

3. **Security Headers Tests**
   - [ ] Verify X-Content-Type-Options header
   - [ ] Verify X-XSS-Protection header
   - [ ] Verify Cache-Control header
   - [ ] Verify HSTS header
   - [ ] Verify X-Frame-Options header

**Deliverables:**
- Comprehensive test suite (50+ tests)
- Test execution report
- Security validation report
- Performance baseline

**Risk:** Low - validation phase

---

### Phase 7: Deployment & Deprecation (Week 6)

#### Part A: Staged Rollout

1. **Version 1.0 (Current Release)**
   - [ ] Release with new architecture
   - [ ] Keep backward compatibility
   - [ ] Add deprecation warnings
   - [ ] Update all documentation
   - [ ] Announce deprecation timeline

2. **Version 1.1 (Next Minor Release)**
   - [ ] Continue backward compatibility
   - [ ] Louder deprecation warnings
   - [ ] Update default configs to use new property
   - [ ] Add migration tool/script

3. **Version 2.0 (Next Major Release)**
   - [ ] Remove old property support
   - [ ] Remove LegacyPropertyMigrationListener
   - [ ] Remove @ConditionalOnExpression fallbacks
   - [ ] Simplify to single @ConditionalOnProperty per class
   - [ ] Remove KeycloakSecurityConfig
   - [ ] Remove WebSecurityConfig
   - [ ] Update all tests

#### Part B: Migration Communication

1. **Release Notes**
   - [ ] Highlight authentication provider changes
   - [ ] Include migration guide link
   - [ ] Show before/after examples
   - [ ] Mention backward compatibility period
   - [ ] Announce deprecation timeline

2. **User Communication**
   - [ ] Email to users/customers
   - [ ] Update project wiki
   - [ ] Update issue tracker/FAQ
   - [ ] Provide migration support

**Deliverables:**
- Release notes
- Migration communication
- Deprecation timeline document

**Risk:** Low - communication only

---

## File-by-File Changes

### New Files to Create (17 files)

#### Tools Module (9 files)

1. **tools/src/main/java/it/eng/tools/auth/core/AuthProviderType.java**
   - Enum: KEYCLOAK, DAPS, DCP
   - ~30 lines

2. **tools/src/main/java/it/eng/tools/auth/core/AuthenticationProperties.java**
   - @ConfigurationProperties(prefix = "application.auth")
   - provider: AuthProviderType
   - ~80 lines

3. **tools/src/main/java/it/eng/tools/auth/core/SecurityConfigProvider.java**
   - Interface with 2 methods
   - ~40 lines (mostly Javadoc)

4. **tools/src/main/java/it/eng/tools/auth/core/SecurityCommonConfig.java**
   - DTO/Builder pattern
   - ~100 lines

5. **tools/src/main/java/it/eng/tools/auth/config/AuthenticationAutoConfiguration.java**
   - @Configuration
   - AuthProvider bean factory
   - ~60 lines

6. **tools/src/main/java/it/eng/tools/auth/config/SecurityAutoConfiguration.java**
   - @Configuration
   - Validation and logging
   - ~40 lines

7. **tools/src/main/java/it/eng/tools/auth/keycloak/KeycloakSecurityConfigProvider.java**
   - Implements SecurityConfigProvider
   - ~150 lines

8. **tools/src/main/java/it/eng/tools/auth/daps/DapsSecurityConfigProvider.java**
   - Implements SecurityConfigProvider
   - ~200 lines

9. **tools/src/main/resources/META-INF/spring.factories**
   - Register auto-configurations
   - ~5 lines

#### Connector Module (3 files)

10. **connector/src/main/java/it/eng/connector/configuration/SecurityConfig.java**
    - Unified security configuration
    - ~100 lines

11. **connector/src/main/java/it/eng/connector/configuration/CorsConfigProperties.java**
    - Extracted CORS logic
    - ~80 lines

12. **connector/src/main/java/it/eng/connector/util/LegacyPropertyMigrationListener.java**
    - ApplicationListener for migration warnings
    - ~100 lines

#### Documentation (5 files)

13. **doc/AUTHENTICATION_MIGRATION.md**
    - Migration guide
    - ~500 lines

14. **doc/auth_provider_architecture.md**
    - Architecture documentation
    - ~300 lines

15. **Example configuration files** (consider as separate updates)

### Files to Modify (15 files)

#### Tools Module (5 files)

1. **tools/src/main/java/it/eng/tools/auth/keycloak/KeycloakAuthenticationService.java**
   - Update @ConditionalOnProperty annotation
   - ~5 lines changed

2. **tools/src/main/java/it/eng/tools/auth/keycloak/KeycloakAuthenticationProperties.java**
   - Update @ConditionalOnProperty annotation
   - Update @ConfigurationProperties prefix
   - ~10 lines changed

3. **tools/src/main/java/it/eng/tools/auth/daps/DapsAuthenticationService.java**
   - Update @ConditionalOnProperty annotation
   - ~5 lines changed

4. **tools/src/main/java/it/eng/tools/auth/daps/DapsAuthenticationProperties.java**
   - Update @ConditionalOnProperty annotation
   - Update @ConfigurationProperties prefix
   - ~10 lines changed

5. **tools/src/main/java/it/eng/tools/auth/daps/DapsCertificateProvider.java**
   - Update @ConditionalOnProperty annotation
   - ~5 lines changed

#### Connector Module (7 files)

6. **connector/src/main/java/it/eng/connector/configuration/KeycloakSecurityConfig.java**
   - Add @ConditionalOnMissingBean(SecurityConfigProvider.class)
   - Add @Deprecated annotation
   - Add deprecation Javadoc
   - ~10 lines changed

7. **connector/src/main/java/it/eng/connector/configuration/WebSecurityConfig.java**
   - Add @ConditionalOnMissingBean(SecurityConfigProvider.class)
   - Add @Deprecated annotation
   - Add deprecation Javadoc
   - ~10 lines changed

8. **connector/src/main/java/it/eng/connector/configuration/JwtAuthenticationProvider.java**
   - Update @ConditionalOnProperty to @ConditionalOnExpression
   - ~8 lines changed

9. **connector/src/main/java/it/eng/connector/rest/api/UserApiController.java**
   - Update @ConditionalOnProperty annotation
   - ~5 lines changed

10. **connector/src/main/java/it/eng/connector/service/UserService.java**
    - Update @ConditionalOnProperty annotation
    - ~5 lines changed

11. **connector/src/main/resources/application-consumer.properties**
    - Add new properties
    - Comment old properties
    - ~20 lines changed

12. **connector/src/main/resources/application-provider.properties**
    - Add new properties
    - Comment old properties
    - ~20 lines changed

#### Documentation (3 files)

13. **README.md**
    - Add authentication providers section
    - ~50 lines added

14. **doc/security.md**
    - Update authentication configuration
    - ~100 lines changed

15. **doc/update_properties.md**
    - Document new properties
    - ~50 lines added

### Files to Delete (Later - Version 2.0)

1. connector/src/main/java/it/eng/connector/configuration/KeycloakSecurityConfig.java
2. connector/src/main/java/it/eng/connector/configuration/WebSecurityConfig.java
3. connector/src/main/java/it/eng/connector/util/LegacyPropertyMigrationListener.java

---

## Testing Strategy

### Unit Tests (30+ tests)

#### Core Classes
- AuthProviderType
  - [ ] Test fromString() conversion
  - [ ] Test valueOf() with invalid input
  - [ ] Test enum values

- AuthenticationProperties
  - [ ] Test property binding
  - [ ] Test default values
  - [ ] Test validation rules
  - [ ] Test legacy property detection
  - [ ] Test conflicting properties handling

- SecurityCommonConfig
  - [ ] Test builder pattern
  - [ ] Test immutability
  - [ ] Test null handling

#### Provider Implementations
- KeycloakSecurityConfigProvider
  - [ ] Test configureSecurityChain()
  - [ ] Test jwtDecoder() with issuer-uri
  - [ ] Test jwtDecoder() with jwk-set-uri
  - [ ] Test jwtDecoder() with neither (should throw)
  - [ ] Mock HttpSecurity and verify configuration

- DapsSecurityConfigProvider
  - [ ] Test configureSecurityChain()
  - [ ] Test jwtDecoder() returns null
  - [ ] Test authenticationManager() creation
  - [ ] Test userDetailsService() creation
  - [ ] Mock dependencies

#### Auto-Configuration
- AuthenticationAutoConfiguration
  - [ ] Test Keycloak provider selection
  - [ ] Test DAPS provider selection
  - [ ] Test DCP provider selection (future)
  - [ ] Test error when no provider available
  - [ ] Test error when invalid provider specified

- SecurityAutoConfiguration
  - [ ] Test conditional bean creation
  - [ ] Test logging of selected provider

### Integration Tests (25+ tests)

#### Provider Switching Tests
- [ ] Test application starts with KEYCLOAK provider
- [ ] Test application starts with DAPS provider
- [ ] Test application starts with legacy property (keycloak=true)
- [ ] Test application starts with legacy property (keycloak=false)
- [ ] Test application fails with invalid provider

#### Security Filter Chain Tests
- [ ] Test Keycloak filter chain is applied correctly
- [ ] Test DAPS filter chain is applied correctly
- [ ] Test CORS configuration is applied
- [ ] Test security headers are applied
- [ ] Test authorization rules are applied

#### Authentication Tests
- [ ] Test Keycloak JWT authentication
- [ ] Test Keycloak role extraction
- [ ] Test DAPS JWT authentication
- [ ] Test DAPS Basic authentication
- [ ] Test unauthorized access is blocked

#### Property Compatibility Tests
- [ ] Test new property only (KEYCLOAK)
- [ ] Test new property only (DAPS)
- [ ] Test old property only (true)
- [ ] Test old property only (false)
- [ ] Test both properties (consistent)
- [ ] Test both properties (conflicting - should fail)

### End-to-End Tests (15+ tests)

#### Connector Operation Tests
- [ ] Start connector with DAPS, test catalog operations
- [ ] Start connector with Keycloak, test catalog operations
- [ ] Test negotiation with DAPS
- [ ] Test negotiation with Keycloak
- [ ] Test transfer with DAPS
- [ ] Test transfer with Keycloak

#### Multi-Connector Tests
- [ ] Connector A (Keycloak) ↔ Connector B (Keycloak)
- [ ] Connector A (DAPS) ↔ Connector B (DAPS)
- [ ] Connector A (Keycloak) ↔ Connector B (DAPS) - should work
- [ ] Test catalog request between connectors
- [ ] Test negotiation between connectors

#### Docker Compose Tests
- [ ] Test with docker-compose-multi-connector.yml
- [ ] Test with keycloak enabled
- [ ] Test without keycloak (DAPS mode)

### TCK Tests
- [ ] Run full TCK suite with DAPS provider
- [ ] Run full TCK suite with Keycloak provider
- [ ] Verify all tests pass

### Performance Tests
- [ ] Measure authentication overhead (Keycloak vs DAPS)
- [ ] Measure startup time (new vs old)
- [ ] Measure memory usage (new vs old)
- [ ] Ensure no regressions

### Security Tests
- [ ] OWASP dependency check
- [ ] Security header validation
- [ ] CORS validation
- [ ] Role-based access control validation
- [ ] Token validation tests

---

## Migration Guide

### For Developers

#### Current Configuration (Before Migration)

**With Keycloak:**
```properties
application.keycloak.enable=true
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://keycloak:8080/realms/dsp-connector
application.keycloak.backend.client-id=dsp-connector-backend
application.keycloak.backend.client-secret=secret
application.keycloak.backend.token-url=http://keycloak:8080/realms/dsp-connector/protocol/openid-connect/token
```

**With DAPS:**
```properties
application.keycloak.enable=false
# OR omit the property entirely
```

#### New Configuration (After Migration)

**With Keycloak:**
```properties
application.auth.provider=KEYCLOAK
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://keycloak:8080/realms/dsp-connector
application.auth.keycloak.backend.client-id=dsp-connector-backend
application.auth.keycloak.backend.client-secret=secret
application.auth.keycloak.backend.token-url=http://keycloak:8080/realms/dsp-connector/protocol/openid-connect/token
```

**With DAPS:**
```properties
application.auth.provider=DAPS
# DAPS-specific properties remain the same
```

#### Backward Compatibility (Transition Period)

During the transition period (versions 1.0-1.1), both configurations are supported:

**Option 1: Use new property**
```properties
application.auth.provider=KEYCLOAK
```

**Option 2: Use old property (deprecated, shows warning)**
```properties
application.keycloak.enable=true
```

**Option 3: Use both (for explicit clarity, shows deprecation warning)**
```properties
application.auth.provider=KEYCLOAK
application.keycloak.enable=true  # Deprecated but consistent
```

**⚠️ Error Case: Conflicting properties**
```properties
application.auth.provider=DAPS
application.keycloak.enable=true  # CONFLICT! Application will fail to start
```

#### Property Prefix Changes

Some properties are being reorganized for consistency:

**Old:**
```properties
application.keycloak.backend.client-id=...
application.keycloak.backend.client-secret=...
application.keycloak.backend.token-url=...
```

**New (with backward compatibility):**
```properties
# Preferred:
application.auth.keycloak.backend.client-id=...
application.auth.keycloak.backend.client-secret=...
application.auth.keycloak.backend.token-url=...

# Also works (deprecated):
application.keycloak.backend.client-id=...
application.keycloak.backend.client-secret=...
application.keycloak.backend.token-url=...
```

### For Deployers

#### Docker Compose Changes

**Before:**
```yaml
environment:
  - application.keycloak.enable=true
  - spring.security.oauth2.resourceserver.jwt.issuer-uri=http://keycloak:8080/realms/dsp-connector
```

**After:**
```yaml
environment:
  - application.auth.provider=KEYCLOAK
  - spring.security.oauth2.resourceserver.jwt.issuer-uri=http://keycloak:8080/realms/dsp-connector
```

#### Kubernetes/Terraform Changes

Update your ConfigMaps or terraform variables:

**Before:**
```hcl
environment_variables = {
  "application.keycloak.enable" = "true"
}
```

**After:**
```hcl
environment_variables = {
  "application.auth.provider" = "KEYCLOAK"
}
```

### Migration Checklist

- [ ] Review current authentication configuration
- [ ] Identify which provider is being used (Keycloak or DAPS)
- [ ] Update application.properties files
- [ ] Update docker-compose files (if applicable)
- [ ] Update terraform configs (if applicable)
- [ ] Update CI/CD pipelines
- [ ] Test application startup
- [ ] Verify authentication works
- [ ] Check for deprecation warnings in logs
- [ ] Plan for version 2.0 (removal of old property)

### Troubleshooting

#### Issue: Application won't start - "No matching bean of type SecurityConfigProvider"

**Cause:** Neither Keycloak nor DAPS provider is active.

**Solution:** Set `application.auth.provider` to either KEYCLOAK or DAPS.

#### Issue: Application won't start - "Conflicting authentication provider properties"

**Cause:** Both old and new properties are set with conflicting values.

**Solution:** Remove old property (`application.keycloak.enable`) and use only new property.

#### Issue: Deprecation warnings in logs

**Cause:** Using old property `application.keycloak.enable`.

**Solution:** Migrate to new property `application.auth.provider` to silence warnings.

#### Issue: Authentication not working after migration

**Cause:** Incorrect provider selection or misconfigured properties.

**Solution:**
1. Check logs for which provider is active
2. Verify all provider-specific properties are correctly set
3. Verify token format matches provider expectations
4. Check JWT decoder configuration (Keycloak only)

---

## Risk Assessment

### High Risk Areas

1. **Security Filter Chain Configuration**
   - **Risk:** Incorrect filter chain could block legitimate requests or allow unauthorized access
   - **Mitigation:**
     - Comprehensive security testing
     - Keep legacy configs as fallback (with @ConditionalOnMissingBean)
     - Code review by security team
     - Penetration testing before release

2. **Property Migration**
   - **Risk:** Breaking existing deployments
   - **Mitigation:**
     - Maintain backward compatibility for 2 versions
     - Clear deprecation warnings
     - Comprehensive migration guide
     - Auto-migration listener
     - Extensive testing of all property combinations

### Medium Risk Areas

3. **Conditional Bean Creation**
   - **Risk:** Wrong beans might be created/missing based on properties
   - **Mitigation:**
     - Fail-fast validation at startup
     - Clear error messages
     - Integration tests for all property combinations
     - Startup logging of active provider

4. **Provider-Specific Logic**
   - **Risk:** Provider-specific authentication logic might be incorrectly extracted
   - **Mitigation:**
     - Careful extraction of logic from existing configs
     - Side-by-side comparison of old vs new
     - Test with real authentication servers (Keycloak, DAPS)
     - End-to-end tests

### Low Risk Areas

5. **Documentation Updates**
   - **Risk:** Outdated documentation could confuse users
   - **Mitigation:**
     - Update all documentation in same PR
     - Review by technical writers
     - User testing of migration guide

6. **Test Suite Maintenance**
   - **Risk:** Tests might need updates for new structure
   - **Mitigation:**
     - Update tests incrementally with code changes
     - Maintain test coverage above 80%
     - Add new tests for new functionality

### Risk Mitigation Strategy

1. **Phased Rollout:**
   - Phase 1: Internal testing (1 week)
   - Phase 2: Beta testing with select users (1 week)
   - Phase 3: General release
   - Phase 4: Monitor for issues

2. **Rollback Plan:**
   - Keep old configs available with @Deprecated
   - Document rollback procedure
   - Maintain separate release branch for hotfixes

3. **Monitoring:**
   - Add metrics for authentication success/failure rates
   - Monitor application startup failures
   - Track deprecation warning frequency
   - Monitor performance metrics

---

## Success Metrics

### Code Quality Metrics

- [ ] **Reduce Conditional Annotations:** From 13 to 3 (77% reduction)
- [ ] **Reduce Code Duplication:** Remove ~100 lines of duplicated security config (50% reduction in security config LOC)
- [ ] **Improve Test Coverage:** Maintain or increase coverage (target: >80%)
- [ ] **Reduce Cyclomatic Complexity:** Security config complexity reduced by ~30%
- [ ] **Documentation:** All public APIs documented with Javadoc

### Functional Metrics

- [ ] **All Existing Tests Pass:** 100% pass rate
- [ ] **TCK Compliance:** 100% TCK tests pass with both providers
- [ ] **Backward Compatibility:** All legacy configurations work with deprecation warnings
- [ ] **Provider Switching:** Successfully switch between providers with config change only

### Performance Metrics

- [ ] **Startup Time:** No increase >5%
- [ ] **Authentication Latency:** No increase >2%
- [ ] **Memory Usage:** No increase >3%
- [ ] **Throughput:** Maintain or improve requests/second

### User Experience Metrics

- [ ] **Migration Time:** Users can migrate in <30 minutes
- [ ] **Documentation Quality:** >90% positive feedback on migration guide
- [ ] **Support Tickets:** <5 tickets related to migration issues
- [ ] **Adoption Rate:** >80% of users migrated to new property within 3 months

### Development Efficiency Metrics

- [ ] **Time to Add New Provider:** Reduced from 2 weeks to 3 days
- [ ] **Lines of Code for New Provider:** ~200 LOC vs ~400 LOC previously
- [ ] **Test Writing Time:** Reduced by ~40% due to better structure

---

## Future Enhancements (Post-Implementation)

### Phase 8: DCP Provider (Future)

When ready to add DCP (Decentralized Connector Protocol) support:

1. **Create DCP Package** (1 week)
   - [ ] tools/src/main/java/it/eng/tools/auth/dcp/
   - [ ] DcpAuthenticationService.java
   - [ ] DcpAuthenticationProperties.java
   - [ ] DcpSecurityConfigProvider.java

2. **Configuration** (2 days)
   - [ ] Add DCP value to AuthProviderType enum
   - [ ] Add @ConditionalOnProperty(havingValue="DCP")
   - [ ] Define DCP-specific properties

3. **Testing** (3 days)
   - [ ] Unit tests
   - [ ] Integration tests
   - [ ] End-to-end tests

**Effort:** ~2 weeks (vs 6+ weeks without this architecture)

### Additional Improvements

1. **Dynamic Provider Loading**
   - [ ] Plugin-based architecture
   - [ ] Load providers from external JARs
   - [ ] Service Provider Interface (SPI) pattern

2. **Provider Health Checks**
   - [ ] Add actuator endpoint for auth provider status
   - [ ] Monitor token fetch success rates
   - [ ] Alert on authentication failures

3. **Multi-Provider Support**
   - [ ] Allow multiple providers simultaneously
   - [ ] Provider selection based on request headers
   - [ ] Provider chaining/fallback

4. **Configuration UI**
   - [ ] Admin UI for changing auth provider
   - [ ] Real-time property updates (Spring Cloud Config)
   - [ ] Provider testing from UI

5. **Enhanced Caching**
   - [ ] Distributed cache for tokens (Redis)
   - [ ] Cache statistics and monitoring
   - [ ] Smart cache invalidation

---

## Appendix

### A. Property Reference

#### Core Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `application.auth.provider` | Enum | DAPS | Active authentication provider (KEYCLOAK, DAPS, DCP) |

#### Keycloak Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | String | - | Keycloak realm issuer URI |
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | String | - | Keycloak JWK set URI |
| `application.auth.keycloak.backend.client-id` | String | - | OAuth2 client ID |
| `application.auth.keycloak.backend.client-secret` | String | - | OAuth2 client secret |
| `application.auth.keycloak.backend.token-url` | String | - | Token endpoint URL |
| `application.auth.keycloak.backend.token-caching` | Boolean | false | Enable token caching |

#### DAPS Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `spring.ssl.bundle.jks.daps.keystore.location` | String | - | DAPS keystore location |
| `spring.ssl.bundle.jks.daps.keystore.password` | String | - | DAPS keystore password |
| `spring.ssl.bundle.jks.daps.key.alias` | String | - | DAPS key alias |
| `application.auth.daps.daps-url` | String | - | DAPS server URL |
| `application.auth.daps.token-url` | String | - | DAPS token endpoint |

#### Legacy Properties (Deprecated)

| Property | Type | Default | Description | Migration |
|----------|------|---------|-------------|-----------|
| `application.keycloak.enable` | Boolean | false | Enable Keycloak auth | Use `application.auth.provider=KEYCLOAK` |
| `application.keycloak.backend.*` | Various | - | Keycloak settings | Use `application.auth.keycloak.backend.*` |

### B. Code Examples

#### Example: Custom Security Config Provider

```java
package com.example.auth.custom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;

import it.eng.tools.auth.core.SecurityCommonConfig;
import it.eng.tools.auth.core.SecurityConfigProvider;

@Component
@ConditionalOnProperty(name = "application.auth.provider", havingValue = "CUSTOM")
public class CustomSecurityConfigProvider implements SecurityConfigProvider {

    @Override
    public SecurityFilterChain configureSecurityChain(HttpSecurity http, SecurityCommonConfig commonConfig) throws Exception {
        // Custom authentication filter
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
                .anyRequest().permitAll()
            )
            .addFilterBefore(customFilter, UsernamePasswordAuthenticationFilter.class);
            
        return http.build();
    }
    
    @Override
    public JwtDecoder jwtDecoder() {
        return new CustomJwtDecoder();
    }
}
```

#### Example: Testing with Different Providers

```java
@SpringBootTest
public class AuthProviderTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public AuthenticationProperties testAuthProperties() {
            AuthenticationProperties props = new AuthenticationProperties();
            props.setProvider(AuthProviderType.KEYCLOAK);
            return props;
        }
    }
    
    @Test
    public void testKeycloakProvider() {
        // Test with Keycloak
    }
}

@SpringBootTest
@TestPropertySource(properties = {
    "application.auth.provider=DAPS"
})
public class DapsProviderTest {
    
    @Test
    public void testDapsProvider() {
        // Test with DAPS
    }
}
```

### C. Glossary

- **DAPS:** Dynamic Attribute Provisioning Service - IDS standard authentication
- **DCP:** Decentralized Connector Protocol - Future authentication provider
- **JWT:** JSON Web Token
- **OIDC:** OpenID Connect
- **OAuth2:** Open Authorization framework
- **IDS:** International Data Spaces
- **TCK:** Technology Compatibility Kit
- **SPI:** Service Provider Interface

### D. References

- [Spring Security Documentation](https://docs.spring.io/spring-security/reference/)
- [Spring Boot Conditional Annotations](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration.condition-annotations)
- [IDS Authentication](https://github.com/International-Data-Spaces-Association/IDS-G)
- [Keycloak Documentation](https://www.keycloak.org/documentation)

---

## Conclusion

This implementation plan provides a comprehensive, step-by-step approach to centralizing authentication provider logic in the DSP True Connector. The phased approach minimizes risk while allowing for incremental progress and validation.

**Key Takeaways:**
- 6-week implementation timeline
- Maintains backward compatibility
- Clear migration path
- Comprehensive testing strategy
- Reduced code duplication
- Easier to extend with new providers

**Next Steps:**
1. Review and approve this plan
2. Create GitHub issues for each phase
3. Assign team members
4. Begin Phase 1 implementation
5. Regular progress reviews

**Questions or Feedback:**
Please provide feedback on this plan before implementation begins. Key decision points:
- Backward compatibility duration (currently: 2 versions)
- Default provider (currently: DAPS)
- Property naming (currently: `application.auth.provider`)
- Migration timeline (currently: 6 weeks)

---

*Document Version: 1.0*  
*Last Updated: February 13, 2026*  
*Author: GitHub Copilot*  
*Status: Draft - Awaiting Review*




