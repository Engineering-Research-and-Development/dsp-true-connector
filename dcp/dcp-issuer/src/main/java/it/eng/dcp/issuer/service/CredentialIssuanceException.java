package it.eng.dcp.issuer.service;

import java.util.List;

/**
 * Thrown when at least one requested credential could not be issued. DCP 1.0.1 §6.5.1 reports the status of
 * the whole request, so no credential of such a request is delivered and the holder gets {@code REJECTED}.
 */
public class CredentialIssuanceException extends IllegalStateException {

    private final transient List<String> failedCredentialIds;

    /**
     * Creates the exception.
     *
     * @param message the reason, safe to send to the holder (no payloads, keys or tokens)
     * @param failedCredentialIds the IDs of the requested credentials that could not be issued
     */
    public CredentialIssuanceException(String message, List<String> failedCredentialIds) {
        super(message);
        this.failedCredentialIds = List.copyOf(failedCredentialIds);
    }

    /**
     * Returns the credentials that could not be issued.
     *
     * @return the requested credential IDs that failed
     */
    public List<String> getFailedCredentialIds() {
        return failedCredentialIds;
    }
}
