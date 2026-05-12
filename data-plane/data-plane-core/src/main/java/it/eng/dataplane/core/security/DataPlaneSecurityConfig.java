package it.eng.dataplane.core.security;

import it.eng.dataplane.core.config.DataPlaneProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Secures Data Plane endpoints.
 * /dataflows/** and /controlplanes require authentication.
 * /actuator/health is public.
 */
@Configuration
@EnableWebSecurity
public class DataPlaneSecurityConfig {

    /**
     * Configures security filter chain for the Data Plane.
     * Registers {@link ApiKeyAuthFilter} to validate X-Api-Key headers.
     *
     * @param http HttpSecurity to configure
     * @param properties DataPlaneProperties containing API key configuration
     * @return configured SecurityFilterChain
     * @throws Exception on configuration error
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, DataPlaneProperties properties) throws Exception {
        ApiKeyAuthFilter apiKeyFilter = new ApiKeyAuthFilter(properties);
        http
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
