/**
 * 
 */
package it.eng.negotiation.policy.evaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.policy.model.Policy;
import it.eng.negotiation.policy.model.PolicyConstants;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.model.PolicyRequest;
import it.eng.negotiation.policy.model.PolicyType;

class TemporalPolicyEvaluatorTest {

	private TemporalPolicyEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new TemporalPolicyEvaluator();
    }

    @Test
    void testGetPolicyType() {
        assertEquals(PolicyType.TEMPORAL, evaluator.getPolicyType());
    }

    @Test
    void testEvaluate_PolicyNotValid() {
        // Create a policy that is not valid
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.TEMPORAL)
                .description("Temporal policy")
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
        assertEquals(PolicyType.TEMPORAL, decision.getPolicyType());
    }

    @Test
    void testEvaluate_DateTimeAfterAccessTime_allow() {
        // Create a valid policy with a start time
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dateTime = now.minusHours(1);
        
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.TEMPORAL)
                .description("Temporal policy")
                .enabled(true)
                .attribute(PolicyConstants.DATE_TIME, dateTime)
                .attribute(PolicyConstants.OPERATOR, Operator.GTEQ)
                .build();
        
        // Create a policy request with an access time before the start time
        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId("agreement-123")
                .resourceId("resource-123")
                .userId("user-123")
                .action(Action.READ)
                .attribute(PolicyConstants.ACCESS_TIME, now)
                .build();
        
        // Evaluate the policy
        PolicyDecision decision = evaluator.evaluate(policy, request);
        
        // Verify the result
        assertNotNull(decision);
        assertTrue(decision.isAllowed());
        assertEquals("policy-123", decision.getPolicyId());
        assertEquals(PolicyType.TEMPORAL, decision.getPolicyType());
    }
    
    @Test
    void testEvaluate_NoAccessTimeProvided_allow() {
        // Create a valid policy with a dateTime and GTEQ operator
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime constraintDateTime = now.minusHours(1);
        
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.TEMPORAL)
                .description("Temporal policy")
                .enabled(true)
                .attribute(PolicyConstants.DATE_TIME, constraintDateTime)
                .attribute(PolicyConstants.OPERATOR, Operator.GTEQ)
                .build();
        
        // Create a policy request with no access time
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
        assertTrue(decision.isAllowed());
        assertEquals("Temporal policy passed", decision.getMessage());
        assertEquals("policy-123", decision.getPolicyId());
        assertEquals(PolicyType.TEMPORAL, decision.getPolicyType());
    }
    
    @Test
    void testEvaluate_DateTimeBeforeAccessTime_deny() {
        // Create a valid policy with a start time
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime constraintDateTime = now.plusHours(1);
        
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.TEMPORAL)
                .description("Temporal policy")
                .enabled(true)
                .attribute(PolicyConstants.DATE_TIME, constraintDateTime)
                .attribute(PolicyConstants.OPERATOR, Operator.GTEQ)
                .build();
        
        // Create a policy request with an access time before the start time
        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId("agreement-123")
                .resourceId("resource-123")
                .userId("user-123")
                .action(Action.READ)
                .attribute(PolicyConstants.ACCESS_TIME, now)
                .build();
        
        // Evaluate the policy
        PolicyDecision decision = evaluator.evaluate(policy, request);
        
        // Verify the result
        assertNotNull(decision);
        assertFalse(decision.isAllowed());
        assertEquals("Access time is before the allowed time", decision.getMessage());
        assertEquals("policy-123", decision.getPolicyId());
        assertEquals(PolicyType.TEMPORAL, decision.getPolicyType());
    }
    
    @Test
    void testEvaluate_DateTimeBeforeAccessTime() {
        // Create a valid policy with an end time
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dateTime = now.minusHours(1);
        
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.TEMPORAL)
                .description("Temporal policy")
                .enabled(true)
                .attribute(PolicyConstants.DATE_TIME, dateTime)
                .attribute(PolicyConstants.OPERATOR, Operator.LTEQ)
                .build();
        
        // Create a policy request with an access time after the end time
        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId("agreement-123")
                .resourceId("resource-123")
                .userId("user-123")
                .action(Action.READ)
                .attribute(PolicyConstants.ACCESS_TIME, now)
                .build();
        
        // Evaluate the policy
        PolicyDecision decision = evaluator.evaluate(policy, request);
        
        // Verify the result
        assertNotNull(decision);
        assertTrue(decision.isAllowed());
        assertEquals("policy-123", decision.getPolicyId());
        assertEquals(PolicyType.TEMPORAL, decision.getPolicyType());
    }
    
    @Test
    void testEvaluate_DateTimeAfterAccessTime_deny() {
        // Create a valid policy with an end time
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dateTime = now.plusHours(1);
        
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.TEMPORAL)
                .description("Temporal policy")
                .enabled(true)
                .attribute(PolicyConstants.DATE_TIME, dateTime)
                .attribute(PolicyConstants.OPERATOR, Operator.LTEQ)
                .build();
        
        // Create a policy request with an access time after the end time
        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId("agreement-123")
                .resourceId("resource-123")
                .userId("user-123")
                .action(Action.READ)
                .attribute(PolicyConstants.ACCESS_TIME, now)
                .build();
        
        // Evaluate the policy
        PolicyDecision decision = evaluator.evaluate(policy, request);
        
        // Verify the result
        assertNotNull(decision);
        assertFalse(decision.isAllowed());
        assertEquals("Access time is after the allowed time", decision.getMessage());
        assertEquals("policy-123", decision.getPolicyId());
        assertEquals(PolicyType.TEMPORAL, decision.getPolicyType());
    }
    
    @Test
    void testEvaluate_NoTimeConstraints() {
        // Create a valid policy with no time constraints
        Policy policy = Policy.Builder.newInstance()
                .id("policy-123")
                .type(PolicyType.TEMPORAL)
                .description("Temporal policy")
                .enabled(true)
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
        assertEquals("Operator not set", decision.getMessage());
        assertEquals("policy-123", decision.getPolicyId());
        assertEquals(PolicyType.TEMPORAL, decision.getPolicyType());
    }
}
