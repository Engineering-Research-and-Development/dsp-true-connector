package it.eng.datatransfer.rest.api;

import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.datatransfer.service.DataPlaneRegistrationService;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that receives status callback messages from a registered Data Plane.
 *
 * <p>The Data Plane POSTs a {@link DataFlowStatusMessage} to one of these endpoints after a
 * transfer finishes or fails.  Every request must carry the Data Plane's {@code X-Api-Key}
 * header so the Control Plane can verify the caller is a known, registered Data Plane.</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DataFlowCallbackController {

    private static final String X_API_KEY = "X-Api-Key";

    private final DataTransferAPIService apiService;
    private final DataPlaneRegistrationService registrationService;

    /**
     * Receives a completion callback from the Data Plane.
     *
     * <p>Verifies the caller via the {@code X-Api-Key} header, then delegates to
     * {@link DataTransferAPIService#completeTransfer(String)} to send the DSP
     * {@code TransferCompletionMessage} and transition the {@code TransferProcess} to
     * {@code COMPLETED}.</p>
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

        if (registrationService.findByApiKey(apiKey).isEmpty()) {
            log.warn("Rejected dataflow completion callback — unknown API key");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GenericApiResponse.error("Unknown Data Plane: invalid or missing API key"));
        }

        log.info("Received dataflow completion callback for processId={}", message.getProcessId());
        apiService.completeTransfer(message.getProcessId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(null, "Transfer process completed"));
    }

    /**
     * Receives an error or termination callback from the Data Plane.
     *
     * <p>Verifies the caller via the {@code X-Api-Key} header, then delegates to
     * {@link DataTransferAPIService#terminateTransfer(String)} to send the DSP
     * {@code TransferTerminationMessage} and transition the {@code TransferProcess} to
     * {@code TERMINATED}.</p>
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

        if (registrationService.findByApiKey(apiKey).isEmpty()) {
            log.warn("Rejected dataflow error callback — unknown API key");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GenericApiResponse.error("Unknown Data Plane: invalid or missing API key"));
        }

        log.info("Received dataflow error/termination callback for processId={}, error={}",
                message.getProcessId(), message.getErrorMessage());
        apiService.terminateTransfer(message.getProcessId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(null, "Transfer process terminated"));
    }
}
