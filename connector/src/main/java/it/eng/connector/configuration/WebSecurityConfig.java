package it.eng.connector.configuration;

import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import it.eng.connector.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

    private final JwtAuthenticationProvider jwtAuthenticationProvider;
    private final UserRepository userRepository;

    public WebSecurityConfig(JwtAuthenticationProvider jwtAuthenticationProvider, UserRepository userRepository) {
        this.jwtAuthenticationProvider = jwtAuthenticationProvider;
        this.userRepository = userRepository;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(HttpSecurity http) {
        return new JwtAuthenticationFilter(authenticationManager());
    }

    @Bean
    public BasicAuthenticationFilter basicAuthenticationFilter() {
        return new BasicAuthenticationFilter(authenticationManager());
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(jwtAuthenticationProvider, daoAUthenticationProvider());
    }

    @Bean
    UserDetailsService userDetailsService() {
        return username -> userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
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
                .sessionManagement(sm -> sm.disable())
                .anonymous(anonymus -> anonymus.disable())
                .authorizeHttpRequests((authorize) -> {
                    authorize
                            .requestMatchers(new AntPathRequestMatcher("/env"), new AntPathRequestMatcher("/actuator/**")).hasRole("ADMIN")
                            .requestMatchers(new AntPathRequestMatcher("/connector/**"),
                                    new AntPathRequestMatcher("/negotiations/**"),
                                    new AntPathRequestMatcher("/catalog/**"),
                                    new AntPathRequestMatcher("/transfers/**"))
                            .hasRole("CONNECTOR")
                            .requestMatchers(new AntPathRequestMatcher("/api/**")).hasRole("ADMIN")
                            .anyRequest().permitAll();
                })
                .addFilterBefore(jwtAuthenticationFilter(http), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(basicAuthenticationFilter(), JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

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