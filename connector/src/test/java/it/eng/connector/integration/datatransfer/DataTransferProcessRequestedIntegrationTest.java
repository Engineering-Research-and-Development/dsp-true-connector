package it.eng.connector.integration.datatransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.model.Reference;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
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
import it.eng.tools.model.IConstants;

public class DataTransferProcessRequestedIntegrationTest extends BaseIntegrationTest {
// Consumer -> REQUESTED
	
	@Autowired
	private AgreementRepository agreementRepository;
	@Autowired
	private ContractNegotiationRepository contractNegotiationRepository;
	@Autowired
	private TransferProcessRepository transferProcessRepository;
	
	@Autowired
	private CatalogRepository catalogRepository;
	@Autowired
	private DatasetRepository datasetRepository;
	@Autowired
	private DistributionRepository distributionRepository;
	
	private Catalog catalog;
	private Dataset dataset;
	private Distribution distribution;
	
	@BeforeEach
	public void populateCatalog() {
		distribution = Distribution.Builder.newInstance()
				.format(Reference.Builder.newInstance().id(DataTransferFormat.HTTP_PULL.format()).build())
				.accessService(Collections.singleton(CatalogMockObjectUtil.DATA_SERVICE))
				.build();
		dataset = Dataset.Builder.newInstance()
				.hasPolicy(Collections.singleton(CatalogMockObjectUtil.OFFER))
				.distribution(Collections.singleton(distribution))
				.build();
		catalog = Catalog.Builder.newInstance()
				.dataset(Collections.singleton(dataset))
				.build();
		
		distributionRepository.save(distribution);
		datasetRepository.save(dataset);
		catalogRepository.save(catalog);
	}
	
	@AfterEach
	public void cleanup() {
		distributionRepository.deleteAll();
		datasetRepository.deleteAll();
		catalogRepository.deleteAll();
		
		agreementRepository.deleteAll();
		contractNegotiationRepository.deleteAll();
		transferProcessRepository.deleteAll();
	}
	
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
    			.role(IConstants.ROLE_PROVIDER)
    			.build();
    	contractNegotiationRepository.save(contractNegotiationFinalized);
    	
    	TransferProcess transferProcessInitialized = TransferProcess.Builder.newInstance()
    			.consumerPid(IConstants.TEMPORARY_CONSUMER_PID)
    			.providerPid(createNewId())
    			.format(DataTransferFormat.HTTP_PULL.format())
    			.agreementId(agreement.getId())
    			.state(TransferState.INITIALIZED)
    			.datasetId(dataset.getId())
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
    	
    	// check if the Transfer Process is properly inserted and that consumerPid and providerPid are correct
    	TransferProcess transferProcessFromDb = transferProcessRepository.findById(transferProcessInitialized.getId()).get();
    	
    	assertEquals(transferProcessInitialized.getProviderPid(), transferProcessFromDb.getProviderPid());
    	assertEquals(transferRequestMessage.getConsumerPid(), transferProcessFromDb.getConsumerPid());
    	assertEquals(TransferState.REQUESTED, transferProcessFromDb.getState());
    	
    	// cleanup
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
    			.datasetId(dataset.getId())
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
      	
      	// cleanup
    	transferProcessRepository.deleteById(transferProcessRequested.getId());
	}
	
	@Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void initiateDataTransfer_wrongDatasetFormat() throws Exception {
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
		    			.role(IConstants.ROLE_PROVIDER)
		    			.build();
		    	contractNegotiationRepository.save(contractNegotiationFinalized);
				
		    	TransferProcess transferProcessInitialized = TransferProcess.Builder.newInstance()
		    			.consumerPid(IConstants.TEMPORARY_CONSUMER_PID)
		    			.providerPid(createNewId())
		    			.format(DataTransferFormat.HTTP_PULL.format())
		    			.agreementId(agreement.getId())
		    			.state(TransferState.INITIALIZED)
		    			.datasetId(dataset.getId())
		    			.build();
		    	transferProcessRepository.save(transferProcessInitialized);
		    	
				TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
			    		.consumerPid(createNewId())
			    		.agreementId(agreement.getId())
			    		.format("some_format")
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
      	
      	// check if the Transfer Process is unchanged and that consumerPid and providerPid are correct
    	TransferProcess transferProcessFromDb = transferProcessRepository.findById(transferProcessInitialized.getId()).get();
    	
    	assertEquals(transferProcessInitialized.getProviderPid(), transferProcessFromDb.getProviderPid());
    	assertNotEquals(transferRequestMessage.getConsumerPid(), transferProcessFromDb.getConsumerPid());
    	assertEquals(transferProcessInitialized.getConsumerPid(), transferProcessFromDb.getConsumerPid());
    	assertEquals(TransferState.INITIALIZED, transferProcessFromDb.getState());
      	
      	// cleanup
    	agreementRepository.delete(agreement);
    	contractNegotiationRepository.delete(contractNegotiationFinalized);
    	transferProcessRepository.deleteById(transferProcessInitialized.getId());
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
    			.datasetId(dataset.getId())
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
      	
      	// cleanup
    	transferProcessRepository.deleteById(transferProcessRequested.getId());
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
