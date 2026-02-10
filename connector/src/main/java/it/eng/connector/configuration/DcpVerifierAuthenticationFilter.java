package it.eng.connector.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authentication filter for DCP Verifiable Credentials (Filter+Provider pattern).
 *
 * <p>This filter extracts Bearer tokens from the Authorization header and delegates
 * validation to {@link DcpVerifierAuthenticationProvider} via the AuthenticationManager.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Extract Bearer token from Authorization header</li>
 *   <li>Create {@link DcpBearerToken} (unauthenticated)</li>
 *   <li>Delegate to AuthenticationManager → DcpVerifierAuthenticationProvider</li>
 *   <li>Set SecurityContext if authentication succeeds</li>
 * </ul>
 *
 * <p>Only applied to /catalog/**, /negotiation/**, and /transfers/** endpoints.
 * Can be enabled/disabled via the dcp.vp.enabled property (checked by provider).
 */
@Slf4j
public class DcpVerifierAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
            .getContextHolderStrategy();

    /**
     * Constructs a new DcpVerifierAuthenticationFilterV2.
     *
     * @param authenticationManager The AuthenticationManager for delegating validation
     */
    public DcpVerifierAuthenticationFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            log.trace("No Bearer token found, continuing to next auth method");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Extract the Bearer token
            String selfIssuedIdToken = authHeader.substring(7);

            log.debug("Attempting DCP VP authentication via AuthenticationManager");

            // Create unauthenticated token
            Authentication authRequest = new DcpBearerToken(selfIssuedIdToken);

            // Delegate to AuthenticationManager → DcpVerifierAuthenticationProvider
            Authentication authResult = this.authenticationManager.authenticate(authRequest);

            // Set SecurityContext if authentication succeeded
            if (authResult != null && authResult.isAuthenticated()) {
                SecurityContext context = this.securityContextHolderStrategy.createEmptyContext();
                context.setAuthentication(authResult);
                this.securityContextHolderStrategy.setContext(context);

                log.debug("SecurityContext set with DCP VP authentication");
            } else {
                log.debug("DCP VP authentication returned null or not authenticated, continuing to next auth method");
            }

        } catch (Exception e) {
            // Any exception - clear context and continue
            log.debug("DCP VP authentication error: {}, continuing to next auth method", e.getMessage());
            this.securityContextHolderStrategy.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String requestPath = request.getRequestURI();

        // Only apply this filter to specific endpoints
        if (requestPath == null) {
            return true;
        }

        boolean shouldFilter = requestPath.startsWith("/catalog")
                || requestPath.startsWith("/negotiation")
                || requestPath.startsWith("/transfers");

        return !shouldFilter;
    }
}
