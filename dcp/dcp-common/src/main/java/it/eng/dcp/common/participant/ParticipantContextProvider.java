package it.eng.dcp.common.participant;

import java.util.Optional;

/**
 * Looks up participant contexts by ID. Implementations must not assume the caller caches results.
 * Hosts replace the default by defining their own bean.
 */
public interface ParticipantContextProvider {

    /**
     * Finds a participant context by ID, whatever its state.
     *
     * @param participantContextId the participant context ID
     * @return the context, which reports {@link ParticipantState#DISABLED} for a disabled participant,
     *         or empty for an unknown ID
     */
    Optional<ParticipantContext> find(String participantContextId);

    /**
     * Finds a participant context that can be used: a disabled participant is never returned.
     *
     * @param participantContextId the participant context ID
     * @return the enabled context, or empty if the ID is unknown or the participant is disabled
     */
    default Optional<ParticipantContext> findEnabled(String participantContextId) {
        return find(participantContextId).filter(ParticipantContext::isEnabled);
    }
}
