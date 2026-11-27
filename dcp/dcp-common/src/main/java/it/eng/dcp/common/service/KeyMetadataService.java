package it.eng.dcp.common.service;

import it.eng.dcp.common.model.KeyMetadata;
import it.eng.dcp.common.repository.KeyMetadataRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing cryptographic key metadata.
 * Handles key lifecycle including activation, archiving, and rotation.
 */
@Service
public class KeyMetadataService {

    private final KeyMetadataRepository repository;

    public KeyMetadataService(KeyMetadataRepository repository) {
        this.repository = repository;
    }

    /**
     * Saves new key metadata and archives the previous active key.
     *
     * @param alias The alias of the new key
     * @return The saved KeyMetadata
     */
    public KeyMetadata saveNewKeyMetadata(String alias) {
        // Mark previous active key as inactive and archived (preserve its id for revocation checks)
        repository.findByActiveTrue().ifPresent(meta -> {
            KeyMetadata archived = KeyMetadata.Builder.newInstance()
                    .id(meta.getId())
                    .alias(meta.getAlias())
                    .createdAt(meta.getCreatedAt())
                    .active(false)
                    .archived(true)
                    .archivedAt(meta.getArchivedAt() == null ? Instant.now() : meta.getArchivedAt())
                    .build();
            repository.save(archived);
        });

        // Create a brand new KeyMetadata with a fresh id
        KeyMetadata metadata = KeyMetadata.Builder.newInstance()
                .alias(alias)
                .createdAt(Instant.now())
                .active(true)
                .archived(false)
                .build();
        return repository.save(metadata);
    }

    /**
     * Retrieves the currently active key metadata.
     *
     * @return Optional containing the active KeyMetadata, or empty if none exists
     */
    public Optional<KeyMetadata> getActiveKeyMetadata() {
        return repository.findByActiveTrue();
    }

    /**
     * Retrieves key metadata by alias.
     *
     * @param alias The key alias to search for
     * @return Optional containing the KeyMetadata, or empty if not found
     */
    public Optional<KeyMetadata> getKeyMetadataByAlias(String alias) {
        return repository.findByAlias(alias);
    }

    /**
     * Retrieves the keys that were retired within the given grace period.
     *
     * @param gracePeriod how long a retired key counts as recently retired
     * @return the archived keys whose retirement is not older than the grace period, most recent first
     */
    public List<KeyMetadata> getRecentlyRetiredKeys(Duration gracePeriod) {
        Instant threshold = Instant.now().minus(gracePeriod);
        return repository.findByArchivedTrue().stream()
                .filter(meta -> meta.getArchivedAt() != null && meta.getArchivedAt().isAfter(threshold))
                .sorted((a, b) -> b.getArchivedAt().compareTo(a.getArchivedAt()))
                .toList();
    }
}
