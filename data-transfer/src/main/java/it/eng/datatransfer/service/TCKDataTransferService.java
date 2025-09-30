package it.eng.datatransfer.service;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.tools.model.IConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("tck")
public class TCKDataTransferService implements TransferProcessStrategy {

    private final DataTransferUtil dataTransferUtil;
    private final DataTransferAPIService dataTransferAPIService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TransferProcessRepository transferProcessRepository;

    public TCKDataTransferService(DataTransferUtil dataTransferUtil, DataTransferAPIService dataTransferAPIService, ApplicationEventPublisher applicationEventPublisher, TransferProcessRepository transferProcessRepository) {
        this.dataTransferUtil = dataTransferUtil;
        this.dataTransferAPIService = dataTransferAPIService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.transferProcessRepository = transferProcessRepository;
    }

    @Override
    public TransferProcess findTransferProcess(String consumerPid, String providerPid) {
        log.info("findTransferProcess TCK stub called");
        return null;
    }

    @Override
    public TransferProcess findTransferProcessByProviderPid(String providerPid) {
        TransferProcess tp = transferProcessRepository.findByProviderPid(providerPid).orElseThrow(() -> new RuntimeException("No transfer process found"));
        log.info("Found transfer process: consumerPid {}, providerPid{} , state {}", tp.getConsumerPid(), tp.getProviderPid(), tp.getState());
        return tp;
    }

    @Override
    public TransferProcess findTransferProcessByConsumerPid(String consumerPid) {
        return transferProcessRepository.findByConsumerPid(consumerPid).orElseThrow(() -> new RuntimeException("No transfer process found"));
    }

    @Override
    public TransferProcess initiateDataTransfer(TransferRequestMessage transferRequestMessage) {
        log.info("initiateDataTransfer TCK stub called, {}", TransferSerializer.serializeProtocol(transferRequestMessage));

        return processTp0101(transferRequestMessage);
    }

    @Override
    public TransferProcess startDataTransfer(TransferStartMessage transferStartMessage, String consumerPid, String providerPid) {
        log.info("startDataTransfer TCK stub called");
//        String consumerPidFinal = consumerPid == null ? transferStartMessage.getConsumerPid() : consumerPid;
//        String providerPidFinal = providerPid == null ? transferStartMessage.getProviderPid() : providerPid;
//        TransferProcess transferProcess = transferProcessRepository.findByConsumerPidAndProviderPid(consumerPidFinal, providerPidFinal).orElseThrow();
//        log.info("Found transfer process: consumerPid {}, providerPid{} , state {} - checking transition to STARTED", transferProcess.getConsumerPid(), transferProcess.getProviderPid(), transferProcess.getState());

//        stateTransitionCheck(transferProcess, TransferState.STARTED);
        TransferProcess transferProcessStarted = dataTransferUtil.startDataTransfer(transferStartMessage, consumerPid, providerPid);
        if (transferProcessStarted.getAgreementId().equals("ATPC0201")) {
            log.info("Publishing event to initiate STARTED -> TERMINATE back to provider for agreementId ATPC0201");
            applicationEventPublisher.publishEvent(transferProcessStarted);
        }
        if (transferProcessStarted.getAgreementId().equals("ATPC0202")) {
            log.info("Publishing event to initiate STARTED -> COMPLETION back to provider for agreementId ATPC0202");
            applicationEventPublisher.publishEvent(transferProcessStarted);
        }
        if (transferProcessStarted.getAgreementId().equals("ATPC0203")) {
            log.info("Publishing event to initiate STARTED -> COMPLETION back to provider for agreementId ATPC0203");
            applicationEventPublisher.publishEvent(transferProcessStarted);
        }
        return transferProcessStarted;
    }

