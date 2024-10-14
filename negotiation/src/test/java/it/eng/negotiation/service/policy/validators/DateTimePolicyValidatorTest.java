package it.eng.negotiation.service.policy.validators;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Operator;

@ExtendWith(MockitoExtension.class)
class DateTimePolicyValidatorTest {

	@InjectMocks
	private DateTimePolicyValidator dateTimePolicyValidator;
	
	@Test
	@DisplayName("DateTime enforcement - not reached")
	void dateTime_lt_OK() {
		assertTrue(dateTimePolicyValidator.validateDateTime(DATE_TIME_LT));
	}
	
	@Test
	@DisplayName("DateTime enforcement - expired")
	void dateTimeExired() {
		assertFalse(dateTimePolicyValidator.validateDateTime(DATE_TIME_EXPIRED));
	}
	
	@Test
	@DisplayName("DateTime enforcement - greather than")
	void dateTime_gt() {
		assertTrue(dateTimePolicyValidator.validateDateTime(DATE_TIME_GT));
	}
	
	@Test
	@DisplayName("DateTime enforcement - greather than - in future")
	void dateTime_gt_not_yet() {
		assertFalse(dateTimePolicyValidator.validateDateTime(DATE_TIME_GT_NOT_YET));
	}
	
	@Test
	@DisplayName("DateTime enforcement - invalid date")
	void dateTime_invalid_date() {
		assertFalse(dateTimePolicyValidator.validateDateTime(Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.DATE_TIME)
				.operator(Operator.LT)
				.rightOperand("INVALID_DATE")
				.build()));
	}

	private Constraint DATE_TIME_LT = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.DATE_TIME)
			.operator(Operator.LT)
			.rightOperand(Instant.now().plus(5, ChronoUnit.DAYS).toString())
			.build();
	
	private Constraint DATE_TIME_EXPIRED = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.DATE_TIME)
			.operator(Operator.LT)
			.rightOperand(Instant.now().minus(5, ChronoUnit.DAYS).toString())
			.build();
	
	private Constraint DATE_TIME_GT = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.DATE_TIME)
			.operator(Operator.GT)
			.rightOperand("2024-10-01T06:00:00Z")
			.build();
	
	private Constraint DATE_TIME_GT_NOT_YET = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.DATE_TIME)
			.operator(Operator.GT)
			.rightOperand("2099-10-01T06:00:00Z")
			.build();
}
