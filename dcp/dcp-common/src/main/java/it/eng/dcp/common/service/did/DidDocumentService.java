package it.eng.dcp.common.service.did;

import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.common.service.KeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for providing DID documents for both holder and issuer.
 * This service is shared between dcp (holder) and dcp-issuer modules.
 */
@Service
public class DidDocumentService {

    /** DID service type advertised by a Credential Issuer (DCP §6.2). */
    static final String ISSUER_SERVICE_TYPE = "IssuerService";

    private final KeyService keyService;

    /**
     * Constructor for DidDocumentService.
     *
     * @param keyService Service for key operations
     */
    @Autowired
    public DidDocumentService(KeyService keyService) {
        this.keyService = keyService;
    }

    /**
     * Provide a DID document based on the provided configuration.
     *
     * @param config Configuration for the DID document
     * @return DidDocument containing service endpoints and verification methods
     */
    public DidDocument provideDidDocument(DidDocumentConfig config) {
        // Load key pair with active alias ONCE (handles metadata and caching)
        KeyPair keyPair = keyService.loadKeyPairWithActiveAlias(config);

        String baseUrl = config.getEffectiveBaseUrl();

        // Build service entries
        List<ServiceEntry> services = config.getServiceEntries().stream()
                .map(serviceConfig -> new ServiceEntry(
                        serviceConfig.getId(),
                        serviceConfig.getType(),
                        serviceConfig.getFullEndpoint(baseUrl)
                ))
                .collect(Collectors.toList());

        // Compute kid and JWK using the loaded keyPair (no reloading!)
        String kid = keyService.getKidFromPublicKey(config, keyPair);
        Map<String, Object> jwk = keyService.convertPublicKeyToJWK(config, keyPair);

        String verificationMethodId = config.getDid() + "#" + kid;

        // Build verification methods
        List<VerificationMethod> verificationMethods = List.of(
                VerificationMethod.Builder.newInstance()
                        .id(verificationMethodId)
                        .type("JsonWebKey2020")
                        .controller(config.getEffectiveController())
                        .publicKeyJwk(jwk)
                        .build()
        );

        // DCP §4.3.3 verifies Self-Issued ID Tokens with a capabilityInvocation key, and VP proofs
        // with an authentication key. Only an Issuer signs Verifiable Credentials (assertionMethod).
        List<String> verificationMethodRef = List.of(verificationMethodId);
        DidDocument.Builder builder = DidDocument.Builder.newInstance()
                .id(config.getDid())
                .service(services)
                .verificationMethod(verificationMethods)
                .authentication(verificationMethodRef)
                .capabilityInvocation(verificationMethodRef);
        if (advertisesIssuerService(config)) {
            builder.assertionMethod(verificationMethodRef);
        }
        return builder.build();
    }

    /**
     * Tells whether the DID document belongs to a Credential Issuer, which is the case when it
     * advertises an {@code IssuerService} endpoint.
     *
     * @param config Configuration for the DID document
     * @return true if one of the service entries has type {@code IssuerService}
     */
    private boolean advertisesIssuerService(DidDocumentConfig config) {
        return config.getServiceEntries() != null && config.getServiceEntries().stream()
                .anyMatch(entry -> ISSUER_SERVICE_TYPE.equals(entry.getType()));
    }

    /**
     * Get the DID document as a JSON string based on the provided configuration.
     *
     * @param config Configuration for the DID document
     * @return JSON string representation of the DID document
     */
    public String getDidDocument(DidDocumentConfig config) {
        DidDocument didDocument = provideDidDocument(config);
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(didDocument);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize DID document", e);
        }
    }
}
