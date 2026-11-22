package it.eng.dcp.holder.exception;

/**
 * Thrown when an authenticated credential offer is not allowed: the token subject is not the offer's
 * {@code issuer}, or that issuer is not a trusted issuer. Mapped to HTTP 403 by the offer endpoint.
 */
public class CredentialOfferForbiddenException extends RuntimeException {

    /**
     * Creates the exception with the rejection reason.
     *
     * @param message the rejection reason, without token content
     */
    public CredentialOfferForbiddenException(String message) {
        super(message);
    }
}
