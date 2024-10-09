package it.eng.negotiation.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.negotiation.exception.PolicyEnforcementException;
import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.model.Permission;

@ExtendWith(MockitoExtension.class)
class PolicyEnforcementServiceTest {

	private static final String AGREEMENT_ID = "agreement_id";
	private static final String ASSIGNEE = "assignee";
	private static final String ASSIGNER = "assigner";
	private static final String TARGET = "target";
	
	@Mock
	private PolicyManager policyManager;
	
	@InjectMocks
	private PolicyEnforcementService service;

	// COUNT
	@Test
	@DisplayName("Count enforcement - not reached")
	void countOK() {
		when(policyManager.getAccessCount(AGREEMENT_ID)).thenReturn(3);
		assertTrue(service.isAgreementValid(agreement(Arrays.asList(COUNT_5))));
	}
	
	@Test
	@DisplayName("Count enforcement - not reached")
	void countOK_equal() {
		when(policyManager.getAccessCount(AGREEMENT_ID)).thenReturn(5);
		assertTrue(service.isAgreementValid(agreement(Arrays.asList(COUNT_5))));
	}
	
	@Test
	@DisplayName("Count enforcement - exceeded")
	void conutExceeded() {
		when(policyManager.getAccessCount(AGREEMENT_ID)).thenReturn(6);
		assertFalse(service.isAgreementValid(agreement(Arrays.asList(COUNT_5))));
	}
	
	@Test
	@DisplayName("Count enforcement - policyEnforcement not found")
	void conutPE_not_found() {
		when(policyManager.getAccessCount(AGREEMENT_ID)).thenThrow(new PolicyEnforcementException("Not found"));
		assertFalse(service.isAgreementValid(agreement(Arrays.asList(COUNT_5))));
	}

	// DATE_TIME
	@Test
	@DisplayName("DateTime enforcement - not reached")
	void dateTimeOK() {
		assertTrue(service.isAgreementValid(agreement(Arrays.asList(DATE_TIME))));
	}
	
	@Test
	@DisplayName("DateTime enforcement - expired")
	void dateTimeExired() {
		assertFalse(service.isAgreementValid(agreement(Arrays.asList(DATE_TIME_EXPIRED))));
	}
	
	@Test
	@DisplayName("DateTime enforcement - invalid date")
	void dateTime_invalid_date() {
		assertFalse(service.isAgreementValid(agreement(Arrays.asList(Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.DATE_TIME)
				.operator(Operator.LT)
				.rightOperand("INVALID_DATE")
				.build()))));
	}
	
	@Test
	@DisplayName("Multiple constraints - count and date")
	public void multipleConstraints_ok() {
		when(policyManager.getAccessCount(AGREEMENT_ID)).thenReturn(3);
		assertTrue(service.isAgreementValid(agreement(Arrays.asList(COUNT_5, DATE_TIME))));
	}
	
	@Test
	@DisplayName("Multiple constraints - count and date - one is expired")
	public void multipleConstraints_invalid() {
		assertFalse(service.isAgreementValid(agreement(Arrays.asList(COUNT_5, DATE_TIME_EXPIRED))));
		when(policyManager.getAccessCount(AGREEMENT_ID)).thenReturn(10);
		assertFalse(service.isAgreementValid(agreement(Arrays.asList(COUNT_5, DATE_TIME_EXPIRED))));
	}
	
	private Agreement agreement(List<Constraint> constraints) {
		return Agreement.Builder.newInstance()
				.id(AGREEMENT_ID)
				.assignee(ASSIGNEE)
				.assigner(ASSIGNER)
				.target(TARGET)
				.permission(Arrays.asList(Permission.Builder
						.newInstance().action(Action.USE)
						.constraint(constraints)
						.build()))
				.build();
	}
	
	private Constraint COUNT_5 = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.COUNT).operator(Operator.LTEQ).rightOperand("5").build();
	
	private Constraint DATE_TIME = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.DATE_TIME)
			.operator(Operator.LT)
			.rightOperand(Instant.now().plus(5, ChronoUnit.DAYS).toString())
			.build();
	
	private Constraint DATE_TIME_EXPIRED = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.DATE_TIME)
			.operator(Operator.LT)
			.rightOperand(Instant.now().minus(5, ChronoUnit.DAYS).toString())
			.build();
}
