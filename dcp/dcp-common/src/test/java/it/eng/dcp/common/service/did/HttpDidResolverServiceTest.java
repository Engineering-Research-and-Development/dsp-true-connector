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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
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
}
