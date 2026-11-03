package it.eng.connector.filter;

import it.eng.tools.auth.internal.InternalCallClaims;
import it.eng.tools.service.TenantContextHolder;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InternalTenantContextFilterTest {

    private final InternalTenantContextFilter filter = new InternalTenantContextFilter();
    private final AtomicReference<String> tenantSeenDownstream = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        tenantSeenDownstream.set("unset");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    private void run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                tenantSeenDownstream.set(TenantContextHolder.getTenantId());
            }
        });
    }

    private static void authenticate(Optional<String> tenantId) {
        PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(
                "internal-call", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
        authentication.setDetails(new InternalCallClaims(List.of("ROLE_INTERNAL"), tenantId));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static MockHttpServletRequest requestWithTenantHeader(String headerTenant) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/datasets/1/formats");
        request.addHeader(TenantContextHolder.HEADER_X_TENANT_ID, headerTenant);
        return request;
    }

    @Test
    @DisplayName("The tenant comes from the verified token and X-Tenant-Id is ignored")
    void tenantFromTokenOnly() throws Exception {
        authenticate(Optional.of("tenant-a"));

        run(requestWithTenantHeader("tenant-b"));

        assertEquals("tenant-a", tenantSeenDownstream.get());
        assertNull(TenantContextHolder.getTenantId(), "The tenant context must be cleared after the request");
    }

    @Test
    @DisplayName("A token without tenant runs with no tenant context, even with X-Tenant-Id")
    void noTenantInToken() throws Exception {
        authenticate(Optional.empty());

        run(requestWithTenantHeader("tenant-b"));

        assertNull(tenantSeenDownstream.get());
    }

    @Test
    @DisplayName("Without an internal-call authentication no tenant is set")
    void noInternalAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of()));

        run(requestWithTenantHeader("tenant-b"));

        assertNull(tenantSeenDownstream.get());
    }

    @Test
    @DisplayName("A tenant left over on the thread is replaced and cleared")
    void staleTenantIsReplaced() throws Exception {
        TenantContextHolder.setTenantId("stale");
        authenticate(Optional.empty());

        run(requestWithTenantHeader("tenant-b"));

        assertNull(tenantSeenDownstream.get());
        assertNull(TenantContextHolder.getTenantId());
    }
}
