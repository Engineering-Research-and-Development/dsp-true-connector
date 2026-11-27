package it.eng.dcp.spi.testkit.defaults;

import it.eng.dcp.common.config.DcpProperties;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantContextProvider;
import it.eng.dcp.common.participant.SingleParticipantContextProvider;
import it.eng.dcp.spi.testkit.ParticipantContextProviderContractTest;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Optional;

/**
 * Runs the provider contract against {@link SingleParticipantContextProvider}, which serves one enabled
 * participant built from the {@code dcp.*} properties.
 */
class SingleParticipantContextProviderContractTest extends ParticipantContextProviderContractTest {

    private SingleParticipantContextProvider provider;

    @BeforeEach
    void setUp() {
        DcpProperties properties = new DcpProperties();
        DidDocumentConfig config = DidDocumentConfig.builder().did("did:web:example.com:default")
                .baseUrl("https://example.com").keystorePath("eckey.p12").keystoreAlias("default-key")
                .serviceEntries(List.of()).build();
        provider = new SingleParticipantContextProvider(properties, config);
    }

    @Override
    protected ParticipantContextProvider provider() {
        return provider;
    }

    @Override
    protected ParticipantContext participantA() {
        return provider.find(ParticipantContext.DEFAULT_ID).orElseThrow();
    }

    @Override
    protected ParticipantContext participantB() {
        throw new UnsupportedOperationException("The default provider serves a single participant");
    }

    @Override
    protected boolean supportsMultipleParticipants() {
        return false;
    }

    @Override
    protected Optional<String> disabledParticipantId() {
        return Optional.empty();
    }
}
