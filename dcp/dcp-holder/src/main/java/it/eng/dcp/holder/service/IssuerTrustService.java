package it.eng.dcp.holder.service;

import java.util.Set;

/**
 * Track trusted issuers per credential type.
 */
public interface IssuerTrustService {

    void addTrust(String credentialType, String issuerDid);

    boolean isTrusted(String credentialType, String issuerDid);

    Set<String> getTrustedIssuers(String credentialType);

    void removeTrust(String credentialType, String issuerDid);
}

