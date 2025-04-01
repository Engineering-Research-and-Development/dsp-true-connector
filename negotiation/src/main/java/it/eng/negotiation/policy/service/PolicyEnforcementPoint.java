package it.eng.negotiation.policy.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.model.PolicyRequest;
import it.eng.tools.usagecontrol.UsageControlProperties;
import lombok.extern.slf4j.Slf4j;

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
	
	
	public PolicyEnforcementPoint(UsageControlProperties usageControlProperties, PolicyInformationPoint policyInformationPoint,
			PolicyDecisionPoint policyDecisionPoint) {
		this.usageControlProperties = usageControlProperties;
		this.policyInformationPoint = policyInformationPoint;
		this.policyDecisionPoint = policyDecisionPoint;
	}

	/**
	 * Enforces a policy for a given agreement and operation.
	 * @param agreement the agreement
	 * @param operation the operation to be performed
	 * @return the policy decision
	 */
	public PolicyDecision enforcePolicy(Agreement agreement, String operation) {
		if (!usageControlProperties.usageControlEnabled()) {
			log.warn("!!!!! UsageControl DISABLED - will not check if policy is present or valid !!!!!");
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
//                .action(operation);
        
        // Add all attributes from the PIP
        attributes.forEach(requestBuilder::attribute);
        
        PolicyRequest request = requestBuilder.build();
		
        // Evaluate policy
        PolicyDecision decision = policyDecisionPoint.evaluate(request, agreement);
        
        // Enforce decision
        if (!decision.isAllowed()) {
            log.error("Policy violation: {}", decision.getMessage());
			return PolicyDecision.Builder.newInstance()
					.allowed(false)
					.message("Policy violation: " + decision.getMessage())
					.build();
        }
        
        // Log the successful policy enforcement
        log.debug("Policy enforcement passed: {}", decision.getMessage());
        
        // Audit the access
        auditAccess(agreement, null, operation, decision);
        
        return decision;
		
	}
	
	
	 /**
     * Audits an access to a resource.
     *
     * @param agreement the agreement
     * @param userId the user ID
     * @param operation the operation
     * @param decision the policy decision
     */
    private void auditAccess(Agreement agreement, String userId, String operation, PolicyDecision decision) {
        // In a real implementation, we would store the audit record in a database
        // For now, we'll just log it
        log.info("AUDIT: User {} performed {} operation on dataset {} (agreement {}). Decision: {}",
                userId != null ? userId : "system",
                operation,
                agreement.getTarget(),
                agreement.getId(),
                decision.isAllowed() ? "ALLOWED" : "DENIED");
    }
}
