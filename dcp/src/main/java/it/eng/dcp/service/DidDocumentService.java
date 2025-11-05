package it.eng.dcp.service;

import it.eng.dcp.model.DidDocument;
import it.eng.dcp.model.ServiceEntry;
import it.eng.dcp.model.VerificationMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DidDocumentService {

    private final KeyService keyService;
    private final KeyMetadataService keyMetadataService;

    @Autowired
    public DidDocumentService(KeyService keyService, KeyMetadataService keyMetadataService) {
        this.keyService = keyService;
        this.keyMetadataService = keyMetadataService;
    }

    public DidDocument provideDidDocument() {
        // Get active key alias from metadata
        String activeAlias = keyMetadataService.getActiveKeyMetadata().map(m -> m.getAlias()).orElse("dsptrueconnector");
        // Load key pair for active alias
        keyService.loadKeyPairFromP12("eckey.p12", "password", activeAlias);

        String did = "did:web:localhost%3A8083:holder";
        String baseEndpoint = "http://localhost:8080";
        String serviceId = "TRUEConnector-Credential-Service";
        return DidDocument.Builder.newInstance()
                .id(did)
                .service(List.of(
                        new ServiceEntry(serviceId, "CredentialService", baseEndpoint)
                ))
                .verificationMethod(List.of(
                        VerificationMethod.Builder.newInstance()
                                .id(did + "#" + keyService.getKidFromPublicKey())
                                .type("JsonWebKey2020")
                                .controller("did:web:localhost%3A8083:holder")
                                .publicKeyJwk(keyService.convertPublicKeyToJWK())
                                .build()
                ))
                .build();
    }
}
