package it.eng.connector.integration.datatransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.UnsupportedEncodingException;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferError;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.model.TransferTerminationMessage;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.TransferSerializer;

public class DataTransferProcessTerminatedIntegrationTest extends BaseIntegrationTest {

	// REQUESTED, STARTED, SUSPENDED -> TERMINATED
	
	@Autowired
	private TransferProcessRepository transferProcessRepository;
	
	@AfterEach
	public void cleanup() {
		transferProcessRepository.deleteAll();
	}

	@DisplayName("Terminate transfer process - provider")
	@ParameterizedTest
	@MethodSource("getTransferStates")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void termianteTransferProcess_provider(TransferState state) throws Exception {

		TransferProcess transferProcessRequested = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.state(state)
    			.build();
    	transferProcessRepository.save(transferProcessRequested);
		
		// send terminate message
		TransferTerminationMessage transferTerminationMessage = TransferTerminationMessage.Builder.newInstance()
				.consumerPid(transferProcessRequested.getConsumerPid())
				.providerPid(transferProcessRequested.getProviderPid())
				.code("1")
				.build();
		
		mockMvc.perform(
				post("/transfers/" + transferTerminationMessage.getProviderPid() + "/termination")
				.content(TransferSerializer.serializeProtocol(transferTerminationMessage))
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk());
		// no response

		// check TransferProcess status for providerPid
		TransferProcess transferProcessTerminated = getTransferProcessForProviderPid(transferProcessRequested.getProviderPid());
		assertEquals(transferProcessTerminated.getState(), TransferState.TERMINATED);
	}
	
	@DisplayName("Terminate transfer process - consumer")
	@ParameterizedTest
	@MethodSource("getTransferStates")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void termianteTransferProcess_consumer(TransferState state) throws Exception {

		TransferProcess transferProcessRequested = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.state(state)
    			.build();
    	transferProcessRepository.save(transferProcessRequested);
		
		// send terminate message
		TransferTerminationMessage transferTerminationMessage = TransferTerminationMessage.Builder.newInstance()
				.consumerPid(transferProcessRequested.getConsumerPid())
				.providerPid(transferProcessRequested.getProviderPid())
				.code("1")
				.build();
		
		mockMvc.perform(
				post("/consumer/transfers/" + transferTerminationMessage.getConsumerPid() + "/termination")
				.content(TransferSerializer.serializeProtocol(transferTerminationMessage))
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk());
		// no response

		// check TransferProcess status for providerPid
		TransferProcess transferProcessTerminated = getTransferProcessForProviderPid(transferProcessRequested.getProviderPid());
		assertEquals(transferProcessTerminated.getState(), TransferState.TERMINATED);
	}
	
	@Test
	@DisplayName("Terminate transfer process - not_found")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void terminateTransferProcess_transfer_not_found() throws Exception {
		
		TransferTerminationMessage transferTerminationMessage = TransferTerminationMessage.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.code("1")
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/" + transferTerminationMessage.getProviderPid() +"/start")
    					.content(TransferSerializer.serializeProtocol(transferTerminationMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());

    	String response = result.andReturn().getResponse().getContentAsString();
    	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
    	assertNotNull(transferError);
	}
	
	@Test
	@DisplayName("Terminate transfer process - consumer - not_found")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void terminateTransferProcess_consumer_transfer_not_found() throws Exception {
		
		TransferTerminationMessage transferTerminationMessage = TransferTerminationMessage.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.code("1")
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/transfers/" + transferTerminationMessage.getConsumerPid() +"/start")
    					.content(TransferSerializer.serializeProtocol(transferTerminationMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());

    	String response = result.andReturn().getResponse().getContentAsString();
    	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
    	assertNotNull(transferError);
	}
	
	private static Stream<TransferState> getTransferStates() {
	    return Stream.of(TransferState.REQUESTED, TransferState.STARTED, TransferState.SUSPENDED);
	}
	
	private TransferProcess getTransferProcessForProviderPid(String providerPid)
			throws Exception, UnsupportedEncodingException, JsonMappingException, JsonProcessingException {
		MvcResult resultCompletedMessage = mockMvc.perform(
        			get("/transfers/" + providerPid)
        			.contentType(MediaType.APPLICATION_JSON))
    			.andExpect(status().isOk())
            	.andReturn();	
    	String jsonTransferProcessCompleted = resultCompletedMessage.getResponse().getContentAsString();
    	JsonNode jsonNodeCompleted = TransferSerializer.serializeStringToProtocolJsonNode(jsonTransferProcessCompleted);
    	TransferProcess transferProcessCompleted = TransferSerializer.deserializeProtocol(jsonNodeCompleted, TransferProcess.class);
		return transferProcessCompleted;
	}
}
