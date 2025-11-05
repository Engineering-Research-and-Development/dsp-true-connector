package it.eng.dcp.service;

import it.eng.dcp.model.KeyMetadata;
import it.eng.dcp.repository.KeyMetadataRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class KeyMetadataService {

    private final KeyMetadataRepository repository;

    public KeyMetadataService(KeyMetadataRepository repository) {
        this.repository = repository;
    }

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

    public Optional<KeyMetadata> getActiveKeyMetadata() {
        return repository.findByActiveTrue();
    }

    public Optional<KeyMetadata> getKeyMetadataByAlias(String alias) {
        return repository.findByAlias(alias);
    }
}
