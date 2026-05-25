package it.eng.datatransfer.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.router.DataPlaneRouter;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.datatransfer.exceptions.DataPlaneClientException;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DataPlaneClientTest {

    @Mock
    private OkHttpClient mockHttpClient;
    @Mock
    private DataPlaneRouter mockRouter;

    private DataPlaneClient client;

    @BeforeEach
    public void setUp() {
        client = new DataPlaneClient(mockHttpClient, new ObjectMapper(), mockRouter);
    }

    private DataPlaneRegistration registrationWithApiKey(String endpoint, String apiKey) {
        return DataPlaneRegistration.Builder.newInstance()
                .endpoint(endpoint)
                .apiKey(apiKey)
                .supportedTransferTypes(Set.of("HttpData-PULL"))
                .build();
    }

    private DataPlaneRegistration registrationNoApiKey(String endpoint) {
        return DataPlaneRegistration.Builder.newInstance()
                .endpoint(endpoint)
                .supportedTransferTypes(Set.of("HttpData-PULL"))
                .build();
    }

    private Call stubCall(int httpCode) throws IOException {
        return stubCall(httpCode, "");
    }

    private Call stubCallWithBody(int httpCode, String body) throws IOException {
        return stubCall(httpCode, body);
    }

    private Call stubCall(int httpCode, String body) throws IOException {
        Call mockCall = mock(Call.class);
        Response fakeResponse = new Response.Builder()
                .request(new Request.Builder().url("http://dp:9090/dataflows/start").build())
                .protocol(Protocol.HTTP_1_1)
                .code(httpCode).message("OK")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build();
        when(mockCall.execute()).thenReturn(fakeResponse);
        return mockCall;
    }

    @Test
    @DisplayName("startSendsPostToDataPlaneWithApiKey - verifies URL and X-Api-Key header")
    public void startSendsPostToDataPlaneWithApiKey() throws IOException {
        DataPlaneRegistration dp = registrationWithApiKey("http://dp:9090", "secret-key");
        when(mockRouter.selectDataPlane("HttpData-PULL")).thenReturn(Optional.of(dp));
        Call mockCall = stubCall(200);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        DataFlowStartMessage msg = DataFlowStartMessage.Builder.newInstance()
                .processId("proc-1")
                .transferType("HttpData-PULL")
                .build();

        client.start(msg);

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockHttpClient).newCall(captor.capture());
        Request captured = captor.getValue();
        assertTrue(captured.url().toString().contains("/dataflows/start"));
        assertEquals("secret-key", captured.header("X-Api-Key"));
    }

    @Test
    @DisplayName("prepareSendsPostToDataPlane - verifies URL contains /dataflows/prepare")
    public void prepareSendsPostToDataPlane() throws IOException {
        DataPlaneRegistration dp = registrationNoApiKey("http://dp:9090");
        when(mockRouter.selectDataPlane("HttpData-PULL")).thenReturn(Optional.of(dp));
        String responseBody = "{\"processId\":\"proc-2\",\"dataAddress\":{\"presignedUrl\":\"https://example.com/obj\"}}";
        Call mockCall = stubCallWithBody(200, responseBody);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        DataFlowPrepareMessage msg = DataFlowPrepareMessage.Builder.newInstance()
                .processId("proc-2")
                .build();

        client.prepare(msg, "HttpData-PULL");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockHttpClient).newCall(captor.capture());
        assertTrue(captor.getValue().url().toString().contains("/dataflows/prepare"));
    }

    @Test
    @DisplayName("startThrowsWhenNoDataPlaneRegistered - router returns empty, expect IllegalStateException")
    public void startThrowsWhenNoDataPlaneRegistered() {
        when(mockRouter.selectDataPlane("HttpData-PULL")).thenReturn(Optional.empty());

        DataFlowStartMessage msg = DataFlowStartMessage.Builder.newInstance()
                .processId("proc-3")
                .transferType("HttpData-PULL")
                .build();

        assertThrows(IllegalStateException.class, () -> client.start(msg));
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    @DisplayName("terminateSendsDeleteToDataPlane - verifies DELETE method and canonical URL contains processId/terminate")
    public void terminateSendsDeleteToDataPlane() throws IOException {
        DataPlaneRegistration dp = registrationWithApiKey("http://dp:9090", "secret-key");
        when(mockRouter.selectDataPlane("HttpData-PULL")).thenReturn(Optional.of(dp));
        Call mockCall = stubCall(200);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        client.terminate("proc-term-1", "HttpData-PULL");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockHttpClient).newCall(captor.capture());
        Request captured = captor.getValue();
        assertEquals("DELETE", captured.method());
        assertTrue(captured.url().toString().contains("/dataflows/proc-term-1/terminate"));
    }

    @Test
    @DisplayName("terminateThrowsWhenNoDataPlaneRegistered - router returns empty, expect IllegalStateException")
    public void terminateThrowsWhenNoDataPlaneRegistered() {
        when(mockRouter.selectDataPlane("HttpData-PULL")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> client.terminate("proc-term-2", "HttpData-PULL"));
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    @DisplayName("postThrowsDataPlaneClientExceptionOnIOFailure - IOException wrapped in DataPlaneClientException")
    public void postThrowsDataPlaneClientExceptionOnIOFailure() throws IOException {
        DataPlaneRegistration dp = registrationNoApiKey("http://dp:9090");
        when(mockRouter.selectDataPlane("HttpData-PULL")).thenReturn(Optional.of(dp));
        Call mockCall = mock(Call.class);
        when(mockCall.execute()).thenThrow(new IOException("Connection refused"));
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        DataFlowStartMessage msg = DataFlowStartMessage.Builder.newInstance()
                .processId("proc-fail")
                .transferType("HttpData-PULL")
                .build();

        assertThrows(DataPlaneClientException.class, () -> client.start(msg));
    }

    @Test
    @DisplayName("terminateThrowsDataPlaneClientExceptionOnIOFailure - IOException wrapped in DataPlaneClientException")
    void terminateThrowsDataPlaneClientExceptionOnIOFailure() throws IOException {
        DataPlaneRegistration dp = registrationNoApiKey("http://dp:9090");
        when(mockRouter.selectDataPlane("HttpData-PULL")).thenReturn(Optional.of(dp));
        Call mockCall = mock(Call.class);
        when(mockCall.execute()).thenThrow(new IOException("connection refused"));
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        assertThrows(DataPlaneClientException.class,
            () -> client.terminate("proc-1", "HttpData-PULL"));
    }

    @Test
    @DisplayName("suspendSendsPostToDataPlane - verifies POST method and URL contains processId/suspend")
    public void suspendSendsPostToDataPlane() throws IOException {
        DataPlaneRegistration dp = registrationWithApiKey("http://dp:9090", "secret-key");
        when(mockRouter.selectDataPlane("HttpData-PULL")).thenReturn(Optional.of(dp));
        Call mockCall = stubCall(200);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        client.suspend("proc-susp-1", "HttpData-PULL");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockHttpClient).newCall(captor.capture());
        Request captured = captor.getValue();
        assertEquals("POST", captured.method());
        assertTrue(captured.url().toString().contains("/dataflows/proc-susp-1/suspend"));
    }

    @Test
    @DisplayName("resumeSendsPostToDataPlane - verifies POST method and URL contains processId/resume")
    public void resumeSendsPostToDataPlane() throws IOException {
        DataPlaneRegistration dp = registrationNoApiKey("http://dp:9090");
        when(mockRouter.selectDataPlane("HttpData-PULL")).thenReturn(Optional.of(dp));
        Call mockCall = stubCall(200);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        client.resume("proc-res-1", "HttpData-PULL");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockHttpClient).newCall(captor.capture());
        Request captured = captor.getValue();
        assertEquals("POST", captured.method());
        assertTrue(captured.url().toString().contains("/dataflows/proc-res-1/resume"));
    }
}
