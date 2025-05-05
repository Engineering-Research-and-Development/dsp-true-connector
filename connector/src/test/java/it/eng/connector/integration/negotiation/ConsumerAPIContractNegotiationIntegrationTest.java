package it.eng.connector.integration.negotiation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;
import org.wiremock.spring.InjectWireMock;

import com.fasterxml.jackson.databind.JavaType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.model.Reason;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;

public class ConsumerAPIContractNegotiationIntegrationTest extends BaseIntegrationTest {

	@InjectWireMock 
	private WireMockServer wiremock;
	
	@Autowired
	private ContractNegotiationRepository contractNegotiationRepository;
	
	@AfterEach
	public void cleanup() {
		contractNegotiationRepository.deleteAll();
	}

	// start negotiation
	@Test
	@DisplayName("Consumer initiates contract negotiation - success")
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void consumerInitiatesContractNegotiation() throws Exception {
		// insert data into consumer DB
		ContractNegotiation contractNegotiationResponse = ContractNegotiation.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.state(ContractNegotiationState.REQUESTED)
				.build();
		
		// prepare provider/wiremock response
		WireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/negotiations/request")
				.withBasicAuth("connector@mail.com", "password")
				.withRequestBody(WireMock.containing("dspace:ContractRequestMessage"))
				.willReturn(
	                aResponse().withHeader("Content-Type", "application/json")
	                .withBody(NegotiationSerializer.serializeProtocol(contractNegotiationResponse))));
		 
		// send API request
		Map<String, Object> apiContractNegotiationRequest = new HashMap<>();
		apiContractNegotiationRequest.put("Forward-To", wiremock.baseUrl());
		apiContractNegotiationRequest.put("offer", NegotiationMockObjectUtil.OFFER);
		final ResultActions result = mockMvc.perform(post(ApiEndpoints.NEGOTIATION_V1)
				.content(NegotiationSerializer.serializePlain(apiContractNegotiationRequest))
				.contentType(MediaType.APPLICATION_JSON));
		
