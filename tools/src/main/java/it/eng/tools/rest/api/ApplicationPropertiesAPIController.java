package it.eng.tools.rest.api;

import java.util.List;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.eng.tools.exception.ApplicationPropertyNotChangedAPIException;
import it.eng.tools.exception.ApplicationPropertyNotFoundAPIException;
import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.ApplicationPropertiesService;
import lombok.extern.java.Log;

/**
 * Controller for managing application properties; get and update
 */
@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/api/v1/properties")
@Log
public class ApplicationPropertiesAPIController {

	private final ApplicationPropertiesService propertiesService;

	/**
	 * Constructor
	 * @param service ApplicationPropertiesService
	 * @param applicationEventPublisher ApplicationEventPublisher
	 */
	public ApplicationPropertiesAPIController(ApplicationPropertiesService service) {
		super();
		this.propertiesService = service;
	}

	/**
	 * GET properties by prefix
	 * @param key_prefix prefix to filter
	 * @return List of properties
	 */
	@GetMapping(path = "/")
	public ResponseEntity<GenericApiResponse<List<ApplicationProperty>>> getProperties(@RequestParam(required = false) String key_prefix) {
		log.info("getProperties()");
		if(key_prefix != null && !key_prefix.isBlank()) log.info(" with key_prefix " + key_prefix);
		var properties = propertiesService.getProperties(key_prefix);

		GenericApiResponse<List<ApplicationProperty>> genericApiResponse = GenericApiResponse.success(properties, "Application properties with prefix");
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(genericApiResponse);
	}

	/**
	 * Get property by key
	 * @param key property key/id
	 * @return property
	 */
	@GetMapping(path = "/{key}")
	public ResponseEntity<GenericApiResponse<ApplicationProperty>> getPropertyByKey(@PathVariable String key) {
		log.info("Fetching property with key " + key);

		ApplicationProperty property = propertiesService.getPropertyByKey(key).orElseThrow(() -> new ApplicationPropertyNotFoundAPIException("Property with key " + key + " not found"));

		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(property, "Application property for key"));
	}

	/**
	 * Update property
	 * @param property updated property for persisting
	 * @return Response
	 */
	@PutMapping(path = "/")
	public ResponseEntity<GenericApiResponse<ApplicationProperty>> modifyProperty(@RequestBody ApplicationProperty property) {
		log.info("modifyProperty(...) ");
		log.info("property = " + property);

		ApplicationProperty storedProperty = null;

		Optional<ApplicationProperty> oldOneOpt = propertiesService.getPropertyByKey(property.getKey());

		ApplicationProperty oldOne = oldOneOpt.get();
		if(!property.equals(oldOne)) {
			storedProperty = propertiesService.updateProperty(property, oldOne);
			log.info("Property changed!");
		} else {
			throw new ApplicationPropertyNotChangedAPIException("Application property not updated becouse it has not changed.");
		}

		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(storedProperty, "Application property updated"));
	}

}
