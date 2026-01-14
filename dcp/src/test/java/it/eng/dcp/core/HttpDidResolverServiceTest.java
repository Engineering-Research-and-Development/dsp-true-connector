package it.eng.dcp.core;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.util.Base64URL;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.common.service.did.HttpDidResolverService;
import it.eng.dcp.common.util.DidDocumentClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        // Mock DidDocumentClient
        DidDocumentClient mockClient = mock(DidDocumentClient.class);
        when(mockClient.fetchDidDocumentCached(anyString())).thenReturn(didDocument);

        HttpDidResolverService svc = new HttpDidResolverService(null, mockClient);

        JWK jwk = svc.resolvePublicKey(DID, "kid-ec", "capabilityInvocation");
        assertNotNull(jwk);
        assertEquals("kid-ec", jwk.getKeyID());
    }

    @Test
    @DisplayName("resolve returns null when verification method not found")
    void resolveReturnsNullWhenNotFound() throws DidResolutionException, IOException {
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

        // Mock DidDocumentClient
        DidDocumentClient mockClient = mock(DidDocumentClient.class);
        when(mockClient.fetchDidDocumentCached(anyString())).thenReturn(didDocument);

        HttpDidResolverService svc = new HttpDidResolverService(null, mockClient);

        // Request a different key ID that doesn't exist
        JWK jwk = svc.resolvePublicKey(DID, "non-existent-kid", "capabilityInvocation");
        assertNull(jwk);
    }
}
