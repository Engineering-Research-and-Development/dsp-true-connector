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
     * <p>Key selection follows DCP §4.3.3: with a {@code kid}, the key it references; without a
     * {@code kid}, the single verification method of the DID document, and none when there are several.
     * The selected key must be referenced by {@code verificationRelationship} when one is given.
     *
     * @param did The DID to resolve (e.g., "did:web:example.com:connector")
     * @param kid The key ID (fragment or full ID) to retrieve from the DID document, or null if the token
     *            has no {@code kid} header
     * @param verificationRelationship Optional verification relationship (e.g., "authentication",
     *                                 "assertionMethod", "capabilityInvocation"). If null, any matching key is returned.
     * @return JWK instance of the selected key, or null if no key satisfies these rules
     * @throws DidResolutionException when resolution fails or the DID document is malformed
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

