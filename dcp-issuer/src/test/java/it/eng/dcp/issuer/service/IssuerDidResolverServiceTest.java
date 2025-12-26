package it.eng.dcp.issuer.service;

import com.nimbusds.jose.jwk.JWK;
import com.fasterxml.jackson.databind.JsonNode;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.issuer.client.SimpleOkHttpRestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssuerDidResolverServiceTest {

    @Mock
    private SimpleOkHttpRestClient simpleOkHttpRestClient;

    private IssuerDidResolverService resolverService;

    @BeforeEach
    void setUp() {
        resolverService = new IssuerDidResolverService(simpleOkHttpRestClient, false);
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
        IssuerDidResolverService resolver = spy(new IssuerDidResolverService(simpleOkHttpRestClient, false));
        doThrow(new IOException("fail")).when(resolver).fetchDidDocument(anyString());
        DidResolutionException ex = assertThrows(DidResolutionException.class, () -> resolver.resolvePublicKey("did:web:test", "kid", null));
        assertTrue(ex.getMessage().contains("Failed to fetch or parse DID document"));
    }

    @Test
    void resolvePublicKey_noVerificationMethod_returnsNull() throws Exception {
        IssuerDidResolverService resolver = spy(new IssuerDidResolverService(simpleOkHttpRestClient, false));
        JsonNode root = mock(JsonNode.class);
        doReturn(root).when(resolver).fetchDidDocumentCached(anyString());
        when(root.get("verificationMethod")).thenReturn(null);
        assertNull(resolver.resolvePublicKey("did:web:test", "kid", null));
    }

    @Test
    void resolvePublicKey_verificationMethodNotArray_returnsNull() throws Exception {
        IssuerDidResolverService resolver = spy(new IssuerDidResolverService(simpleOkHttpRestClient, false));
        JsonNode root = mock(JsonNode.class);
        doReturn(root).when(resolver).fetchDidDocumentCached(anyString());
        JsonNode vmNode = mock(JsonNode.class);
        when(root.get("verificationMethod")).thenReturn(vmNode);
        when(vmNode.isArray()).thenReturn(false);
        assertNull(resolver.resolvePublicKey("did:web:test", "kid", null));
    }

    @Test
    void resolvePublicKey_keyNotFound_returnsNull() throws Exception {
        IssuerDidResolverService resolver = spy(new IssuerDidResolverService(simpleOkHttpRestClient, false));
        JsonNode root = mock(JsonNode.class);
        JsonNode vmArray = mock(JsonNode.class);
        doReturn(root).when(resolver).fetchDidDocumentCached(anyString());
        when(root.get("verificationMethod")).thenReturn(vmArray);
        when(vmArray.isArray()).thenReturn(true);
        when(vmArray.elements()).thenReturn(new java.util.ArrayList<JsonNode>().iterator());
        assertNull(resolver.resolvePublicKey("did:web:test", "kid", null));
    }

    @Test
    void resolvePublicKey_jwkParseFails_throwsResolutionException() throws Exception {
        IssuerDidResolverService resolver = spy(new IssuerDidResolverService(simpleOkHttpRestClient, false));
        JsonNode root = mock(JsonNode.class);
        JsonNode vmArray = mock(JsonNode.class);
        JsonNode vm = mock(JsonNode.class);
        JsonNode idNode = mock(JsonNode.class);
        JsonNode jwkNode = mock(JsonNode.class);
        doReturn(root).when(resolver).fetchDidDocumentCached(anyString());
        when(root.get("verificationMethod")).thenReturn(vmArray);
        when(vmArray.isArray()).thenReturn(true);
        when(vmArray.elements()).thenReturn(List.of(vm).iterator());
        when(vm.get("id")).thenReturn(idNode);
        when(vm.get("publicKeyJwk")).thenReturn(jwkNode);
        when(idNode.asText()).thenReturn("vmId");
        when(jwkNode.toString()).thenReturn("invalid-jwk"); // This will cause JWK.parse to throw
        // Remove doThrow on isKeyMatch, let JWK.parse fail naturally
        assertThrows(DidResolutionException.class, () -> resolver.resolvePublicKey("did:web:test", "kid", null));
    }

    @Test
    void resolvePublicKey_keyFound_returnsJwk() throws Exception {
        IssuerDidResolverService resolver = spy(new IssuerDidResolverService(simpleOkHttpRestClient, false));
        JsonNode root = mock(JsonNode.class);
        JsonNode vmArray = mock(JsonNode.class);
        JsonNode vm = mock(JsonNode.class);
        JsonNode idNode = mock(JsonNode.class);
        JsonNode jwkNode = mock(JsonNode.class);
        doReturn(root).when(resolver).fetchDidDocumentCached(anyString());
        when(root.get("verificationMethod")).thenReturn(vmArray);
        when(vmArray.isArray()).thenReturn(true);
        when(vmArray.elements()).thenReturn(Collections.singletonList(vm).iterator());
        when(vm.get("id")).thenReturn(idNode);
        when(vm.get("publicKeyJwk")).thenReturn(jwkNode);
        when(idNode.asText()).thenReturn("vmId");
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
                    .keyID("kid")
                    .keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
                    .build();
                break;
            } catch (IllegalStateException e) {
                // Retry if invalid
            }
        }
        assertNotNull(ecKey, "Failed to generate valid ECKey for P-256 curve");
        String jwkJson = ecKey.toJSONString();
        when(jwkNode.toString()).thenReturn(jwkJson);
        doReturn(true).when(resolver).isKeyMatch(any(JWK.class), eq("vmId"), eq("kid"));
        JWK result = resolver.resolvePublicKey("did:web:test", "kid", null);
        assertNotNull(result);
        assertEquals("kid", result.getKeyID());
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
    void enforceVerificationRelationship_found_succeeds() throws Exception {
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
