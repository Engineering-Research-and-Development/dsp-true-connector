package it.eng.dcp.issuer.service.jwt;

import java.util.Map;

/**
 * Interface for generating Verifiable Credential JWTs.
 * Implementations provide profile-specific JWT generation logic.
 */
public interface VcJwtGenerator {

    /**
     * Generate a signed JWT for a Verifiable Credential.
     *
     * @param holderDid The holder's DID
     * @param credentialType The type of credential
     * @param claims Additional claims to include in the credential subject
     * @return A properly signed JWT VC string
     */
    String generateJwt(String holderDid, String credentialType, Map<String, String> claims);

    /**
     * Generate a signed JWT for a Verifiable Credential, with status list info.
     *
     * @param holderDid The holder's DID
     * @param credentialType The type of credential
     * @param claims Additional claims to include in the credential subject
     * @param statusListId The status list resource ID (nullable for non-status-list profiles)
     * @param statusListIndex The status list index (nullable for non-status-list profiles)
     * @return A properly signed JWT VC string
     */
    default String generateJwt(String holderDid, String credentialType, Map<String, String> claims, String statusListId, Integer statusListIndex) {
        return generateJwt(holderDid, credentialType, claims);
    }
}

