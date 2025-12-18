package it.eng.dcp.common.service.sts;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.service.did.DidResolverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class SelfIssuedIdTokenServiceTest {

    private SelfIssuedIdTokenService svc;
    private ECKey signingKey;
    private final String connectorDid = "did:example:connector";
    private InMemoryDidResolverForTest didResolver;
    private InMemoryJtiCacheForTest jtiCache;

    @BeforeEach
    void setUp() throws JOSEException {
        // Create test implementations
        didResolver = new InMemoryDidResolverForTest();
        jtiCache = new InMemoryJtiCacheForTest();

        // Create service with connector DID injected
        svc = new SelfIssuedIdTokenService(connectorDid, didResolver, jtiCache, null);

        // Generate EC key for signing
        signingKey = new ECKeyGenerator(Curve.P_256)
                .keyID("kid-ec")
                .generate();

        // Override with test key
        svc.setOverrideSigningKey(signingKey);

        // Register public key in DID resolver
        didResolver.put(connectorDid, new JWKSet(signingKey.toPublicJWK()));
    }

    @Test
    @DisplayName("issue and validate token successfully")
    void issueAndValidateToken() {
        String token = svc.createAndSignToken("did:example:aud", null);
        assertNotNull(token);

        // Validation should succeed
        var claims = svc.validateToken(token);
        assertEquals(connectorDid, claims.getIssuer());
        assertEquals(connectorDid, claims.getSubject());
        assertEquals("did:example:aud", claims.getAudience().get(0));
    }

    @Test
    @DisplayName("create token with access token claim")
    void createTokenWithAccessToken() {
        String token = svc.createAndSignToken("did:example:aud", "access-token-123");
        assertNotNull(token);

        var claims = svc.validateToken(token);
        assertEquals("access-token-123", claims.getClaim("token"));
    }

    @Test
    @DisplayName("reject token creation with null audience")
    void rejectNullAudience() {
        assertThrows(IllegalArgumentException.class, 
            () -> svc.createAndSignToken(null, null));
    }

    @Test
    @DisplayName("reject token creation with blank audience")
    void rejectBlankAudience() {
        assertThrows(IllegalArgumentException.class, 
            () -> svc.createAndSignToken("", null));
    }

    // Test implementation of DidResolverService
    private static class InMemoryDidResolverForTest implements DidResolverService {
        private final Map<String, JWKSet> store = new ConcurrentHashMap<>();

        public void put(String did, JWKSet set) {
            store.put(did, set);
        }

        @Override
        public JWK resolvePublicKey(String did, String kid, String verificationRelationship) 
                throws DidResolutionException {
            JWKSet set = store.get(did);
            if (set == null) {
                throw new DidResolutionException("DID not found: " + did);
            }
            JWK key = set.getKeyByKeyId(kid);
            if (key == null) {
                throw new DidResolutionException("Key not found: " + kid);
            }
            return key;
        }
    }

    // Test implementation of JtiReplayCache (no-op for tests)
    private static class InMemoryJtiCacheForTest implements JtiReplayCache {
        private final Map<String, Instant> cache = new ConcurrentHashMap<>();

        @Override
        public void checkAndPut(String jti, Instant expiry) {
            // Note: For testing, we don't enforce replay detection
            // Production implementations should throw IllegalStateException on replay
            cache.put(jti, expiry);
        }
    }
}

