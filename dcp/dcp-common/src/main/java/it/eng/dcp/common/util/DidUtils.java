package it.eng.dcp.common.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for handling DID (Decentralized Identifier) operations.
 * Provides methods for parsing, normalizing, and comparing DIDs.
 */
@UtilityClass
@Slf4j
public class DidUtils {

    /**
     * Strip the fragment portion from a DID.
     * Fragments are used to reference specific keys or services within a DID document.
     *
     * @param did The DID, potentially with a fragment (e.g., "did:web:example.com:8080#holder")
     * @return The DID without the fragment (e.g., "did:web:example.com:8080")
     */
    public static String stripFragment(String did) {
        if (did == null || did.isEmpty()) {
            return did;
        }
        int fragmentIndex = did.indexOf('#');
        return fragmentIndex > 0 ? did.substring(0, fragmentIndex) : did;
    }

    /**
     * Extract the fragment portion from a DID.
     *
     * @param did The DID, potentially with a fragment (e.g., "did:web:example.com:8080#holder")
     * @return The fragment without the '#' symbol (e.g., "holder"), or null if no fragment exists
     */
    public static String extractFragment(String did) {
        if (did == null || did.isEmpty()) {
            return null;
        }
        int fragmentIndex = did.indexOf('#');
        return fragmentIndex > 0 && fragmentIndex < did.length() - 1
            ? did.substring(fragmentIndex + 1)
            : null;
    }

    /**
     * Check if a DID contains a fragment.
     *
     * @param did The DID to check
     * @return true if the DID contains a fragment, false otherwise
     */
    public static boolean hasFragment(String did) {
        return did != null && did.contains("#");
    }

    /**
     * Compare two DIDs, ignoring fragments.
     * Useful for validating JWT audience claims that may include fragments.
     *
     * @param did1 First DID to compare
     * @param did2 Second DID to compare
     * @return true if the base DIDs match (ignoring fragments)
     */
    public static boolean compareIgnoringFragment(String did1, String did2) {
        if (did1 == null && did2 == null) {
            return true;
        }
        if (did1 == null || did2 == null) {
            return false;
        }
        return stripFragment(did1).equals(stripFragment(did2));
    }

    /**
     * Validate that a DID audience matches the expected DID, handling fragments gracefully.
     * This is useful for JWT validation where external systems may include fragments in the audience.
     *
     * @param audience The audience from the JWT (may include fragment)
     * @param expectedDid The expected DID (your connector's DID)
     * @return true if the audience is valid
     */
    public static boolean isValidAudience(String audience, String expectedDid) {
        if (audience == null || expectedDid == null) {
            return false;
        }

        boolean isValid = compareIgnoringFragment(audience, expectedDid);

        if (isValid && hasFragment(audience)) {
            String fragment = extractFragment(audience);
            log.debug("Audience includes fragment '{}' - this is acceptable for interoperability", fragment);
        }

        return isValid;
    }

    /**
     * Build a DID with a fragment.
     *
     * @param baseDid The base DID without fragment
     * @param fragment The fragment to append (without '#' symbol)
     * @return The complete DID with fragment
     */
    public static String withFragment(String baseDid, String fragment) {
        if (baseDid == null || baseDid.isEmpty()) {
            return baseDid;
        }
        if (fragment == null || fragment.isEmpty()) {
            return baseDid;
        }
        // Strip any existing fragment first
        String base = stripFragment(baseDid);
        return base + "#" + fragment;
    }

    /**
     * Normalize a DID for comparison purposes.
     * Removes fragment and trims whitespace.
     *
     * @param did The DID to normalize
     * @return The normalized DID
     */
    public static String normalize(String did) {
        if (did == null) {
            return null;
        }
        return stripFragment(did.trim());
    }
}
