package it.eng.tools.rest.api;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.tools.exception.ApplicationPropertyNotChangedAPIException;
import it.eng.tools.exception.ApplicationPropertyNotFoundAPIException;
import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.model.Serializer;
import it.eng.tools.service.ApplicationPropertiesService;
import lombok.extern.java.Log;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/api/connectorProperty")
@Log
public class ApplicationPropertiesAPIController {

	private final ApplicationEventPublisher applicationEventPublisher;

	private final ApplicationPropertiesService propertiesService;

	public ApplicationPropertiesAPIController(ApplicationPropertiesService service, ApplicationEventPublisher applicationEventPublisher) {
		super();
		this.propertiesService = service;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	/*
	 * @Autowired private Environment environment;
	 */

	@GetMapping(path = "/")
	public ResponseEntity<List<JsonNode>> getProperties(@RequestParam(required = false) String key_prefix) {
		log.info("getProperties()");
		if(key_prefix != null && !key_prefix.isBlank()) log.info(" with key_prefix " + key_prefix);
		var properties = propertiesService.getProperties(key_prefix);

		applicationEventPublisher.publishEvent(properties);

		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(Serializer.serializeProtocolListOfJsonNode(properties));
	}

	@GetMapping(path = "/{key}")
	public ResponseEntity<JsonNode> getPropertyByKey(@PathVariable String key) {
		log.info("Fetching property with key " + key);

		ApplicationProperty property = propertiesService.getPropertyByKey(key).orElseThrow(() -> new ApplicationPropertyNotFoundAPIException("Property with key " + key + " not found"));

		applicationEventPublisher.publishEvent(property);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(Serializer.serializeProtocolJsonNode(property));
	}

	@PutMapping(path = "/")
	public ResponseEntity<JsonNode> modifyProperty(@RequestBody ApplicationProperty property) {
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
				.body(Serializer.serializeProtocolJsonNode(storedProperty));
	}

}
