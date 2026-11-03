package it.eng.connector.configuration;

import it.eng.tools.controller.ApiEndpoints;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

/**
 * Authenticates requests by decoding Keycloak-issued JWTs from the Authorization header.
 *
 * <p>The decoder decides which issuers are trusted, so each security chain gets its own instance: the
 * admin chain trusts tenant, {@code connector-admin} and platform issuers, the protocol chain the platform
 * issuer only.
 *
 * <p>A rejected token leaves the request unauthenticated, so the chain answers 401. A token rejected because
 * it carries a privileged realm role its issuer may not grant is also written to the audit log through
 * {@link RealmRoleViolationAuditor}. A rejection caused by the token itself ({@link BadJwtException}, which
 * includes validation failures) is logged at DEBUG; any other {@link JwtException}, such as signing keys that
 * could not be loaded, is logged at WARN. Rejections are logged without token content.
 */
@Slf4j
public class KeycloakAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_ZONE_PREFIX = ApiEndpoints.INTERNAL_ROOT + "/";

    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter;
    private final RealmRoleViolationAuditor realmRoleViolationAuditor;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    /**
     * Creates the filter using a JWT decoder, a role converter and the realm role violation auditor.
     *
     * @param jwtDecoder the JWT decoder configured for Keycloak
     * @param authoritiesConverter converts JWTs into granted authorities
     * @param realmRoleViolationAuditor writes the audit event of a token rejected for a realm role violation
     * @throws NullPointerException when an argument is {@code null}
     */
    KeycloakAuthenticationFilter(JwtDecoder jwtDecoder, Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter,
            RealmRoleViolationAuditor realmRoleViolationAuditor) {
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder");
        this.authoritiesConverter = Objects.requireNonNull(authoritiesConverter, "authoritiesConverter");
        this.realmRoleViolationAuditor = Objects.requireNonNull(realmRoleViolationAuditor, "realmRoleViolationAuditor");
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
            log.debug("Extracted authorities from JWT: {}", authorities);

            Authentication authentication = new JwtAuthenticationToken(jwt, authorities);

            SecurityContext context = securityContextHolderStrategy.createEmptyContext();
            context.setAuthentication(authentication);
            securityContextHolderStrategy.setContext(context);

            log.info("Authentication successful for user: {} with roles: {}", jwt.getSubject(), authorities);
        } catch (JwtException ex) {
            securityContextHolderStrategy.clearContext();
            if (RealmRoleViolationAuditor.isRealmRoleViolation(ex)) {
                log.warn("Bearer token rejected on {}: it carries a privileged realm role its issuer may not grant",
                        request.getRequestURI());
                realmRoleViolationAuditor.audit(tokenValue, request);
            } else if (ex instanceof BadJwtException) {
                // Caused by the token itself (malformed, untrusted issuer, bad signature, failed validation).
                log.debug("Bearer token rejected: {}", rejectionReason(ex));
            } else {
                // Not the client's fault: the signing keys or the decoder of a trusted issuer are unavailable.
                log.warn("Bearer token could not be verified on {}: {}", request.getRequestURI(), verificationFailure(ex));
            }
        }

        filterChain.doFilter(request, response);
    }

    // Names the failure without token content: the exception type, plus the error codes for a validation failure.
    private static String rejectionReason(JwtException ex) {
        if (ex instanceof JwtValidationException validationException) {
            return ex.getClass().getSimpleName() + " " + validationException.getErrors().stream()
                    .map(OAuth2Error::getErrorCode)
                    .toList();
        }
        return ex.getClass().getSimpleName();
    }

    // Names an infrastructure failure without token content or exception messages: the types only.
    private static String verificationFailure(JwtException ex) {
        Throwable cause = ex.getCause();
        return ex.getClass().getSimpleName() + ", signing keys or decoder unavailable"
                + (cause == null ? "" : " (cause " + cause.getClass().getSimpleName() + ")");
    }

    /**
     * Skips the {@code /internal/**} zone, which only internal-call tokens authenticate.
     *
     * <p>The filter beans are added to their security chains only; their global servlet-filter
     * registration is disabled in {@code ConnectorSecurityConfig}. This check is a second guard: should the
     * filter ever run for the internal zone, it would reject the internal-call token and log an error on
     * every loopback call.
     *
     * @param request the current HTTP request
     * @return {@code true} for requests to {@code /internal/**}
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + INTERNAL_ZONE_PREFIX);
    }
}
