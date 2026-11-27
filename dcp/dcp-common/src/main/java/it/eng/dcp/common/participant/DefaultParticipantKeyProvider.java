package it.eng.dcp.common.participant;

import com.nimbusds.jose.jwk.ECKey;
import it.eng.dcp.common.config.DcpProperties;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.KeyMetadata;
import it.eng.dcp.common.service.KeyMetadataService;
import it.eng.dcp.common.service.KeyService;
import lombok.extern.slf4j.Slf4j;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

/**
 * Default key provider for the single configured participant: wraps {@link KeyService} and
 * {@link KeyMetadataService}, which keep the key pairs in the configured PKCS12 keystore.
 */
@Slf4j
public class DefaultParticipantKeyProvider implements ParticipantKeyProvider {

    private final KeyService keyService;
    private final KeyMetadataService keyMetadataService;
    private final DidDocumentConfig didDocumentConfig;
    private final DcpProperties properties;

    /**
     * Creates the provider.
     *
     * @param keyService         low-level key loading and rotation
     * @param keyMetadataService key lifecycle metadata
     * @param didDocumentConfig  keystore location and credentials of the single participant
     * @param properties         DCP properties, for the key grace period
     */
    public DefaultParticipantKeyProvider(KeyService keyService, KeyMetadataService keyMetadataService,
                                         DidDocumentConfig didDocumentConfig, DcpProperties properties) {
        this.keyService = keyService;
        this.keyMetadataService = keyMetadataService;
        this.didDocumentConfig = didDocumentConfig;
        this.properties = properties;
    }

    @Override
    public ECKey activeSigningKey(ParticipantContext participant) {
        requireServed(participant);
        return keyService.getSigningJwk(didDocumentConfig);
    }

    @Override
    public List<PublishedKey> publishedKeys(ParticipantContext participant) {
        requireServed(participant);
        List<PublishedKey> keys = new ArrayList<>();
        KeyPair active = keyService.loadKeyPairWithActiveAlias(didDocumentConfig);
        keys.add(toPublishedKey(active, true));
        for (KeyMetadata retired : keyMetadataService.getRecentlyRetiredKeys(properties.getKeyGracePeriod())) {
            try {
                keys.add(toPublishedKey(keyService.loadKeyPair(didDocumentConfig, retired.getAlias()), false));
            } catch (RuntimeException e) {
                log.warn("Retired key '{}' cannot be loaded and is not published: {}", retired.getAlias(), e.getMessage());
            }
        }
        return keys;
    }

    @Override
    public String rotate(ParticipantContext participant) {
        requireServed(participant);
        return keyService.rotateKeyAndUpdateMetadata(didDocumentConfig.getKeystorePath(),
                didDocumentConfig.getKeystorePassword(), didDocumentConfig.getKeystoreAlias());
    }

    private PublishedKey toPublishedKey(KeyPair keyPair, boolean active) {
        return new PublishedKey(keyService.getKidFromPublicKey(didDocumentConfig, keyPair),
                keyService.convertPublicKeyToJWK(didDocumentConfig, keyPair), active);
    }

    private void requireServed(ParticipantContext participant) {
        if (participant == null || !participant.did().equals(didDocumentConfig.getDid())) {
            throw new IllegalArgumentException("Participant is not served by the default key provider");
        }
        if (!participant.isEnabled()) {
            throw new IllegalArgumentException("Participant is disabled");
        }
    }
}
