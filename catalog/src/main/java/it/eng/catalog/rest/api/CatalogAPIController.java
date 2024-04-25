package it.eng.catalog.rest.api;

import it.eng.catalog.exceptions.CatalogNotFoundAPIException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Serializer;
import it.eng.catalog.service.CatalogService;
import lombok.extern.java.Log;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/api/catalog")
@Log
public class CatalogAPIController {

    private final ApplicationEventPublisher applicationEventPublisher;

    private final CatalogService catalogService;

    public CatalogAPIController(CatalogService service, ApplicationEventPublisher applicationEventPublisher) {
        super();
        this.catalogService = service;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @GetMapping(path = "/")
    public ResponseEntity<String> getCatalog() {
        var catalog = catalogService.getCatalog();

        applicationEventPublisher.publishEvent(catalog);
        return ResponseEntity.ok().header("id", "bar").contentType(MediaType.APPLICATION_JSON)
                .body(Serializer.serializePlain(catalog));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<String> getCatalogById(@PathVariable String id) {

        log.info("Fetching catalog with id '" + id + "'");
        Catalog catalog = catalogService.getCatalogById(id).orElseThrow(() -> new CatalogNotFoundAPIException("Catalog with id" + id + " not Found"));

        applicationEventPublisher.publishEvent(catalog);
        return ResponseEntity.ok().header("id", "bar").contentType(MediaType.APPLICATION_JSON)
                .body(Serializer.serializePlain(catalog));

    }

    @PostMapping
    public ResponseEntity<String> createCatalog(@RequestBody Catalog catalog) {
        catalogService.saveCatalog(catalog);
        return ResponseEntity.ok().body("Catalog created successfully");
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<String> deleteCatalog(@PathVariable String id) {
        catalogService.deleteCatalog(id);
        return ResponseEntity.ok().body("Catalog deleted successfully");
    }
}
