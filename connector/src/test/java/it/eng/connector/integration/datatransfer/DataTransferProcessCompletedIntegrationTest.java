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
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;
import it.eng.tools.model.IConstants;

public class DataTransferProcessCompletedIntegrationTest extends BaseIntegrationTest {
// STARTED -> COMPLETED
	
	@Autowired
	private TransferProcessRepository transferProcessRepository;
	
	@AfterEach
	public void cleanup() {
		transferProcessRepository.deleteAll();
	}

	// Provider
	@Test
	@DisplayName("Complete transfer process - from started")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void completeTransferProcess_provider() throws Exception {
		
		TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.state(TransferState.STARTED)
    			.role(IConstants.ROLE_PROVIDER)
    			.build();
    	transferProcessRepository.save(transferProcessStarted);
	      
    	TransferCompletionMessage transferCompletionMessage = TransferCompletionMessage.Builder.newInstance()
				.consumerPid(transferProcessStarted.getConsumerPid())
				.providerPid(transferProcessStarted.getProviderPid())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/" + transferCompletionMessage.getProviderPid() +"/completion")
    					.content(TransferSerializer.serializeProtocol(transferCompletionMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk());
//    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
    	ResultActions transferProcessStartedAction = mockMvc.perform(
    			get("/transfers/" + transferProcessStarted.getProviderPid())
    			.contentType(MediaType.APPLICATION_JSON));
    	// check if status is STARTED
    	transferProcessStartedAction.andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
    	String response = transferProcessStartedAction.andReturn().getResponse().getContentAsString();
    	TransferProcess transferProcessStarted2 = TransferSerializer.deserializeProtocol(response, TransferProcess.class);
    	assertNotNull(transferProcessStarted2);
	}
	
	@Test
	@DisplayName("Complete transfer process - not_found")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void completeTransferProcess_transfer_not_found() throws Exception {
    	TransferStartMessage transferProcessStarted = TransferStartMessage.Builder.newInstance()
				.providerPid(createNewId())
				.consumerPid(createNewId())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/" + transferProcessStarted.getProviderPid() +"/completion")
    					.content(TransferSerializer.serializeProtocol(transferProcessStarted))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());

    	String response = result.andReturn().getResponse().getContentAsString();
    	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
    	assertNotNull(transferError);
	}
	
	@Test
	@DisplayName("Complete transfer process - provider - invalid message type")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void completeTransferProcess_provider_transfer_invalid_msg() throws Exception {
		TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
	    		.consumerPid(createNewId())
	    		.agreementId("agreement_id")
	    		.format(DataTransferFormat.HTTP_PULL.format())
	    		.callbackAddress(DataTranferMockObjectUtil.CALLBACK_ADDRESS)
	    		.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/" + transferRequestMessage.getConsumerPid() +"/completion")
    					.content(TransferSerializer.serializeProtocol(transferRequestMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());
	}
	
	@Test
	@DisplayName("Complete transfer process - invalid state")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void completeTransferProcess_invalid_state() throws Exception {
		TransferProcess transferProcessRequested = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.state(TransferState.REQUESTED)
    			.role(IConstants.ROLE_PROVIDER)
    			.build();
    	transferProcessRepository.save(transferProcessRequested);
	      
    	TransferStartMessage transferProcessStarted = TransferStartMessage.Builder.newInstance()
				.providerPid(createNewId())
				.consumerPid(createNewId())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/" + transferProcessStarted.getProviderPid() +"/completion")
    					.content(TransferSerializer.serializeProtocol(transferProcessStarted))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());

    	String response = result.andReturn().getResponse().getContentAsString();
    	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
    	assertNotNull(transferError);
	}
	
	// Consumer
	@Test
	@DisplayName("Complete transfer process - from requested - consumer")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void startTransferProcess_consumer() throws Exception {
		TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.state(TransferState.STARTED)
    			.role(IConstants.ROLE_PROVIDER)
    			.build();
    	transferProcessRepository.save(transferProcessStarted);
    	
    	TransferCompletionMessage transferCompletionMessage = TransferCompletionMessage.Builder.newInstance()
				.consumerPid(transferProcessStarted.getConsumerPid())
				.providerPid(transferProcessStarted.getProviderPid())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/transfers/" + transferCompletionMessage.getConsumerPid() +"/completion")
    					.content(TransferSerializer.serializeProtocol(transferCompletionMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isOk());
    	
    	//?? not sure if this is correct endpoint or it should be consumer/transfers/{consumerPid}
    	// but such endpoint does not exists in protocol specification
    	ResultActions transferProcessStartedAction = mockMvc.perform(
    			get("/transfers/" + transferProcessStarted.getProviderPid())
    			.contentType(MediaType.APPLICATION_JSON));
    	// check if status is STARTED
    	transferProcessStartedAction.andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
    	String response = transferProcessStartedAction.andReturn().getResponse().getContentAsString();
    	TransferProcess transferProcessStarted2 = TransferSerializer.deserializeProtocol(response, TransferProcess.class);
    	assertNotNull(transferProcessStarted2);
	}
	
	@Test
	@DisplayName("Complete transfer process - consumer - not_found")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void completeTransferProcess_consumer_transfer_not_found() throws Exception {
    	TransferStartMessage transferProcessStarted = TransferStartMessage.Builder.newInstance()
				.providerPid(createNewId())
				.consumerPid(createNewId())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/transfers/" + transferProcessStarted.getConsumerPid() +"/completion")
    					.content(TransferSerializer.serializeProtocol(transferProcessStarted))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());

    	String response = result.andReturn().getResponse().getContentAsString();
    	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
    	assertNotNull(transferError);
	}
	
	@Test
	@DisplayName("Complete transfer process - consumer - invalid msg")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void completeTransferProcess_consumer_transfer_invalid_msg() throws Exception {
		TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
	    		.consumerPid(createNewId())
	    		.agreementId("agreement_id")
	    		.format(DataTransferFormat.HTTP_PULL.format())
	    		.callbackAddress(DataTranferMockObjectUtil.CALLBACK_ADDRESS)
	    		.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/consumer/transfers/" + transferRequestMessage.getConsumerPid() +"/completion")
    					.content(TransferSerializer.serializeProtocol(transferRequestMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());
	}
	
	@Test
	@DisplayName("Complete transfer process - consumer - invalid state")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void completeTransferProcess_consumer_invalid_state() throws Exception {
		TransferProcess transferProcessRequested = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.state(TransferState.REQUESTED)
    			.role(IConstants.ROLE_PROVIDER)
    			.build();
    	transferProcessRepository.save(transferProcessRequested);
	      
    	TransferStartMessage transferProcessStarted = TransferStartMessage.Builder.newInstance()
				.providerPid(createNewId())
				.consumerPid(createNewId())
				.build();
    	
		final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/" + transferProcessStarted.getConsumerPid() +"/completion")
    					.content(TransferSerializer.serializeProtocol(transferProcessStarted))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest());

    	String response = result.andReturn().getResponse().getContentAsString();
    	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
    	assertNotNull(transferError);
	}
}
