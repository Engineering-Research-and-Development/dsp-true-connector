package it.eng.dcp.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serial;
import java.io.Serializable;

/**
 * Well-known profile identifiers used by the DCP implementation to determine credential/presentation profiles.
 */
public enum ProfileId implements Serializable {
    VC11_SL2021_JWT,
    VC11_SL2021_JSONLD;

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonCreator
    public static ProfileId fromString(String value) {
        if (value == null) return null;
        try {
            return ProfileId.valueOf(value);
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