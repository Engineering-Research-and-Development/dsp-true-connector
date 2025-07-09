package it.eng.negotiation.policy.service;

import it.eng.negotiation.model.*;
import it.eng.negotiation.policy.evaluator.*;
import it.eng.negotiation.policy.model.PolicyConstants;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.model.PolicyRequest;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyDecisionPointTest {

    @Mock
    private ApplicationEventPublisher publisher;

    private PolicyDecisionPoint policyDecisionPoint;

    ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);

    @BeforeEach
    public void setup() {
        // Not using mocks for the evaluators to test the real implementations
        List<PolicyEvaluator> evaluators = List.of(new AccessCountPolicyEvaluator(),
                new PurposePolicyEvaluator(),
                new SpatialPolicyEvaluator(),
                new TemporalPolicyEvaluator());
        policyDecisionPoint = new PolicyDecisionPoint(evaluators, publisher);
    }

    @Test
    public void evaluateNoAgreementId() {
        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .resourceId("resourceId")
                .userId("userId")
                .action(Action.READ)
                .build();

        PolicyDecision policyDecision = policyDecisionPoint.evaluate(request, null);

        verify(publisher).publishEvent(eventCaptor.capture());

        assertNotNull(policyDecision);
        assertFalse(policyDecision.isAllowed());
        assertEquals("Agreement ID is missing", policyDecision.getMessage());
        AuditEvent event = eventCaptor.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED, event.getEventType());
    }

    @Test
    void evaluateSuccess_count() {
        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId("agreementId")
                .resourceId("resourceId")
                .userId("userId")
                .action(Action.READ)
                .attribute(PolicyConstants.CURRENT_COUNT, 3)
                .build();

        PolicyDecision policyDecision = policyDecisionPoint.evaluate(request, NegotiationMockObjectUtil.AGREEMENT);

        verify(publisher).publishEvent(eventCaptor.capture());

        assertNotNull(policyDecision);
        assertTrue(policyDecision.isAllowed());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_APPROVE, event.getEventType());
    }

    @Test
    void evaluateSuccess_count_denied() {
        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId("agreementId")
                .resourceId("resourceId")
                .userId("userId")
                .action(Action.READ)
                .attribute(PolicyConstants.CURRENT_COUNT, 6)
                .build();

        PolicyDecision policyDecision = policyDecisionPoint.evaluate(request, NegotiationMockObjectUtil.AGREEMENT);

        verify(publisher).publishEvent(eventCaptor.capture());

        assertNotNull(policyDecision);
        assertFalse(policyDecision.isAllowed());
        assertEquals("Access count exceeded", policyDecision.getMessage());
        AuditEvent event = eventCaptor.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED, event.getEventType());
    }

    @Test
    public void evaluateSuccess_dateTime() {
        Agreement agreement = Agreement.Builder.newInstance()
                .id(NegotiationMockObjectUtil.generateUUID())
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .target(NegotiationMockObjectUtil.TARGET)
                .timestamp(ZonedDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .permission(Collections.singletonList(NegotiationMockObjectUtil.PERMISSION))
                .build();

        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId(agreement.getId())
                .resourceId(agreement.getTarget())
                .userId("userId")
                .action(Action.READ)
                // accessTime is now
                .build();

        PolicyDecision policyDecision = policyDecisionPoint.evaluate(request, agreement);

        verify(publisher).publishEvent(eventCaptor.capture());

        assertNotNull(policyDecision);
        assertTrue(policyDecision.isAllowed());
        AuditEvent event = eventCaptor.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_APPROVE, event.getEventType());
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
                .constraint(Collections.singletonList(constraint))
                .build();

        Agreement agreement = Agreement.Builder.newInstance()
                .id(NegotiationMockObjectUtil.generateUUID())
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .target(NegotiationMockObjectUtil.TARGET)
                .timestamp(ZonedDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .permission(Collections.singletonList(permission))
                .build();

        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId(agreement.getId())
                .resourceId(agreement.getTarget())
                .userId("userId")
                .action(Action.READ)
                .build();

        PolicyDecision policyDecision = policyDecisionPoint.evaluate(request, agreement);

        verify(publisher).publishEvent(eventCaptor.capture());

        assertNotNull(policyDecision);
        assertFalse(policyDecision.isAllowed());
        AuditEvent event = eventCaptor.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED, event.getEventType());
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
                .constraint(Collections.singletonList(constraint))
                .build();

        Agreement agreement = Agreement.Builder.newInstance()
                .id(NegotiationMockObjectUtil.generateUUID())
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .target(NegotiationMockObjectUtil.TARGET)
                .timestamp(ZonedDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .permission(Collections.singletonList(permission)).build();

        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId(agreement.getId())
                .resourceId(agreement.getTarget())
                .userId("userId").action(Action.READ)
                .attribute(PolicyConstants.PURPOSE, "dsp_test")
                .build();

        PolicyDecision policyDecision = policyDecisionPoint.evaluate(request, agreement);

        verify(publisher).publishEvent(eventCaptor.capture());

        assertNotNull(policyDecision);
        assertTrue(policyDecision.isAllowed());
        AuditEvent event = eventCaptor.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_APPROVE, event.getEventType());
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
                .constraint(Collections.singletonList(constraint))
                .build();

        Agreement agreement = Agreement.Builder.newInstance()
                .id(NegotiationMockObjectUtil.generateUUID())
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .target(NegotiationMockObjectUtil.TARGET)
                .timestamp(ZonedDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .permission(Collections.singletonList(permission)).build();

        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId(agreement.getId())
                .resourceId(agreement.getTarget())
                .userId("userId").action(Action.READ)
                .attribute(PolicyConstants.PURPOSE, "dsp_test")
                .build();

        PolicyDecision policyDecision = policyDecisionPoint.evaluate(request, agreement);

        verify(publisher).publishEvent(eventCaptor.capture());

        assertNotNull(policyDecision);
        assertFalse(policyDecision.isAllowed());
        assertEquals("Purpose is not allowed", policyDecision.getMessage());
        AuditEvent event = eventCaptor.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED, event.getEventType());
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
                .constraint(Collections.singletonList(constraint))
                .build();

        Agreement agreement = Agreement.Builder.newInstance()
                .id(NegotiationMockObjectUtil.generateUUID())
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .target(NegotiationMockObjectUtil.TARGET)
                .timestamp(ZonedDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .permission(Collections.singletonList(permission)).build();

        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId(agreement.getId())
                .resourceId(agreement.getTarget())
                .userId("userId").action(Action.READ)
                .attribute(PolicyConstants.LOCATION, "spatial_test")
                .build();

        PolicyDecision policyDecision = policyDecisionPoint.evaluate(request, agreement);

        verify(publisher).publishEvent(eventCaptor.capture());

        assertNotNull(policyDecision);
        assertTrue(policyDecision.isAllowed());
        AuditEvent event = eventCaptor.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_APPROVE, event.getEventType());
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
                .constraint(Collections.singletonList(constraint))
                .build();

        Agreement agreement = Agreement.Builder.newInstance()
                .id(NegotiationMockObjectUtil.generateUUID())
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .target(NegotiationMockObjectUtil.TARGET)
                .timestamp(ZonedDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .permission(Collections.singletonList(permission)).build();

        PolicyRequest request = PolicyRequest.Builder.newInstance()
                .agreementId(agreement.getId())
                .resourceId(agreement.getTarget())
                .userId("userId").action(Action.READ)
                .attribute(PolicyConstants.LOCATION, "spatial_test")
                .build();

        PolicyDecision policyDecision = policyDecisionPoint.evaluate(request, agreement);

        verify(publisher).publishEvent(eventCaptor.capture());

        assertNotNull(policyDecision);
        assertFalse(policyDecision.isAllowed());
        assertEquals("Location is in the allowed", policyDecision.getMessage());
        AuditEvent event = eventCaptor.getValue();
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED, event.getEventType());
    }
}
