package it.eng.dcp.common.participant;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Resolver for a single-participant deployment: every request addresses the one configured participant.
 */
public class FixedParticipantContextResolver implements ParticipantContextResolver {

    private final String participantContextId;

    /**
     * Creates the resolver.
     *
     * @param participantContextId the ID returned for every request
     */
    public FixedParticipantContextResolver(String participantContextId) {
        this.participantContextId = participantContextId;
    }

    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        return Optional.of(participantContextId);
    }
}
