package it.eng.catalog.rest.api;

import java.net.URL;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.service.ArtifactService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.Serializer;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(path = ApiEndpoints.CATALOG_ARTIFACT_V1)
@Slf4j
public class ArtifactAPIController {

	private final ArtifactService artifactService;
	
	public ArtifactAPIController(ArtifactService artifactService) {
		super();
		this.artifactService = artifactService;
	}

	@GetMapping(path = {"", "/{artifact}"})
	public ResponseEntity<GenericApiResponse<JsonNode>> getArtifacts(@PathVariable(required = false) String artifact) {
		List<Artifact> result = artifactService.getArtifacts(artifact);
		return ResponseEntity.ok(GenericApiResponse.success(Serializer.serializePlainJsonNode(result), "Fetched artifacts"));
	}

	@PostMapping("/upload/{datasetId}")
	public ResponseEntity<GenericApiResponse<JsonNode>> uploadArtifact(
			@RequestPart(value = "file", required = false) MultipartFile file,
			@RequestPart(value = "url", required = false) URL externalURL,
			@PathVariable(required = true) String datasetId) {
		log.info("Uploading artifact");
		Artifact artifact = artifactService.uploadArtifact(file, datasetId, externalURL);
		log.info("Artifact uploaded {} ", artifact.getId());
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(Serializer.serializePlainJsonNode(artifact), "Artifact uploaded successfully"));
	}
	
	@DeleteMapping(path = "/{id}")
    public ResponseEntity<GenericApiResponse<JsonNode>> deleteArtifact(@PathVariable String id) {
        log.info("Deleting artifact with id: " + id);

        artifactService.deleteArtifact(id);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(null, "Artifact deleted successfully"));
    }
}
