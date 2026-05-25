package it.eng.datatransfer.service;

import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Centralizes Data Plane callback handling on the Control Plane.
 *
 * <p>Each callback method persists the internal {@code dataFlowState} (and optionally
 * {@code dataAddress}) on the {@link TransferProcess} before triggering the external
 * DSP state transition via {@link DataTransferAPIService}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataFlowCallbackService {

    private final TransferProcessRepository repository;
    private final DataTransferAPIService apiService;

    /**
     * Handles a {@code prepared} callback from the Data Plane.
     * Persists {@code dataFlowState=PREPARED} and the mapped data address.
     *
     * @param processId   the internal transfer process ID
     * @param dataAddress optional data address map sent by the Data Plane
     */
    public void handlePrepared(String processId, Map<String, String> dataAddress) {
        log.info("Handling prepared callback for processId={}", processId);
        var process = findRequired(processId);
        var updated = process.withDataFlowState("PREPARED");
        if (dataAddress != null && !dataAddress.isEmpty()) {
            updated = updated.withDataAddress(dataAddressFromMap(dataAddress));
        }
        repository.save(updated);
    }

    /**
     * Handles a {@code started} callback from the Data Plane.
     * Persists {@code dataFlowState=STARTED} and the mapped data address.
     *
     * @param processId   the internal transfer process ID
     * @param dataAddress optional data address map sent by the Data Plane
     */
    public void handleStarted(String processId, Map<String, String> dataAddress) {
        log.info("Handling started callback for processId={}", processId);
        var process = findRequired(processId);
        var updated = process.withDataFlowState("STARTED");
        if (dataAddress != null && !dataAddress.isEmpty()) {
            updated = updated.withDataAddress(dataAddressFromMap(dataAddress));
        }
        repository.save(updated);
    }

    /**
     * Handles a {@code completed} callback from the Data Plane.
     * Persists {@code dataFlowState=COMPLETED} (and data address if provided), then
     * delegates to {@link DataTransferAPIService#completeTransfer(String)} to send the
     * DSP {@code TransferCompletionMessage} and transition to {@code COMPLETED}.
     *
     * @param processId   the internal transfer process ID
     * @param dataAddress optional data address map sent by the Data Plane
     */
    public void handleCompleted(String processId, Map<String, String> dataAddress) {
        log.info("Handling completed callback for processId={}", processId);
        var process = findRequired(processId);
        var updated = process.withDataFlowState("COMPLETED");
        if (dataAddress != null && !dataAddress.isEmpty()) {
            updated = updated.withDataAddress(dataAddressFromMap(dataAddress));
        }
        repository.save(updated);
        apiService.completeTransfer(processId);
    }

    /**
     * Handles an {@code errored} callback from the Data Plane.
     * Persists {@code dataFlowState=TERMINATED} and the error message, then delegates to
     * {@link DataTransferAPIService#terminateTransfer(String)} to send the DSP
     * {@code TransferTerminationMessage} and transition to {@code TERMINATED}.
     *
     * @param processId    the internal transfer process ID
     * @param errorMessage the error message received from the Data Plane
     */
    public void handleErrored(String processId, String errorMessage) {
        log.info("Handling errored callback for processId={}, error={}", processId, errorMessage);
        var process = findRequired(processId);
        repository.save(process.withDataFlowState("TERMINATED").withDataFlowErrorMessage(errorMessage));
        apiService.terminateTransfer(processId);
    }

    private TransferProcess findRequired(String processId) {
        return repository.findById(processId)
                .orElseThrow(() -> new IllegalStateException(
                        "TransferProcess not found for processId: " + processId));
    }

    /**
     * Maps a flat string map from the Data Plane to a {@link DataAddress}.
     *
     * <p>Keys {@code endpointType} and {@code endpoint} are mapped to the dedicated
     * {@link DataAddress} fields; all remaining entries become {@link EndpointProperty}
     * instances so no information is lost.</p>
     *
     * @param map the raw data address map from the Data Plane
     * @return a populated {@link DataAddress}, or {@code null} if the map is null or empty
     */
    DataAddress dataAddressFromMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        List<EndpointProperty> extraProps = map.entrySet().stream()
                .filter(e -> !"endpointType".equals(e.getKey()) && !"endpoint".equals(e.getKey()))
                .map(e -> EndpointProperty.Builder.newInstance()
                        .name(e.getKey())
                        .value(e.getValue())
                        .build())
                .toList();
        return DataAddress.Builder.newInstance()
                .endpointType(map.get("endpointType"))
                .endpoint(map.get("endpoint"))
                .endpointProperties(extraProps.isEmpty() ? null : extraProps)
                .build();
    }
}
