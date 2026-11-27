package it.eng.dcp.issuer.service;

import com.nimbusds.jose.jwk.ECKey;
import it.eng.dcp.common.config.DcpProperties;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantContextProvider;
import it.eng.dcp.common.participant.ParticipantKeyProvider;
import org.springframework.stereotype.Component;

/**
 * The single identity of the Credential Issuer: one DID and one key set shared by every holder.
 * Holders are counterparties identified by the data of their credential requests, not participants
 * of the Issuer. The identity is resolved once, from the {@code dcp.*} configuration, on first use,
 * so a node without a configured DID still starts.
 */
@Component
public class IssuerIdentity {

    private final ParticipantContextProvider contextProvider;
    private final ParticipantKeyProvider keyProvider;
    private final String participantId;

    private volatile ParticipantContext participant;

    /**
     * Creates the identity.
     *
     * @param contextProvider provider of the configured participant context
     * @param keyProvider     provider of the Issuer keys
     * @param properties      the DCP properties
     */
    public IssuerIdentity(ParticipantContextProvider contextProvider, ParticipantKeyProvider keyProvider,
                          DcpProperties properties) {
        this.contextProvider = contextProvider;
        this.keyProvider = keyProvider;
        this.participantId = properties.getParticipant().getId();
    }

    /**
     * Returns the participant context the shared services sign and validate tokens with.
     *
     * @return the Issuer participant context
     * @throws IllegalStateException if the Issuer is not configured
     */
    public ParticipantContext participant() {
        ParticipantContext result = participant;
        if (result == null) {
            synchronized (this) {
                if (participant == null) {
                    participant = contextProvider.findEnabled(participantId)
                            .orElseThrow(() -> new IllegalStateException("Issuer identity is not configured"));
                }
                result = participant;
            }
        }
        return result;
    }

    /**
     * Returns the Issuer DID.
     *
     * @return the DID
     */
    public String did() {
        return participant().did();
    }

    /**
     * Returns the active signing key of the Issuer, including its private part.
     *
     * @return the signing key
     */
    public ECKey signingKey() {
        return keyProvider.activeSigningKey(participant());
    }

    /**
     * Rotates the signing key of the Issuer.
     *
     * @return the alias of the new signing key
     */
    public String rotateKey() {
        return keyProvider.rotate(participant());
    }
}
