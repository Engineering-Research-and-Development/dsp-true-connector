package it.eng.dcp.common.participant;

import java.security.Principal;

/**
 * Default authorization: any administration principal may act on the single participant. Authentication of
 * the principal is enforced by the host's security chain, not by this hook.
 */
public class AllowAllAdminsParticipantAdminAuthorization implements ParticipantAdminAuthorization {

    @Override
    public boolean isAllowed(Principal principal, ParticipantContext participant) {
        return participant != null;
    }
}
