package it.eng.catalog.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.service.DatasetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
        path = "/catalog")
@Slf4j
public class CatalogController {

    private final CatalogService catalogService;
    private final DatasetService datasetService;


    public CatalogController(CatalogService catalogService, DatasetService datasetService) {
        super();
        this.catalogService = catalogService;
        this.datasetService = datasetService;

    }

    @PostMapping(path = "/request")
    protected ResponseEntity<JsonNode> getCatalog(@RequestHeader(required = false) String authorization,
                                                  @RequestBody JsonNode jsonBody) {
        log.info("Handling catalog request \n{}", CatalogSerializer.serializeProtocol(jsonBody));
        CatalogSerializer.deserializeProtocol(jsonBody, CatalogRequestMessage.class);
        Catalog catalog = catalogService.getCatalog();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(CatalogSerializer.serializeProtocolJsonNode(catalog));

    }

    @GetMapping(path = "/datasets/{id}")
    public ResponseEntity<JsonNode> getDataset(@RequestHeader(required = false) String authorization,
                                               @PathVariable String id) {

        log.info("Handling dataset request {}", id);
        Dataset dataSet = datasetService.getDatasetById(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(CatalogSerializer.serializeProtocolJsonNode(dataSet));
    }
}
