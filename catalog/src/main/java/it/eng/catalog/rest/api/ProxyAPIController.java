package it.eng.catalog.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.ProxyAPIService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, 
	path = ApiEndpoints.PROXY_V1)
@Slf4j
public class ProxyAPIController {

	private final ProxyAPIService proxyApiService;

	public ProxyAPIController(ProxyAPIService proxyApiService) {
		super();
		this.proxyApiService = proxyApiService;
	}

	@PostMapping(path = "/datasets/{id}/formats")
	public ResponseEntity<GenericApiResponse<List<String>>> getFormatsFromDataset(@PathVariable("id") String id,
			@RequestBody JsonNode formatsRequest) {
		log.info("Fetching formats from dataset with id: '" + id + "'");
		String forwardTo = formatsRequest.get("Forward-To").asText();
		List<String> formats = proxyApiService.getFormatsFromDataset(id, forwardTo);

		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(formats, "Fetched formats"));
	}
	
	@PostMapping(path = "/catalogs")
	public ResponseEntity<GenericApiResponse<JsonNode>> getCatalog(@RequestBody JsonNode catalogRequest) {
		log.info("Fetching proxy catalog");
		String forwardTo = catalogRequest.get("Forward-To").asText();
		Catalog catalog = proxyApiService.getCatalog(forwardTo);

		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(catalog), "Fetched catalog"));
	}
}
