package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractAgreementVerificationMessageTest {

	private ContractAgreementVerificationMessage contractAgreementVerificationMessage = ContractAgreementVerificationMessage.Builder
			.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.build();
	
	@Test
	public void testPlain() {
		String result = Serializer.serializePlain(contractAgreementVerificationMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		
		ContractAgreementVerificationMessage javaObj = Serializer.deserializePlain(result, ContractAgreementVerificationMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	public void testProtocol() {
		String result = Serializer.serializeProtocol(contractAgreementVerificationMessage);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_PROVIDER_PID));
		
		ContractAgreementVerificationMessage javaObj = Serializer.deserializeProtocol(result, ContractAgreementVerificationMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractAgreementVerificationMessage.Builder.newInstance()
					.build());
	}

	private void validateJavaObj(ContractAgreementVerificationMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
	}
	
}
