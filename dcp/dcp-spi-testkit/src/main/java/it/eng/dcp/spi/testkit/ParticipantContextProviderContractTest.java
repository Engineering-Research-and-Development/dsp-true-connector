package it.eng.dcp.spi.testkit;

import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantContextProvider;
import it.eng.dcp.common.participant.ParticipantState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Contract every {@link ParticipantContextProvider} must honour. A host subclasses it, supplies the
 * fixtures and inherits the tests.
 *
 * <p>JUnit creates a new instance per test method, so create fixtures in a {@code @BeforeEach} method
 * of the subclass instead of sharing them statically.
 */
public abstract class ParticipantContextProviderContractTest {

    /**
     * Returns the implementation under test.
     *
     * @return the provider
     */
    protected abstract ParticipantContextProvider provider();

    /**
     * Returns an enabled participant the provider serves.
     *
     * @return the first participant
     */
    protected abstract ParticipantContext participantA();

    /**
     * Returns a second enabled participant with its own ID and DID. Only called when
     * {@link #supportsMultipleParticipants()} is {@code true}.
     *
     * @return the second participant
     */
    protected abstract ParticipantContext participantB();

    /**
     * Declares whether the implementation serves more than one participant. The multi-participant cases are
     * skipped when it does not.
     *
     * @return {@code true} if the implementation serves more than one participant
     */
    protected abstract boolean supportsMultipleParticipants();

    /**
     * Returns the ID of a participant the provider knows but has disabled. Implementations that cannot
     * represent a disabled participant, such as the single-participant default, return empty, and the
     * disabled cases are skipped.
     *
     * @return the ID of a disabled participant, or empty if none can exist
     */
    protected abstract Optional<String> disabledParticipantId();

    /**
     * Returns an ID that no participant uses. The default is a random value.
     *
     * @return an unknown participant ID
     */
    protected String unknownParticipantId() {
        return "unknown-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("an unknown ID returns empty")
    void unknownIdReturnsEmpty() {
        String unknown = unknownParticipantId();

        assertThat(provider().find(unknown)).isEmpty();
        assertThat(provider().findEnabled(unknown)).isEmpty();
    }

    @Test
    @DisplayName("a known enabled participant is found and usable")
    void enabledParticipantIsFoundAndUsable() {
        ParticipantContext expected = participantA();

        assertThat(provider().find(expected.id())).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(expected.id());
            assertThat(found.did()).isEqualTo(expected.did());
            assertThat(found.state()).isEqualTo(ParticipantState.ENABLED);
        });
        assertThat(provider().findEnabled(expected.id())).isPresent();
    }

    @Test
    @DisplayName("a disabled participant is reported as disabled and is never usable")
    void disabledParticipantIsNeverUsable() {
        Optional<String> disabledId = disabledParticipantId();
        assumeTrue(disabledId.isPresent(), "the implementation cannot represent a disabled participant");

        assertThat(provider().find(disabledId.get())).hasValueSatisfying(found -> {
            assertThat(found.state()).isEqualTo(ParticipantState.DISABLED);
            assertThat(found.isEnabled()).isFalse();
        });
        assertThat(provider().findEnabled(disabledId.get())).isEmpty();
    }

    @Test
    @DisplayName("each context has exactly one DID and a resolvable key reference")
    void contextHasOneDidAndKeyReference() {
        ParticipantContext expected = participantA();

        ParticipantContext found = provider().find(expected.id()).orElseThrow();
        ParticipantContext foundAgain = provider().find(expected.id()).orElseThrow();

        assertThat(found.did()).startsWith("did:").isEqualTo(foundAgain.did());
        assertThat(found.signingKey()).isNotNull();
        assertThat(found.signingKey().keystorePath()).isNotBlank();
        assertThat(found.signingKey().alias()).isNotBlank();
        assertThat(found.signingKey()).isEqualTo(foundAgain.signingKey());
    }

    @Test
    @DisplayName("DIDs are unique across participants, and a lookup never returns another participant")
    void didsAreUniqueAcrossParticipants() {
        assumeTrue(supportsMultipleParticipants(), "the implementation serves a single participant");
        ParticipantContext a = participantA();
        ParticipantContext b = participantB();

        ParticipantContext foundA = provider().find(a.id()).orElseThrow();
        ParticipantContext foundB = provider().find(b.id()).orElseThrow();

        assertThat(foundA.id()).isEqualTo(a.id()).isNotEqualTo(foundB.id());
        assertThat(foundA.did()).isEqualTo(a.did()).isNotEqualTo(foundB.did());
        assertThat(foundB.did()).isEqualTo(b.did());
    }
}
