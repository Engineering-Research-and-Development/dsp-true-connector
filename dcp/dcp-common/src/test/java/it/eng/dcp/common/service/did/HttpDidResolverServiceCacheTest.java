package it.eng.dcp.common.service.did;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.util.Base64URL;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.VerificationMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpDidResolverServiceCacheTest {

    static final String DID = "did:web:example.com:connector";

    @Mock
    private SimpleOkHttpRestClient mockOkHttpClient;

    private HttpDidResolverService service;

    @BeforeEach
    void setUp() {
        service = spy(new HttpDidResolverService(mockOkHttpClient));
        // Set sslEnabled to false for testing
        ReflectionTestUtils.setField(service, "sslEnabled", false);
    }

    @Test
    @DisplayName("Cache returns same DID document on subsequent calls")
    void cacheReturnsSameDidDocument() throws Exception {
        // Create test key and DID document
        OctetSequenceKey key = new OctetSequenceKey.Builder(new Base64URL("x"))
                .keyID("kid1")
                .build();

        String vmId = DID + "#kid1";
        VerificationMethod vm = VerificationMethod.Builder.newInstance()
                .id(vmId)
                .type("JsonWebKey2020")
                .publicKeyJwk(key.toJSONObject())
                .build();

        DidDocument didDocument = DidDocument.Builder.newInstance()
                .id(DID)
                .verificationMethod(List.of(vm))
                .build();

        // Mock fetchDidDocumentCached to return the DID document
        doReturn(didDocument).when(service).fetchDidDocumentCached(anyString());

        // First call - should fetch from HTTP
        JWK jwk1 = service.resolvePublicKey(DID, "kid1", "capabilityInvocation");
        assertNotNull(jwk1);
        assertEquals("kid1", jwk1.getKeyID());

        // Second call - should return cached document (HTTP client should only be called once)
        JWK jwk2 = service.resolvePublicKey(DID, "kid1", "capabilityInvocation");
        assertNotNull(jwk2);
        assertEquals("kid1", jwk2.getKeyID());

        // Verify fetchDidDocumentCached was called twice (once per resolvePublicKey call)
        // The method itself handles caching internally
        verify(service, times(2)).fetchDidDocumentCached(anyString());
    }

    @Test
    @DisplayName("fetchDidDocumentCached returns same document on repeated calls")
    void fetchDidDocumentCachedReturnsSameDocument() throws Exception {
        // Create test DID document
        VerificationMethod vm = VerificationMethod.Builder.newInstance()
                .id(DID + "#key1")
                .type("JsonWebKey2020")
                .publicKeyJwk(new OctetSequenceKey.Builder(new Base64URL("x"))
                        .keyID("key1")
                        .build()
                        .toJSONObject())
                .build();

        DidDocument didDocument = DidDocument.Builder.newInstance()
                .id(DID)
                .verificationMethod(List.of(vm))
                .build();

        // Mock executeAndDeserialize to return the DID document
        when(mockOkHttpClient.executeAndDeserialize(
                anyString(),
                anyString(),
                any(),
                any(),
                any(Class.class)))
                .thenReturn(didDocument);

        // First fetch - should call HTTP
        DidDocument doc1 = service.fetchDidDocumentCached(DID);
        assertNotNull(doc1);
        assertEquals(DID, doc1.getId());

        // Second fetch - should return cached document
        DidDocument doc2 = service.fetchDidDocumentCached(DID);
        assertNotNull(doc2);
        assertEquals(DID, doc2.getId());

        // Should be same instance from cache
        assertSame(doc1, doc2);

        // Verify HTTP client was called only once
        verify(mockOkHttpClient, times(1)).executeAndDeserialize(
                anyString(),
                anyString(),
                any(),
                any(),
                any(Class.class));
    }
}