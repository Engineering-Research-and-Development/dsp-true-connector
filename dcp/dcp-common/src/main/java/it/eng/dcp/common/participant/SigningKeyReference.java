package it.eng.dcp.common.participant;

import jakarta.validation.ValidationException;

/**
 * Reference to the signing key of a participant. It carries no key material and no secret: the
 * {@link ParticipantKeyProvider} resolves it to the actual key.
 *
 * @param keystorePath location of the keystore holding the DCP key pair
 * @param alias        alias of the key entry the participant was configured with
 */
public record SigningKeyReference(String keystorePath, String alias) {

    /**
     * Creates a signing key reference.
     *
     * @throws ValidationException if the keystore path or the alias is blank
     */
    public SigningKeyReference {
        if (keystorePath == null || keystorePath.isBlank()) {
            throw new ValidationException("SigningKeyReference - keystorePath must not be blank");
        }
        if (alias == null || alias.isBlank()) {
            throw new ValidationException("SigningKeyReference - alias must not be blank");
        }
    }
}
