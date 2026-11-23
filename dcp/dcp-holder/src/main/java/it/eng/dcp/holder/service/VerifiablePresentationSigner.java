package it.eng.dcp.holder.service;

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
     * @param verifierDid the DID of the verifier receiving the presentation
     * @return if format is "jwt" a compact JWT string; if "json-ld" a signed JSON object representation (as Object);
     *         caller is responsible for casting/serializing as needed.
     */
    Object sign(VerifiablePresentation vp, String format, String verifierDid);

    /**
     * Sign a presentation without verifier binding.
     *
     * @param vp the presentation to sign
     * @param format the target format
     * @return the signed presentation
     */
    default Object sign(VerifiablePresentation vp, String format) {
        return sign(vp, format, null);
    }
}

