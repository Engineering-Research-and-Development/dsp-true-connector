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
import it.eng.catalog.model.Serializer;
import it.eng.catalog.service.CatalogService;
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
                                                @RequestBody JsonNode jsonBody) {
    	log.info("Handling catalog request");
        verifyAuthorization(authorization);
        Serializer.deserializeProtocol(jsonBody, CatalogRequestMessage.class);
        Catalog catalog = catalogService.getCatalog();
        return ResponseEntity.ok()
        		.contentType(MediaType.APPLICATION_JSON)
                .body(Serializer.serializeProtocol(catalog));

    }

    @GetMapping(path = "datasets/{id}")
    public ResponseEntity<String> getDataset(@RequestHeader(required = false) String authorization,
    		@PathVariable String id, @RequestBody JsonNode jsonBody) {
        log.info("Preparing dataset");
        verifyAuthorization(authorization);
        Serializer.deserializeProtocol(jsonBody, DatasetRequestMessage.class);
        Dataset dataSet = catalogService.getDataSetById(id);
        return ResponseEntity.ok()
        		.contentType(MediaType.APPLICATION_JSON)
                .body(Serializer.serializeProtocol(dataSet));
    }

    private void verifyAuthorization(String authorization) {
        // TODO Auto-generated method stub
    }

}
