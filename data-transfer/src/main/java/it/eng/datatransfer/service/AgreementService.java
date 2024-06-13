package it.eng.datatransfer.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import it.eng.datatransfer.exceptions.AgreementNotFoundException;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AgreementService {
	
	Map<String, Boolean> agreementStorage = new HashMap<>();
	private TransferRequestMessageRepository transferRequestMessageRepository;
	
	public AgreementService(TransferRequestMessageRepository transferRequestMessageRepository) {
		super();
		this.transferRequestMessageRepository = transferRequestMessageRepository;
	}

	public boolean isAgreementValid(String consumerPid, String providerPid) {
		
		String agreementId = transferRequestMessageRepository.findByConsumerPid(consumerPid)
				.map(trm -> trm.getAgreementId())
				.orElseThrow(() -> new AgreementNotFoundException("Agreement for cosnumerPid '"+ consumerPid +
						"' and providerPid '" + providerPid + "' not found"));
		
		// TODO once usage policy enforcement is done, call should be made here
		if(!agreementStorage.containsKey(agreementId)) {
			boolean isVaid = checkIfAgreementIsValid(agreementId);
			agreementStorage.put(agreementId, isVaid);
		}
		log.info("Validating agreement id {}", agreementId);
		return agreementStorage.get(agreementId);
	}

	private boolean checkIfAgreementIsValid(String agreementId) {
		if ((Integer.valueOf(agreementId) % 2) == 0) {
			   return true;
			} else {
			   return false;
			}
	}
}
