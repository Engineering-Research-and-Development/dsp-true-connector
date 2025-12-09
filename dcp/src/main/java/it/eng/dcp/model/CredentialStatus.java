package it.eng.dcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serial;
import java.io.Serializable;

/**
 * Status of a credential. Terminal states are ISSUED and REJECTED.
 */
public enum CredentialStatus implements Serializable {
    PENDING,
    RECEIVED,
    ISSUED,
    REJECTED;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Returns true if this status is terminal (no further state transitions expected).
     *
     * @return true if this status is terminal (ISSUED or REJECTED), false otherwise.
     */
    public boolean isTerminal() {
        return this == ISSUED || this == REJECTED;
    }

    @JsonCreator
    public static CredentialStatus fromString(String value) {
        if (value == null) return null;
        try {
            return CredentialStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @JsonValue
    @Override
    public String toString() {
        return name();
    }
}
