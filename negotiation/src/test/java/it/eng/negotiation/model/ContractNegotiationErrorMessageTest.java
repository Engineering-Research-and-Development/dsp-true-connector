package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.serializer.Serializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractNegotiationErrorMessageTest {

	private ContractNegotiationErrorMessage contractNegotiationErrorMessage = ContractNegotiationErrorMessage.Builder
			.newInstance()
			.consumerPid(MockObjectUtil.CONSUMER_PID)
			.providerPid(MockObjectUtil.PROVIDER_PID)
			.code("Negotiation error code 123")
			.description(Arrays.asList(
					Description.Builder.newInstance().language("en").value("English description text").build(),
					Description.Builder.newInstance().language("it").value("Description text but in Italian").build()
					))
			.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("reason text goes here").build()))
			.build();

	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = Serializer.serializePlain(contractNegotiationErrorMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CODE));
		assertTrue(result.contains(DSpaceConstants.REASON));
		assertTrue(result.contains(DSpaceConstants.DESCRIPTION));
		
		ContractNegotiationErrorMessage javaObj = Serializer.deserializePlain(result, ContractNegotiationErrorMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testPlain_protocol() {
		JsonNode result = Serializer.serializeProtocolJsonNode(contractNegotiationErrorMessage);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CODE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_REASON).get(0));
		assertNotNull(result.get(DSpaceConstants.DCT_DESCRIPTION).get(0));
		
		ContractNegotiationErrorMessage javaObj = Serializer.deserializeProtocol(result, ContractNegotiationErrorMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractNegotiationErrorMessage.Builder.newInstance()
					.build());
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = Serializer.serializePlainJsonNode(contractNegotiationErrorMessage);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, ContractNegotiationErrorMessage.class));
	}

	private void validateJavaObj(ContractNegotiationErrorMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getCode());
		// must be exact one in array
		assertNotNull(javaObj.getReason().get(0));
		// must be 2 descriptions
		assertEquals(2, javaObj.getDescription().size());
	}
}
