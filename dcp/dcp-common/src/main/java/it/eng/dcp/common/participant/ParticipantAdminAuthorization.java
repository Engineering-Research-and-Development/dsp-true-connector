package it.eng.dcp.common.participant;

import java.security.Principal;

/**
 * Hook through which the library asks the host whether an authenticated administration principal may act
 * on a participant.
 */
public interface ParticipantAdminAuthorization {

    /**
     * Tells whether the principal may administer the participant.
     *
     * @param principal   the authenticated administration principal
     * @param participant the participant the principal wants to act on
     * @return {@code true} if the action is allowed
     */
    boolean isAllowed(Principal principal, ParticipantContext participant);
}
