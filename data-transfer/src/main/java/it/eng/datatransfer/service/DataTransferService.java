package it.eng.datatransfer.service;

import org.springframework.stereotype.Service;

import it.eng.datatransfer.exceptions.TransferProcessExistsException;
import it.eng.datatransfer.exceptions.TransferProcessNotFoundException;
import it.eng.datatransfer.model.Serializer;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DataTransferService {

	private TransferProcessRepository transferProcessRepository;
	
	public DataTransferService(TransferProcessRepository transferProcessRepository) {
		super();
		this.transferProcessRepository = transferProcessRepository;
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
}
