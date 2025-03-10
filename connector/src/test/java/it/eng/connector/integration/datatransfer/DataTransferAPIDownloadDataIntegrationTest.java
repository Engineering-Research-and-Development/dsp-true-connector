package it.eng.connector.integration.datatransfer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;
import org.wiremock.spring.InjectWireMock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.Filters;

import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;
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
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import okhttp3.Credentials;

public class DataTransferAPIDownloadDataIntegrationTest extends BaseIntegrationTest{
	
	private static final String FILE_NAME = "hello.txt";

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
	
	@Test
	@DisplayName("Download data - success")
    @WithUserDetails(TestUtil.API_USER)
	public void downloadData_success() throws Exception {
		int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();
		long startingFilesCollectionSize = mongoTemplate.getCollection(FS_FILES).countDocuments();
		
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
				
		String transactionId = Base64.getEncoder().encodeToString((consumerPid + "|" + providerPid)
				.getBytes(Charset.forName("UTF-8")));
		
		String mockUser = "mockUser";
		String mockPassword = "mockPassword";
		
		EndpointProperty authType = EndpointProperty.Builder.newInstance()
				.name(IConstants.AUTH_TYPE)
				.value(IConstants.AUTH_BASIC)
				.build();
		
		EndpointProperty authorization = EndpointProperty.Builder.newInstance()
				.name(IConstants.AUTHORIZATION)
				.value(Credentials.basic(mockUser, mockPassword).replaceFirst(IConstants.AUTH_BASIC + " ", ""))
				.build();
		
		List<EndpointProperty> properties = List.of(authType, authorization);
		
		DataAddress dataAddress = DataAddress.Builder.newInstance()
				.endpoint(wiremock.baseUrl() + "/artifacts/" + transactionId)
				.endpointType(DataTranferMockObjectUtil.ENDPOINT_TYPE)
				.endpointProperties(properties)
				.build();
		
		TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.agreementId(agreement.getId())
				.callbackAddress(wiremock.baseUrl())
				.dataAddress(dataAddress)
				.state(TransferState.STARTED)
				.build();
		transferProcessRepository.save(transferProcessStarted);
		
		// mock provider success response Download
		String fileContent = "Hello, World!";
		
		
		WireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/artifacts/" + transactionId)
				.withBasicAuth(mockUser, mockPassword)
				.willReturn(
	                aResponse().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
	                .withHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + FILE_NAME)
	                .withBody(fileContent.getBytes())));
    	
		// send request
    	final ResultActions result =
    			mockMvc.perform(
    					get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId() + "/download")
    					.contentType(MediaType.APPLICATION_JSON));
    	
    	result.andExpect(status().isOk())
    		.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    	
    	TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {};
		
		String json = result.andReturn().getResponse().getContentAsString();
		GenericApiResponse<String> apiResp =  CatalogSerializer.deserializePlain(json, typeRef);
    	
		assertNotNull(apiResp);
		assertTrue(apiResp.isSuccess());
		assertNull(apiResp.getData());
		
		
		// check if the TransferProcess is inserted in the database
		TransferProcess transferProcessFromDb = transferProcessRepository.findById(transferProcessStarted.getId()).get();

		assertTrue(transferProcessFromDb.isDownloaded());
		assertNotNull(transferProcessFromDb.getDataId());
		assertEquals(transferProcessStarted.getConsumerPid(), transferProcessFromDb.getConsumerPid());
		assertEquals(transferProcessStarted.getProviderPid(), transferProcessFromDb.getProviderPid());
		assertEquals(transferProcessStarted.getAgreementId(), transferProcessFromDb.getAgreementId());
		assertEquals(transferProcessStarted.getCallbackAddress(), transferProcessFromDb.getCallbackAddress());
		assertEquals(transferProcessStarted.getState(), transferProcessFromDb.getState());
		// +1 from test
		assertEquals(startingTransferProcessCollectionSize + 1, transferProcessRepository.findAll().size());
		
		// check if the file is inserted in the database
		GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
		ObjectId fileIdentifier = new ObjectId(transferProcessFromDb.getDataId());
		Bson query = Filters.eq("_id", fileIdentifier);
		GridFSFile fileInDb = gridFSBucket.find(query).first();
		GridFSDownloadStream gridFSDownloadStream = gridFSBucket.openDownloadStream(fileInDb.getObjectId());
		GridFsResource gridFsResource = new GridFsResource(fileInDb, gridFSDownloadStream);
		
		assertEquals(MediaType.TEXT_PLAIN_VALUE, gridFsResource.getContentType());
		assertEquals(FILE_NAME, gridFsResource.getFilename());
		assertEquals(fileContent, gridFsResource.getContentAsString(StandardCharsets.UTF_8));
		// 1 from initial data + 1 from test
		assertEquals(startingFilesCollectionSize + 1, mongoTemplate.getCollection(FS_FILES).countDocuments());
		
		cleanup();
		ObjectId objectId = new ObjectId(transferProcessFromDb.getDataId());
		gridFSBucket.delete(objectId);
		
    }
	
	@Test
	@DisplayName("Download data - fail")
    @WithUserDetails(TestUtil.API_USER)
	public void downloadData_fail() throws Exception {
		int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();
		long startingFilesCollectionSize = mongoTemplate.getCollection(FS_FILES).countDocuments();
		
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
				
		String transactionId = Base64.getEncoder().encodeToString((consumerPid + "|" + providerPid)
				.getBytes(Charset.forName("UTF-8")));
		
		DataAddress dataAddress = DataAddress.Builder.newInstance()
				.endpoint(wiremock.baseUrl() + "/artifacts/" + transactionId)
				.endpointType(DataTranferMockObjectUtil.ENDPOINT_TYPE)
				.build();
		
		TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.agreementId(agreement.getId())
				.callbackAddress(wiremock.baseUrl())
				.dataAddress(dataAddress)
				.state(TransferState.STARTED)
				.build();
		transferProcessRepository.save(transferProcessStarted);
		
		// mock provider error response Download
		
		WireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/artifacts/" + transactionId)
				.willReturn(
	                aResponse().withStatus(400)));
    	
		// send request
    	final ResultActions result =
    			mockMvc.perform(
    					get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId() + "/download")
    					.contentType(MediaType.APPLICATION_JSON));
    	
    	result.andExpect(status().isBadRequest());
    	
    	TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {};
		
		String json = result.andReturn().getResponse().getContentAsString();
		GenericApiResponse<String> apiResp =  CatalogSerializer.deserializePlain(json, typeRef);
    	
		assertNotNull(apiResp);
		assertFalse(apiResp.isSuccess());
		assertNull(apiResp.getData());
		
		
		// check if the TransferProcess is inserted in the database
		TransferProcess transferProcessFromDb = transferProcessRepository.findById(transferProcessStarted.getId()).get();

		assertFalse(transferProcessFromDb.isDownloaded());
		assertNull(transferProcessFromDb.getDataId());
		assertEquals(transferProcessStarted.getConsumerPid(), transferProcessFromDb.getConsumerPid());
		assertEquals(transferProcessStarted.getProviderPid(), transferProcessFromDb.getProviderPid());
		assertEquals(transferProcessStarted.getAgreementId(), transferProcessFromDb.getAgreementId());
		assertEquals(transferProcessStarted.getCallbackAddress(), transferProcessFromDb.getCallbackAddress());
		assertEquals(transferProcessStarted.getState(), transferProcessFromDb.getState());
		// +1 from test
		assertEquals(startingTransferProcessCollectionSize + 1, transferProcessRepository.findAll().size());
		
		// check if the file is inserted in the database
		// 1 from initial data + 0 from test
		assertEquals(startingFilesCollectionSize, mongoTemplate.getCollection(FS_FILES).countDocuments());
		
		cleanup();
    }
	
	private void cleanup() {
		transferProcessRepository.deleteAll();
		agreementRepository.deleteAll();
		policyEnforcementRepository.deleteAll();
	}
}
