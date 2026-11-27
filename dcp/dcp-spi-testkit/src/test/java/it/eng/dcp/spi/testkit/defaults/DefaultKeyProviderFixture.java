package it.eng.dcp.spi.testkit.defaults;

import it.eng.dcp.common.config.DcpProperties;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.KeyMetadata;
import it.eng.dcp.common.participant.DefaultParticipantKeyProvider;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantState;
import it.eng.dcp.common.participant.SigningKeyReference;
import it.eng.dcp.common.repository.KeyMetadataRepository;
import it.eng.dcp.common.service.KeyMetadataService;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.common.service.audit.DcpAuditEventPublisher;
import it.eng.dcp.common.util.SelfSignedCertGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * One participant's {@link DefaultParticipantKeyProvider} backed by a real PKCS12 keystore and an in-memory
 * key metadata store, so the contract exercises real key generation, signing and rotation without MongoDB.
 */
final class DefaultKeyProviderFixture {

    private static final String PASSWORD = "password";
    private static final Duration GRACE_PERIOD = Duration.ofHours(1);

    private final ParticipantContext participant;
    private final DefaultParticipantKeyProvider keyProvider;
    private final List<KeyMetadata> metadata;

    private DefaultKeyProviderFixture(ParticipantContext participant, DefaultParticipantKeyProvider keyProvider,
                                      List<KeyMetadata> metadata) {
        this.participant = participant;
        this.keyProvider = keyProvider;
        this.metadata = metadata;
    }

    /**
     * Creates the keystore with one key entry and registers that key as the active one, as a deployed
     * participant has.
     *
     * @param participantId the participant ID
     * @param did           the participant DID
     * @param directory     directory that receives the keystore
     * @return the fixture
     * @throws Exception if the keystore cannot be created
     */
    static DefaultKeyProviderFixture create(String participantId, String did, Path directory) throws Exception {
        String alias = participantId + "-key";
        Path keystore = directory.resolve(participantId + ".p12");
        writeKeystore(keystore, alias);

        List<KeyMetadata> metadata = new ArrayList<>();
        KeyMetadataService metadataService = new KeyMetadataService(inMemoryRepository(metadata));
        KeyService keyService = new KeyService(metadataService, mock(DcpAuditEventPublisher.class));
        DidDocumentConfig config = DidDocumentConfig.builder().did(did).keystorePath(keystore.toString())
                .keystorePassword(PASSWORD).keystoreAlias(alias).serviceEntries(List.of()).build();
        DcpProperties properties = new DcpProperties();
        properties.setKeyGracePeriod(GRACE_PERIOD);
        // Without a registered active key, the first rotation would not retire the initial key
        metadataService.saveNewKeyMetadata(alias);

        ParticipantContext participant = new ParticipantContext(participantId, did,
                new SigningKeyReference(keystore.toString(), alias), List.of(), Map.of(), null,
                ParticipantState.ENABLED);
        return new DefaultKeyProviderFixture(participant,
                new DefaultParticipantKeyProvider(keyService, metadataService, config, properties), metadata);
    }

    ParticipantContext participant() {
        return participant;
    }

    DefaultParticipantKeyProvider keyProvider() {
        return keyProvider;
    }

    /**
     * Moves the retirement time of every retired key beyond the grace period.
     */
    void expireGracePeriod() {
        metadata.replaceAll(meta -> meta.isArchived() ? expire(meta) : meta);
    }

    private static KeyMetadata expire(KeyMetadata meta) {
        return KeyMetadata.Builder.newInstance().id(meta.getId()).alias(meta.getAlias())
                .createdAt(meta.getCreatedAt()).active(false).archived(true)
                .archivedAt(meta.getArchivedAt().minus(GRACE_PERIOD.multipliedBy(2))).build();
    }

    private static void writeKeystore(Path keystore, String alias) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair keyPair = generator.generateKeyPair();
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry(alias, keyPair.getPrivate(), PASSWORD.toCharArray(),
                new Certificate[]{SelfSignedCertGenerator.generate("CN=DidKey", keyPair)});
        try (OutputStream out = Files.newOutputStream(keystore)) {
            store.store(out, PASSWORD.toCharArray());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write the test keystore", e);
        }
    }

    private static KeyMetadataRepository inMemoryRepository(List<KeyMetadata> store) {
        KeyMetadataRepository repository = mock(KeyMetadataRepository.class);
        when(repository.save(any(KeyMetadata.class))).thenAnswer(invocation -> {
            KeyMetadata saved = invocation.getArgument(0);
            store.removeIf(existing -> existing.getId().equals(saved.getId()));
            store.add(saved);
            return saved;
        });
        when(repository.findByActiveTrue()).thenAnswer(invocation ->
                store.stream().filter(KeyMetadata::isActive).findFirst());
        when(repository.findByAlias(anyString())).thenAnswer(invocation ->
                store.stream().filter(meta -> meta.getAlias().equals(invocation.<String>getArgument(0))).findFirst());
        when(repository.findByArchivedTrue()).thenAnswer(invocation ->
                store.stream().filter(KeyMetadata::isArchived).toList());
        return repository;
    }
}
