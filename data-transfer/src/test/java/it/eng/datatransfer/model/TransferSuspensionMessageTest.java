package it.eng.datatransfer.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class TransferSuspensionMessageTest {

	private TransferSuspensionMessage transferSuspensionMessage = TransferSuspensionMessage.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.code("pause")
//			.reason(Arrays.asList("I got tierd"))
			.build();
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = TransferSerializer.serializePlain(transferSuspensionMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CODE));
		
		TransferSuspensionMessage javaObj = TransferSerializer.deserializePlain(result, TransferSuspensionMessage.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testPlain_protocol() {
		JsonNode result = TransferSerializer.serializeProtocolJsonNode(transferSuspensionMessage);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CODE).asText());
		
		TransferSuspensionMessage javaObj = TransferSerializer.deserializeProtocol(result, TransferSuspensionMessage.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class,
				() -> TransferSuspensionMessage.Builder.newInstance()
				.build());
	}

	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = TransferSerializer.serializePlainJsonNode(transferSuspensionMessage);
		assertThrows(ValidationException.class, () -> TransferSerializer.deserializeProtocol(result, TransferSuspensionMessage.class));
	}
	
	private void validateJavaObject(TransferSuspensionMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getCode());
	}
}
