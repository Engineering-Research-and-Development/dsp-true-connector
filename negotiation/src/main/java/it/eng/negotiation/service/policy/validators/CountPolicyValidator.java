package it.eng.negotiation.service.policy.validators;

import org.springframework.stereotype.Component;

import it.eng.negotiation.exception.PolicyEnforcementException;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.service.policy.PolicyManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CountPolicyValidator {

	private PolicyManager policyManager;
	
	public CountPolicyValidator(PolicyManager policyManager) {
		super();
		this.policyManager = policyManager;
	}

	public boolean validateCount(String agreementId, Constraint constraint) {
		log.debug("Validating count constraint");
		boolean valid = false;
		int count = 0;
		try {
			count = policyManager.getAccessCount(agreementId);
		} catch (PolicyEnforcementException e) {
			log.error(e.getMessage());
			return false;
		}
		switch (constraint.getOperator()) {
//			case EQ:
//				if(count == Integer.valueOf(constraint.getRightOperand())) {
//					valid = true;
//				}
//				break;
			case LT:
				if(count < Integer.valueOf(constraint.getRightOperand())) {
					valid = true;
				}
				break;
			case LTEQ:
				if(count <= Integer.valueOf(constraint.getRightOperand())) {
					valid = true;
				}
				break;
//			case GT: 
//				if(count > Integer.valueOf(constraint.getRightOperand())) {
//					valid = true;
//				}
//				break;
//			case GTEQ:
//				if(count >= Integer.valueOf(constraint.getRightOperand())) {
//					valid = true;
//				}
//				break;
			default:
				log.warn("Operator not supported {}", constraint.getOperator().name());
				valid = false;
				break;
		}
		return valid;
	}
}
