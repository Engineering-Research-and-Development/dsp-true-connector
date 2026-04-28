package it.eng.connector.filter;

import it.eng.connector.model.User;
import it.eng.tools.service.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that resolves the tenant identifier from the authenticated principal
 * and stores it in {@link TenantContextHolder} for the duration of the API request.
 * The context is always cleared in a {@code finally} block to prevent leakage.
 *
 * <p>This filter is applied only to {@code /api/**} paths.
 *
 * <p>Super-admin users (those with a {@code null} tenantId) may supply an
 * {@value #HEADER_X_TENANT_ID} request header to declare on behalf of which tenant they
 * are acting.  Regular users always use their own stored tenantId — the header is ignored
 * for them.
 */
@Slf4j
public class ApiTenantContextFilter extends OncePerRequestFilter {

    /** Request header that allows super-admin users to act on behalf of a specific tenant. */
    public static final String HEADER_X_TENANT_ID = "X-Tenant-Id";

    /**
     * Constructs a new {@link ApiTenantContextFilter}.
     */
    public ApiTenantContextFilter() {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                Object principal = auth.getPrincipal();
                String tenantId = null;
                if (principal instanceof User user) {
                    tenantId = user.getTenantId();
                    log.debug("Set tenant context from User principal: {}", tenantId);
                } else if (auth instanceof JwtAuthenticationToken jwtAuth) {
                    tenantId = jwtAuth.getToken().getClaimAsString("tenantId");
                    log.debug("Set tenant context from JWT claim: {}", tenantId);
                }
                // Super-admin (null tenantId) may delegate to a specific tenant via header.
                if (tenantId == null) {
                    String headerTenantId = request.getHeader(HEADER_X_TENANT_ID);
                    if (headerTenantId != null && !headerTenantId.isBlank()) {
                        tenantId = headerTenantId;
                        log.debug("Super-admin acting as tenant from {} header: {}", HEADER_X_TENANT_ID, tenantId);
                    }
                }
                TenantContextHolder.setTenantId(tenantId);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }
}
