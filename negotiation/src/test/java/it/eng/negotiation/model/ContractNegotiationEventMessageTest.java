package it.eng.negotiation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.serializer.Serializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractNegotiationEventMessageTest {

	private ContractNegotiationEventMessage contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
			.consumerPid(MockObjectUtil.CONSUMER_PID)
			.providerPid(MockObjectUtil.PROVIDER_PID)
			.eventType(ContractNegotiationEventType.ACCEPTED)
			.build();
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = Serializer.serializePlain(contractNegotiationEventMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.EVENT_TYPE));
		
		ContractNegotiationEventMessage javaObj = Serializer.deserializePlain(result, ContractNegotiationEventMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = Serializer.serializeProtocolJsonNode(contractNegotiationEventMessage);
		assertNotNull(result.get(DSpaceConstants.CONTEXT));
		assertTrue(DSpaceConstants.validateContext(result.get(DSpaceConstants.CONTEXT)));
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_EVENT_TYPE).asText());
		
		ContractNegotiationEventMessage javaObj = Serializer.deserializeProtocol(result, ContractNegotiationEventMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractNegotiationEventMessage.Builder.newInstance()
					.build());
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = Serializer.serializePlainJsonNode(contractNegotiationEventMessage);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, ContractNegotiationEventMessage.class));
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		String ss = Serializer.serializePlain(contractNegotiationEventMessage);
		ContractNegotiationEventMessage obj = Serializer.deserializePlain(ss, ContractNegotiationEventMessage.class);
		assertThat(contractNegotiationEventMessage).usingRecursiveComparison().isEqualTo(obj);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		String ss = Serializer.serializeProtocol(contractNegotiationEventMessage);
		ContractNegotiationEventMessage obj = Serializer.deserializeProtocol(ss, ContractNegotiationEventMessage.class);
		assertThat(contractNegotiationEventMessage).usingRecursiveComparison().isEqualTo(obj);
	}
	
	private void validateJavaObj(ContractNegotiationEventMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getEventType());
	}
	
}
