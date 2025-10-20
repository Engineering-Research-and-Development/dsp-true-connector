package it.eng.datatransfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import it.eng.datatransfer.rest.protocol.DataTransferCallback;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@Profile("tck")
public class TCKDataTransferService extends AbstractDataTransferService {

    private final DataTransferAPIService dataTransferAPIService;
    private final AuditEventPublisher auditEventPublisher;
    private final OkHttpRestClient okHttpRestClient;
    private final DataTransferProperties dataTransferProperties;
    private final CredentialUtils credentialUtils;

    private final ObjectMapper mapper = new ObjectMapper();

    public TCKDataTransferService(DataTransferAPIService dataTransferAPIService,
                                  TransferProcessRepository transferProcessRepository,
                                  TransferRequestMessageRepository transferRequestMessageRepository,
                                  AuditEventPublisher auditEventPublisher,
                                  OkHttpRestClient okHttpRestClient,
                                  DataTransferProperties dataTransferProperties, CredentialUtils credentialUtils) {
        super(transferProcessRepository, auditEventPublisher, okHttpRestClient, transferRequestMessageRepository);
        this.dataTransferAPIService = dataTransferAPIService;
        this.auditEventPublisher = auditEventPublisher;
        this.okHttpRestClient = okHttpRestClient;
        this.dataTransferProperties = dataTransferProperties;
        this.credentialUtils = credentialUtils;
    }

    @Override
    public TransferProcess startDataTransfer(TransferStartMessage transferStartMessage, String consumerPid, String providerPid) {
        log.info("startDataTransfer TCK stub called");
        TransferProcess transferProcessStarted = super.startDataTransfer(transferStartMessage, consumerPid, providerPid);

        if (transferProcessStarted.getAgreementId().equals("ATPC0201")) {
            log.info("Publishing event to initiate STARTED -> TERMINATE back to provider for agreementId ATPC0201");
            auditEventPublisher.publishEvent(transferProcessStarted);
        }
        if (transferProcessStarted.getAgreementId().equals("ATPC0202")) {
            log.info("Publishing event to initiate STARTED -> COMPLETION back to provider for agreementId ATPC0202");
            auditEventPublisher.publishEvent(transferProcessStarted);
        }
        if (transferProcessStarted.getAgreementId().equals("ATPC0203")) {
            log.info("Publishing event to initiate STARTED -> COMPLETION back to provider for agreementId ATPC0203");
            auditEventPublisher.publishEvent(transferProcessStarted);
        }
        return transferProcessStarted;
    }

    @Override
    public TransferProcess requestTransfer(TCKRequest tckRequest) {
        TransferProcess transferProcessInitialized = findByAgreementId(tckRequest.getAgreementId());
        log.info("TransferProcess found for agreementId {}: consumerPid {}, providerPid {} , state {}",
                transferProcessInitialized.getAgreementId(),
                transferProcessInitialized.getConsumerPid(),
                transferProcessInitialized.getProviderPid(),
                transferProcessInitialized.getState());

        stateTransitionCheck(transferProcessInitialized, TransferState.REQUESTED);

        TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
                .agreementId(transferProcessInitialized.getAgreementId())
                .callbackAddress(dataTransferProperties.consumerCallbackAddress())
                .consumerPid(transferProcessInitialized.getConsumerPid())
                .format(tckRequest.getFormat())
                .dataAddress(null)
                .build();

        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
                DataTransferCallback.getConsumerDataTransferRequest(transferProcessInitialized.getCallbackAddress()),
                TransferSerializer.serializeProtocolJsonNode(transferRequestMessage),
                credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);

