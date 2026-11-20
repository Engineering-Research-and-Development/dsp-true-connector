package it.eng.dcp.common.service.did;

import com.nimbusds.jose.jwk.JWK;

/**
 * A verification method selected from a resolved DID document, with its public key.
 *
 * <p>The ID is an absolute DID URL ({@code did:web:example.com#key-1}), so its DID part names the
 * controller of the key. DCP §5.4.3 compares that DID with the issuer of a Verifiable Credential and the
 * holder of a Verifiable Presentation.
 *
 * @param id the absolute DID URL of the verification method
 * @param publicKey the public key of the verification method
 */
public record ResolvedVerificationMethod(String id, JWK publicKey) {
}
