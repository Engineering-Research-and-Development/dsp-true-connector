package it.eng.negotiation.service.policy.validators;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.negotiation.exception.PolicyEnforcementException;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.service.policy.PolicyManager;

@ExtendWith(MockitoExtension.class)
class CountPolicyValidatorTest {
	
	private static final String AGREEMENT_ID = "agreement_id";
	
	@Mock
	private PolicyManager policyManager;

	@InjectMocks
	private CountPolicyValidator countPolicyValidator;
	
	@Test
	@DisplayName("Count enforcement - not reached")
	void countOK() {
		when(policyManager.getAccessCount(AGREEMENT_ID)).thenReturn(3);
		assertTrue(countPolicyValidator.validateCount(AGREEMENT_ID, COUNT_5));
	}
	
	@Test
	@DisplayName("Count enforcement - equal, not reached")
	void countOK_equal() {
		when(policyManager.getAccessCount(AGREEMENT_ID)).thenReturn(5);
		assertTrue(countPolicyValidator.validateCount(AGREEMENT_ID, COUNT_5));
	}
	
	@Test
	@DisplayName("Count enforcement - exceeded")
	void conutExceeded() {
		when(policyManager.getAccessCount(AGREEMENT_ID)).thenReturn(6);
		assertFalse(countPolicyValidator.validateCount(AGREEMENT_ID, COUNT_5));
	}
	
	@Test
	@DisplayName("Count enforcement - policyEnforcement not found")
	void conutPE_not_found() {
		when(policyManager.getAccessCount(AGREEMENT_ID)).thenThrow(new PolicyEnforcementException("Not found"));
		assertFalse(countPolicyValidator.validateCount(AGREEMENT_ID, COUNT_5));
	}

	private Constraint COUNT_5 = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.COUNT).operator(Operator.LTEQ).rightOperand("5").build();

}
