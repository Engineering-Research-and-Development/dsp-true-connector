package it.eng.dcp.common.service.did;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.VerificationMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Key selection (DCP §4.3.3) and verification relationship enforcement of {@link HttpDidResolverService}.
 */
class HttpDidResolverServiceTest {

    static final String DID = "did:web:example.com:connector";
    static final String VM_1 = DID + "#key-1";
    static final String VM_2 = DID + "#key-2";

    private HttpDidResolverService svc;
    private ECKey key1;
    private ECKey key2;

    @BeforeEach
    void setUp() throws Exception {
        svc = spy(new HttpDidResolverService(mock(SimpleOkHttpRestClient.class)));
        key1 = new ECKeyGenerator(Curve.P_256).keyID("jwk-kid-1").generate();
        key2 = new ECKeyGenerator(Curve.P_256).keyID("jwk-kid-2").generate();
    }

    private static VerificationMethod vm(String id, ECKey key) {
        return VerificationMethod.Builder.newInstance()
                .id(id)
                .type("JsonWebKey2020")
                .controller(DID)
                .publicKeyJwk(key.toPublicJWK().toJSONObject())
                .build();
    }

    private void serve(DidDocument document) throws IOException {
        doReturn(document).when(svc).fetchDidDocumentCached(anyString());
    }

