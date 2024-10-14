package it.eng.negotiation.service.policy.validators;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

import it.eng.negotiation.model.Constraint;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DateTimePolicyValidator {

	public boolean validateDateTime(Constraint constraint) {
		boolean valid = false;
		log.debug("Validating date time constraint");
		Instant constraintDateTime = null;
		try {
			constraintDateTime = Instant.parse(constraint.getRightOperand());
		} catch (DateTimeParseException e) {
			log.error("Could not parse following date {}", constraint.getRightOperand());
			return valid;
		}
		switch (constraint.getOperator()) {
//			case EQ:
//				valid = Instant.now().equals(constraintDateTime);
//				break;
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
}