    @Override
    public TransferProcess completeDataTransfer(TransferCompletionMessage transferCompletionMessage, String consumerPid, String providerPid) {
        log.info("completeDataTransfer TCK stub called");
        String consumerPidFinal = consumerPid == null ? transferCompletionMessage.getConsumerPid() : consumerPid;
        String providerPidFinal = providerPid == null ? transferCompletionMessage.getProviderPid() : providerPid;
        TransferProcess transferProcess = transferProcessRepository.findByConsumerPidAndProviderPid(consumerPidFinal, providerPidFinal).orElseThrow();

        stateTransitionCheck(transferProcess, TransferState.COMPLETED);


//        if (transferProcess.getAgreementId().equalsIgnoreCase("ATP0301") && transferProcess.getState().equals(TransferState.REQUESTED)) {
//            log.info("Processing ATP0301 - COMPLETION -> ERROR : {}", transferProcess.getId());
//            dataTransferUtil.sendErrorMessage(consumerPid, providerPid, transferProcess.getCallbackAddress());
//            return null;
//        }

        TransferProcess transferProcessCompleted = TransferProcess.Builder.newInstance()
                .id(transferProcess.getId())
                .agreementId(transferProcess.getAgreementId())
                .consumerPid(transferProcess.getConsumerPid())
                .providerPid(transferProcess.getProviderPid())
                .callbackAddress(transferProcess.getCallbackAddress())
                .dataAddress(transferProcess.getDataAddress())
                .isDownloaded(true)
                .dataId(transferProcess.getId())
                .format(transferProcess.getFormat())
                .state(TransferState.COMPLETED)
                .role(transferProcess.getRole())
                .datasetId(transferProcess.getDatasetId())
                .created(transferProcess.getCreated())
                .createdBy(transferProcess.getCreatedBy())
                .modified(transferProcess.getModified())
                .lastModifiedBy(transferProcess.getLastModifiedBy())
                .version(transferProcess.getVersion())
                .build();

        transferProcessRepository.save(transferProcessCompleted);
        return transferProcessCompleted;
    }

    @Override
    public TransferProcess terminateDataTransfer(TransferTerminationMessage transferTerminationMessage, String consumerPid, String providerPid) {
        log.info("terminateDataTransfer TCK stub called");
        TransferProcess transferProcess = transferProcessRepository.findByConsumerPidAndProviderPid(transferTerminationMessage.getConsumerPid(), transferTerminationMessage.getProviderPid())
                .orElseThrow();

        TransferProcess transferProcessTerminated = transferProcess.copyWithNewTransferState(TransferState.TERMINATED);
        log.info("TransferProcess {} state changed to {}", transferProcessTerminated.getId(), transferProcessTerminated.getState());
        return transferProcessRepository.save(transferProcessTerminated);

//        JsonNode jsonNode = dataTransferAPIService.terminateTransfer(transferProcess.getId());
//        return TransferSerializer.deserializePlain(jsonNode, TransferProcess.class);
    }

    @Override
    public TransferProcess suspendDataTransfer(TransferSuspensionMessage transferSuspensionMessage, String consumerPid, String providerPid) {
        log.info("suspendDataTransfer TCK stub called for consumePid {} and providerPid {}", consumerPid, providerPid);
        String consumerPidFinal = consumerPid == null ? transferSuspensionMessage.getConsumerPid() : consumerPid;
        String providerPidFinal = providerPid == null ? transferSuspensionMessage.getProviderPid() : providerPid;
        TransferProcess transferProcess = transferProcessRepository.findByConsumerPidAndProviderPid(consumerPidFinal, providerPidFinal).orElseThrow();
        log.info("Found TransferProcessState {} ", transferProcess.getState());

        if (transferProcess.getRole().equalsIgnoreCase(IConstants.ROLE_PROVIDER)) {
            log.info("Acting as provider, just changes state to SUSPENDED if state transition allows");
            stateTransitionCheck(transferProcess, TransferState.SUSPENDED);

            TransferProcess transferProcessSuspended = transferProcess.copyWithNewTransferState(TransferState.SUSPENDED);
            return transferProcessRepository.save(transferProcessSuspended);
        }

        stateTransitionCheck(transferProcess, TransferState.SUSPENDED);
        log.info("Acting as consumer, suspend the transfer process");
        TransferProcess transferProcessSuspended = transferProcess.copyWithNewTransferState(TransferState.SUSPENDED);
        return transferProcessRepository.save(transferProcessSuspended);
//        JsonNode jsonNOdeSuspended = dataTransferAPIService.suspendTransfer(transferProcess.getId());
//        return TransferSerializer.deserializePlain(jsonNOdeSuspended, TransferProcess.class);
    }

