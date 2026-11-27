package it.eng.dcp.common.participant;

import com.nimbusds.jose.jwk.ECKey;

import java.util.List;
import java.util.Map;

/**
 * Supplies the keys of a participant. Signing, DID document generation and key rotation always go through
 * this provider. DCP keys are separate from TLS keys.
 */
public interface ParticipantKeyProvider {

    /**
     * A public verification key a participant publishes in its DID document.
     *
     * @param kid    key identifier, the thumbprint-style {@code kid} of the key
     * @param jwk    public key as JWK parameters, with the {@code kid} qualified by the participant DID
     * @param active {@code true} for the current signing key, {@code false} for a retired key in its grace period
     */
    record PublishedKey(String kid, Map<String, Object> jwk, boolean active) {
    }

    /**
     * Returns the active signing key, including its private part.
     *
     * @param participant the participant
     * @return the signing key
     * @throws IllegalArgumentException if the participant is not served by this provider
     */
    ECKey activeSigningKey(ParticipantContext participant);

    /**
     * Returns the public keys to publish: the active key plus the keys retired within the grace period.
     * The active key is the first element.
     *
     * @param participant the participant
     * @return the keys to publish
     * @throws IllegalArgumentException if the participant is not served by this provider
     */
    List<PublishedKey> publishedKeys(ParticipantContext participant);

    /**
     * Rotates the signing key. The previous public key stays published for the grace period.
     *
     * @param participant the participant
     * @return the alias of the new signing key
     * @throws IllegalArgumentException if the participant is not served by this provider
     */
    String rotate(ParticipantContext participant);
}
