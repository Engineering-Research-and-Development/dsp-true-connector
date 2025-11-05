package it.eng.dcp.core;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory DID resolver stub that holds a mapping from DID -> JWKSet and returns keys by kid.
 */
@Service
public class InMemoryDidResolverService implements DidResolverService {

    private final Map<String, JWKSet> store = new ConcurrentHashMap<>();

    public void put(String did, JWKSet set) {
        Objects.requireNonNull(did);
        Objects.requireNonNull(set);
        store.put(did, set);
    }

    @Override
    public JWK resolvePublicKey(String did, String kid, String verificationRelationship) throws DidResolutionException {
        Objects.requireNonNull(did);
        Objects.requireNonNull(kid);
        JWKSet set = store.get(did);
        if (set == null) return null;
        return set.getKeyByKeyId(kid);
    }
}
