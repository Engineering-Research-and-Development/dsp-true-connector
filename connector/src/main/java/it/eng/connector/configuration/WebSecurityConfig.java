package it.eng.connector.configuration;

import it.eng.connector.repository.UserRepository;
import it.eng.connector.service.JwtProcessingService;
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
import org.springframework.security.config.http.SessionCreationPolicy;
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
    private AuthenticationEntryPoint protocolAuthEntryPoint;

    @Autowired
    @Qualifier("apiAuthenticationEntryPoint")
    private AuthenticationEntryPoint apiAuthEntryPoint;

    private final JwtAuthenticationProvider jwtAuthenticationProvider;
    private final UserRepository userRepository;
    private final ApplicationPropertiesService applicationPropertiesService;
    private final JwtProcessingService jwtProcessingService;

    public WebSecurityConfig(JwtAuthenticationProvider jwtAuthenticationProvider, UserRepository userRepository,
                             ApplicationPropertiesService applicationPropertiesService,
                             JwtProcessingService jwtProcessingService) {
        this.jwtAuthenticationProvider = jwtAuthenticationProvider;
        this.userRepository = userRepository;
        this.applicationPropertiesService = applicationPropertiesService;
        this.jwtProcessingService = jwtProcessingService;
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(HttpSecurity http) {
        return new JwtAuthenticationFilter(authenticationManager());
    }

    @Bean
    ApiJwtAuthenticationFilter apiJwtAuthenticationFilter(JwtProcessingService jwtProcessingService) {
        return new ApiJwtAuthenticationFilter(jwtProcessingService);
    }

    @Bean
    BasicAuthenticationFilter basicAuthenticationFilter() {
        return new BasicAuthenticationFilter(authenticationManager());
    }

    @Bean
    DataspaceProtocolEndpointsAuthenticationFilter protocolEndpointsAuthenticationFilter(ApplicationPropertiesService applicationPropertiesService) {
        return new DataspaceProtocolEndpointsAuthenticationFilter(applicationPropertiesService);
    }

    @Bean
    AuthenticationManager authenticationManager() {
        return new ProviderManager(jwtAuthenticationProvider, daoAUthenticationProvider());
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
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
                // Fix session management - properly disable sessions and request cache
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .anonymous(anonymus -> anonymus.disable())
                .authorizeHttpRequests((authorize) -> {
                    authorize
                            .requestMatchers(new AntPathRequestMatcher("/env"), new AntPathRequestMatcher("/actuator/**")).hasRole("ADMIN")
                            // Allow authentication endpoints without authentication
                            .requestMatchers(new AntPathRequestMatcher("/api/v1/auth/login"),
                                    new AntPathRequestMatcher("/api/v1/auth/register"),
                                    new AntPathRequestMatcher("/api/v1/auth/refresh")).permitAll()
                            // User profile management - requires authentication (USER or ADMIN)
                            .requestMatchers(new AntPathRequestMatcher("/api/v1/profile/**"),
                                    new AntPathRequestMatcher("/api/v1/auth/me"),
                                    new AntPathRequestMatcher("/api/v1/auth/logout")).hasAnyRole("USER", "ADMIN")
                            // Admin-only user management
                            .requestMatchers(new AntPathRequestMatcher("/api/v1/users/**")).hasRole("ADMIN")
                            // Catalog, dataset, dataservices, distributions, negotiation, transfer endpoints - require authentication (USER or ADMIN)
                            .requestMatchers(new AntPathRequestMatcher("/api/v1/catalogs/**"),
                                    new AntPathRequestMatcher("/api/v1/datasets/**"),
                                    new AntPathRequestMatcher("/api/v1/dataservices/**"),
                                    new AntPathRequestMatcher("/api/v1/distributions/**"),
                                    new AntPathRequestMatcher("/api/v1/negotiations/**"),
                                    new AntPathRequestMatcher("/api/v1/transfers/**")).hasAnyRole("USER", "ADMIN")
                            // Properties management - ADMIN only
                            .requestMatchers(new AntPathRequestMatcher("/api/v1/properties/**")).hasRole("ADMIN")
                            // TODO consider wrapping up all protocol endpoints under single context (/protocol/ or /dsp/ or anything else)
                            .requestMatchers(new AntPathRequestMatcher("/connector/**"),
                                    new AntPathRequestMatcher("/negotiations/**"),
                                    new AntPathRequestMatcher("/catalog/**"),
                                    new AntPathRequestMatcher("/transfers/**"))
                            .hasRole("CONNECTOR")
                            // All other requests require authentication
                            .anyRequest().authenticated();
                })
                .addFilterBefore(protocolEndpointsAuthenticationFilter(applicationPropertiesService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiJwtAuthenticationFilter(jwtProcessingService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter(http), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(basicAuthenticationFilter(), JwtAuthenticationFilter.class)
                // Configure different authentication entry points for API vs Protocol endpoints
                .exceptionHandling((exHandler) -> exHandler
                        .defaultAuthenticationEntryPointFor(apiAuthEntryPoint, new AntPathRequestMatcher("/api/**"))
                        .defaultAuthenticationEntryPointFor(protocolAuthEntryPoint, new AntPathRequestMatcher("/**")));
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
        } else {
            configuration.setAllowCredentials(false);
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
} 