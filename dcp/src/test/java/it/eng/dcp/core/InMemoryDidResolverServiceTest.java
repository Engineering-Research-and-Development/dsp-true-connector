package it.eng.dcp.core;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryDidResolverServiceTest {

    @Test
    @DisplayName("resolve known kid returns JWK")
    void resolveKnownKidReturnsJwk() throws DidResolutionException {
        InMemoryDidResolverService svc = new InMemoryDidResolverService();
        OctetSequenceKey key = new OctetSequenceKey.Builder(new com.nimbusds.jose.util.Base64URL("a2V5MQ"))
                .keyID("kid1")
                .build();
        JWKSet set = new JWKSet(key);
        svc.put("did:example:issuer1", set);

        JWK resolved = svc.resolvePublicKey("did:example:issuer1", "kid1", null);
        assertNotNull(resolved);
        assertEquals("kid1", resolved.getKeyID());
    }

    @Test
    @DisplayName("unknown kid returns null")
    void unknownKidReturnsNull() throws DidResolutionException {
        InMemoryDidResolverService svc = new InMemoryDidResolverService();
        JWK resolved = svc.resolvePublicKey("did:example:issuer2", "nope", null);
        assertNull(resolved);
    }
}
