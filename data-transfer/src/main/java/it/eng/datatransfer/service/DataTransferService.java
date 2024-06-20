package it.eng.datatransfer.service;

import org.springframework.stereotype.Service;

import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;

@Service
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
}
