package it.eng.datatransfer.service;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import it.eng.datatransfer.event.TransferProcessChangeEvent;
import it.eng.datatransfer.exceptions.AgreementNotFoundException;
import it.eng.datatransfer.exceptions.TransferProcessExistsException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.exceptions.TransferProcessNotFoundException;
import it.eng.datatransfer.model.Serializer;
import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.model.TransferSuspensionMessage;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DataTransferService {

	private final TransferProcessRepository transferProcessRepository;
	private final ApplicationEventPublisher publisher;
	
	private final OkHttpRestClient okHttpRestClient;
	private final CredentialUtils credentialUtils;
	private final DataTransferProperties properties;
	
	public DataTransferService(TransferProcessRepository transferProcessRepository, ApplicationEventPublisher publisher, 
			OkHttpRestClient okHttpRestClient,
			CredentialUtils credentialUtils, DataTransferProperties properties) {
		super();
		this.transferProcessRepository = transferProcessRepository;
		this.publisher = publisher;
		this.okHttpRestClient = okHttpRestClient;
		this.credentialUtils = credentialUtils;
		this.properties = properties;
	}

	/**
	 * If TransferProcess for given consumerPid and providerPid exists and state is STARTED</br>
	 * Note: those 2 Pid's are not to be mixed with Contract Negotiation ones. They are unique
	 * @param consumerPid
	 * @param providerPid
	 * @return
	 */
	public boolean isDataTransferStarted(String consumerPid ,String providerPid ) {
		return transferProcessRepository.findByConsumerPidAndProviderPid(consumerPid, providerPid)
				.map(tp -> TransferState.STARTED.equals(tp.getState()))
				.orElse(false);
	}

	public TransferProcess findTransferProcessByProviderPid(String providerPid) {
		return transferProcessRepository.findByProviderPid(providerPid)
				.orElseThrow(() -> new TransferProcessNotFoundException("TransferProcess with providerPid " + providerPid + " not found"));
	}

	public TransferProcess initiateDataTransfer(TransferRequestMessage transferRequestMessage) {
		transferProcessRepository.findByAgreementId(transferRequestMessage.getAgreementId())
			.ifPresent(tp -> {
				throw new TransferProcessExistsException("For agreementId " + tp.getAgreementId() + 
					" there is already transfer process created.", tp.getConsumerPid());
				});
		// check if agreement exists
//		/{agreementId}/valid
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol("http://localhost:" + 
				properties.serverPort() + ApiEndpoints.NEGOTIATION_AGREEMENTS_V1 + "/"+  transferRequestMessage.getAgreementId() + "/valid", 
				null, 
				credentialUtils.getAPICredentials());
		if (!response.isSuccess()) {
			throw new AgreementNotFoundException("Agreement with id " + transferRequestMessage.getAgreementId()+ " not found",
					transferRequestMessage.getConsumerPid(), "urn:uuid:" + UUID.randomUUID());
		}
		
		// TODO save also transferRequestMessage once we implement provider push data - will need information where to push
		
		TransferProcess transferProcessRequested = TransferProcess.Builder.newInstance()
				.agreementId(transferRequestMessage.getAgreementId())
				.callbackAddress(transferRequestMessage.getCallbackAddress())
				.consumerPid(transferRequestMessage.getConsumerPid())
				.state(TransferState.REQUESTED)
				.build();
		transferProcessRepository.save(transferProcessRequested);
		log.info("Requested TransferProcess created");
		if(log.isDebugEnabled()) {
			log.debug("message: " + Serializer.serializePlain(transferProcessRequested));
		}
		return transferProcessRequested;
	}

	/**
	 * Transfer from REQUESTED or SUSPENDED to STARTED state
	 * @param transferStartMessage TransferStartMessage
	 * @param consumerPid null in case of provider side 
	 * @param providerPid null in case of consumer callback controller
	 * @return TransferProcess with STARTED state
	 */
	public TransferProcess startDataTransfer(TransferStartMessage transferStartMessage, String consumerPid, String providerPid) {
		String consumerPidFinal = consumerPid == null ? transferStartMessage.getConsumerPid() : consumerPid;
		String providerPidFinal = providerPid == null ? transferStartMessage.getProviderPid() : providerPid;
		log.debug("Starting data transfer for consumerPid {} and providerPid {}", consumerPidFinal, providerPidFinal);

		TransferProcess transferProcessRequested = findTransferProcess(consumerPidFinal, providerPidFinal);
		stateTransitionCheck(transferProcessRequested, TransferState.STARTED);

		TransferProcess transferProcessStarted = transferProcessRequested.copyWithNewTransferState(TransferState.STARTED);
		transferProcessRepository.save(transferProcessStarted);
		publisher.publishEvent(TransferProcessChangeEvent.Builder.newInstance()
				.oldTransferProcess(transferProcessRequested)
				.newTransferProcess(transferProcessStarted)
				.build());
		return transferProcessStarted;
	}
	
	/**
	 * Finds transfer process, check if status is correct, publish event and update state to COMPLETED
	 * @param transferCompletionMessage
	 * @param consumerPid
	 * @param providerPid
	 * @return
	 */
	public TransferProcess completeDataTransfer(TransferCompletionMessage transferCompletionMessage, String consumerPid,
			String providerPid) {
		String consumerPidFinal = consumerPid == null ? transferCompletionMessage.getConsumerPid() : consumerPid;
		String providerPidFinal = providerPid == null ? transferCompletionMessage.getProviderPid() : providerPid;
		log.debug("Completing data transfer for consumerPid {} and providerPid {}", consumerPidFinal, providerPidFinal);

		TransferProcess transferProcessStarted = findTransferProcess(consumerPidFinal, providerPidFinal);
		stateTransitionCheck(transferProcessStarted, TransferState.COMPLETED);

		TransferProcess transferProcessCompleted = transferProcessStarted.copyWithNewTransferState(TransferState.COMPLETED);
		transferProcessRepository.save(transferProcessCompleted);
		publisher.publishEvent(TransferProcessChangeEvent.Builder.newInstance()
				.oldTransferProcess(transferProcessStarted)
				.newTransferProcess(transferProcessCompleted)
				.build());
		return transferProcessCompleted;
	}

	public TransferProcess suspendDataTransfer(TransferSuspensionMessage transferSuspensionMessage, String consumerPid,
			String providerPid) {
		String consumerPidFinal = consumerPid == null ? transferSuspensionMessage.getConsumerPid() : consumerPid;
		String providerPidFinal = providerPid == null ? transferSuspensionMessage.getProviderPid() : providerPid;
		log.debug("Suspending data transfer for consumerPid {} and providerPid {}", consumerPidFinal, providerPidFinal);

		TransferProcess transferProcessStarted = findTransferProcess(consumerPidFinal, providerPidFinal);
		stateTransitionCheck(transferProcessStarted, TransferState.SUSPENDED);

		TransferProcess transferProcessSuspended = transferProcessStarted.copyWithNewTransferState(TransferState.SUSPENDED);
		transferProcessRepository.save(transferProcessSuspended);
		publisher.publishEvent(TransferProcessChangeEvent.Builder.newInstance()
				.oldTransferProcess(transferProcessStarted)
				.newTransferProcess(transferProcessSuspended)
				.build());
		publisher.publishEvent(transferSuspensionMessage);
		return transferProcessSuspended;
	}
	
	private TransferProcess findTransferProcess(String consumerPid, String providerPidFinal) {
		TransferProcess transferProcessRequested = transferProcessRepository.findByConsumerPidAndProviderPid(consumerPid, providerPidFinal)
			.orElseThrow(() -> new TransferProcessNotFoundException("Transfer process for consumerPid " + consumerPid
			 + " and providerPid " + consumerPid + " not found"));
		return transferProcessRequested;
	}
	
	private void stateTransitionCheck(TransferProcess transferProcess, TransferState stateToTransit) {
		if(!transferProcess.getState().canTransitTo(stateToTransit)) {
			throw new TransferProcessInvalidStateException("TransferProcess is in invalid state " + transferProcess.getState(),
					transferProcess.getConsumerPid(), transferProcess.getProviderPid());
		}
	}

}
