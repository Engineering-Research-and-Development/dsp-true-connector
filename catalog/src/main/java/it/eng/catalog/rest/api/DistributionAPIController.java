package it.eng.catalog.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.DistributionService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = ApiEndpoints.CATALOG_DISTRIBUTIONS_V1)
@Slf4j
public class DistributionAPIController {

    private final DistributionService distributionService;

    public DistributionAPIController(DistributionService distributionService) {
        this.distributionService = distributionService;
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<GenericApiResponse<JsonNode>> getDistributionById(@PathVariable("id") String id) {
        log.info("Fetching distribution with id: '" + id + "'");
        Distribution distribution = distributionService.getDistributionById(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(distribution), "Fetched distribution"));
    }

    @GetMapping
    public ResponseEntity<GenericApiResponse<JsonNode>> getAllDistributions() {
        log.info("Fetching all distributions");
        var distributions = distributionService.getAllDistributions();

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(distributions), "Fetched all distributions"));
    }

    @PostMapping
    public ResponseEntity<GenericApiResponse<JsonNode>> saveDistribution(@RequestBody String distribution) {
        Distribution ds = CatalogSerializer.deserializePlain(distribution, Distribution.class);

        log.info("Saving new distribution");

        Distribution storedDistribution = distributionService.saveDistribution(ds);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(storedDistribution), "Distribution saved"));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<GenericApiResponse<Void>> deleteDistribution(@PathVariable("id") String id) {
        log.info("Deleting distribution with id: " + id);

        distributionService.deleteDistribution(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(null, "Distribution deleted successfully"));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<GenericApiResponse<JsonNode>> updateDistribution(@PathVariable("id") String id, @RequestBody String distribution) {
        Distribution ds = CatalogSerializer.deserializePlain(distribution, Distribution.class);

        log.info("Updating distribution with id: " + id);

        Distribution updatedDistribution = distributionService.updateDistribution(id, ds);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(updatedDistribution), "Distribution updated"));
    }


}