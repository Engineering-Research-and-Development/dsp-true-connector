package it.eng.negotiation.policy.service;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.usagecontrol.UsageControlProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyEnforcementPointTest {

    @Mock
    private UsageControlProperties usageControlProperties;
    @Mock
    private PolicyInformationPoint policyInformationPoint;
    @Mock
    private PolicyDecisionPoint policyDecisionPoint;
    @Mock
    private ContractNegotiationRepository contractNegotiationRepository;
    @Mock
    private ApplicationEventPublisher publisher;

    @InjectMocks
    private PolicyEnforcementPoint policyEnforcementPoint;

    ArgumentCaptor<AuditEvent> auditEventCaptor = ArgumentCaptor.forClass(AuditEvent.class);

    @Test
    public void enforcePolicy_usageControlDisabled() {
        Agreement agreement = NegotiationMockObjectUtil.AGREEMENT;
        when(contractNegotiationRepository.findByAgreement(any()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_FINALIZED));

        // Mock the behavior of usageControlProperties
        when(usageControlProperties.usageControlEnabled()).thenReturn(false);

        // Call the method to test
        policyEnforcementPoint.enforcePolicy(agreement, "use");

        // Verify that the policy decision is allowed when usage control is disabled
        verify(policyDecisionPoint, never()).evaluate(any(), any(Agreement.class));
        verify(policyInformationPoint, never()).getAllAttributes(any());

        verify(publisher, times(1)).publishEvent(auditEventCaptor.capture());
        AuditEvent auditEvent = auditEventCaptor.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DISABLED, auditEvent.getEventType());
    }

    @Test
    void enforcePolicy_allowed() {
        Agreement agreement = NegotiationMockObjectUtil.AGREEMENT;
        when(contractNegotiationRepository.findByAgreement(any()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_FINALIZED));

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

        verify(publisher, times(1)).publishEvent(auditEventCaptor.capture());
        AuditEvent auditEvent = auditEventCaptor.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_APPROVE, auditEvent.getEventType());
        assertEquals("Test case - allowed", auditEvent.getDescription());
    }

    @Test
    public void enforcePolicy_denied() {
        Agreement agreement = NegotiationMockObjectUtil.AGREEMENT;
        when(contractNegotiationRepository.findByAgreement(any()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_FINALIZED));

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

        verify(publisher, times(1)).publishEvent(auditEventCaptor.capture());
        AuditEvent auditEvent = auditEventCaptor.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED, auditEvent.getEventType());
    }

    @Test
    void enforcePolicy_contractNegotiationNotFound() {
        Agreement agreement = NegotiationMockObjectUtil.AGREEMENT;
        when(contractNegotiationRepository.findByAgreement(any()))
                .thenReturn(Optional.empty());

        ContractNegotiationAPIException exception = assertThrows(ContractNegotiationAPIException.class,
                () -> policyEnforcementPoint.enforcePolicy(agreement, "use"));

        assertEquals("Contract negotiation with agreement Id " + agreement.getId() + " not found.",
                exception.getMessage());

        verify(policyDecisionPoint, never()).evaluate(any(), any(Agreement.class));
        verify(policyInformationPoint, never()).getAllAttributes(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void enforcePolicy_nonFinalizedContractNegotiation() {
        Agreement agreement = NegotiationMockObjectUtil.AGREEMENT;
        when(contractNegotiationRepository.findByAgreement(any()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));

        PolicyDecision policyDecision = policyEnforcementPoint.enforcePolicy(agreement, "use");

        assertNotNull(policyDecision);
        assertFalse(policyDecision.isAllowed());
        assertEquals("Contract negotiation with agreement Id " + agreement.getId() + " not FINALIZED.",
                policyDecision.getMessage());

        verify(policyDecisionPoint, never()).evaluate(any(), any(Agreement.class));
        verify(policyInformationPoint, never()).getAllAttributes(any());

        verify(publisher).publishEvent(auditEventCaptor.capture());
        AuditEvent auditEvent = auditEventCaptor.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED, auditEvent.getEventType());
        assertEquals("Contract negotiation with agreement Id " + agreement.getId() + " not FINALIZED.",
                auditEvent.getDescription());
    }

}
