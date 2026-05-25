package it.eng.dataplane.core.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.config.DataPlaneProperties;
import it.eng.dataplane.core.DataPlaneApiEndpoints;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP client that sends DPS status callbacks from Data Plane to Control Plane.
 * Uses the shared {@link OkHttpClient} bean from {@code OkHttpClientConfiguration} in tools,
 * which supports TLS with custom truststore or an insecure noop client for development.
 */
@Slf4j
@Component
public class ControlPlaneClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final DataPlaneProperties properties;

    /**
     * @param okHttpClient TLS-aware HTTP client from OkHttpClientConfiguration
     * @param objectMapper shared Jackson mapper
     * @param properties Data Plane configuration properties
     */
    public ControlPlaneClient(OkHttpClient okHttpClient, ObjectMapper objectMapper, DataPlaneProperties properties) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Sends a prepared callback to the Control Plane canonical endpoint.
     * Routes to {@code /api/v1/transfers/{processId}/dataflow/prepared}.
     *
     * @param callbackBaseAddress base CP URL (e.g. {@code http://connector:8080})
     * @param processId           the TransferProcess ID on the CP
     * @param dataAddress         optional data address map
     */
    public void sendPrepared(String callbackBaseAddress, String processId, Map<String, String> dataAddress) {
        String url = callbackBaseAddress
                + String.format(DataPlaneApiEndpoints.DATAFLOW_CALLBACK_PREPARED_TEMPLATE, processId);
        send(url, processId, DataFlowState.PREPARED, dataAddress, null);
    }

    /**
     * Sends a started callback to the Control Plane canonical endpoint.
     * Routes to {@code /api/v1/transfers/{processId}/dataflow/started}.
     *
     * @param callbackBaseAddress base CP URL (e.g. {@code http://connector:8080})
     * @param processId           the TransferProcess ID on the CP
     * @param dataAddress         optional data address map
     */
    public void sendStarted(String callbackBaseAddress, String processId, Map<String, String> dataAddress) {
        String url = callbackBaseAddress
                + String.format(DataPlaneApiEndpoints.DATAFLOW_CALLBACK_STARTED_TEMPLATE, processId);
        send(url, processId, DataFlowState.STARTED, dataAddress, null);
    }

    /**
     * Sends a completed callback to the Control Plane canonical endpoint.
     * Routes to {@code /api/v1/transfers/{processId}/dataflow/completed}.
     *
     * @param callbackBaseAddress base CP URL (e.g. {@code http://connector:8080})
     * @param processId           the TransferProcess ID on the CP
     * @param dataAddress         optional data address map
     */
    public void sendCompleted(String callbackBaseAddress, String processId, Map<String, String> dataAddress) {
        String url = callbackBaseAddress
                + String.format(DataPlaneApiEndpoints.DATAFLOW_CALLBACK_COMPLETED_TEMPLATE, processId);
        send(url, processId, DataFlowState.COMPLETED, dataAddress, null);
    }

    /**
     * Sends an errored callback to the Control Plane canonical endpoint.
     * Routes to {@code /api/v1/transfers/{processId}/dataflow/errored}.
     *
     * @param callbackBaseAddress base CP URL (e.g. {@code http://connector:8080})
     * @param processId           the TransferProcess ID on the CP
     * @param errorMessage        optional error message for TERMINATED state
     */
    public void sendErrored(String callbackBaseAddress, String processId, String errorMessage) {
        String url = callbackBaseAddress
                + String.format(DataPlaneApiEndpoints.DATAFLOW_CALLBACK_ERRORED_TEMPLATE, processId);
        send(url, processId, DataFlowState.TERMINATED, null, errorMessage);
    }

    /**
     * Sends a DataFlowStatusMessage to the Control Plane.
     * Routes {@link DataFlowState#COMPLETED} to the canonical completed endpoint and
     * {@link DataFlowState#TERMINATED} to the canonical errored endpoint.
     * Other states fall back to the legacy error endpoint.
     * Includes the {@code X-Api-Key} header if configured.
     *
     * @param callbackBaseAddress base CP URL (e.g. {@code http://connector:8080})
     * @param processId the TransferProcess ID on the CP
     * @param state the new DataFlow state
     * @param dataAddress optional data address map
     * @param errorMessage optional error message for TERMINATED state
     */
    public void sendStatus(String callbackBaseAddress, String processId,
                           DataFlowState state, Map<String, String> dataAddress, String errorMessage) {
        if (state == DataFlowState.COMPLETED) {
            sendCompleted(callbackBaseAddress, processId, dataAddress);
        } else if (state == DataFlowState.TERMINATED) {
            sendErrored(callbackBaseAddress, processId, errorMessage);
        } else {
            // Legacy fallback for other states (STARTED, SUSPENDED, etc.)
            String url = callbackBaseAddress + DataPlaneApiEndpoints.DATAFLOW_CALLBACK_ERROR;
            send(url, processId, state, dataAddress, errorMessage);
        }
    }

    private void send(String url, String processId, DataFlowState state,
                      Map<String, String> dataAddress, String errorMessage) {
        if (url == null || url.isBlank() || url.startsWith("null/")) {
            log.warn("Skipping {} callback for processId={} — no valid callback URL configured", state, processId);
            return;
        }
        DataFlowStatusMessage message = DataFlowStatusMessage.Builder.newInstance()
                .dataFlowId(UUID.randomUUID().toString())
                .processId(processId)
                .state(state)
                .dataAddress(dataAddress)
                .errorMessage(errorMessage)
                .build();
        try {
            String json = objectMapper.writeValueAsString(message);
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, JSON))
                    .addHeader("Content-Type", "application/json");
            if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                requestBuilder.addHeader("X-Api-Key", properties.getApiKey());
            }
            Request request = requestBuilder.build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                log.info("Sent {} status for processId={}, HTTP {}", state, processId, response.code());
                if (!response.isSuccessful()) {
                    log.error("CP callback rejected with HTTP {} at {}", response.code(), url);
                }
            }
        } catch (IOException e) {
            log.error("Failed to send {} callback to {}: {}", state, url, e.getMessage());
        }
    }
}

