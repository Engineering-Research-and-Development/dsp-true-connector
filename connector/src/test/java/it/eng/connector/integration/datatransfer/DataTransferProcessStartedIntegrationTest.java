package it.eng.connector.integration.datatransfer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferError;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.model.IConstants;

public class DataTransferProcessStartedIntegrationTest extends BaseIntegrationTest {

	// REQUESTED->STARTED
	// @PostMapping(path = "/{consumerPid}/start")
	
	@Autowired
	private TransferProcessRepository transferProcessRepository;
	
	@AfterEach
	public void cleanup() {
		transferProcessRepository.deleteAll();
	}
	
	@Test
	@DisplayName("Start transfer process - from requested - provider - cannot transit")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_provider_cannot_transit() throws Exception {
		
		TransferProcess transferProcessRequested = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.state(TransferState.REQUESTED)
    			.role(IConstants.ROLE_PROVIDER)
    			.build();
    	transferProcessRepository.save(transferProcessRequested);
	      
    	TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
				.consumerPid(transferProcessRequested.getConsumerPid())
				.providerPid(transferProcessRequested.getProviderPid())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/" + transferStartMessage.getProviderPid() +"/start")
    					.content(TransferSerializer.serializeProtocol(transferStartMessage))
    					.contentType(MediaType.APPLICATION_JSON));
		//["State transition aborted, consumer can not transit from REQUESTED to STARTED"]}
    	result.andExpect(status().isBadRequest());
//    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
    	String response = result.andReturn().getResponse().getContentAsString();
    	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
    	assertNotNull(transferError);
	}
	
	@Test
	@DisplayName("Start transfer process - not_found")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_transfer_not_found() throws Exception {
    	
    	TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
				.providerPid(createNewId())
				.consumerPid(createNewId())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/" + transferStartMessage.getProviderPid() +"/start")
    					.content(TransferSerializer.serializeProtocol(transferStartMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());

    	String response = result.andReturn().getResponse().getContentAsString();
    	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
    	assertNotNull(transferError);
	}
	
	@Test
	@DisplayName("Start transfer process - wrong message")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_transfer_wrong_message() throws Exception {
    	
    	TransferCompletionMessage transferCompletionMessage = TransferCompletionMessage.Builder.newInstance()
				.providerPid(createNewId())
				.consumerPid(createNewId())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/" + transferCompletionMessage.getProviderPid() +"/start")
    					.content(TransferSerializer.serializeProtocol(transferCompletionMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());

    	String response = result.andReturn().getResponse().getContentAsString();
    	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
    	assertNotNull(transferError);
	}

	
	@Test
	@DisplayName("Start transfer process - from requested - consumer")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_consumer() throws Exception {
		TransferProcess transferProcessRequested = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.state(TransferState.REQUESTED)
    			.role(IConstants.ROLE_CONSUMER)
    			.build();
    	transferProcessRepository.save(transferProcessRequested);
    	
    	TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
				.consumerPid(transferProcessRequested.getConsumerPid())
				.providerPid(transferProcessRequested.getProviderPid())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/transfers/" + transferStartMessage.getConsumerPid() +"/start")
    					.content(TransferSerializer.serializeProtocol(transferStartMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk());
    	
    	//?? not sure if this is correct endpoint or it should be consumer/transfers/{consumerPid}
    	// but such endpoint does not exists in protocol specification
    	ResultActions transferProcessStartedAction = mockMvc.perform(
    			get("/transfers/" + transferProcessRequested.getProviderPid())
    			.contentType(MediaType.APPLICATION_JSON));
    	// check if status is STARTED
    	transferProcessStartedAction.andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
    	String response = transferProcessStartedAction.andReturn().getResponse().getContentAsString();
    	TransferProcess transferProcessStarted = TransferSerializer.deserializeProtocol(response, TransferProcess.class);
    	assertNotNull(transferProcessStarted);
	}
	
	@Test
	@DisplayName("Start transfer process - not_found - consumer")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_consumer_transfer_not_found() throws Exception {
    	
    	TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/transfers/" + transferStartMessage.getConsumerPid() +"/start")
    					.content(TransferSerializer.serializeProtocol(transferStartMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());

    	String response = result.andReturn().getResponse().getContentAsString();
    	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
    	assertNotNull(transferError);
	}
	
	@Test
	@DisplayName("Start transfer process - consumer - wrong message")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess__consumer_transfer_wrong_message() throws Exception {
    	
    	TransferCompletionMessage transferCompletionMessage = TransferCompletionMessage.Builder.newInstance()
				.providerPid(createNewId())
				.consumerPid(createNewId())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/transfers/" + transferCompletionMessage.getProviderPid() +"/start")
    					.content(TransferSerializer.serializeProtocol(transferCompletionMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());

    	String response = result.andReturn().getResponse().getContentAsString();
    	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
    	assertNotNull(transferError);
	}
	
	// SUSPENDED->STARTED
	@Test
	@DisplayName("Start transfer process - from suspended - provider")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_suspended() throws Exception {
		TransferProcess transferProcessSuspended = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.state(TransferState.SUSPENDED)
    			.role(IConstants.ROLE_CONSUMER)
    			.build();
    	transferProcessRepository.save(transferProcessSuspended);
    	
    	TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
				.consumerPid(transferProcessSuspended.getConsumerPid())
				.providerPid(transferProcessSuspended.getProviderPid())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/" + transferStartMessage.getProviderPid() +"/start")
    					.content(TransferSerializer.serializeProtocol(transferStartMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk());
		// no response
	}
	
	@Test
	@DisplayName("Start transfer process - from suspended - provider - invalid state")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_suspended_ivalid_state() throws Exception {
		TransferProcess transferProcessSuspended = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.state(TransferState.STARTED)
    			.role(IConstants.ROLE_CONSUMER)
    			.build();
    	transferProcessRepository.save(transferProcessSuspended);
    	
    	TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
				.consumerPid(transferProcessSuspended.getConsumerPid())
				.providerPid(transferProcessSuspended.getProviderPid())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/" + transferStartMessage.getProviderPid() +"/start")
    					.content(TransferSerializer.serializeProtocol(transferStartMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());

    	String response = result.andReturn().getResponse().getContentAsString();
    	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
    	assertNotNull(transferError);
	}
	
	@Test
	@DisplayName("Start transfer process - from suspended - consumer")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_suspended_consumer() throws Exception {
		TransferProcess transferProcessSuspended = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.state(TransferState.SUSPENDED)
    			.role(IConstants.ROLE_PROVIDER)
    			.build();
    	transferProcessRepository.save(transferProcessSuspended);
    	
    	TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
				.consumerPid(transferProcessSuspended.getConsumerPid())
				.providerPid(transferProcessSuspended.getProviderPid())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/transfers/" + transferStartMessage.getConsumerPid() +"/start")
    					.content(TransferSerializer.serializeProtocol(transferStartMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk());
		// no response
	}
	
	@Test
	@DisplayName("Start transfer process - from suspended - consumer - invalid state")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_suspended_consumer_invalid_state() throws Exception {
		TransferProcess transferProcessSuspended = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.state(TransferState.STARTED)
    			.role(IConstants.ROLE_PROVIDER)
    			.build();
    	transferProcessRepository.save(transferProcessSuspended);
    	
    	TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
				.consumerPid(transferProcessSuspended.getConsumerPid())
				.providerPid(transferProcessSuspended.getProviderPid())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/transfers/" + transferStartMessage.getConsumerPid() +"/start")
    					.content(TransferSerializer.serializeProtocol(transferStartMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());
    	
    	String response = result.andReturn().getResponse().getContentAsString();
    	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
    	assertNotNull(transferError);
	}

}
