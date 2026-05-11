package it.eng.dataplane.core.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.config.DataPlaneProperties;
import it.eng.tools.controller.ApiEndpoints;
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
     * Sends a DataFlowStatusMessage to the Control Plane callback endpoint.
     * Routes COMPLETED state to {@code /api/v1/dataflows/complete} and all other states
     * (TERMINATED, FAILED) to {@code /api/v1/dataflows/error}.
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
        String path = (state == DataFlowState.COMPLETED)
                ? ApiEndpoints.DATAFLOW_CALLBACK_COMPLETE
                : ApiEndpoints.DATAFLOW_CALLBACK_ERROR;
        String url = callbackBaseAddress + path;
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
