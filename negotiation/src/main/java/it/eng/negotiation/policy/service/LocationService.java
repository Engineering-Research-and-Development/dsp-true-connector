package it.eng.negotiation.policy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service class for providing location-related functionalities.
 */
@Service
@Slf4j
public class LocationService {
			
	@Value("${application.usagecontrol.constraint.location}")	
	private String connectorLocation;
	
	/**
	 * For now return location from property file.
	 * Once verifiable credential will be used, this method will be used to
	 * return location based on claim.
	 * @return connector location
	 */
	public String getConnectorLocation() {
		log.info("Retrieving location");
		return connectorLocation;
	}

}
