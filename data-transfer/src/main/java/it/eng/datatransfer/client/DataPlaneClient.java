package it.eng.datatransfer.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.datatransfer.exceptions.DataPlaneClientException;
import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.router.DataPlaneRouter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * HTTP client that forwards Data Plane Signaling Protocol messages to registered Data Plane services.
 *
 * <p>Uses {@link OkHttpClient} (TLS-aware, configured by {@code OkHttpClientConfiguration}) and
 * {@link DataPlaneRouter} to select the target Data Plane via round-robin.</p>
 */
@Slf4j
@Component
public class DataPlaneClient {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final String X_API_KEY = "X-Api-Key";

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final DataPlaneRouter router;

    /**
     * Creates a new {@code DataPlaneClient}.
     *
     * @param okHttpClient TLS-aware HTTP client
     * @param objectMapper JSON serializer
     * @param router       Data Plane selection router
     */
    public DataPlaneClient(OkHttpClient okHttpClient, ObjectMapper objectMapper, DataPlaneRouter router) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.router = router;
    }

    /**
     * Sends a {@link DataFlowStartMessage} to the selected Data Plane.
     *
     * <p>The target Data Plane is selected based on {@link DataFlowStartMessage#getTransferType()}.
     * The message is POSTed to {@code {dpEndpoint}/dataflows/start}.</p>
     *
     * @param startMessage the start message to forward
     * @throws IllegalStateException if no Data Plane is registered for the transfer type
     */
    public void start(DataFlowStartMessage startMessage) {
        DataPlaneRegistration dp = selectOrThrow(startMessage.getTransferType());
        String url = dp.getEndpoint() + "/dataflows/start";
        log.info("Sending DataFlowStartMessage to '{}'", url);
        post(url, startMessage, dp.getApiKey());
    }

    /**
     * Sends a {@link DataFlowPrepareMessage} to the selected Data Plane and returns the
     * {@link DataFlowPrepareResponse} with protocol-specific addressing data.
     *
     * <p>The transfer type is passed explicitly because {@link DataFlowPrepareMessage} has no
     * {@code transferType} field — the caller (Control Plane) derives it from the
     * {@code TransferProcess}. The message is POSTed to {@code {dpEndpoint}/dataflows/prepare}.</p>
     *
     * @param prepareMessage the prepare message to forward
     * @param transferType   the transfer type used to select the target Data Plane
     * @return the response from the Data Plane with protocol-specific addressing data
     * @throws IllegalStateException if no Data Plane is registered for the transfer type
     */
    public DataFlowPrepareResponse prepare(DataFlowPrepareMessage prepareMessage, String transferType) {
        DataPlaneRegistration dp = selectOrThrow(transferType);
        String url = dp.getEndpoint() + "/dataflows/prepare";
        log.info("Sending DataFlowPrepareMessage to '{}'", url);
        return postForResponse(url, prepareMessage, dp.getApiKey(), DataFlowPrepareResponse.class);
    }

    /**
     * Sends a termination request for the given process to the selected Data Plane.
     *
     * <p>Sends {@code DELETE {dpEndpoint}/dataflows/{processId}} as required by the DSP spec.</p>
     *
     * @param processId    the transfer process ID to terminate
     * @param transferType the transfer type used to select the target Data Plane
     * @throws IllegalStateException   if no Data Plane is registered for the transfer type
     * @throws DataPlaneClientException if the DELETE request fails due to an I/O error
     */
    public void terminate(String processId, String transferType) {
        DataPlaneRegistration dp = selectOrThrow(transferType);
        String url = dp.getEndpoint() + "/dataflows/" + processId;
        log.info("Sending terminate request for process '{}' to '{}'", processId, url);
        delete(url, dp);
    }

    private DataPlaneRegistration selectOrThrow(String transferType) {
        return router.selectDataPlane(transferType)
                .orElseThrow(() -> new IllegalStateException(
                        "No Data Plane registered for transfer type: " + transferType));
    }

    private void post(String url, Object body, String apiKey) {
        postForResponse(url, body, apiKey, Void.class);
    }

    private <T> T postForResponse(String url, Object body, String apiKey, Class<T> responseType) {
        String json = serializeBody(body);
        RequestBody requestBody = RequestBody.create(json, JSON);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json");
        if (apiKey != null) {
            builder.header(X_API_KEY, apiKey);
        }
        Request request = builder.build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new DataPlaneClientException("Data Plane returned HTTP " + response.code() + " for " + url);
            }
            log.debug("Data Plane responded with HTTP {}", response.code());
            if (responseType == Void.class || response.body() == null) {
                return null;
            }
            String responseBody = response.body().string();
            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }
            return objectMapper.readValue(responseBody, responseType);
        } catch (IOException e) {
            log.error("Failed to send POST to {}: {}", url, e.getMessage());
            throw new DataPlaneClientException("Failed to send message to data plane at " + url, e);
        }
    }

    private void delete(String url, DataPlaneRegistration dp) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .delete();
        if (dp.getApiKey() != null) {
            builder.addHeader(X_API_KEY, dp.getApiKey());
        }
        Request request = builder.build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new DataPlaneClientException("Data Plane returned HTTP " + response.code() + " for " + url);
            }
            log.debug("DELETE {} -> {}", url, response.code());
        } catch (IOException e) {
            log.error("Failed to send DELETE to {}: {}", url, e.getMessage());
            throw new DataPlaneClientException("Failed to terminate transfer at " + url, e);
        }
    }

    private String serializeBody(Object body) {
        if (body == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new DataPlaneClientException("Failed to serialize message body", e);
        }
    }
}
