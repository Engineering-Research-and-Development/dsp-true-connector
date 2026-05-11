package it.eng.dataplane.core.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.config.DataPlaneProperties;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ControlPlaneClient}.
 */
@ExtendWith(MockitoExtension.class)
class ControlPlaneClientTest {

    @Mock
    private OkHttpClient okHttpClient;

    @Mock
    private Call call;

    private ObjectMapper objectMapper;
    private DataPlaneProperties properties;
    private ControlPlaneClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new DataPlaneProperties();
        client = new ControlPlaneClient(okHttpClient, objectMapper, properties);

        Response mockResponse = mock(Response.class);
        when(mockResponse.isSuccessful()).thenReturn(true);
        when(mockResponse.code()).thenReturn(200);
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        try {
            when(call.execute()).thenReturn(mockResponse);
        } catch (Exception e) {
            // Ignore
        }
    }

    @Test
    void sendsApiKeyHeaderInCallback() throws Exception {
        properties.setApiKey("test-api-key-value");
        client = new ControlPlaneClient(okHttpClient, objectMapper, properties);

        client.sendStatus("http://localhost:8080/callback", "process-123",
                DataFlowState.STARTED, null, null);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        Request capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest.header("X-Api-Key"));
        assertEquals("test-api-key-value", capturedRequest.header("X-Api-Key"));
    }

    @Test
    void doesNotSendApiKeyWhenNotConfigured() throws Exception {
        properties.setApiKey(null);
        client = new ControlPlaneClient(okHttpClient, objectMapper, properties);

        client.sendStatus("http://localhost:8080/callback", "process-123",
                DataFlowState.STARTED, null, null);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        Request capturedRequest = requestCaptor.getValue();
        assertNull(capturedRequest.header("X-Api-Key"));
    }

    @Test
    void doesNotSendApiKeyWhenBlank() throws Exception {
        properties.setApiKey("   ");
        client = new ControlPlaneClient(okHttpClient, objectMapper, properties);

        client.sendStatus("http://localhost:8080/callback", "process-123",
                DataFlowState.STARTED, null, null);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        Request capturedRequest = requestCaptor.getValue();
        assertNull(capturedRequest.header("X-Api-Key"));
    }

    @Test
    void routesCompletedStateToCompleteEndpoint() throws Exception {
        client.sendStatus("http://localhost:8080", "process-789",
                DataFlowState.COMPLETED, null, null);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        assertEquals("http://localhost:8080/api/v1/dataflows/complete",
                requestCaptor.getValue().url().toString());
    }

    @Test
    void routesTerminatedStateToErrorEndpoint() throws Exception {
        client.sendStatus("http://localhost:8080", "process-789",
                DataFlowState.TERMINATED, null, "something went wrong");

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        assertEquals("http://localhost:8080/api/v1/dataflows/error",
                requestCaptor.getValue().url().toString());
    }
}
