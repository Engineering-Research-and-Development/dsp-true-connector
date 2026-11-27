package it.eng.dcp.spi.testkit.defaults;

import com.nimbusds.jose.jwk.ECKey;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantKeyProvider;
import it.eng.dcp.spi.testkit.ParticipantKeyProviderContractTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Runs the key provider contract, including its multi-participant cases, against a provider that routes
 * each participant to its own {@code DefaultParticipantKeyProvider} with its own keystore and key metadata.
 * It proves that the multi-participant cases of the kit pass for a correct implementation.
 */
class MultiParticipantKeyProviderContractTest extends ParticipantKeyProviderContractTest {

    @TempDir
    Path keystoreDirectory;

    private DefaultKeyProviderFixture fixtureA;
    private DefaultKeyProviderFixture fixtureB;
    private ParticipantKeyProvider keyProvider;

    @BeforeEach
    void setUp() throws Exception {
        fixtureA = DefaultKeyProviderFixture.create("tenant-a", "did:web:example.com:tenant-a", keystoreDirectory);
        fixtureB = DefaultKeyProviderFixture.create("tenant-b", "did:web:example.com:tenant-b", keystoreDirectory);
        Map<String, ParticipantKeyProvider> byParticipant = Map.of(
                fixtureA.participant().id(), fixtureA.keyProvider(),
                fixtureB.participant().id(), fixtureB.keyProvider());
        keyProvider = new ParticipantKeyProvider() {
            @Override
            public ECKey activeSigningKey(ParticipantContext participant) {
                return byParticipant.get(participant.id()).activeSigningKey(participant);
            }

            @Override
            public List<PublishedKey> publishedKeys(ParticipantContext participant) {
                return byParticipant.get(participant.id()).publishedKeys(participant);
            }

            @Override
            public String rotate(ParticipantContext participant) {
                return byParticipant.get(participant.id()).rotate(participant);
            }
        };
    }

    @Override
    protected ParticipantKeyProvider keyProvider() {
        return keyProvider;
    }

    @Override
    protected ParticipantContext participantA() {
        return fixtureA.participant();
    }

    @Override
    protected ParticipantContext participantB() {
        return fixtureB.participant();
    }

    @Override
    protected boolean supportsMultipleParticipants() {
        return true;
    }

    @Override
    protected void expireKeyGracePeriod() {
        fixtureA.expireGracePeriod();
        fixtureB.expireGracePeriod();
    }
}
