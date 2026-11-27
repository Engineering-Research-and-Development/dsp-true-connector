package it.eng.dcp.common.service.did;

import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantKeyProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for providing DID documents for both holder and issuer.
 * This service is shared between dcp (holder) and dcp-issuer modules.
 */
@Service
public class DidDocumentService {

    /** DID service type advertised by a Credential Issuer (DCP §6.2). */
    static final String ISSUER_SERVICE_TYPE = "IssuerService";

    private final ParticipantKeyProvider keyProvider;

    /**
     * Constructor for DidDocumentService.
     *
     * @param keyProvider provider of the keys the DID document publishes
     */
    @Autowired
    public DidDocumentService(ParticipantKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    /**
     * Provide the DID document of a participant. The verification methods are the participant's published
     * keys: the active key plus the keys retired within the grace period.
     *
     * @param participant the participant the DID document belongs to
     * @return DidDocument containing service endpoints and verification methods
     */
    public DidDocument provideDidDocument(ParticipantContext participant) {
        List<ServiceEntry> services = participant.serviceEntries();

        List<VerificationMethod> verificationMethods = keyProvider.publishedKeys(participant).stream()
                .map(key -> VerificationMethod.Builder.newInstance()
                        .id(participant.did() + "#" + key.kid())
                        .type("JsonWebKey2020")
                        .controller(participant.did())
                        .publicKeyJwk(key.jwk())
                        .build())
                .toList();

        // DCP §4.3.3 verifies Self-Issued ID Tokens with a capabilityInvocation key, and VP proofs
        // with an authentication key. Only an Issuer signs Verifiable Credentials (assertionMethod).
        List<String> verificationMethodRefs = verificationMethods.stream().map(VerificationMethod::getId).toList();
        DidDocument.Builder builder = DidDocument.Builder.newInstance()
                .id(participant.did())
                .service(services)
                .verificationMethod(verificationMethods)
                .authentication(verificationMethodRefs)
                .capabilityInvocation(verificationMethodRefs);
        if (advertisesIssuerService(participant)) {
            builder.assertionMethod(verificationMethodRefs);
        }
        return builder.build();
    }

    /**
     * Tells whether the DID document belongs to a Credential Issuer, which is the case when it
     * advertises an {@code IssuerService} endpoint.
     *
     * @param participant the participant the DID document belongs to
     * @return true if one of the service entries has type {@code IssuerService}
     */
    private boolean advertisesIssuerService(ParticipantContext participant) {
        return participant.serviceEntries().stream()
                .anyMatch(entry -> ISSUER_SERVICE_TYPE.equals(entry.type()));
    }

    /**
     * Get the DID document as a JSON string based on the provided configuration.
     *
     * @param participant the participant the DID document belongs to
     * @return JSON string representation of the DID document
     */
    public String getDidDocument(ParticipantContext participant) {
        DidDocument didDocument = provideDidDocument(participant);
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(didDocument);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize DID document", e);
        }
    }
}
