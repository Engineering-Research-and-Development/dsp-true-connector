package it.eng.dcp.common.participant;

import jakarta.servlet.http.HttpServletRequest;

import java.security.Principal;

/**
 * Convenience for controllers: resolves the usable participant context of a request at the controller
 * edge. Services receive the result as an explicit parameter.
 */
public class RequestParticipantContext {

    private final ParticipantContextResolver resolver;
    private final ParticipantContextProvider provider;
    private final ParticipantAdminAuthorization adminAuthorization;

    /**
     * Creates the helper.
     *
     * @param resolver determines the participant ID of a request
     * @param provider looks up the participant
     * @param adminAuthorization tells whether an administration principal may act on a participant
     */
    public RequestParticipantContext(ParticipantContextResolver resolver, ParticipantContextProvider provider,
                                     ParticipantAdminAuthorization adminAuthorization) {
        this.resolver = resolver;
        this.provider = provider;
        this.adminAuthorization = adminAuthorization;
    }

    /**
     * Resolves the participant a request addresses.
     *
     * @param request the inbound request
     * @return the enabled participant context
     * @throws ParticipantNotFoundException if the request addresses no participant, an unknown one or a disabled one
     */
    public ParticipantContext require(HttpServletRequest request) {
        return resolver.resolve(request)
                .flatMap(provider::findEnabled)
                .orElseThrow(() -> new ParticipantNotFoundException("Participant not found"));
    }

    /**
     * Resolves the participant an administration request acts on and checks that the principal may do so.
     *
     * @param request   the inbound administration request
     * @param principal the authenticated administration principal
     * @return the enabled participant context
     * @throws ParticipantNotFoundException  if the request addresses no participant, an unknown one or a disabled one
     * @throws ParticipantForbiddenException if the principal may not act on the participant
     */
    public ParticipantContext requireForAdmin(HttpServletRequest request, Principal principal) {
        ParticipantContext participant = require(request);
        if (!adminAuthorization.isAllowed(principal, participant)) {
            throw new ParticipantForbiddenException("Not authorized for this participant");
        }
        return participant;
    }
}
