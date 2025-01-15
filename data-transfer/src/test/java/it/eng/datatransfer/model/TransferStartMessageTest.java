package it.eng.datatransfer.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class TransferStartMessageTest {

	private DataAddress dataAddress = DataAddress.Builder.newInstance()
			.endpoint(ModelUtil.ENDPOINT)
			.endpointType(ModelUtil.ENDPOINT_TYPE)
			.endpointProperties(Arrays.asList(
					EndpointProperty.Builder.newInstance().name("username").value("John").build(),
					EndpointProperty.Builder.newInstance().name("password").value("encodedPassword").build())
				)
			.build();
	
	private TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.dataAddress(dataAddress)
			.build();
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = TransferSerializer.serializePlain(transferStartMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.DATA_ADDRESS));
		
		TransferStartMessage javaObj = TransferSerializer.deserializePlain(result, TransferStartMessage.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = TransferSerializer.serializeProtocolJsonNode(transferStartMessage);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		JsonNode dataAddres = result.get(DSpaceConstants.DSPACE_DATA_ADDRESS);
		assertNotNull(dataAddres);
		validateDataAddress(dataAddres);
		
		TransferStartMessage javaObj = TransferSerializer.deserializeProtocol(result, TransferStartMessage.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class,
				() -> TransferStartMessage.Builder.newInstance()
				.build());
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = TransferSerializer.serializePlainJsonNode(transferStartMessage);
		assertThrows(ValidationException.class, () -> TransferSerializer.deserializeProtocol(result, TransferStartMessage.class));
	}

	private void validateJavaObject(TransferStartMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getDataAddress());
		
		assertNotNull(javaObj.getDataAddress().getEndpoint());
		assertNotNull(javaObj.getDataAddress().getEndpointType());
		assertNotNull(javaObj.getDataAddress().getEndpointProperties());

		assertNotNull(javaObj.getDataAddress().getEndpointProperties().get(0).getName());
		assertNotNull(javaObj.getDataAddress().getEndpointProperties().get(0).getValue());
		assertNotNull(javaObj.getDataAddress().getEndpointProperties().get(1).getName());
		assertNotNull(javaObj.getDataAddress().getEndpointProperties().get(1).getValue());
		
	}
	
	private void validateDataAddress(JsonNode dataAddress) {
		assertNotNull(dataAddress.get(DSpaceConstants.DSPACE_ENDPOINT_TYPE).asText());
		assertNotNull(dataAddress.get(DSpaceConstants.DSPACE_ENDPOINT).asText());
		assertNotNull(dataAddress.get(DSpaceConstants.DSPACE_ENDPOINT_PROPERTIES));
	}
}
