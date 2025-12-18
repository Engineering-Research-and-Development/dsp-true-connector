package it.eng.dcp.service;

import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.model.VerificationMethod;
import it.eng.dcp.common.service.KeyMetadataService;
import it.eng.dcp.common.service.KeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DidDocumentService {

    private final KeyService keyService;
    private final KeyMetadataService keyMetadataService;
    private final boolean sslEnabled;
    private final String serverPort;

    @Autowired
    public DidDocumentService(KeyService keyService,
                             KeyMetadataService keyMetadataService,
                             @Value("${server.ssl.enabled:false}") boolean sslEnabled,
                             @Value("${server.port}") String serverPort) {
        this.keyService = keyService;
        this.keyMetadataService = keyMetadataService;
        this.sslEnabled = sslEnabled;
        this.serverPort = serverPort;
    }

    public DidDocument provideDidDocument() {
        // Get active key alias from metadata
        String activeAlias = keyMetadataService.getActiveKeyMetadata().map(m -> m.getAlias()).orElse("dsptrueconnector");
        // Load key pair for active alias
        keyService.loadKeyPairFromP12("eckey.p12", "password", activeAlias);

        String did = "did:web:localhost%3A8083:holder";

        // Construct baseEndpoint based on SSL configuration
        String protocol = sslEnabled ? "https" : "http";
        String baseEndpoint = protocol + "://localhost:" + serverPort;

        String serviceId = "TRUEConnector-Credential-Service";
        return DidDocument.Builder.newInstance()
                .id(did)
                .service(List.of(
                        new ServiceEntry(serviceId, "CredentialService", baseEndpoint),
                        new ServiceEntry(serviceId, "IssuerService", baseEndpoint + "/issuer")
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
