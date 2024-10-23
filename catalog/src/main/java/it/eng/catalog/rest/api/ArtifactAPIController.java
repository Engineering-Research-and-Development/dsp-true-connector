package it.eng.catalog.rest.api;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import it.eng.catalog.model.Artifact;
import it.eng.catalog.service.ArtifactService;
import it.eng.tools.controller.ApiEndpoints;
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

	@PostMapping("/upload/{datasetId}")
	public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file,
			@PathVariable(required = true) String datasetId) {
		ObjectId objId = artifactService.storeFile(file, datasetId);
		log.info(objId.toHexString());
		return ResponseEntity.ok("File uploaded " + objId.toHexString());
	}
	
	@GetMapping(path = {"", "/{artifactId}"})
	public ResponseEntity<GenericApiResponse<List<Artifact>>> listArtifacts(@PathVariable(required = false) String artifactId) {
		List<Artifact> result = artifactService.listArtifacts(artifactId);
		return ResponseEntity.ok(GenericApiResponse.success(result, "Stored artifacts"));
	}
}
