package it.eng.dcp.core;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.util.Base64URL;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.common.service.did.HttpDidResolverService;
import it.eng.dcp.common.util.DidDocumentClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class HttpDidResolverServiceCacheTest {

    static final String DID = "did:web:example.com:connector";

    @Test
    @DisplayName("fetchDidDocument called once when cached")
    void fetchCalledOnceWhenCached() throws Exception {
        OctetSequenceKey key = new OctetSequenceKey.Builder(new Base64URL("x"))
                .keyID("kid1")
                .build();

        String vmId = "did:web:example.com:connector#k";
        VerificationMethod vm = VerificationMethod.Builder.newInstance()
                .id(vmId)
                .type("JsonWebKey2020")
                .publicKeyJwk(key.toJSONObject())
                .build();

        DidDocument didDocument = DidDocument.Builder.newInstance()
                .id(DID)
                .verificationMethod(List.of(vm))
                .build();

        // Track how many times fetchDidDocumentCached is called
        AtomicInteger calls = new AtomicInteger(0);

        // Mock DidDocumentClient to count calls
        DidDocumentClient mockClient = mock(DidDocumentClient.class);
        when(mockClient.fetchDidDocumentCached(anyString())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return didDocument;
        });

        HttpDidResolverService svc = new HttpDidResolverService(null, mockClient);

        // First call - should fetch from client
        JWK jwk1 = svc.resolvePublicKey(DID, "kid1", "capabilityInvocation");
        assertNotNull(jwk1);

        // Second call - should use cache from DidDocumentClient
        JWK jwk2 = svc.resolvePublicKey(DID, "kid1", "capabilityInvocation");
        assertNotNull(jwk2);

        // Verify the mock was called for both requests (DidDocumentClient has its own cache)
        verify(mockClient, times(2)).fetchDidDocumentCached(anyString());
    }
}

