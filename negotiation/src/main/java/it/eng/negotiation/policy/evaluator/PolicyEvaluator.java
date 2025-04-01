package it.eng.negotiation.policy.evaluator;

import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.policy.model.Policy;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.model.PolicyRequest;

/**
 * Interface for policy evaluators.
 * Policy evaluators evaluate specific types of policies against requests.
 */
public interface PolicyEvaluator {
	/**
     * Returns the type of policy that this evaluator can evaluate.
     *
     * @return the policy type
     */
    LeftOperand getPolicyType();

    /**
     * Evaluates a policy against a request and returns a decision.
     *
     * @param policy the policy to evaluate
     * @param request the request to evaluate against
     * @return the policy decision
     */
    PolicyDecision evaluate(Policy policy, PolicyRequest request);
}
