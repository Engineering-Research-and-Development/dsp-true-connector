package it.eng.dcp.service;

import it.eng.dcp.model.VerifiableCredential;

/**
 * Stub for revocation checking used in Phase 2.1.
 */
public interface RevocationService {

    /**
     * Return true if the provided credential is considered revoked.
     * @param vc The verifiable credential to check.
     * @return true if revoked, false otherwise.
     */
    boolean isRevoked(VerifiableCredential vc);
}

