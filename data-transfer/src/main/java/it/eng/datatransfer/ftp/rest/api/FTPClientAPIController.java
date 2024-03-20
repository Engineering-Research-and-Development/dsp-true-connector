package it.eng.datatransfer.ftp.rest.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.ftp.client.FTPClient;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/api/transfer")
@Slf4j
public class FTPClientAPIController {

	@Autowired
	FTPClient ftpClient;

	@PostMapping
	public ResponseEntity<String> downloadArtifact(@RequestBody JsonNode request) {
		log.info("Received download request");
		boolean isDownloaded = ftpClient.downloadArtifact(request.get("artifact").asText(), request.get("host").asText(), request.get("port").asInt());

		if (isDownloaded) {
			log.info("downloaded artifact " + request.get("artifact").asText());
			return ResponseEntity.ok().body("downloaded artifact " + request.get("artifact").asText());
		}
		log.error("failed to download artifact " + request.get("artifact").asText());
		return ResponseEntity.ok().body("failed to download artifact " + request.get("artifact").asText());
	}

}
