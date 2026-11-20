package it.eng.dcp.common.service.did;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.util.Base64URL;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryDidResolverServiceTest {

    @Test
    @DisplayName("resolve known kid returns JWK")
    void resolveKnownKidReturnsJwk() throws DidResolutionException {
        InMemoryDidResolverService svc = new InMemoryDidResolverService();
        OctetSequenceKey key = new OctetSequenceKey.Builder(new Base64URL("a2V5MQ"))
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

    @Test
    @DisplayName("no kid returns the single registered key and none when several are registered")
    void noKidSelectsSingleKeyOnly() throws DidResolutionException {
        InMemoryDidResolverService svc = new InMemoryDidResolverService();
        OctetSequenceKey key1 = new OctetSequenceKey.Builder(new Base64URL("a2V5MQ")).keyID("kid1").build();
        OctetSequenceKey key2 = new OctetSequenceKey.Builder(new Base64URL("a2V5Mg")).keyID("kid2").build();
        svc.put("did:example:single", new JWKSet(key1));
        svc.put("did:example:several", new JWKSet(List.of(key1, key2)));

        assertEquals("kid1", svc.resolvePublicKey("did:example:single", null, null).getKeyID());
        assertNull(svc.resolvePublicKey("did:example:several", null, null));
    }

    @Test
    @DisplayName("fetchDidDocumentCached returns a document with the DID as id for registered DIDs only")
    void fetchDidDocumentReturnsIdForRegisteredDid() {
        InMemoryDidResolverService svc = new InMemoryDidResolverService();
        svc.put("did:example:issuer1", new JWKSet(new OctetSequenceKey.Builder(new Base64URL("a2V5MQ")).build()));

        DidDocument document = svc.fetchDidDocumentCached("did:example:issuer1");

        assertEquals("did:example:issuer1", document.getId());
        assertNull(svc.fetchDidDocumentCached("did:example:unknown"));
    }
}
