package it.eng.catalog.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.service.DatasetService;
import it.eng.tools.rest.api.TenantAwareProtocolController;
import it.eng.tools.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Protocol controller that handles DSP catalog and dataset requests scoped to a specific tenant.
 * Each request URL carries a {@code {tenantId}} path segment that is resolved and validated
 * before delegating to the catalog service layer.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/{tenantId}/catalog")
@Slf4j
public class CatalogController extends TenantAwareProtocolController {

    private final CatalogService catalogService;
    private final DatasetService datasetService;

    /**
     * Constructs the controller with its catalog, dataset and tenant service dependencies.
     *
     * @param catalogService the catalog service
     * @param datasetService the dataset service
     * @param tenantService  the tenant service used for tenant resolution
     */
    public CatalogController(CatalogService catalogService, DatasetService datasetService, TenantService tenantService) {
        super(tenantService);
        this.catalogService = catalogService;
        this.datasetService = datasetService;
    }

    /**
     * Handles a DSP catalog request for the given tenant.
     *
     * @param tenantId  the tenant identifier extracted from the request path
     * @param authorization optional authorization header
     * @param jsonBody  the serialized {@link CatalogRequestMessage}
     * @return the serialized tenant-scoped catalog
     */
    @PostMapping(path = "/request")
    protected ResponseEntity<JsonNode> getCatalog(@PathVariable String tenantId,
                                                  @RequestHeader(required = false) String authorization,
                                                  @RequestBody JsonNode jsonBody) {
        log.info("Handling catalog request for tenant '{}'\n{}", tenantId, CatalogSerializer.serializeProtocol(jsonBody));
        resolveTenant(tenantId);
        CatalogSerializer.deserializeProtocol(jsonBody, CatalogRequestMessage.class);
        Catalog catalog = catalogService.getCatalog();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(CatalogSerializer.serializeProtocolJsonNode(catalog));
    }

    /**
     * Handles a DSP dataset request for the given tenant and dataset ID.
     *
     * @param tenantId      the tenant identifier extracted from the request path
     * @param authorization optional authorization header
     * @param id            the dataset identifier
     * @return the serialized dataset
     */
    @GetMapping(path = "/datasets/{id}")
    public ResponseEntity<JsonNode> getDataset(@PathVariable String tenantId,
                                               @RequestHeader(required = false) String authorization,
                                               @PathVariable String id) {
        log.info("Handling dataset request for tenant '{}' dataset '{}'", tenantId, id);
        resolveTenant(tenantId);
        Dataset dataSet = datasetService.getDatasetById(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(CatalogSerializer.serializeProtocolJsonNode(dataSet));
    }
}
