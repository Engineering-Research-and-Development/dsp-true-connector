package it.eng.tools.rest.api;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;
import it.eng.tools.service.ApplicationPropertiesService;
import lombok.extern.java.Log;

/**
 * Controller for managing application properties; get and update.
 */
@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, 
	path = ApiEndpoints.PROPERTIES_V1)
@Log
public class ApplicationPropertiesAPIController {

	private final ApplicationPropertiesService propertiesService;

	/**
	 * Constructor.
	 * @param service ApplicationPropertiesService
	 */
	public ApplicationPropertiesAPIController(ApplicationPropertiesService service) {
		super();
		this.propertiesService = service;
	}

	/**
	 * GET all properties or by prefix.
	 * @param key_prefix prefix to filter
	 * @return List of properties
	 */
	@GetMapping(path = "/")
	public ResponseEntity<GenericApiResponse<JsonNode>> getProperties(@RequestParam(required = false) String key_prefix) {
		log.info("getProperties()");
		if(key_prefix != null && !key_prefix.isBlank()) log.info(" with key_prefix " + key_prefix);
		List<ApplicationProperty> properties = propertiesService.getProperties(key_prefix);
		String responseText = StringUtils.isBlank(key_prefix) ? "All Application properties" : "Application properties with prefix";
		GenericApiResponse<JsonNode> genericApiResponse = 
				GenericApiResponse.success(ToolsSerializer.serializePlainJsonNode(properties), responseText);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(genericApiResponse);
	}

	/**
	 * Update properties.
	 * @param properties for updating
	 * @return GenericApiResponse
	 */
	@PutMapping(path = "/")
	public ResponseEntity<GenericApiResponse<JsonNode>> modifyProperty(@RequestBody List<ApplicationProperty> properties) {
		log.info("modifyProperties(...) ");

		List<ApplicationProperty> allPropertiesAfterUpdate = propertiesService.updateProperties(properties);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				// return all properties after update
				.body(GenericApiResponse.success(ToolsSerializer.serializePlainJsonNode(allPropertiesAfterUpdate), "Application property updated"));
	}
}
