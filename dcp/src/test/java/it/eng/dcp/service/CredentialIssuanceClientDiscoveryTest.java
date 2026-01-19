package it.eng.dcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.common.model.IssuerMetadata.CredentialObject;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.exception.IssuerServiceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredentialIssuanceClientDiscoveryTest {

    @Mock
    private SimpleOkHttpRestClient restClient;

    @Mock
    private SelfIssuedIdTokenService tokenService;

    @InjectMocks
    private CredentialIssuanceClient client;

    private IssuerMetadata validIssuer(String issuer) {
        CredentialObject co = CredentialObject.Builder.newInstance()
                .id("cred1")
                .credentialType("VerifiableCredential")
                .build();
        return IssuerMetadata.Builder.newInstance()
                .issuer(issuer)
                .credentialsSupported(List.of(co))
                .build();
    }

    @DisplayName("discoverIssuerService returns serviceEndpoint when DID document contains IssuerService entry")
    @Test
    void discoverIssuerService_ReturnsServiceEndpoint_WhenDIDHasIssuerService() throws IOException {
        IssuerMetadata meta = validIssuer("did:web:issuer.example.com");
        String didJson = "{ \"id\": \"did:web:issuer.example.com\", \"service\": [ { \"id\": \"did:example:issuer#svc\", \"type\": \"IssuerService\", \"serviceEndpoint\": \"https://issuer.example.com\" } ] }";

        DidDocument didDocument = new ObjectMapper().readValue(didJson, DidDocument.class);
        when(restClient.executeAndDeserialize(eq("https://issuer.example.com/.well-known/did.json"), eq("GET"), isNull(), isNull(), eq(DidDocument.class)))
                .thenReturn(didDocument);

        String svc = client.discoverIssuerService(meta);
        assertEquals("https://issuer.example.com", svc);
    }

    @DisplayName("discoverIssuerService throws when DID document missing IssuerService")
    @Test
    void discoverIssuerService_Throws_WhenDIDMissingService() throws IOException {
        IssuerMetadata meta = validIssuer("did:web:issuer.example.com");
        String didJson = "{ \"id\": \"did:web:issuer.example.com\", \"service\": [ { \"id\": \"did:example:issuer#svc\", \"type\": \"OtherService\", \"serviceEndpoint\": \"https://x\" } ] }";

        DidDocument didDocument = new ObjectMapper().readValue(didJson, DidDocument.class);
        when(restClient.executeAndDeserialize(eq("https://issuer.example.com/.well-known/did.json"), eq("GET"), isNull(), isNull(), eq(DidDocument.class)))
                .thenReturn(didDocument);

        assertThrows(IssuerServiceNotFoundException.class, () -> client.discoverIssuerService(meta));
    }
}
