package it.eng.negotiation.policy.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of the Policy Information Point (PIP) interface. This service
 * retrieves attribute values needed for policy evaluation.
 */
@Service
@Slf4j
public class PolicyInformationPoint {
	
	private final PolicyEnforcementRepository policyEnforcementRepository;
	
	public PolicyInformationPoint(PolicyEnforcementRepository policyEnforcementRepository) {
		this.policyEnforcementRepository = policyEnforcementRepository;
	}

	public String getLocation() {
		// In a real implementation, we would get the location from a location service
		// For now, we'll just return a default location
		log.debug("Getting location");
		return "EU";
	}

	public LocalDateTime getAccessTime() {
		// Simply return the current time
		return LocalDateTime.now();
	}
	
	public String getAccessPurpose() {
		// In a real implementation, we would get the purpose from a purpose service
        log.debug("Getting purpose");
		return "dsp";
    }
	
	public Map<String, Object> getAllAttributes(Agreement agreement) {
        Map<String, Object> attributes = new HashMap<>();
        
        // Add transfer process attributes
        attributes.put("agreementId", agreement.getId());
        attributes.put("assignee", agreement.getAssignee());
        attributes.put("assigner", agreement.getAssigner());
        
        // Add access time
        attributes.put("accessTime", getAccessTime());
        // Add location
        attributes.put("location", getLocation());
        // Add purpose
        attributes.put("purpose", getAccessPurpose());
        
		attributes.put("currentCount",
				policyEnforcementRepository.findByAgreementId(agreement.getId()).map(pe -> pe.getCount()).orElse(Integer.MAX_VALUE));
        
        return attributes;
    }
	
}
