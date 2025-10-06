package it.eng.datatransfer.service;

import it.eng.datatransfer.event.TransferProcessChangeEvent;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.IConstants;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@Profile("!tck")
public class DataTransferService extends AbstractDataTransferService {

    private final AuditEventPublisher publisher;

    public DataTransferService(TransferProcessRepository transferProcessRepository,
                               TransferRequestMessageRepository transferRequestMessageRepository,
                               AuditEventPublisher publisher,
                               OkHttpRestClient okHttpRestClient) {
        super(transferProcessRepository, publisher, okHttpRestClient, transferRequestMessageRepository);
        this.publisher = publisher;
    }

    /**
     * If TransferProcess for given consumerPid and providerPid exists and state is STARTED.<br>
     * Note: those 2 Pid's are not to be mixed with Contract Negotiation ones. They are unique
     *
     * @param consumerPid consumerPid to search by
     * @param providerPid providerPid to search by
     * @return true if there is transferProcess with state STARTED for consumerPid and providerPid
     */
    public boolean isDataTransferStarted(String consumerPid, String providerPid) {
        return findByConsumerPidAndProviderPid(consumerPid, providerPid).getState().equals(TransferState.STARTED);
    }

    @Override
    public TransferProcess requestTransfer(TCKRequest tckRequest) {
        throw new UnsupportedOperationException("Not supported!");
    }

    /**
     * Find transferProcess for given providerPid.
     *
     * @param providerPid providerPid to search by
     * @return TransferProcess
     */
    public TransferProcess findTransferProcessByProviderPid(String providerPid) {
        return super.findTransferProcessByProviderPid(providerPid);
    }

    @Override
    public TransferProcess findTransferProcessByConsumerPid(String consumerPid) {
        return super.findTransferProcessByConsumerPid(consumerPid);
    }

    /**
     * Initiate data transfer.
     *
     * @param transferRequestMessage message
     * @return TransferProcess with status REQUESTED
     */
    public TransferProcess initiateDataTransfer(TransferRequestMessage transferRequestMessage) {
        return super.initiateDataTransfer(transferRequestMessage);
    }

    /**
     * Transfer from REQUESTED or SUSPENDED to STARTED state.
     *
     * @param transferStartMessage TransferStartMessage
     * @param consumerPid          consumerPid in case of consumer callback usage
     * @param providerPid          providerPid in case of provider usage
     * @return TransferProcess with status STARTED
     */
    public TransferProcess startDataTransfer(TransferStartMessage transferStartMessage, String consumerPid, String providerPid) {
        return super.startDataTransfer(transferStartMessage, consumerPid, providerPid);
    }

