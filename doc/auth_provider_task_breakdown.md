# Authentication Provider Centralization - Task Breakdown

## Project Overview

**Project Name:** Authentication Provider Centralization  
**Start Date:** TBD  
**Target Completion:** 6 weeks from start  
**Team Size:** 2-3 developers  
**Priority:** High  

---

## Phase 1: Foundation (Week 1) - 5 days

### Task 1.1: Create Core Package Structure
**Assigned to:** TBD  
**Estimated:** 2 hours  
**Status:** Not Started  

**Subtasks:**
- [ ] Create `tools/src/main/java/it/eng/tools/auth/core/` package
- [ ] Create `tools/src/main/java/it/eng/tools/auth/config/` package
- [ ] Update package documentation

**Acceptance Criteria:**
- Package structure exists
- Package-info.java files created
- Build succeeds

---

### Task 1.2: Create AuthProviderType Enum
**Assigned to:** TBD  
**Estimated:** 4 hours  
**Status:** Not Started  
**Depends on:** Task 1.1

**Subtasks:**
- [ ] Create AuthProviderType.java in core package
- [ ] Define enum values: BASIC, KEYCLOAK, DAPS, DCP
- [ ] Add getValue() method returning lowercase string
- [ ] Add static fromString() method for parsing
- [ ] Add validation logic for invalid values
- [ ] Write unit tests (6+ tests)
  - Test valueOf() with valid inputs (all 4 providers)
  - Test valueOf() with invalid inputs (expect exception)
  - Test fromString() with valid inputs
  - Test fromString() with case-insensitive inputs
  - Test fromString() with invalid inputs
  - Test getValue() for all providers

**Files to Create:**
- `tools/src/main/java/it/eng/tools/auth/core/AuthProviderType.java` (~35 lines)
- `tools/src/test/java/it/eng/tools/auth/core/AuthProviderTypeTest.java` (~120 lines)

**Acceptance Criteria:**
- Enum compiles without errors
- All 4 provider values defined (BASIC, KEYCLOAK, DAPS, DCP)
- All unit tests pass
- Code coverage >90%
- Javadoc is complete

---

### Task 1.3: Create AuthenticationProperties
**Assigned to:** TBD  
**Estimated:** 4 hours  
**Status:** Not Started  
**Depends on:** Task 1.2

**Subtasks:**
- [ ] Create AuthenticationProperties.java in core package
- [ ] Add @ConfigurationProperties(prefix = "application.auth")
- [ ] Add provider field (AuthProviderType)
- [ ] **No default value** - must be explicitly set
- [ ] Add @PostConstruct validation method
- [ ] Fail fast if provider is null (not configured)
- [ ] Write unit tests (6+ tests)
  - Test property binding with new property (all 4 providers)
  - Test validation fails when provider not set
  - Test validation passes when provider is set
  - Test with invalid provider value (should fail at binding)
  - Test @ConfigurationProperties prefix is correct
  - Test all four enum values bind correctly

**Files to Create:**
- `tools/src/main/java/it/eng/tools/auth/core/AuthenticationProperties.java` (~60 lines)
- `tools/src/test/java/it/eng/tools/auth/core/AuthenticationPropertiesTest.java` (~120 lines)

**Acceptance Criteria:**
- Properties bind correctly from application.properties
- Application fails fast at startup if provider not configured
- Clear error message when provider missing
- All unit tests pass
- Code coverage >85%

---

### Task 1.4: Create SecurityConfigProvider Interface
**Assigned to:** TBD  
**Estimated:** 3 hours  
**Status:** Not Started  
**Depends on:** Task 1.1

**Subtasks:**
- [ ] Create SecurityConfigProvider.java interface in core package
- [ ] Define method: configureSecurityChain(HttpSecurity, SecurityCommonConfig)
- [ ] Define method: jwtDecoder() returning JwtDecoder (nullable)
- [ ] Write comprehensive Javadoc
  - Explain strategy pattern
  - Document method parameters and return values
  - Provide usage examples
  - Document null return for jwtDecoder() (DAPS case)

**Files to Create:**
- `tools/src/main/java/it/eng/tools/auth/core/SecurityConfigProvider.java` (~40 lines, mostly Javadoc)

**Acceptance Criteria:**
- Interface compiles without errors
- Javadoc is clear and comprehensive
- Interface is documented in architecture diagram

---

### Task 1.5: Create SecurityCommonConfig DTO
**Assigned to:** TBD  
**Estimated:** 4 hours  
**Status:** Not Started  
**Depends on:** Task 1.4

**Subtasks:**
- [ ] Create SecurityCommonConfig.java in core package
- [ ] Add fields:
  - corsConfigurationSource (CorsConfigurationSource)
  - adminEndpoints (List<RequestMatcher>)
  - connectorEndpoints (List<RequestMatcher>)
  - apiEndpoints (List<RequestMatcher>)
- [ ] Implement Builder pattern
- [ ] Make immutable (final fields, no setters)
- [ ] Add validation in builder
- [ ] Write unit tests (5+ tests)
  - Test builder creation
  - Test immutability
  - Test null handling
  - Test validation
  - Test getter methods

**Files to Create:**
- `tools/src/main/java/it/eng/tools/auth/core/SecurityCommonConfig.java` (~100 lines)
- `tools/src/test/java/it/eng/tools/auth/core/SecurityCommonConfigTest.java` (~80 lines)

