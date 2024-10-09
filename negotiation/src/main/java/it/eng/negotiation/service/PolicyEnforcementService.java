package it.eng.negotiation.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Service;

import it.eng.negotiation.exception.PolicyEnforcementException;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.Permission;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PolicyEnforcementService {
	
	private PolicyManager policyManager;
	
	public PolicyEnforcementService(PolicyManager policyManager) {
		super();
		this.policyManager = policyManager;
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
			valid = validateCount(agreementId, constraint);
			break;
		case DATE_TIME:
			valid = validateDateTime(constraint);
			break;
		default:
			log.warn("Left operand not supported {}", constraint.getLeftOperand().name());
			return false;
		}
		return valid;
	}

	private boolean validateDateTime(Constraint constraint) {
		boolean valid;
		log.debug("Validating date time constraint");
		Instant constraintDateTime = null;
		valid = false;
		try {
			constraintDateTime = Instant.parse(constraint.getRightOperand());
		} catch (DateTimeParseException e) {
			log.error("Could not parse following date {}", constraint.getRightOperand());
			return valid;
		}
		switch (constraint.getOperator()) {
			case EQ:
				valid = Instant.now().equals(constraintDateTime);
				break;
			case LT:
				valid =  Instant.now().isBefore(constraintDateTime);
				break;
			case GT:
				valid =  Instant.now().isAfter(constraintDateTime);
				break;
			default:
				log.warn("Operator not supported {}", constraint.getOperator().name());
				return valid;
		}
		return valid;
	}

	private boolean validateCount(String agreementId, Constraint constraint) {
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
			case EQ:
				if(count == Integer.valueOf(constraint.getRightOperand())) {
					valid = true;
				}
				log.debug("break");
				break;
			case LT:
				if(count < Integer.valueOf(constraint.getRightOperand())) {
					valid = true;
				}
				break;
			case GT: 
				if(count > Integer.valueOf(constraint.getRightOperand())) {
					valid = true;
				}
				break;
			case GTEQ:
				if(count >= Integer.valueOf(constraint.getRightOperand())) {
					valid = true;
				}
				break;
			case LTEQ:
				if(count <= Integer.valueOf(constraint.getRightOperand())) {
					valid = true;
				}
				break;
			default:
				log.warn("Operator not supported {}", constraint.getOperator().name());
				valid = false;
				break;
		}
		return valid;
	}
}
