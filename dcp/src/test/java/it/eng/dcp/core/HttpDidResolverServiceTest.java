package it.eng.dcp.core;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.util.Base64URL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

        // craft DID document JSON where verificationMethod contains the JWK and capabilityInvocation references the VM id
        String vmId = "did:web:example.com:connector#key-1";
        String didDoc = "{" +
                "\"id\": \"did:web:example.com:connector\"," +
                "\"verificationMethod\": [ { \"id\": \"" + vmId + "\", \"type\": \"JsonWebKey2020\", \"publicKeyJwk\": " + key.toJSONString() + " } ]," +
                "\"capabilityInvocation\": [ \"" + vmId + "\" ]" +
                "}";

        HttpDidResolverService svc = new HttpDidResolverService(null) {
            @Override
            protected String fetchDidDocument(String url) {
                return didDoc;
            }
        };

        JWK jwk = svc.resolvePublicKey(DID, "kid-ec", "capabilityInvocation");
        assertNotNull(jwk);
        assertEquals("kid-ec", jwk.getKeyID());
    }

    @Test
    @DisplayName("resolve throws when verification relationship does not reference VM id")
    void resolveThrowsWhenNotReferenced() throws DidResolutionException {
        OctetSequenceKey key = new OctetSequenceKey.Builder(new Base64URL("x"))
                .keyID("kid-ec")
                .build();

        String vmId = "did:web:example.com:connector#key-1";
        String didDoc = "{" +
                "\"id\": \"did:web:example.com:connector\"," +
                "\"verificationMethod\": [ { \"id\": \"" + vmId + "\", \"type\": \"JsonWebKey2020\", \"publicKeyJwk\": " + key.toJSONString() + " } ]," +
                "\"capabilityInvocation\": [ \"did:web:example.com:connector#other\" ]" +
                "}";

        HttpDidResolverService svc = new HttpDidResolverService(null) {
            @Override
            protected String fetchDidDocument(String url) {
                return didDoc;
            }
        };

        assertThrows(DidResolutionException.class, () -> svc.resolvePublicKey(DID, "kid-ec", "capabilityInvocation"));
    }
}