    @Override
    public boolean isDataTransferStarted(String consumerPid, String providerPid) {
        log.info("isDataTransferStarted TCK stub called");
        return false;
    }

    @Override
    public TransferProcess requestTransfer(TCKRequest tckRequest) {
        TransferProcess tpInitialized = transferProcessRepository.findByAgreementId(tckRequest.getAgreementId())
                .orElseThrow(() -> new RuntimeException("No transfer process found"));
        log.info("TransferProcess found for agreementId {}: consumerPid {}, providerPid {} , state {}",
                tpInitialized.getAgreementId(), tpInitialized.getConsumerPid(), tpInitialized.getProviderPid(), tpInitialized.getState());

        JsonNode jsonNodeRequested = dataTransferUtil.requestTransfer(tpInitialized, tckRequest.getFormat());
        TransferProcess transferProcess = TransferSerializer.deserializePlain(jsonNodeRequested, TransferProcess.class);
        if (transferProcess.getAgreementId().equals("ATPC0205")) {
            log.info("Publishing event to initiate REQUESTED -> TERMINATED back to provider for agreementId ATPC0205");
            applicationEventPublisher.publishEvent(transferProcess);
        }
        return TransferSerializer.deserializePlain(jsonNodeRequested, TransferProcess.class);
    }

    private TransferProcess processTp0101(TransferRequestMessage transferRequestMessage) {
        return dataTransferUtil.initiateDataTransfer(transferRequestMessage);
    }

