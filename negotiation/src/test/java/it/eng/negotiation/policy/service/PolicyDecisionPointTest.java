package it.eng.negotiation.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.model.Permission;
import it.eng.negotiation.policy.evaluator.AccessCountPolicyEvaluator;
import it.eng.negotiation.policy.evaluator.PolicyEvaluator;
import it.eng.negotiation.policy.evaluator.PurposePolicyEvaluator;
import it.eng.negotiation.policy.evaluator.SpatialPolicyEvaluator;
import it.eng.negotiation.policy.evaluator.TemporalPolicyEvaluator;
import it.eng.negotiation.policy.model.PolicyConstants;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.model.PolicyRequest;
import it.eng.negotiation.repository.AgreementRepository;

@ExtendWith(MockitoExtension.class)
class PolicyDecisionPointTest {

	@Mock
	private AgreementRepository agreementRepository;
	
	private PolicyDecisionPoint policyDecisionPoint;
	
	@BeforeEach
	public void setup() {
		// Not using mocks for the evaluators to test the real implementations
		List<PolicyEvaluator> evaluators = List.of(new AccessCountPolicyEvaluator(), 
				new PurposePolicyEvaluator(), 
				new SpatialPolicyEvaluator(), 
				new TemporalPolicyEvaluator());
		policyDecisionPoint = new PolicyDecisionPoint(agreementRepository, evaluators);
	}
	
	@Test
	public void evaluateNoAgreementId() {
		PolicyRequest request = PolicyRequest.Builder.newInstance()
				.resourceId("resourceId")
				.userId("userId")
				.action(Action.READ)
				.build();

		PolicyDecision policyDecision = policyDecisionPoint.evaluate(request);
		
		assertNotNull(policyDecision);
		assertFalse(policyDecision.isAllowed());
		assertEquals("Agreement ID is missing", policyDecision.getMessage());
	}
	
	@Test
	public void evaluateAgreementNotFound() {
		PolicyRequest request = PolicyRequest.Builder.newInstance()
				.agreementId("agreementId")
				.resourceId("resourceId")
				.userId("userId")
				.action(Action.READ).build();

		when(agreementRepository.findById(anyString())).thenReturn(Optional.empty());

		PolicyDecision policyDecision = policyDecisionPoint.evaluate(request);

		assertNotNull(policyDecision);
		assertFalse(policyDecision.isAllowed());
		assertEquals("Agreement not found", policyDecision.getMessage());
	}
	
	@Test
	void evaluateSuccess_count() {
		Agreement agreement = NegotiationMockObjectUtil.AGREEMENT;
		
		when(agreementRepository.findById(anyString())).thenReturn(Optional.of(agreement));
		
		PolicyRequest request = PolicyRequest.Builder.newInstance()
				.agreementId("agreementId")
				.resourceId("resourceId")
				.userId("userId")
				.action(Action.READ)
				.attribute(PolicyConstants.CURRENT_COUNT, 3)
				.build();
		
		PolicyDecision policyDecision = policyDecisionPoint.evaluate(request);
		
		assertNotNull(policyDecision);
		assertTrue(policyDecision.isAllowed());
	}
	
	@Test
	void evaluateSuccess_count_denied() {
		Agreement agreement = NegotiationMockObjectUtil.AGREEMENT;
		
		when(agreementRepository.findById(anyString())).thenReturn(Optional.of(agreement));
		
		PolicyRequest request = PolicyRequest.Builder.newInstance()
				.agreementId("agreementId")
				.resourceId("resourceId")
				.userId("userId")
				.action(Action.READ)
				.attribute(PolicyConstants.CURRENT_COUNT, 6)
				.build();
		
		PolicyDecision policyDecision = policyDecisionPoint.evaluate(request);
		
		assertNotNull(policyDecision);
		assertFalse(policyDecision.isAllowed());
		assertEquals("Access count exceeded", policyDecision.getMessage());
	}
	
	@Test
	public void evaluateSuccess_dateTime() {
		Agreement agreement = Agreement.Builder.newInstance()
				.id(NegotiationMockObjectUtil.generateUUID())
				.assignee(NegotiationMockObjectUtil.ASSIGNEE)
				.assigner(NegotiationMockObjectUtil.ASSIGNER)
				.target(NegotiationMockObjectUtil.TARGET)
				.timestamp(ZonedDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
				.permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION))
				.build();

		when(agreementRepository.findById(anyString())).thenReturn(Optional.of(agreement));

		PolicyRequest request = PolicyRequest.Builder.newInstance()
				.agreementId(agreement.getId())
				.resourceId(agreement.getTarget())
				.userId("userId")
				.action(Action.READ)
				// accessTime is now
				.build();

		PolicyDecision policyDecision = policyDecisionPoint.evaluate(request);

