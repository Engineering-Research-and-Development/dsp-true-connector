package it.eng.dcp.spi.testkit.defaults;

import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantKeyProvider;
import it.eng.dcp.spi.testkit.ParticipantKeyProviderContractTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * Runs the key provider contract against {@code DefaultParticipantKeyProvider}, which serves one participant.
 */
class DefaultParticipantKeyProviderContractTest extends ParticipantKeyProviderContractTest {

    @TempDir
    Path keystoreDirectory;

    private DefaultKeyProviderFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = DefaultKeyProviderFixture.create("default", "did:web:example.com:default", keystoreDirectory);
    }

    @Override
    protected ParticipantKeyProvider keyProvider() {
        return fixture.keyProvider();
    }

    @Override
    protected ParticipantContext participantA() {
        return fixture.participant();
    }

    @Override
    protected ParticipantContext participantB() {
        throw new UnsupportedOperationException("The default key provider serves a single participant");
    }

    @Override
    protected boolean supportsMultipleParticipants() {
        return false;
    }

    @Override
    protected void expireKeyGracePeriod() {
        fixture.expireGracePeriod();
    }
}
