package it.eng.negotiation.service.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.model.Permission;
import it.eng.negotiation.service.policy.validators.CountPolicyValidator;
import it.eng.negotiation.service.policy.validators.DateTimePolicyValidator;

@ExtendWith(MockitoExtension.class)
class PolicyEnforcementServiceTest {

	private static final String AGREEMENT_ID = "agreement_id";
	private static final String ASSIGNEE = "assignee";
	private static final String ASSIGNER = "assigner";
	private static final String TARGET = "target";
	
	@Mock
	private CountPolicyValidator countPolicyValidator;
	@Mock
	private DateTimePolicyValidator dateTimePolicyValidator;
	
	@InjectMocks
	private PolicyEnforcementService service;

	// COUNT
	@Test
	@DisplayName("Count enforcement - not reached")
	void countOK() {
		when(countPolicyValidator.validateCount(AGREEMENT_ID, COUNT_5)).thenReturn(true);
		assertTrue(service.isAgreementValid(agreement(Arrays.asList(COUNT_5))));
	}
	
	@Test
	@DisplayName("Count enforcement - exceeded")
	void conutExceeded() {
		when(countPolicyValidator.validateCount(AGREEMENT_ID, COUNT_5)).thenReturn(false);
		assertFalse(service.isAgreementValid(agreement(Arrays.asList(COUNT_5))));
	}
	
	// DATE_TIME
	@Test
	@DisplayName("DateTime enforcement - not reached")
	void dateTimeOK() {
		when(dateTimePolicyValidator.validateDateTime(DATE_TIME)).thenReturn(true);
		assertTrue(service.isAgreementValid(agreement(Arrays.asList(DATE_TIME))));
	}
	
	@Test
	@DisplayName("DateTime enforcement - expired")
	void dateTimeExired() {
		when(dateTimePolicyValidator.validateDateTime(DATE_TIME)).thenReturn(false);
		assertFalse(service.isAgreementValid(agreement(Arrays.asList(DATE_TIME))));
	}
	
	// Multiple constraints	
	@Test
	@DisplayName("Multiple constraints - count and date")
	public void multipleConstraints_ok() {
		when(countPolicyValidator.validateCount(AGREEMENT_ID, COUNT_5)).thenReturn(true);
		when(dateTimePolicyValidator.validateDateTime(DATE_TIME)).thenReturn(true);
		assertTrue(service.isAgreementValid(agreement(Arrays.asList(COUNT_5, DATE_TIME))));
	}
	
	@Test
	@DisplayName("Multiple constraints - count and date - one is expired")
	public void multipleConstraints_invalid() {
		when(countPolicyValidator.validateCount(AGREEMENT_ID, COUNT_5)).thenReturn(false);
		when(dateTimePolicyValidator.validateDateTime(DATE_TIME)).thenReturn(true);
		assertFalse(service.isAgreementValid(agreement(Arrays.asList(COUNT_5, DATE_TIME))));
		
		when(countPolicyValidator.validateCount(AGREEMENT_ID, COUNT_5)).thenReturn(true);
		when(dateTimePolicyValidator.validateDateTime(DATE_TIME)).thenReturn(false);
		assertFalse(service.isAgreementValid(agreement(Arrays.asList(COUNT_5, DATE_TIME))));
	}
	
	// No permission
	@Test
	@DisplayName("No permissions - allow")
	public void emptyPermission() {
		Agreement emptyPermissionAgreement = Agreement.Builder.newInstance()
			.id(AGREEMENT_ID)
			.assignee(ASSIGNEE)
			.assigner(ASSIGNER)
			.target(TARGET)
			.permission(new ArrayList<Permission>())
			.build();
		assertTrue(service.isAgreementValid(emptyPermissionAgreement));
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
}
