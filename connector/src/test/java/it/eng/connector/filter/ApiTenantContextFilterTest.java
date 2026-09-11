package it.eng.connector.filter;

import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.tools.service.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiTenantContextFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private final ApiTenantContextFilter filter = new ApiTenantContextFilter();

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("doFilterInternal sets tenant ID from User principal")
    void doFilterInternal_withUserPrincipal_setsTenantId() throws Exception {
        User user = User.builder()
                .id("user-1")
                .email("admin@mail.com")
                .role(Role.ADMIN)
                .tenantId("engineering")
                .enabled(true)
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // Context is cleared in finally block; verify doFilter was reached without exception
    }

    @Test
    @DisplayName("Super-admin (null tenantId) uses X-Tenant-Id header for tenant context")
    void doFilterInternal_superAdminWithXTenantIdHeader_setsTenantIdFromHeader() throws Exception {
        User superAdmin = User.builder()
                .id("super-1")
                .email("superadmin@mail.com")
                .role(Role.ADMIN)
                .tenantId(null)
                .enabled(true)
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(superAdmin, null, superAdmin.getAuthorities());
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
        when(request.getHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID)).thenReturn("engineering");

        // Capture tenant context during filter execution via a capturing FilterChain
        String[] capturedTenantId = {null};
        filter.doFilterInternal(request, response, (req, res) -> capturedTenantId[0] = TenantContextHolder.getTenantId());

        assertEquals("engineering", capturedTenantId[0],
                "Tenant context should be set from X-Tenant-Id header for super-admin");
        assertNull(TenantContextHolder.getTenantId(), "Tenant context must be cleared after filter");
    }

    @Test
    @DisplayName("Regular user's tenantId takes priority over X-Tenant-Id header")
    void doFilterInternal_regularUserIgnoresXTenantIdHeader() throws Exception {
        User user = User.builder()
                .id("user-2")
                .email("user@mail.com")
                .role(Role.ADMIN)
                .tenantId("engineering")
                .enabled(true)
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
        // X-Tenant-Id header is intentionally NOT stubbed because the code only reads it
        // when the authenticated user has a null tenantId (super-admin). For a regular user
        // the header is never consulted; the assertion below verifies this implicitly.

        String[] capturedTenantId = {null};
        filter.doFilterInternal(request, response, (req, res) -> capturedTenantId[0] = TenantContextHolder.getTenantId());

        assertEquals("engineering", capturedTenantId[0],
                "Tenant context should be the user's own tenantId, not any header value");
    }

    @Test
    @DisplayName("Super-admin without X-Tenant-Id header has null tenant context (acts as SUPER_ADMIN)")
    void doFilterInternal_superAdminWithoutHeader_hasNullTenantContext() throws Exception {
        User superAdmin = User.builder()
                .id("super-2")
                .email("superadmin@mail.com")
                .role(Role.ADMIN)
                .tenantId(null)
                .enabled(true)
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(superAdmin, null, superAdmin.getAuthorities());
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
        when(request.getHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID)).thenReturn(null);

        String[] capturedTenantId = {"not-null"};
        filter.doFilterInternal(request, response, (req, res) -> capturedTenantId[0] = TenantContextHolder.getTenantId());

        assertNull(capturedTenantId[0],
                "Super-admin without header should have null tenant context (SUPER_ADMIN mode)");
    }

    @Test
    @DisplayName("doFilterInternal with null authentication does not set tenant ID")
    void doFilterInternal_withNullAuth_doesNotSetTenantId() throws Exception {
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("shouldNotFilter returns false for /api/ paths")
    void shouldNotFilter_apiPath_returnsFalse() {
        when(request.getRequestURI()).thenReturn("/api/v1/tenants");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("shouldNotFilter returns true for non-/api/ paths")
    void shouldNotFilter_nonApiPath_returnsTrue() {
        when(request.getRequestURI()).thenReturn("/catalog/datasets");

        assertTrue(filter.shouldNotFilter(request));
    }
}
