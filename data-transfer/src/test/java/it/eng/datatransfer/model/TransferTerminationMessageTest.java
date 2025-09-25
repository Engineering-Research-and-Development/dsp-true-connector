package it.eng.datatransfer.model;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

import static org.junit.jupiter.api.Assertions.*;

public class TransferTerminationMessageTest {

	private TransferTerminationMessage transferTerminationMessage = TransferTerminationMessage.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID).code("stop")
//			.reason(Arrays.asList("You got terminated!"))
			.build();

	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = TransferSerializer.serializePlain(transferTerminationMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CODE));

		TransferTerminationMessage javaObj = TransferSerializer.deserializePlain(result, TransferTerminationMessage.class);
		validateJavaObject(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testPlain_protocol() {
		JsonNode result = TransferSerializer.serializeProtocolJsonNode(transferTerminationMessage);
		JsonNode context = result.get(DSpaceConstants.CONTEXT);
		assertNotNull(context);
		if (context.isArray()) {
			ArrayNode arrayNode = (ArrayNode) context;
			assertFalse(arrayNode.isEmpty());
			assertEquals(DSpaceConstants.DSPACE_2025_01_CONTEXT, arrayNode.get(0).asText());
		}
		assertEquals(result.get(DSpaceConstants.TYPE).asText(), transferTerminationMessage.getType());
		assertEquals(result.get(DSpaceConstants.CONSUMER_PID).asText(), transferTerminationMessage.getConsumerPid());
		assertEquals(result.get(DSpaceConstants.PROVIDER_PID).asText(), transferTerminationMessage.getProviderPid());
		assertEquals(result.get(DSpaceConstants.CODE).asText(), transferTerminationMessage.getCode());

		TransferTerminationMessage javaObj = TransferSerializer.deserializeProtocol(result, TransferTerminationMessage.class);
		validateJavaObject(javaObj);
	}

	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class, () -> TransferTerminationMessage.Builder.newInstance().build());
	}
	
	@Test
	@DisplayName("Missing @context and @ype")
	public void missingContextAndType() {
		JsonNode result = TransferSerializer.serializePlainJsonNode(transferTerminationMessage);
		assertThrows(ValidationException.class, () -> TransferSerializer.deserializeProtocol(result, TransferTerminationMessage.class));
	}

	public void validateJavaObject(TransferTerminationMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getCode());
	}
}
