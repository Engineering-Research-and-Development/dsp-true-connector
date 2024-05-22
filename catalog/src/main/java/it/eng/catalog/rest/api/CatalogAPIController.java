package it.eng.catalog.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.exceptions.CatalogNotFoundAPIException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.service.CatalogService;
import lombok.extern.java.Log;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/api/catalog")
@Log
public class CatalogAPIController {

    private final CatalogService catalogService;

    public CatalogAPIController(CatalogService service) {
        super();
        this.catalogService = service;
    }

    @GetMapping(path = "/")
    public ResponseEntity<JsonNode> getCatalog() {
        log.info("Fetching catalog");

        var catalog = catalogService.getCatalog();

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(Serializer.serializePlainJsonNode(catalog));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<JsonNode> getCatalogById(@PathVariable String id) {
        log.info("Fetching catalog with id '" + id + "'");

        Catalog catalog = catalogService.getCatalogById(id).orElseThrow(() -> new CatalogNotFoundAPIException("Catalog with id" + id + " not Found"));

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(Serializer.serializePlainJsonNode(catalog));
    }

    @PostMapping
    public ResponseEntity<JsonNode> createCatalog(@RequestBody String catalog) {
        Catalog c = Serializer.deserializePlain(catalog, Catalog.class);

        log.info("Saving new catalog");

        Catalog storedCatalog = catalogService.saveCatalog(c);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(Serializer.serializePlainJsonNode(storedCatalog));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<String> deleteCatalog(@PathVariable String id) {
        log.info("Deleting catalog with id: " + id);

        catalogService.deleteCatalog(id);
        return ResponseEntity.ok().body("Catalog deleted successfully");
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<JsonNode> updateCatalog(@PathVariable String id, @RequestBody String catalog) {
        Catalog c = Serializer.deserializePlain(catalog, Catalog.class);

        log.info("Updating catalog with id: " + id);

        Catalog updatedCatalog = catalogService.updateCatalog(id, c);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(Serializer.serializePlainJsonNode(updatedCatalog));
    }
}
