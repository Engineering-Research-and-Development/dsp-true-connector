package it.eng.dcp.service;

import it.eng.dcp.model.VerifiableCredential;
import org.springframework.stereotype.Service;

/**
 * Simple in-memory RevocationService stub that treats no credential as revoked.
 */
@Service
public class InMemoryRevocationService implements RevocationService {

    @Override
    public boolean isRevoked(VerifiableCredential vc) {
        // Phase 2.1: default to not revoked. Real implementation will check status lists.
        return false;
    }
}

