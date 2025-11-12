package it.eng.negotiation.policy.evaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.policy.model.Policy;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.model.PolicyRequest;

public class SpatialPolicyEvaluatorTest {

	 private SpatialPolicyEvaluator evaluator;

	    @BeforeEach
	    void setUp() {
	        evaluator = new SpatialPolicyEvaluator();
	    }

	    @Test
	    void testGetPolicyType() {
	        assertEquals(LeftOperand.SPATIAL, evaluator.getPolicyType());
	    }

	    @Test
	    void testEvaluate_PolicyNotValid() {
	        // Create a policy that is not valid
	        Policy policy = Policy.Builder.newInstance()
	                .id("policy-123")
	                .type(LeftOperand.SPATIAL)
	                .description("Spatial policy")
	                .enabled(true)
	                .validFrom(LocalDateTime.now().plusDays(1)) // Not valid yet
	                .build();
	        
	        // Create a policy request
	        PolicyRequest request = PolicyRequest.Builder.newInstance()
	                .agreementId("agreement-123")
	                .resourceId("resource-123")
	                .userId("user-123")
	                .action(Action.USE)
	                .build();
	        
	        // Evaluate the policy
	        PolicyDecision decision = evaluator.evaluate(policy, request);
	        
	        // Verify the result
	        assertNotNull(decision);
	        assertFalse(decision.isAllowed());
	        assertEquals("Policy is not valid at the current time", decision.getMessage());
	        assertEquals("policy-123", decision.getPolicyId());
	        assertEquals(LeftOperand.SPATIAL, decision.getPolicyType());
	    }

	    @Test
	    void testEvaluate_MissingLocation() {
	        // Create a valid policy
	        Policy policy = Policy.Builder.newInstance()
	                .id("policy-123")
	                .type(LeftOperand.SPATIAL)
	                .description("Spatial policy")
	                .enabled(true)
	                .build();
	        
	        // Create a policy request with no location
	        PolicyRequest request = PolicyRequest.Builder.newInstance()
	                .agreementId("agreement-123")
	                .resourceId("resource-123")
	                .userId("user-123")
	                .action(Action.USE)
	                .build();
	        
	        // Evaluate the policy
	        PolicyDecision decision = evaluator.evaluate(policy, request);
	        
	        // Verify the result
	        assertNotNull(decision);
	        assertFalse(decision.isAllowed());
	        assertEquals("Location is missing", decision.getMessage());
	        assertEquals("policy-123", decision.getPolicyId());
	        assertEquals(LeftOperand.SPATIAL, decision.getPolicyType());
	    }

	    @Test
	    void testEvaluate_LocationNotInAllowedList() {
	        // Create a valid policy with an allowed locations list
	        Policy policy = Policy.Builder.newInstance()
	                .id("policy-123")
	                .type(LeftOperand.SPATIAL)
	                .description("Spatial policy")
	                .enabled(true)
	                .attribute("allowedLocations", Arrays.asList("EU", "US"))
	                .attribute("operator", Operator.IS_ANY_OF)
	                .build();
	        
	        // Create a policy request with a location not in the allowed list
	        PolicyRequest request = PolicyRequest.Builder.newInstance()
	                .agreementId("agreement-123")
	                .resourceId("resource-123")
	                .userId("user-123")
	                .action(Action.USE)
	                .attribute("location", "Asia")
	                .build();
	        
	        // Evaluate the policy
	        PolicyDecision decision = evaluator.evaluate(policy, request);
	        
	        // Verify the result
	        assertNotNull(decision);
	        assertFalse(decision.isAllowed());
	        assertEquals("Location is not in the allowed locations list", decision.getMessage());
	        assertEquals("policy-123", decision.getPolicyId());
	        assertEquals(LeftOperand.SPATIAL, decision.getPolicyType());
	    }

	    @Test
	    void testEvaluate_LocationInDeniedList() {
	        // Create a valid policy with a denied locations list
	        Policy policy = Policy.Builder.newInstance()
	                .id("policy-123")
	                .type(LeftOperand.SPATIAL)
	                .description("Spatial policy")
	                .enabled(true)
	                .attribute("deniedLocations", Arrays.asList("Asia", "Africa"))
	                .attribute("operator", Operator.IS_ANY_OF)
	                .build();
	        
	        // Create a policy request with a location in the denied list
	        PolicyRequest request = PolicyRequest.Builder.newInstance()
	                .agreementId("agreement-123")
	                .resourceId("resource-123")
	                .userId("user-123")
	                .action(Action.USE)
	                .attribute("location", "Asia")
	                .build();
	        
	        // Evaluate the policy
	        PolicyDecision decision = evaluator.evaluate(policy, request);
	        
	        // Verify the result
	        assertNotNull(decision);
	        assertFalse(decision.isAllowed());
	        assertEquals("Location is in the denied locations list", decision.getMessage());
	        assertEquals("policy-123", decision.getPolicyId());
	        assertEquals(LeftOperand.SPATIAL, decision.getPolicyType());
	    }

