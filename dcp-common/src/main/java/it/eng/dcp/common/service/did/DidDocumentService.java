package it.eng.dcp.common.service.did;

import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.KeyMetadata;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.common.service.KeyMetadataService;
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
    private final KeyMetadataService keyMetadataService;

    /**
     * Constructor for DidDocumentService.
     *
     * @param keyService Service for key operations
     * @param keyMetadataService Service for key metadata operations
     */
    @Autowired
    public DidDocumentService(KeyService keyService,
                             KeyMetadataService keyMetadataService) {
        this.keyService = keyService;
        this.keyMetadataService = keyMetadataService;
    }

    /**
     * Provide a DID document based on the provided configuration.
     *
     * @param config Configuration for the DID document
     * @return DidDocument containing service endpoints and verification methods
     */
    public DidDocument provideDidDocument(DidDocumentConfig config) {
        // Get active key alias from metadata
        String activeAlias = keyMetadataService.getActiveKeyMetadata()
                .map(KeyMetadata::getAlias)
                .orElse("dsptrueconnector");

        // Load key pair for active alias
        keyService.loadKeyPairFromP12(config.getKeystorePath(), config.getKeystorePassword(), activeAlias);

        String baseUrl = config.getEffectiveBaseUrl();

        // Build service entries
        List<ServiceEntry> services = config.getServiceEntries().stream()
                .map(serviceConfig -> new ServiceEntry(
                        serviceConfig.getId(),
                        serviceConfig.getType(),
                        serviceConfig.getFullEndpoint(baseUrl)
                ))
                .collect(Collectors.toList());

        // Build verification methods
        List<VerificationMethod> verificationMethods = List.of(
                VerificationMethod.Builder.newInstance()
                        .id(config.getDid() + "#" + keyService.getKidFromPublicKey())
                        .type("JsonWebKey2020")
                        .controller(config.getEffectiveController())
                        .publicKeyJwk(keyService.convertPublicKeyToJWK())
                        .build()
        );

        return DidDocument.Builder.newInstance()
                .id(config.getDid())
                .service(services)
                .verificationMethod(verificationMethods)
                .build();
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

