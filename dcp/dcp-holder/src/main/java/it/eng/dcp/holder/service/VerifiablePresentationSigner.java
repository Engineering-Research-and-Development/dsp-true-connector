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
     * @return if format is "jwt" a compact JWT string; if "json-ld" a signed JSON object representation (as Object);
     *         caller is responsible for casting/serializing as needed.
     */
    default Object sign(VerifiablePresentation vp, String format) {
        return sign(vp, format, null);
    }

    /**
     * Sign the provided VerifiablePresentation in the requested format, binding it to a verifier.
     *
     * <p>For the "jwt" format the presentation carries an {@code aud} claim set to the verifier DID
     * (when provided) and a short-lived {@code exp} claim.
     *
     * @param vp          the presentation to sign
     * @param format      the target format, e.g. "jwt" or "json-ld"
     * @param verifierDid the DID of the querying verifier used as audience; may be null for an unbound presentation
     * @return the signed presentation, see {@link #sign(VerifiablePresentation, String)}
     */
    Object sign(VerifiablePresentation vp, String format, String verifierDid);
}

