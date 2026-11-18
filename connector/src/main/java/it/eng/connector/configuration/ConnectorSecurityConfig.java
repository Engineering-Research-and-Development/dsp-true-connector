package it.eng.connector.configuration;

import it.eng.connector.filter.ApiTenantContextFilter;
import it.eng.connector.filter.InternalCallAuthenticationFilter;
import it.eng.connector.filter.InternalTenantContextFilter;
import it.eng.connector.model.Role;
import it.eng.connector.repository.UserRepository;
import it.eng.tools.auth.AuthenticationMode;
import it.eng.tools.auth.AuthenticationModeResolver;
import it.eng.tools.auth.condition.InternalAuthenticationModeCondition;
import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import it.eng.tools.auth.internal.InternalCallClaims;
import it.eng.tools.auth.internal.InternalCallTokenIssuer;
import it.eng.tools.auth.jwt.JwtService;
import it.eng.tools.auth.keycloak.KeycloakTrustProperties;
import it.eng.tools.auth.keycloak.issuer.IssuerTokenValidators;
import it.eng.tools.auth.keycloak.issuer.IssuerType;
import it.eng.tools.auth.keycloak.issuer.TrustedIssuerRegistry;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.service.AuditEventPublisher;
import jakarta.servlet.ServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Unified security configuration for the connector.
 *
 * <p>Defines four {@link SecurityFilterChain} beans matched by URL zone:
 * <ol>
 *   <li><b>Internal chain</b> ({@code /internal/**}) — the connector's own loopback calls. Requires an
 *       internal-call token signed with this process's in-memory key ({@code ROLE_INTERNAL}) and a loopback
 *       TCP peer, in every authentication mode except {@code DISABLED}. See D-ARC-005.</li>
 *   <li><b>Admin chain</b> ({@code /api/**, /actuator/**, /env}) — requires {@code ROLE_ADMIN}.
 *       Keycloak or self-issued JWT depending on {@code application.auth.provider}. In {@code KEYCLOAK}
 *       mode it authenticates tenant, {@code connector-admin} and {@code platform} tokens; a platform token
 *       holds {@code ROLE_CONNECTOR} only, so it gets 403 on every route.</li>
 *   <li><b>Protocol chain</b> ({@code /connector/**, /catalog/**, /negotiations/**, /transfers/**})
 *       — requires {@code ROLE_CONNECTOR}. Uses DCP when {@code application.auth.dcp.enabled=true},
 *       otherwise follows the configured provider. In {@code KEYCLOAK} mode it trusts the
 *       {@code platform} issuer only.</li>
 *   <li><b>Default chain</b> — permits all other requests.</li>
 * </ol>
 *
 * <p>Supported authentication matrix:
 * <pre>
 * provider=KEYCLOAK + dcp.enabled=false → admin: Keycloak, protocol: Keycloak
 * provider=KEYCLOAK + dcp.enabled=true → admin: Keycloak, protocol: DCP
 * provider=INTERNAL + dcp.enabled=false → admin: self-issued JWT, protocol: self-issued JWT
 * provider=INTERNAL + dcp.enabled=true → admin: self-issued JWT, protocol: DCP
 * provider=DISABLED → all endpoints: permitAll
 * any provider, any dcp.enabled → internal: in-memory internal-call token (permitAll when DISABLED)
 * </pre>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ConnectorSecurityConfig {

    @Value("${application.cors.allowed.origins:}")
    private String allowedOrigins;

    @Value("${application.cors.allowed.methods:}")
    private String allowedMethods;

    @Value("${application.cors.allowed.headers:}")
    private String allowedHeaders;

    @Value("${application.cors.allowed.credentials:}")
    private String allowedCredentials;

    @Autowired
    @Qualifier("delegatedAuthenticationEntryPoint")
    private AuthenticationEntryPoint authEntryPoint;

    private static final String KEYCLOAK_ADMIN_FILTER = "keycloakAuthenticationFilter";
    private static final String KEYCLOAK_PROTOCOL_FILTER = "keycloakProtocolAuthenticationFilter";

    private static final Set<String> LOOPBACK_ADDRESSES = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    private final AuthenticationMode authMode;
    private final boolean dcpEnabled;

    /**
     * Constructs the config and resolves the active authentication mode and DCP flag.
     *
     * @param environment the Spring environment
     */
    public ConnectorSecurityConfig(Environment environment) {
        this.authMode = AuthenticationModeResolver.resolve(environment);
        this.dcpEnabled = AuthenticationModeResolver.isDcpEnabled(environment);
    }

    /**
     * Internal security filter chain covering {@code /internal/**}, the zone of the connector's loopback
     * calls.
     *
     * <p>Only an internal-call token verified with the in-memory key of {@link InternalCallTokenIssuer}
     * authenticates; any other token, or none, gets 401. An authenticated request also needs
     * {@code ROLE_INTERNAL} and a loopback connection: both the TCP peer ({@code ServletRequest#getRemoteAddr()})
     * and the local socket address ({@code ServletRequest#getLocalAddr()}) must be {@code 127.0.0.1} or
     * {@code ::1}, so the check holds even when a forward-headers strategy (auto-enabled on Kubernetes)
     * rewrites the remote address from {@code X-Forwarded-For}; otherwise it gets 403.
     * {@link InternalTenantContextFilter} sets the tenant from the token's {@code tenant_id} claim.
     * The chain is the same in {@code INTERNAL} and {@code KEYCLOAK} mode and ignores DCP. {@code DISABLED}
     * mode permits all requests, as on the other chains.
     *
     * @param http the HttpSecurity builder
     * @param internalCallTokenIssuer the issuer whose in-memory key verifies internal-call tokens
     * @return the configured filter chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @Order(0)
    SecurityFilterChain internalFilterChain(HttpSecurity http, InternalCallTokenIssuer internalCallTokenIssuer)
            throws Exception {
        applyCommonConfiguration(http);
        http.securityMatcher(ApiEndpoints.INTERNAL_ROOT + "/**")
                .anonymous(AbstractHttpConfigurer::disable)
                .addFilterBefore(new InternalCallAuthenticationFilter(internalCallTokenIssuer),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new InternalTenantContextFilter(), UsernamePasswordAuthenticationFilter.class);

        if (authMode == AuthenticationMode.DISABLED) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().access(ConnectorSecurityConfig::authorizeInternalCall))
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint));
        }
        return http.build();
    }

    /**
     * Admin security filter chain covering {@code /api/**}, {@code /actuator/**} and {@code /env}.
     * Requires {@code ROLE_SUPER_ADMIN} for {@code /api/v1/tenants/**},
     * {@code /api/v1/properties/**}, and most {@code /api/v1/users/**} endpoints.
     * In INTERNAL mode {@code GET /api/v1/users/me}, {@code PUT /api/v1/users/*\/update} and
     * {@code PUT /api/v1/users/*\/password} are accessible to {@code ROLE_ADMIN} and
     * {@code ROLE_SUPER_ADMIN} (self-service only — the service layer enforces ownership).
     * In KEYCLOAK mode all of {@code /api/v1/users/**} is {@code ROLE_SUPER_ADMIN} only.
     * {@code /api/v1/auth/**} is permit-all in INTERNAL mode only; in KEYCLOAK mode people sign in
     * to Keycloak directly and neither the auth routes nor the password route exist.
     * All other {@code /api/**} endpoints require at minimum {@code ROLE_ADMIN}.
     * In KEYCLOAK mode, tenant, {@code connector-admin} and {@code platform} tokens authenticate; a platform
     * token holds {@code ROLE_CONNECTOR} only and so gets 403 on every route of this chain.
     * Disabled mode permits all requests.
     *
     * @param http the HttpSecurity builder
     * @param keycloakFilter optional Keycloak filter for tenant, connector-admin and platform tokens, present when
     *        mode is KEYCLOAK
     * @param apiTenantContextFilter the tenant context filter for API requests
     * @param jwtAuthFilter the JWT authentication filter for human logins (INTERNAL mode)
     * @return the configured filter chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @Order(1)
    SecurityFilterChain adminFilterChain(HttpSecurity http,
                                         @Qualifier(KEYCLOAK_ADMIN_FILTER)
                                         ObjectProvider<KeycloakAuthenticationFilter> keycloakFilter,
                                         ObjectProvider<ApiTenantContextFilter> apiTenantContextFilter,
                                         ObjectProvider<InternalJwtAuthenticationFilter> jwtAuthFilter) throws Exception {
        applyCommonConfiguration(http);
        http.securityMatcher("/api/**", "/actuator/**", "/env");

        if (authMode == AuthenticationMode.DISABLED) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else if (authMode == AuthenticationMode.KEYCLOAK) {
            http.anonymous(AbstractHttpConfigurer::disable)
                    .addFilterBefore(keycloakFilter.getObject(), UsernamePasswordAuthenticationFilter.class)
                    .addFilterAfter(apiTenantContextFilter.getObject(), UsernamePasswordAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth
                            // Health endpoint is unauthenticated for CI/CD and orchestration health checks
                            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                            .requestMatchers(ApiEndpoints.TENANTS_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
                            .requestMatchers(ApiEndpoints.USERS_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
                            .requestMatchers(ApiEndpoints.PROPERTIES_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
                            .requestMatchers(ApiEndpoints.DASHBOARD_V1 + "/**")
                            .hasAnyRole(Role.ADMIN.name(),Role.SUPER_ADMIN.name())
                            .anyRequest().hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name()))
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint));
        } else {
            // INTERNAL: ApiTenantContextFilter must run AFTER UsernamePasswordAuthenticationFilter so that
            // the Authentication is already populated in SecurityContextHolder when we read it.
            http.anonymous(AbstractHttpConfigurer::disable)
                    .addFilterBefore(jwtAuthFilter.getObject(), UsernamePasswordAuthenticationFilter.class)
                    .addFilterAfter(apiTenantContextFilter.getObject(), UsernamePasswordAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth
                            // Health endpoint is unauthenticated for CI/CD and orchestration health checks
                            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                            .requestMatchers(ApiEndpoints.TENANTS_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
                            .requestMatchers(HttpMethod.GET, ApiEndpoints.USERS_V1 + "/me")
                            .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
                            .requestMatchers(HttpMethod.PUT, ApiEndpoints.USERS_V1 + "/*/update")
                            .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
                            .requestMatchers(HttpMethod.PUT, ApiEndpoints.USERS_V1 + "/*/password")
                            .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
                            .requestMatchers(ApiEndpoints.USERS_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
                            .requestMatchers(ApiEndpoints.PROPERTIES_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
                            .requestMatchers(ApiEndpoints.DASHBOARD_V1 + "/**")
                            .hasAnyRole(Role.ADMIN.name(),Role.SUPER_ADMIN.name())
                            .requestMatchers(ApiEndpoints.AUTH_V1 + "/**").permitAll()
                            .anyRequest().hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name()))
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint));
        }
        return http.build();
    }

    /**
     * Protocol security filter chain covering DSP endpoints.
     * Requires {@code ROLE_CONNECTOR}. DCP overrides the provider when enabled.
     * In KEYCLOAK mode only {@code platform} tokens authenticate (see
     * {@link #keycloakProtocolAuthenticationFilter}); any tenant or {@code connector-admin} token gets 401.
     * Disabled mode permits all requests.
     *
     * @param http the HttpSecurity builder
     * @param keycloakFilter optional platform-only Keycloak filter, present when mode is KEYCLOAK
     * @param dcpFilter optional DCP filter, present when dcp.enabled is true
     * @param jwtAuthFilter optional JWT filter, present when mode is INTERNAL
     * @return the configured filter chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @Order(2)
    SecurityFilterChain protocolFilterChain(HttpSecurity http,
                                            @Qualifier(KEYCLOAK_PROTOCOL_FILTER)
                                            ObjectProvider<KeycloakAuthenticationFilter> keycloakFilter,
                                            ObjectProvider<DcpAuthenticationFilter> dcpFilter,
                                            ObjectProvider<InternalJwtAuthenticationFilter> jwtAuthFilter) throws Exception {
        applyCommonConfiguration(http);
        // Tenant-prefixed paths (/{tenantId}/catalog/request etc.) — added in Phase 1.
        // Legacy non-prefixed catalog paths have been removed; the catalog controller
        // now requires the /{tenantId} prefix (Phase 2).
        // Negotiation and consumer paths now also require the /{tenantId} prefix (Phase 3).
        // Non-prefixed protocol paths are also included here so that the TckProtocolForwardingFilter
        // (active when application.tck.enabled=true) can forward them to the tenant-prefixed
        // controller before the security chain completes authentication for the forwarded path.
        http.securityMatcher(
                "/*/connector/**", "/*/catalog/**", "/*/negotiations/**", "/*/transfers/**",
                "/*/consumer/**",
                "/connector/**", "/catalog/**", "/negotiations/**", "/transfers/**", "/consumer/**");

        if (authMode == AuthenticationMode.DISABLED) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else if (dcpEnabled) {
            DcpAuthenticationFilter dcp = dcpFilter.getIfAvailable();
            if (dcp != null) {
                http.addFilterBefore(dcp, UsernamePasswordAuthenticationFilter.class);
            }
            http.anonymous(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(Role.CONNECTOR.name()))
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint));
        } else if (authMode == AuthenticationMode.KEYCLOAK) {
            http.anonymous(AbstractHttpConfigurer::disable)
                    .addFilterBefore(keycloakFilter.getObject(), UsernamePasswordAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(Role.CONNECTOR.name()))
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint));
        } else {
            // INTERNAL
            http.anonymous(AbstractHttpConfigurer::disable)
                    .addFilterBefore(jwtAuthFilter.getObject(), UsernamePasswordAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(Role.CONNECTOR.name()))
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint));
        }
        return http.build();
    }

    /**
     * Default security filter chain that permits all requests not matched by admin or protocol chains.
     *
     * @param http the HttpSecurity builder
     * @return the configured filter chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @Order(3)
    SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        applyCommonConfiguration(http);
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    // =========================================================================
    // Conditional Keycloak beans
    // =========================================================================

    /**
     * Creates the per-issuer token validators of Keycloak mode.
     * Active only in Keycloak mode.
     *
     * <p>Every token's {@code aud} must contain the configured audience. Tenant and connector-admin tokens must
     * name {@code application.keycloak.ui-client-id} in {@code azp}, a platform token
     * {@code application.keycloak.platform-client-id}. A token carrying a privileged realm role other than its
     * realm's own is rejected, and {@code tenant_id} must match the bound tenant for tenant tokens and be absent
     * otherwise. See {@link IssuerTokenValidators}.
     *
     * @param trustProperties the Keycloak trust configuration, which holds the audience and the client ids
     * @return the validators
     */
    @Bean
    @Conditional(KeycloakAuthenticationModeCondition.class)
    IssuerTokenValidators keycloakIssuerTokenValidators(KeycloakTrustProperties trustProperties) {
        return new IssuerTokenValidators(trustProperties.getAudience(), trustProperties.getPlatformClientId(),
                trustProperties.getUiClientId());
    }

    /**
     * Creates the classifier that tells the issuer type and realm of a verified Keycloak token.
     * Active only in Keycloak mode.
     *
     * @param trustProperties the Keycloak trust configuration, which names the platform and admin realms
     * @return a new {@link KeycloakIssuerClassifier}
     */
    @Bean
    @Conditional(KeycloakAuthenticationModeCondition.class)
    KeycloakIssuerClassifier keycloakIssuerClassifier(KeycloakTrustProperties trustProperties) {
        return new KeycloakIssuerClassifier(trustProperties);
    }

    /**
     * Creates the Keycloak realm role converter, a per-realm allow-list.
     * Active only in Keycloak mode.
     *
     * <p>A tenant token yields {@code ROLE_ADMIN} only, a {@code connector-admin} token {@code ROLE_SUPER_ADMIN}
     * only and a {@code platform} token {@code ROLE_CONNECTOR} only, each when the token holds that realm role.
     * Every other role is dropped.
     *
     * @param issuerClassifier tells the issuer type of a verified token
     * @return a new {@link KeycloakRealmRoleConverter}
     */
    @Bean
    @Conditional(KeycloakAuthenticationModeCondition.class)
    KeycloakRealmRoleConverter keycloakRealmRoleConverter(KeycloakIssuerClassifier issuerClassifier) {
        return new KeycloakRealmRoleConverter(issuerClassifier);
    }

    /**
     * Creates the auditor of tokens rejected for a privileged realm role their issuer may not grant.
     * Active only in Keycloak mode.
     *
     * @param issuerClassifier tells the issuer type and realm of a verified issuer
     * @param auditEventPublisher publishes the audit event
     * @return a new {@link RealmRoleViolationAuditor}
     */
    @Bean
    @Conditional(KeycloakAuthenticationModeCondition.class)
    RealmRoleViolationAuditor realmRoleViolationAuditor(KeycloakIssuerClassifier issuerClassifier,
                                                        AuditEventPublisher auditEventPublisher) {
        return new RealmRoleViolationAuditor(issuerClassifier, auditEventPublisher);
    }

    /**
     * Creates the Keycloak authentication filter of the admin chain.
     * Active only in Keycloak mode.
     *
     * <p>Its decoder selects the issuer's cached decoder from the {@link TrustedIssuerRegistry} and accepts
     * tenant issuers (the realm of an enabled tenant), the {@code connector-admin} issuer and the
     * {@code platform} issuer. A platform token is accepted only so that it gets 403 instead of 401 on
     * {@code /api/**}: it holds {@code ROLE_CONNECTOR} only. Signing keys are loaded on first use, so startup
     * does not need Keycloak to be reachable.
     *
     * @param registry the trusted-issuer registry
     * @param validators the per-issuer token validators
     * @param keycloakRealmRoleConverter the role converter
     * @param realmRoleViolationAuditor audits tokens rejected for a realm role violation
     * @return a configured {@link KeycloakAuthenticationFilter}
     */
    @Bean(KEYCLOAK_ADMIN_FILTER)
    @Conditional(KeycloakAuthenticationModeCondition.class)
    KeycloakAuthenticationFilter keycloakAuthenticationFilter(TrustedIssuerRegistry registry,
                                                              IssuerTokenValidators validators,
                                                              KeycloakRealmRoleConverter keycloakRealmRoleConverter,
                                                              RealmRoleViolationAuditor realmRoleViolationAuditor) {
        JwtDecoder decoder = registry.issuerSelectingDecoder(
                Set.of(IssuerType.TENANT, IssuerType.ADMIN, IssuerType.PLATFORM), validators::forIssuer);
        return new KeycloakAuthenticationFilter(decoder, keycloakRealmRoleConverter, realmRoleViolationAuditor);
    }

    /**
     * Creates the Keycloak authentication filter of the protocol chain.
     * Active only in Keycloak mode.
     *
     * <p>Its decoder trusts the {@code platform} issuer only: the token's {@code aud} must contain the
     * configured audience, its {@code azp} must equal {@code application.keycloak.platform-client-id} and its
     * {@code realm_access.roles} must hold {@code CONNECTOR}. Any other token, tenant and
     * {@code connector-admin} tokens included, leaves the request unauthenticated (401).
     *
     * @param registry the trusted-issuer registry
     * @param validators the per-issuer token validators
     * @param keycloakRealmRoleConverter the role converter
     * @param realmRoleViolationAuditor audits tokens rejected for a realm role violation
     * @return a configured {@link KeycloakAuthenticationFilter}
     */
    @Bean(KEYCLOAK_PROTOCOL_FILTER)
    @Conditional(KeycloakAuthenticationModeCondition.class)
    KeycloakAuthenticationFilter keycloakProtocolAuthenticationFilter(TrustedIssuerRegistry registry,
                                                                      IssuerTokenValidators validators,
                                                                      KeycloakRealmRoleConverter keycloakRealmRoleConverter,
                                                                      RealmRoleViolationAuditor realmRoleViolationAuditor) {
        JwtDecoder decoder = registry.issuerSelectingDecoder(Set.of(IssuerType.PLATFORM), validators::forIssuer);
        return new KeycloakAuthenticationFilter(decoder, keycloakRealmRoleConverter, realmRoleViolationAuditor);
    }

    /**
     * Prevents Spring Boot from registering the admin-chain Keycloak filter as a global servlet filter; it
     * runs only inside the admin chain.
     *
     * @param filter the admin-chain Keycloak filter
     * @return a disabled {@link FilterRegistrationBean}
     */
    @Bean
    @Conditional(KeycloakAuthenticationModeCondition.class)
    FilterRegistrationBean<KeycloakAuthenticationFilter> keycloakAuthenticationFilterRegistration(
            @Qualifier(KEYCLOAK_ADMIN_FILTER) KeycloakAuthenticationFilter filter) {
        return disabledRegistration(filter);
    }

    /**
     * Prevents Spring Boot from registering the protocol-chain Keycloak filter as a global servlet filter;
     * it runs only inside the protocol chain.
     *
     * @param filter the protocol-chain Keycloak filter
     * @return a disabled {@link FilterRegistrationBean}
     */
    @Bean
    @Conditional(KeycloakAuthenticationModeCondition.class)
    FilterRegistrationBean<KeycloakAuthenticationFilter> keycloakProtocolAuthenticationFilterRegistration(
            @Qualifier(KEYCLOAK_PROTOCOL_FILTER) KeycloakAuthenticationFilter filter) {
        return disabledRegistration(filter);
    }

    // =========================================================================
    // Conditional Internal Auth beans
    // =========================================================================

    /**
     * Creates the JWT authentication filter for INTERNAL mode.
     * Active only in INTERNAL mode.
     *
     * @param jwtService the JwtService for validating and decoding tokens
     * @return a new {@link InternalJwtAuthenticationFilter}
     */
    @Bean
    @Conditional(InternalAuthenticationModeCondition.class)
    public InternalJwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new InternalJwtAuthenticationFilter(jwtService);
    }

    /**
     * Creates the {@link UserDetailsService} backed by MongoDB for Internal Auth mode.
     *
     * <p>Throws {@link UsernameNotFoundException} on a missing user, per the
     * {@code UserDetailsService.loadUserByUsername} contract. {@link DaoAuthenticationProvider}
     * only recognizes this exception type in {@code retrieveUser()}; any other exception type is
     * wrapped as an {@code InternalAuthenticationServiceException}, which would incorrectly surface
     * a missing user as an internal server error to callers such as {@code AuthController} instead
     * of a clean authentication failure.
     *
     * @param userRepository the user repository
     * @return the user details service
     */
    @Bean
    @Conditional(InternalAuthenticationModeCondition.class)
    UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    /**
     * Creates the {@link DaoAuthenticationProvider} for Internal Auth mode.
     *
     * @param userDetailsService the user details service
     * @param passwordEncoder the password encoder
     * @return the configured DAO authentication provider
     */
    @Bean
    @Conditional(InternalAuthenticationModeCondition.class)
    DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService userDetailsService,
                                                        PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * Creates the {@link AuthenticationManager} for Internal Auth mode.
     *
     * @param daoAuthenticationProvider the DAO authentication provider
     * @return the authentication manager
     */
    @Bean
    @Conditional(InternalAuthenticationModeCondition.class)
    AuthenticationManager authenticationManager(DaoAuthenticationProvider daoAuthenticationProvider) {
        return new ProviderManager(daoAuthenticationProvider);
    }

    // =========================================================================
    // Shared beans
    // =========================================================================

    /**
     * Creates the {@link ApiTenantContextFilter} bean for resolving tenant context from API requests.
     * Not annotated with {@code @Component} to prevent auto-registration as a servlet filter;
     * it is added explicitly to the admin security filter chain only.
     *
     * @param tenantRepository    the tenant registry used to check the {@code X-Tenant-Id} tenant
     * @param auditEventPublisher publisher for the tenant delegation audit event
     * @return the filter instance
     */
    @Bean
    ApiTenantContextFilter apiTenantContextFilter(TenantRepository tenantRepository,
                                                  AuditEventPublisher auditEventPublisher) {
        return new ApiTenantContextFilter(tenantRepository, auditEventPublisher);
    }

    /**
     * Password encoder used for hashing and verifying user passwords.
     *
     * @return a BCrypt password encoder
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS configuration source applying values from application properties.
     *
     * @return the CORS configuration source
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
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

        if (StringUtils.isBlank(allowedCredentials) || Strings.CI.equals(allowedCredentials, "false")) {
            configuration.setAllowCredentials(false);
        } else configuration.setAllowCredentials(Strings.CI.equals(allowedCredentials, "true"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static FilterRegistrationBean<KeycloakAuthenticationFilter> disabledRegistration(
            KeycloakAuthenticationFilter filter) {
        FilterRegistrationBean<KeycloakAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    // Decides an authenticated /internal/** request: verified internal-call claims with ROLE_INTERNAL and a
    // loopback connection. Reading the authentication first makes a request without a valid token fail with
    // 401 before the other checks.
    private static AuthorizationDecision authorizeInternalCall(Supplier<Authentication> authentication,
                                                               RequestAuthorizationContext context) {
        boolean internalRole = authentication.get().getDetails() instanceof InternalCallClaims claims
                && claims.hasInternalRole();
        return new AuthorizationDecision(internalRole && isLoopbackConnection(context.getRequest()));
    }

    /**
     * Tells whether both ends of the request's TCP connection are loopback addresses.
     *
     * <p>The remote address alone is not enough: a forward-headers strategy (auto-enabled on Kubernetes,
     * where Tomcat's RemoteIpValve trusts private and loopback proxies) rewrites it from
     * {@code X-Forwarded-For}. The local socket address is never rewritten, and a connection whose local
     * end is {@code 127.0.0.1} or {@code ::1} can only arrive over the loopback interface.
     *
     * @param request the request
     * @return {@code true} when the remote and the local address are both loopback
     */
    static boolean isLoopbackConnection(ServletRequest request) {
        return LOOPBACK_ADDRESSES.contains(request.getRemoteAddr())
                && LOOPBACK_ADDRESSES.contains(request.getLocalAddr());
    }

    private void applyCommonConfiguration(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .xssProtection(Customizer.withDefaults())
                        .cacheControl(Customizer.withDefaults())
                        .httpStrictTransportSecurity(Customizer.withDefaults())
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                )
                .sessionManagement(AbstractHttpConfigurer::disable);
    }
}
