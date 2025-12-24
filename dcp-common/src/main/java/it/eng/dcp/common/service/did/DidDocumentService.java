package it.eng.dcp.common.service.did;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.common.service.KeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for providing DID documents for both holder and issuer.
 * This service is shared between dcp (holder) and dcp-issuer modules.
 */
@Service
public class DidDocumentService {

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
    public DidDocument provideDidDocument(BaseDidDocumentConfiguration config) {
        DidDocumentConfig didConfig = config.getDidDocumentConfig();
        // Load key pair with active alias (handles metadata and caching)
        keyService.loadKeyPairWithActiveAlias(didConfig);

        String baseUrl = didConfig.getEffectiveBaseUrl();

        // Build service entries
        List<ServiceEntry> services = didConfig.getServiceEntries().stream()
                .map(serviceConfig -> new ServiceEntry(
                        serviceConfig.getId(),
                        serviceConfig.getType(),
                        serviceConfig.getFullEndpoint(baseUrl)
                ))
                .collect(Collectors.toList());

        // Build verification methods
        List<VerificationMethod> verificationMethods = List.of(
                VerificationMethod.Builder.newInstance()
                        .id(didConfig.getDid() + "#" + keyService.getKidFromPublicKey(didConfig))
                        .type("JsonWebKey2020")
                        .controller(didConfig.getEffectiveController())
                        .publicKeyJwk(keyService.convertPublicKeyToJWK(didConfig))
                        .build()
        );
        String verificationMethodId = verificationMethods.get(0).getId();

        return DidDocument.Builder.newInstance()
                .id(didConfig.getDid())
                .service(services)
                .verificationMethod(verificationMethods)
                .capabilityInvocation(List.of(verificationMethodId))
                .build();
    }

    /**
     * Get the DID document as a JSON string based on the provided configuration.
     *
     * @param config Configuration for the DID document
     * @return JSON string representation of the DID document
     */
    public String getDidDocument(BaseDidDocumentConfiguration config) {
        DidDocument didDocument = provideDidDocument(config);
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(didDocument);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize DID document", e);
        }
    }
}
