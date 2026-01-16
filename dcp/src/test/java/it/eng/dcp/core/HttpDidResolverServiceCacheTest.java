package it.eng.dcp.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.util.Base64URL;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.common.service.did.HttpDidResolverService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpDidResolverServiceCacheTest {

    static final String DID = "did:web:example.com:connector";

    @Mock
    private OkHttpClient mockOkHttpClient;

    private HttpDidResolverService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SimpleOkHttpRestClient restClient = new SimpleOkHttpRestClient(mockOkHttpClient);
        service = new HttpDidResolverService(restClient);
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

        String didDocJson = mapper.writeValueAsString(didDocument);

        // Mock HTTP response
        okhttp3.Call mockCall = mock(okhttp3.Call.class);
        Response mockResponse = mock(Response.class);
        ResponseBody mockBody = mock(ResponseBody.class);
        lenient().when(mockResponse.code()).thenReturn(200);
        lenient().when(mockResponse.isSuccessful()).thenReturn(true);
        when(mockResponse.body()).thenReturn(mockBody);
        when(mockBody.string()).thenReturn(didDocJson);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockOkHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        // First call - should fetch from HTTP
        JWK jwk1 = service.resolvePublicKey(DID, "kid1", "capabilityInvocation");
        assertNotNull(jwk1);
        assertEquals("kid1", jwk1.getKeyID());

        // Second call - should return cached document (HTTP client should only be called once)
        JWK jwk2 = service.resolvePublicKey(DID, "kid1", "capabilityInvocation");
        assertNotNull(jwk2);
        assertEquals("kid1", jwk2.getKeyID());

        // Verify HTTP client was called only once (due to caching)
        verify(mockOkHttpClient, times(1)).newCall(any(Request.class));
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

        String didDocJson = mapper.writeValueAsString(didDocument);

        // Mock HTTP response
        okhttp3.Call mockCall = mock(okhttp3.Call.class);
        Response mockResponse = mock(Response.class);
        ResponseBody mockBody = mock(ResponseBody.class);
        lenient().when(mockResponse.code()).thenReturn(200);
        lenient().when(mockResponse.isSuccessful()).thenReturn(true);
        when(mockResponse.body()).thenReturn(mockBody);
        when(mockBody.string()).thenReturn(didDocJson);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockOkHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

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
        verify(mockOkHttpClient, times(1)).newCall(any(Request.class));
    }
}

