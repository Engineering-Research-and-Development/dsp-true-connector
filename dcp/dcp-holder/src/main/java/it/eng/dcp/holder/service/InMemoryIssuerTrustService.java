package it.eng.dcp.holder.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class InMemoryIssuerTrustService implements IssuerTrustService {

    private final ConcurrentMap<String, Set<String>> trustMap = new ConcurrentHashMap<>();

    @Override
    public void addTrust(String credentialType, String issuerDid) {
        if (credentialType == null || credentialType.isBlank() || issuerDid == null || issuerDid.isBlank()) {
            throw new IllegalArgumentException("credentialType and issuerDid must be provided");
        }
        trustMap.computeIfAbsent(credentialType, k -> ConcurrentHashMap.newKeySet()).add(issuerDid);
    }

    @Override
    public boolean isTrusted(String credentialType, String issuerDid) {
        if (credentialType == null || issuerDid == null) return false;
        Set<String> issuers = trustMap.get(credentialType);
        return issuers != null && issuers.contains(issuerDid);
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
            issuers.remove(issuerDid);
            if (issuers.isEmpty()) {
                trustMap.remove(credentialType, Collections.emptySet());
            }
        }
    }
}

