package it.eng.catalog.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.*;
import it.eng.catalog.service.CatalogService;
import lombok.extern.java.Log;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
        path = "/catalog")
@Log
public class CatalogController {

    private final CatalogService catalogService;


    public CatalogController(CatalogService catalogService) {
        super();
        this.catalogService = catalogService;

    }

    @PostMapping(path = "/request")
    protected ResponseEntity<JsonNode> getCatalog(@RequestHeader(required = false) String authorization,
                                                  @RequestBody JsonNode jsonBody) {
        log.info("Handling catalog request");
        Serializer.deserializeProtocol(jsonBody, CatalogRequestMessage.class);
        Catalog catalog = catalogService.getCatalog();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Serializer.serializeProtocolJsonNode(catalog));

    }

    @GetMapping(path = "datasets/{id}")
    public ResponseEntity<JsonNode> getDataset(@RequestHeader(required = false) String authorization,
                                               @PathVariable String id, @RequestBody JsonNode jsonBody) {
        log.info("Preparing dataset");
        Serializer.deserializeProtocol(jsonBody, DatasetRequestMessage.class);
        Dataset dataSet = catalogService.getDataSetById(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Serializer.serializeProtocolJsonNode(dataSet));
    }
}
