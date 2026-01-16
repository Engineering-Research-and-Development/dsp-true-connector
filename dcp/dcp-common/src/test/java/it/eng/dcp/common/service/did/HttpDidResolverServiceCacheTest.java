package it.eng.dcp.common.service.did;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.util.Base64URL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HttpDidResolverServiceCacheTest {

    @Test
    @DisplayName("fetchDidDocument called once when cached")
    void fetchCalledOnceWhenCached() throws Exception {
        OctetSequenceKey key = new OctetSequenceKey.Builder(new Base64URL("x"))
                .keyID("kid1")
                .build();

        String vmId = "did:web:example.com:connector#k";
        String didDoc = "{" +
                "\"id\": \"did:web:example.com:connector\"," +
                "\"verificationMethod\": [ { \"id\": \"" + vmId + "\", \"type\": \"JsonWebKey2020\", \"publicKeyJwk\": " + key.toJSONString() + " } ]," +
                "\"capabilityInvocation\": [ \"" + vmId + "\" ]" +
                "}";

        AtomicInteger calls = new AtomicInteger(0);
        HttpDidResolverService svc = new HttpDidResolverService(null) {
            @Override
            protected String fetchDidDocument(String url) {
                calls.incrementAndGet();
                return didDoc;
            }
        };

        svc.setCacheTtlSeconds(60);

        JWK jwk1 = svc.resolvePublicKey("did:web:example.com:connector", "kid1", "capabilityInvocation");
        assertNotNull(jwk1);
        JWK jwk2 = svc.resolvePublicKey("did:web:example.com:connector", "kid1", "capabilityInvocation");
        assertNotNull(jwk2);

        assertEquals(1, calls.get(), "fetchDidDocument should be called only once due to caching");
    }
}