**Acceptance Criteria:**
- Class is immutable
- Builder pattern works correctly
- All unit tests pass
- Code coverage >85%

---

## Phase 2: Tools Module Refactoring (Week 2-3) - 10 days

### Task 2.1: Create BasicSecurityConfigProvider
**Assigned to:** TBD  
**Estimated:** 1 day  
**Status:** Not Started  
**Depends on:** Task 1.4, Task 1.5

**Subtasks:**
- [ ] Create basic/ package in tools/auth/
- [ ] Create BasicAuthenticationService.java (minimal or reuse existing)
- [ ] Create BasicSecurityConfigProvider.java
- [ ] Add @Component annotation
- [ ] Add @ConditionalOnProperty(name="application.auth.provider", havingValue="BASIC")
- [ ] Implement SecurityConfigProvider interface
- [ ] Inject JwtAuthenticationProvider
- [ ] Inject UserRepository
- [ ] Implement configureSecurityChain() method:
  - Extract logic from WebSecurityConfig (form login + basic auth)
  - Create AuthenticationManager with DaoAuthenticationProvider
  - Create JwtAuthenticationFilter  
  - Create BasicAuthenticationFilter
  - Apply common config (CORS, headers, authz rules)
- [ ] Implement jwtDecoder() method:
  - Return null (BASIC uses JwtAuthenticationProvider)
- [ ] Write unit tests (10+ tests)

**Files to Create:**
- `tools/src/main/java/it/eng/tools/auth/basic/BasicAuthenticationService.java` (~50 lines)
- `tools/src/main/java/it/eng/tools/auth/basic/BasicSecurityConfigProvider.java` (~180 lines)
- `tools/src/test/java/it/eng/tools/auth/basic/BasicSecurityConfigProviderTest.java` (~200 lines)

**Acceptance Criteria:**
- Provider compiles without errors
- Logic supports MongoDB users with Basic Auth + form login
- All unit tests pass
- Integration test with mock dependencies passes
- Code coverage >80%

---

### Task 2.2: Create KeycloakSecurityConfigProvider
**Assigned to:** TBD  
**Estimated:** 1 day  
**Status:** Not Started  
**Depends on:** Task 1.4, Task 1.5

**Subtasks:**
- [ ] Create KeycloakSecurityConfigProvider.java in keycloak package
- [ ] Add @Component annotation
- [ ] Add @ConditionalOnProperty(name="application.auth.provider", havingValue="KEYCLOAK")
- [ ] Implement SecurityConfigProvider interface
- [ ] Inject KeycloakAuthenticationProperties
- [ ] Inject KeycloakRealmRoleConverter
- [ ] Implement configureSecurityChain() method:
  - Extract logic from KeycloakSecurityConfig
  - Create KeycloakAuthenticationFilter
  - Apply common config (CORS, headers, authz rules)
  - Add Keycloak-specific filters
- [ ] Implement jwtDecoder() method:
  - Check issuer-uri property
  - Check jwk-set-uri property
  - Create appropriate JwtDecoder
  - Throw exception if neither is set
- [ ] Write unit tests (10+ tests)

**Files to Create:**
- `tools/src/main/java/it/eng/tools/auth/keycloak/KeycloakSecurityConfigProvider.java` (~150 lines)
- `tools/src/test/java/it/eng/tools/auth/keycloak/KeycloakSecurityConfigProviderTest.java` (~200 lines)

**Acceptance Criteria:**
- Provider compiles without errors
- Logic matches existing KeycloakSecurityConfig
- All unit tests pass
- Integration test with mock HttpSecurity passes
- Code coverage >80%

---

### Task 2.3: Update KeycloakAuthenticationService
**Assigned to:** TBD  
**Estimated:** 1 hour  
**Status:** Not Started  
**Depends on:** Task 1.3

**Subtasks:**
- [ ] Update @ConditionalOnProperty annotation
  - OLD: `value = "application.keycloak.enable", havingValue = "true"`
  - NEW: `name = "application.auth.provider", havingValue = "KEYCLOAK"`
- [ ] Update Javadoc
- [ ] Run existing unit tests to verify no regression
- [ ] Update integration tests to use new property

**Files to Modify:**
- `tools/src/main/java/it/eng/tools/auth/keycloak/KeycloakAuthenticationService.java` (~3 lines)

**Acceptance Criteria:**
- Bean is created when new property is set to KEYCLOAK
- Bean is NOT created when new property is set to other providers
- All existing tests still pass
- No functional changes

---

### Task 2.4: Update KeycloakAuthenticationProperties
**Assigned to:** TBD  
**Estimated:** 2 hours  
**Status:** Not Started  
**Depends on:** Task 1.3

**Subtasks:**
- [ ] Update @ConditionalOnProperty annotation
- [ ] Update @ConfigurationProperties prefix to `application.auth.keycloak`
- [ ] Update Javadoc
- [ ] Write tests for new property prefix

**Files to Modify:**
- `tools/src/main/java/it/eng/tools/auth/keycloak/KeycloakAuthenticationProperties.java` (~5 lines)

**Acceptance Criteria:**
- Properties bind with new prefix
- All tests pass

---

### Task 2.4: Create DapsSecurityConfigProvider
**Assigned to:** TBD  
**Estimated:** 1.5 days  
**Status:** Not Started  
**Depends on:** Task 1.4, Task 1.5

