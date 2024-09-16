package it.eng.datatransfer.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.serializer.Serializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class TransferTerminationMessageTest {

	private TransferTerminationMessage transferTerminationMessage = TransferTerminationMessage.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID).code("stop")
//			.reason(Arrays.asList("You got terminated!"))
			.build();

	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = Serializer.serializePlain(transferTerminationMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CODE));

		TransferTerminationMessage javaObj = Serializer.deserializePlain(result, TransferTerminationMessage.class);
		validateJavaObject(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testPlain_protocol() {
		JsonNode result = Serializer.serializeProtocolJsonNode(transferTerminationMessage);
		assertNotNull(result.get(DSpaceConstants.CONTEXT));
		assertTrue(DSpaceConstants.validateContext(result.get(DSpaceConstants.CONTEXT)));
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CODE).asText());

		TransferTerminationMessage javaObj = Serializer.deserializeProtocol(result, TransferTerminationMessage.class);
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
		JsonNode result = Serializer.serializePlainJsonNode(transferTerminationMessage);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, TransferTerminationMessage.class));
	}

	public void validateJavaObject(TransferTerminationMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getCode());
	}
}
