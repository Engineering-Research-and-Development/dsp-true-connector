package it.eng.negotiation.service.policy;

import org.springframework.stereotype.Component;

import it.eng.negotiation.exception.PolicyEnforcementException;
import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PolicyManager {
	
	private PolicyEnforcementRepository repository;
	
	public PolicyManager(PolicyEnforcementRepository repository) {
		super();
		this.repository = repository;
	}

	public int getAccessCount(String agreementId) {
		return repository.findByAgreementId(agreementId).map(pe -> pe.getCount())
				.orElseThrow(() -> new PolicyEnforcementException("PolicyManager for agreementId '" + agreementId + "' not found"));
	}
	
	public synchronized void updateAccessCount(String agreementId) {
		log.info("Updating access count for agreementId {}", agreementId);
		PolicyEnforcement pe = repository.findByAgreementId(agreementId)
				.orElseThrow(() -> new PolicyEnforcementException("PolicyManager for agreementId  '" + agreementId + "' not found"));
		pe.setCount(pe.getCount() + 1);
		repository.save(pe);
		log.debug("Access count updated to {}", pe.getCount());
	}
}
