package it.eng.negotiation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractNegotiationTerminationMessageTest {

	private ContractNegotiationTerminationMessage contractNegotiationTerminationMessage = ContractNegotiationTerminationMessage.Builder
			.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.code("Termination CD_123")
			.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("Meaningful reas for term").build(),
					Reason.Builder.newInstance().language("it").value("Meaningful reas for term but in Italian").build()))
			.build();
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = NegotiationSerializer.serializePlain(contractNegotiationTerminationMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CODE));
		assertTrue(result.contains(DSpaceConstants.REASON));
		
		ContractNegotiationTerminationMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractNegotiationTerminationMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = NegotiationSerializer.serializeProtocolJsonNode(contractNegotiationTerminationMessage);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CODE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_REASON).get(0));
		
		ContractNegotiationTerminationMessage javaObj = NegotiationSerializer.deserializeProtocol(result, ContractNegotiationTerminationMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractNegotiationTerminationMessage.Builder.newInstance()
					.build());
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = NegotiationSerializer.serializePlainJsonNode(contractNegotiationTerminationMessage);
		assertThrows(ValidationException.class, () -> NegotiationSerializer.deserializeProtocol(result, ContractNegotiationTerminationMessage.class));
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		String ss = NegotiationSerializer.serializePlain(contractNegotiationTerminationMessage);
		ContractNegotiationTerminationMessage obj = NegotiationSerializer.deserializePlain(ss, ContractNegotiationTerminationMessage.class);
		assertThat(contractNegotiationTerminationMessage).usingRecursiveComparison().isEqualTo(obj);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		String ss = NegotiationSerializer.serializeProtocol(contractNegotiationTerminationMessage);
		ContractNegotiationTerminationMessage obj = NegotiationSerializer.deserializeProtocol(ss, ContractNegotiationTerminationMessage.class);
		assertThat(contractNegotiationTerminationMessage).usingRecursiveComparison().isEqualTo(obj);
	}
	
	private void validateJavaObj(ContractNegotiationTerminationMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getCode());
		// must be 2 descriptions
		assertEquals(2, javaObj.getReason().size());
	}
}
