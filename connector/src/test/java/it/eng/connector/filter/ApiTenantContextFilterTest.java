package it.eng.connector.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.connector.configuration.InternalJwtAuthenticationFilter;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.tools.auth.jwt.JwtProperties;
import it.eng.tools.auth.jwt.JwtService;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.TenantContextHolder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiTenantContextFilterTest {

    private static final String API_PATH = "/api/v1/datasets";
    private static final String TENANT_A = "engineering-a";
    private static final String TENANT_B = "engineering-b";
    private static final String SECRET = "unit-test-secret-value-with-at-least-256-bits!!";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private AuditEventPublisher auditEventPublisher;

    private ApiTenantContextFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new ApiTenantContextFilter(tenantRepository, auditEventPublisher);
        request = new MockHttpServletRequest("GET", API_PATH);
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------------
    // Tenant from the principal
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("A User principal gets its stored tenant")
    void userPrincipal_setsStoredTenant() throws Exception {
        authenticate(userAuthentication(Role.ADMIN, TENANT_A));

        assertEquals(TENANT_A, runAndCaptureTenant());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("A Keycloak tenant-realm token gets the tenant from its tenant_id claim")
    void keycloakToken_setsTenantFromTenantIdClaim() throws Exception {
        authenticate(jwtAuthentication(Map.of("tenant_id", TENANT_A), Role.ADMIN));

        assertEquals(TENANT_A, runAndCaptureTenant());
    }

    @Test
    @DisplayName("An INTERNAL login token gets the tenant from its tenant_id claim")
    void internalToken_setsTenantFromTenantIdClaim() throws Exception {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(SECRET);
        jwtProperties.setAccessExpirationMs(900_000L);
        jwtProperties.setRefreshExpirationMs(604_800_000L);
        JwtService jwtService = new JwtService(jwtProperties);
        jwtService.init();
        String accessToken = jwtService.issueTokenPair("user-1", "admin@mail.com", List.of("ADMIN"),
                TENANT_A, Map.of()).accessToken();
        MockHttpServletRequest loginRequest = new MockHttpServletRequest("GET", API_PATH);
        loginRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        new InternalJwtAuthenticationFilter(jwtService)
                .doFilter(loginRequest, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(TENANT_A, runAndCaptureTenant());
    }

    @Test
    @DisplayName("The old tenantId claim name is no longer read")
    void legacyTenantIdClaim_isIgnored() throws Exception {
        authenticate(jwtAuthentication(Map.of("tenantId", TENANT_A), Role.ADMIN));

        assertNull(runAndCaptureTenant());
    }

    @Test
    @DisplayName("A super-admin without the header keeps a null tenant")
    void superAdminWithoutHeader_hasNullTenant() throws Exception {
        authenticate(jwtAuthentication(Map.of(), Role.SUPER_ADMIN));

        assertNull(runAndCaptureTenant());
        verifyNoInteractions(tenantRepository, auditEventPublisher);
    }

    @Test
    @DisplayName("A blank header is treated as no header")
    void superAdminWithBlankHeader_hasNullTenant() throws Exception {
        authenticate(jwtAuthentication(Map.of(), Role.SUPER_ADMIN));
        request.addHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID, "  ");

        assertNull(runAndCaptureTenant());
        verifyNoInteractions(tenantRepository, auditEventPublisher);
    }

    // ---------------------------------------------------------------------
    // X-Tenant-Id delegation
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("A super-admin with the header of an enabled tenant acts for it, and the delegation is audited")
    void superAdminWithEnabledTenant_setsTenantAndAudits() throws Exception {
        authenticate(jwtAuthentication(Map.of("email", "superadmin@mail.com"), Role.SUPER_ADMIN));
        request.addHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID, TENANT_B);
        when(tenantRepository.findById(TENANT_B)).thenReturn(Optional.of(tenant(TENANT_B, true)));

        assertEquals(TENANT_B, runAndCaptureTenant());

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishEvent(event.capture());
        assertEquals(AuditEventType.TENANT_DELEGATION, event.getValue().getEventType());
        assertEquals("Super-admin acting for a tenant", event.getValue().getDescription());
        assertEquals("superadmin@mail.com", event.getValue().getUsername());
        assertEquals(TENANT_B, event.getValue().getTenantId());
        assertEquals(Map.of("principal", "superadmin@mail.com", "targetTenantId", TENANT_B),
                event.getValue().getDetails());
    }

    @Test
    @DisplayName("A super-admin JWT without sub still delegates; the audit names the principal as unknown")
    void superAdminJwtWithoutSubject_auditsUnknownPrincipal() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("http://kc/realms/connector-admin")
                .claim("azp", "keycloak-api")
                .build();
        authenticate(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(Role.SUPER_ADMIN.authorityName()))));
        request.addHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID, TENANT_B);
        when(tenantRepository.findById(TENANT_B)).thenReturn(Optional.of(tenant(TENANT_B, true)));

        assertEquals(TENANT_B, runAndCaptureTenant());

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishEvent(event.capture());
        assertEquals(AuditEventType.TENANT_DELEGATION, event.getValue().getEventType());
        assertEquals(Map.of("principal", "unknown", "targetTenantId", TENANT_B), event.getValue().getDetails());
    }

    @Test
    @DisplayName("An INTERNAL super-admin User principal may delegate too")
    void superAdminUserPrincipalWithEnabledTenant_setsTenant() throws Exception {
        authenticate(userAuthentication(Role.SUPER_ADMIN, null));
        request.addHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID, TENANT_A);
        when(tenantRepository.findById(TENANT_A)).thenReturn(Optional.of(tenant(TENANT_A, true)));

        assertEquals(TENANT_A, runAndCaptureTenant());
        verify(auditEventPublisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("A super-admin with the header of an unknown tenant gets 400")
    void superAdminWithUnknownTenant_returns400() throws Exception {
        authenticate(jwtAuthentication(Map.of(), Role.SUPER_ADMIN));
        request.addHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID, "unknown\r\nforged-log-line");
        when(tenantRepository.findById(anyString())).thenReturn(Optional.empty());

        assertChainNotCalled();
        assertErrorResponse(400, ApiTenantContextFilter.UNKNOWN_TENANT_MESSAGE);
        assertFalse(response.getContentAsString().contains("forged-log-line"), "The header value is not echoed");
        verifyNoInteractions(auditEventPublisher);
    }

    @Test
    @DisplayName("A super-admin with the header of a disabled tenant gets 400")
    void superAdminWithDisabledTenant_returns400() throws Exception {
        authenticate(jwtAuthentication(Map.of(), Role.SUPER_ADMIN));
        request.addHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID, TENANT_B);
        when(tenantRepository.findById(TENANT_B)).thenReturn(Optional.of(tenant(TENANT_B, false)));

        assertChainNotCalled();
        assertErrorResponse(400, ApiTenantContextFilter.UNKNOWN_TENANT_MESSAGE);
        verifyNoInteractions(auditEventPublisher);
    }

    @Test
    @DisplayName("A tenant token that sends the header gets 403, and the header is never used")
    void tenantTokenWithHeader_returns403() throws Exception {
        authenticate(jwtAuthentication(Map.of("tenant_id", TENANT_A), Role.ADMIN));
        request.addHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID, TENANT_B);

        assertChainNotCalled();
        assertErrorResponse(403, ApiTenantContextFilter.HEADER_NOT_ALLOWED_MESSAGE);
        verifyNoInteractions(tenantRepository, auditEventPublisher);
    }

    @Test
    @DisplayName("A tenant token holding SUPER_ADMIN that sends the header still gets 403")
    void tenantTokenWithSuperAdminRoleAndHeader_returns403() throws Exception {
        authenticate(jwtAuthentication(Map.of("tenant_id", TENANT_A), Role.SUPER_ADMIN));
        request.addHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID, TENANT_B);

        assertChainNotCalled();
        assertErrorResponse(403, ApiTenantContextFilter.HEADER_NOT_ALLOWED_MESSAGE);
        verifyNoInteractions(tenantRepository, auditEventPublisher);
    }

    @Test
    @DisplayName("An INTERNAL tenant user (User principal) that sends the header gets 403, even for its own tenant")
    void tenantUserWithHeader_returns403() throws Exception {
        authenticate(userAuthentication(Role.ADMIN, TENANT_A));
        request.addHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID, TENANT_A);

        assertChainNotCalled();
        assertErrorResponse(403, ApiTenantContextFilter.HEADER_NOT_ALLOWED_MESSAGE);
        verifyNoInteractions(tenantRepository, auditEventPublisher);
    }

    @Test
    @DisplayName("A principal without a tenant and without SUPER_ADMIN that sends the header gets 403")
    void adminWithoutTenantWithHeader_returns403() throws Exception {
        authenticate(jwtAuthentication(Map.of(), Role.ADMIN));
        request.addHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID, TENANT_A);

        assertChainNotCalled();
        assertErrorResponse(403, ApiTenantContextFilter.HEADER_NOT_ALLOWED_MESSAGE);
        verifyNoInteractions(tenantRepository, auditEventPublisher);
    }

    @Test
    @DisplayName("A connector (platform) token gets no tenant, even with the header, and the chain still runs")
    void connectorToken_ignoresHeader() throws Exception {
        authenticate(jwtAuthentication(Map.of(), Role.CONNECTOR));
        request.addHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID, TENANT_A);

        assertNull(runAndCaptureTenant(), "A connector principal must not get a tenant from the header");
        assertEquals(200, response.getStatus());
        verifyNoInteractions(tenantRepository, auditEventPublisher);
    }

    @Test
    @DisplayName("Without authentication no tenant is set and the chain runs")
    void noAuthentication_setsNoTenant() throws Exception {
        request.addHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID, TENANT_A);

        assertNull(runAndCaptureTenant());
        verifyNoInteractions(tenantRepository, auditEventPublisher);
    }

    // ---------------------------------------------------------------------
    // Context clearing
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("The tenant context is cleared after a successful request")
    void contextClearedAfterSuccess() throws Exception {
        authenticate(userAuthentication(Role.ADMIN, TENANT_A));

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    @DisplayName("The tenant context is cleared after a delegation")
    void contextClearedAfterDelegation() throws Exception {
        authenticate(jwtAuthentication(Map.of(), Role.SUPER_ADMIN));
        request.addHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID, TENANT_B);
        when(tenantRepository.findById(TENANT_B)).thenReturn(Optional.of(tenant(TENANT_B, true)));

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    @DisplayName("The tenant context is cleared when the chain throws")
    void contextClearedWhenChainThrows() {
        authenticate(userAuthentication(Role.ADMIN, TENANT_A));
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, failing));
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    @DisplayName("A stale tenant context is cleared after a rejected request")
    void contextClearedAfterRejection() throws Exception {
        TenantContextHolder.setTenantId("stale");
        authenticate(userAuthentication(Role.ADMIN, TENANT_A));
        request.addHeader(ApiTenantContextFilter.HEADER_X_TENANT_ID, TENANT_B);

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        assertNull(TenantContextHolder.getTenantId());
    }

    // ---------------------------------------------------------------------
    // Path matching
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("shouldNotFilter returns false for /api/ paths")
    void shouldNotFilter_apiPath_returnsFalse() {
        assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/tenants")));
    }

    @Test
    @DisplayName("shouldNotFilter returns true for non-/api/ paths")
    void shouldNotFilter_nonApiPath_returnsTrue() {
        assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/catalog/datasets")));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private String runAndCaptureTenant() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>("not-called");
        filter.doFilter(request, response, (req, res) -> captured.set(TenantContextHolder.getTenantId()));
        assertFalse("not-called".equals(captured.get()), "The filter chain must be called");
        assertNull(TenantContextHolder.getTenantId(), "Tenant context must be cleared after the filter");
        return captured.get();
    }

    private void assertChainNotCalled() throws Exception {
        AtomicBoolean called = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertFalse(called.get(), "The filter chain must not be called");
        assertNull(TenantContextHolder.getTenantId(), "Tenant context must be cleared after the filter");
    }

    private void assertErrorResponse(int status, String message) throws Exception {
        assertEquals(status, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        JsonNode body = OBJECT_MAPPER.readTree(response.getContentAsString());
        assertFalse(body.get("success").asBoolean());
        assertEquals(message, body.get("message").asText());
        assertTrue(body.hasNonNull("timestamp"));
    }

    private static void authenticate(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Authentication userAuthentication(Role role, String tenantId) {
        User user = User.builder()
                .id("user-1")
                .email("user@mail.com")
                .role(role)
                .tenantId(tenantId)
                .enabled(true)
                .build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private static Authentication jwtAuthentication(Map<String, Object> claims, Role role) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("http://kc/realms/some-realm")
                .subject("subject-1");
        claims.forEach(builder::claim);
        Object email = claims.get("email");
        return new JwtAuthenticationToken(builder.build(),
                List.of(new SimpleGrantedAuthority(role.authorityName())),
                email != null ? email.toString() : "subject-1");
    }

    private static Tenant tenant(String id, boolean enabled) {
        return Tenant.Builder.newInstance()
                .id(id)
                .name(id)
                .participantId("urn:participant:" + id)
                .enabled(enabled)
                .build();
    }
}
