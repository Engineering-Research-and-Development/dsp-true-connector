package it.eng.connector.configuration;

import java.io.IOException;
import java.util.Collection;

import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Authenticates requests by decoding Keycloak-issued JWTs from the Authorization header.
 */
@Slf4j
public class KeycloakAuthenticationFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    /**
     * Creates the filter using a JWT decoder and a role converter.
     *
     * @param jwtDecoder the JWT decoder configured for Keycloak
     * @param authoritiesConverter converts JWTs into granted authorities
     */
    public KeycloakAuthenticationFilter(JwtDecoder jwtDecoder, Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter) {
        this.jwtDecoder = jwtDecoder;
        this.authoritiesConverter = authoritiesConverter;
    }

    /**
     * Resolves a bearer token and populates the security context when it is valid.
     *
     * @param request the current HTTP request
     * @param response the current HTTP response
     * @param filterChain the remaining filter chain
     * @throws ServletException when the filter chain fails
     * @throws IOException when the request cannot be processed
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        log.debug("Processing request to: {} {}", request.getMethod(), request.getRequestURI());

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            log.debug("No Bearer token found in Authorization header");
            filterChain.doFilter(request, response);
            return;
        }

        String tokenValue = authHeader.substring(7);
        log.debug("Found Bearer token, attempting to decode...");

        try {
            Jwt jwt = jwtDecoder.decode(tokenValue);
            log.debug("JWT decoded successfully. Subject: {}, Issuer: {}", jwt.getSubject(), jwt.getIssuer());

            Collection<GrantedAuthority> authorities = authoritiesConverter.convert(jwt);
            log.info("Extracted authorities from JWT: {}", authorities);

            Authentication authentication = new JwtAuthenticationToken(jwt, authorities);

            SecurityContext context = securityContextHolderStrategy.createEmptyContext();
            context.setAuthentication(authentication);
            securityContextHolderStrategy.setContext(context);

            log.info("Authentication successful for user: {} with roles: {}", jwt.getSubject(), authorities);
        } catch (JwtException ex) {
            log.error("JWT validation failed: {}", ex.getMessage());
            securityContextHolderStrategy.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
