package it.eng.dcp.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serial;
import java.io.Serializable;

/**
 * DCP Profile identifiers as defined in DCP Specification Appendix A.1.
 *
 * <p>This enum represents the two official DCP profiles:
 * <ul>
 *   <li>vc11-sl2021/jwt - VC Data Model 1.1 with StatusList2021 and JWT format</li>
 *   <li>vc20-bssl/jwt - VC Data Model 2.0 with BitstringStatusList and JWT format</li>
 * </ul>
 *
 * <p>Both profiles use JWT format exclusively - JSON-LD is not part of the DCP specification.
 */
public enum ProfileId implements Serializable {
    /**
     * VC Data Model 1.1 with StatusList2021 revocation and JWT format.
     * Specification alias: "vc11-sl2021/jwt"
     */
    VC11_SL2021_JWT("vc11-sl2021/jwt"),

    /**
     * VC Data Model 2.0 with BitstringStatusList revocation and JWT format.
     * Specification alias: "vc20-bssl/jwt"
     */
    VC20_BSSL_JWT("vc20-bssl/jwt");

    @Serial
    private static final long serialVersionUID = 1L;

    private final String specAlias;

    ProfileId(String specAlias) {
        this.specAlias = specAlias;
    }

    /**
     * Gets the specification-compliant alias for this profile.
     * @return The profile alias as defined in DCP Specification (e.g., "vc11-sl2021/jwt")
     */
    public String getSpecAlias() {
        return specAlias;
    }

    /**
     * Returns "jwt" for all profiles (both official DCP profiles use JWT format).
     * @return "jwt"
     */
    public String getFormat() {
        return "jwt";
    }

    /**
     * Gets the VC Data Model version for this profile.
     * @return "1.1" for VC11_SL2021_JWT, "2.0" for VC20_BSSL_JWT
     */
    public String getVcVersion() {
        return this == VC11_SL2021_JWT ? "1.1" : "2.0";
    }

    /**
     * Gets the status list mechanism for this profile.
     * @return "StatusList2021" for VC11_SL2021_JWT, "BitstringStatusList" for VC20_BSSL_JWT
     */
    public String getStatusListType() {
        return this == VC11_SL2021_JWT ? "StatusList2021" : "BitstringStatusList";
    }

    /**
     * Creates a ProfileId from a specification alias or enum name.
     * Supports both spec-compliant aliases (e.g., "vc11-sl2021/jwt") and enum names (e.g., "VC11_SL2021_JWT").
     *
     * @param value The profile identifier string
     * @return The matching ProfileId, or null if not found
     */
    @JsonCreator
    public static ProfileId fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        // Normalize input
        String normalized = value.trim();

        // Try spec alias first (e.g., "vc11-sl2021/jwt")
        for (ProfileId profile : values()) {
            if (profile.specAlias.equalsIgnoreCase(normalized)) {
                return profile;
            }
        }

        // Try enum name (e.g., "VC11_SL2021_JWT") for backward compatibility
        try {
            return ProfileId.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException ex) {
            // Not a valid enum name
        }

        return null;
    }

    /**
     * Returns the specification-compliant alias for JSON serialization.
     * @return The spec alias (e.g., "vc11-sl2021/jwt")
     */
    @JsonValue
    @Override
    public String toString() {
        return specAlias;
    }
}