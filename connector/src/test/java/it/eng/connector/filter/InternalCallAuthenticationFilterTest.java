package it.eng.connector.filter;

import it.eng.connector.model.Role;
import it.eng.tools.auth.internal.InternalCallClaims;
import it.eng.tools.auth.internal.InternalCallTokenIssuer;
import it.eng.tools.controller.ApiEndpoints;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InternalCallAuthenticationFilterTest {

    private final InternalCallTokenIssuer issuer = new InternalCallTokenIssuer();
    private final InternalCallAuthenticationFilter filter = new InternalCallAuthenticationFilter(issuer);

    private Authentication authenticationSeenDownstream;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        authenticationSeenDownstream = null;
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                authenticationSeenDownstream = SecurityContextHolder.getContext().getAuthentication();
            }
        });
        return response;
    }

    private static MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ApiEndpoints.INTERNAL_DATASETS_V1 + "/1/formats");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request;
    }

    @Test
    @DisplayName("A valid internal-call token authenticates with ROLE_INTERNAL and the verified claims")
    void validTokenAuthenticates() throws Exception {
        MockHttpServletResponse response = run(request(issuer.authorizationHeader("tenant-a")));

        assertEquals(200, response.getStatus());
        assertNotNull(authenticationSeenDownstream);
        assertTrue(authenticationSeenDownstream.isAuthenticated());
        assertEquals(InternalCallTokenIssuer.SUBJECT, authenticationSeenDownstream.getName());
        assertTrue(authenticationSeenDownstream.getAuthorities().stream()
                .anyMatch(authority -> Role.INTERNAL.authorityName().equals(authority.getAuthority())));
        InternalCallClaims claims = (InternalCallClaims) authenticationSeenDownstream.getDetails();
        assertEquals(Optional.of("tenant-a"), claims.tenantId());
        assertNull(authenticationSeenDownstream.getCredentials());
    }

    @Test
    @DisplayName("The token value is not kept in the authentication")
    void tokenNotKept() throws Exception {
        String header = issuer.authorizationHeader("tenant-a");

        run(request(header));

        String token = header.substring("Bearer ".length());
        assertFalse(authenticationSeenDownstream.toString().contains(token));
    }

    @Test
    @DisplayName("A request without a token continues with an empty context")
    void noTokenLeavesContextEmpty() throws Exception {
        MockHttpServletResponse response = run(request(null));

        assertEquals(200, response.getStatus());
        assertNull(authenticationSeenDownstream);
    }

    @Test
    @DisplayName("A token from another key or a non-bearer header leaves the context empty")
    void foreignTokenLeavesContextEmpty() throws Exception {
        run(request(new InternalCallTokenIssuer().authorizationHeader(null)));
        assertNull(authenticationSeenDownstream);

        run(request("Basic YWRtaW46cGFzc3dvcmQ="));
        assertNull(authenticationSeenDownstream);
    }

    @Test
    @DisplayName("An invalid token clears an authentication set before the filter")
    void invalidTokenClearsExistingAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "someone", null, List.of()));

        run(request("Bearer not-a-token"));

        assertNull(authenticationSeenDownstream);
    }
}
