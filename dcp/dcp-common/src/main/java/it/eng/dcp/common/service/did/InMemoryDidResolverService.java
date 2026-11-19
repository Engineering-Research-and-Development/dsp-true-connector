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

    /**
     * {@inheritDoc}
     *
     * <p>The in-memory store holds no verification relationships, so {@code verificationRelationship}
     * is not checked. Without a {@code kid}, the single registered key is returned, and none when
     * several are registered.
     */
    @Override
    public JWK resolvePublicKey(String did, String kid, String verificationRelationship) throws DidResolutionException {
        Objects.requireNonNull(did, "DID must not be null");

        JWKSet set = store.get(did);
        if (set == null) {
            return null;
        }

        if (kid == null || kid.isBlank()) {
            return set.getKeys().size() == 1 ? set.getKeys().get(0) : null;
        }
        return set.getKeyByKeyId(kid);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The in-memory store holds keys only, so the returned document carries just the DID as its
     * {@code id} (enough for the {@code sub} == DID Document {@code id} check), without services or
     * verification methods.
     *
     * @return a minimal DID document for a registered DID, or null if the DID is not registered
     */
    @Override
    public DidDocument fetchDidDocumentCached(String did) {
        if (did == null || !store.containsKey(did)) {
            return null;
        }
        return DidDocument.Builder.newInstance().id(did).build();
    }
}

