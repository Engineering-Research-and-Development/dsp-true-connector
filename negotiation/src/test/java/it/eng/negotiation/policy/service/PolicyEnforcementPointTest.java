package it.eng.negotiation.policy.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.tools.usagecontrol.UsageControlProperties;

@ExtendWith(MockitoExtension.class)
class PolicyEnforcementPointTest {

	@Mock
	private UsageControlProperties usageControlProperties;
	@Mock
	private PolicyInformationPoint policyInformationPoint;
	@Mock
	private PolicyDecisionPoint policyDecisionPoint;
	
	@InjectMocks
	private PolicyEnforcementPoint policyEnforcementPoint;
	
	@Test
	public void enforcePolicy_usageControlDisabled() {
		Agreement agreement = NegotiationMockObjectUtil.AGREEMENT;
		
		// Mock the behavior of usageControlProperties
		when(usageControlProperties.usageControlEnabled()).thenReturn(false);
		
		// Call the method to test
		policyEnforcementPoint.enforcePolicy(agreement, "use");
		
		// Verify that the policy decision is allowed when usage control is disabled
		verify(policyDecisionPoint, never()).evaluate(any(), any(Agreement.class));
		verify(policyInformationPoint, never()).getAllAttributes(any());
	}

	
	@Test
	void enforcePolicy_allowed() {
		Agreement agreement = NegotiationMockObjectUtil.AGREEMENT;
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		when(policyInformationPoint.getAllAttributes(any())).thenReturn(Map.of("key", "value"));
		when(policyDecisionPoint.evaluate(any(), any(Agreement.class)))
				.thenReturn(PolicyDecision.Builder.newInstance()
						.message("Test case - allowed")
						.policyId("test-policy-id")
						.policyType(LeftOperand.COUNT)
						.allowed(true)
						.build());
		
		PolicyDecision policyDecision = policyEnforcementPoint.enforcePolicy(agreement, "use");
		
		assertNotNull(policyDecision);
		assertTrue(policyDecision.isAllowed());
		
		verify(policyDecisionPoint).evaluate(any(), any(Agreement.class));
		verify(policyInformationPoint).getAllAttributes(any());
	}
	
	@Test
	public void enforcePolicy_denied() {
		Agreement agreement = NegotiationMockObjectUtil.AGREEMENT;
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		when(policyInformationPoint.getAllAttributes(any())).thenReturn(Map.of("key", "value"));
		when(policyDecisionPoint.evaluate(any(), any(Agreement.class)))
				.thenReturn(PolicyDecision.Builder.newInstance()
						.message("Test case - denied")
						.policyId("test-policy-id")
						.policyType(LeftOperand.COUNT)
						.allowed(false)
						.build());
		
		PolicyDecision policyDecision = policyEnforcementPoint.enforcePolicy(agreement, "use");

		assertNotNull(policyDecision);
		assertFalse(policyDecision.isAllowed());
		
		verify(policyDecisionPoint).evaluate(any(), any(Agreement.class));
		verify(policyInformationPoint).getAllAttributes(any());
	}
	
}
