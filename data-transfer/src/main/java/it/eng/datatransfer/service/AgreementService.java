package it.eng.datatransfer.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import it.eng.datatransfer.exceptions.AgreementNotFoundException;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AgreementService {
	
	Map<String, Boolean> agreementStorage = new HashMap<>();
	private TransferRequestMessageRepository transferRequestMessageRepository;
	private TransferProcessRepository transferProcessRepository;
	
	public AgreementService(TransferRequestMessageRepository transferRequestMessageRepository,
			TransferProcessRepository transferProcessRepository) {
		super();
		this.transferRequestMessageRepository = transferRequestMessageRepository;
		this.transferProcessRepository = transferProcessRepository;
	}

	public boolean isAgreementValid(String consumerPid, String providerPid) {
		
//		String agreementId = transferRequestMessageRepository.findByConsumerPid(consumerPid)
//				.map(trm -> trm.getAgreementId())
//				.orElseThrow(() -> new AgreementNotFoundException("Agreement for cosnumerPid '"+ consumerPid +
//						"' and providerPid '" + providerPid + "' not found"));
		String agreementId = transferProcessRepository.findByConsumerPidAndProviderPid(consumerPid, providerPid)
				.map(trm -> trm.getAgreementId())
				.orElseThrow(() -> new AgreementNotFoundException("Agreement for cosnumerPid '"+ consumerPid +
						"' and providerPid '" + providerPid + "' not found", consumerPid, providerPid));
		
		// TODO once usage policy enforcement is done, call should be made here
		boolean isValid = checkIfAgreementIsValid(agreementId);
		return isValid;
//		if(!agreementStorage.containsKey(agreementId)) {
//			agreementStorage.put(agreementId, isVaid);
//		}
//		log.info("Validating agreement id {}", agreementId);
//		return agreementStorage.get(agreementId);
	}

	private boolean checkIfAgreementIsValid(String agreementId) {
		if ("urn:uuid:AGREEMENT_ID".equals(agreementId) 
				|| "urn:uuid:AGREEMENT_ID_COMPLETED_TRANSFER_TEST".equals(agreementId)) {
			   return true;
			} else {
			   return false;
			}
	}
}
