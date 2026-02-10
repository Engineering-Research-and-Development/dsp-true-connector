package it.eng.connector.configuration;

import it.eng.connector.repository.UserRepository;
import it.eng.tools.service.ApplicationPropertiesService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

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

    private final DcpVerifierAuthenticationProvider dcpVerifierAuthenticationProvider;
    private final UserRepository userRepository;
    private final ApplicationPropertiesService applicationPropertiesService;

    public WebSecurityConfig(DcpVerifierAuthenticationProvider dcpVerifierAuthenticationProvider,
                             UserRepository userRepository,
    		ApplicationPropertiesService applicationPropertiesService) {
        this.dcpVerifierAuthenticationProvider = dcpVerifierAuthenticationProvider;
        this.userRepository = userRepository;
        this.applicationPropertiesService = applicationPropertiesService;
    }

    /**
     * Bean for DCP Verifier authentication filter (Filter+Provider pattern).
     * This filter extracts Bearer tokens and delegates validation to DcpVerifierAuthenticationProvider.
     * Only applied to /catalog/**, /negotiation/**, and /transfers/** endpoints.
     *
     * @return The configured DcpVerifierAuthenticationFilterV2 instance
     */
    @Bean
    DcpVerifierAuthenticationFilter dcpVerifierAuthenticationFilterV2() {
        return new DcpVerifierAuthenticationFilter(authenticationManager());
    }

    /**
     * Bean for Basic authentication filter using username and password.
     *
     * @return The configured BasicAuthenticationFilter instance
     */
    @Bean
    BasicAuthenticationFilter basicAuthenticationFilter() {
        return new BasicAuthenticationFilter(authenticationManager());
    }
    
    /**
     * Bean for Dataspace Protocol endpoints authentication filter.
     * This filter enables/disables authentication based on application configuration.
     *
     * @param applicationPropertiesService Service providing application configuration properties
     * @return The configured DataspaceProtocolEndpointsAuthenticationFilter instance
     */
    @Bean
    DataspaceProtocolEndpointsAuthenticationFilter protocolEndpointsAuthenticationFilter(ApplicationPropertiesService applicationPropertiesService) {
    	return new DataspaceProtocolEndpointsAuthenticationFilter(applicationPropertiesService);
    }

    /**
     * Authentication manager bean.
     * Configures authentication providers: DCP VP provider first, then DAO provider (Basic auth fallback).
     * Uses Spring Security's standard ProviderManager to chain providers.
     *
     * @return The configured AuthenticationManager instance
     */
    @Bean
    AuthenticationManager authenticationManager() {
        // DCP VP authentication via provider (checks dcp.vp.enabled internally)
        // If disabled or validation fails, falls back to Basic auth via DAO provider
        return new ProviderManager(dcpVerifierAuthenticationProvider, daoAUthenticationProvider());
    }

    @Bean
    UserDetailsService userDetailsService() {
        return username -> userRepository.findByEmail(username)
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
    }

    @Bean
    DaoAuthenticationProvider daoAUthenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    RedirectStrategy noRedirectStrategy() {
        return new RedirectStrategy() {

            @Override
            public void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IOException {
                // Do nothing
            }
        };
    }

    @Bean
    SimpleUrlAuthenticationSuccessHandler successHandler() {
        final SimpleUrlAuthenticationSuccessHandler successHandler = new SimpleUrlAuthenticationSuccessHandler();
        successHandler.setRedirectStrategy(noRedirectStrategy());
        return successHandler;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                             com.fasterxml.jackson.databind.ObjectMapper objectMapper) throws Exception {
        http
                .csrf(crsf -> crsf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers ->
                        headers
                                .contentTypeOptions(Customizer.withDefaults())
                                .xssProtection(Customizer.withDefaults())
                                .cacheControl(Customizer.withDefaults())
                                .httpStrictTransportSecurity(Customizer.withDefaults())
                                .frameOptions(frame -> frame.sameOrigin())
                )
                .sessionManagement(sm -> sm.disable())
                .anonymous(anonymus -> anonymus.disable())
                .authorizeHttpRequests((authorize) -> {
                    authorize
                            .requestMatchers(new AntPathRequestMatcher("/env"), new AntPathRequestMatcher("/actuator/**")).hasRole("ADMIN")
                            // DSP protocol endpoints require CONNECTOR role
                            // These are authenticated via DCP VP (if enabled) or Basic auth (fallback)
                            .requestMatchers(
                                    new AntPathRequestMatcher("/catalog/**"),
                                    new AntPathRequestMatcher("/negotiations/**"),
                                    new AntPathRequestMatcher("/transfers/**"))
                            .hasRole("CONNECTOR")
                            // Development/Testing token generator (disable in production)
                            .requestMatchers(new AntPathRequestMatcher("/api/dev/token/**")).permitAll()
                            .requestMatchers(new AntPathRequestMatcher("/api/**")).hasRole("ADMIN")
                            // DCP endpoints handle their own authentication (Self-Issued ID Tokens)
                            .requestMatchers(new AntPathRequestMatcher("/dcp/**"))
                                .permitAll()
                            .anyRequest().permitAll();
                })
                .addFilterBefore(protocolEndpointsAuthenticationFilter(applicationPropertiesService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(dcpVerifierAuthenticationFilterV2(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(basicAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling((exHandler) -> exHandler.authenticationEntryPoint(authEntryPoint));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

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

        if (StringUtils.isBlank(allowedCredentials) || StringUtils.equals(allowedCredentials, "false")) {
            configuration.setAllowCredentials(false);
        } else if (StringUtils.equals(allowedCredentials, "true")) {
            configuration.setAllowCredentials(true);
        } else{
            configuration.setAllowCredentials(false);
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
