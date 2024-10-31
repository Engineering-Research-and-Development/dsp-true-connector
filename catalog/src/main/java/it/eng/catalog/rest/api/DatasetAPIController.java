package it.eng.catalog.rest.api;

import java.util.Collection;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.model.Dataset;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.service.DatasetService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = ApiEndpoints.CATALOG_DATASETS_V1)
@Slf4j
public class DatasetAPIController {

    private final DatasetService datasetService;

    public DatasetAPIController(DatasetService datasetService) {
        super();
        this.datasetService = datasetService;
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<GenericApiResponse<JsonNode>> getDatasetById(@PathVariable String id) {
        log.info("Fetching dataset with id: '" + id + "'");
        Dataset dataset = datasetService.getDatasetByIdForApi(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(Serializer.serializePlainJsonNode(dataset), "Fetched dataset"));
    }
    
    /**
     * Used for fetching all dct:formats for a Dataset </br>
     * Generally used for creating Transfer Processes with INITIALIZED state
     * 
     * @param id id of Dataset
     * @return List of formats
     */
    @GetMapping(path = "/{id}/formats")
    public ResponseEntity<GenericApiResponse<List<String>>> getFormatsFromDataset(@PathVariable String id) {
        log.info("Fetching formats from dataset with id: '" + id + "'");
        List<String> formats = datasetService.getFormatsFromDataset(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(formats, "Fetched formats"));
    }
    
    /**
     * Used for fetching the fileId from a Dataset
     * 
     * @param id id of Dataset
     * @return file id
     */
    @GetMapping(path = "/{id}/fileid")
    public ResponseEntity<GenericApiResponse<String>> getFileIdFromDataset(@PathVariable String id) {
        log.info("Fetching fileId from dataset with id: '" + id + "'");
        String fileId = datasetService.getFileIdFromDataset(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(fileId, "Fetched file id"));
    }

    @GetMapping
    public ResponseEntity<GenericApiResponse<JsonNode>> getAllDatasets() {
        log.info("Fetching all datasets");
        Collection<Dataset> datasets = datasetService.getAllDatasets();

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(Serializer.serializePlainJsonNode(datasets), "Fetched all datasets"));
    }

    @PostMapping
    public ResponseEntity<GenericApiResponse<JsonNode>> saveDataset(@RequestBody String dataset) {
        Dataset ds = Serializer.deserializePlain(dataset, Dataset.class);

        log.info("Saving new dataset");

        Dataset storedDataset = datasetService.saveDataset(ds);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(Serializer.serializePlainJsonNode(storedDataset), "Saved dataset"));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<GenericApiResponse<Object>> deleteDataset(@PathVariable String id) {
        log.info("Deleting dataset with id: " + id);

        datasetService.deleteDataset(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(null, "Dataset deleted successfully"));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<GenericApiResponse<JsonNode>> updateDataset(@PathVariable String id, @RequestBody String dataset) {
        Dataset ds = Serializer.deserializePlain(dataset, Dataset.class);

        log.info("Updating dataset with id: " + id);

        Dataset storedDataset = datasetService.updateDataset(id, ds);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(Serializer.serializePlainJsonNode(storedDataset), "Dataset updated"));
    }
}

