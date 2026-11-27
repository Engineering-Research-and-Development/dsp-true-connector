package it.eng.dcp.common.participant;

import com.nimbusds.jose.jwk.ECKey;
import it.eng.dcp.common.config.DcpProperties;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.KeyMetadata;
import it.eng.dcp.common.service.KeyMetadataService;
import it.eng.dcp.common.service.KeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultParticipantKeyProviderTest {

    private static final String DID = "did:web:example.com:holder";

    @Mock
    private KeyService keyService;
    @Mock
    private KeyMetadataService keyMetadataService;

    private DidDocumentConfig config;
    private DcpProperties properties;
    private DefaultParticipantKeyProvider provider;
    private ParticipantContext participant;

    @BeforeEach
    void setUp() {
        config = DidDocumentConfig.builder().did(DID).keystorePath("eckey.p12").keystorePassword("pw")
                .keystoreAlias("holder").serviceEntries(List.of()).build();
        properties = new DcpProperties();
        provider = new DefaultParticipantKeyProvider(keyService, keyMetadataService, config, properties);
        participant = new ParticipantContext("default", DID, new SigningKeyReference("eckey.p12", "holder"),
                List.of(), Map.of(), null, ParticipantState.ENABLED);
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    @Test
    @DisplayName("the active signing key is the one of the key service")
    void activeSigningKey() {
        ECKey key = org.mockito.Mockito.mock(ECKey.class);
        when(keyService.getSigningJwk(config)).thenReturn(key);

        assertSame(key, provider.activeSigningKey(participant));
    }

    @Test
    @DisplayName("published keys are the active key followed by keys retired within the grace period")
    void publishedKeys() throws Exception {
        KeyPair active = newKeyPair();
        KeyPair retired = newKeyPair();
        KeyMetadata retiredMetadata = KeyMetadata.Builder.newInstance().alias("old").active(false).archived(true)
                .archivedAt(Instant.now().minusSeconds(60)).build();
        when(keyService.loadKeyPairWithActiveAlias(config)).thenReturn(active);
        when(keyMetadataService.getRecentlyRetiredKeys(Duration.ofHours(24))).thenReturn(List.of(retiredMetadata));
        when(keyService.loadKeyPair(config, "old")).thenReturn(retired);
        when(keyService.getKidFromPublicKey(config, active)).thenReturn("new-kid");
        when(keyService.getKidFromPublicKey(config, retired)).thenReturn("old-kid");
        when(keyService.convertPublicKeyToJWK(config, active)).thenReturn(Map.of("kid", DID + "#new-kid"));
        when(keyService.convertPublicKeyToJWK(config, retired)).thenReturn(Map.of("kid", DID + "#old-kid"));

        List<ParticipantKeyProvider.PublishedKey> keys = provider.publishedKeys(participant);

        assertEquals(2, keys.size());
        assertEquals("new-kid", keys.get(0).kid());
        assertTrue(keys.get(0).active());
        assertEquals("old-kid", keys.get(1).kid());
        assertFalse(keys.get(1).active());
    }

    @Test
    @DisplayName("a retired key that cannot be loaded is not published, the others still are")
    void unloadableRetiredKeyIsSkipped() throws Exception {
        KeyPair active = newKeyPair();
        KeyMetadata retiredMetadata = KeyMetadata.Builder.newInstance().alias("gone").active(false).archived(true)
                .archivedAt(Instant.now()).build();
        when(keyService.loadKeyPairWithActiveAlias(config)).thenReturn(active);
        when(keyMetadataService.getRecentlyRetiredKeys(any())).thenReturn(List.of(retiredMetadata));
        when(keyService.loadKeyPair(config, "gone")).thenThrow(new RuntimeException("missing"));
        when(keyService.getKidFromPublicKey(config, active)).thenReturn("new-kid");
        when(keyService.convertPublicKeyToJWK(config, active)).thenReturn(Map.of("kid", "k"));

        assertEquals(1, provider.publishedKeys(participant).size());
    }

    @Test
    @DisplayName("rotation goes through the key service with the configured keystore")
    void rotate() {
        when(keyService.rotateKeyAndUpdateMetadata("eckey.p12", "pw", "holder")).thenReturn("holder-2");

        assertEquals("holder-2", provider.rotate(participant));
    }

    @Test
    @DisplayName("the key grace period comes from dcp.key-grace-period")
    void gracePeriodIsConfigurable() throws Exception {
        properties.setKeyGracePeriod(Duration.ofMinutes(5));
        KeyPair active = newKeyPair();
        when(keyService.loadKeyPairWithActiveAlias(config)).thenReturn(active);
        when(keyService.getKidFromPublicKey(config, active)).thenReturn("k");
        when(keyService.convertPublicKeyToJWK(config, active)).thenReturn(Map.of("kid", "k"));
        when(keyMetadataService.getRecentlyRetiredKeys(Duration.ofMinutes(5))).thenReturn(List.of());

        provider.publishedKeys(participant);

        verify(keyMetadataService).getRecentlyRetiredKeys(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("a participant of another DID is rejected without touching any key")
    void foreignParticipantIsRejected() {
        ParticipantContext other = new ParticipantContext("other", "did:web:other", new SigningKeyReference("k", "a"),
                List.of(), Map.of(), null, ParticipantState.ENABLED);

        assertThrows(IllegalArgumentException.class, () -> provider.activeSigningKey(other));
        assertThrows(IllegalArgumentException.class, () -> provider.publishedKeys(other));
        assertThrows(IllegalArgumentException.class, () -> provider.rotate(other));
        verify(keyService, never()).rotateKeyAndUpdateMetadata(any(), any(), any());
    }

    @Test
    @DisplayName("a disabled participant is never used for signing or rotation")
    void disabledParticipantIsRejected() {
        ParticipantContext disabled = new ParticipantContext("default", DID, new SigningKeyReference("k", "a"),
                List.of(), Map.of(), null, ParticipantState.DISABLED);

        assertThrows(IllegalArgumentException.class, () -> provider.activeSigningKey(disabled));
        assertThrows(IllegalArgumentException.class, () -> provider.rotate(disabled));
    }
}
