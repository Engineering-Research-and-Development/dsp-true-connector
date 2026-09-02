package it.eng.dataplane.core.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.config.DataPlaneProperties;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

    // ── API-key header behavior ────────────────────────────────────────────────

    @Test
    @DisplayName("sendStatus includes X-Api-Key header when configured")
    void sendsApiKeyHeaderInCallback() throws Exception {
        properties.setApiKey("test-api-key-value");
        client = new ControlPlaneClient(okHttpClient, objectMapper, properties);

        client.sendStatus("http://localhost:8080", "process-123",
                DataFlowState.COMPLETED, null, null);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        assertNotNull(requestCaptor.getValue().header("X-Api-Key"));
        assertEquals("test-api-key-value", requestCaptor.getValue().header("X-Api-Key"));
    }

    @Test
    @DisplayName("sendStatus omits X-Api-Key when not configured")
    void doesNotSendApiKeyWhenNotConfigured() throws Exception {
        properties.setApiKey(null);
        client = new ControlPlaneClient(okHttpClient, objectMapper, properties);

        client.sendStatus("http://localhost:8080", "process-123",
                DataFlowState.COMPLETED, null, null);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        assertNull(requestCaptor.getValue().header("X-Api-Key"));
    }

    @Test
    @DisplayName("sendStatus omits X-Api-Key when blank")
    void doesNotSendApiKeyWhenBlank() throws Exception {
        properties.setApiKey("   ");
        client = new ControlPlaneClient(okHttpClient, objectMapper, properties);

        client.sendStatus("http://localhost:8080", "process-123",
                DataFlowState.COMPLETED, null, null);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        assertNull(requestCaptor.getValue().header("X-Api-Key"));
    }

    // ── sendStatus routing ─────────────────────────────────────────────────────

    @Test
    @DisplayName("sendStatus with COMPLETED routes to canonical completed endpoint")
    void routesCompletedStateToCanonicalCompletedEndpoint() throws Exception {
        client.sendStatus("http://localhost:8080", "process-789",
                DataFlowState.COMPLETED, null, null);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        assertEquals("http://localhost:8080/api/v1/transfers/process-789/dataflow/completed",
                requestCaptor.getValue().url().toString());
    }

    @Test
    @DisplayName("sendStatus with TERMINATED routes to canonical errored endpoint")
    void routesTerminatedStateToCanonicalErroredEndpoint() throws Exception {
        client.sendStatus("http://localhost:8080", "process-789",
                DataFlowState.TERMINATED, null, "something went wrong");

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        assertEquals("http://localhost:8080/api/v1/transfers/process-789/dataflow/errored",
                requestCaptor.getValue().url().toString());
    }

    // ── Explicit canonical methods ─────────────────────────────────────────────

    @Test
    @DisplayName("sendCompleted targets canonical completed endpoint")
    void sendCompletedTargetsCanonicalCompletedEndpoint() throws Exception {
        client.sendCompleted("http://connector:8080", "tp-1", null);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        assertEquals("http://connector:8080/api/v1/transfers/tp-1/dataflow/completed",
                requestCaptor.getValue().url().toString());
    }

    @Test
    @DisplayName("sendErrored targets canonical errored endpoint")
    void sendErroredTargetsCanonicalErroredEndpoint() throws Exception {
        client.sendErrored("http://connector:8080", "tp-1", "failed");

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        assertEquals("http://connector:8080/api/v1/transfers/tp-1/dataflow/errored",
                requestCaptor.getValue().url().toString());
    }

    @Test
    @DisplayName("sendStarted targets canonical started endpoint")
    void sendStartedTargetsCanonicalStartedEndpoint() throws Exception {
        client.sendStarted("http://connector:8080", "tp-1", null);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        assertEquals("http://connector:8080/api/v1/transfers/tp-1/dataflow/started",
                requestCaptor.getValue().url().toString());
    }

    @Test
    @DisplayName("sendPrepared targets canonical prepared endpoint")
    void sendPreparedTargetsCanonicalPreparedEndpoint() throws Exception {
        client.sendPrepared("http://connector:8080", "tp-1", null);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        assertEquals("http://connector:8080/api/v1/transfers/tp-1/dataflow/prepared",
                requestCaptor.getValue().url().toString());
    }

    @Test
    @DisplayName("sendCompleted includes X-Api-Key header when configured")
    void sendCompletedIncludesApiKeyHeader() throws Exception {
        properties.setApiKey("my-key");
        client = new ControlPlaneClient(okHttpClient, objectMapper, properties);

        client.sendCompleted("http://connector:8080", "tp-1", null);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newCall(requestCaptor.capture());

        assertEquals("my-key", requestCaptor.getValue().header("X-Api-Key"));
    }
}

