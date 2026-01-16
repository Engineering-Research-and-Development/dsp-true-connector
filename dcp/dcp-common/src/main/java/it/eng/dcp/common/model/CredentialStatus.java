package it.eng.dcp.common.model;

/**
 * Enumeration of possible credential request/issuance statuses.
 */
public enum CredentialStatus {
    PENDING,
    RECEIVED,
    ISSUED,
    REJECTED;

    /**
     * Checks if this status represents a terminal state (ISSUED or REJECTED).
     * Terminal states indicate that the credential request has been completed.
     *
     * @return true if this is a terminal status (ISSUED or REJECTED), false otherwise
     */
    public boolean isTerminal() {
        return this == ISSUED || this == REJECTED;
    }
}

