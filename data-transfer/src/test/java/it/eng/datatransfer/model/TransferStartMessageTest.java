package it.eng.datatransfer.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.serializer.Serializer;
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
		String result = Serializer.serializePlain(transferStartMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.DATA_ADDRESS));
		
		TransferStartMessage javaObj = Serializer.deserializePlain(result, TransferStartMessage.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = Serializer.serializeProtocolJsonNode(transferStartMessage);
		assertNotNull(result.get(DSpaceConstants.CONTEXT));
		assertTrue(DSpaceConstants.validateContext(result.get(DSpaceConstants.CONTEXT)));
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		JsonNode dataAddres = result.get(DSpaceConstants.DSPACE_DATA_ADDRESS);
		assertNotNull(dataAddres);
		validateDataAddress(dataAddres);
		
		TransferStartMessage javaObj = Serializer.deserializeProtocol(result, TransferStartMessage.class);
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
		JsonNode result = Serializer.serializePlainJsonNode(transferStartMessage);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, TransferStartMessage.class));
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
	
	@Test
	public void deseriazlizeEDC() {
		String fromEDC = "{\r\n"
				+ "	\"@id\": \"9bf46bb8-4b22-4775-87bd-a9e5607fd84d\",\r\n"
				+ "	\"@type\": \"dspace:TransferStartMessage\",\r\n"
				+ "	\"dspace:providerPid\": \"0802be4c-ab4b-4173-b081-ae6a00f76304\",\r\n"
				+ "	\"dspace:consumerPid\": \"urn:uuid:f94f263a-6050-4c03-a0ea-67d4893db569\",\r\n"
				+ "	\"dspace:processId\": \"urn:uuid:f94f263a-6050-4c03-a0ea-67d4893db569\",\r\n"
				+ "	\"dspace:dataAddress\": {\r\n"
				+ "		\"@type\": \"dspace:DataAddress\",\r\n"
				+ "		\"dspace:endpointType\": \"https://w3id.org/idsa/v4.1/HTTP\",\r\n"
				+ "		\"dspace:endpointProperties\": [\r\n"
				+ "			{\r\n"
				+ "				\"@type\": \"dspace:EndpointProperty\",\r\n"
				+ "				\"dspace:name\": \"https://w3id.org/edc/v0.0.1/ns/endpoint\",\r\n"
				+ "				\"dspace:value\": \"http://localhost:19291/public\"\r\n"
				+ "			},\r\n"
				+ "			{\r\n"
				+ "				\"@type\": \"dspace:EndpointProperty\",\r\n"
				+ "				\"dspace:name\": \"https://w3id.org/edc/v0.0.1/ns/authType\",\r\n"
				+ "				\"dspace:value\": \"bearer\"\r\n"
				+ "			},\r\n"
				+ "			{\r\n"
				+ "				\"@type\": \"dspace:EndpointProperty\",\r\n"
				+ "				\"dspace:name\": \"https://w3id.org/edc/v0.0.1/ns/endpointType\",\r\n"
				+ "				\"dspace:value\": \"https://w3id.org/idsa/v4.1/HTTP\"\r\n"
				+ "			},\r\n"
				+ "			{\r\n"
				+ "				\"@type\": \"dspace:EndpointProperty\",\r\n"
				+ "				\"dspace:name\": \"https://w3id.org/edc/v0.0.1/ns/authorization\",\r\n"
				+ "				\"dspace:value\": \"eyJraWQiOiJwdWJsaWMta2V5IiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJwcm92aWRlciIsImF1ZCI6ImNvbnN1bWVyIiwic3ViIjoicHJvdmlkZXIiLCJpYXQiOjE3MjcyNzQ5Mjg4NjMsImp0aSI6Ijg1M2E4NjRkLTM4OTEtNDdiNC05ODU4LWZjOGRhMjM5MWNhMCJ9.b-lpmggt65muO3mG5pO6TVZIYEtBytqKCL8bjx224ItH9uOdPN_I_qegPf0WQ6GGaFUyvagAOIy4rhL7PMnYsidwN6A3IuNHbTKme1pN9Si-TeD6acZsHFwmBKLxZVEzm5kxprDGPH9EudKh_DQSgtpEdDuUqd33zJXyn6n92w2u4UKF91L1fedbnRYC-4ICBCbic92W_Pl6OQlemYzr2JM89G1ZlZEMRj3vq191c-zN6UQkbYwroEaWu2xBJwyLPCqfLpnK2C5dDpwzGnDabHLXghcxXjnXzSNVX4AJWNgkpjo9UCYqOZruoR1dgPAJOkAzyY4ETgDgvtwQK62eIg\"\r\n"
				+ "			}\r\n"
				+ "		]\r\n"
				+ "	},\r\n"
				+ "	\"@context\": {\r\n"
				+ "		\"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\",\r\n"
				+ "		\"edc\": \"https://w3id.org/edc/v0.0.1/ns/\",\r\n"
				+ "		\"dcat\": \"http://www.w3.org/ns/dcat#\",\r\n"
				+ "		\"dct\": \"http://purl.org/dc/terms/\",\r\n"
				+ "		\"odrl\": \"http://www.w3.org/ns/odrl/2/\",\r\n"
				+ "		\"dspace\": \"https://w3id.org/dspace/v0.8/\"\r\n"
				+ "	}\r\n"
				+ "}";
		
		TransferStartMessage startMsg = Serializer.deserializeProtocol(fromEDC, TransferStartMessage.class);
		assertNotNull(startMsg);
	}
}
