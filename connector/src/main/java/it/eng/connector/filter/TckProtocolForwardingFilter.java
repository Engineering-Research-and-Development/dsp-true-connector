package it.eng.connector.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Servlet filter that transparently prepends the configured TCK tenant prefix to inbound
 * DSP protocol requests that arrive without a tenant path segment.
 *
 * <p>Activated only when the Spring {@code tck} profile is active (see {@code application-tck.properties}).
 * — which sends requests to bare protocol paths such as {@code /catalog/request} or
 * {@code /negotiations/request} — to reach the tenant-scoped protocol controllers that
 * are mapped at {@code /{tenantId}/catalog/...}, {@code /{tenantId}/negotiations/...}, etc.
 *
 * <p>The filter wraps the request with a {@link TenantPrefixRequestWrapper} that returns the
 * tenant-prefixed path for {@code getRequestURI()}, {@code getServletPath()}, and
 * {@code getPathInfo()} so that Spring MVC's {@code DispatcherServlet} routes the request to
 * the correct controller while Spring Security sees the same request in the same dispatch cycle.
 *
 * <p>Example: {@code POST /catalog/request} with {@code application.tck.tenant.id=engineering}
 * is routed to {@code POST /engineering/catalog/request} via the wrapped request.
 */
@Slf4j
@Component
@Profile("tck")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TckProtocolForwardingFilter extends OncePerRequestFilter {

    /** DSP protocol path prefixes that require the tenant segment. */
    private static final List<String> PROTOCOL_PREFIXES = List.of(
            "/catalog/",
            "/negotiations/",
            "/transfers/",
            "/connector/",
            "/consumer/"
    );

    /** Exact protocol path segments used as entry points by the TCK (without trailing slash). */
    private static final List<String> PROTOCOL_EXACT = List.of(
            "/catalog",
            "/negotiations",
            "/transfers",
            "/connector",
            "/consumer"
    );

    @Value("${application.tck.tenant.id:engineering}")
    private String tckTenantId;

    /**
     * Intercepts requests to non-tenant-prefixed protocol paths and continues the filter chain
     * with a wrapped request that presents the tenant-prefixed URI to the {@code DispatcherServlet}.
     *
     * @param request     the incoming HTTP request
     * @param response    the HTTP response
     * @param filterChain the remaining filter chain
     * @throws ServletException if a servlet error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        String pathWithoutContext = contextPath.isEmpty() ? path : path.substring(contextPath.length());

        if (needsTenantPrefix(pathWithoutContext)) {
            String tenantPrefixedPath = contextPath + "/" + tckTenantId + pathWithoutContext;
            log.debug("TCK routing filter: {} {} -> {}", request.getMethod(), pathWithoutContext, tenantPrefixedPath);
            filterChain.doFilter(new TenantPrefixRequestWrapper(request, tenantPrefixedPath), response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Returns {@code true} if the path matches a known DSP protocol prefix but does NOT
     * already start with the TCK tenant segment.
     *
     * @param path the request path (without context path)
     * @return {@code true} if the path should be prefixed with the tenant ID
     */
    private boolean needsTenantPrefix(String path) {
        if (path.startsWith("/" + tckTenantId + "/")) {
            return false;
        }
        for (String prefix : PROTOCOL_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        for (String exact : PROTOCOL_EXACT) {
            if (path.equals(exact)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Request wrapper that overrides the URI/path methods to return the tenant-prefixed path.
     * Used to transparently route DSP protocol requests to the tenant-scoped controller handlers.
     */
    private static class TenantPrefixRequestWrapper extends HttpServletRequestWrapper {

        private final String overriddenUri;
        private final String overriddenServletPath;

        /**
         * Creates a wrapper that presents {@code tenantPrefixedUri} as the request URI.
         *
         * @param request            the original request
         * @param tenantPrefixedUri  the URI with the tenant prefix prepended
         */
        TenantPrefixRequestWrapper(HttpServletRequest request, String tenantPrefixedUri) {
            super(request);
            this.overriddenUri = tenantPrefixedUri;
            // Servlet path = URI minus context path
            String ctx = request.getContextPath();
            this.overriddenServletPath = ctx.isEmpty() ? tenantPrefixedUri : tenantPrefixedUri.substring(ctx.length());
        }

        /** {@inheritDoc} */
        @Override
        public String getRequestURI() {
            return overriddenUri;
        }

        /** {@inheritDoc} */
        @Override
        public String getServletPath() {
            return overriddenServletPath;
        }

        /** {@inheritDoc} */
        @Override
        public String getPathInfo() {
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer();
            url.append(getScheme()).append("://").append(getServerName());
            int port = getServerPort();
            if ((getScheme().equals("http") && port != 80) || (getScheme().equals("https") && port != 443)) {
                url.append(":").append(port);
            }
            url.append(overriddenUri);
            return url;
        }
    }
}
