package it.eng.connector.filter;

import it.eng.tools.auth.internal.InternalCallClaims;
import it.eng.tools.service.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Sets {@link TenantContextHolder} on the {@code /internal/**} chain from the verified {@code tenant_id}
 * claim of the internal-call token.
 *
 * <p>The {@value TenantContextHolder#HEADER_X_TENANT_ID} header is never read: the tenant comes only from
 * the token that {@link InternalCallAuthenticationFilter} verified. A token without a tenant runs with no
 * tenant context. The context is cleared in a {@code finally} block.
 *
 * <p>Not a Spring bean, so it is never registered as a global servlet filter.
 */
@Slf4j
public class InternalTenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Optional<String> tenantId = verifiedTenant(SecurityContextHolder.getContext().getAuthentication());
            TenantContextHolder.setTenantId(tenantId.orElse(null));
            log.debug("Internal call tenant context: {}", tenantId.orElse("<none>"));
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private static Optional<String> verifiedTenant(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getDetails() instanceof InternalCallClaims claims) {
            return claims.tenantId();
        }
        return Optional.empty();
    }
}
