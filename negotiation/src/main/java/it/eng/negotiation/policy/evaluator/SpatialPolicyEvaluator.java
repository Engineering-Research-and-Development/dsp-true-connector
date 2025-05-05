package it.eng.negotiation.policy.evaluator;

import java.util.List;

import org.springframework.stereotype.Component;

import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.policy.model.Policy;
import it.eng.negotiation.policy.model.PolicyConstants;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.model.PolicyRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Evaluator for spatial policies.
 * This evaluator checks if the access is allowed from the current location.
 */
@Slf4j
@Component
public class SpatialPolicyEvaluator implements PolicyEvaluator {

	@Override
	public LeftOperand getPolicyType() {
		return LeftOperand.SPATIAL;
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

        // Get the location from the request attributes
        String location = (String) request.getAttribute(PolicyConstants.LOCATION);
        if (location == null || location.isEmpty()) {
            return PolicyDecision.Builder.newInstance()
                    .allowed(false)
                    .message("Location is missing")
                    .policyId(policy.getId())
                    .policyType(getPolicyType())
                    .build();
        }
        
        Operator operator = (Operator) policy.getAttribute(PolicyConstants.OPERATOR);
  		if (operator == null) {
  			return PolicyDecision.Builder.newInstance()
  					.allowed(false)
  					.message("Operator not set")
  					.policyId(policy.getId())
  					.policyType(getPolicyType())
  					.build();
  		}
  		
  		@SuppressWarnings("unchecked")
  		List<String> allowedLocations = (List<String>) policy.getAttribute(PolicyConstants.ALLOWED_LOCATIONS);

  		switch (operator) {
		case IS_ANY_OF:
			// Check if the location is in the allowed locations list
			if (allowedLocations != null && !allowedLocations.isEmpty() && !allowedLocations.contains(location)) {
				return PolicyDecision.Builder.newInstance().allowed(false)
						.message("Location is not in the allowed locations list")
						.policyId(policy.getId())
						.policyType(getPolicyType())
						.build();
			}
			break;
		case EQ:
			if (allowedLocations != null && !allowedLocations.isEmpty() && !allowedLocations.get(0).equals(location)) {
				return PolicyDecision.Builder.newInstance().allowed(false)
						.message("Location is in the allowed")
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
  		

        // Check if the location is in the denied locations list
        @SuppressWarnings("unchecked")
        List<String> deniedLocations = (List<String>) policy.getAttribute(PolicyConstants.DENIED_LOCATIONS);
        if (deniedLocations != null && deniedLocations.contains(location)) {
            return PolicyDecision.Builder.newInstance()
                    .allowed(false)
                    .message("Location is in the denied locations list")
                    .policyId(policy.getId())
                    .policyType(getPolicyType())
                    .build();
        }

        // All checks passed
        return PolicyDecision.Builder.newInstance()
                .allowed(true)
                .message("Spatial policy passed")
	                .policyId(policy.getId())
	                .policyType(getPolicyType())
	                .build();
	    }
}
