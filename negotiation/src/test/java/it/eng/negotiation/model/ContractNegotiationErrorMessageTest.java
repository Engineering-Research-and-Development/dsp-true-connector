package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractNegotiationErrorMessageTest {

	private ContractNegotiationErrorMessage contractNegotiationErrorMessage = ContractNegotiationErrorMessage.Builder
			.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.code("Negotiation error code 123")
			.description(Arrays.asList(
					Description.Builder.newInstance().language("en").value("English description text").build(),
					Description.Builder.newInstance().language("it").value("Description text but in Italian").build()
					))
			.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("reason text goes here").build()))
			.build();

	@Test
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
	public void testPlain_protocol() {
		String result = Serializer.serializeProtocol(contractNegotiationErrorMessage);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CODE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_REASON));
		assertTrue(result.contains(DSpaceConstants.DCT_DESCRIPTION));
		
		ContractNegotiationErrorMessage javaObj = Serializer.deserializeProtocol(result, ContractNegotiationErrorMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractNegotiationErrorMessage.Builder.newInstance()
					.build());
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
