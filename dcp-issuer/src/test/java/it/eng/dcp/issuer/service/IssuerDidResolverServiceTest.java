package it.eng.dcp.issuer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.jwk.JWK;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.common.util.DidDocumentClient;
import it.eng.dcp.issuer.client.SimpleOkHttpRestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssuerDidResolverServiceTest {

    @Mock
    private SimpleOkHttpRestClient simpleOkHttpRestClient;

    @Mock
    private DidDocumentClient didDocumentClient;

    private IssuerDidResolverService resolverService;

    @BeforeEach
    void setUp() {
        resolverService = new IssuerDidResolverService(simpleOkHttpRestClient, false, didDocumentClient);
        resolverService.setCacheTtlSeconds(1); // Short cache for tests
        resolverService.setMaxRetries(0); // No retry for tests
    }

    @Test
    void resolvePublicKey_nullDidOrKid_returnsNull() throws Exception {
        assertNull(resolverService.resolvePublicKey(null, "kid", null));
        assertNull(resolverService.resolvePublicKey("did:web:example", null, null));
    }

    @Test
    void resolvePublicKey_unsupportedDidMethod_returnsNull() throws Exception {
        assertNull(resolverService.resolvePublicKey("did:key:xyz", "kid", null));
    }

    @Test
    void resolvePublicKey_fetchDidDocumentThrows_throwsResolutionException() throws Exception {
        when(didDocumentClient.fetchDidDocumentCached(anyString())).thenThrow(new java.io.IOException("Network error"));
        DidResolutionException ex = assertThrows(DidResolutionException.class, () -> resolverService.resolvePublicKey("did:web:test", "kid", null));
        assertTrue(ex.getMessage().contains("Failed to fetch or parse DID document"));
        assertNotNull(ex.getCause());
        assertInstanceOf(java.io.IOException.class, ex.getCause());
    }

    @Test
    void resolvePublicKey_noVerificationMethod_returnsNull() throws Exception {
        DidDocument didDocument = DidDocument.Builder.newInstance()
                .id("did:web:test")
                .build();
        when(didDocumentClient.fetchDidDocumentCached(anyString())).thenReturn(didDocument);
        assertNull(resolverService.resolvePublicKey("did:web:test", "kid", null));
    }

    @Test
    void resolvePublicKey_keyNotFound_returnsNull() throws Exception {
        DidDocument didDocument = DidDocument.Builder.newInstance()
                .id("did:web:test")
                .verificationMethod(Collections.emptyList())
                .build();
        when(didDocumentClient.fetchDidDocumentCached(anyString())).thenReturn(didDocument);
        assertNull(resolverService.resolvePublicKey("did:web:test", "kid", null));
    }

    @Test
    void resolvePublicKey_jwkParseFails_throwsResolutionException() throws Exception {
        // Create a verification method with invalid JWK data
        Map<String, Object> invalidJwk = new java.util.HashMap<>();
        invalidJwk.put("kty", "invalid");

        VerificationMethod vm = VerificationMethod.Builder.newInstance()
                .id("did:web:test#key-1")
                .type("JsonWebKey2020")
                .controller("did:web:test")
                .publicKeyJwk(invalidJwk)
                .build();

        DidDocument didDocument = DidDocument.Builder.newInstance()
                .id("did:web:test")
                .verificationMethod(List.of(vm))
                .build();

        when(didDocumentClient.fetchDidDocumentCached(anyString())).thenReturn(didDocument);
        assertThrows(DidResolutionException.class, () -> resolverService.resolvePublicKey("did:web:test", "key-1", null));
    }

    @Test
    void resolvePublicKey_keyFound_returnsJwk() throws Exception {
        // Robustly generate a valid ECKey for P-256 curve
        com.nimbusds.jose.jwk.ECKey ecKey = null;
        for (int i = 0; i < 10; i++) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256);
            KeyPair kp = kpg.generateKeyPair();
            ECPublicKey ecPublicKey = (ECPublicKey) kp.getPublic();
            try {
                ecKey = new com.nimbusds.jose.jwk.ECKey.Builder(
                    com.nimbusds.jose.jwk.Curve.P_256, ecPublicKey)
                    .keyID("key-1")
                    .keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
                    .build();
                break;
            } catch (IllegalStateException e) {
                // Retry if invalid
            }
        }
        assertNotNull(ecKey, "Failed to generate valid ECKey for P-256 curve");

        // Create verification method with the valid JWK
        VerificationMethod vm = VerificationMethod.Builder.newInstance()
                .id("did:web:test#key-1")
                .type("JsonWebKey2020")
                .controller("did:web:test")
                .publicKeyJwk(ecKey.toJSONObject())
                .build();

        DidDocument didDocument = DidDocument.Builder.newInstance()
                .id("did:web:test")
                .verificationMethod(List.of(vm))
                .build();

        when(didDocumentClient.fetchDidDocumentCached(anyString())).thenReturn(didDocument);

        JWK result = resolverService.resolvePublicKey("did:web:test", "key-1", null);
        assertNotNull(result);
        assertEquals("key-1", result.getKeyID());
    }

    @Test
    void enforceVerificationRelationship_notPresent_throws() {
        JsonNode root = mock(JsonNode.class);
        when(root.get("auth")).thenReturn(null);
        DidResolutionException ex = assertThrows(DidResolutionException.class, () ->
            resolverService.enforceVerificationRelationship(root, "vmId", "kid", "auth"));
        assertTrue(ex.getMessage().contains("Verification relationship 'auth' not present"));
    }

    @Test
    void enforceVerificationRelationship_found_succeeds() {
        JsonNode root = mock(JsonNode.class);
        JsonNode relArray = mock(JsonNode.class);
        JsonNode rel = mock(JsonNode.class);
        when(root.get("auth")).thenReturn(relArray);
        when(relArray.iterator()).thenReturn(java.util.List.of(rel).iterator());
        when(rel.isTextual()).thenReturn(true);
        when(rel.asText()).thenReturn("vmId");
        assertDoesNotThrow(() -> resolverService.enforceVerificationRelationship(root, "vmId", "kid", "auth"));
    }
}
