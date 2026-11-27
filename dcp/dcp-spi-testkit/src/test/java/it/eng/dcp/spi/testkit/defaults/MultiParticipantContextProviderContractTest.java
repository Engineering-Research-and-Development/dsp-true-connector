package it.eng.dcp.spi.testkit.defaults;

import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantContextProvider;
import it.eng.dcp.common.participant.ParticipantState;
import it.eng.dcp.common.participant.SigningKeyReference;
import it.eng.dcp.spi.testkit.ParticipantContextProviderContractTest;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs the provider contract, including its multi-participant and disabled-participant cases, against a
 * map-backed provider. It proves that those cases of the kit pass for a correct implementation, which the
 * single-participant default cannot show.
 */
class MultiParticipantContextProviderContractTest extends ParticipantContextProviderContractTest {

    private static final String DISABLED_ID = "tenant-disabled";

    private final Map<String, ParticipantContext> participants = new HashMap<>();
    private final ParticipantContextProvider provider = id -> Optional.ofNullable(participants.get(id));

    @BeforeEach
    void setUp() {
        add("tenant-a", ParticipantState.ENABLED);
        add("tenant-b", ParticipantState.ENABLED);
        add(DISABLED_ID, ParticipantState.DISABLED);
    }

    @Override
    protected ParticipantContextProvider provider() {
        return provider;
    }

    @Override
    protected ParticipantContext participantA() {
        return participants.get("tenant-a");
    }

    @Override
    protected ParticipantContext participantB() {
        return participants.get("tenant-b");
    }

    @Override
    protected boolean supportsMultipleParticipants() {
        return true;
    }

    @Override
    protected Optional<String> disabledParticipantId() {
        return Optional.of(DISABLED_ID);
    }

    private void add(String id, ParticipantState state) {
        participants.put(id, new ParticipantContext(id, "did:web:example.com:" + id,
                new SigningKeyReference(id + ".p12", id + "-key"), List.of(), Map.of(), null, state));
    }
}
