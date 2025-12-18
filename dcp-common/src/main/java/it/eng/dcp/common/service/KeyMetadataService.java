package it.eng.dcp.common.service;

import it.eng.dcp.common.model.KeyMetadata;
import it.eng.dcp.common.repository.KeyMetadataRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
}

