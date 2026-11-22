package it.eng.dcp.holder.service;

import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Outcome of authorizing a presentation query (DCP v1.0.1 §5.3.1).
 *
 * <p>Created only after both tokens are valid: the Verifier's Self-Issued ID Token, addressed to this
 * Credential Service, and the access token it carries, issued by this Credential Service's holder to that
 * Verifier.
 *
 * @param verifierDid the validated {@code sub} of the Verifier's Self-Issued ID Token
 * @param holderDid the DID of the holder that owns this Credential Service (the access token {@code sub})
 * @param accessTokenClaims the validated access token claims; the only source of authorized scopes
 */
public record PresentationQueryAuthorization(String verifierDid, String holderDid, JWTClaimsSet accessTokenClaims) {
}
