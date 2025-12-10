package it.eng.dcp.service;

import it.eng.dcp.exception.InsecureEndpointException;
import it.eng.dcp.exception.RemoteHolderAuthException;
import it.eng.dcp.exception.RemoteHolderClientException;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DcpVerifierClientTest {

    @Mock
    private OkHttpRestClient okHttpRestClient;

    private DcpVerifierClient client;

    @BeforeEach
    void setup() {
        // keep ObjectMapper local to silence IDE warning (field can be local)
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        client = new DcpVerifierClient(okHttpRestClient, mapper);
    }

    @Test
    void fetchPresentations_success_returnsPresentation() {
        String json = "{\"presentation\": []}";
        doReturn(GenericApiResponse.success(json, "ok"))
                .when(okHttpRestClient).sendRequestProtocol(ArgumentMatchers.anyString(), ArgumentMatchers.<com.fasterxml.jackson.databind.JsonNode>any(), ArgumentMatchers.isNull());

        var result = client.fetchPresentations("https://holder.example", List.of("SomeType"), null);
        assertNotNull(result);
        assertNotNull(result.getPresentation());
        assertTrue(result.getPresentation().isEmpty());

        verify(okHttpRestClient, times(1)).sendRequestProtocol(ArgumentMatchers.anyString(), ArgumentMatchers.<com.fasterxml.jackson.databind.JsonNode>any(), ArgumentMatchers.isNull());
    }

    @Test
    void fetchPresentations_unauthorized_throwsRemoteHolderAuthException() {
        doReturn(GenericApiResponse.error("401 Unauthorized"))
                .when(okHttpRestClient).sendRequestProtocol(ArgumentMatchers.anyString(), ArgumentMatchers.<com.fasterxml.jackson.databind.JsonNode>any(), ArgumentMatchers.isNull());

        assertThrows(RemoteHolderAuthException.class, () -> client.fetchPresentations("https://holder.example", List.of("t"), null));

        verify(okHttpRestClient, times(1)).sendRequestProtocol(ArgumentMatchers.anyString(), ArgumentMatchers.<com.fasterxml.jackson.databind.JsonNode>any(), ArgumentMatchers.isNull());
    }

    @Test
    void fetchPresentations_clientError_throwsRemoteHolderClientException() {
        doReturn(GenericApiResponse.error("400 Bad Request"))
                .when(okHttpRestClient).sendRequestProtocol(ArgumentMatchers.anyString(), ArgumentMatchers.<com.fasterxml.jackson.databind.JsonNode>any(), ArgumentMatchers.isNull());

        assertThrows(RemoteHolderClientException.class, () -> client.fetchPresentations("https://holder.example", List.of("t"), null));

        verify(okHttpRestClient, times(1)).sendRequestProtocol(ArgumentMatchers.anyString(), ArgumentMatchers.<com.fasterxml.jackson.databind.JsonNode>any(), ArgumentMatchers.isNull());
    }

    @Test
    void fetchPresentations_transientFailure_thenSuccess_retriesAndReturns() {
        String json = "{\"presentation\": []}";
        doThrow(new RuntimeException("network error"))
                .doReturn(GenericApiResponse.success(json, "ok"))
                .when(okHttpRestClient).sendRequestProtocol(ArgumentMatchers.anyString(), ArgumentMatchers.<com.fasterxml.jackson.databind.JsonNode>any(), ArgumentMatchers.isNull());

        var result = client.fetchPresentations("https://holder.example", List.of("SomeType"), null);
        assertNotNull(result);
        assertTrue(result.getPresentation().isEmpty());

        verify(okHttpRestClient, atLeast(2)).sendRequestProtocol(ArgumentMatchers.anyString(), ArgumentMatchers.<com.fasterxml.jackson.databind.JsonNode>any(), ArgumentMatchers.isNull());
    }

    @Test
    void fetchPresentations_insecureEndpoint_throwsInsecureEndpointException() {
        assertThrows(InsecureEndpointException.class, () -> client.fetchPresentations("http://insecure.example", List.of("t"), null));
    }
}
