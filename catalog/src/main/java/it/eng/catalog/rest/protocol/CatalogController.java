package it.eng.catalog.rest.protocol;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.DatasetRequestMessage;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.service.DatasetService;
import lombok.extern.slf4j.Slf4j;

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
        log.info("Handling catalog request");
        //TODO don't show datasets which don't have atifacts
        CatalogSerializer.deserializeProtocol(jsonBody, CatalogRequestMessage.class);
        Catalog catalog = catalogService.getCatalog();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(CatalogSerializer.serializeProtocolJsonNode(catalog));

    }

    @GetMapping(path = "/datasets/{id}")
    public ResponseEntity<JsonNode> getDataset(@RequestHeader(required = false) String authorization,
                                               @PathVariable String id, @RequestBody JsonNode jsonBody) {
        log.info("Preparing dataset");
        CatalogSerializer.deserializeProtocol(jsonBody, DatasetRequestMessage.class);
        Dataset dataSet = datasetService.getDatasetById(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(CatalogSerializer.serializeProtocolJsonNode(dataSet));
    }
}
