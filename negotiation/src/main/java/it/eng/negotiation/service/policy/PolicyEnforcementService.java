package it.eng.negotiation.service.policy;

import org.springframework.stereotype.Service;

import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PolicyEnforcementService {
	
	private PolicyEnforcementRepository repository;
	
	public PolicyEnforcementService(PolicyEnforcementRepository repository) {
		super();
		this.repository = repository;
	}
	
	/**
	 * Check if PolicyEnforcement for agreement exists.
	 * Must be sure that policy can be enforced after data is returned
	 * @param agreementId
	 * @return boolean value if policy enforcement exists or not
	 */
	public boolean policyEnforcementExists(String agreementId) {
		return repository.findByAgreementId(agreementId).isPresent();
	}
	
	/**
	 * Crete policy enforcement object in DB.
	 * @param agreementId
	 */
	public void createPolicyEnforcement(String agreementId) {
		PolicyEnforcement pe = new PolicyEnforcement();
		pe.setAgreementId(agreementId);
		pe.setCount(1);
		repository.save(pe);
	}
}
