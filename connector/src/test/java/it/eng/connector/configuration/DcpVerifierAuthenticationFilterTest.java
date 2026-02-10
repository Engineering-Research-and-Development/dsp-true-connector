package it.eng.connector.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DcpVerifierAuthenticationFilterV2.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DcpVerifierAuthenticationFilterV2 Tests")
class DcpVerifierAuthenticationFilterTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private DcpVerifierAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new DcpVerifierAuthenticationFilter(authenticationManager);
        lenient().when(request.getRequestURI()).thenReturn("/catalog/datasets");
    }

    @Test
    @DisplayName("Should skip when no Authorization header present")
    void shouldSkipWhenNoAuthHeader() throws Exception {
        // Given: No Authorization header
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        // When: Filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: AuthenticationManager never called
        verify(authenticationManager, never()).authenticate(any());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should skip when Authorization is not Bearer")
    void shouldSkipWhenNotBearer() throws Exception {
        // Given: Basic auth header
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic dXNlcjpwYXNz");

        // When: Filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: AuthenticationManager never called
        verify(authenticationManager, never()).authenticate(any());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should create DcpBearerToken and delegate to AuthenticationManager")
    void shouldDelegateToAuthenticationManager() throws Exception {
        // Given: Valid Bearer token
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer test-token");

        UsernamePasswordAuthenticationToken authenticatedToken = new UsernamePasswordAuthenticationToken(
                "did:example:holder",
                "test-token",
                List.of(new SimpleGrantedAuthority("ROLE_CONNECTOR"))
        );
        when(authenticationManager.authenticate(any(DcpBearerToken.class))).thenReturn(authenticatedToken);

        // When: Filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: AuthenticationManager called with DcpBearerToken
        ArgumentCaptor<DcpBearerToken> tokenCaptor = ArgumentCaptor.forClass(DcpBearerToken.class);
        verify(authenticationManager, times(1)).authenticate(tokenCaptor.capture());

        DcpBearerToken capturedToken = tokenCaptor.getValue();
        assertEquals("test-token", capturedToken.getToken());
        assertFalse(capturedToken.isAuthenticated());

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should set SecurityContext when authentication succeeds")
    void shouldSetSecurityContextOnSuccess() throws Exception {
        // Given: Valid Bearer token and successful authentication
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");

        UsernamePasswordAuthenticationToken authenticatedToken = new UsernamePasswordAuthenticationToken(
                "did:example:holder",
                "valid-token",
                List.of(new SimpleGrantedAuthority("ROLE_CONNECTOR"))
        );
        when(authenticationManager.authenticate(any(DcpBearerToken.class))).thenReturn(authenticatedToken);

        // When: Filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: SecurityContext is set
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.isAuthenticated());
        assertEquals("did:example:holder", auth.getPrincipal());

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should continue to next filter when authentication returns null")
    void shouldContinueWhenAuthenticationReturnsNull() throws Exception {
        // Given: Bearer token but authentication returns null (provider disabled or failed)
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(authenticationManager.authenticate(any(DcpBearerToken.class))).thenReturn(null);

        // When: Filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: SecurityContext remains empty, continues to next filter
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "SecurityContext should be empty to allow fallback");

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should continue to next filter when authentication throws exception")
    void shouldContinueWhenAuthenticationThrowsException() throws Exception {
        // Given: Bearer token but authentication throws exception
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer invalid-token");
        when(authenticationManager.authenticate(any(DcpBearerToken.class)))
                .thenThrow(new RuntimeException("Authentication failed"));

        // When: Filter processes request
        filter.doFilterInternal(request, response, filterChain);

        // Then: SecurityContext cleared, continues to next filter
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "SecurityContext should be empty after exception");

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should not filter /dcp/** endpoints")
    void shouldNotFilterDcpEndpoints() throws Exception {
        // Given: Request to /dcp endpoint
        when(request.getRequestURI()).thenReturn("/dcp/holder/presentations");

        // When: Check shouldNotFilter
        boolean result = filter.shouldNotFilter(request);

        // Then: Should not filter
        assertTrue(result, "Should not filter /dcp/** endpoints");
    }

    @Test
    @DisplayName("Should filter /catalog/** endpoints")
    void shouldFilterCatalogEndpoints() throws Exception {
        // Given: Request to /catalog endpoint
        when(request.getRequestURI()).thenReturn("/catalog/datasets");

        // When: Check shouldNotFilter
        boolean result = filter.shouldNotFilter(request);

        // Then: Should filter
        assertFalse(result, "Should filter /catalog/** endpoints");
    }

    @Test
    @DisplayName("Should filter /negotiation/** endpoints")
    void shouldFilterNegotiationEndpoints() throws Exception {
        // Given: Request to /negotiation endpoint
        when(request.getRequestURI()).thenReturn("/negotiation/contracts");

        // When: Check shouldNotFilter
        boolean result = filter.shouldNotFilter(request);

        // Then: Should filter
        assertFalse(result, "Should filter /negotiation/** endpoints");
    }

    @Test
    @DisplayName("Should filter /transfers/** endpoints")
    void shouldFilterTransfersEndpoints() throws Exception {
        // Given: Request to /transfers endpoint
        when(request.getRequestURI()).thenReturn("/transfers/data");

        // When: Check shouldNotFilter
        boolean result = filter.shouldNotFilter(request);

        // Then: Should filter
        assertFalse(result, "Should filter /transfers/** endpoints");
    }

    @Test
    @DisplayName("Should not filter null request URI")
    void shouldNotFilterNullURI() throws Exception {
        // Given: Request with null URI
        when(request.getRequestURI()).thenReturn(null);

        // When: Check shouldNotFilter
        boolean result = filter.shouldNotFilter(request);

        // Then: Should not filter
        assertTrue(result, "Should not filter null URI");
    }

    @Test
    @DisplayName("Should not filter /api/** endpoints")
    void shouldNotFilterApiEndpoints() throws Exception {
        // Given: Request to /api endpoint
        when(request.getRequestURI()).thenReturn("/api/admin/config");

        // When: Check shouldNotFilter
        boolean result = filter.shouldNotFilter(request);

        // Then: Should not filter
        assertTrue(result, "Should not filter /api/** endpoints");
    }
}