**Subtasks:**
- [ ] Create DapsSecurityConfigProvider.java in daps package
- [ ] Add @Component annotation
- [ ] Add @ConditionalOnProperty(name="application.auth.provider", havingValue="DAPS", matchIfMissing=true)
- [ ] Implement SecurityConfigProvider interface
- [ ] Inject JwtAuthenticationProvider
- [ ] Inject UserRepository
- [ ] Inject ApplicationPropertiesService
- [ ] Implement configureSecurityChain() method:
  - Extract logic from WebSecurityConfig
  - Create AuthenticationManager with:
    - JwtAuthenticationProvider
    - DaoAuthenticationProvider
  - Create JwtAuthenticationFilter
  - Create BasicAuthenticationFilter
  - Create DataspaceProtocolEndpointsAuthenticationFilter
  - Apply common config (CORS, headers, authz rules)
  - Add DAPS-specific filters
- [ ] Implement jwtDecoder() method:
  - Return null (DAPS uses JwtAuthenticationProvider)
- [ ] Create private helper methods:
  - authenticationManager()
  - userDetailsService()
- [ ] Write unit tests (12+ tests)

**Files to Create:**
- `tools/src/main/java/it/eng/tools/auth/daps/DapsSecurityConfigProvider.java` (~200 lines)
- `tools/src/test/java/it/eng/tools/auth/daps/DapsSecurityConfigProviderTest.java` (~250 lines)

**Acceptance Criteria:**
- Provider compiles without errors
- Logic matches existing WebSecurityConfig
- All unit tests pass
- Integration test with mock dependencies passes
- Code coverage >80%

---

### Task 2.5: Update DapsAuthenticationService
**Assigned to:** TBD  
**Estimated:** 2 hours  
**Status:** Not Started  
**Depends on:** Task 1.3

**Subtasks:**
- [ ] Update @ConditionalOnProperty annotation
  - OLD: `value = "application.keycloak.enable", havingValue = "false", matchIfMissing = true`
  - NEW: `value = "application.auth.provider", havingValue = "DAPS", matchIfMissing = true`
- [ ] Update Javadoc
- [ ] Run existing unit tests to verify no regression

**Files to Modify:**
- `tools/src/main/java/it/eng/tools/auth/daps/DapsAuthenticationService.java` (~5 lines)

**Acceptance Criteria:**
- Bean is created when new property is set to DAPS
- Bean is created when property is not set (default)
- Bean is NOT created when new property is set to KEYCLOAK
- All existing tests still pass

---

### Task 2.6: Update DapsAuthenticationProperties
**Assigned to:** TBD  
**Estimated:** 3 hours  
**Status:** Not Started  
**Depends on:** Task 1.3

**Subtasks:**
- [ ] Update @ConditionalOnProperty annotation
- [ ] Update @ConfigurationProperties prefix (if needed)
- [ ] Add @DeprecatedConfigurationProperty (if applicable)
- [ ] Update Javadoc
- [ ] Write tests for property binding

**Files to Modify:**
- `tools/src/main/java/it/eng/tools/auth/daps/DapsAuthenticationProperties.java` (~10 lines)

**Acceptance Criteria:**
- Properties bind correctly
- All tests pass

---

### Task 2.7: Update DapsCertificateProvider
**Assigned to:** TBD  
**Estimated:** 2 hours  
**Status:** Not Started  
**Depends on:** Task 1.3

**Subtasks:**
- [ ] Update @ConditionalOnProperty annotation
- [ ] Update Javadoc
- [ ] Run existing tests

**Files to Modify:**
- `tools/src/main/java/it/eng/tools/auth/daps/DapsCertificateProvider.java` (~5 lines)

**Acceptance Criteria:**
- Bean is created when DAPS provider is active
- All existing tests pass

---

### Task 2.8: Create AuthenticationAutoConfiguration
**Assigned to:** TBD  
**Estimated:** 4 hours  
**Status:** Not Started  
**Depends on:** Task 1.3, Task 2.2, Task 2.5

**Subtasks:**
- [ ] Create AuthenticationAutoConfiguration.java in config package
- [ ] Add @Configuration annotation
- [ ] Add @EnableConfigurationProperties(AuthenticationProperties.class)
- [ ] Inject AuthenticationProperties
- [ ] Inject Optional<KeycloakAuthenticationService>
- [ ] Inject Optional<DapsAuthenticationService>
- [ ] Inject Optional<DcpAuthenticationService> (future)
- [ ] Create @Bean method for AuthProvider:
  - Use switch on provider type
  - Return appropriate service
  - Throw clear exception if service not available
- [ ] Add @PostConstruct method for startup logging
- [ ] Write unit tests (8+ tests)
  - Test Keycloak provider selection
  - Test DAPS provider selection
  - Test error when no provider available
  - Test error when invalid provider
  - Test startup logging

**Files to Create:**
- `tools/src/main/java/it/eng/tools/auth/config/AuthenticationAutoConfiguration.java` (~60 lines)
- `tools/src/test/java/it/eng/tools/auth/config/AuthenticationAutoConfigurationTest.java` (~150 lines)

**Acceptance Criteria:**
- Correct provider is selected based on property
- Clear error messages for misconfiguration
- Startup logging shows selected provider
- All unit tests pass
- Code coverage >85%

---

### Task 2.9: Create SecurityAutoConfiguration
**Assigned to:** TBD  
**Estimated:** 3 hours  
**Status:** Not Started  
**Depends on:** Task 2.1, Task 2.4

