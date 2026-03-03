package it.eng.dcp.common.service;

import it.eng.dcp.common.util.DidUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory implementation of {@link IssuerTrustService}.
 *
 * <p>Seeded at startup from {@code dcp.trusted-issuers.*} properties via
 * {@code TrustedIssuersConfig} and used by both the holder and verifier modules
 * to enforce trusted-issuer policy.
 */
@Service
public class InMemoryIssuerTrustService implements IssuerTrustService {

    private final ConcurrentMap<String, Set<String>> trustMap = new ConcurrentHashMap<>();

    @Override
    public void addTrust(String credentialType, String issuerDid) {
        if (credentialType == null || credentialType.isBlank() || issuerDid == null || issuerDid.isBlank()) {
            throw new IllegalArgumentException("credentialType and issuerDid must be provided");
        }
        String normalizedDid = DidUtils.normalize(issuerDid);
        trustMap.computeIfAbsent(credentialType, k -> ConcurrentHashMap.newKeySet()).add(normalizedDid);
    }

    @Override
    public boolean isTrusted(String credentialType, String issuerDid) {
        if (credentialType == null || issuerDid == null) return false;
        Set<String> issuers = trustMap.get(credentialType);
        return issuers != null && issuers.contains(DidUtils.normalize(issuerDid));
    }

    @Override
    public Set<String> getTrustedIssuers(String credentialType) {
        Set<String> issuers = trustMap.get(credentialType);
        return issuers == null ? Collections.emptySet() : Collections.unmodifiableSet(issuers);
    }

    @Override
    public void removeTrust(String credentialType, String issuerDid) {
        Set<String> issuers = trustMap.get(credentialType);
        if (issuers != null) {
            issuers.remove(DidUtils.normalize(issuerDid));
            if (issuers.isEmpty()) {
                trustMap.remove(credentialType, Collections.emptySet());
            }
        }
    }
}

