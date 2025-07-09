package it.eng.negotiation.policy.service;

import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.policy.evaluator.PolicyEvaluator;
import it.eng.negotiation.policy.model.Policy;
import it.eng.negotiation.policy.model.PolicyConstants;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.model.PolicyRequest;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of the Policy Decision Point (PDP) interface. This service
 * evaluates policies against requests and makes decisions.
 */
@Service
@Slf4j
public class PolicyDecisionPoint {

    private final Map<LeftOperand, PolicyEvaluator> evaluators;
    private final ApplicationEventPublisher publisher;

    public PolicyDecisionPoint(List<PolicyEvaluator> evaluators, ApplicationEventPublisher publisher) {
        this.evaluators = evaluators.stream()
                .collect(Collectors.toMap(PolicyEvaluator::getPolicyType, Function.identity()));
        this.publisher = publisher;
    }

    /**
     * Evaluates a policy request and returns a decision.
     *
     * @param request   the policy request
     * @param agreement the agreement to be evaluated
     * @return the policy decision
     */
    public PolicyDecision evaluate(PolicyRequest request, Agreement agreement) {
        String agreementId = request.getAgreementId();
        String resourceId = request.getResourceId();
        String userId = request.getUserId();
        Action action = request.getAction();

        log.debug("Evaluating policy for agreement {}, resource {}, user {}, action {}", agreementId, resourceId, userId, action);

        // Get the agreement
        if (agreementId == null || agreementId.isEmpty()) {
            PolicyDecision decision = PolicyDecision.Builder.newInstance()
                    .allowed(false)
                    .message("Agreement ID is missing")
                    .build();
//            decisionCache.put(cacheKey, decision);
            publisher.publishEvent(
                    AuditEvent.Builder.newInstance()
                            .description("Policy evaluation failed: Agreement ID is missing")
                            .eventType(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED)
                            .details(createAuditDetails(agreement, resourceId, userId, action, null, decision.getMessage()))
                            .build());
            return decision;
        }

        // Get the policies from the agreement
        // convert all constraints to policies
        List<Policy> policies = convertConstraintsToPolicies(agreement);
        if (policies == null || policies.isEmpty()) {
            PolicyDecision decision = PolicyDecision.Builder.newInstance()
                    .allowed(false)
                    .message("No policies found for agreement " + agreementId)
                    .build();
//            decisionCache.put(cacheKey, decision);
            publisher.publishEvent(AuditEvent.Builder.newInstance()
                    .description("No policies found for agreement " + agreementId)
                    .eventType(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED)
                    .details(createAuditDetails(agreement, resourceId, userId, action, null, decision.getMessage()))
                    .build());
            return decision;
        }

        // Evaluate each policy
        for (Policy policy : policies) {
            if (!policy.isValidNow()) {
                log.debug("Policy {} is not valid at the current time", policy.getId());
                continue;
            }

            LeftOperand policyType = policy.getType();
            PolicyEvaluator evaluator = evaluators.get(policyType);
            if (evaluator == null) {
                log.warn("No evaluator found for policy type {}", policyType);
                continue;
            }

            PolicyDecision decision = evaluator.evaluate(policy, request);
            if (!decision.isAllowed()) {
                log.debug("Policy {} denied access: {}", policy.getId(), decision.getMessage());
//                decisionCache.put(cacheKey, decision);
                publisher.publishEvent(AuditEvent.Builder.newInstance()
                        .description("Policy evaluation denied for agreement " + agreementId)
                        .eventType(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED)
                        .details(createAuditDetails(agreement, resourceId, userId, action, policy, decision.getMessage()))
                        .build());
                return decision;
            }
        }
        // All policies passed
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .description("Policy evaluation approved for agreement " + agreementId)
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_APPROVE)
                .details(createAuditDetails(agreement, resourceId, userId, action, null, "All policies passed"))
                .build());
        return PolicyDecision.Builder.newInstance()
                .allowed(true)
                .message("All policies passed")
                .build();
//        decisionCache.put(cacheKey, decision);
    }

    private List<Policy> convertConstraintsToPolicies(Agreement agreement) {
        List<Policy> policies = new ArrayList<>();
        agreement.getPermission().forEach(p -> {
            p.getConstraint().forEach(c -> {
                Policy policy = convertToPolicy(c, agreement.getId());
                policies.add(policy);
            });
        });
        return policies;
    }

    private Policy convertToPolicy(Constraint c, String agreementId) {
        return Policy.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .agreementId(agreementId)
                .type(c.getLeftOperand())
                .attributes(getPolicyAttributesFromConstraint(c))
                .enabled(true)
                .build();
    }

    private Map<String, Object> getPolicyAttributesFromConstraint(Constraint c) {
        Map<String, Object> attributes = new HashMap<>();
        switch (c.getLeftOperand()) {
            case COUNT:
                attributes.put(PolicyConstants.COUNT, Integer.valueOf(c.getRightOperand()));
                break;
            case DATE_TIME:
                // convert string to LocalDateTime
                LocalDateTime dateTime = LocalDateTime.parse(c.getRightOperand(), DateTimeFormatter.ISO_DATE_TIME);
                attributes.put(PolicyConstants.DATE_TIME, dateTime);
                break;
            case PURPOSE:
                attributes.put(PolicyConstants.ALLOWED_PURPOSES, List.of(c.getRightOperand()));
                break;
            case SPATIAL:
                attributes.put(PolicyConstants.ALLOWED_LOCATIONS, List.of(c.getRightOperand()));
                break;
            default:
                return null;
        }
        attributes.put(PolicyConstants.OPERATOR, c.getOperator());
        return attributes;
    }

    private Map<String, Object> createAuditDetails(Agreement agreement, String resourceId, String userId, Action action,
                                                   Policy policy, String message) {
        Map<String, Object> details = new HashMap<>();
        if (agreement != null) {
            details.put("agreement", agreement);
        }
        if (resourceId != null) {
            details.put("resourceId", resourceId);
        }
        if (userId != null) {
            details.put("userId", userId);
        }
        if (action != null) {
            details.put("action", action);
        }
        if (policy != null) {
            details.put("policy", policy);
        }
        if (message != null) {
            details.put("message", message);
        }
        return details;
    }

}