	    @Test
	    void testEvaluate_LocationInAllowedList() {
	        // Create a valid policy with an allowed locations list
	        Policy policy = Policy.Builder.newInstance()
	                .id("policy-123")
	                .type(LeftOperand.SPATIAL)
	                .description("Spatial policy")
	                .enabled(true)
	                .attribute("allowedLocations", Arrays.asList("EU", "US"))
	                .attribute("operator", Operator.IS_ANY_OF)
	                .build();
	        
	        // Create a policy request with a location in the allowed list
	        PolicyRequest request = PolicyRequest.Builder.newInstance()
	                .agreementId("agreement-123")
	                .resourceId("resource-123")
	                .userId("user-123")
	                .action(Action.USE)
	                .attribute("location", "EU")
	                .build();
	        
	        // Evaluate the policy
	        PolicyDecision decision = evaluator.evaluate(policy, request);
	        
	        // Verify the result
	        assertNotNull(decision);
	        assertTrue(decision.isAllowed());
	        assertEquals("Spatial policy passed", decision.getMessage());
	        assertEquals("policy-123", decision.getPolicyId());
	        assertEquals(LeftOperand.SPATIAL, decision.getPolicyType());
	    }
	    
	    @Test
	    void testEvaluate_LocationEquals() {
	        // Create a valid policy with an allowed locations list
	        Policy policy = Policy.Builder.newInstance()
	                .id("policy-123")
	                .type(LeftOperand.SPATIAL)
	                .description("Spatial policy")
	                .enabled(true)
	                .attribute("allowedLocations", Arrays.asList("EU"))
	                .attribute("operator", Operator.EQ)
	                .build();
	        
	        // Create a policy request with a location in the allowed list
	        PolicyRequest request = PolicyRequest.Builder.newInstance()
	                .agreementId("agreement-123")
	                .resourceId("resource-123")
	                .userId("user-123")
	                .action(Action.USE)
	                .attribute("location", "EU")
	                .build();
	        
	        // Evaluate the policy
	        PolicyDecision decision = evaluator.evaluate(policy, request);
	        
	        // Verify the result
	        assertNotNull(decision);
	        assertTrue(decision.isAllowed());
	        assertEquals("Spatial policy passed", decision.getMessage());
	        assertEquals("policy-123", decision.getPolicyId());
	        assertEquals(LeftOperand.SPATIAL, decision.getPolicyType());
	    }

	    @Test
	    void testEvaluate_EmptyAllowedList() {
	        // Create a valid policy with an empty allowed locations list
	        Policy policy = Policy.Builder.newInstance()
	                .id("policy-123")
	                .type(LeftOperand.SPATIAL)
	                .description("Spatial policy")
	                .enabled(true)
	                .attribute("allowedLocations", Collections.emptyList())
	                .attribute("operator", Operator.IS_ANY_OF)
	                .build();
	        
	        // Create a policy request
	        PolicyRequest request = PolicyRequest.Builder.newInstance()
	                .agreementId("agreement-123")
	                .resourceId("resource-123")
	                .userId("user-123")
	                .action(Action.USE)
	                .attribute("location", "EU")
	                .build();
	        
	        // Evaluate the policy
	        PolicyDecision decision = evaluator.evaluate(policy, request);
	        
	        // Verify the result
	        assertNotNull(decision);
	        assertTrue(decision.isAllowed());
	        assertEquals("Spatial policy passed", decision.getMessage());
	        assertEquals("policy-123", decision.getPolicyId());
	        assertEquals(LeftOperand.SPATIAL, decision.getPolicyType());
	    }

	    @Test
	    void testEvaluate_NoLists() {
	        // Create a valid policy with no allowed or denied locations lists
	        Policy policy = Policy.Builder.newInstance()
	                .id("policy-123")
	                .type(LeftOperand.SPATIAL)
	                .description("Spatial policy")
	                .enabled(true)
	                .attribute("operator", Operator.IS_ANY_OF)
	                .build();
	        
	        // Create a policy request
	        PolicyRequest request = PolicyRequest.Builder.newInstance()
	                .agreementId("agreement-123")
	                .resourceId("resource-123")
	                .userId("user-123")
	                .action(Action.USE)
	                .attribute("location", "EU")
	                .build();
	        
	        // Evaluate the policy
	        PolicyDecision decision = evaluator.evaluate(policy, request);
	        
	        // Verify the result
	        assertNotNull(decision);
	        assertTrue(decision.isAllowed());
	        assertEquals("Spatial policy passed", decision.getMessage());
	        assertEquals("policy-123", decision.getPolicyId());
	        assertEquals(LeftOperand.SPATIAL, decision.getPolicyType());
	    }
}
