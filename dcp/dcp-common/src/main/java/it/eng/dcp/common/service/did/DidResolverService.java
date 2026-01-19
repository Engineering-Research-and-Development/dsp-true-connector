package it.eng.dcp.common.service.did;

import com.nimbusds.jose.jwk.JWK;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;

import java.io.IOException;

/**
 * Service for resolving DIDs and extracting public keys.
 *
 * <p>This interface abstracts DID resolution functionality, allowing different
 * implementations for various DID methods (did:web, did:key, etc.).
 *
 * <p>Both issuer and holder/verifier modules need DID resolution for:
 * <ul>
 *   <li>Verifying signatures on incoming tokens</li>
 *   <li>Discovering service endpoints</li>
 *   <li>Retrieving public keys for encryption</li>
 * </ul>
 */
public interface DidResolverService {

    /**
     * Resolves the public JWK for a DID and key ID with an optional verification relationship.
     *
     * @param did The DID to resolve (e.g., "did:web:example.com:connector")
     * @param kid The key ID (fragment or full ID) to retrieve from the DID document
     * @param verificationRelationship Optional verification relationship (e.g., "authentication",
     *                                 "assertionMethod", "capabilityInvocation"). If null, any matching key is returned.
     * @return JWK instance matching the kid
     * @throws DidResolutionException when resolution fails, DID document is malformed, or key not found
     */
    JWK resolvePublicKey(String did, String kid, String verificationRelationship) throws DidResolutionException;

    /**
     * Fetches and returns a DID document for the given DID.
     *
     * <p>Implementations may cache documents to improve performance.
     * The caching strategy is implementation-specific.
     *
     * @param did The DID to resolve (e.g., "did:web:example.com:connector")
     * @return The parsed DID document, or null if not found
     * @throws IOException if the document cannot be fetched or parsed
     */
    DidDocument fetchDidDocumentCached(String did) throws IOException;

}

