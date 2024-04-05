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

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.CatalogError;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.DatasetRequestMessage;
import it.eng.catalog.model.Reason;
import it.eng.catalog.model.Serializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.service.DatasetService;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.exception.ResourceNotFoundException;
import it.eng.tools.model.DSpaceConstants;
import lombok.extern.java.Log;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/catalog")
@Log
public class CatalogProtocolController {

    private final CatalogService catalogService;
	
	private final DatasetService datasetService;

	public CatalogProtocolController(CatalogService catalogService, DatasetService datasetService) {
		super();
		this.catalogService = catalogService;
		this.datasetService = datasetService;
	}
	
	/**
	 * Endpoint for handling Catalog requests
	 * 
	 * @param authorization - Authorization header
	 * @param body          - CatalogRequestMessage
	 * @return - Catalog or CatalogError
	 */
	@PostMapping(path = "/request")
	protected ResponseEntity<String> getCatalog(@RequestHeader(required = false) String authorization,
			@RequestBody JsonNode body) {
		log.info("Handling catalog request");
		verifyAuthorization(authorization);
		try {
			CatalogRequestMessage catalogRequestMessage = verifyCatalogRequestMessage(body);
			Catalog catalog = catalogService.findByFilter(catalogRequestMessage.getFilter());

			// @formatter:off
			return ResponseEntity.ok()
					.header("foo", "bar")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Serializer.serializeProtocol(catalog));
			// @formatter:on
		} catch (BadRequestException | ResourceNotFoundException e) {
			// @formatter:off
			CatalogError catalogError = CatalogError.Builder.newInstance()
					.reason(Arrays.asList(Reason.Builder.newInstance()
							.language("en")
							.value(e.getLocalizedMessage())
							.build()))
					.build();
			// @formatter:on
			// @formatter:off
			return ResponseEntity.ok()
					.header("foo", "bar")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Serializer.serializeProtocol(catalogError));
			// @formatter:on
		}
	}

	/**
	 * Endpoint for handling Dataset requests
	 * 
	 * @param authorization - Authorization header
	 * @param id            - id of the Dataset
	 * @param body          - DatasetRequestMessage
	 * @return - Dataset
	 */
	@GetMapping(path = "datasets/{id}")
	public ResponseEntity<String> getDataset(@RequestHeader(required = false) String authorization,
			@PathVariable String id, @RequestBody JsonNode body) {
		log.info("Preparing dataset");
		verifyAuthorization(authorization);
		try {
			verifyDatasetRequestMessage(body);
			Dataset dataset = datasetService.findById(id);

			// @formatter:off
			return ResponseEntity.ok()
					.header("foo", "bar")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Serializer.serializeProtocol(dataset));
			// @formatter:on
		} catch (BadRequestException | ResourceNotFoundException e) {
			// @formatter:off
			CatalogError catalogError = CatalogError.Builder.newInstance()
					.reason(Arrays.asList(Reason.Builder.newInstance()
							.language("en")
							.value(e.getLocalizedMessage())
							.build()))
					.build();
			// @formatter:on
			// @formatter:off
			return ResponseEntity.ok()
					.header("foo", "bar")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Serializer.serializeProtocol(catalogError));
			// @formatter:on
		}
	}

	private DatasetRequestMessage verifyDatasetRequestMessage(JsonNode jsonNode) {
		if (ObjectUtils.isEmpty(jsonNode.get(DSpaceConstants.TYPE))) {
			throw new BadRequestException("Dataset request message not present");
		}
		if (!jsonNode.get(DSpaceConstants.TYPE).asText()
				.equals(DSpaceConstants.DSPACE + DatasetRequestMessage.class.getSimpleName())) {
			throw new BadRequestException("Not valid dataset request message");
		}

		return Serializer.deserializeProtocol(jsonNode.toString(), DatasetRequestMessage.class);
	}

	private CatalogRequestMessage verifyCatalogRequestMessage(JsonNode jsonNode) {
		if (ObjectUtils.isEmpty(jsonNode.get(DSpaceConstants.TYPE))) {
			throw new BadRequestException("Catalog request message not present");
		}
		if (!jsonNode.get(DSpaceConstants.TYPE).asText()
				.equals(DSpaceConstants.DSPACE + CatalogRequestMessage.class.getSimpleName())) {
			throw new BadRequestException("Not valid catalog request message");
		}

		return Serializer.deserializeProtocol(jsonNode.toString(), CatalogRequestMessage.class);
    }

    private void verifyAuthorization(String authorization) {
        // TODO Auto-generated method stub
    }
}