**Subtasks:**
- [ ] Create SecurityAutoConfiguration.java in config package
- [ ] Add @Configuration annotation
- [ ] Add fail-fast validation for SecurityConfigProvider bean
- [ ] Add startup logging for security configuration
- [ ] Write unit tests (5+ tests)

**Files to Create:**
- `tools/src/main/java/it/eng/tools/auth/config/SecurityAutoConfiguration.java` (~40 lines)
- `tools/src/test/java/it/eng/tools/auth/config/SecurityAutoConfigurationTest.java` (~80 lines)

**Acceptance Criteria:**
- Validation catches missing SecurityConfigProvider
- Logging shows security configuration status
- All unit tests pass

---

### Task 2.10: Register Auto-Configurations
**Assigned to:** TBD  
**Estimated:** 1 hour  
**Status:** Not Started  
**Depends on:** Task 2.8, Task 2.9

**Subtasks:**
- [ ] Create or update META-INF/spring.factories
- [ ] Register AuthenticationAutoConfiguration
- [ ] Register SecurityAutoConfiguration
- [ ] Test auto-configuration loading

**Files to Create/Modify:**
- `tools/src/main/resources/META-INF/spring.factories` (~5 lines)

**Acceptance Criteria:**
- Auto-configurations are loaded on startup
- Spring Boot test confirms loading

---

## Phase 3: Connector Module Refactoring (Week 4) - 5 days

### Task 3.1: Create CorsConfigProperties
**Assigned to:** TBD  
**Estimated:** 4 hours  
**Status:** Not Started  
**Depends on:** None

**Subtasks:**
- [ ] Create CorsConfigProperties.java in connector/configuration package
- [ ] Add @Component annotation
- [ ] Extract CORS configuration logic from KeycloakSecurityConfig
- [ ] Extract CORS configuration logic from WebSecurityConfig
- [ ] Add @Value bindings for:
  - application.cors.allowed.origins
  - application.cors.allowed.methods
  - application.cors.allowed.headers
  - application.cors.allowed.credentials
- [ ] Create method: corsConfigurationSource() returning CorsConfigurationSource
- [ ] Handle blank values (use defaults)
- [ ] Write unit tests (6+ tests)
  - Test with all properties set
  - Test with blank properties (defaults)
  - Test with partial properties
  - Test credentials handling
  - Test origin parsing
  - Test method parsing

**Files to Create:**
- `connector/src/main/java/it/eng/connector/configuration/CorsConfigProperties.java` (~80 lines)
- `connector/src/test/java/it/eng/connector/configuration/CorsConfigPropertiesTest.java` (~120 lines)

**Acceptance Criteria:**
- CORS configuration is identical to existing behavior
- All unit tests pass
- Code coverage >85%

---

### Task 3.2: Verify Filter Classes Accessibility
**Assigned to:** TBD  
**Estimated:** 2 hours  
**Status:** Not Started  
**Depends on:** None

**Subtasks:**
- [ ] Check KeycloakAuthenticationFilter constructor visibility
- [ ] Check KeycloakRealmRoleConverter constructor visibility
- [ ] Check JwtAuthenticationFilter constructor visibility
- [ ] Check JwtAuthenticationProvider constructor visibility
- [ ] Check DataspaceProtocolEndpointsAuthenticationFilter constructor visibility
- [ ] Make constructors public if needed
- [ ] Update Javadoc if visibility changes

**Files to Check:**
- `connector/src/main/java/it/eng/connector/configuration/KeycloakAuthenticationFilter.java`
- `connector/src/main/java/it/eng/connector/configuration/KeycloakRealmRoleConverter.java`
- `connector/src/main/java/it/eng/connector/configuration/JwtAuthenticationFilter.java`
- `connector/src/main/java/it/eng/connector/configuration/JwtAuthenticationProvider.java`
- `connector/src/main/java/it/eng/connector/configuration/DataspaceProtocolEndpointsAuthenticationFilter.java`

**Acceptance Criteria:**
- All filter classes can be instantiated by provider implementations
- No breaking changes to existing functionality

---

### Task 3.3: Create Unified SecurityConfig
**Assigned to:** TBD  
**Estimated:** 1 day  
**Status:** Not Started  
**Depends on:** Task 3.1, Task 3.2, Task 2.1, Task 2.4

**Subtasks:**
- [ ] Create SecurityConfig.java in connector/configuration package
- [ ] Add @Configuration annotation
- [ ] Add @EnableWebSecurity annotation
- [ ] Add @EnableMethodSecurity annotation
- [ ] Inject SecurityConfigProvider
- [ ] Inject CorsConfigProperties
- [ ] Create @Bean method: securityFilterChain(HttpSecurity)
- [ ] Define commonSecurityConfig() method:
  - Build SecurityCommonConfig
  - Define CORS configuration source
  - Define admin endpoints (List<RequestMatcher>)
  - Define connector endpoints (List<RequestMatcher>)
  - Define API endpoints (List<RequestMatcher>)
- [ ] Delegate to securityConfigProvider.configureSecurityChain(http, commonConfig)
- [ ] Write unit tests (8+ tests)
  - Test with Keycloak provider (mock)
  - Test with DAPS provider (mock)
  - Test CORS configuration is passed
  - Test authorization rules are passed
  - Test SecurityFilterChain is created
  - Test with missing SecurityConfigProvider (should fail)

**Files to Create:**
- `connector/src/main/java/it/eng/connector/configuration/SecurityConfig.java` (~100 lines)
- `connector/src/test/java/it/eng/connector/configuration/SecurityConfigTest.java` (~150 lines)

