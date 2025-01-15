package it.eng.negotiation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractNegotiationEventMessageTest {

	private ContractNegotiationEventMessage contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.eventType(ContractNegotiationEventType.ACCEPTED)
			.build();
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = NegotiationSerializer.serializePlain(contractNegotiationEventMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.EVENT_TYPE));
		
		ContractNegotiationEventMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractNegotiationEventMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = NegotiationSerializer.serializeProtocolJsonNode(contractNegotiationEventMessage);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_EVENT_TYPE).asText());
		
		ContractNegotiationEventMessage javaObj = NegotiationSerializer.deserializeProtocol(result, ContractNegotiationEventMessage.class);
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
		JsonNode result = NegotiationSerializer.serializePlainJsonNode(contractNegotiationEventMessage);
		assertThrows(ValidationException.class, () -> NegotiationSerializer.deserializeProtocol(result, ContractNegotiationEventMessage.class));
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		String ss = NegotiationSerializer.serializePlain(contractNegotiationEventMessage);
		ContractNegotiationEventMessage obj = NegotiationSerializer.deserializePlain(ss, ContractNegotiationEventMessage.class);
		assertThat(contractNegotiationEventMessage).usingRecursiveComparison().isEqualTo(obj);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		String ss = NegotiationSerializer.serializeProtocol(contractNegotiationEventMessage);
		ContractNegotiationEventMessage obj = NegotiationSerializer.deserializeProtocol(ss, ContractNegotiationEventMessage.class);
		assertThat(contractNegotiationEventMessage).usingRecursiveComparison().isEqualTo(obj);
	}
	
	private void validateJavaObj(ContractNegotiationEventMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getEventType());
	}
	
}