        TransferProcess transferProcessForDB;
        if (response.isSuccess()) {
            try {
                JsonNode jsonNode = mapper.readTree(response.getData());
                TransferProcess transferProcessFromResponse = TransferSerializer.deserializeProtocol(jsonNode, TransferProcess.class);

                transferProcessForDB = TransferProcess.Builder.newInstance()
                        .id(transferProcessInitialized.getId())
                        .agreementId(transferProcessInitialized.getAgreementId())
                        .consumerPid(transferProcessInitialized.getConsumerPid())
                        .providerPid(transferProcessFromResponse.getProviderPid())
                        .format(tckRequest.getFormat())
                        .dataAddress(null)
                        .isDownloaded(transferProcessInitialized.isDownloaded())
                        .dataId(transferProcessInitialized.getDataId())
                        .callbackAddress(transferProcessInitialized.getCallbackAddress())
                        .role(IConstants.ROLE_CONSUMER)
                        .state(transferProcessFromResponse.getState())
                        .created(transferProcessInitialized.getCreated())
                        .createdBy(transferProcessInitialized.getCreatedBy())
                        .modified(transferProcessInitialized.getModified())
                        .lastModifiedBy(transferProcessInitialized.getLastModifiedBy())
                        .version(transferProcessInitialized.getVersion())
                        // although not needed on consumer side it is added here to avoid duplicate id exception from mongodb
                        .datasetId(transferProcessInitialized.getDatasetId())
                        .build();

                saveTransferProcess(transferProcessForDB);
                log.info("Transfer process {} saved", transferProcessForDB.getId());
            } catch (JsonProcessingException e) {
                log.error("Transfer process from response not valid");
                throw new DataTransferAPIException(e.getLocalizedMessage(), e);
            }
        } else {
            log.info("Error response received!");
            log.error("Transfer process from response not valid");
            JsonNode jsonNode;
            try {
                jsonNode = mapper.readTree(response.getData());
                TransferError transferError = TransferSerializer.deserializeProtocol(jsonNode, TransferError.class);
                Map<String, Object> details = new HashMap<>();
                details.put("transferProcess", transferProcessInitialized);
                details.put("role", IConstants.ROLE_API);
                details.put("errorMessage", transferError);
                if (transferProcessInitialized.getConsumerPid() != null) {
                    details.put("consumerPid", transferProcessInitialized.getConsumerPid());
                }
                if (transferProcessInitialized.getProviderPid() != null) {
                    details.put("providerPid", transferProcessInitialized.getProviderPid());
                }
                auditEventPublisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_REQUESTED,
                        "Transfer process request failed",
                        details);
                throw new DataTransferAPIException(transferError, "Error making request");
            } catch (JsonProcessingException ex) {
                throw new DataTransferAPIException("Error occurred");
            }
        }

        if (transferProcessForDB.getAgreementId().equals("ATPC0205")) {
            log.info("Publishing event to initiate REQUESTED -> TERMINATED back to provider for agreementId ATPC0205");
            auditEventPublisher.publishEvent(transferProcessForDB);
        }
        return transferProcessForDB;
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
            auditEventPublisher.publishEvent(TransferSerializer.deserializePlain(jsonNode, TransferProcess.class));
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
            auditEventPublisher.publishEvent(TransferSerializer.deserializePlain(jsonNode, TransferProcess.class));
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
    }

    public void sendErrorMessage(String consumerPid, String providerPid, String targetAddress) {
        TransferError transferError = TransferError.Builder.newInstance()
                .consumerPid(consumerPid)
                .providerPid(providerPid)
                .code("tck-error")
                .build();
        Request.Builder requestBuilder = new Request.Builder().url(targetAddress);
        RequestBody body;
        JsonNode jsonNode = TransferSerializer.serializePlainJsonNode(transferError);
        if (jsonNode != null) {
            body = RequestBody.create(jsonNode.toPrettyString(), MediaType.parse("application/json"));
        } else {
            body = RequestBody.create("", MediaType.parse("application/json"));
        }
        requestBuilder.post(body);
        try (Response response = okHttpRestClient.executeCall(requestBuilder.build())) {
            int code = response.code();
            log.info("Status {}", code);
            //why is this not JSONNode
            String resp = null;
            if (response.body() != null) {
                resp = response.body().string();
            }
            log.info("Response received: {}", resp);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
