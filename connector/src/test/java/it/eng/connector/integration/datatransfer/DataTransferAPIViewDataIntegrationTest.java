package it.eng.connector.integration.datatransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;
import org.wiremock.spring.InjectWireMock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;

import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.model.Permission;
import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;

public class DataTransferAPIViewDataIntegrationTest extends BaseIntegrationTest{
	
	@InjectWireMock 
	private WireMockServer wiremock;
	
	@Autowired
	private TransferProcessRepository transferProcessRepository;
	
	@Autowired
	private AgreementRepository agreementRepository;
	
	@Autowired
	private PolicyEnforcementRepository policyEnforcementRepository;
	
	@Autowired
	private MongoTemplate mongoTemplate;
	
	private static final String CONTENT_TYPE_FIELD = "_contentType";
	private static final String DATASET_ID_METADATA = "datasetId";
	private static final String FILE_NAME = "hello.txt";
	
	@AfterEach
	public void cleanup() {
		transferProcessRepository.deleteAll();
		agreementRepository.deleteAll();
		policyEnforcementRepository.deleteAll();
	}

	@Test
	@DisplayName("View data - success")
    @WithUserDetails(TestUtil.API_USER)
	public void viewData_success() throws Exception {
		String datasetId = createNewId();
		String fileContent = "Hello, World!";
		
		GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
		Document doc = new Document();
		//TODO check what happens if file.getContentType() is null
		doc.append(CONTENT_TYPE_FIELD, MediaType.TEXT_PLAIN_VALUE);
		doc.append(DATASET_ID_METADATA, datasetId);
		GridFSUploadOptions options = new GridFSUploadOptions()
				.chunkSizeBytes(1048576) // 1MB chunk size
				.metadata(doc);
		ObjectId fileId = gridFSBucket.uploadFromStream(FILE_NAME, new ByteArrayInputStream(fileContent.getBytes()), options);
		
		Agreement agreement = Agreement.Builder.newInstance()
				.id(createNewId())
				.assignee(NegotiationMockObjectUtil.ASSIGNEE)
				.assigner(NegotiationMockObjectUtil.ASSIGNER)
				.target(NegotiationMockObjectUtil.TARGET)
				.timestamp(Instant.now().toString())
				.permission(Arrays.asList(Permission.Builder.newInstance()
						.action(Action.USE)
						.constraint(Arrays.asList(Constraint.Builder.newInstance()
								.leftOperand(LeftOperand.COUNT)
								.operator(Operator.LTEQ)
								.rightOperand("5")
								.build()))
						.build()))
				.build();
		
		agreementRepository.save(agreement);
		
		PolicyEnforcement policyEnforcement = new PolicyEnforcement(createNewId(), agreement.getId(), 0);
		
		policyEnforcementRepository.save(policyEnforcement);
		
		String consumerPid = createNewId();
		String providerPid = createNewId();
				
		TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.agreementId(agreement.getId())
				.callbackAddress(wiremock.baseUrl())
				.isDownloaded(true)
				.dataId(fileId.toHexString())
				.state(TransferState.STARTED)
				.build();
		transferProcessRepository.save(transferProcessStarted);
		
		// send request
    	final ResultActions result =
    			mockMvc.perform(
    					get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId() + "/view")
    					.contentType(MediaType.APPLICATION_JSON));
    	
    	result.andExpect(status().isOk())
    		.andExpect(content().contentType(MediaType.TEXT_PLAIN));
    	
		
		String response = result.andReturn().getResponse().getContentAsString();
    	
		assertEquals("attachment;filename=" + FILE_NAME, result.andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
		assertEquals(response, fileContent);
		
		
		// check if the TransferProcess is inserted in the database
		TransferProcess transferProcessFromDb = transferProcessRepository.findById(transferProcessStarted.getId()).get();

		assertTrue(transferProcessFromDb.isDownloaded());
		assertNotNull(transferProcessFromDb.getDataId());
		assertEquals(transferProcessStarted.getConsumerPid(), transferProcessFromDb.getConsumerPid());
		assertEquals(transferProcessStarted.getProviderPid(), transferProcessFromDb.getProviderPid());
		assertEquals(transferProcessStarted.getAgreementId(), transferProcessFromDb.getAgreementId());
		assertEquals(transferProcessStarted.getCallbackAddress(), transferProcessFromDb.getCallbackAddress());
		assertEquals(transferProcessStarted.getState(), transferProcessFromDb.getState());
		
		// check if the PolicyEnforcement count is increased
		// waiting for 1 second to give time to the publisher to increase the policy access count
		TimeUnit.SECONDS.sleep(1);
		PolicyEnforcement enforcementFromDb = policyEnforcementRepository.findById(policyEnforcement.getId()).get();
		
		assertEquals(policyEnforcement.getCount() + 1, enforcementFromDb.getCount());
		
		ObjectId objectId = new ObjectId(transferProcessFromDb.getDataId());
		gridFSBucket.delete(objectId);
    }

	@Test
	@DisplayName("View data - fail policy expired")
    @WithUserDetails(TestUtil.API_USER)
	public void viewData_failPolicyExpired() throws Exception {
		Agreement agreement = Agreement.Builder.newInstance()
				.id(createNewId())
				.assignee(NegotiationMockObjectUtil.ASSIGNEE)
				.assigner(NegotiationMockObjectUtil.ASSIGNER)
				.target(NegotiationMockObjectUtil.TARGET)
				.timestamp(Instant.now().toString())
				.permission(Arrays.asList(Permission.Builder.newInstance()
						.action(Action.USE)
						.constraint(Arrays.asList(Constraint.Builder.newInstance()
								.leftOperand(LeftOperand.COUNT)
								.operator(Operator.LTEQ)
								.rightOperand("5")
								.build()))
						.build()))
				.build();
		
		agreementRepository.save(agreement);
		
		PolicyEnforcement policyEnforcement = new PolicyEnforcement(createNewId(), agreement.getId(), 6);
		
		policyEnforcementRepository.save(policyEnforcement);
		
		String consumerPid = createNewId();
		String providerPid = createNewId();
				
		TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.agreementId(agreement.getId())
				.callbackAddress(wiremock.baseUrl())
				.isDownloaded(true)
				.state(TransferState.STARTED)
				.build();
		transferProcessRepository.save(transferProcessStarted);
		
		// send request
    	final ResultActions result =
    			mockMvc.perform(
    					get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId() + "/view")
    					.contentType(MediaType.APPLICATION_JSON));
    	
    	result.andExpect(status().isBadRequest())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
    	TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {};
		
		String json = result.andReturn().getResponse().getContentAsString();
		GenericApiResponse<String> apiResp =  CatalogSerializer.deserializePlain(json, typeRef);
    	
		assertNotNull(apiResp);
		assertFalse(apiResp.isSuccess());
		assertNull(apiResp.getData());
    }

}
