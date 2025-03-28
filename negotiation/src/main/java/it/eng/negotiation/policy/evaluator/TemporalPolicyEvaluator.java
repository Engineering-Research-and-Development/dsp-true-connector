package it.eng.negotiation.policy.evaluator;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import it.eng.negotiation.model.Operator;
import it.eng.negotiation.policy.model.Policy;
import it.eng.negotiation.policy.model.PolicyConstants;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.model.PolicyRequest;
import it.eng.negotiation.policy.model.PolicyType;
import lombok.extern.slf4j.Slf4j;

/**
 * Evaluator for temporal policies.
 * This evaluator checks if the access is allowed at the current time.
 */
@Slf4j
@Component
public class TemporalPolicyEvaluator implements PolicyEvaluator {

	@Override
	public PolicyType getPolicyType() {
		return PolicyType.TEMPORAL;
	}

	@Override
	public PolicyDecision evaluate(Policy policy, PolicyRequest request) {
		if (!policy.isValidNow()) {
            return PolicyDecision.Builder.newInstance()
                    .allowed(false)
                    .message("Policy is not valid at the current time")
                    .policyId(policy.getId())
                    .policyType(getPolicyType())
                    .build();
        }

        // Get the access time from the request attributes
        LocalDateTime accessTime = (LocalDateTime) request.getAttribute(PolicyConstants.ACCESS_TIME);
        if (accessTime == null) {
            // If access time is not provided, use the current time
            accessTime = LocalDateTime.now();
        }

        // Check if the access time is within the allowed time range
        LocalDateTime dateTime = (LocalDateTime) policy.getAttribute(PolicyConstants.DATE_TIME);
        Operator operator = (Operator) policy.getAttribute(PolicyConstants.OPERATOR);
		if (operator == null) {
			return PolicyDecision.Builder.newInstance()
					.allowed(false)
					.message("Operator not set")
					.policyId(policy.getId())
					.policyType(getPolicyType())
					.build();
		}
		
		switch (operator) {
		case LT, LTEQ:
			if (dateTime != null && accessTime.isBefore(dateTime)) {
				return PolicyDecision.Builder.newInstance()
						.allowed(false)
						.message("Access time is after the allowed time")
						.policyId(policy.getId())
						.policyType(getPolicyType())
						.build();
			}
			break;
		case GT, GTEQ:
			if (dateTime != null && !accessTime.isAfter(dateTime)) {
				return PolicyDecision.Builder.newInstance()
						.allowed(false)
						.message("Access time is before the allowed time")
						.policyId(policy.getId())
						.policyType(getPolicyType()).build();
			}
			break;
		default:
			return PolicyDecision.Builder.newInstance()
					.allowed(false)
					.message("Operator not supported")
					.policyId(policy.getId())
					.policyType(getPolicyType())
					.build();
		}
		
        // All checks passed
        return PolicyDecision.Builder.newInstance()
                .allowed(true)
                .message("Temporal policy passed")
                .policyId(policy.getId())
                .policyType(getPolicyType())
                .build();
    }

}
