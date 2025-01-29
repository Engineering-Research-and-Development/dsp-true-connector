package it.eng.catalog.rest.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.CatalogService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = ApiEndpoints.CATALOG_CATALOGS_V1)
@Slf4j
public class CatalogAPIController {

    private final CatalogService catalogService;

    public CatalogAPIController(CatalogService service) {
        super();
        this.catalogService = service;
    }

    @GetMapping
    public ResponseEntity<GenericApiResponse<JsonNode>> getCatalog() {
        log.info("Fetching catalog");

        Catalog catalog = catalogService.getCatalogForApi();

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(catalog), "Fetched catalog"));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<GenericApiResponse<JsonNode>> getCatalogById(@PathVariable String id) {
        log.info("Fetching catalog with id '" + id + "'");

        Catalog catalog = catalogService.getCatalogById(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(catalog), "Fetched catalog"));
    }

    @PostMapping
    public ResponseEntity<GenericApiResponse<JsonNode>> createCatalog(@RequestBody String catalog) {
        Catalog c = CatalogSerializer.deserializePlain(catalog, Catalog.class);

        log.info("Saving new catalog");

        Catalog storedCatalog = catalogService.saveCatalog(c);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(storedCatalog), "Catalog saved"));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<GenericApiResponse<Void>> deleteCatalog(@PathVariable String id) {
        log.info("Deleting catalog with id: " + id);

        catalogService.deleteCatalog(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        		.body(GenericApiResponse.success(null, "Catalog deleted successfully"));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<GenericApiResponse<JsonNode>> updateCatalog(@PathVariable String id, @RequestBody String catalog) {
        Catalog c = CatalogSerializer.deserializePlain(catalog, Catalog.class);

        log.info("Updating catalog with id: " + id);
        
        Catalog updatedCatalog = catalogService.updateCatalog(id, c);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(updatedCatalog), "Catalog updated"));
    }
}
