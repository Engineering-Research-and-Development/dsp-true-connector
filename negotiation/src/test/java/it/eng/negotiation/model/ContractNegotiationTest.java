package it.eng.negotiation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractNegotiationTest {

	private ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
			.id("urn:uuid:" + UUID.randomUUID())
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.state(ContractNegotiationState.ACCEPTED)
			.build();
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = NegotiationSerializer.serializePlain(contractNegotiation);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains("ACCEPTED"));
		
		ContractNegotiation javaObj = NegotiationSerializer.deserializePlain(result, ContractNegotiation.class);
		validateJavaObj(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = NegotiationSerializer.serializeProtocolJsonNode(contractNegotiation);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_STATE).asText());
		assertNull(result.get(DSpaceConstants.ID));
		ContractNegotiation javaObj = NegotiationSerializer.deserializeProtocol(result, ContractNegotiation.class);
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
		JsonNode result = NegotiationSerializer.serializePlainJsonNode(contractNegotiation);
		assertThrows(ValidationException.class, () -> NegotiationSerializer.deserializeProtocol(result, ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("From Requestsed state")
	public void requestedState() {
		ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.state(ContractNegotiationState.REQUESTED)
			.build();
		assertTrue(cn.getState().nextState().containsAll(Arrays.asList(ContractNegotiationState.OFFERED, ContractNegotiationState.AGREED, ContractNegotiationState.TERMINATED)));
	}
	
	@Test
	@DisplayName("From Offered state")
	public void offeredState() {
		ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.state(ContractNegotiationState.OFFERED)
			.build();
		assertTrue(cn.getState().nextState().containsAll(Arrays.asList(ContractNegotiationState.REQUESTED, ContractNegotiationState.ACCEPTED, ContractNegotiationState.TERMINATED)));
	}
	
	@Test
	@DisplayName("From Accepted state")
	public void acceptedState() {
		ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.state(ContractNegotiationState.ACCEPTED)
			.build();
		assertTrue(cn.getState().nextState().containsAll(Arrays.asList(ContractNegotiationState.AGREED, ContractNegotiationState.TERMINATED)));
	}
	
	@Test
	@DisplayName("From Agreed state")
	public void agreedState() {
		ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.state(ContractNegotiationState.AGREED)
			.build();
		assertTrue(cn.getState().nextState().containsAll(Arrays.asList(ContractNegotiationState.VERIFIED, ContractNegotiationState.TERMINATED)));
	}
	
	@Test
	@DisplayName("From Verified state")
	public void verifiedState() {
		ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.state(ContractNegotiationState.VERIFIED)
			.build();
		assertTrue(cn.getState().nextState().containsAll(Arrays.asList(ContractNegotiationState.FINALIZED, ContractNegotiationState.TERMINATED)));
	}

	@Test
	@DisplayName("From initial ContractNegotiation with new ContractNegotiationState")
	public void withNewState() {
		ContractNegotiation contractNegotiationOffered = NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.withNewContractNegotiationState(ContractNegotiationState.OFFERED);
		assertEquals(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId(), contractNegotiationOffered.getId());
		assertEquals(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getConsumerPid(), contractNegotiationOffered.getConsumerPid());
		assertEquals(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getProviderPid(), contractNegotiationOffered.getProviderPid());
		assertEquals(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getCallbackAddress(), contractNegotiationOffered.getCallbackAddress());
		assertEquals(ContractNegotiationState.OFFERED, contractNegotiationOffered.getState());
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		String ss = NegotiationSerializer.serializePlain(contractNegotiation);
		ContractNegotiation obj = NegotiationSerializer.deserializePlain(ss, ContractNegotiation.class);
		// must exclude id since it is not according to protocol but internally
		assertThat(contractNegotiation).usingRecursiveComparison().ignoringFieldsMatchingRegexes("id").isEqualTo(obj);
		assertEquals(contractNegotiation.getId(), obj.getId());
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		String ss = NegotiationSerializer.serializeProtocol(contractNegotiation);
		ContractNegotiation obj = NegotiationSerializer.deserializeProtocol(ss, ContractNegotiation.class);
		// must exclude id since it is not according to protocol but internally
		assertThat(contractNegotiation).usingRecursiveComparison().ignoringFieldsMatchingRegexes("id").isEqualTo(obj);
		// protocol does not have id field
		//		assertEquals(contractNegotiation.getId(), obj.getId());
	}
	
	private void validateJavaObj(ContractNegotiation javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getState());
	}
}
