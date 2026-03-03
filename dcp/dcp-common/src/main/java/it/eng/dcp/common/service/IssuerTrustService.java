package it.eng.dcp.common.service;

import java.util.Set;

/**
 * Tracks trusted issuers per credential type.
 *
 * <p>Used by both the holder (when accepting incoming credentials) and the verifier
 * (when validating embedded credentials inside a Verifiable Presentation) to enforce
 * the Dataspace Governance Authority's list of trusted issuer DIDs per credential type.
 */
public interface IssuerTrustService {

    /**
     * Registers an issuer DID as trusted for the given credential type.
     *
     * @param credentialType the credential type (e.g. {@code MembershipCredential})
     * @param issuerDid the issuer DID to trust
     */
    void addTrust(String credentialType, String issuerDid);

    /**
     * Returns whether the given issuer is trusted for the given credential type.
     *
     * @param credentialType the credential type to check
     * @param issuerDid the issuer DID to check
     * @return {@code true} if the issuer is trusted for the credential type
     */
    boolean isTrusted(String credentialType, String issuerDid);

    /**
     * Returns all trusted issuer DIDs for the given credential type.
     *
     * @param credentialType the credential type to query
     * @return unmodifiable set of trusted issuer DIDs; empty if none configured
     */
    Set<String> getTrustedIssuers(String credentialType);

    /**
     * Removes trust for an issuer DID for the given credential type.
     *
     * @param credentialType the credential type
     * @param issuerDid the issuer DID to remove
     */
    void removeTrust(String credentialType, String issuerDid);
}