    /**
     * Finds transfer process, check if status is correct, publish event and update state to COMPLETED.
     *
     * @param transferCompletionMessage TransferCompletionMessage
     * @param consumerPid               consumerPid in case of consumer callback usage
     * @param providerPid               providerPid in case of provider usage
     * @return TransferProcess with status COMPLETED
     */
    public TransferProcess completeDataTransfer(TransferCompletionMessage transferCompletionMessage, String consumerPid,
                                                String providerPid) {
        String consumerPidFinal = consumerPid == null ? transferCompletionMessage.getConsumerPid() : consumerPid;
        String providerPidFinal = providerPid == null ? transferCompletionMessage.getProviderPid() : providerPid;
        log.debug("Completing data transfer for consumerPid {} and providerPid {}", consumerPidFinal, providerPidFinal);

        TransferProcess transferProcessStarted = findTransferProcess(consumerPidFinal, providerPidFinal);
        stateTransitionCheck(transferProcessStarted, TransferState.COMPLETED);

        TransferProcess transferProcessCompleted = TransferProcess.Builder.newInstance()
                .id(transferProcessStarted.getId())
                .agreementId(transferProcessStarted.getAgreementId())
                .consumerPid(transferProcessStarted.getConsumerPid())
                .providerPid(transferProcessStarted.getProviderPid())
                .callbackAddress(transferProcessStarted.getCallbackAddress())
                .dataAddress(transferProcessStarted.getDataAddress())
                .isDownloaded(true)
                .dataId(transferProcessStarted.getId())
                .format(transferProcessStarted.getFormat())
                .state(TransferState.COMPLETED)
                .role(transferProcessStarted.getRole())
                .datasetId(transferProcessStarted.getDatasetId())
                .created(transferProcessStarted.getCreated())
                .createdBy(transferProcessStarted.getCreatedBy())
                .modified(transferProcessStarted.getModified())
                .lastModifiedBy(transferProcessStarted.getLastModifiedBy())
                .version(transferProcessStarted.getVersion())
                .build();

        saveTransferProcess(transferProcessCompleted);
        publisher.publishEvent(TransferProcessChangeEvent.Builder.newInstance()
                .oldTransferProcess(transferProcessStarted)
                .newTransferProcess(transferProcessCompleted)
                .build());
        publisher.publishEvent(transferCompletionMessage);
        publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_COMPLETED,
                "Transfer process completed",
                Map.of("role", IConstants.ROLE_PROTOCOL,
                        "transferProcess", transferProcessCompleted,
                        "consumerPid", transferProcessCompleted.getConsumerPid(),
                        "providerPid", transferProcessCompleted.getProviderPid()));
        return transferProcessCompleted;
    }

    /**
     * Transition data transfer to SUSPENDED state.
     *
     * @param transferSuspensionMessage message
     * @param consumerPid               consumerPid in case of consumer callback usage
     * @param providerPid               providerPid in case of provider usage
     * @return TransferProcess with status SUSPENDED
     */
    public TransferProcess suspendDataTransfer(TransferSuspensionMessage transferSuspensionMessage, String consumerPid,
                                               String providerPid) {
        String consumerPidFinal = consumerPid == null ? transferSuspensionMessage.getConsumerPid() : consumerPid;
        String providerPidFinal = providerPid == null ? transferSuspensionMessage.getProviderPid() : providerPid;
        log.debug("Suspending data transfer for consumerPid {} and providerPid {}", consumerPidFinal, providerPidFinal);

        TransferProcess transferProcessStarted = findTransferProcess(consumerPidFinal, providerPidFinal);
        stateTransitionCheck(transferProcessStarted, TransferState.SUSPENDED);

        TransferProcess transferProcessSuspended = transferProcessStarted.copyWithNewTransferState(TransferState.SUSPENDED);
        saveTransferProcess(transferProcessSuspended);
        publisher.publishEvent(TransferProcessChangeEvent.Builder.newInstance()
                .oldTransferProcess(transferProcessStarted)
                .newTransferProcess(transferProcessSuspended)
                .build());
        publisher.publishEvent(transferSuspensionMessage);
        publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED,
                "Transfer process completed",
                Map.of("role", IConstants.ROLE_PROTOCOL,
                        "transferProcess", transferProcessSuspended,
                        "consumerPid", transferProcessSuspended.getConsumerPid(),
                        "providerPid", transferProcessSuspended.getProviderPid()));
        return transferProcessSuspended;
    }

    /**
     * Transition data transfer to TERMINATED state.
     *
     * @param transferTerminationMessage message
     * @param consumerPid                consumerPid in case of consumer callback usage
     * @param providerPid                providerPid in case of provider usage
     * @return TransferProcess with status TERMINATED
     */
    public TransferProcess terminateDataTransfer(TransferTerminationMessage transferTerminationMessage, String consumerPid,
                                                 String providerPid) {
        String consumerPidFinal = consumerPid == null ? transferTerminationMessage.getConsumerPid() : consumerPid;
        String providerPidFinal = providerPid == null ? transferTerminationMessage.getProviderPid() : providerPid;
        log.debug("Terminating data transfer for consumerPid {} and providerPid {}", consumerPidFinal, providerPidFinal);

        // can be in any state except TERMINATED
        TransferProcess transferProcess = findTransferProcess(consumerPidFinal, providerPidFinal);
        stateTransitionCheck(transferProcess, TransferState.TERMINATED);

        TransferProcess transferProcessTerminated = transferProcess.copyWithNewTransferState(TransferState.TERMINATED);
        saveTransferProcess(transferProcessTerminated);
        publisher.publishEvent(TransferProcessChangeEvent.Builder.newInstance()
                .oldTransferProcess(transferProcess)
                .newTransferProcess(transferProcessTerminated)
                .build());
        publisher.publishEvent(transferTerminationMessage);
        publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_TERMINATED,
                "Transfer process completed",
                Map.of("role", IConstants.ROLE_PROTOCOL,
                        "transferProcess", transferProcessTerminated,
                        "consumerPid", transferProcessTerminated.getConsumerPid(),
                        "providerPid", transferProcessTerminated.getProviderPid()));
        return transferProcessTerminated;
    }
}
