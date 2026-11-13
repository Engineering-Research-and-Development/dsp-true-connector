package it.eng.catalog.rest.internal;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Hidden;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.DatasetService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal dataset lookups for the connector's own loopback calls made during a transfer.
 *
 * <p>Served under {@code /internal/**}, which accepts only an internal-call token from this process sent
 * from loopback, and is not part of the published OpenAPI documentation. The methods call the same
 * {@link DatasetService} methods and return the same response shapes as their {@code /api/v1/datasets}
 * counterparts, which stay in place for the UI.
 */
@Hidden
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = ApiEndpoints.INTERNAL_DATASETS_V1)
@Slf4j
public class InternalDatasetController {

    private final DatasetService datasetService;

    /**
     * Creates the controller.
     *
     * @param datasetService the dataset service
     */
    public InternalDatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    /**
     * Returns the {@code dct:format} values of a dataset's distributions.
     *
     * @param id the dataset ID
     * @return the formats
     */
    @GetMapping(path = "/{id}/formats")
    public ResponseEntity<GenericApiResponse<List<String>>> getFormatsFromDataset(@PathVariable String id) {
        log.info("Fetching formats from dataset with id: '{}'", id);
        List<String> formats = datasetService.getFormatsFromDataset(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(formats, "Fetched formats"));
    }

    /**
     * Returns the artifact of a dataset.
     *
     * @param id the dataset ID
     * @return the artifact, as plain JSON
     */
    @GetMapping(path = "/{id}/artifact")
    public ResponseEntity<GenericApiResponse<JsonNode>> getArtifactFromDataset(@PathVariable String id) {
        log.info("Fetching artifact from dataset with id: '{}'", id);
        Artifact artifact = datasetService.getArtifactFromDataset(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(CatalogSerializer.serializePlainJsonNode(artifact), "Fetched artifact"));
    }
}
