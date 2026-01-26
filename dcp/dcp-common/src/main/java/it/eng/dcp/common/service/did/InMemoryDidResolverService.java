package it.eng.dcp.common.service.did;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory DID resolver implementation for testing and development.
 *
 * <p>This implementation maintains a simple in-memory mapping from DID to JWKSet
 * and returns keys by key ID. It's suitable for:
 * <ul>
 *   <li>Unit testing</li>
 *   <li>Integration testing</li>
 *   <li>Development environments</li>
 *   <li>Simple scenarios where did:web is not needed</li>
 * </ul>
 *
 * <p>For production use with did:web, use {@link HttpDidResolverService} instead.
 */
@Service
public class InMemoryDidResolverService implements DidResolverService {

    private final Map<String, JWKSet> store = new ConcurrentHashMap<>();

    /**
     * Registers a JWKSet for a given DID.
     *
     * @param did The DID to register (e.g., "did:example:123")
     * @param set The JWKSet containing public keys for this DID
     * @throws NullPointerException if did or set is null
     */
    public void put(String did, JWKSet set) {
        Objects.requireNonNull(did, "DID must not be null");
        Objects.requireNonNull(set, "JWKSet must not be null");
        store.put(did, set);
    }

    /**
     * Removes all registered DIDs from the store.
     * Useful for test cleanup.
     */
    public void clear() {
        store.clear();
    }

    @Override
    public JWK resolvePublicKey(String did, String kid, String verificationRelationship) throws DidResolutionException {
        Objects.requireNonNull(did, "DID must not be null");
        Objects.requireNonNull(kid, "Key ID must not be null");

        JWKSet set = store.get(did);
        if (set == null) {
            return null;
        }

        return set.getKeyByKeyId(kid);
    }

    @Override
    public DidDocument fetchDidDocumentCached(String did) {
        // In-memory implementation doesn't support full DID document resolution
        // This is primarily used for testing with JWKSet
        throw new UnsupportedOperationException(
                "InMemoryDidResolverService does not support DID document fetching. Use HttpDidResolverService for did:web resolution.");
    }
}

