package it.eng.catalog.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.service.DatasetService;
import it.eng.dcp.verifier.service.VerifierService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/catalog")
@Slf4j
public class CatalogController {

    private final CatalogService catalogService;
    private final DatasetService datasetService;

    private final VerifierService verifierService;

    public CatalogController(CatalogService catalogService, DatasetService datasetService, VerifierService verifierService) {
        super();
        this.catalogService = catalogService;
        this.datasetService = datasetService;
        this.verifierService = verifierService;
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

    @PostMapping("/tck/protocol/2025/1/catalog/request")
    public ResponseEntity<JsonNode> tckProtocolCatalogRequest(@RequestHeader(required = true) String authorization,
                                          @RequestBody JsonNode jsonBody) throws IOException {
        log.info("Handling tck protocol request {}", jsonBody);
        log.info("Authorization: {}", authorization);

        verifierService.validateAndQueryHolderPresentations(extractBearerToken(authorization));

        CatalogSerializer.deserializeProtocol(jsonBody, CatalogRequestMessage.class);
        Catalog catalog = catalogService.getCatalog();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(CatalogSerializer.serializeProtocolJsonNode(catalog));
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new SecurityException("Missing or invalid Authorization header");
        }
        return authorizationHeader.substring("Bearer ".length());
    }
}
