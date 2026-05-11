package it.eng.dataplane.core.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.dataplane.api.model.DataFlowState;
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

    /**
     * @param okHttpClient TLS-aware HTTP client from OkHttpClientConfiguration
     * @param objectMapper shared Jackson mapper
     */
    public ControlPlaneClient(OkHttpClient okHttpClient, ObjectMapper objectMapper) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a DataFlowStatusMessage to the Control Plane callback endpoint.
     *
     * @param callbackAddress base callback URL from Control Plane
     * @param processId the TransferProcess ID on the CP
     * @param state the new DataFlow state
     * @param dataAddress optional data address map
     * @param errorMessage optional error message for TERMINATED state
     */
    public void sendStatus(String callbackAddress, String processId,
                           DataFlowState state, Map<String, String> dataAddress, String errorMessage) {
        String url = callbackAddress + "/" + processId + "/dataflow/" + state.name().toLowerCase();
        DataFlowStatusMessage message = DataFlowStatusMessage.Builder.newInstance()
            .dataFlowId(UUID.randomUUID().toString())
            .processId(processId)
            .state(state)
            .dataAddress(dataAddress)
            .errorMessage(errorMessage)
            .build();
        try {
            String json = objectMapper.writeValueAsString(message);
            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .addHeader("Content-Type", "application/json")
                .build();
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