    @Test
    @DisplayName("kid fragment selects the referenced key with capabilityInvocation")
    void kidFragmentSelectsKey() throws DidResolutionException, IOException {
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(VM_1, key1), vm(VM_2, key2)))
                .capabilityInvocation(List.of(VM_1, VM_2))
                .build());

        JWK jwk = svc.resolvePublicKey(DID, "key-2", DidDocument.CAPABILITY_INVOCATION);

        assertNotNull(jwk);
        assertEquals(key2.toPublicJWK(), jwk);
    }

    @Test
    @DisplayName("kid as full verification method id or JWK kid selects the key")
    void kidFullIdOrJwkKidSelectsKey() throws DidResolutionException, IOException {
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(VM_1, key1), vm(VM_2, key2)))
                .capabilityInvocation(List.of(VM_1, VM_2))
                .build());

        assertEquals(key1.toPublicJWK(), svc.resolvePublicKey(DID, VM_1, DidDocument.CAPABILITY_INVOCATION));
        assertEquals(key2.toPublicJWK(), svc.resolvePublicKey(DID, "jwk-kid-2", DidDocument.CAPABILITY_INVOCATION));
    }

    @Test
    @DisplayName("relative relationship reference matches the verification method")
    void relativeRelationshipReferenceMatches() throws DidResolutionException, IOException {
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(VM_1, key1)))
                .capabilityInvocation(List.of("#key-1"))
                .build());

        assertNotNull(svc.resolvePublicKey(DID, "key-1", DidDocument.CAPABILITY_INVOCATION));
    }

    @Test
    @DisplayName("kid that matches no verification method is rejected")
    void unknownKidRejected() throws DidResolutionException, IOException {
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(VM_1, key1)))
                .capabilityInvocation(List.of(VM_1))
                .build());

        assertNull(svc.resolvePublicKey(DID, "non-existent-kid", DidDocument.CAPABILITY_INVOCATION));
    }

    @Test
    @DisplayName("no kid and exactly one verification method selects that key")
    void noKidSingleMethodSelectsKey() throws DidResolutionException, IOException {
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(VM_1, key1)))
                .capabilityInvocation(List.of(VM_1))
                .build());

        assertEquals(key1.toPublicJWK(), svc.resolvePublicKey(DID, null, DidDocument.CAPABILITY_INVOCATION));
    }

    @Test
    @DisplayName("no kid and several verification methods is rejected")
    void noKidSeveralMethodsRejected() throws DidResolutionException, IOException {
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(VM_1, key1), vm(VM_2, key2)))
                .capabilityInvocation(List.of(VM_1, VM_2))
                .build());

        assertNull(svc.resolvePublicKey(DID, null, DidDocument.CAPABILITY_INVOCATION));
    }

    @Test
    @DisplayName("key without capabilityInvocation relationship is rejected")
    void keyWithoutCapabilityInvocationRejected() throws DidResolutionException, IOException {
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(VM_1, key1), vm(VM_2, key2)))
                .authentication(List.of(VM_1))
                .capabilityInvocation(List.of(VM_2))
                .build());

        assertNull(svc.resolvePublicKey(DID, "key-1", DidDocument.CAPABILITY_INVOCATION));
        assertNotNull(svc.resolvePublicKey(DID, "key-1", DidDocument.AUTHENTICATION));
    }

    @Test
    @DisplayName("DID document without the requested relationship is rejected")
    void missingRelationshipRejected() throws DidResolutionException, IOException {
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(VM_1, key1)))
                .build());

        assertNull(svc.resolvePublicKey(DID, "key-1", DidDocument.CAPABILITY_INVOCATION));
        assertNull(svc.resolvePublicKey(DID, "key-1", DidDocument.ASSERTION_METHOD));
    }

    @Test
    @DisplayName("without a requested relationship any matching key is returned")
    void noRelationshipRequestedReturnsMatchingKey() throws DidResolutionException, IOException {
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(VM_1, key1)))
                .build());

        assertNotNull(svc.resolvePublicKey(DID, "key-1", null));
    }

    @Test
    @DisplayName("non did:web DIDs and missing documents resolve to no key")
    void unsupportedMethodOrMissingDocument() throws DidResolutionException, IOException {
        assertNull(svc.resolvePublicKey("did:key:z6Mk", "key-1", DidDocument.CAPABILITY_INVOCATION));

        serve(null);
        assertNull(svc.resolvePublicKey(DID, "key-1", DidDocument.CAPABILITY_INVOCATION));
    }

    @Test
    @DisplayName("a malformed JWK in another verification method does not break selection")
    void malformedOtherJwkIgnored() throws DidResolutionException, IOException {
        VerificationMethod broken = VerificationMethod.Builder.newInstance()
                .id(VM_1).type("JsonWebKey2020").controller(DID)
                .publicKeyJwk(Map.of("kty", "EC", "crv", "P-256"))
                .build();
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(broken, vm(VM_2, key2)))
                .capabilityInvocation(List.of(VM_1, VM_2))
                .build());

        assertEquals(key2.toPublicJWK(), svc.resolvePublicKey(DID, "key-2", DidDocument.CAPABILITY_INVOCATION));
        assertEquals(key2.toPublicJWK(), svc.resolvePublicKey(DID, "jwk-kid-2", DidDocument.CAPABILITY_INVOCATION));
    }

    @Test
    @DisplayName("when several methods match the kid, the one referenced by the relationship wins")
    void relationshipReferencedCandidatePreferred() throws Exception {
        ECKey sameKidOther = new ECKeyGenerator(Curve.P_256).keyID("shared").generate();
        ECKey sameKidInvocation = new ECKeyGenerator(Curve.P_256).keyID("shared").generate();
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(VM_1, sameKidOther), vm(VM_2, sameKidInvocation)))
                .authentication(List.of(VM_1))
                .capabilityInvocation(List.of(VM_2))
                .build());

        assertEquals(sameKidInvocation.toPublicJWK(),
                svc.resolvePublicKey(DID, "shared", DidDocument.CAPABILITY_INVOCATION));
    }

    @Test
    @DisplayName("relative verification method ids match absolute kids and references")
    void relativeVerificationMethodIdMatches() throws DidResolutionException, IOException {
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm("#key-1", key1)))
                .capabilityInvocation(List.of(VM_1))
                .build());

        assertEquals(key1.toPublicJWK(), svc.resolvePublicKey(DID, VM_1, DidDocument.CAPABILITY_INVOCATION));
    }

    @Test
    @DisplayName("a relative reference does not match a method of another DID with the same fragment")
    void relativeReferenceDoesNotMatchForeignMethod() throws DidResolutionException, IOException {
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm("did:web:other.example.com#key-1", key1)))
                .capabilityInvocation(List.of("#key-1"))
                .build());

        assertNull(svc.resolvePublicKey(DID, "did:web:other.example.com#key-1", DidDocument.CAPABILITY_INVOCATION));
    }

    @Test
    @DisplayName("resolveVerificationMethod returns the absolute id of a relative verification method")
    void resolveVerificationMethodReturnsAbsoluteId() throws DidResolutionException, IOException {
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm("#key-1", key1)))
                .assertionMethod(List.of("#key-1"))
                .build());

        Optional<ResolvedVerificationMethod> method =
                svc.resolveVerificationMethod(DID, "key-1", DidDocument.ASSERTION_METHOD);

        assertTrue(method.isPresent());
        assertEquals(VM_1, method.get().id());
        assertEquals(key1.toPublicJWK(), method.get().publicKey());
    }

    @Test
    @DisplayName("resolveVerificationMethod exposes a foreign method id selected by JWK kid")
    void resolveVerificationMethodExposesForeignMethodId() throws DidResolutionException, IOException {
        String foreignVm = "did:web:other.example.com#key-1";
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(foreignVm, key1)))
                .assertionMethod(List.of(foreignVm))
                .build());

        Optional<ResolvedVerificationMethod> method =
                svc.resolveVerificationMethod(DID, "jwk-kid-1", DidDocument.ASSERTION_METHOD);

        assertTrue(method.isPresent());
        assertEquals(foreignVm, method.get().id());
    }

    @Test
    @DisplayName("a method of the document's own DID is preferred over a foreign one with the same JWK kid")
    void ownMethodPreferredOverForeignMethodWithSameJwkKid() throws Exception {
        ECKey ownKey = new ECKeyGenerator(Curve.P_256).keyID("jwk-kid-1").generate();
        String foreignVm = "did:web:other.example.com#key-1";
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(foreignVm, key1), vm(VM_2, ownKey)))
                .authentication(List.of(foreignVm, VM_2))
                .build());

        Optional<ResolvedVerificationMethod> method =
                svc.resolveVerificationMethod(DID, "jwk-kid-1", DidDocument.AUTHENTICATION);

        assertTrue(method.isPresent());
        assertEquals(VM_2, method.get().id());
        assertEquals(ownKey.toPublicJWK(), method.get().publicKey());
    }

    @Test
    @DisplayName("resolveVerificationMethod is empty when the key lacks the requested relationship")
    void resolveVerificationMethodEmptyWithoutRelationship() throws DidResolutionException, IOException {
        serve(DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(VM_1, key1)))
                .capabilityInvocation(List.of(VM_1))
                .build());

        assertTrue(svc.resolveVerificationMethod(DID, "key-1", DidDocument.AUTHENTICATION).isEmpty());
    }

    @Test
    @DisplayName("an unknown kid refetches a cached document once, so a rotated key is accepted")
    void unknownKidRefetchesRotatedDocument() throws Exception {
        SimpleOkHttpRestClient httpClient = mock(SimpleOkHttpRestClient.class);
        HttpDidResolverService resolver = new HttpDidResolverService(httpClient);
        resolver.setMinRefreshIntervalSeconds(0);
        DidDocument beforeRotation = DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(VM_1, key1))).capabilityInvocation(List.of(VM_1)).build();
        DidDocument afterRotation = DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(VM_2, key2))).capabilityInvocation(List.of(VM_2)).build();
        when(httpClient.executeAndDeserialize(anyString(), eq("GET"), any(), any(), eq(DidDocument.class)))
                .thenReturn(beforeRotation, afterRotation);

        assertNotNull(resolver.resolvePublicKey(DID, "key-1", DidDocument.CAPABILITY_INVOCATION));
        assertEquals(key2.toPublicJWK(), resolver.resolvePublicKey(DID, "key-2", DidDocument.CAPABILITY_INVOCATION));
        verify(httpClient, times(2)).executeAndDeserialize(anyString(), eq("GET"), any(), any(), eq(DidDocument.class));
    }

    @Test
    @DisplayName("an unknown kid does not refetch a document fetched within the refresh interval")
    void unknownKidRefetchIsRateLimited() throws Exception {
        SimpleOkHttpRestClient httpClient = mock(SimpleOkHttpRestClient.class);
        HttpDidResolverService resolver = new HttpDidResolverService(httpClient);
        DidDocument document = DidDocument.Builder.newInstance().id(DID)
                .verificationMethod(List.of(vm(VM_1, key1))).capabilityInvocation(List.of(VM_1)).build();
        when(httpClient.executeAndDeserialize(anyString(), eq("GET"), any(), any(), eq(DidDocument.class)))
                .thenReturn(document);

        assertNotNull(resolver.resolvePublicKey(DID, "key-1", DidDocument.CAPABILITY_INVOCATION));
        assertNull(resolver.resolvePublicKey(DID, "forged-kid", DidDocument.CAPABILITY_INVOCATION));
        assertNull(resolver.resolvePublicKey(DID, "forged-kid", DidDocument.CAPABILITY_INVOCATION));
        verify(httpClient, times(1)).executeAndDeserialize(anyString(), eq("GET"), any(), any(), eq(DidDocument.class));
    }

    @Test
    @DisplayName("DID documents are fetched over https by default")
    void didDocumentUrl_defaultsToHttps() throws Exception {
        SimpleOkHttpRestClient client = mock(SimpleOkHttpRestClient.class);
        HttpDidResolverService resolver = new HttpDidResolverService(client);
        resolver.setMaxRetries(0);
        when(client.executeAndDeserialize(anyString(), eq("GET"), any(), any(), eq(DidDocument.class)))
                .thenReturn(DidDocument.Builder.newInstance().id(DID).build());

        resolver.fetchDidDocumentCached("did:web:localhost%3A8080:connector");

        verify(client).executeAndDeserialize(eq("https://localhost:8080/.well-known/did.json"),
                eq("GET"), any(), any(), eq(DidDocument.class));
    }

    @Test
    @DisplayName("DID documents are fetched over http only when insecure http is allowed and TLS is off")
    void didDocumentUrl_httpWhenAllowed() throws Exception {
        SimpleOkHttpRestClient client = mock(SimpleOkHttpRestClient.class);
        HttpDidResolverService resolver = new HttpDidResolverService(client);
        org.springframework.test.util.ReflectionTestUtils.setField(resolver, "allowInsecureHttp", true);
        when(client.executeAndDeserialize(anyString(), eq("GET"), any(), any(), eq(DidDocument.class)))
                .thenReturn(DidDocument.Builder.newInstance().id(DID).build());

        resolver.fetchDidDocumentCached("did:web:localhost%3A8080:connector");

        verify(client).executeAndDeserialize(eq("http://localhost:8080/.well-known/did.json"),
                eq("GET"), any(), any(), eq(DidDocument.class));
    }
}
