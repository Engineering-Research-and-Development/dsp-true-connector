package it.eng.dcp.common.service.did;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.util.Base64URL;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.VerificationMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HttpDidResolverServiceTest {

    static final String DID = "did:web:example.com:connector";

    @Test
    @DisplayName("resolve returns JWK when verification relationship references VM id")
    void resolveReturnsJwkWhenReferenced() throws DidResolutionException {
        // build a sample symmetric JWK
        OctetSequenceKey key = new OctetSequenceKey.Builder(new Base64URL("x"))
                .keyID("kid-ec")
                .build();

        // craft DID document where verificationMethod contains the JWK and capabilityInvocation references the VM id
        String vmId = "did:web:example.com:connector#key-1";

        VerificationMethod vm = VerificationMethod.Builder.newInstance()
                .id(vmId)
                .type("JsonWebKey2020")
                .publicKeyJwk(key.toJSONObject())
                .build();

        DidDocument didDoc = DidDocument.Builder.newInstance()
                .id("did:web:example.com:connector")
                .verificationMethod(List.of(vm))
                .capabilityInvocation(List.of(vmId))
                .build();

        HttpDidResolverService svc = new HttpDidResolverService(null) {
            @Override
            public DidDocument fetchDidDocumentCached(String did) {
                return didDoc;
            }
        };

        JWK jwk = svc.resolvePublicKey(DID, "kid-ec", "capabilityInvocation");
        assertNotNull(jwk);
        assertEquals("kid-ec", jwk.getKeyID());
    }

    @Test
    @DisplayName("resolve throws when verification relationship does not reference VM id")
    void resolveThrowsWhenNotReferenced() {
        OctetSequenceKey key = new OctetSequenceKey.Builder(new Base64URL("x"))
                .keyID("kid-ec")
                .build();

        String vmId = "did:web:example.com:connector#key-1";

        VerificationMethod vm = VerificationMethod.Builder.newInstance()
                .id(vmId)
                .type("JsonWebKey2020")
                .publicKeyJwk(key.toJSONObject())
                .build();

        DidDocument didDoc = DidDocument.Builder.newInstance()
                .id("did:web:example.com:connector")
                .verificationMethod(List.of(vm))
                .capabilityInvocation(List.of("did:web:example.com:connector#other"))
                .build();

        HttpDidResolverService svc = new HttpDidResolverService(null) {
            @Override
            public DidDocument fetchDidDocumentCached(String did) {
                return didDoc;
            }
        };

        assertThrows(DidResolutionException.class, () -> svc.resolvePublicKey(DID, "kid-ec", "capabilityInvocation"));
    }
}