    @EventListener(classes = TransferProcess.class)
    public void onTransferProcessEvent(TransferProcess transferProcess) throws InterruptedException {
        log.info("TCKDataTransferService received event for Agreement id: {} with state {}", transferProcess.getAgreementId(), transferProcess.getState());
        log.info("ConsumerPid: {}, ProviderPid: {}", transferProcess.getConsumerPid(), transferProcess.getProviderPid());
        try {
            Thread.sleep(2000);
            log.info("sleep over");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if ((transferProcess.getAgreementId().equalsIgnoreCase("ATP0101")
                || transferProcess.getAgreementId().equalsIgnoreCase("ATP0102")
                || transferProcess.getAgreementId().equalsIgnoreCase("ATP0103")
                || transferProcess.getAgreementId().equalsIgnoreCase("ATP0104")
                || transferProcess.getAgreementId().equalsIgnoreCase("ATP0201")
                || transferProcess.getAgreementId().equalsIgnoreCase("ATP0202")
                || transferProcess.getAgreementId().equalsIgnoreCase("ATP0203")
                || transferProcess.getAgreementId().equalsIgnoreCase("ATP0204")
                || transferProcess.getAgreementId().equalsIgnoreCase("ATP0303")
                || transferProcess.getAgreementId().equalsIgnoreCase("ATP0304")
                || transferProcess.getAgreementId().equalsIgnoreCase("ATP0305")
                || transferProcess.getAgreementId().equalsIgnoreCase("ATP0306"))
                && transferProcess.getState().equals(TransferState.REQUESTED)) {
            log.info("Processing {} - REQUESTED -> STARTED : {}", transferProcess.getAgreementId(), transferProcess.getId());
            JsonNode jsonNode = dataTransferAPIService.startTransfer(transferProcess.getId());
            applicationEventPublisher.publishEvent(TransferSerializer.deserializePlain(jsonNode, TransferProcess.class));
        }

        if (transferProcess.getAgreementId().equals("ATPC0201") && transferProcess.getState().equals(TransferState.STARTED)) {
            dataTransferAPIService.terminateTransfer(transferProcess.getId());
        }
        if (transferProcess.getAgreementId().equals("ATPC0202") && transferProcess.getState().equals(TransferState.STARTED)) {
            dataTransferAPIService.completeTransfer(transferProcess.getId());
        }
        if (transferProcess.getAgreementId().equals("ATPC0203") && transferProcess.getState().equals(TransferState.STARTED)) {
            dataTransferAPIService.suspendTransfer(transferProcess.getId());
            Thread.sleep(2000);
            dataTransferAPIService.terminateTransfer(transferProcess.getId());
        }
        if (transferProcess.getAgreementId().equals("ATPC0205") && transferProcess.getState().equals(TransferState.REQUESTED)) {
            dataTransferAPIService.terminateTransfer(transferProcess.getId());
        }

        if (transferProcess.getAgreementId().equalsIgnoreCase("ATP0101")
                && transferProcess.getState().equals(TransferState.STARTED)) {
            log.info("Processing ATP0101 - STARTED -> TERMINATED: {}", transferProcess.getId());
            dataTransferAPIService.terminateTransfer(transferProcess.getId());
        }
        if (transferProcess.getAgreementId().equalsIgnoreCase("ATP0102") && transferProcess.getState().equals(TransferState.STARTED)) {
            log.info("Processing ATP0101 - STARTED -> TERMINATED: {}", transferProcess.getId());
            dataTransferAPIService.completeTransfer(transferProcess.getId());
        }
        if ((transferProcess.getAgreementId().equalsIgnoreCase("ATP0103") ||
                transferProcess.getAgreementId().equalsIgnoreCase("ATP0104"))
                && transferProcess.getState().equals(TransferState.STARTED)) {
            log.info("Processing {} - STARTED -> SUSPENDED", transferProcess.getAgreementId());
            JsonNode jsonNode = dataTransferAPIService.suspendTransfer(transferProcess.getId());
            // need to transit to TERMINATED state
            applicationEventPublisher.publishEvent(TransferSerializer.deserializePlain(jsonNode, TransferProcess.class));
        }

        if (transferProcess.getAgreementId().equalsIgnoreCase("ATP0104")
                && transferProcess.getState().equals(TransferState.SUSPENDED)) {
            dataTransferAPIService.startTransfer(transferProcess.getId());
            Thread.sleep(2000);
            dataTransferAPIService.completeTransfer(transferProcess.getId());

        }
        if (transferProcess.getAgreementId().equalsIgnoreCase("ATP0103")
                && transferProcess.getState().equals(TransferState.SUSPENDED)) {
            log.info("Processing ATP0101 - SUSPENDED -> TERMINATE: {}", transferProcess.getId());
            dataTransferAPIService.terminateTransfer(transferProcess.getId());
        }
        if (transferProcess.getAgreementId().equalsIgnoreCase("ATP0105") && transferProcess.getState().equals(TransferState.REQUESTED)) {
            log.info("Processing ATP0105 - REQUESTED -> TERMINATED: {}", transferProcess.getId());
            dataTransferAPIService.terminateTransfer(transferProcess.getId());
        }

//        if (transferProcess.getAgreementId().equalsIgnoreCase("ATP0303") && transferProcess.getState().equals(TransferState.STARTED)) {
//            log.info("Processing ATP0303 - STARTED -> SUSPENDED: {}", transferProcess.getId());
//            dataTransferAPIService.suspendTransfer(transferProcess.getId());
//        }
    }

    private void stateTransitionCheck(TransferProcess transferProcess, TransferState stateToTransit) {
        if (!transferProcess.getState().canTransitTo(stateToTransit)) {
            throw new TransferProcessInvalidStateException("TransferProcess is in invalid state " + transferProcess.getState(),
                    transferProcess.getConsumerPid(), transferProcess.getProviderPid());
        }
    }

}
