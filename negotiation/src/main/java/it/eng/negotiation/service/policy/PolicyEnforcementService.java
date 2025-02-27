package it.eng.negotiation.service.policy;

import org.springframework.stereotype.Service;

import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.Permission;
import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import it.eng.negotiation.service.policy.validators.CountPolicyValidator;
import it.eng.negotiation.service.policy.validators.DateTimePolicyValidator;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PolicyEnforcementService {
	
	private CountPolicyValidator countPolicyValidator;
	private DateTimePolicyValidator dateTimePolicyValidator;
	private PolicyEnforcementRepository repository;
	
	public PolicyEnforcementService(CountPolicyValidator countPolicyValidator, DateTimePolicyValidator dateTimePolicyValidator,
			PolicyEnforcementRepository repository) {
		super();
		this.countPolicyValidator = countPolicyValidator;
		this.dateTimePolicyValidator = dateTimePolicyValidator;
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

	public boolean isAgreementValid(Agreement agreement) {
		return agreement.getPermission().stream().allMatch(p -> validatePermission(agreement.getId(), p));
	}
	
	private boolean validatePermission(String agreementId, Permission permission) {
		return permission.getConstraint().stream().allMatch(c -> validateConstraint(agreementId, c));
	}
	
	private boolean validateConstraint(String agreementId, Constraint constraint) {
		boolean valid = false;
		switch (constraint.getLeftOperand()) {
		case COUNT:
			valid = countPolicyValidator.validateCount(agreementId, constraint);
			break;
		case DATE_TIME:
			valid = dateTimePolicyValidator.validateDateTime(constraint);
			break;
		default:
			log.warn("Constraint not supported {}", constraint.getLeftOperand().name());
			return false;
		}
		return valid;
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
