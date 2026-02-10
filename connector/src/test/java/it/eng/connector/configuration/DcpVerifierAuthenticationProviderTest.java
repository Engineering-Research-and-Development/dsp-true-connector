package it.eng.connector.configuration;

import it.eng.dcp.verifier.service.VerifierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DcpVerifierAuthenticationProvider.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DcpVerifierAuthenticationProvider Tests")
class DcpVerifierAuthenticationProviderTest {

    @Mock
    private VerifierService verifierService;

    private DcpVerifierAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DcpVerifierAuthenticationProvider(verifierService);
    }

    @Test
    @DisplayName("Should return null when dcp.vp.enabled=false")
    void shouldReturnNullWhenDisabled() throws IOException {
        // Given: Provider with vcVpEnabled=false
        ReflectionTestUtils.setField(provider, "vcVpEnabled", false);
        DcpBearerToken authRequest = new DcpBearerToken("test-token");

        // When: Provider authenticates
        Authentication result = provider.authenticate(authRequest);

        // Then: Returns null to allow fallback
        assertNull(result, "Should return null when disabled");
        verify(verifierService, never()).validateAndQueryHolderPresentations(anyString());
    }

    @Test
    @DisplayName("Should authenticate valid token successfully")
    void shouldAuthenticateValidToken() throws Exception {
        // Given: Provider with vcVpEnabled=true and valid token
        ReflectionTestUtils.setField(provider, "vcVpEnabled", true);
        DcpBearerToken authRequest = new DcpBearerToken("valid-token");

        VerifierService.PresentationFlowResult mockResult = mock(VerifierService.PresentationFlowResult.class);
        when(mockResult.getHolderDid()).thenReturn("did:example:holder123");
        when(mockResult.getScopes()).thenReturn(List.of("org.eclipse.edc.vc.type:MembershipCredential:read"));
        when(mockResult.getValidatedPresentations()).thenReturn(List.of());
        when(verifierService.validateAndQueryHolderPresentations("valid-token")).thenReturn(mockResult);

        // When: Provider authenticates
        Authentication result = provider.authenticate(authRequest);

        // Then: Returns authenticated token
        assertNotNull(result, "Should return authenticated token");
        assertTrue(result.isAuthenticated(), "Token should be authenticated");
        assertEquals("did:example:holder123", result.getPrincipal(), "Principal should be holder DID");
        assertEquals("valid-token", result.getCredentials(), "Credentials should be the token");
        assertTrue(result.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_CONNECTOR")),
                "Should have ROLE_CONNECTOR authority");

        verify(verifierService, times(1)).validateAndQueryHolderPresentations("valid-token");
    }

    @Test
    @DisplayName("Should return null when validation fails with SecurityException")
    void shouldReturnNullOnSecurityException() throws Exception {
        // Given: Provider enabled but validation fails
        ReflectionTestUtils.setField(provider, "vcVpEnabled", true);
        DcpBearerToken authRequest = new DcpBearerToken("invalid-token");

        when(verifierService.validateAndQueryHolderPresentations("invalid-token"))
                .thenThrow(new SecurityException("Invalid token"));

        // When: Provider authenticates
        Authentication result = provider.authenticate(authRequest);

        // Then: Returns null to allow fallback
        assertNull(result, "Should return null on validation failure");
        verify(verifierService, times(1)).validateAndQueryHolderPresentations("invalid-token");
    }

    @Test
    @DisplayName("Should return null when validation fails with IOException")
    void shouldReturnNullOnIOException() throws Exception {
        // Given: Provider enabled but network error occurs
        ReflectionTestUtils.setField(provider, "vcVpEnabled", true);
        DcpBearerToken authRequest = new DcpBearerToken("network-error-token");

        when(verifierService.validateAndQueryHolderPresentations("network-error-token"))
                .thenThrow(new IOException("Network error"));

        // When: Provider authenticates
        Authentication result = provider.authenticate(authRequest);

        // Then: Returns null to allow fallback
        assertNull(result, "Should return null on network error");
        verify(verifierService, times(1)).validateAndQueryHolderPresentations("network-error-token");
    }

    @Test
    @DisplayName("Should return null when validation fails with generic Exception")
    void shouldReturnNullOnGenericException() throws Exception {
        // Given: Provider enabled but unexpected error occurs
        ReflectionTestUtils.setField(provider, "vcVpEnabled", true);
        DcpBearerToken authRequest = new DcpBearerToken("error-token");

        when(verifierService.validateAndQueryHolderPresentations("error-token"))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When: Provider authenticates
        Authentication result = provider.authenticate(authRequest);

        // Then: Returns null to allow fallback
        assertNull(result, "Should return null on unexpected error");
        verify(verifierService, times(1)).validateAndQueryHolderPresentations("error-token");
    }

    @Test
    @DisplayName("Should support DcpBearerToken class")
    void shouldSupportDcpBearerToken() {
        // When: Check if provider supports DcpBearerToken
        boolean supports = provider.supports(DcpBearerToken.class);

        // Then: Should support it
        assertTrue(supports, "Provider should support DcpBearerToken");
    }

    @Test
    @DisplayName("Should not support other authentication types")
    void shouldNotSupportOtherAuthTypes() {
        // When: Check if provider supports other types
        boolean supports = provider.supports(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.class);

        // Then: Should not support it
        assertFalse(supports, "Provider should not support UsernamePasswordAuthenticationToken");
    }

    @Test
    @DisplayName("Should handle null token gracefully")
    void shouldHandleNullToken() throws Exception {
        // Given: Provider enabled with null token
        ReflectionTestUtils.setField(provider, "vcVpEnabled", true);
        DcpBearerToken authRequest = new DcpBearerToken(null);

        when(verifierService.validateAndQueryHolderPresentations(null))
                .thenThrow(new IllegalArgumentException("Token cannot be null"));

        // When: Provider authenticates
        Authentication result = provider.authenticate(authRequest);

        // Then: Returns null to allow fallback
        assertNull(result, "Should return null when token is null");
    }

    @Test
    @DisplayName("Should handle empty token gracefully")
    void shouldHandleEmptyToken() throws Exception {
        // Given: Provider enabled with empty token
        ReflectionTestUtils.setField(provider, "vcVpEnabled", true);
        DcpBearerToken authRequest = new DcpBearerToken("");

        when(verifierService.validateAndQueryHolderPresentations(""))
                .thenThrow(new IllegalArgumentException("Token cannot be empty"));

        // When: Provider authenticates
        Authentication result = provider.authenticate(authRequest);

        // Then: Returns null to allow fallback
        assertNull(result, "Should return null when token is empty");
    }
}
