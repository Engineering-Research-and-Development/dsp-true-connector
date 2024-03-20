package it.eng.catalog.rest.protocol;

import java.util.Arrays;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.catalog.model.CatalogError;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.Reason;
import it.eng.catalog.model.Serializer;
import it.eng.catalog.service.CatalogService;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.exception.ResourceNotFoundException;
import it.eng.tools.model.DSpaceConstants;
import lombok.extern.java.Log;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, 
	path = "/catalog")
@Log
public class CatalogProtocolController {

	private final CatalogService catalogService;
	
	public CatalogProtocolController(CatalogService catalogService) {
		super();
		this.catalogService = catalogService;
	}

	@PostMapping(path = "/request")
	protected ResponseEntity<String> getCatalog(@RequestHeader(required = false) String authorization, 
			@RequestBody JsonNode object) {
		log.info("Handling get catalog");
		verifyAuthorization(authorization);
		try {
			verifyRequestMessage(object);
			var catalogRequestMessage = Serializer.deserializePlain(object.toString(), CatalogRequestMessage.class);
//			var catalogRequestMessage = jsonToCatalogRequestMessageTransform.transform(object);
			//TODO review how to handle filter, there should be ONE logical catalog 
			var cat = catalogService.findAll();
//			JsonNode jsonNode = jsonFromCatalogTransform.transform(cat);
			
			return ResponseEntity.ok().header("foo", "bar")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Serializer.serializeProtocol(cat));
		} catch (BadRequestException | ResourceNotFoundException e) {
			CatalogError catalogError = CatalogError.Builder.newInstance().reason(Arrays.asList(
					Reason.Builder.newInstance()
					.language("en")
					.value(e.getLocalizedMessage())
					.build()
					)).build();
			return ResponseEntity.ok().header("foo", "bar")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Serializer.serializeProtocol(catalogError));
		}
	}
	
	private void verifyRequestMessage(JsonNode jsonNode) {
		if(ObjectUtils.isEmpty(jsonNode.get(DSpaceConstants.TYPE))) {
			throw new BadRequestException("Request message not present");
		}
		if(!jsonNode.get(DSpaceConstants.TYPE).asText().equals(DSpaceConstants.DSPACE + CatalogRequestMessage.class.getSimpleName())) {
			throw new BadRequestException("Not valid request message");
		}
	}

	private void verifyAuthorization(String authorization) {
		// TODO Auto-generated method stub
	}

	@GetMapping(path = "datasets/{id}")
	public ResponseEntity<String> getDataset(@PathVariable String id) {
		log.info("Preparing dataset");
		return ResponseEntity.ok().header("foo", "bar")
				.contentType(MediaType.APPLICATION_JSON)
				.body("ABC");
	}
	
	private String getCatalogIdFromFilter(String filter) {
		ObjectMapper mapper = new ObjectMapper();
	    JsonNode actualObj;
		try {
			actualObj = mapper.readTree(filter);
			return actualObj.get("catalogId").asText();
		} catch (JsonProcessingException e) {
			log.info("No catalog in filter");
		}
		return null;
	}

}
