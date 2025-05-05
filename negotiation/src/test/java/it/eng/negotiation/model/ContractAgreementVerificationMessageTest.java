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

public class ContractAgreementVerificationMessageTest {

	private ContractAgreementVerificationMessage contractAgreementVerificationMessage = ContractAgreementVerificationMessage.Builder
			.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.build();
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = NegotiationSerializer.serializePlain(contractAgreementVerificationMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		
		ContractAgreementVerificationMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractAgreementVerificationMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = NegotiationSerializer.serializeProtocolJsonNode(contractAgreementVerificationMessage);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		
		ContractAgreementVerificationMessage javaObj = NegotiationSerializer.deserializeProtocol(result, ContractAgreementVerificationMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractAgreementVerificationMessage.Builder.newInstance()
					.build());
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = NegotiationSerializer.serializePlainJsonNode(contractAgreementVerificationMessage);
		assertThrows(ValidationException.class, () -> NegotiationSerializer.deserializeProtocol(result, ContractAgreementVerificationMessage.class));
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		String ss = NegotiationSerializer.serializePlain(contractAgreementVerificationMessage);
		ContractAgreementVerificationMessage obj = NegotiationSerializer.deserializePlain(ss, ContractAgreementVerificationMessage.class);
		assertThat(contractAgreementVerificationMessage).usingRecursiveComparison().isEqualTo(obj);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		String ss = NegotiationSerializer.serializeProtocol(contractAgreementVerificationMessage);
		ContractAgreementVerificationMessage obj = NegotiationSerializer.deserializeProtocol(ss, ContractAgreementVerificationMessage.class);
		assertThat(contractAgreementVerificationMessage).usingRecursiveComparison().isEqualTo(obj);
	}

	private void validateJavaObj(ContractAgreementVerificationMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
	}
	
}
