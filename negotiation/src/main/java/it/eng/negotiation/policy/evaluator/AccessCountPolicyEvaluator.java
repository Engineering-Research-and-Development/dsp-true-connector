package it.eng.negotiation.policy.evaluator;

import org.springframework.stereotype.Component;

import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.policy.model.Policy;
import it.eng.negotiation.policy.model.PolicyConstants;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.model.PolicyRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Evaluator for access control policies.
 * This evaluator checks if the user is allowed to access the resource.
 */
@Slf4j
@Component
public class AccessCountPolicyEvaluator implements PolicyEvaluator {

	@Override
	public LeftOperand getPolicyType() {
		return LeftOperand.COUNT;
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
		
		int count = (int) policy.getAttribute(PolicyConstants.COUNT);
		Operator operator = (Operator) policy.getAttribute(PolicyConstants.OPERATOR);
		int currentCount = (int) request.getAttribute(PolicyConstants.CURRENT_COUNT);
		
		if (operator == null) {
			return PolicyDecision.Builder.newInstance()
					.allowed(false)
					.message("Operator not set")
					.policyId(policy.getId())
					.policyType(getPolicyType())
					.build();
		}
		
		switch (operator) {	
		case LT:
			if (!(currentCount < count)) {
				return PolicyDecision.Builder.newInstance()
						.allowed(false)
						.message("Access count exceeded")
						.policyId(policy.getId())
						.policyType(getPolicyType())
						.build();
			}
			break;
		case LTEQ:
			if (!(currentCount <= count)) {
				return PolicyDecision.Builder.newInstance()
						.allowed(false)
						.message("Access count exceeded")
						.policyId(policy.getId())
						.policyType(getPolicyType())
						.build();
			}
			break;
		/* Does following ones make sense?
		case GT:
			if (currentCount <= count) {
				return PolicyDecision.Builder.newInstance()
						.allowed(false)
						.message("Access count exceeded")
						.policyId(policy.getId())
						.policyType(getPolicyType())
						.build();
			}
			break;
		case GTEQ:
			if (currentCount < count) {
				return PolicyDecision.Builder.newInstance()
						.allowed(false)
						.message("Access count exceeded")
						.policyId(policy.getId())
						.policyType(getPolicyType())
						.build();
			}
			break;
			*/
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
                .message("Access count policy passed")
                .policyId(policy.getId())
                .policyType(getPolicyType())
                .build();
	}

}
