package it.eng.negotiation.policy.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import it.eng.negotiation.exception.PolicyEnforcementException;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.policy.model.Policy;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Policy Administration Point (PAP) component.
 * The PAP is responsible for managing policies/policyEnforcement (create, read, update, delete).
 */
@Service
@Slf4j
public class PolicyAdministrationPoint {
	
	private final PolicyEnforcementRepository policyEnforcementRepository;
	
	public PolicyAdministrationPoint(PolicyEnforcementRepository policyEnforcementRepository) {
		super();
		this.policyEnforcementRepository = policyEnforcementRepository;
	}
	
	/**
	 * Create policy enforcement object in DB.
	 * @param agreementId
	 */
	public void createPolicyEnforcement(String agreementId) {
		PolicyEnforcement pe = new PolicyEnforcement();
		pe.setAgreementId(agreementId);
		pe.setCount(1);
		policyEnforcementRepository.save(pe);
	}
	
	/**
	 * Check if PolicyEnforcement for agreement exists.
	 * Must be sure that policy can be enforced after data is returned
	 * @param agreementId agreementId to check
	 * @return boolean value if policy enforcement exists or not
	 */
	public boolean policyEnforcementExists(String agreementId) {
		return policyEnforcementRepository.findByAgreementId(agreementId).isPresent();
	}
	
	/**
	 * Get access count for agreementId.
	 * 
	 * @param agreementId agreementId to check
	 */
	public synchronized void updateAccessCount(String agreementId) {
		log.info("Updating access count for agreementId {}", agreementId);
		PolicyEnforcement pe = policyEnforcementRepository.findByAgreementId(agreementId)
				.orElseThrow(() -> new PolicyEnforcementException("PolicyManager for agreementId  '" + agreementId + "' not found"));
		pe.setCount(pe.getCount() + 1);
		policyEnforcementRepository.save(pe);
		log.debug("Access count updated to {}", pe.getCount());
	}

	/**
	 * Creates a new policy.
	 *
	 * @param policy the policy to create
	 * @return the created policy
	 */
	Policy createPolicy(Policy policy) {
		// Implementation for creating a policy
		return null;
	}

	/**
	 * Retrieves a policy by ID.
	 *
	 * @param policyId the policy ID
	 * @return the policy, or empty if not found
	 */
	Optional<Policy> getPolicy(String policyId) {
		// Implementation for retrieving a policy by ID
		return Optional.empty();
	}

	/**
	 * Retrieves all policies.
	 *
	 * @return the list of policies
	 */
	List<Policy> getAllPolicies() {
		// Implementation for retrieving all policies
		return List.of();
	}

	/**
	 * Retrieves policies by agreement ID.
	 *
	 * @param agreementId the agreement ID
	 * @return the list of policies
	 */
	List<Policy> getPoliciesByAgreementId(String agreementId) {
		// Implementation for retrieving policies by agreement ID
		return List.of();
	}

	/**
	 * Retrieves policies by type.
	 *
	 * @param type the policy type
	 * @return the list of policies
	 */
	List<Policy> getPoliciesByType(LeftOperand type) {
		// Implementation for retrieving policies by type
		return List.of();
	}

	/**
	 * Updates a policy.
	 *
	 * @param policyId the policy ID
	 * @param policy   the updated policy
	 * @return the updated policy, or empty if not found
	 */
	Optional<Policy> updatePolicy(String policyId, Policy policy) {
		// Implementation for updating a policy
		return Optional.empty();
	}

	/**
	 * Deletes a policy.
	 *
	 * @param policyId the policy ID
	 * @return true if the policy was deleted, false otherwise
	 */
	boolean deletePolicy(String policyId) {
		// Implementation for deleting a policy
		return false;
	}

	/**
	 * Associates a policy with an agreement.
	 *
	 * @param policyId    the policy ID
	 * @param agreementId the agreement ID
	 * @return true if the policy was associated with the agreement, false otherwise
	 */
	boolean associatePolicyWithAgreement(String policyId, String agreementId) {
		// Implementation for associating a policy with an agreement
		return false;
	}

	/**
	 * Disassociates a policy from an agreement.
	 *
	 * @param policyId    the policy ID
	 * @param agreementId the agreement ID
	 * @return true if the policy was disassociated from the agreement, false
	 *         otherwise
	 */
	boolean disassociatePolicyFromAgreement(String policyId, String agreementId) {
		// Implementation for disassociating a policy from an agreement
		return false;
	}
}
