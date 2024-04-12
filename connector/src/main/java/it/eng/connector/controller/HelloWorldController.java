package it.eng.connector.controller;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.connector.calculator.AsyncCalculatror;
import lombok.extern.java.Log;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@RestController
@Log
public class HelloWorldController {

	@Autowired
	public AsyncCalculatror asyncCalc;
	private String ID = "@id";
	private String TYPE = "@type";
	String DSPACE_SCHEMA = "https://w3id.org/dspace/v0.8/";
	String DSPACE_TYPE_CONTRACT_REQUEST_MESSAGE = DSPACE_SCHEMA + "ContractRequestMessage";
	String DSPACE_PROPERTY_PROCESS_ID = DSPACE_SCHEMA + "processId";


	@GetMapping(path = "/hello")
	public ResponseEntity<String> helloWorld() throws InterruptedException, ExecutionException {
		log.info("Before aysnc call...");
		Future<String> completableFuture = asyncCalc.calculateAsync();
		log.info("waiting for async to be done....");
		return ResponseEntity.ok().header("foo", "bar").contentType(MediaType.TEXT_PLAIN).body(completableFuture.get());

	}


	@PostMapping(path = "/protocol/negotiations/request", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> helloWorld(@RequestBody JsonNode object,
			@RequestHeader(value = "Authorization", required = false) String authorization)
			throws InterruptedException, ExecutionException {
		log.info("Before aysnc call...");

		JSONObject jo = new JSONObject();
		jo.put(ID, UUID.randomUUID().toString());
		jo.put(TYPE, DSPACE_TYPE_CONTRACT_REQUEST_MESSAGE);
		jo.put(DSPACE_PROPERTY_PROCESS_ID, "requestMessage.getProcessId");
		
		return ResponseEntity.ok().header("foo", "bar").contentType(MediaType.TEXT_PLAIN).body(jo.toString(2));

	}
}
