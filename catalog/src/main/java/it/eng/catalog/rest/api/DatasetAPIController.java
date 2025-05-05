package it.eng.catalog.rest.api;

import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.model.Dataset;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.DatasetService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE, 
	path = ApiEndpoints.CATALOG_DATASETS_V1)
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
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(dataset), "Fetched dataset"));
    }
    
    @GetMapping
	public ResponseEntity<GenericApiResponse<JsonNode>> getAllDatasets() {
	    log.info("Fetching all datasets");
	    Collection<Dataset> datasets = datasetService.getAllDatasets();
	
	    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
	            .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(datasets), "Fetched all datasets"));
	}

	/**
     * Used for fetching all dct:formats for a Dataset.<br>
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
     * Used for fetching the artifact from a Dataset.
     * 
     * @param id id of Dataset
     * @return Artifact
     */
    @GetMapping(path = "/{id}/artifact")
    public ResponseEntity<GenericApiResponse<JsonNode>> getArtifactFromDataset(@PathVariable String id) {
        log.info("Fetching artifact from dataset with id: '" + id + "'");
        Artifact artifact = datasetService.getArtifactFromDataset(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(artifact), "Fetched artifact"));
    }

    @PostMapping
    public ResponseEntity<GenericApiResponse<JsonNode>> saveDataset(
    		@RequestPart(value = "file", required = false) MultipartFile file,
			@RequestPart(value = "url", required = false) String externalURL,
			@RequestPart(value = "authorization", required = false) String authorization,
			@RequestPart(value = "dataset", required = true) String dataset) {
        Dataset ds = CatalogSerializer.deserializePlain(dataset, Dataset.class);

        log.info("Saving new dataset");

        Dataset storedDataset = datasetService.saveDataset(ds, file, externalURL, authorization);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(storedDataset), "Saved dataset"));
    }
    
    @PutMapping(path = "/{id}")
	public ResponseEntity<GenericApiResponse<JsonNode>> updateDataset(
			@PathVariable String id,
			@RequestPart(value = "file", required = false) MultipartFile file,
			@RequestPart(value = "url", required = false) String externalURL,
			@RequestPart(value = "authorization", required = false) String authorization,
			@RequestPart(value = "dataset", required = false) String dataset) {
		
	    Dataset ds = null;
	    // if we are updating just the artifact, the dataset can be null
		if (StringUtils.isNotBlank(dataset)) {
			ds = CatalogSerializer.deserializePlain(dataset, Dataset.class);
		}
	
	    log.info("Updating dataset with id: " + id);
	
	    Dataset storedDataset = datasetService.updateDataset(id, ds, file, externalURL, authorization);
	
	    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
	            .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(storedDataset), "Dataset updated"));
	}

	@DeleteMapping(path = "/{id}")
    public ResponseEntity<GenericApiResponse<Void>> deleteDataset(@PathVariable String id) {
        log.info("Deleting dataset with id: " + id);

        datasetService.deleteDataset(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(null, "Dataset deleted successfully"));
    }
}

