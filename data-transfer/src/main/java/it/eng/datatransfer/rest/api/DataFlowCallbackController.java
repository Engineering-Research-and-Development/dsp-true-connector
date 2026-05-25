package it.eng.datatransfer.rest.api;

import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.datatransfer.service.DataFlowCallbackService;
import it.eng.datatransfer.service.DataPlaneRegistrationService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that receives status callback messages from a registered Data Plane.
 *
 * <p>Canonical per-transfer endpoints (preferred):
 * <ul>
 *   <li>{@code POST /api/v1/transfers/{processId}/dataflow/prepared}</li>
 *   <li>{@code POST /api/v1/transfers/{processId}/dataflow/started}</li>
 *   <li>{@code POST /api/v1/transfers/{processId}/dataflow/completed}</li>
 *   <li>{@code POST /api/v1/transfers/{processId}/dataflow/errored}</li>
 * </ul>
 *
 * <p>Legacy endpoints (preserved for backward compatibility):
 * <ul>
 *   <li>{@code POST /api/v1/dataflows/complete}</li>
 *   <li>{@code POST /api/v1/dataflows/error}</li>
 * </ul>
 *
 * <p>Every request must carry the Data Plane's {@code X-Api-Key} header so the
 * Control Plane can verify the caller is a known, registered Data Plane.</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DataFlowCallbackController {

    private static final String X_API_KEY = "X-Api-Key";

    private final DataFlowCallbackService callbackService;
    private final DataPlaneRegistrationService registrationService;

    // ── Canonical per-transfer endpoints ──────────────────────────────────────

    /**
     * Canonical callback: Data Plane reports resources prepared for a transfer.
     *
     * @param processId the internal transfer process ID
     * @param apiKey    the Data Plane API key from the {@code X-Api-Key} request header
     * @param message   the {@link DataFlowStatusMessage} sent by the Data Plane
     * @return 200 OK on success, or 401 if the API key is not recognised
     */
    @PostMapping(path = ApiEndpoints.DATAFLOW_CALLBACK_PREPARED,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericApiResponse<?>> preparedCallback(
            @PathVariable String processId,
            @RequestHeader(value = X_API_KEY, required = false) String apiKey,
            @RequestBody DataFlowStatusMessage message) {

        var authError = authenticate(apiKey);
        if (authError != null) {
            return authError;
        }
        log.info("Received dataflow prepared callback for processId={}", processId);
        callbackService.handlePrepared(processId, message.getDataAddress());
        return ok("Transfer process prepared");
    }

    /**
     * Canonical callback: Data Plane reports a transfer has started.
     *
     * @param processId the internal transfer process ID
     * @param apiKey    the Data Plane API key from the {@code X-Api-Key} request header
     * @param message   the {@link DataFlowStatusMessage} sent by the Data Plane
     * @return 200 OK on success, or 401 if the API key is not recognised
     */
    @PostMapping(path = ApiEndpoints.DATAFLOW_CALLBACK_STARTED,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericApiResponse<?>> startedCallback(
            @PathVariable String processId,
            @RequestHeader(value = X_API_KEY, required = false) String apiKey,
            @RequestBody DataFlowStatusMessage message) {

        var authError = authenticate(apiKey);
        if (authError != null) {
            return authError;
        }
        log.info("Received dataflow started callback for processId={}", processId);
        callbackService.handleStarted(processId, message.getDataAddress());
        return ok("Transfer process started");
    }

    /**
     * Canonical callback: Data Plane reports a transfer has completed.
     *
     * <p>Delegates to {@link DataFlowCallbackService#handleCompleted(String, java.util.Map)}
     * which persists the internal state and then sends the DSP completion message.</p>
     *
     * @param processId the internal transfer process ID
     * @param apiKey    the Data Plane API key from the {@code X-Api-Key} request header
     * @param message   the {@link DataFlowStatusMessage} sent by the Data Plane
     * @return 200 OK on success, or 401 if the API key is not recognised
     */
    @PostMapping(path = ApiEndpoints.DATAFLOW_CALLBACK_COMPLETED,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericApiResponse<?>> completedCallback(
            @PathVariable String processId,
            @RequestHeader(value = X_API_KEY, required = false) String apiKey,
            @RequestBody DataFlowStatusMessage message) {

        var authError = authenticate(apiKey);
        if (authError != null) {
            return authError;
        }
        log.info("Received dataflow completed callback for processId={}", processId);
        callbackService.handleCompleted(processId, message.getDataAddress());
        return ok("Transfer process completed");
    }

    /**
     * Canonical callback: Data Plane reports a transfer has errored.
     *
     * <p>Delegates to {@link DataFlowCallbackService#handleErrored(String, String)}
     * which persists the internal state and error message, then sends the DSP
     * termination message.</p>
     *
     * @param processId the internal transfer process ID
     * @param apiKey    the Data Plane API key from the {@code X-Api-Key} request header
     * @param message   the {@link DataFlowStatusMessage} sent by the Data Plane
     * @return 200 OK on success, or 401 if the API key is not recognised
     */
    @PostMapping(path = ApiEndpoints.DATAFLOW_CALLBACK_ERRORED,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericApiResponse<?>> erroredCallback(
            @PathVariable String processId,
            @RequestHeader(value = X_API_KEY, required = false) String apiKey,
            @RequestBody DataFlowStatusMessage message) {

        var authError = authenticate(apiKey);
        if (authError != null) {
            return authError;
        }
        log.info("Received dataflow errored callback for processId={}, error={}",
                processId, message.getErrorMessage());
        callbackService.handleErrored(processId, message.getErrorMessage());
        return ok("Transfer process terminated");
    }

    // ── Legacy endpoints (preserved for backward compatibility) ───────────────

    /**
     * Legacy completion callback from the Data Plane.
     *
     * <p>Preserved for backward compatibility. Delegates to
     * {@link DataFlowCallbackService#handleCompleted(String, java.util.Map)} so behavior
     * is consistent with the canonical endpoint.</p>
     *
     * @param apiKey  the Data Plane API key from the {@code X-Api-Key} request header
     * @param message the {@link DataFlowStatusMessage} sent by the Data Plane
     * @return 200 OK on success, or 401 if the API key is not recognised
     */
    @PostMapping(path = ApiEndpoints.DATAFLOW_CALLBACK_COMPLETE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericApiResponse<?>> completeCallback(
            @RequestHeader(value = X_API_KEY, required = false) String apiKey,
            @RequestBody DataFlowStatusMessage message) {

        var authError = authenticate(apiKey);
        if (authError != null) {
            return authError;
        }
        log.info("Received dataflow completion callback (legacy) for processId={}", message.getProcessId());
        callbackService.handleCompleted(message.getProcessId(), message.getDataAddress());
        return ok("Transfer process completed");
    }

    /**
     * Legacy error/termination callback from the Data Plane.
     *
     * <p>Preserved for backward compatibility. Delegates to
     * {@link DataFlowCallbackService#handleErrored(String, String)} so behavior
     * is consistent with the canonical endpoint.</p>
     *
     * @param apiKey  the Data Plane API key from the {@code X-Api-Key} request header
     * @param message the {@link DataFlowStatusMessage} sent by the Data Plane
     * @return 200 OK on success, or 401 if the API key is not recognised
     */
    @PostMapping(path = ApiEndpoints.DATAFLOW_CALLBACK_ERROR,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericApiResponse<?>> errorCallback(
            @RequestHeader(value = X_API_KEY, required = false) String apiKey,
            @RequestBody DataFlowStatusMessage message) {

        var authError = authenticate(apiKey);
        if (authError != null) {
            return authError;
        }
        log.info("Received dataflow error/termination callback (legacy) for processId={}, error={}",
                message.getProcessId(), message.getErrorMessage());
        callbackService.handleErrored(message.getProcessId(), message.getErrorMessage());
        return ok("Transfer process terminated");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<GenericApiResponse<?>> authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GenericApiResponse.error("Missing X-Api-Key header"));
        }
        if (registrationService.findByApiKey(apiKey).isEmpty()) {
            log.warn("Rejected dataflow callback — unknown API key");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GenericApiResponse.error("Unknown Data Plane: invalid or missing API key"));
        }
        return null;
    }

    private ResponseEntity<GenericApiResponse<?>> ok(String message) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(null, message));
    }
}

