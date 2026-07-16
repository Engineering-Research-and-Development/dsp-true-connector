package it.eng.connector.configuration;

import it.eng.connector.filter.ApiTenantContextFilter;
import it.eng.connector.model.Role;
import it.eng.connector.repository.UserRepository;
import it.eng.tools.auth.AuthenticationMode;
import it.eng.tools.auth.AuthenticationModeResolver;
import it.eng.tools.auth.condition.InternalAuthenticationModeCondition;
import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import it.eng.tools.controller.ApiEndpoints;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Unified security configuration for the connector.
 *
 * <p>Defines three {@link SecurityFilterChain} beans matched by URL zone:
 * <ol>
 *   <li><b>Admin chain</b> ({@code /api/**, /actuator/**, /env}) — requires {@code ROLE_ADMIN}.
 *       Keycloak or Basic Auth depending on {@code application.auth.provider}.</li>
 *   <li><b>Protocol chain</b> ({@code /connector/**, /catalog/**, /negotiations/**, /transfers/**})
 *       — requires {@code ROLE_CONNECTOR}. Uses DCP when {@code application.auth.dcp.enabled=true},
 *       otherwise follows the configured provider.</li>
 *   <li><b>Default chain</b> — permits all other requests.</li>
 * </ol>
 *
 * <p>Supported authentication matrix:
 * <pre>
 * provider=KEYCLOAK + dcp.enabled=false  → admin: Keycloak,  protocol: Keycloak
 * provider=KEYCLOAK + dcp.enabled=true   → admin: Keycloak,  protocol: DCP
 * provider=INTERNAL + dcp.enabled=false  → admin: Basic Auth, protocol: Basic Auth
 * provider=INTERNAL + dcp.enabled=true   → admin: Basic Auth, protocol: DCP
 * provider=DISABLED                      → all endpoints: permitAll
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

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Autowired
    @Qualifier("delegatedAuthenticationEntryPoint")
    private AuthenticationEntryPoint authEntryPoint;

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
     * Admin security filter chain covering {@code /api/**}, {@code /actuator/**} and {@code /env}.
     * Requires {@code ROLE_SUPER_ADMIN} for {@code /api/v1/tenants/**},
     * {@code /api/v1/properties/**}, and most {@code /api/v1/users/**} endpoints.
     * {@code GET /api/v1/users/me}, {@code PUT /api/v1/users/*\/update}, and
     * {@code PUT /api/v1/users/*\/password} are accessible to {@code ROLE_ADMIN} and
     * {@code ROLE_SUPER_ADMIN} (self-service only — the service layer enforces ownership).
     * All other {@code /api/**} endpoints require at minimum {@code ROLE_ADMIN}.
     * Disabled mode permits all requests.
     *
     * <p>In INTERNAL mode, both {@link InternalServiceAuthenticationProvider} and
     * {@link DaoAuthenticationProvider} are registered so that internal machine-to-machine
     * calls authenticated as {@code internal-service} bypass tenant-scoped user lookup.
     *
     * @param http the HttpSecurity builder
     * @param keycloakFilter optional Keycloak filter, present when mode is KEYCLOAK
     * @param apiTenantContextFilter the tenant context filter for API requests
     * @param daoAuthenticationProvider the DAO provider for normal user authentication (INTERNAL mode)
     * @param internalServiceAuthProvider the internal-service provider (INTERNAL mode)
     * @return the configured filter chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @Order(1)
    SecurityFilterChain adminFilterChain(HttpSecurity http,
            ObjectProvider<KeycloakAuthenticationFilter> keycloakFilter,
            ObjectProvider<ApiTenantContextFilter> apiTenantContextFilter,
            ObjectProvider<DaoAuthenticationProvider> daoAuthenticationProvider,
            ObjectProvider<InternalServiceAuthenticationProvider> internalServiceAuthProvider) throws Exception {
        applyCommonConfiguration(http);
        http.securityMatcher("/api/**", "/actuator/**", "/env");

        if (authMode == AuthenticationMode.DISABLED) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else if (authMode == AuthenticationMode.KEYCLOAK) {
            http.anonymous(AbstractHttpConfigurer::disable)
                    .addFilterBefore(keycloakFilter.getObject(), UsernamePasswordAuthenticationFilter.class)
                    .addFilterAfter(apiTenantContextFilter.getObject(), UsernamePasswordAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(ApiEndpoints.TENANTS_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
                            .requestMatchers(HttpMethod.GET,  ApiEndpoints.USERS_V1 + "/me")
                                .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
                            .requestMatchers(HttpMethod.PUT,  ApiEndpoints.USERS_V1 + "/*/update")
                                .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
                            .requestMatchers(HttpMethod.PUT,  ApiEndpoints.USERS_V1 + "/*/password")
                                .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
                            .requestMatchers(ApiEndpoints.USERS_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
                            .requestMatchers(ApiEndpoints.PROPERTIES_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
                            .anyRequest().hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name()))
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint));
        } else {
            // INTERNAL: ApiTenantContextFilter must run AFTER BasicAuthenticationFilter so that
            // the Authentication is already populated in SecurityContextHolder when we read it.
            // InternalServiceAuthenticationProvider is checked first; unmatched usernames fall
            // through to DaoAuthenticationProvider for normal user login.
            http.anonymous(AbstractHttpConfigurer::disable)
                    .httpBasic(basic -> basic.authenticationEntryPoint(authEntryPoint))
                    .authenticationManager(new ProviderManager(
                            internalServiceAuthProvider.getObject(),
                            daoAuthenticationProvider.getObject()))
                    .addFilterAfter(apiTenantContextFilter.getObject(), BasicAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(ApiEndpoints.TENANTS_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
                            .requestMatchers(HttpMethod.GET,  ApiEndpoints.USERS_V1 + "/me")
                                .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
                            .requestMatchers(HttpMethod.PUT,  ApiEndpoints.USERS_V1 + "/*/update")
                                .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
                            .requestMatchers(HttpMethod.PUT,  ApiEndpoints.USERS_V1 + "/*/password")
                                .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
                            .requestMatchers(ApiEndpoints.USERS_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
                            .requestMatchers(ApiEndpoints.PROPERTIES_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
                            .anyRequest().hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name()))
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint));
        }
        return http.build();
    }

    /**
     * Protocol security filter chain covering DSP endpoints.
     * Requires {@code ROLE_CONNECTOR}. DCP overrides the provider when enabled.
     * Disabled mode permits all requests.
     *
     * @param http the HttpSecurity builder
     * @param keycloakFilter optional Keycloak filter, present when mode is KEYCLOAK
     * @param dcpFilter optional DCP filter, present when dcp.enabled is true
     * @return the configured filter chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @Order(2)
    SecurityFilterChain protocolFilterChain(HttpSecurity http,
            ObjectProvider<KeycloakAuthenticationFilter> keycloakFilter,
            ObjectProvider<DcpAuthenticationFilter> dcpFilter) throws Exception {
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
                    .httpBasic(basic -> basic.authenticationEntryPoint(authEntryPoint))
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
     * Creates the JWT decoder for validating Keycloak-issued tokens.
     * Active only in Keycloak mode.
     *
     * @return a configured {@link JwtDecoder}
     * @throws IllegalStateException if neither issuer-uri nor jwk-set-uri is configured
     */
    @Bean
    @Conditional(KeycloakAuthenticationModeCondition.class)
    JwtDecoder keycloakJwtDecoder() {
        if (StringUtils.isNotBlank(issuerUri)) {
            return JwtDecoders.fromIssuerLocation(issuerUri);
        }
        if (StringUtils.isNotBlank(jwkSetUri)) {
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }
        throw new IllegalStateException("Keycloak issuer-uri or jwk-set-uri must be configured.");
    }

    /**
     * Creates the Keycloak realm role converter.
     * Active only in Keycloak mode.
     *
     * @return a new {@link KeycloakRealmRoleConverter}
     */
    @Bean
    @Conditional(KeycloakAuthenticationModeCondition.class)
    KeycloakRealmRoleConverter keycloakRealmRoleConverter() {
        return new KeycloakRealmRoleConverter();
    }

    /**
     * Creates the Keycloak authentication filter.
     * Active only in Keycloak mode.
     *
     * @param jwtDecoder the JWT decoder
     * @param keycloakRealmRoleConverter the role converter
     * @return a configured {@link KeycloakAuthenticationFilter}
     */
    @Bean
    @Conditional(KeycloakAuthenticationModeCondition.class)
    KeycloakAuthenticationFilter keycloakAuthenticationFilter(JwtDecoder jwtDecoder,
            KeycloakRealmRoleConverter keycloakRealmRoleConverter) {
        return new KeycloakAuthenticationFilter(jwtDecoder, keycloakRealmRoleConverter);
    }

    // =========================================================================
    // Conditional Internal Auth beans
    // =========================================================================

    /**
     * Creates the {@link UserDetailsService} backed by MongoDB for Internal Auth mode.
     *
     * @param userRepository the user repository
     * @return the user details service
     */
    @Bean
    @Conditional(InternalAuthenticationModeCondition.class)
    UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByEmail(username)
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
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
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
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
     * @return the filter instance
     */
    @Bean
    ApiTenantContextFilter apiTenantContextFilter() {
        return new ApiTenantContextFilter();
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