**Acceptance Criteria:**
- Security configuration is identical to existing behavior
- Works with both Keycloak and DAPS providers
- All unit tests pass
- Integration tests pass
- Code coverage >80%

---

### Task 3.4: Add Backward Compatibility to Legacy Configs
**Assigned to:** TBD  
**Estimated:** 2 hours  
**Status:** Not Started  
**Depends on:** Task 3.3

**Subtasks:**
- [ ] Update KeycloakSecurityConfig.java:
  - Add @ConditionalOnMissingBean(SecurityConfigProvider.class)
  - Add @Deprecated annotation
  - Add deprecation notice in Javadoc
  - Add @SuppressWarnings("deprecation")
- [ ] Update WebSecurityConfig.java:
  - Add @ConditionalOnMissingBean(SecurityConfigProvider.class)
  - Add @Deprecated annotation
  - Add deprecation notice in Javadoc
  - Add @SuppressWarnings("deprecation")
- [ ] Test backward compatibility:
  - New config takes precedence
  - Legacy configs still work if new config is absent

**Files to Modify:**
- `connector/src/main/java/it/eng/connector/configuration/KeycloakSecurityConfig.java` (~10 lines)
- `connector/src/main/java/it/eng/connector/configuration/WebSecurityConfig.java` (~10 lines)

**Acceptance Criteria:**
- New SecurityConfig is used when SecurityConfigProvider is present
- Legacy configs are used only if SecurityConfigProvider is absent
- Deprecation is clearly marked
- All tests pass

---

## Phase 4: Update Conditional Annotations (Week 4) - 3 days

### Task 4.1: Update JwtAuthenticationProvider
**Assigned to:** TBD  
**Estimated:** 2 hours  
**Status:** Not Started  
**Depends on:** Task 1.3

**Subtasks:**
- [ ] Update @ConditionalOnProperty to @ConditionalOnExpression
- [ ] Expression: Check both new and legacy properties
- [ ] Test bean creation with new property
- [ ] Test bean creation with legacy property
- [ ] Test bean is NOT created with Keycloak provider

**Files to Modify:**
- `connector/src/main/java/it/eng/connector/configuration/JwtAuthenticationProvider.java` (~8 lines)

**Acceptance Criteria:**
- Bean created when DAPS provider is active
- Bean NOT created when Keycloak provider is active
- Works with both new and legacy properties
- All tests pass

---

### Task 4.2: Update UserApiController
**Assigned to:** TBD  
**Estimated:** 2 hours  
**Status:** Not Started  
**Depends on:** Task 1.3

**Subtasks:**
- [ ] Update @ConditionalOnProperty annotation
- [ ] Add backward compatibility
- [ ] Test controller is available with DAPS provider
- [ ] Test controller is NOT available with Keycloak provider

**Files to Modify:**
- `connector/src/main/java/it/eng/connector/rest/api/UserApiController.java` (~5 lines)

**Acceptance Criteria:**
- Controller available when DAPS provider is active
- Controller NOT available when Keycloak provider is active
- All tests pass

---

### Task 4.3: Update UserService
**Assigned to:** TBD  
**Estimated:** 2 hours  
**Status:** Not Started  
**Depends on:** Task 1.3

**Subtasks:**
- [ ] Update @ConditionalOnProperty annotation
- [ ] Add backward compatibility
- [ ] Test service is available with DAPS provider
- [ ] Test service is NOT available with Keycloak provider

**Files to Modify:**
- `connector/src/main/java/it/eng/connector/service/UserService.java` (~5 lines)

**Acceptance Criteria:**
- Service available when DAPS provider is active
- Service NOT available when Keycloak provider is active
- All tests pass

---

## Phase 5: Property Migration Support (Week 5) - 5 days

### Task 5.1: Create LegacyPropertyMigrationListener
**Assigned to:** TBD  
**Estimated:** 4 hours  
**Status:** Not Started  
**Depends on:** Task 1.3

**Subtasks:**
- [ ] Create LegacyPropertyMigrationListener.java in connector/util package
- [ ] Implement ApplicationListener<ApplicationReadyEvent>
- [ ] Check if legacy property is present
- [ ] Log deprecation warning with clear instructions
- [ ] Suggest migration to new property
- [ ] Log migration timeline (removal in version 2.0)
- [ ] Write unit tests (5+ tests)

**Files to Create:**
- `connector/src/main/java/it/eng/connector/util/LegacyPropertyMigrationListener.java` (~100 lines)
- `connector/src/test/java/it/eng/connector/util/LegacyPropertyMigrationListenerTest.java` (~80 lines)

**Acceptance Criteria:**
- Warning is logged when legacy property is used
- No warning when only new property is used
- Clear migration instructions in log
- All tests pass

---

### Task 5.2: Update README.md
**Assigned to:** TBD  
**Estimated:** 4 hours  
**Status:** Not Started  
**Depends on:** All previous tasks

**Subtasks:**
- [ ] Add "Authentication Providers" section
- [ ] Document all providers (Keycloak, DAPS)
- [ ] Show configuration examples
- [ ] Add table of properties
- [ ] Link to detailed security.md
- [ ] Add migration notice

**Files to Modify:**
- `README.md` (~50 lines added)

**Acceptance Criteria:**
- Section is clear and concise
- Examples are accurate
- Links work
- Reviewed by team

---

