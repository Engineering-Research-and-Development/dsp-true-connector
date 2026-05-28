package it.eng.dataplane.core.startup;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import it.eng.dataplane.core.repository.DataFlowRepository;
import it.eng.dataplane.core.service.DataFlowCheckpointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Recovers data flows that were in-flight when the Data Plane last shut down.
 *
 * <p>On {@link ApplicationReadyEvent}, scans for {@link DataFlowState#STARTING} and
 * {@link DataFlowState#STARTED} entities and reconciles each one:
 * <ul>
 *   <li>If a resumable checkpoint exists <em>and</em> the protocol reports usable access
 *       material, the flow is moved to {@link DataFlowState#SUSPENDED} so the operator
 *       or Control Plane can issue a resume.</li>
 *   <li>Otherwise, the flow is moved to {@link DataFlowState#TERMINATED} with the message
 *       {@value #UNRECOVERABLE_ERROR} to signal that a new transfer must be initiated.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataFlowRecoveryStartupBean implements ApplicationListener<ApplicationReadyEvent> {

    static final String UNRECOVERABLE_ERROR = "unrecoverable error, start a new data transfer";

    private final DataFlowRepository repository;
    private final DataFlowCheckpointService checkpointService;
    private final DataTransferProtocolRegistry protocolRegistry;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        recoverOnStartup();
    }

    /**
     * Reconciles all in-flight data flows to a stable state.
     * Called on {@link ApplicationReadyEvent}; exposed for testing.
     */
    public void recoverOnStartup() {
        List<DataFlowEntity> inFlight = repository.findAllByStateIn(
                Set.of(DataFlowState.STARTING, DataFlowState.STARTED));

        if (inFlight.isEmpty()) {
            log.debug("No in-flight data flows to recover.");
            return;
        }

        log.info("Recovering {} in-flight data flow(s) after restart.", inFlight.size());
        inFlight.forEach(this::reconcile);
    }

    private void reconcile(DataFlowEntity entity) {
        String processId = entity.getProcessId();
        log.debug("Reconciling data flow processId={}, state={}", processId, entity.getState());

        if (isResumable(entity)) {
            log.info("Data flow processId={} has a valid checkpoint and usable access material — suspending.", processId);
            repository.save(entity.withState(DataFlowState.SUSPENDED));
        } else {
            log.warn("Data flow processId={} cannot be recovered — terminating.", processId);
            repository.save(entity.withError(UNRECOVERABLE_ERROR, DataFlowState.TERMINATED));
        }
    }

    private boolean isResumable(DataFlowEntity entity) {
        if (!checkpointService.hasResumableCheckpoint(entity.getProcessId())) {
            return false;
        }
        DataTransferProtocol protocol = protocolRegistry.getProtocol(entity.getTransferType());
        if (protocol == null) {
            log.warn("No protocol registered for transferType={}, cannot verify access material.", entity.getTransferType());
            return false;
        }
        return protocol.hasUsableAccessMaterial(toDataFlow(entity));
    }

    private DataFlow toDataFlow(DataFlowEntity entity) {
        return DataFlow.Builder.newInstance()
                .dataFlowId(entity.getId())
                .processId(entity.getProcessId())
                .agreementId(entity.getAgreementId())
                .datasetId(entity.getDatasetId())
                .transferType(entity.getTransferType())
                .callbackAddress(entity.getCallbackAddress())
                .state(entity.getState())
                .dataAddress(entity.getDataAddress())
                .tenantId(entity.getTenantId())
                .participantId(entity.getParticipantId())
                .counterPartyId(entity.getCounterPartyId())
                .errorMessage(entity.getErrorMessage())
                .build();
    }
}
