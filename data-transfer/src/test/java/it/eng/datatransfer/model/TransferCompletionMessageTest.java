package it.eng.datatransfer.model;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

import static org.junit.jupiter.api.Assertions.*;

public class TransferCompletionMessageTest {

	private TransferCompletionMessage transferCompletionMessage = TransferCompletionMessage.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.build();

	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = TransferSerializer.serializePlain(transferCompletionMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));

		TransferCompletionMessage javaObj = TransferSerializer.deserializePlain(result, TransferCompletionMessage.class);
		validateJavaObject(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testPlain_protocol() {
		JsonNode result = TransferSerializer.serializeProtocolJsonNode(transferCompletionMessage);
		JsonNode context = result.get(DSpaceConstants.CONTEXT);
		assertNotNull(context);
		if (context.isArray()) {
			ArrayNode arrayNode = (ArrayNode) context;
			assertFalse(arrayNode.isEmpty());
			assertEquals(DSpaceConstants.DSPACE_2025_01_CONTEXT, arrayNode.get(0).asText());
		}
		assertEquals(result.get(DSpaceConstants.TYPE).asText(), transferCompletionMessage.getType());
		assertEquals(result.get(DSpaceConstants.CONSUMER_PID).asText(), transferCompletionMessage.getConsumerPid());
		assertEquals(result.get(DSpaceConstants.PROVIDER_PID).asText(), transferCompletionMessage.getProviderPid());

		TransferCompletionMessage javaObj = TransferSerializer.deserializeProtocol(result, TransferCompletionMessage.class);
		validateJavaObject(javaObj);
	}

	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class,
				() -> TransferCompletionMessage.Builder.newInstance()
				.build());
	}

	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = TransferSerializer.serializePlainJsonNode(transferCompletionMessage);
		assertThrows(ValidationException.class, () -> TransferSerializer.deserializeProtocol(result, TransferCompletionMessage.class));
	}

	private void validateJavaObject(TransferCompletionMessage javaObj) {
		assertNotNull(javaObj);
		assertEquals(transferCompletionMessage.getConsumerPid(), javaObj.getConsumerPid());
		assertEquals(transferCompletionMessage.getProviderPid(), javaObj.getProviderPid());

	}
}