		// verify expected behavior
		result.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		
		String json = result.andReturn().getResponse().getContentAsString();
		JavaType javaType = jsonMapper.getTypeFactory().constructParametricType(GenericApiResponse.class, ContractNegotiation.class);
		GenericApiResponse<ContractNegotiation> genericApiResponse = jsonMapper.readValue(json, javaType);
		assertNotNull(genericApiResponse);
		assertTrue(genericApiResponse.isSuccess());
		assertNotNull(genericApiResponse.getData());
		assertEquals(ContractNegotiation.class, genericApiResponse.getData().getClass());
	}
	
	@Test
	@DisplayName("Consumer initiates contract negotiation - provider error")
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void consumerInitiatesContractNegotiation_providerError() throws Exception {
		// prepare provider/wiremock response
		ContractNegotiationErrorMessage contractNegotiationError = ContractNegotiationErrorMessage.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.code("TEST_ERROR")
				.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("Test error").build()))
				.build();
		WireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/negotiations/request")
				.withBasicAuth("connector@mail.com", "password")
				.withRequestBody(WireMock.containing("dspace:ContractRequestMessage"))
				.willReturn(
	                aResponse().withHeader("Content-Type", "application/json")
	                .withStatus(400)
	                .withBody(NegotiationSerializer.serializeProtocol(contractNegotiationError))));
		
		// send API request
		Map<String, Object> apiContractNegotiationRequest = new HashMap<>();
		apiContractNegotiationRequest.put("Forward-To", wiremock.baseUrl());
		apiContractNegotiationRequest.put("offer", NegotiationMockObjectUtil.OFFER);
		final ResultActions result = mockMvc.perform(post(ApiEndpoints.NEGOTIATION_V1)
				.content(NegotiationSerializer.serializePlain(apiContractNegotiationRequest))
				.contentType(MediaType.APPLICATION_JSON));
		
		// verify expected behavior
		result.andExpect(status().is4xxClientError())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		String json = result.andReturn().getResponse().getContentAsString();
		JavaType javaType = jsonMapper.getTypeFactory().constructParametricType(GenericApiResponse.class, ContractNegotiationErrorMessage.class);
		GenericApiResponse<ContractNegotiationErrorMessage> genericApiResponse = jsonMapper.readValue(json, javaType);
		assertNotNull(genericApiResponse);
		assertFalse(genericApiResponse.isSuccess());
		assertNotNull(genericApiResponse.getData());
		assertEquals(ContractNegotiationErrorMessage.class, genericApiResponse.getData().getClass());
	}

	// verify negotiation
	@Test
	@DisplayName("Consumer verify contract negotiation")
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void consumerVerifyContractNegotiation() throws Exception {
		// insert data into consumer DB
		ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.state(ContractNegotiationState.AGREED)
				// wiremock acts as provider
				.callbackAddress(wiremock.baseUrl())
				.build();
		contractNegotiationRepository.save(contractNegotiation);
		
		// prepare provider/wiremock response
//		":callback:/negotiations/:providerPid:/agreement/verification"
		WireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/negotiations/" + contractNegotiation.getProviderPid() + "/agreement/verification")
				.withBasicAuth("connector@mail.com", "password")
				.withRequestBody(WireMock.containing("dspace:ContractAgreementVerificationMessage"))
				.willReturn(
	                aResponse().withHeader("Content-Type", "application/json")));
		
		// send API request
		//{contractNegotiationId}/verify
		final ResultActions result = mockMvc.perform(put(ApiEndpoints.NEGOTIATION_V1 + "/" + contractNegotiation.getId() + "/verify")
				.contentType(MediaType.APPLICATION_JSON));
		
		// verify expected behavior
		// state changed to VERIFIED
		assertEquals(ContractNegotiationState.VERIFIED, contractNegotiationRepository.findById(contractNegotiation.getId()).get().getState());

		String json = result.andReturn().getResponse().getContentAsString();
		JavaType javaType = jsonMapper.getTypeFactory().constructParametricType(GenericApiResponse.class, ContractNegotiation.class);
		GenericApiResponse<ContractNegotiation> genericApiResponse = jsonMapper.readValue(json, javaType);
		assertNotNull(genericApiResponse);
		assertTrue(genericApiResponse.isSuccess());
	}
	
	@Test
	@DisplayName("Consumer verify contract negotiation - provider error")
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void consumerVerifyContractNegotiation_providerError() throws Exception {
		// insert data into consumer DB
		ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.state(ContractNegotiationState.AGREED)
				// wiremock acts as provider
				.callbackAddress(wiremock.baseUrl())
				.build();
		contractNegotiationRepository.save(contractNegotiation);
		
		// prepare provider/wiremock response
		ContractNegotiationErrorMessage contractNegotiationError = ContractNegotiationErrorMessage.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.code("TEST_ERROR")
				.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("Test error").build()))
				.build();
		WireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/negotiations/" + contractNegotiation.getProviderPid() + "/agreement/verification")
				.withBasicAuth("connector@mail.com", "password")
				.withRequestBody(WireMock.containing("dspace:ContractAgreementVerificationMessage"))
				.willReturn(
	                aResponse().withHeader("Content-Type", "application/json")
	                .withStatus(400)
	                .withBody(NegotiationSerializer.serializeProtocol(contractNegotiationError))));
		
		// send API request
		//{contractNegotiationId}/verify
		final ResultActions result = mockMvc.perform(put(ApiEndpoints.NEGOTIATION_V1 + "/" + contractNegotiation.getId() + "/verify")
				.contentType(MediaType.APPLICATION_JSON));
		
		// verify expected behavior
		result.andExpect(status().is4xxClientError())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		String json = result.andReturn().getResponse().getContentAsString();
		JavaType javaType = jsonMapper.getTypeFactory().constructParametricType(GenericApiResponse.class, ContractNegotiationErrorMessage.class);
		GenericApiResponse<ContractNegotiationErrorMessage> genericApiResponse = jsonMapper.readValue(json, javaType);
		
		assertNotNull(genericApiResponse);
		assertFalse(genericApiResponse.isSuccess());
		assertNotNull(genericApiResponse.getData());
		assertEquals(ContractNegotiationErrorMessage.class, genericApiResponse.getData().getClass());
			
		// state NOT changed
		assertEquals(ContractNegotiationState.AGREED, contractNegotiationRepository.findById(contractNegotiation.getId()).get().getState());
	}
	
	// terminate contract negotiation
	@Test
	@DisplayName("Consumer terminates contract negotiation")
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void consumerTerminatesContractNegotiation() throws Exception {
		// insert data into consumer DB
		ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.state(ContractNegotiationState.AGREED)
				.role(IConstants.ROLE_CONSUMER)
				// wiremock acts as provider
				.callbackAddress(wiremock.baseUrl())
				.build();
		contractNegotiationRepository.save(contractNegotiation);
		
		// prepare provider/wiremock response
		// /{providerPid}/termination
		WireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/negotiations/" + contractNegotiation.getProviderPid() + "/termination")
				.withBasicAuth("connector@mail.com", "password")
				.withRequestBody(WireMock.containing("dspace:ContractNegotiationTerminationMessage"))
				.willReturn(
	                aResponse().withHeader("Content-Type", "application/json")));
		
		// send API request
		//{contractNegotiationId}/terminate
		final ResultActions result = mockMvc.perform(put(ApiEndpoints.NEGOTIATION_V1 + "/" + contractNegotiation.getId() + "/terminate")
				.contentType(MediaType.APPLICATION_JSON));

		// verify expected behavior
		String json = result.andReturn().getResponse().getContentAsString();
		JavaType javaType = jsonMapper.getTypeFactory().constructParametricType(GenericApiResponse.class, ContractNegotiation.class);
		GenericApiResponse<ContractNegotiation> genericApiResponse = jsonMapper.readValue(json, javaType);
		assertNotNull(genericApiResponse);
		assertTrue(genericApiResponse.isSuccess());
		
		// state TERMIANTED
		assertEquals(ContractNegotiationState.TERMINATED, contractNegotiationRepository.findById(contractNegotiation.getId()).get().getState());
	}
	
	// terminate contract negotiation
	@Test
	@DisplayName("Consumer terminates contract negotiation - provider error")
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void consumerTerminatesContractNegotiation_providerError() throws Exception {
		// insert data into consumer DB
		ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.state(ContractNegotiationState.AGREED)
				// wiremock acts as provider
				.callbackAddress(wiremock.baseUrl())
				.role(IConstants.ROLE_CONSUMER)
				.build();
		contractNegotiationRepository.save(contractNegotiation);
		
		// prepare provider/wiremock response
		ContractNegotiationErrorMessage contractNegotiationError = ContractNegotiationErrorMessage.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.code("TEST_ERROR")
				.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("Test error").build()))
				.build();
		WireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/negotiations/" + contractNegotiation.getProviderPid() + "/termination")
				.withBasicAuth("connector@mail.com", "password")
				.withRequestBody(WireMock.containing("dspace:ContractNegotiationTerminationMessage"))
				.willReturn(
	                aResponse().withHeader("Content-Type", "application/json")
	                .withStatus(400)
	                .withBody(NegotiationSerializer.serializeProtocol(contractNegotiationError))));

		// send API request
		//{contractNegotiationId}/terminate
		final ResultActions result = mockMvc.perform(put(ApiEndpoints.NEGOTIATION_V1 + "/" + contractNegotiation.getId() + "/terminate")
				.contentType(MediaType.APPLICATION_JSON));

		// verify expected behavior
		result.andExpect(status().is4xxClientError())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));
		String json = result.andReturn().getResponse().getContentAsString();
		JavaType javaType = jsonMapper.getTypeFactory().constructParametricType(GenericApiResponse.class, ContractNegotiationErrorMessage.class);
		GenericApiResponse<ContractNegotiationErrorMessage> genericApiResponse = jsonMapper.readValue(json, javaType);
		
		assertNotNull(genericApiResponse);
		assertFalse(genericApiResponse.isSuccess());
		assertNotNull(genericApiResponse.getData());
		assertEquals(ContractNegotiationErrorMessage.class, genericApiResponse.getData().getClass());
			
		// state NOT changed
		assertEquals(ContractNegotiationState.AGREED, contractNegotiationRepository.findById(contractNegotiation.getId()).get().getState());
	}
}
