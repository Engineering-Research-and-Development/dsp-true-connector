package it.eng.negotiation.policy.service;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.model.PolicyRequest;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.usagecontrol.UsageControlProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Implementation of the Policy Enforcement Point (PEP) interface. This service
 * intercepts access requests, forwards them to the PDP for evaluation, and
 * enforces the decisions made by the PDP.
 */
@Service
@Slf4j
public class PolicyEnforcementPoint {

    private final UsageControlProperties usageControlProperties;

    private final PolicyInformationPoint policyInformationPoint;

    private final PolicyDecisionPoint policyDecisionPoint;

    private final ContractNegotiationRepository contractNegotiationRepository;

    private final ApplicationEventPublisher publisher;

    public PolicyEnforcementPoint(UsageControlProperties usageControlProperties,
                                  PolicyInformationPoint policyInformationPoint,
                                  PolicyDecisionPoint policyDecisionPoint,
                                  ContractNegotiationRepository contractNegotiationRepository,
                                  ApplicationEventPublisher publisher) {
        this.usageControlProperties = usageControlProperties;
        this.policyInformationPoint = policyInformationPoint;
        this.policyDecisionPoint = policyDecisionPoint;
        this.contractNegotiationRepository = contractNegotiationRepository;
        this.publisher = publisher;
    }

    /**
     * Enforces a policy for a given agreement and operation.
     *
     * @param agreement the agreement
     * @param operation the operation to be performed
     * @return the policy decision
     */
    public PolicyDecision enforcePolicy(Agreement agreement, String operation) {
        ContractNegotiation contractNegotiation = contractNegotiationRepository.findByAgreement(agreement.getId())
                .orElseThrow(() -> new ContractNegotiationAPIException("Contract negotiation with agreement Id " + agreement.getId() + " not found."));

        if (!usageControlProperties.usageControlEnabled()) {
            log.warn("!!!!! UsageControl DISABLED - will not check if policy is present or valid !!!!!");
            publisher.publishEvent(AuditEvent.Builder.newInstance()
                    .eventType(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_APPROVE)
                    .description("Usage control disabled")
                    .details(Map.of("agreement", agreement,
                            "contractNegotiation", contractNegotiation))
                    .build());
            return PolicyDecision.Builder.newInstance()
                    .allowed(true)
                    .message("Usage control disabled")
                    .build();
        }

        log.debug("Enforcing policy for transfer process: {}", agreement.getId());

        // Create policy request with attributes from the Policy Information Point
        Map<String, Object> attributes = policyInformationPoint.getAllAttributes(agreement);

        PolicyRequest.Builder requestBuilder = PolicyRequest.Builder.newInstance()
                .agreementId(agreement.getId())
                .resourceId(agreement.getTarget());

        // Add all attributes from the PIP
        attributes.forEach(requestBuilder::attribute);

        PolicyRequest request = requestBuilder.build();

        // Evaluate policy
        PolicyDecision decision = policyDecisionPoint.evaluate(request, agreement);

        log.debug("Policy enforcement passed: {}", decision.getMessage());
        auditAccess(agreement, contractNegotiation, operation, decision);

        return decision;
    }

    /**
     * Audits access to a resource.
     *
     * @param agreement           the agreement
     * @param contractNegotiation contractNegotiation for which the access is audited
     * @param operation           the operation
     * @param decision            the policy decision
     */
    private void auditAccess(Agreement agreement, ContractNegotiation contractNegotiation, String operation, PolicyDecision decision) {
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(decision.isAllowed() ? AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_APPROVE : AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED)
                .description(decision.getMessage())
                .details(Map.of("agreement", agreement,
                        "contractNegotiation", contractNegotiation,
                        "operation", operation,
                        "decision", decision))
                .build());
    }
}
