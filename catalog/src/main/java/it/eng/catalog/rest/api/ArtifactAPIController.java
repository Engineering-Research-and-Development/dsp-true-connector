package it.eng.catalog.rest.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.service.ArtifactService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;

@RestController
@RequestMapping(path = ApiEndpoints.CATALOG_ARTIFACT_V1)
public class ArtifactAPIController {

	private final ArtifactService artifactService;
	
	public ArtifactAPIController(ArtifactService artifactService) {
		super();
		this.artifactService = artifactService;
	}

	@GetMapping(path = {"", "/{artifact}"})
	public ResponseEntity<GenericApiResponse<JsonNode>> getArtifacts(@PathVariable(required = false) String artifact) {
		List<Artifact> result = artifactService.getArtifacts(artifact);
		return ResponseEntity.ok(GenericApiResponse.success(ToolsSerializer.serializePlainJsonNode(result), "Fetched artifacts"));
	}
}
