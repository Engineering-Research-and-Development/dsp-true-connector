package it.eng.connector.integration.datatransfer;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferError;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;
import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.model.Permission;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.tools.model.DSpaceConstants;

public class DataTransferProcessRequestedIntegrationTest extends BaseIntegrationTest {
// Consumer -> REQUESTED
	
	@Autowired
	private AgreementRepository agreementRepository;
	@Autowired
	private ContractNegotiationRepository contractNegotiationRepository;
	@Autowired
	private TransferProcessRepository transferProcessRepository;
	// from initial_data
	private String datasetId = "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5";
	
	@Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void initiateDataTransfer() throws Exception {
		// finalized contract negotiation
		Permission permission = Permission.Builder.newInstance()
    			.action(Action.USE)
    			.constraint(Arrays.asList(Constraint.Builder.newInstance()
    					.leftOperand(LeftOperand.COUNT)
    					.operator(Operator.LTEQ)
    					.rightOperand("5")
    					.build()))
    			.build();
		Agreement agreement = Agreement.Builder.newInstance()
    			.assignee("assignee")
    			.assigner("assigner")
    			.target("test_dataset")
    			.permission(Arrays.asList(permission))
    			.build();
    	agreementRepository.save(agreement);
    	
    	// finalized contract negotiation
    	ContractNegotiation contractNegotiationFinalized = ContractNegotiation.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.callbackAddress("callbackAddress.test")
    			.agreement(agreement)
    			.state(ContractNegotiationState.FINALIZED)
    			.role("consumer")
    			.build();
    	contractNegotiationRepository.save(contractNegotiationFinalized);
		
    	TransferProcess transferProcessInitialized = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.agreementId(agreement.getId())
    			.state(TransferState.INITIALIZED)
    			.datasetId(datasetId)
    			.build();
    	transferProcessRepository.save(transferProcessInitialized);
    	
		TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
	    		.consumerPid(createNewId())
	    		.agreementId(agreement.getId())
	    		.format(DataTransferFormat.HTTP_PULL.format())
	    		.callbackAddress(DataTranferMockObjectUtil.CALLBACK_ADDRESS)
	    		.build();
		
    	final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/request")
    					.content(TransferSerializer.serializeProtocol(transferRequestMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isCreated())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
       	String response = result.andReturn().getResponse().getContentAsString();
    	TransferProcess transferProcessRequested = TransferSerializer.deserializeProtocol(response, TransferProcess.class);
    	assertNotNull(transferProcessRequested);
    	assertEquals(TransferState.REQUESTED, transferProcessRequested.getState());
    	
    	agreementRepository.delete(agreement);
    	contractNegotiationRepository.delete(contractNegotiationFinalized);
    	transferProcessRepository.deleteById(transferProcessInitialized.getId());
    }
	
	@Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void initiateDataTransfer_already_requested() throws Exception {
		TransferProcess transferProcessRequested = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.agreementId(createNewId())
    			.state(TransferState.REQUESTED)
    			.datasetId(datasetId)
    			.build();
    	transferProcessRepository.save(transferProcessRequested);
    	
    	TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
	    		.consumerPid(createNewId())
	    		.agreementId(transferProcessRequested.getAgreementId())
	    		.format(DataTransferFormat.HTTP_PULL.format())
	    		.callbackAddress(DataTranferMockObjectUtil.CALLBACK_ADDRESS)
	    		.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/request")
    					.content(TransferSerializer.serializeProtocol(transferRequestMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
       	String response = result.andReturn().getResponse().getContentAsString();
      	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
      	assertNotNull(transferError);
	}
	
	@Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void initiateDataTransfer_no_agreement() throws Exception {
		TransferProcess transferProcessRequested = TransferProcess.Builder.newInstance()
    			.consumerPid(createNewId())
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.agreementId(createNewId())
    			.state(TransferState.INITIALIZED)
    			.datasetId(datasetId)
    			.build();
    	transferProcessRepository.save(transferProcessRequested);
    	
    	TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
	    		.consumerPid(createNewId())
	    		.agreementId("different_agreement_id")
	    		.format(DataTransferFormat.HTTP_PULL.format())
	    		.callbackAddress(DataTranferMockObjectUtil.CALLBACK_ADDRESS)
	    		.build();
    	
    	final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/request")
    					.content(TransferSerializer.serializeProtocol(transferRequestMessage))
    					.contentType(MediaType.APPLICATION_JSON));
    	result.andExpect(status().isBadRequest())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
       	String response = result.andReturn().getResponse().getContentAsString();
      	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
      	assertNotNull(transferError);
	}
	
	@Test
    @DisplayName("Start transfer - unauthorized")
    public void getCatalog_UnauthorizedTest() throws Exception {
    	
		TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
	    		.consumerPid(DataTranferMockObjectUtil.CONSUMER_PID)
	    		.agreementId(createNewId()) 
	    		.format(DataTransferFormat.HTTP_PULL.format())
	    		.callbackAddress(DataTranferMockObjectUtil.CALLBACK_ADDRESS)
	    		.build();
		
    	final ResultActions result =
    			mockMvc.perform(
    					post("/transfers/request")
    					.content(TransferSerializer.serializeProtocol(transferRequestMessage))
    					.contentType(MediaType.APPLICATION_JSON)
    					.header("Authorization", "Basic YXNkckBtYWlsLmNvbTpwYXNzd29yZA=="));
    	result.andExpect(status().isUnauthorized())
    	.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	String response = result.andReturn().getResponse().getContentAsString();
    	TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
      	assertNotNull(transferError);
    }
}
