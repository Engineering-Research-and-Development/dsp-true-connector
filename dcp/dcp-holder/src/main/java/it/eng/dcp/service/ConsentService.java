package it.eng.dcp.service;

import it.eng.dcp.model.ConsentRecord;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal in-memory ConsentService used for enforcing consent prior to presentations.
 * This is a stub implementation for PH2; persistence can be added later.
 */
@Service
public class ConsentService {

    private final Map<String, ConsentRecord> store = new HashMap<>();

    public Optional<ConsentRecord> findByHolderDid(String holderDid) {
        return store.values().stream().filter(c -> c.getHolderDid().equals(holderDid)).findFirst();
    }

    public void save(ConsentRecord rec) {
        store.put(rec.getId(), rec);
    }

    public boolean isConsentValidFor(String holderDid) {
        Optional<ConsentRecord> maybe = findByHolderDid(holderDid);
        if (maybe.isEmpty()) return false;
        ConsentRecord r = maybe.get();
        return !r.isExpired(Instant.now());
    }
}

