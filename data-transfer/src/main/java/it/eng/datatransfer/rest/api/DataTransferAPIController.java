package it.eng.datatransfer.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.event.TransferArtifactEvent;
import it.eng.datatransfer.model.DataTransferRequest;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping(path = ApiEndpoints.TRANSFER_DATATRANSFER_V1)
@Slf4j
public class DataTransferAPIController {

    private final DataTransferAPIService apiService;
    private final ApplicationEventPublisher publisher;

    public DataTransferAPIController(DataTransferAPIService apiService, ApplicationEventPublisher publisher) {
        this.apiService = apiService;
        this.publisher = publisher;
    }

    /********* CONSUMER ***********/

    /**
     * Consumer requests (initiates) data transfer.
     *
     * @param dataTransferRequest specifying transfer process id and other parameters.
     * @return GenericApiResponse response with transfer process details.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericApiResponse<JsonNode>> requestTransfer(@RequestBody DataTransferRequest dataTransferRequest) {
        log.info("Consumer sends transfer request {}", dataTransferRequest.getTransferProcessId());
        JsonNode response = apiService.requestTransfer(dataTransferRequest);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(response, "Data transfer requested"));
    }

    /**
     * Consumer download artifact.
     *
     * @param transferProcessId transfer process id to download data for.
     * @return GenericApiResponse response with success message.
     */
    @GetMapping(path = {"/{transferProcessId}/download"})
    public ResponseEntity<GenericApiResponse<String>> downloadData(
            @PathVariable String transferProcessId) {
        log.info("Downloading transfer process id - {} data", transferProcessId);
        apiService.downloadData(transferProcessId)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Download failed for process {}: {}",
                                transferProcessId, throwable.getMessage(), throwable);
                        publisher.publishEvent(
                                TransferArtifactEvent.Builder.newInstance()
                                        .transferProcessId(transferProcessId)
                                        .isDownload(false)
                                        .message("Download failed: " + throwable.getMessage())
                                        .build());
                    } else {
                        log.info("Download completed successfully for process {}", transferProcessId);
                        publisher.publishEvent(TransferArtifactEvent.Builder.newInstance()
                                .transferProcessId(transferProcessId)
                                .isDownload(true)
                                .message(String.format("Download completed successfully for process %s", transferProcessId))
                                .build());
                    }
                });

        return ResponseEntity.accepted()
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(
                        "Download started for transfer process " + transferProcessId,
                        "Request accepted"));
    }

    /**
     * Consumer view downloaded artifact.<br>
     * Before "viewing" artifact, policy will be enforced to check if agreement is still valid.
     *
     * @param transferProcessId transfer process id to view data for.
     * @return GenericApiResponse response with artifact URL
     */
    @GetMapping(path = {"/{transferProcessId}/view"})
    public ResponseEntity<String> viewData(
            @PathVariable String transferProcessId) {
        log.info("Accessing transfer process id - {} data", transferProcessId);
        String artifactURL = apiService.viewData(transferProcessId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(artifactURL);
    }

    /********* CONSUMER & PROVIDER ***********/

    /**
     * Find by id if present, next by state and get all.
     *
     * @param transferProcessId transfer process id to filter by, if present.
     * @param state             optional state to filter transfer processes by.
     * @param role              optional role to filter transfer processes by (e.g., CONSUMER or PROVIDER).
     * @return GenericApiResponse
     */
    @GetMapping(path = {"", "/{transferProcessId}"})
    public ResponseEntity<GenericApiResponse<Collection<JsonNode>>> getTransfersProcess(
            @PathVariable(required = false) String transferProcessId,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String role) {
        log.info("Fetching transfer process id - {}, state {}", transferProcessId, state);
        Collection<JsonNode> response = apiService.findDataTransfers(transferProcessId, state, role);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(response, "Fetched transfer process"));
    }

    /**
     * Start transfer process.
     *
     * @param transferProcessId transfer process id to start.
     * @return GenericApiResponse response with success message.
     */
    @PutMapping(path = "/{transferProcessId}/start")
    public ResponseEntity<GenericApiResponse<JsonNode>> startTransfer(@PathVariable String transferProcessId) {
        log.info("Starting data transfer {}", transferProcessId);
        JsonNode response = apiService.startTransfer(transferProcessId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(response, "Data transfer started"));
    }

    /**
     * Complete transfer process.
     *
     * @param transferProcessId transfer process id to complete.
     * @return GenericApiResponse response with success message.
     */
    @PutMapping(path = "/{transferProcessId}/complete")
    public ResponseEntity<GenericApiResponse<JsonNode>> completeTransfer(@PathVariable String transferProcessId) {
        log.info("Completing data transfer {}", transferProcessId);
        JsonNode response = apiService.completeTransfer(transferProcessId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(response, "Data transfer completed"));
    }

    /**
     * Suspend transfer process.
     *
     * @param transferProcessId transfer process id to suspend.
     * @return GenericApiResponse response with success message.
     */
    @PutMapping(path = "/{transferProcessId}/suspend")
    public ResponseEntity<GenericApiResponse<JsonNode>> suspendTransfer(@PathVariable String transferProcessId) {
        log.info("Suspending data transfer {}", transferProcessId);
        JsonNode response = apiService.suspendTransfer(transferProcessId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(response, "Data transfer suspended"));
    }

    /**
     * Terminate transfer process.
     *
     * @param transferProcessId transfer process id to terminate.
     * @return GenericApiResponse response with success message.
     */
    @PutMapping(path = "/{transferProcessId}/terminate")
    public ResponseEntity<GenericApiResponse<JsonNode>> terminateTransfer(@PathVariable String transferProcessId) {
        log.info("Terminating data transfer {}", transferProcessId);
        JsonNode response = apiService.terminateTransfer(transferProcessId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(response, "Data transfer terminated"));
    }

}
