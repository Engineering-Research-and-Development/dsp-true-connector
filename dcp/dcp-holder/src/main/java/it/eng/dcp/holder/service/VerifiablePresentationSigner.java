package it.eng.dcp.holder.service;

import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.holder.model.VerifiablePresentation;

/**
 * Service responsible for signing verifiable presentations in different formats.
 */
public interface VerifiablePresentationSigner {

    /**
     * Sign the provided VerifiablePresentation in the requested format.
     *
     * @param vp     the presentation to sign
     * @param format the target format, e.g. "jwt" or "json-ld"
     * @param participant the participant whose key signs the presentation
     * @return if format is "jwt" a compact JWT string; if "json-ld" a signed JSON object representation (as Object);
     *         caller is responsible for casting/serializing as needed.
     */
    Object sign(VerifiablePresentation vp, String format, ParticipantContext participant);
}

