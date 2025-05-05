package it.eng.negotiation.policy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service class for providing purpose-related functionalities.
 */
@Service
@Slf4j
public class PurposeService {

	@Value("${application.usagecontrol.constraint.purpose}")
	private String connectorPurpose;
	
	/**
	 * For now return purpose from property file.
	 * Once verifiable credential will be used, this method will be used to
	 * return purpose based on claim.
	 * @return connector purpose
	 */
	public String getPurpose() {
		log.info("Retrieving purpose");
		return connectorPurpose;
	}
}
