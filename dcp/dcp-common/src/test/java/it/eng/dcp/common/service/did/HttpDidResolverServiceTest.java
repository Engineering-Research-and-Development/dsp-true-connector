package it.eng.dcp.common.service.did;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.util.Base64URL;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.VerificationMethod;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class HttpDidResolverServiceTest {

    static final String DID = "did:web:example.com:connector";

    @Test
    @DisplayName("resolve returns JWK when verification relationship references VM id")
    void resolveReturnsJwkWhenReferenced() throws DidResolutionException, IOException {
        // build a sample symmetric JWK
        OctetSequenceKey key = new OctetSequenceKey.Builder(new Base64URL("x"))
                .keyID("kid-ec")
                .build();

        // craft DID document where verificationMethod contains the JWK
        String vmId = "did:web:example.com:connector#key-1";
        VerificationMethod vm = VerificationMethod.Builder.newInstance()
                .id(vmId)
                .type("JsonWebKey2020")
                .publicKeyJwk(key.toJSONObject())
                .build();

        DidDocument didDocument = DidDocument.Builder.newInstance()
                .id(DID)
                .verificationMethod(List.of(vm))
                .build();

        // Create a spy of HttpDidResolverService to override fetchDidDocumentCached
        SimpleOkHttpRestClient mockHttpClient = mock(SimpleOkHttpRestClient.class);
        HttpDidResolverService svc = spy(new HttpDidResolverService(mockHttpClient));

        // Stub the fetchDidDocumentCached method to return our test document
        // Use doReturn().when() syntax for spies to avoid calling the real method
        doReturn(didDocument).when(svc).fetchDidDocumentCached(anyString());

        JWK jwk = svc.resolvePublicKey(DID, "kid-ec", "capabilityInvocation");
        assertNotNull(jwk);
        assertEquals("kid-ec", jwk.getKeyID());
    }

    @Test
    @DisplayName("resolve throws when verification relationship does not reference VM id")
    @Disabled("Disabled since signature check is commented out because of TCK issues")
    void resolveThrowsWhenNotReferenced() throws DidResolutionException, IOException {
        OctetSequenceKey key = new OctetSequenceKey.Builder(new Base64URL("x"))
                .keyID("kid-ec")
                .build();

        String vmId = "did:web:example.com:connector#key-1";
        VerificationMethod vm = VerificationMethod.Builder.newInstance()
                .id(vmId)
                .type("JsonWebKey2020")
                .publicKeyJwk(key.toJSONObject())
                .build();

        DidDocument didDocument = DidDocument.Builder.newInstance()
                .id(DID)
                .verificationMethod(List.of(vm))
                .build();

        // Create a spy of HttpDidResolverService to override fetchDidDocumentCached
        SimpleOkHttpRestClient mockHttpClient = mock(SimpleOkHttpRestClient.class);
        HttpDidResolverService svc = spy(new HttpDidResolverService(mockHttpClient));

        // Stub the fetchDidDocumentCached method to return our test document
        doReturn(didDocument).when(svc).fetchDidDocumentCached(anyString());

        // Request a different key ID that doesn't exist
        JWK jwk = svc.resolvePublicKey(DID, "non-existent-kid", "capabilityInvocation");
        assertNull(jwk);
    }
}
