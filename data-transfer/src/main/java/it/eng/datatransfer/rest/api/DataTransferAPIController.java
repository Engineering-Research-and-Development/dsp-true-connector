package it.eng.datatransfer.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.model.DataTransferRequest;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.rest.api.PagedAPIResponse;
import it.eng.tools.service.GenericFilterBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = ApiEndpoints.TRANSFER_DATATRANSFER_V1)
@Slf4j
public class DataTransferAPIController {

    private final DataTransferAPIService apiService;
    private final GenericFilterBuilder filterBuilder;
    private final ApplicationEventPublisher publisher;
    private final PagedResourcesAssembler<TransferProcess> pagedResourcesAssembler;
    private final PlainTransferProcessAssembler plainAssembler;

    public DataTransferAPIController(DataTransferAPIService apiService, GenericFilterBuilder filterBuilder, ApplicationEventPublisher publisher, PagedResourcesAssembler<TransferProcess> pagedResourcesAssembler, PlainTransferProcessAssembler plainAssembler) {
        this.apiService = apiService;
        this.filterBuilder = filterBuilder;
        this.publisher = publisher;
        this.pagedResourcesAssembler = pagedResourcesAssembler;
        this.plainAssembler = plainAssembler;
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
                                AuditEvent.Builder.newInstance()
                                        .description("Download failed for process " + transferProcessId)
                                        .eventType(AuditEventType.TRANSFER_FAILED)
                                        .details(Map.of("transferProcessId", transferProcessId,
                                                "error", throwable.getMessage()))
                                        .build()
                        );
                    } else {
                        log.info("Download completed successfully for process {}", transferProcessId);
                        publisher.publishEvent(
                                AuditEvent.Builder.newInstance()
                                        .description("Download completed successfully for process " + transferProcessId)
                                        .eventType(AuditEventType.TRANSFER_COMPLETED)
                                        .details(Map.of("transferProcessId", transferProcessId))
                                        .build()
                        );
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
     * Generic endpoint for finding transfer processes with automatic filtering.
     * Supports any request parameter with automatic type detection and conversion.
     *
     * @param transferProcessId optional transfer process id to filter by (path variable)
     * @param request           HttpServletRequest containing all filter parameters
     * @param page              pagination page number (default 0)
     * @param size              pagination parameters
     * @param sort              sorting parameters in the format "field,direction"
     * @return GenericApiResponse with matching transfer processes
     */
    @GetMapping(path = {"", "/{transferProcessId}"})
    public ResponseEntity<PagedAPIResponse> getTransfersProcess(
            @PathVariable(required = false) String transferProcessId,
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp,desc") String[] sort) {

        log.info("Fetching transfer processes with generic filtering");

        Sort.Direction direction = sort[1].equalsIgnoreCase("desc") ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sorting = Sort.by(direction, sort[0]);
        Pageable pageable = PageRequest.of(page, size, sorting);
        // Build filter map automatically from ALL request parameters
        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        // Handle path variable with proper validation
        if (StringUtils.hasText(transferProcessId)) {
            // Create mutable copy if needed (defensive programming)
            try {
                filters.clear();
                filters.put("id", transferProcessId.trim());
            } catch (UnsupportedOperationException e) {
                filters = new HashMap<>(filters);
                filters.put("id", transferProcessId.trim());
            }
        }

        log.debug("Generated filters: {}", filters);

        Page<TransferProcess> transferProcesses = apiService.findDataTransfers(filters, pageable);
        PagedModel<EntityModel<Object>> pagedModel = pagedResourcesAssembler.toModel(transferProcesses, plainAssembler);

        String filterString = filters.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(", "));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(PagedAPIResponse.of(pagedModel,
                        "Transfer process - Page " + page + " of " + transferProcesses.getTotalPages() + ", Size: " + size +
                                ", Sort: " + sorting + ", Filters: [" + filterString + "]"));

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
