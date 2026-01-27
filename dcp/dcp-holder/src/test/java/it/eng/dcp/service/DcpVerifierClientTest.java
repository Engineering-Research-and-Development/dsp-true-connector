package it.eng.dcp.service;

import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.holder.exception.InsecureEndpointException;
import it.eng.dcp.holder.exception.RemoteHolderAuthException;
import it.eng.dcp.holder.exception.RemoteHolderClientException;
import it.eng.dcp.holder.service.DcpVerifierClient;
import okhttp3.RequestBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DcpVerifierClientTest {

    @Mock
    private SimpleOkHttpRestClient okHttpRestClient;

    private DcpVerifierClient client;

    @BeforeEach
    void setup() {
        // keep ObjectMapper local to silence IDE warning (field can be local)
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        client = new DcpVerifierClient(okHttpRestClient, mapper);
    }

    @Test
    void fetchPresentations_success_returnsPresentation() throws Exception {
        PresentationResponseMessage mockResponse = PresentationResponseMessage.Builder.newInstance()
                .presentation(List.of())
                .build();

        when(okHttpRestClient.executeAndDeserialize(
                anyString(),
                eq("POST"),
                any(Map.class),
                any(RequestBody.class),
                eq(PresentationResponseMessage.class)))
                .thenReturn(mockResponse);

        var result = client.fetchPresentations("https://holder.example", List.of("SomeType"), null);
        assertNotNull(result);
        assertNotNull(result.getPresentation());
        assertTrue(result.getPresentation().isEmpty());

        verify(okHttpRestClient, times(1)).executeAndDeserialize(
                anyString(),
                eq("POST"),
                any(Map.class),
                any(RequestBody.class),
                eq(PresentationResponseMessage.class));
    }

    @Test
    void fetchPresentations_unauthorized_throwsRemoteHolderAuthException() throws Exception {
        when(okHttpRestClient.executeAndDeserialize(
                anyString(),
                eq("POST"),
                any(Map.class),
                any(RequestBody.class),
                eq(PresentationResponseMessage.class)))
                .thenThrow(new IOException("401 Unauthorized"));

        assertThrows(RemoteHolderAuthException.class, () -> client.fetchPresentations("https://holder.example", List.of("t"), null));

        verify(okHttpRestClient, times(1)).executeAndDeserialize(
                anyString(),
                eq("POST"),
                any(Map.class),
                any(RequestBody.class),
                eq(PresentationResponseMessage.class));
    }

    @Test
    void fetchPresentations_clientError_throwsRemoteHolderClientException() throws Exception {
        when(okHttpRestClient.executeAndDeserialize(
                anyString(),
                eq("POST"),
                any(Map.class),
                any(RequestBody.class),
                eq(PresentationResponseMessage.class)))
                .thenThrow(new IOException("400 Bad Request"));

        assertThrows(RemoteHolderClientException.class, () -> client.fetchPresentations("https://holder.example", List.of("t"), null));

        verify(okHttpRestClient, times(1)).executeAndDeserialize(
                anyString(),
                eq("POST"),
                any(Map.class),
                any(RequestBody.class),
                eq(PresentationResponseMessage.class));
    }

    @Test
    void fetchPresentations_transientFailure_thenSuccess_retriesAndReturns() throws Exception {
        PresentationResponseMessage mockResponse = PresentationResponseMessage.Builder.newInstance()
                .presentation(List.of())
                .build();

        when(okHttpRestClient.executeAndDeserialize(
                anyString(),
                eq("POST"),
                any(Map.class),
                any(RequestBody.class),
                eq(PresentationResponseMessage.class)))
                .thenThrow(new IOException("network error"))
                .thenReturn(mockResponse);

        var result = client.fetchPresentations("https://holder.example", List.of("SomeType"), null);
        assertNotNull(result);
        assertTrue(result.getPresentation().isEmpty());

        verify(okHttpRestClient, atLeast(2)).executeAndDeserialize(
                anyString(),
                eq("POST"),
                any(Map.class),
                any(RequestBody.class),
                eq(PresentationResponseMessage.class));
    }

    @Test
    void fetchPresentations_insecureEndpoint_throwsInsecureEndpointException() {
        assertThrows(InsecureEndpointException.class, () -> client.fetchPresentations("http://insecure.example", List.of("t"), null));
    }
}
