package it.eng.dcp.service;

import it.eng.dcp.model.IssuerMetadata;
import it.eng.dcp.model.IssuerMetadata.CredentialObject;
import it.eng.dcp.model.CredentialRequestMessage;
import it.eng.tools.client.rest.OkHttpRestClient;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class CredentialIssuanceClientRequestTest {

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

    @DisplayName("requestCredential returns Location header when issuer returns 201 Created")
    @Test
    void requestCredential_ReturnsLocation_On201() {
        IssuerMetadata meta = validIssuer("https://issuer.example.com");

        Request req = new Request.Builder().url("https://issuer.example.com/credentials").build();
        ResponseBody body = ResponseBody.create(MediaType.parse("application/json"), "");
        Response response = new Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.CREATED.value())
                .message("Created")
                .addHeader(HttpHeaders.LOCATION, "https://issuer.example.com/requests/req123")
                .body(body)
                .build();

        when(restClient.executeCall(any(Request.class))).thenReturn(response);

        String loc = client.requestCredential(meta, "cred1", "holderPid");
        assertEquals("https://issuer.example.com/requests/req123", loc);
    }

    @DisplayName("requestCredential throws RuntimeException on 400 response")
    @Test
    void requestCredential_Throws_On400() {
        IssuerMetadata meta = validIssuer("https://issuer.example.com");

        Request req = new Request.Builder().url("https://issuer.example.com/credentials").build();
        ResponseBody body = ResponseBody.create(MediaType.parse("application/json"), "Bad request");
        Response response = new Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.BAD_REQUEST.value())
                .message("Bad request")
                .body(body)
                .build();

        when(restClient.executeCall(any(Request.class))).thenReturn(response);

        assertThrows(RuntimeException.class, () -> client.requestCredential(meta, "cred1", "holderPid"));
    }
}
