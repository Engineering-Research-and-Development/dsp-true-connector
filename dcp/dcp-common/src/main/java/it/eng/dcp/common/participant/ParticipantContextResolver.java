package it.eng.dcp.common.participant;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Determines the participant context ID an inbound request addresses.
 */
public interface ParticipantContextResolver {

    /**
     * Resolves the participant context ID of a request.
     *
     * @param request the inbound request
     * @return the participant context ID, or empty if the request does not address a participant
     */
    Optional<String> resolve(HttpServletRequest request);
}