### Task 5.3: Update doc/security.md
**Assigned to:** TBD  
**Estimated:** 6 hours  
**Status:** Not Started  
**Depends on:** All previous tasks

**Subtasks:**
- [ ] Replace old property documentation
- [ ] Add new property documentation
- [ ] Explain provider selection mechanism
- [ ] Document each provider's specific properties
- [ ] Add configuration examples
- [ ] Add troubleshooting section
- [ ] Add FAQ section

**Files to Modify:**
- `doc/security.md` (~100 lines changed)

**Acceptance Criteria:**
- Documentation is comprehensive
- Examples are tested
- Troubleshooting covers common issues
- Reviewed by team

---

### Task 5.4: Create doc/AUTHENTICATION_MIGRATION.md
**Assigned to:** TBD  
**Estimated:** 6 hours  
**Status:** Not Started  
**Depends on:** All previous tasks

**Subtasks:**
- [ ] Write step-by-step migration guide
- [ ] Add before/after property examples
- [ ] Document backward compatibility period
- [ ] Add deprecation timeline
- [ ] Create FAQ section
- [ ] Add troubleshooting section
- [ ] Document docker-compose changes
- [ ] Document kubernetes/terraform changes
- [ ] Add migration checklist

**Files to Create:**
- `doc/AUTHENTICATION_MIGRATION.md` (~500 lines)

**Acceptance Criteria:**
- Guide is clear and actionable
- All scenarios are covered
- Examples are tested
- Reviewed by team and stakeholders

---

### Task 5.5: Update Application Property Files
**Assigned to:** TBD  
**Estimated:** 4 hours  
**Status:** Not Started  
**Depends on:** Task 5.4

**Subtasks:**
- [ ] Update connector/src/main/resources/application-consumer.properties
- [ ] Update connector/src/main/resources/application-provider.properties
- [ ] Update ci/docker/connector_a_resources/application.properties
- [ ] Update ci/docker/connector_b_resources/application.properties
- [ ] Update ci/docker/keycloak_resources/application-keycloak-a.properties
- [ ] Update ci/docker/keycloak_resources/application-keycloak-b.properties
- [ ] Update terraform/app-resources/connector_a_resources/application.properties
- [ ] Update terraform/app-resources/connector_b_resources/application.properties
- [ ] Comment old properties with deprecation notice
- [ ] Add new properties
- [ ] Test with Docker Compose
- [ ] Test with Terraform (if applicable)

**Files to Modify:** 8+ property files

**Acceptance Criteria:**
- All property files use new properties
- Old properties are commented with deprecation notice
- Docker Compose still works
- Terraform still works (if applicable)

---

## Phase 6: Testing & Validation (Week 5-6) - 10 days

### Task 6.1: Provider Switching Tests
**Assigned to:** TBD  
**Estimated:** 1 day  
**Status:** Not Started  
**Depends on:** All Phase 3 tasks

**Subtasks:**
- [ ] Create integration test class: ProviderSwitchingTest
- [ ] Test application starts with KEYCLOAK provider
- [ ] Test application starts with DAPS provider
- [ ] Test application starts with legacy property (keycloak=true)
- [ ] Test application starts with legacy property (keycloak=false)
- [ ] Test application fails with invalid provider
- [ ] Test application fails with conflicting properties
- [ ] Verify correct beans are created for each provider
- [ ] Verify correct filter chain is applied for each provider

**Files to Create:**
- `connector/src/test/java/it/eng/connector/integration/ProviderSwitchingTest.java` (~200 lines)

**Acceptance Criteria:**
- All tests pass
- Each provider can be activated correctly
- Invalid configurations fail-fast with clear errors

---

### Task 6.2: Security Filter Chain Tests
**Assigned to:** TBD  
**Estimated:** 1 day  
**Status:** Not Started  
**Depends on:** Task 3.3

**Subtasks:**
- [ ] Create integration test class: SecurityFilterChainTest
- [ ] Test Keycloak filter chain is applied correctly
- [ ] Test DAPS filter chain is applied correctly
- [ ] Test CORS configuration is applied
- [ ] Test security headers are applied
- [ ] Test authorization rules are applied
- [ ] Mock HttpSecurity and verify configuration

**Files to Create:**
- `connector/src/test/java/it/eng/connector/integration/SecurityFilterChainTest.java` (~250 lines)

**Acceptance Criteria:**
- All tests pass
- Filter chains match expected configuration
- CORS, headers, and authorization rules are correct

---

### Task 6.3: Authentication Tests
**Assigned to:** TBD  
**Estimated:** 1.5 days  
**Status:** Not Started  
**Depends on:** All Phase 2 and Phase 3 tasks

**Subtasks:**
- [ ] Create integration test class: AuthenticationIntegrationTest
- [ ] Test Keycloak JWT authentication
- [ ] Test Keycloak role extraction (ADMIN, CONNECTOR)
- [ ] Test DAPS JWT authentication
- [ ] Test DAPS Basic authentication
- [ ] Test unauthorized access is blocked
- [ ] Test role-based access control (admin, connector, api endpoints)
- [ ] Mock authentication servers (Keycloak, DAPS)
- [ ] Test token validation

**Files to Create:**
- `connector/src/test/java/it/eng/connector/integration/AuthenticationIntegrationTest.java` (~300 lines)

**Acceptance Criteria:**
- All tests pass
- Authentication works correctly for both providers
- Authorization rules are enforced

---

