package it.eng.negotiation.policy.evaluator;

import java.util.List;

import org.springframework.stereotype.Component;

import it.eng.negotiation.model.Operator;
import it.eng.negotiation.policy.model.Policy;
import it.eng.negotiation.policy.model.PolicyConstants;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.model.PolicyRequest;
import it.eng.negotiation.policy.model.PolicyType;
import lombok.extern.slf4j.Slf4j;

/**
 * Evaluator for purpose policies.
 * This evaluator checks if the access is allowed for the specified purpose.
 */
@Slf4j
@Component
public class PurposePolicyEvaluator implements PolicyEvaluator {

	@Override
    public PolicyType getPolicyType() {
        return PolicyType.PURPOSE;
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

        // Get the purpose from the request attributes
        String purpose = (String) request.getAttribute(PolicyConstants.PURPOSE);
        if (purpose == null || purpose.isEmpty()) {
            return PolicyDecision.Builder.newInstance()
                    .allowed(false)
                    .message("Purpose is missing")
                    .policyId(policy.getId())
                    .policyType(getPolicyType())
                    .build();
        }
        
        Operator operator = (Operator) policy.getAttribute("operator");
		if (operator == null) {
			return PolicyDecision.Builder.newInstance()
					.allowed(false)
					.message("Operator not set")
					.policyId(policy.getId())
					.policyType(getPolicyType())
					.build();
		}
		
		@SuppressWarnings("unchecked")
		List<String> allowedPurposes = (List<String>) policy.getAttribute(PolicyConstants.ALLOWED_PURPOSES);

		switch (operator) {
		case IS_ANY_OF:
			// Check if the purpose is in the allowed purposes list
			if (allowedPurposes != null && !allowedPurposes.isEmpty() && !allowedPurposes.contains(purpose)) {
				return PolicyDecision.Builder.newInstance()
						.allowed(false)
						.message("Purpose is not in the allowed purposes list")
						.policyId(policy.getId())
						.policyType(getPolicyType())
						.build();
			}
			break;
		case EQ:
			// exactly equals with single purpose
			if (allowedPurposes != null && !allowedPurposes.isEmpty() && !allowedPurposes.get(0).equals(purpose)) {
				return PolicyDecision.Builder.newInstance()
						.allowed(false)
						.message("Purpose is not allowed")
						.policyId(policy.getId())
						.policyType(getPolicyType())
						.build();
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

        // Check if the purpose is in the denied purposes list
        @SuppressWarnings("unchecked")
        List<String> deniedPurposes = (List<String>) policy.getAttribute(PolicyConstants.DENIED_PURPOSES);
        if (deniedPurposes != null && deniedPurposes.contains(purpose)) {
            return PolicyDecision.Builder.newInstance()
                    .allowed(false)
                    .message("Purpose is in the denied purposes list")
                    .policyId(policy.getId())
                    .policyType(getPolicyType())
                    .build();
        }

        // All checks passed
        return PolicyDecision.Builder.newInstance()
                .allowed(true)
                .message("Purpose policy passed")
                .policyId(policy.getId())
                .policyType(getPolicyType())
                .build();
    }

}
