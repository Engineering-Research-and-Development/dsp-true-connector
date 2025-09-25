package it.eng.datatransfer.model;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransferErrorTest {
	TransferError transferError = TransferError.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.code("Hit fan")
//			.reason(Arrays.asList("We hit something"))
			.build();
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = TransferSerializer.serializePlain(transferError);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		
		TransferError javaObj = TransferSerializer.deserializePlain(result, TransferError.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testPlain_protocol() {
		JsonNode result = TransferSerializer.serializeProtocolJsonNode(transferError);
		JsonNode context = result.get(DSpaceConstants.CONTEXT);
		assertNotNull(context);
		if (context.isArray()) {
			ArrayNode arrayNode = (ArrayNode) context;
			assertFalse(arrayNode.isEmpty());
			assertEquals(DSpaceConstants.DSPACE_2025_01_CONTEXT, arrayNode.get(0).asText());
		}
		assertEquals(result.get(DSpaceConstants.TYPE).asText(), transferError.getType());
		assertEquals(result.get(DSpaceConstants.CONSUMER_PID).asText(), transferError.getConsumerPid());
		assertEquals(result.get(DSpaceConstants.PROVIDER_PID).asText(), transferError.getProviderPid());
		
		TransferError javaObj = TransferSerializer.deserializeProtocol(result, TransferError.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class,
				() -> TransferError.Builder.newInstance()
				.build());
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = TransferSerializer.serializePlainJsonNode(transferError);
		assertThrows(ValidationException.class, () -> TransferSerializer.deserializeProtocol(result, TransferError.class));
	}

	public void validateJavaObject(TransferError javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getCode());
	}
}
