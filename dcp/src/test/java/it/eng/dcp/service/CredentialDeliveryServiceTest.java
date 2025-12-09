package it.eng.dcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.core.DidResolverService;
import it.eng.dcp.repository.CredentialRequestRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for CredentialDeliveryService, particularly DID resolution logic.
 */
@ExtendWith(MockitoExtension.class)
class CredentialDeliveryServiceTest {

    @Mock
    private CredentialRequestRepository requestRepository;

    @Mock
    private DidResolverService didResolverService;

    @Mock
    private SelfIssuedIdTokenService tokenService;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private OkHttpRestClient httpClient;

    @InjectMocks
    private CredentialDeliveryService service;

    @Test
    void testResolveCredentialServiceEndpoint_WithEncodedPort() throws Exception {
        // Test did:web:localhost%3A8080 -> https://localhost:8080/dcp/credentials
        String result = invokeResolveMethod("did:web:localhost%3A8080");
        assertEquals("https://localhost:8080/dcp/credentials", result);
    }

    @Test
    void testResolveCredentialServiceEndpoint_WithColonPort() throws Exception {
        // Test did:web:localhost:8080 -> https://localhost:8080/dcp/credentials
        String result = invokeResolveMethod("did:web:localhost:8080");
        assertEquals("https://localhost:8080/dcp/credentials", result);
    }

    @Test
    void testResolveCredentialServiceEndpoint_WithEncodedPortAndPath() throws Exception {
        // Test did:web:localhost%3A8080:holder -> https://localhost:8080/dcp/credentials
        String result = invokeResolveMethod("did:web:localhost%3A8080:holder");
        assertEquals("https://localhost:8080/dcp/credentials", result);
    }

    @Test
    void testResolveCredentialServiceEndpoint_WithColonPortAndPath() throws Exception {
        // Test did:web:localhost:8080:holder -> https://localhost:8080/dcp/credentials
        String result = invokeResolveMethod("did:web:localhost:8080:holder");
        assertEquals("https://localhost:8080/dcp/credentials", result);
    }

    @Test
    void testResolveCredentialServiceEndpoint_WithoutPort() throws Exception {
        // Test did:web:localhost -> https://localhost/dcp/credentials
        String result = invokeResolveMethod("did:web:localhost");
        assertEquals("https://localhost/dcp/credentials", result);
    }

    @Test
    void testResolveCredentialServiceEndpoint_ExternalDomain() throws Exception {
        // Test did:web:example.com -> https://example.com/dcp/credentials
        String result = invokeResolveMethod("did:web:example.com");
        assertEquals("https://example.com/dcp/credentials", result);
    }

    @Test
    void testResolveCredentialServiceEndpoint_ExternalDomainWithPort() throws Exception {
        // Test did:web:example.com%3A443 -> https://example.com:443/dcp/credentials
        String result = invokeResolveMethod("did:web:example.com%3A443");
        assertEquals("https://example.com:443/dcp/credentials", result);
    }

    @Test
    void testResolveCredentialServiceEndpoint_InvalidDid() throws Exception {
        // Test invalid DID format
        String result = invokeResolveMethod("did:key:12345");
        assertNull(result);
    }

    /**
     * Helper method to invoke the private resolveCredentialServiceEndpoint method via reflection
     */
    private String invokeResolveMethod(String holderDid) throws Exception {
        Method method = CredentialDeliveryService.class.getDeclaredMethod("resolveCredentialServiceEndpoint", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, holderDid);
    }
}

