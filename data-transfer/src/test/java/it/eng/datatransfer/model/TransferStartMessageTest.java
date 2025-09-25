package it.eng.datatransfer.model;

import java.util.Arrays;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
		JsonNode context = result.get(DSpaceConstants.CONTEXT);
		assertNotNull(context);
		if (context.isArray()) {
			ArrayNode arrayNode = (ArrayNode) context;
			assertFalse(arrayNode.isEmpty());
			assertEquals(DSpaceConstants.DSPACE_2025_01_CONTEXT, arrayNode.get(0).asText());
		}
		assertEquals(result.get(DSpaceConstants.TYPE).asText(), transferStartMessage.getType());
		assertEquals(result.get(DSpaceConstants.CONSUMER_PID).asText(), transferStartMessage.getConsumerPid());
		assertEquals(result.get(DSpaceConstants.PROVIDER_PID).asText(), transferStartMessage.getProviderPid());


		JsonNode dataAddress = result.get(DSpaceConstants.DATA_ADDRESS);
		assertNotNull(dataAddress);
		validateDataAddress(dataAddress);
		
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
		assertEquals(transferStartMessage.getConsumerPid(), javaObj.getConsumerPid());
		assertEquals(transferStartMessage.getProviderPid(), javaObj.getProviderPid());
		assertNotNull(javaObj.getDataAddress());

		DataAddress expected = transferStartMessage.getDataAddress();
		assertEquals(expected.getEndpoint(), javaObj.getDataAddress().getEndpoint());
		assertEquals(expected.getEndpointType(), javaObj.getDataAddress().getEndpointType());
		assertNotNull(javaObj.getDataAddress().getEndpointProperties());
		assertEquals(expected.getEndpointProperties().size(), javaObj.getDataAddress().getEndpointProperties().size());

		for (int i = 0; i < expected.getEndpointProperties().size(); i++) {
			EndpointProperty exp = expected.getEndpointProperties().get(i);
			EndpointProperty actual = javaObj.getDataAddress().getEndpointProperties().get(i);
			assertEquals(exp.getName(), actual.getName());
			assertEquals(exp.getValue(), actual.getValue());
		}
	}

	private void validateDataAddress(JsonNode dataAddressForChecking) {
		assertEquals(dataAddressForChecking.get(DSpaceConstants.ENDPOINT_TYPE).asText(), dataAddress.getEndpointType());
		assertEquals(dataAddressForChecking.get(DSpaceConstants.ENDPOINT).asText(), dataAddress.getEndpoint());

		JsonNode properties = dataAddressForChecking.get(DSpaceConstants.ENDPOINT_PROPERTIES);
		assertNotNull(properties);
		if (properties.isArray()) {
			ArrayNode arrayNode = (ArrayNode) properties;
			assertFalse(arrayNode.isEmpty());
			for (int i = 0; i < arrayNode.size(); i++) {
				JsonNode prop = arrayNode.get(i);
				assertEquals(prop.get(DSpaceConstants.NAME).asText(), dataAddress.getEndpointProperties().get(i).getName());
				assertEquals(prop.get(DSpaceConstants.VALUE).asText(), dataAddress.getEndpointProperties().get(i).getValue());
			}
		}
	}
}