		assertNotNull(policyDecision);
		assertTrue(policyDecision.isAllowed());
	}
	
	@Test
	public void evaluateSuccess_dateTime_denied() {
		Constraint constraint = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.DATE_TIME)
				.operator(Operator.GT)
				.rightOperand(LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ISO_DATE_TIME))
				.build();
		
		Permission permission = Permission.Builder.newInstance()
				.action(Action.USE)
				.target(NegotiationMockObjectUtil.TARGET)
				.constraint(Arrays.asList(constraint))
				.build();
		
		Agreement agreement = Agreement.Builder.newInstance()
				.id(NegotiationMockObjectUtil.generateUUID())
				.assignee(NegotiationMockObjectUtil.ASSIGNEE)
				.assigner(NegotiationMockObjectUtil.ASSIGNER)
				.target(NegotiationMockObjectUtil.TARGET)
				.timestamp(ZonedDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
				.permission(Arrays.asList(permission))
				.build();

		when(agreementRepository.findById(anyString())).thenReturn(Optional.of(agreement));

		PolicyRequest request = PolicyRequest.Builder.newInstance()
				.agreementId(agreement.getId())
				.resourceId(agreement.getTarget())
				.userId("userId")
				.action(Action.READ)
				.build();

		PolicyDecision policyDecision = policyDecisionPoint.evaluate(request);

		assertNotNull(policyDecision);
		assertFalse(policyDecision.isAllowed());
	}
	
	@Test
	public void evaluatePurpose_success() {
		Constraint constraint = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.PURPOSE)
				.operator(Operator.EQ)
				.rightOperand("dsp_test")
				.build();

		Permission permission = Permission.Builder.newInstance()
				.action(Action.USE)
				.target(NegotiationMockObjectUtil.TARGET)
				.constraint(Arrays.asList(constraint))
				.build();

		Agreement agreement = Agreement.Builder.newInstance()
				.id(NegotiationMockObjectUtil.generateUUID())
				.assignee(NegotiationMockObjectUtil.ASSIGNEE)
				.assigner(NegotiationMockObjectUtil.ASSIGNER)
				.target(NegotiationMockObjectUtil.TARGET)
				.timestamp(ZonedDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
				.permission(Arrays.asList(permission)).build();

		when(agreementRepository.findById(anyString())).thenReturn(Optional.of(agreement));

		PolicyRequest request = PolicyRequest.Builder.newInstance()
				.agreementId(agreement.getId())
				.resourceId(agreement.getTarget())
				.userId("userId").action(Action.READ)
				.attribute(PolicyConstants.PURPOSE, "dsp_test")
				.build();

		PolicyDecision policyDecision = policyDecisionPoint.evaluate(request);

		assertNotNull(policyDecision);
		assertTrue(policyDecision.isAllowed());
	}
	
	@Test
	public void evaluatePurpose_deny() {
        Constraint constraint = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.PURPOSE)
                .operator(Operator.EQ)
                .rightOperand("denied_purpose")
                .build();

        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .target(NegotiationMockObjectUtil.TARGET)
                .constraint(Arrays.asList(constraint))
                .build();

        Agreement agreement = Agreement.Builder.newInstance()
                .id(NegotiationMockObjectUtil.generateUUID())
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .target(NegotiationMockObjectUtil.TARGET)
                .timestamp(ZonedDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .permission(Arrays.asList(permission)).build();

        when(agreementRepository.findById(anyString())).thenReturn(Optional.of(agreement));

        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId(agreement.getId())
                .resourceId(agreement.getTarget())
                .userId("userId").action(Action.READ)
                .attribute(PolicyConstants.PURPOSE, "dsp_test")
                .build();

        PolicyDecision policyDecision = policyDecisionPoint.evaluate(request);

        assertNotNull(policyDecision);
        assertFalse(policyDecision.isAllowed());
        assertEquals("Purpose is not allowed", policyDecision.getMessage());
	}
	
	
	@Test
	public void evaluateSpatial_success() {
        Constraint constraint = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.SPATIAL)
                .operator(Operator.EQ)
                .rightOperand("spatial_test")
                .build();

        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .target(NegotiationMockObjectUtil.TARGET)
                .constraint(Arrays.asList(constraint))
                .build();

        Agreement agreement = Agreement.Builder.newInstance()
                .id(NegotiationMockObjectUtil.generateUUID())
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .target(NegotiationMockObjectUtil.TARGET)
                .timestamp(ZonedDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .permission(Arrays.asList(permission)).build();

        when(agreementRepository.findById(anyString())).thenReturn(Optional.of(agreement));

        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId(agreement.getId())
                .resourceId(agreement.getTarget())
                .userId("userId").action(Action.READ)
                .attribute(PolicyConstants.LOCATION, "spatial_test")
                .build();

        PolicyDecision policyDecision = policyDecisionPoint.evaluate(request);

        assertNotNull(policyDecision);
        assertTrue(policyDecision.isAllowed());
	}
	
    @Test
    public void evaluateSpatial_deny() {
    	Constraint constraint = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.SPATIAL)
                .operator(Operator.EQ)
                .rightOperand("denied_location")
                .build();

        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .target(NegotiationMockObjectUtil.TARGET)
                .constraint(Arrays.asList(constraint))
                .build();

        Agreement agreement = Agreement.Builder.newInstance()
                .id(NegotiationMockObjectUtil.generateUUID())
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .target(NegotiationMockObjectUtil.TARGET)
                .timestamp(ZonedDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .permission(Arrays.asList(permission)).build();

        when(agreementRepository.findById(anyString())).thenReturn(Optional.of(agreement));

        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId(agreement.getId())
                .resourceId(agreement.getTarget())
                .userId("userId").action(Action.READ)
                .attribute(PolicyConstants.LOCATION, "spatial_test")
                .build();

        PolicyDecision policyDecision = policyDecisionPoint.evaluate(request);
        
        assertNotNull(policyDecision);
        assertFalse(policyDecision.isAllowed());
        assertEquals("Location is in the allowed", policyDecision.getMessage());
    }
}
