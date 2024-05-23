package it.eng.catalog.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.service.DatasetService;
import lombok.extern.java.Log;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/api/dataset")
@Log
public class DatasetAPIController {

    private final DatasetService datasetService;

    public DatasetAPIController(DatasetService datasetService) {
        super();
        this.datasetService = datasetService;
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<JsonNode> getDatasetById(@PathVariable String id) {
        log.info("Fetching dataset with id: '" + id + "'");
        Dataset dataset = datasetService.getDatasetById(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializePlainJsonNode(dataset));
    }

    @GetMapping
    public ResponseEntity<JsonNode> getAllDatasets() {
        log.info("Fetching all datasets");
        List<Dataset> datasets = datasetService.getAllDatasets();

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializePlainJsonNode(datasets));
    }

    @PostMapping
    public ResponseEntity<JsonNode> saveDataset(@RequestBody String dataset) {
        Dataset ds = Serializer.deserializePlain(dataset, Dataset.class);

        log.info("Saving new dataset");

        Dataset storedDataset = datasetService.saveDataset(ds);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializePlainJsonNode(storedDataset));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<String> deleteDataset(@PathVariable String id) {
        log.info("Deleting dataset with id: " + id);

        datasetService.deleteDataset(id);

        return ResponseEntity.ok().body("Dataset deleted successfully");
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<JsonNode> updateDataset(@PathVariable String id, @RequestBody String dataset) {
        Dataset ds = Serializer.deserializePlain(dataset, Dataset.class);

        log.info("Updating dataset with id: " + id);

        Dataset storedDataset = datasetService.updateDataset(id, ds);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializePlainJsonNode(storedDataset));
    }
}

