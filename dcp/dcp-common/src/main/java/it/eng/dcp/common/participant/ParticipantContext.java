package it.eng.dcp.common.participant;

import it.eng.dcp.common.model.ServiceEntry;
import jakarta.validation.ValidationException;

import java.util.List;
import java.util.Map;

/**
 * Immutable description of one participant a DCP host serves. The identifier has no meaning for DCP;
 * a multi-tenant host typically uses its tenant ID.
 *
 * @param id                 participant context ID
 * @param did                the DID of the participant
 * @param signingKey         reference to the participant's signing key
 * @param serviceEntries     service entries the participant advertises in its DID document
 * @param trustedIssuers     trusted issuer DIDs per credential type
 * @param issuerMetadata     issuer metadata configuration, {@code null} when the participant is not an Issuer
 * @param state              {@link ParticipantState#ENABLED} or {@link ParticipantState#DISABLED}
 */
public record ParticipantContext(
        String id,
        String did,
        SigningKeyReference signingKey,
        List<ServiceEntry> serviceEntries,
        Map<String, List<String>> trustedIssuers,
        IssuerMetadataConfiguration issuerMetadata,
        ParticipantState state) {

    /** The participant context ID used when a single participant is configured. */
    public static final String DEFAULT_ID = "default";

    /**
     * Validates the participant context and copies its collections.
     *
     * @throws ValidationException if the ID, DID, signing key or state is missing
     */
    public ParticipantContext {
        if (id == null || id.isBlank()) {
            throw new ValidationException("ParticipantContext - id must not be blank");
        }
        if (did == null || did.isBlank()) {
            throw new ValidationException("ParticipantContext - did must not be blank");
        }
        if (signingKey == null) {
            throw new ValidationException("ParticipantContext - signingKey must not be null");
        }
        if (state == null) {
            throw new ValidationException("ParticipantContext - state must not be null");
        }
        serviceEntries = serviceEntries == null ? List.of() : List.copyOf(serviceEntries);
        trustedIssuers = trustedIssuers == null ? Map.of() : Map.copyOf(trustedIssuers);
    }

    /**
     * Tells whether the participant can be served.
     *
     * @return {@code true} if the state is {@link ParticipantState#ENABLED}
     */
    public boolean isEnabled() {
        return state == ParticipantState.ENABLED;
    }
}
