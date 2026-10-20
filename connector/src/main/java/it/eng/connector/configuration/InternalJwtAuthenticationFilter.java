package it.eng.connector.configuration;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import it.eng.tools.auth.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Filter that validates JWT access tokens for INTERNAL mode admin endpoints.
 *
 * <p>Reuses {@link JwtService}'s verification logic to avoid duplicating configuration
 * and maintaining multiple sources of truth for the signing key.
 *
 * <p>On successful validation, populates the {@link SecurityContext} with a
 * {@link JwtAuthenticationToken} constructed from mapped claim values. Authorities are read from
 * {@code realm_access.roles} with {@link KeycloakRealmRoleConverter}, and the principal name is the
 * {@code email} claim.
 *
 * <p>If validation fails (e.g., token is expired or tampered), the filter clears
 * the security context and continues the filter chain, allowing the request to fall through
 * to Basic Authentication (if Basic credentials are provided) or default denial mechanisms.
 */
@Slf4j
public class InternalJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final KeycloakRealmRoleConverter roleConverter = new KeycloakRealmRoleConverter();
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    /**
     * Creates a new filter using the specified {@link JwtService}.
     *
     * @param jwtService the service used to verify and decode JWT tokens
     */
    public InternalJwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        log.debug("Processing INTERNAL JWT request to: {} {}", request.getMethod(), request.getRequestURI());

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            log.debug("No Bearer token found in Authorization header");
            filterChain.doFilter(request, response);
            return;
        }

        String tokenValue = authHeader.substring(7);
        log.debug("Found Bearer token, attempting to decode...");

        try {
            //TODO find a way to simplify this JWT handling. Either return org.springframework.security.oauth2.jwt.Jwt from JwtService
            // or use a common library to decode and validate the token. Currently, we are using Auth0's JWT library for decoding and validation,
            // but Spring Security has its own JWT handling that could be leveraged to avoid this duplication.
            DecodedJWT decodedJWT = jwtService.verifyAndDecode(tokenValue);

            // Access token validation: reject if it contains token_type = refresh
            String tokenType = decodedJWT.getClaim(JwtService.TOKEN_TYPE_CLAIM).asString();
            if (JwtService.REFRESH_TOKEN_TYPE.equals(tokenType)) {
                throw new JWTVerificationException("Refresh token cannot be used as an access token");
            }

            log.debug("INTERNAL JWT decoded successfully. Subject: {}", decodedJWT.getSubject());

            // Safe conversion of issuedAt/expiresAt to support older and newer Auth0 JWT dependency versions
            Instant issuedAt = decodedJWT.getIssuedAt() != null ? decodedJWT.getIssuedAt().toInstant() : Instant.now();
            Instant expiresAt = decodedJWT.getExpiresAt() != null ? decodedJWT.getExpiresAt().toInstant() : Instant.now();

            Map<String, Object> headers = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", decodedJWT.getSubject());
            putIfPresent(claims, JwtService.EMAIL_CLAIM, decodedJWT.getClaim(JwtService.EMAIL_CLAIM).asString());
            putIfPresent(claims, JwtService.REALM_ACCESS_CLAIM, decodedJWT.getClaim(JwtService.REALM_ACCESS_CLAIM).asMap());
            putIfPresent(claims, JwtService.TENANT_ID_CLAIM, decodedJWT.getClaim(JwtService.TENANT_ID_CLAIM).asString());
            putIfPresent(claims, "jti", decodedJWT.getId());
            claims.put("iat", issuedAt);
            claims.put("exp", expiresAt);

            // Construct Spring Security Jwt and use it to instantiate JwtAuthenticationToken
            Jwt springJwt = new Jwt(tokenValue, issuedAt, expiresAt, headers, claims);
            // Roles sit under realm_access.roles, as in a Keycloak token, so the same converter applies.
            Collection<GrantedAuthority> authorities = roleConverter.convert(springJwt);
            log.debug("Extracted authorities from INTERNAL JWT: {}", authorities);
            // The principal name stays the email, which the user services compare ownership against.
            Authentication authentication = new JwtAuthenticationToken(springJwt, authorities,
                    (String) claims.get(JwtService.EMAIL_CLAIM));

            SecurityContext context = securityContextHolderStrategy.createEmptyContext();
            context.setAuthentication(authentication);
            securityContextHolderStrategy.setContext(context);

            log.info("INTERNAL JWT Authentication successful for user: {} with roles: {}", decodedJWT.getSubject(), authorities);
        } catch (JWTVerificationException | IllegalArgumentException ex) {
            log.error("INTERNAL JWT validation failed: {}", ex.getMessage());
            securityContextHolderStrategy.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private static void putIfPresent(Map<String, Object> claims, String name, Object value) {
        if (value != null) {
            claims.put(name, value);
        }
    }
}