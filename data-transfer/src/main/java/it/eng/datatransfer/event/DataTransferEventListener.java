package it.eng.datatransfer.event;

import it.eng.datatransfer.model.*;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.tools.event.datatransfer.CompletedDataTransfer;
import it.eng.tools.event.datatransfer.InitializeTransferProcess;
import it.eng.tools.model.IConstants;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@Slf4j
public class DataTransferEventListener {

    private final AuditEventPublisher publisher;
    private final TransferRequestMessageRepository transferRequestMessageRepository;
    private final TransferProcessRepository transferProcessRepository;
    private final DataTransferAPIService dataTransferAPIService;

    public DataTransferEventListener(AuditEventPublisher publisher, TransferRequestMessageRepository transferRequestMessageRepository, TransferProcessRepository transferProcessRepository, DataTransferAPIService dataTransferAPIService) {
        super();
        this.publisher = publisher;
        this.transferRequestMessageRepository = transferRequestMessageRepository;
        this.transferProcessRepository = transferProcessRepository;
        this.dataTransferAPIService = dataTransferAPIService;
    }

    @EventListener
    public void initializeTransferProcess(InitializeTransferProcess initializeTransferProcess) {
        log.info("Initializing transfer process");
        TransferProcess.Builder transferProcessBuilder = TransferProcess.Builder.newInstance()
                .callbackAddress(initializeTransferProcess.getCallbackAddress())
                .agreementId(initializeTransferProcess.getAgreementId())
                .datasetId(initializeTransferProcess.getDatasetId())
                .state(TransferState.INITIALIZED)
                .created(Instant.now())
                .modified(Instant.now())
                .role(initializeTransferProcess.getRole());

        if (initializeTransferProcess.getRole().equals(IConstants.ROLE_CONSUMER)) {
            transferProcessBuilder.providerPid(IConstants.TEMPORARY_PROVIDER_PID);
        }
        if (initializeTransferProcess.getRole().equals(IConstants.ROLE_PROVIDER)) {
            transferProcessBuilder.consumerPid(IConstants.TEMPORARY_CONSUMER_PID);
        }

        transferProcessRepository.save(transferProcessBuilder.build());
    }

    @EventListener
    public void handleTransferProcessChange(TransferProcessChangeEvent transferProcessEvent) {
        log.info("Transfering process {} from state '{}' to '{}'",
                transferProcessEvent.getOldTransferProcess().getId(), transferProcessEvent.getOldTransferProcess().getState(),
                transferProcessEvent.getNewTransferProcess().getState());
    }

    @EventListener
    public void handleTransferStartMessage(TransferStartMessage transferStartMessage) {
        log.info("Transfer Start message event received");
        Optional<TransferRequestMessage> transferRequestMessage = transferRequestMessageRepository.findByConsumerPid(transferStartMessage.getConsumerPid());
        if (transferRequestMessage.isPresent() && transferRequestMessage.get().getFormat().equals(DataTransferFormat.SFTP.name())) {
            log.info("Publishing event to start SFTP server...");
            publisher.publishEvent(new StartFTPServerEvent());
        }
    }

    @EventListener
    public void handleTransferSuspensionMessage(TransferSuspensionMessage transferSuspensionMessage) {
        log.info("Suspending transfer with code {} and reason {}", transferSuspensionMessage.getCode(), transferSuspensionMessage.getReason());
        Optional<TransferRequestMessage> transferRequestMessage = transferRequestMessageRepository.findByConsumerPid(transferSuspensionMessage.getConsumerPid());
        if (transferRequestMessage.isPresent() && transferRequestMessage.get().getFormat().equals(DataTransferFormat.SFTP.name())) {
            publisher.publishEvent(new StopFTPServerEvent());
        }
    }

    @EventListener
    public void handleTransferCompletionMessage(TransferCompletionMessage transferCompletionMessage) {
        log.info("Completeing transfer with consumerPid {} and providerPid {}", transferCompletionMessage.getConsumerPid(), transferCompletionMessage.getProviderPid());
        Optional<TransferRequestMessage> transferRequestMessage = transferRequestMessageRepository.findByConsumerPid(transferCompletionMessage.getConsumerPid());
        if (transferRequestMessage.isPresent() && transferRequestMessage.get().getFormat().equals(DataTransferFormat.SFTP.name())) {
            publisher.publishEvent(new StopFTPServerEvent());
        }
    }

    @EventListener
    public void handleTransferTerminationMessage(TransferTerminationMessage transferTerminationMessage) {
        log.info("Terminating transfer with consumerPid {} and providerPid {}", transferTerminationMessage.getConsumerPid(), transferTerminationMessage.getProviderPid());
    }

    @EventListener
    public void handleCompletedDataTransfer(CompletedDataTransfer completedDataTransfer) {
        log.info("Completed data transfer event received for transferProcessId: {}", completedDataTransfer.getTransferProcessId());
        dataTransferAPIService.completeTransfer(completedDataTransfer.getTransferProcessId());
    }

}
