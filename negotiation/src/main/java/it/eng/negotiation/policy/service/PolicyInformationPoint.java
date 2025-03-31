package it.eng.negotiation.policy.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.policy.model.PolicyConstants;
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
	private final LocationService locationService;
	private final PurposeService purposeService;
	
	public PolicyInformationPoint(PolicyEnforcementRepository policyEnforcementRepository,
			LocationService locationService, PurposeService purposeService) {
		this.policyEnforcementRepository = policyEnforcementRepository;
		this.locationService = locationService;
		this.purposeService = purposeService;
	}

	public String getLocation() {
		// In a real implementation, we would get the location from a location service
		// For now, we'll just return a default location
		log.debug("Getting location");
		return locationService.getConnectorLocation();
	}

	public LocalDateTime getAccessTime() {
		// Simply return the current time
		return LocalDateTime.now();
	}
	
	public String getAccessPurpose() {
		// In a real implementation, we would get the purpose from a purpose service
        log.debug("Getting purpose");
		return purposeService.getPurpose();
    }
	
	public Map<String, Object> getAllAttributes(Agreement agreement) {
        Map<String, Object> attributes = new HashMap<>();
        
        // Add transfer process attributes
        attributes.put("agreementId", agreement.getId());
        attributes.put("assignee", agreement.getAssignee());
        attributes.put("assigner", agreement.getAssigner());
        
        // Add access time
        attributes.put(PolicyConstants.ACCESS_TIME, getAccessTime());
        // Add location
        attributes.put(PolicyConstants.LOCATION, getLocation());
        // Add purpose
        attributes.put(PolicyConstants.PURPOSE, getAccessPurpose());
        
		attributes.put(PolicyConstants.CURRENT_COUNT,
				policyEnforcementRepository.findByAgreementId(agreement.getId()).map(pe -> pe.getCount()).orElse(Integer.MAX_VALUE));
        
        return attributes;
    }
	
}