### Task 6.4: Property Compatibility Tests
**Assigned to:** TBD  
**Estimated:** 1 day  
**Status:** Not Started  
**Depends on:** Task 5.1

**Subtasks:**
- [ ] Create test class: PropertyCompatibilityTest
- [ ] Test new property only (KEYCLOAK)
- [ ] Test new property only (DAPS)
- [ ] Test old property only (true)
- [ ] Test old property only (false)
- [ ] Test both properties (consistent values)
- [ ] Test both properties (conflicting values - should fail)
- [ ] Test no properties (should use default DAPS)
- [ ] Verify deprecation warnings

**Files to Create:**
- `connector/src/test/java/it/eng/connector/integration/PropertyCompatibilityTest.java` (~200 lines)

**Acceptance Criteria:**
- All valid property combinations work
- Invalid combinations fail with clear errors
- Deprecation warnings are logged correctly

---

### Task 6.5: End-to-End Connector Operation Tests
**Assigned to:** TBD  
**Estimated:** 2 days  
**Status:** Not Started  
**Depends on:** All previous tasks

**Subtasks:**
- [ ] Start connector with DAPS provider
- [ ] Test catalog operations
- [ ] Test negotiation operations
- [ ] Test transfer operations
- [ ] Restart connector with Keycloak provider
- [ ] Test same operations
- [ ] Verify functionality is identical
- [ ] Test with real Keycloak server
- [ ] Test with mock DAPS server

**Files to Create:**
- `connector/src/test/java/it/eng/connector/e2e/EndToEndOperationTest.java` (~400 lines)

**Acceptance Criteria:**
- All tests pass
- Both providers support full connector functionality
- No regressions from existing behavior

---

### Task 6.6: Multi-Connector Tests
**Assigned to:** TBD  
**Estimated:** 1 day  
**Status:** Not Started  
**Depends on:** Task 6.5

**Subtasks:**
- [ ] Setup: Connector A (Keycloak) + Connector B (Keycloak)
- [ ] Test catalog request between connectors
- [ ] Test negotiation between connectors
- [ ] Setup: Connector A (DAPS) + Connector B (DAPS)
- [ ] Test same operations
- [ ] Setup: Connector A (Keycloak) + Connector B (DAPS)
- [ ] Test interoperability
- [ ] Document any limitations

**Files to Create:**
- `connector/src/test/java/it/eng/connector/e2e/MultiConnectorTest.java` (~300 lines)

**Acceptance Criteria:**
- Connectors can communicate regardless of auth provider
- All tests pass
- Interoperability is maintained

---

### Task 6.7: Docker Compose Tests
**Assigned to:** TBD  
**Estimated:** 1 day  
**Status:** Not Started  
**Depends on:** Task 5.5

**Subtasks:**
- [ ] Update docker-compose-multi-connector.yml (if needed)
- [ ] Test with Keycloak enabled
- [ ] Test without Keycloak (DAPS mode)
- [ ] Test connector-to-connector communication
- [ ] Verify startup logs
- [ ] Verify functionality

**Files to Modify:**
- `docker-compose-multi-connector.yml` (if needed)

**Acceptance Criteria:**
- Docker Compose setup works with new properties
- All services start successfully
- Connector communication works

---

### Task 6.8: TCK Tests
**Assigned to:** TBD  
**Estimated:** 1 day  
**Status:** Not Started  
**Depends on:** All previous tasks

**Subtasks:**
- [ ] Run full TCK suite with DAPS provider
- [ ] Document any failures
- [ ] Fix any issues
- [ ] Run full TCK suite with Keycloak provider
- [ ] Document any failures
- [ ] Fix any issues
- [ ] Verify 100% pass rate for both providers

**Acceptance Criteria:**
- TCK tests pass 100% with DAPS provider
- TCK tests pass 100% with Keycloak provider
- No regressions

---

### Task 6.9: Security Validation
**Assigned to:** TBD  
**Estimated:** 1 day  
**Status:** Not Started  
**Depends on:** All previous tasks

**Subtasks:**
- [ ] Run OWASP dependency check
- [ ] Validate CORS headers
- [ ] Validate security headers (X-Frame-Options, X-XSS-Protection, etc.)
- [ ] Test role-based access control
- [ ] Test token validation
- [ ] Test unauthorized access blocking
- [ ] Perform basic penetration testing
- [ ] Document findings

**Acceptance Criteria:**
- No high/critical vulnerabilities
- Security headers are correct
- Authorization is enforced
- Tokens are validated correctly

---

### Task 6.10: Performance Testing
**Assigned to:** TBD  
**Estimated:** 1 day  
**Status:** Not Started  
**Depends on:** All previous tasks

**Subtasks:**
- [ ] Measure authentication overhead (Keycloak vs DAPS)
- [ ] Measure startup time (new vs old architecture)
- [ ] Measure memory usage (new vs old)
- [ ] Measure request throughput
- [ ] Compare with baseline (before migration)
- [ ] Document results
- [ ] Investigate any regressions

**Acceptance Criteria:**
- Startup time: no increase >5%
- Memory usage: no increase >3%
- Authentication latency: no increase >2%
- Throughput: maintained or improved

---

## Phase 7: Deployment & Communication (Week 6) - 5 days

### Task 7.1: Write Release Notes
**Assigned to:** TBD  
**Estimated:** 4 hours  
**Status:** Not Started  
**Depends on:** All previous tasks

