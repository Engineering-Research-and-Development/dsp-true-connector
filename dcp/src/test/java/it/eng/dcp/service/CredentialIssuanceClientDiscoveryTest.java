package it.eng.dcp.service;

import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.exception.IssuerServiceNotFoundException;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.common.model.IssuerMetadata.CredentialObject;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredentialIssuanceClientDiscoveryTest {

    @Mock
    private OkHttpRestClient restClient;

    @Mock
    private SelfIssuedIdTokenService tokenService;

    @InjectMocks
    private CredentialIssuanceClient client;

    private IssuerMetadata validIssuer(String issuer) {
        CredentialObject co = CredentialObject.Builder.newInstance()
                .id("cred1")
                .type("CredType")
                .credentialType("VerifiableCredential")
                .build();
        return IssuerMetadata.Builder.newInstance()
                .issuer(issuer)
                .credentialsSupported(List.of(co))
                .build();
    }

    @DisplayName("discoverIssuerService returns serviceEndpoint when DID document contains IssuerService entry")
    @Test
    void discoverIssuerService_ReturnsServiceEndpoint_WhenDIDHasIssuerService() {
        IssuerMetadata meta = validIssuer("did:web:issuer.example.com");
        String didJson = "{ \"service\": [ { \"id\": \"did:example:issuer#svc\", \"type\": \"IssuerService\", \"serviceEndpoint\": \"https://issuer.example.com\" } ] }";
        when(restClient.sendGETRequest("https://issuer.example.com/.well-known/did.json", null))
                .thenReturn(GenericApiResponse.success(didJson, "ok"));

        String svc = client.discoverIssuerService(meta);
        assertEquals("https://issuer.example.com", svc);
    }

    @DisplayName("discoverIssuerService throws when DID document missing IssuerService")
    @Test
    void discoverIssuerService_Throws_WhenDIDMissingService() {
        IssuerMetadata meta = validIssuer("did:web:issuer.example.com");
        String didJson = "{ \"service\": [ { \"id\": \"did:example:issuer#svc\", \"type\": \"OtherService\", \"serviceEndpoint\": \"https://x\" } ] }";
        when(restClient.sendGETRequest("https://issuer.example.com/.well-known/did.json", null))
                .thenReturn(GenericApiResponse.success(didJson, "ok"));

        assertThrows(IssuerServiceNotFoundException.class, () -> client.discoverIssuerService(meta));
    }
}
