package it.eng.negotiation.policy.evaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.policy.model.Policy;
import it.eng.negotiation.policy.model.PolicyConstants;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.model.PolicyRequest;

class AccessCountPolicyEvaluatorTest {

	private AccessCountPolicyEvaluator evaluator;

	@BeforeEach
	void setUp() {
		evaluator = new AccessCountPolicyEvaluator();
	}

	@Test
	void testGetPolicyType() {
		assertEquals(LeftOperand.COUNT, evaluator.getPolicyType());
	}

	@Test
	void testEvaluate_PolicyNotValid() {
		  // Create a policy that is not valid
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(LeftOperand.COUNT)
                .description("Access count policy")
                .enabled(true)
                .validFrom(LocalDateTime.now().plusDays(1)) // Not valid yet
                .build();
        
        // Create a policy request
        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId("agreement-123")
                .resourceId("resource-123")
                .userId("user-123")
                .action(Action.READ)
                .build();
        
        // Evaluate the policy
        PolicyDecision decision = evaluator.evaluate(policy, request);
        
        // Verify the result
        assertNotNull(decision);
        assertFalse(decision.isAllowed());
        assertEquals("Policy is not valid at the current time", decision.getMessage());
        assertEquals("policy-123", decision.getPolicyId());
        assertEquals(LeftOperand.COUNT, decision.getPolicyType());
	}
	
	@Test
	public void evaluteAccessCountAllowed_LT() {
		  Policy policy = Policy.Builder.newInstance()
	                .id("policy-123")
	                .type(LeftOperand.COUNT)
	                .description("Access count policy")
	                .enabled(true)
	                .validFrom(LocalDateTime.now().minusDays(1))
	                .attribute(PolicyConstants.COUNT, 3) // Max count
	                .attribute(PolicyConstants.OPERATOR, Operator.LT) // Operator
	                .build();
		  
		  // Create a policy request
	        PolicyRequest request = PolicyRequest.Builder.newInstance()
	                .agreementId("agreement-123")
	                .resourceId("resource-123")
	                .userId("user-123")
	                .attribute(PolicyConstants.CURRENT_COUNT, 2) // Current count
	                .action(Action.READ)
	                .build();
	        
	        // Evaluate the policy
	        PolicyDecision decision = evaluator.evaluate(policy, request);
	        
	        // Verify the result
	        assertNotNull(decision);
	        assertTrue(decision.isAllowed());
	        assertEquals("Access count policy passed", decision.getMessage());
	        assertEquals("policy-123", decision.getPolicyId());
	        assertEquals(LeftOperand.COUNT, decision.getPolicyType());
	}
	
	@Test
	public void evaluteAccessCountExceeded_LT() {
		  Policy policy = Policy.Builder.newInstance()
	                .id("policy-123")
	                .type(LeftOperand.COUNT)
	                .description("Access count policy")
	                .enabled(true)
	                .validFrom(LocalDateTime.now().minusDays(1))
	                .attribute("count", 3) // Max count
	                .attribute("operator", Operator.LT) // Operator
	                .build();
		  
		  // Create a policy request
	        PolicyRequest request = PolicyRequest.Builder.newInstance()
	                .agreementId("agreement-123")
	                .resourceId("resource-123")
	                .userId("user-123")
	                .attribute(PolicyConstants.CURRENT_COUNT, 3) // Current count
	                .action(Action.READ)
	                .build();
	        
	        // Evaluate the policy
	        PolicyDecision decision = evaluator.evaluate(policy, request);
	        
	        // Verify the result
	        assertNotNull(decision);
	        assertFalse(decision.isAllowed());
	        assertEquals("Access count exceeded", decision.getMessage());
	        assertEquals("policy-123", decision.getPolicyId());
	        assertEquals(LeftOperand.COUNT, decision.getPolicyType());
	}

	@Test
	public void evaluteAccessCountAllowed_LTEQ() {
		  Policy policy = Policy.Builder.newInstance()
	                .id("policy-123")
	                .type(LeftOperand.COUNT)
	                .description("Access count policy")
	                .enabled(true)
	                .validFrom(LocalDateTime.now().minusDays(1))
	                .attribute(PolicyConstants.COUNT, 3) // Max count
	                .attribute(PolicyConstants.OPERATOR, Operator.LTEQ) // Operator
	                .build();
		  
		  // Create a policy request
	        PolicyRequest request = PolicyRequest.Builder.newInstance()
	                .agreementId("agreement-123")
	                .resourceId("resource-123")
	                .userId("user-123")
	                .attribute(PolicyConstants.CURRENT_COUNT, 2) // Current count
	                .action(Action.READ)
	                .build();
	        
	        // Evaluate the policy
	        PolicyDecision decision = evaluator.evaluate(policy, request);
	        
	        // Verify the result
	        assertNotNull(decision);
	        assertTrue(decision.isAllowed());
	        assertEquals("Access count policy passed", decision.getMessage());
	        assertEquals("policy-123", decision.getPolicyId());
	        assertEquals(LeftOperand.COUNT, decision.getPolicyType());
	}
	
	@Test
	public void evaluteAccessCountExceeded_LTEQ() {
		  Policy policy = Policy.Builder.newInstance()
	                .id("policy-123")
	                .type(LeftOperand.COUNT)
	                .description("Access count policy")
	                .enabled(true)
	                .validFrom(LocalDateTime.now().minusDays(1))
	                .attribute(PolicyConstants.COUNT, 3) // Max count
	                .attribute(PolicyConstants.OPERATOR, Operator.LTEQ) // Operator
	                .build();
		  
		  // Create a policy request
	        PolicyRequest request = PolicyRequest.Builder.newInstance()
	                .agreementId("agreement-123")
	                .resourceId("resource-123")
	                .userId("user-123")
	                .attribute(PolicyConstants.CURRENT_COUNT, 4) // Current count
	                .action(Action.READ)
	                .build();
	        
	        // Evaluate the policy
	        PolicyDecision decision = evaluator.evaluate(policy, request);
	        
	        // Verify the result
	        assertNotNull(decision);
	        assertFalse(decision.isAllowed());
	        assertEquals("Access count exceeded", decision.getMessage());
	        assertEquals("policy-123", decision.getPolicyId());
	        assertEquals(LeftOperand.COUNT, decision.getPolicyType());
	}
}
