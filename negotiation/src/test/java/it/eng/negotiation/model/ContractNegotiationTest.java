package it.eng.negotiation.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Arrays;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

import static org.junit.jupiter.api.Assertions.*;

public class ContractNegotiationTest {

	private ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.state(ContractNegotiationState.ACCEPTED)
			.build();
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = Serializer.serializePlain(contractNegotiation);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains("ACCEPTED"));
		
		ContractNegotiation javaObj = Serializer.deserializePlain(result, ContractNegotiation.class);
		validateJavaObj(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = Serializer.serializeProtocolJsonNode(contractNegotiation);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_STATE).asText());
		assertNull(result.get(DSpaceConstants.ID));
		ContractNegotiation javaObj = Serializer.deserializeProtocol(result, ContractNegotiation.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractNegotiation.Builder.newInstance()
					.build());
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = Serializer.serializePlainJsonNode(contractNegotiation);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("From Requestsed state")
	public void requestedState() {
		ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.state(ContractNegotiationState.REQUESTED)
			.build();
		assertTrue(cn.getState().nextState().containsAll(Arrays.asList(ContractNegotiationState.OFFERED, ContractNegotiationState.AGREED, ContractNegotiationState.TERMINATED)));
	}
	
	@Test
	@DisplayName("From Offered state")
	public void offeredState() {
		ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.state(ContractNegotiationState.OFFERED)
			.build();
		assertTrue(cn.getState().nextState().containsAll(Arrays.asList(ContractNegotiationState.REQUESTED, ContractNegotiationState.ACCEPTED, ContractNegotiationState.TERMINATED)));
	}
	
	@Test
	@DisplayName("From Accepted state")
	public void acceptedState() {
		ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.state(ContractNegotiationState.ACCEPTED)
			.build();
		assertTrue(cn.getState().nextState().containsAll(Arrays.asList(ContractNegotiationState.AGREED, ContractNegotiationState.TERMINATED)));
	}
	
	@Test
	@DisplayName("From Agreed state")
	public void agreedState() {
		ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.state(ContractNegotiationState.AGREED)
			.build();
		assertTrue(cn.getState().nextState().containsAll(Arrays.asList(ContractNegotiationState.VERIFIED, ContractNegotiationState.TERMINATED)));
	}
	
	@Test
	@DisplayName("From Verified state")
	public void verifiedState() {
		ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.state(ContractNegotiationState.VERIFIED)
			.build();
		assertTrue(cn.getState().nextState().containsAll(Arrays.asList(ContractNegotiationState.FINALIZED, ContractNegotiationState.TERMINATED)));
	}

	
	private void validateJavaObj(ContractNegotiation javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getState());
	}
}
