package it.eng.dcp.issuer.service;

import java.util.UUID;

/**
 * Creates identifiers for DCP credential objects.
 *
 * <p>The DCP v1.0.1 CredentialObject schema declares {@code id} as {@code format: uri}, so every
 * identifier is a {@code urn:uuid:} URN rather than a bare UUID.
 */
public final class CredentialObjectIds {

    private static final String URN_UUID_PREFIX = "urn:uuid:";

    private CredentialObjectIds() {
    }

    /**
     * Create a new random credential object identifier.
     *
     * @return an identifier of the form {@code urn:uuid:<uuid>}
     */
    public static String newId() {
        return URN_UUID_PREFIX + UUID.randomUUID();
    }
}
