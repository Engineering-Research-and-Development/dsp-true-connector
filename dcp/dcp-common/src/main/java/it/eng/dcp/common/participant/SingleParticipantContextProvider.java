package it.eng.dcp.common.participant;

import it.eng.dcp.common.config.DcpProperties;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.ServiceEntry;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Default provider: serves the one participant configured by the {@code dcp.*} properties, with the
 * ID {@value ParticipantContext#DEFAULT_ID} unless {@code dcp.participant.id} says otherwise.
 */
public class SingleParticipantContextProvider implements ParticipantContextProvider {

    private final String participantId;
    private final Supplier<ParticipantContext> participant;

    /**
     * Builds the single participant from the DCP properties. The context is built on first use, so a node
     * without a configured DID (for example with DCP disabled) still starts.
     *
     * @param properties        the DCP properties
     * @param didDocumentConfig the DID document configuration derived from the properties; it holds the
     *                          resolved base URL used for the advertised service endpoints
     */
    public SingleParticipantContextProvider(DcpProperties properties, DidDocumentConfig didDocumentConfig) {
        this.participantId = properties.getParticipant().getId();
        this.participant = new Lazy(() -> build(properties, didDocumentConfig));
    }

    private ParticipantContext build(DcpProperties properties, DidDocumentConfig didDocumentConfig) {
        if (didDocumentConfig.getDid() == null || didDocumentConfig.getDid().isBlank()) {
            throw new IllegalStateException("dcp.connector-did is not configured");
        }
        String baseUrl = didDocumentConfig.getEffectiveBaseUrl();
        List<ServiceEntry> serviceEntries = didDocumentConfig.getServiceEntries().stream()
                .map(entry -> new ServiceEntry(entry.getId(), entry.getType(), entry.getFullEndpoint(baseUrl)))
                .toList();
        Map<String, List<String>> trustedIssuers = new LinkedHashMap<>();
        properties.getTrustedIssuers().forEach((type, dids) -> trustedIssuers.put(type, Arrays.stream(dids.split(","))
                .map(String::trim).filter(did -> !did.isEmpty()).toList()));
        String issuerLocation = properties.getIssuer() == null ? null : properties.getIssuer().getLocation();
        return new ParticipantContext(
                participantId,
                didDocumentConfig.getDid(),
                new SigningKeyReference(didDocumentConfig.getKeystorePath(), didDocumentConfig.getKeystoreAlias()),
                serviceEntries,
                trustedIssuers,
                new IssuerMetadataConfiguration(issuerLocation),
                ParticipantState.ENABLED);
    }

    @Override
    public Optional<ParticipantContext> find(String participantContextId) {
        return participantId.equals(participantContextId) ? Optional.of(participant.get()) : Optional.empty();
    }

    /** Memoizing supplier. */
    private static final class Lazy implements Supplier<ParticipantContext> {
        private final Supplier<ParticipantContext> delegate;
        private volatile ParticipantContext value;

        Lazy(Supplier<ParticipantContext> delegate) {
            this.delegate = delegate;
        }

        @Override
        public ParticipantContext get() {
            ParticipantContext result = value;
            if (result == null) {
                synchronized (this) {
                    if (value == null) {
                        value = delegate.get();
                    }
                    result = value;
                }
            }
            return result;
        }
    }
}
