package it.eng.connector.configuration;

import it.eng.tools.auth.jwt.JwtProperties;
import it.eng.tools.auth.jwt.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InternalJwtAuthenticationFilter}.
 */
class InternalJwtAuthenticationFilterTest {

    private static final String SECRET = "unit-test-secret-value-with-at-least-256-bits!!";

    private JwtService jwtService;
    private InternalJwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(SECRET);
        jwtProperties.setAccessExpirationMs(900_000L);
        jwtProperties.setRefreshExpirationMs(604_800_000L);
        jwtService = new JwtService(jwtProperties);
        jwtService.init();
        filter = new InternalJwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should authenticate an email-less protocol token with ROLE_CONNECTOR and principal name = sub")
    void authenticatesProtocolTokenWithSubAsPrincipalName() throws Exception {
        Authentication authentication = filterWithBearer(jwtService.issueProtocolToken());

        assertNotNull(authentication);
        assertInstanceOf(JwtAuthenticationToken.class, authentication);
        assertEquals(JwtService.PROTOCOL_TOKEN_SUBJECT, authentication.getName());
        assertEquals(Set.of("ROLE_CONNECTOR"), authorityNames(authentication));
    }

    @Test
    @DisplayName("Should keep the email as principal name for a login token")
    void keepsEmailAsPrincipalNameForLoginToken() throws Exception {
        String accessToken = jwtService.issueTokenPair("user-1", "user@example.com", List.of("ADMIN"),
                "tenant-a", Map.of()).accessToken();

        Authentication authentication = filterWithBearer(accessToken);

        assertNotNull(authentication);
        assertEquals("user@example.com", authentication.getName());
        assertEquals(Set.of("ROLE_ADMIN"), authorityNames(authentication));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "admin, ROLE_ADMIN",
            "ADMIN, ROLE_ADMIN",
            "Admin, ROLE_ADMIN",
            "ROLE_admin, ROLE_ADMIN",
            "super_admin, ROLE_SUPER_ADMIN",
            "SUPER_ADMIN, ROLE_SUPER_ADMIN",
            "Super_Admin, ROLE_SUPER_ADMIN",
            "connector, ROLE_CONNECTOR",
            "CONNECTOR, ROLE_CONNECTOR",
            "Connector, ROLE_CONNECTOR"
    })
    @DisplayName("Should map INTERNAL role names to authorities exactly as before, in any case")
    void mapsInternalRolesInAnyCase(String role, String expectedAuthority) throws Exception {
        String accessToken = jwtService.issueTokenPair("user-1", "user@example.com", List.of(role),
                null, Map.of()).accessToken();

        Authentication authentication = filterWithBearer(accessToken);

        assertNotNull(authentication);
        assertEquals(Set.of(expectedAuthority), authorityNames(authentication));
    }

    @Test
    @DisplayName("Should map several INTERNAL roles together")
    void mapsSeveralInternalRoles() throws Exception {
        String accessToken = jwtService.issueTokenPair("user-1", "user@example.com",
                List.of("admin", "super_admin", "connector"), null, Map.of()).accessToken();

        Authentication authentication = filterWithBearer(accessToken);

        assertNotNull(authentication);
        assertEquals(Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_CONNECTOR"), authorityNames(authentication));
    }

    @Test
    @DisplayName("Should expose the tenant under the tenant_id claim only")
    void exposesTenantUnderTenantIdClaim() throws Exception {
        String accessToken = jwtService.issueTokenPair("user-1", "user@example.com", List.of("ADMIN"),
                "tenant-a", Map.of()).accessToken();

        JwtAuthenticationToken authentication = assertInstanceOf(JwtAuthenticationToken.class,
                filterWithBearer(accessToken));

        assertEquals("tenant-a", authentication.getToken().getClaimAsString(JwtService.TENANT_ID_CLAIM));
        assertFalse(authentication.getToken().hasClaim("tenantId"));
    }

    @Test
    @DisplayName("Should not authenticate a refresh token")
    void rejectsRefreshToken() throws Exception {
        String refreshToken = jwtService.issueTokenPair("user-1", "user@example.com", List.of("ADMIN"),
                "tenant-a", Map.of()).refreshToken();

        assertNull(filterWithBearer(refreshToken));
    }

    private Authentication filterWithBearer(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/catalog/request");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static Set<String> authorityNames(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }
}
