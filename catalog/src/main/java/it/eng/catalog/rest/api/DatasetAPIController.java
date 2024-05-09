package it.eng.catalog.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Serializer;
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
        log.info("Fetching dataset with id '" + id + "'");
        Dataset dataset = datasetService.getDataSetById(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializePlainJsonNode(dataset));
    }

    @GetMapping
    public ResponseEntity<JsonNode> getAllDatasets() {
        log.info("Fetching all datasets");
        List<Dataset> datasets = datasetService.getAllDataSets();

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Serializer.serializePlainJsonNode(datasets));
    }

    @PostMapping
    public ResponseEntity<String> saveDataset(@RequestBody String dataset) {
        Dataset ds = Serializer.deserializePlain(dataset, Dataset.class);

        datasetService.saveDataSet(ds);
        return ResponseEntity.ok().body("Dataset created successfully");
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<String> deleteDataset(@PathVariable String id) {
        datasetService.deleteDataSet(id);
        return ResponseEntity.ok().body("Dataset deleted successfully");
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<String> updateDataset(@PathVariable String id, @RequestBody String dataset) {
        Dataset ds = Serializer.deserializePlain(dataset, Dataset.class);
        datasetService.updateDataSet(id, ds);
        return ResponseEntity.ok().body("Dataset updated successfully");
    }

}

