package it.eng.negotiation.transformer.to;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class AbstractToTransformerTest {

	protected ObjectMapper mapper = new ObjectMapper(); 
	
	protected abstract JsonNode createJsonNode();
	
	protected String generateUUID() {
		return "urn:uuid:" + UUID.randomUUID();
	}
}
