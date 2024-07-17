
package it.eng.catalog.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.service.DistributionService;
import lombok.extern.java.Log;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/api/v1/distribution")
@Log
public class DistributionAPIController {

    private final DistributionService distributionService;

    public DistributionAPIController(DistributionService distributionService) {
        this.distributionService = distributionService;
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<JsonNode> getDistributionById(@PathVariable String id) {
        log.info("Fetching distribution with id: '" + id + "'");
        Distribution distribution = distributionService.getDistributionById(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializePlainJsonNode(distribution));
    }

    @GetMapping
    public ResponseEntity<JsonNode> getAllDistributions() {
        log.info("Fetching all distributions");
        var distributions = distributionService.getAllDistributions();

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializePlainJsonNode(distributions));
    }

    @PostMapping
    public ResponseEntity<JsonNode> saveDistribution(@RequestBody String distribution) {
        Distribution ds = Serializer.deserializePlain(distribution, Distribution.class);

        log.info("Saving new distribution");

        Distribution storedDistribution = distributionService.saveDistribution(ds);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializePlainJsonNode(storedDistribution));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<String> deleteDistribution(@PathVariable String id) {
        log.info("Deleting distribution with id: " + id);

        distributionService.deleteDistribution(id);

        return ResponseEntity.ok().body("Distribution deleted successfully");
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<JsonNode> updateDistribution(@PathVariable String id, @RequestBody String distribution) {
        Distribution ds = Serializer.deserializePlain(distribution, Distribution.class);

        log.info("Updating distribution with id: " + id);

        Distribution updatedDistribution = distributionService.updateDistribution(id, ds);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializePlainJsonNode(updatedDistribution));
    }


}