**Subtasks:**
- [ ] Create RELEASE_NOTES.md for version 1.0
- [ ] Highlight authentication provider changes
- [ ] Include migration guide link
- [ ] Show before/after examples
- [ ] Mention backward compatibility period
- [ ] Announce deprecation timeline
- [ ] List all breaking changes (none expected)
- [ ] List all new features

**Files to Create:**
- `RELEASE_NOTES.md` (~200 lines)

**Acceptance Criteria:**
- Release notes are clear and comprehensive
- All major changes are documented
- Reviewed by team and stakeholders

---

### Task 7.2: Update CHANGELOG.md
**Assigned to:** TBD  
**Estimated:** 2 hours  
**Status:** Not Started  
**Depends on:** Task 7.1

**Subtasks:**
- [ ] Add entry for version 1.0
- [ ] List all changes
- [ ] Follow Keep a Changelog format
- [ ] Link to relevant issues/PRs

**Files to Modify:**
- `CHANGELOG.md` (~50 lines added)

**Acceptance Criteria:**
- Changelog follows standard format
- All changes are listed
- Links are correct

---

### Task 7.3: Create Migration Communication
**Assigned to:** TBD  
**Estimated:** 4 hours  
**Status:** Not Started  
**Depends on:** Task 7.1

**Subtasks:**
- [ ] Draft email to users/customers
- [ ] Include key changes
- [ ] Include migration guide link
- [ ] Include support contact information
- [ ] Include deprecation timeline
- [ ] Review with stakeholders

**Files to Create:**
- `doc/MIGRATION_ANNOUNCEMENT.md` (~100 lines)

**Acceptance Criteria:**
- Communication is clear and professional
- All necessary information is included
- Reviewed by stakeholders

---

### Task 7.4: Update Project Wiki/Documentation Site
**Assigned to:** TBD  
**Estimated:** 4 hours  
**Status:** Not Started  
**Depends on:** Task 5.2, Task 5.3, Task 5.4

**Subtasks:**
- [ ] Update wiki pages (if applicable)
- [ ] Update documentation site (if applicable)
- [ ] Add new authentication section
- [ ] Update existing examples
- [ ] Update FAQ

**Acceptance Criteria:**
- All documentation is up-to-date
- Examples are tested
- FAQ is comprehensive

---

### Task 7.5: Final Code Review
**Assigned to:** TBD (senior developer or architect)  
**Estimated:** 1 day  
**Status:** Not Started  
**Depends on:** All previous tasks

**Subtasks:**
- [ ] Review all new code
- [ ] Review all modified code
- [ ] Check code style and conventions
- [ ] Check for security issues
- [ ] Check for performance issues
- [ ] Verify test coverage
- [ ] Verify documentation completeness
- [ ] Approve for merge/release

**Acceptance Criteria:**
- Code review is complete
- All issues are addressed
- Code is approved for merge

---

### Task 7.6: Merge and Release
**Assigned to:** TBD (release manager)  
**Estimated:** 4 hours  
**Status:** Not Started  
**Depends on:** Task 7.5

**Subtasks:**
- [ ] Merge to main/master branch
- [ ] Tag release (e.g., v1.0.0)
- [ ] Build release artifacts
- [ ] Publish to artifact repository
- [ ] Update documentation site
- [ ] Announce release
- [ ] Monitor for issues

**Acceptance Criteria:**
- Release is published
- Artifacts are available
- Documentation is updated
- Announcement is sent

---

## Summary Statistics

**Total Tasks:** 46  
**Total Estimated Time:** ~30 days (6 weeks with 2-3 developers)

**Breakdown by Phase:**
- Phase 1 (Foundation): 19 hours (~2.5 days)
- Phase 2 (Tools Refactoring): 6 days
- Phase 3 (Connector Refactoring): 2.5 days
- Phase 4 (Update Conditionals): 0.75 days
- Phase 5 (Migration Support): 3 days
- Phase 6 (Testing): 10 days
- Phase 7 (Deployment): 2.5 days

**Risk Level by Phase:**
- Phase 1: Low (new code only)
- Phase 2: Medium (refactoring existing logic)
- Phase 3: Medium-High (security-critical)
- Phase 4: Low (annotation updates)
- Phase 5: Low (documentation)
- Phase 6: Low (validation)
- Phase 7: Low (release)

---

## Resources Needed

**Team:**
- 2-3 Java developers (full-time for 6 weeks)
- 1 QA engineer (part-time for weeks 5-6)
- 1 technical writer (part-time for week 5)
- 1 security reviewer (part-time for week 6)
- 1 release manager (part-time for week 6)

**Infrastructure:**
- Development environment
- Test Keycloak server
- Mock DAPS server
- Docker/Docker Compose
- CI/CD pipeline

**Tools:**
- IDE (IntelliJ IDEA recommended)
- Git
- Maven
- JUnit 5
- Mockito
- Test containers

---

## Success Criteria

- [ ] All 46 tasks completed
- [ ] All unit tests pass (>300 tests)
- [ ] All integration tests pass (>50 tests)
- [ ] All TCK tests pass (100%)
- [ ] Code coverage >80%
- [ ] No critical/high security vulnerabilities
- [ ] Performance within acceptable limits (<5% regression)
- [ ] Documentation complete and reviewed
- [ ] Backward compatibility maintained
- [ ] Release published and announced

---

*This task breakdown should be imported into your project management tool (Jira, GitHub Projects, etc.)*  
*Assign tasks to team members and track progress.*





