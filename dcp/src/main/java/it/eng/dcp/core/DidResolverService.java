package it.eng.dcp.core;

import com.nimbusds.jose.jwk.JWK;

/**
 * Resolve a DID and return a public key JWK for the given kid and verification relationship.
 */
public interface DidResolverService {

    /**
     * Resolve the public JWK for a DID and kid with an optional verification relationship (e.g., "capabilityInvocation").
     * @param did the DID (e.g., did:web:example.com:connector)
     * @param kid the key id (fragment or full id)
     * @param verificationRelationship optional verification relationship
     * @return JWK instance matching the kid
     * @throws DidResolutionException when resolution or parsing fails
     */
    JWK resolvePublicKey(String did, String kid, String verificationRelationship) throws DidResolutionException;
}
