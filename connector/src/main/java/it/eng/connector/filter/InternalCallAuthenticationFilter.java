package it.eng.connector.filter;

import it.eng.tools.auth.internal.InternalCallClaims;
import it.eng.tools.auth.internal.InternalCallTokenIssuer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates requests on the {@code /internal/**} chain with an internal-call token.
 *
 * <p>The bearer token is verified only with the in-memory key of {@link InternalCallTokenIssuer}, so a
 * Keycloak token, an {@code INTERNAL}-mode user token or a token signed by another process never
 * authenticates here. On success the security context holds a {@link PreAuthenticatedAuthenticationToken}
 * whose principal is {@value InternalCallTokenIssuer#SUBJECT}, whose authorities are the token's roles and
 * whose details are the verified {@link InternalCallClaims}. The token itself is not kept.
 *
 * <p>The filter never rejects a request: without a valid token the context stays empty and the chain's
 * authorization rules answer 401. Tokens are never logged.
 *
 * <p>Not a Spring bean, so it is never registered as a global servlet filter.
 */
@Slf4j
public class InternalCallAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final InternalCallTokenIssuer internalCallTokenIssuer;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    /**
     * Creates the filter.
     *
     * @param internalCallTokenIssuer the issuer whose in-memory key verifies the token
     */
    public InternalCallAuthenticationFilter(InternalCallTokenIssuer internalCallTokenIssuer) {
        this.internalCallTokenIssuer = internalCallTokenIssuer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<InternalCallClaims> claims = bearerToken(request).flatMap(internalCallTokenIssuer::verify);
        if (claims.isPresent()) {
            PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(
                    InternalCallTokenIssuer.SUBJECT, null,
                    claims.get().roles().stream().map(SimpleGrantedAuthority::new).toList());
            authentication.setDetails(claims.get());
            SecurityContext context = securityContextHolderStrategy.createEmptyContext();
            context.setAuthentication(authentication);
            securityContextHolderStrategy.setContext(context);
            log.debug("Internal call authenticated for {} {}", request.getMethod(), request.getRequestURI());
        } else {
            securityContextHolderStrategy.clearContext();
            log.debug("Internal call without a valid internal-call token: {} {}",
                    request.getMethod(), request.getRequestURI());
        }
        filterChain.doFilter(request, response);
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION))
                .filter(header -> header.startsWith(BEARER_PREFIX))
                .map(header -> header.substring(BEARER_PREFIX.length()).trim());
    }
}
