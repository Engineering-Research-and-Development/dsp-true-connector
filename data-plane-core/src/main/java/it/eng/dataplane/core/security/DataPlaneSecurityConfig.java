package it.eng.dataplane.core.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

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
     *
     * @param http HttpSecurity to configure
     * @return configured SecurityFilterChain
     * @throws Exception on configuration error
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> {});
        return http.build();
    }
}
