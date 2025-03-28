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
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.policy.model.Policy;
import it.eng.negotiation.policy.model.PolicyConstants;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.model.PolicyRequest;
import it.eng.negotiation.policy.model.PolicyType;

public class PurposePolicyEvaluatorTest {

	private PurposePolicyEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new PurposePolicyEvaluator();
    }

    @Test
    void testGetPolicyType() {
        assertEquals(PolicyType.PURPOSE, evaluator.getPolicyType());
    }

    @Test
    void testEvaluate_PolicyNotValid() {
        // Create a policy that is not valid
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.PURPOSE)
                .description("Purpose policy")
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
        assertEquals(PolicyType.PURPOSE, decision.getPolicyType());
    }

    @Test
    void testEvaluate_MissingPurpose() {
        // Create a valid policy
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.PURPOSE)
                .description("Purpose policy")
                .enabled(true)
                .build();
        
        // Create a policy request with no purpose
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
        assertEquals("Purpose is missing", decision.getMessage());
        assertEquals("policy-123", decision.getPolicyId());
        assertEquals(PolicyType.PURPOSE, decision.getPolicyType());
    }

    @Test
    void testEvaluate_PurposeNotInAllowedList() {
        // Create a valid policy with an allowed purposes list
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.PURPOSE)
                .description("Purpose policy")
                .enabled(true)
                .attribute(PolicyConstants.ALLOWED_PURPOSES, Arrays.asList("research", "education"))
                .attribute(PolicyConstants.OPERATOR, Operator.IS_ANY_OF)
                .build();
        
        // Create a policy request with a purpose not in the allowed list
        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId("agreement-123")
                .resourceId("resource-123")
                .userId("user-123")
                .action(Action.READ)
                .attribute(PolicyConstants.PURPOSE, "commercial")
                .build();
        
        // Evaluate the policy
        PolicyDecision decision = evaluator.evaluate(policy, request);
        
        // Verify the result
        assertNotNull(decision);
        assertFalse(decision.isAllowed());
        assertEquals("Purpose is not in the allowed purposes list", decision.getMessage());
        assertEquals("policy-123", decision.getPolicyId());
        assertEquals(PolicyType.PURPOSE, decision.getPolicyType());
    }
    
    @Test
    void testEvaluate_PurposeNotEqual() {
        // Create a valid policy with an allowed purposes list
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.PURPOSE)
                .description("Purpose policy")
                .enabled(true)
                .attribute(PolicyConstants.ALLOWED_PURPOSES, Arrays.asList("research"))
                .attribute(PolicyConstants.OPERATOR, Operator.EQ)
                .build();
        
        // Create a policy request with a purpose not in the allowed list
        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId("agreement-123")
                .resourceId("resource-123")
                .userId("user-123")
                .action(Action.READ)
                .attribute(PolicyConstants.PURPOSE, "research")
                .build();
        
        // Evaluate the policy
        PolicyDecision decision = evaluator.evaluate(policy, request);
        
        // Verify the result
        assertNotNull(decision);
        assertTrue(decision.isAllowed());
        assertEquals("Purpose policy passed", decision.getMessage());
        assertEquals("policy-123", decision.getPolicyId());
        assertEquals(PolicyType.PURPOSE, decision.getPolicyType());
    }

    @Test
    void testEvaluate_PurposeInDeniedList() {
        // Create a valid policy with a denied purposes list
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.PURPOSE)
                .description("Purpose policy")
                .enabled(true)
                .attribute(PolicyConstants.DENIED_PURPOSES, Arrays.asList("commercial", "marketing"))
                .attribute(PolicyConstants.OPERATOR, Operator.EQ)
                .build();
        
        // Create a policy request with a purpose in the denied list
        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId("agreement-123")
                .resourceId("resource-123")
                .userId("user-123")
                .action(Action.READ)
                .attribute(PolicyConstants.PURPOSE, "commercial")
                .build();
        
        // Evaluate the policy
        PolicyDecision decision = evaluator.evaluate(policy, request);
        
        // Verify the result
        assertNotNull(decision);
        assertFalse(decision.isAllowed());
        assertEquals("Purpose is in the denied purposes list", decision.getMessage());
        assertEquals("policy-123", decision.getPolicyId());
        assertEquals(PolicyType.PURPOSE, decision.getPolicyType());
    }

    @Test
    void testEvaluate_PurposeInAllowedList() {
        // Create a valid policy with an allowed purposes list
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.PURPOSE)
                .description("Purpose policy")
                .enabled(true)
                .attribute(PolicyConstants.ALLOWED_PURPOSES, Arrays.asList("research", "education"))
                .attribute(PolicyConstants.OPERATOR, Operator.IS_ANY_OF)
                .build();
        
        // Create a policy request with a purpose in the allowed list
        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId("agreement-123")
                .resourceId("resource-123")
                .userId("user-123")
                .action(Action.READ)
                .attribute(PolicyConstants.PURPOSE, "research")
                .build();
        
        // Evaluate the policy
        PolicyDecision decision = evaluator.evaluate(policy, request);
        
        // Verify the result
        assertNotNull(decision);
        assertTrue(decision.isAllowed());
        assertEquals("Purpose policy passed", decision.getMessage());
        assertEquals("policy-123", decision.getPolicyId());
        assertEquals(PolicyType.PURPOSE, decision.getPolicyType());
    }

    @Test
    void testEvaluate_EmptyAllowedList() {
        // Create a valid policy with an empty allowed purposes list
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.PURPOSE)
                .description("Purpose policy")
                .enabled(true)
                .attribute(PolicyConstants.ALLOWED_PURPOSES, Collections.emptyList())
                .attribute(PolicyConstants.OPERATOR, Operator.EQ)
                .build();
        
        // Create a policy request
        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId("agreement-123")
                .resourceId("resource-123")
                .userId("user-123")
                .action(Action.READ)
                .attribute(PolicyConstants.PURPOSE, "research")
                .build();
        
        // Evaluate the policy
        PolicyDecision decision = evaluator.evaluate(policy, request);
        
        // Verify the result
        assertNotNull(decision);
        assertTrue(decision.isAllowed());
        assertEquals("Purpose policy passed", decision.getMessage());
        assertEquals("policy-123", decision.getPolicyId());
        assertEquals(PolicyType.PURPOSE, decision.getPolicyType());
    }

    @Test
    void testEvaluate_NoLists() {
        // Create a valid policy with no allowed or denied purposes lists
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.PURPOSE)
                .description("Purpose policy")
                .enabled(true)
                .attribute(PolicyConstants.OPERATOR, Operator.EQ)
                .build();
        
        // Create a policy request
        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId("agreement-123")
                .resourceId("resource-123")
                .userId("user-123")
                .action(Action.READ)
                .attribute(PolicyConstants.PURPOSE, "research")
                .build();
        
        // Evaluate the policy
        PolicyDecision decision = evaluator.evaluate(policy, request);
        
        // Verify the result
        assertNotNull(decision);
        assertTrue(decision.isAllowed());
        assertEquals("Purpose policy passed", decision.getMessage());
        assertEquals("policy-123", decision.getPolicyId());
        assertEquals(PolicyType.PURPOSE, decision.getPolicyType());
    }
}
