package it.eng.dataplane.core.startup;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.core.config.DataPlaneProperties;
import it.eng.dataplane.core.model.DataPlaneAuditEventType;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import it.eng.dataplane.core.service.DataPlaneAuditEventService;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for ControlPlaneRegistrationBean retry logic.
 * Overrides sleep to avoid real delays in tests.
 */
@ExtendWith(MockitoExtension.class)
class ControlPlaneRegistrationBeanTest {

    @Mock
    private DataPlaneProperties properties;

    @Mock
    private DataTransferProtocolRegistry registry;

    @Mock
    private OkHttpClient okHttpClient;

    @Mock
    private Call call;

    @Mock
    private DataPlaneAuditEventService auditEventService;

    private ControlPlaneRegistrationBean bean;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        bean = spy(new ControlPlaneRegistrationBean(properties, registry, okHttpClient, objectMapper, auditEventService) {
            @Override
            protected void sleep(long ms) {
                // no-op in tests to avoid delays
            }
        });
    }

    private Response okResponse() {
        return new Response.Builder()
            .request(new okhttp3.Request.Builder().url("http://test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(ResponseBody.create("", null))
            .build();
    }

    private Response failResponse(int code) {
        return new Response.Builder()
            .request(new okhttp3.Request.Builder().url("http://test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Error")
            .body(ResponseBody.create("", null))
            .build();
    }

    @Test
    void registersSuccessfullyOnFirstAttempt() throws IOException {
        when(properties.getControlPlaneAdminEndpoint()).thenReturn("http://cp:8080");
        when(properties.getEndpoint()).thenReturn("http://dp:9090");
        when(properties.getId()).thenReturn("dp-test-id");
        when(registry.getSupportedProtocols()).thenReturn(Set.of("HttpData-PULL"));
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(okResponse());

        bean.onApplicationEvent(null);

        verify(okHttpClient, times(1)).newCall(any());
    }

    @Test
    void retriesOnIoExceptionThenSucceeds() throws IOException {
        when(properties.getControlPlaneAdminEndpoint()).thenReturn("http://cp:8080");
        when(properties.getEndpoint()).thenReturn("http://dp:9090");
        when(properties.getId()).thenReturn("dp-test-id");
        when(registry.getSupportedProtocols()).thenReturn(Set.of("HttpData-PULL"));
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute())
            .thenThrow(new IOException("timeout"))
            .thenReturn(okResponse());

        bean.onApplicationEvent(null);

        verify(okHttpClient, times(2)).newCall(any());
    }

    @Test
    void skipsRegistrationWhenEndpointNotConfigured() {
        when(properties.getControlPlaneAdminEndpoint()).thenReturn(null);

        bean.onApplicationEvent(null);

        verifyNoInteractions(okHttpClient);
    }

    @Test
    void skipsRegistrationWhenEndpointIsBlank() {
        when(properties.getControlPlaneAdminEndpoint()).thenReturn("   ");

        bean.onApplicationEvent(null);

        verifyNoInteractions(okHttpClient);
    }

    @Test
    void retriesOnHttpErrorThenSucceeds() throws IOException {
        when(properties.getControlPlaneAdminEndpoint()).thenReturn("http://cp:8080");
        when(properties.getEndpoint()).thenReturn("http://dp:9090");
        when(properties.getId()).thenReturn("dp-test-id");
        when(registry.getSupportedProtocols()).thenReturn(Set.of("HttpData-PULL"));
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute())
            .thenReturn(failResponse(500))
            .thenReturn(okResponse());

        bean.onApplicationEvent(null);

        verify(okHttpClient, times(2)).newCall(any());
    }

    @Test
    void retriesMultipleTimesBeforeFinalFailure() throws IOException {
        when(properties.getControlPlaneAdminEndpoint()).thenReturn("http://cp:8080");
        when(properties.getEndpoint()).thenReturn("http://dp:9090");
        when(properties.getId()).thenReturn("dp-test-id");
        when(registry.getSupportedProtocols()).thenReturn(Set.of("HttpData-PULL"));
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute())
            .thenThrow(new IOException("timeout"))
            .thenThrow(new IOException("timeout"))
            .thenReturn(failResponse(503))
            .thenReturn(failResponse(503))
            .thenReturn(failResponse(503));

        bean.onApplicationEvent(null);

        verify(okHttpClient, times(5)).newCall(any());
    }

    @Test
    @DisplayName("deregisterFromControlPlane sends DELETE and logs success")
    void deregistersSuccessfully() throws IOException {
        when(properties.getControlPlaneAdminEndpoint()).thenReturn("http://cp:8080");
        when(properties.getId()).thenReturn("dp-test-id");
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(okResponse());

        bean.deregisterFromControlPlane();

        verify(okHttpClient, times(1)).newCall(any());
        verify(auditEventService).saveEvent(
                eq(DataPlaneAuditEventType.DP_DEREGISTRATION_SUCCESS),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("deregisterFromControlPlane logs warning on HTTP error but does not throw")
    void deregistrationLogsWarningOnHttpError() throws IOException {
        when(properties.getControlPlaneAdminEndpoint()).thenReturn("http://cp:8080");
        when(properties.getId()).thenReturn("dp-test-id");
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(failResponse(404));

        bean.deregisterFromControlPlane();

        verify(auditEventService).saveEvent(
                eq(DataPlaneAuditEventType.DP_DEREGISTRATION_FAILED),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("deregisterFromControlPlane logs warning on IOException but does not throw")
    void deregistrationLogsWarningOnIoException() throws IOException {
        when(properties.getControlPlaneAdminEndpoint()).thenReturn("http://cp:8080");
        when(properties.getId()).thenReturn("dp-test-id");
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("connection refused"));

        bean.deregisterFromControlPlane();

        verify(auditEventService).saveEvent(
                eq(DataPlaneAuditEventType.DP_DEREGISTRATION_FAILED),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("deregisterFromControlPlane skips when controlPlaneAdminEndpoint is not configured")
    void deregistrationSkipsWhenEndpointNotConfigured() {
        when(properties.getControlPlaneAdminEndpoint()).thenReturn(null);

        bean.deregisterFromControlPlane();

        verifyNoInteractions(okHttpClient);
    }
}
