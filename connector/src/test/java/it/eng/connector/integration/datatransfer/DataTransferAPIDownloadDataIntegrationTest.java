package it.eng.connector.integration.datatransfer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;
import it.eng.negotiation.model.*;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import okhttp3.Credentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;
import org.wiremock.spring.InjectWireMock;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
	private S3ClientService s3ClientService;

	@Autowired
	private S3Properties s3Properties;

	@AfterEach
	public void cleanup() {
		transferProcessRepository.deleteAll();
		agreementRepository.deleteAll();
		policyEnforcementRepository.deleteAll();
		if (!s3ClientService.bucketExists(s3Properties.getBucketName())) {
			List<String> files = s3ClientService.listFiles(s3Properties.getBucketName());
			if (files != null) {
				for (String file : files) {
					s3ClientService.deleteFile(s3Properties.getBucketName(), file);
				}
			}
			s3ClientService.deleteBucket(s3Properties.getBucketName());
		}
	}

	@Test
	@DisplayName("Download data - success")
    @WithUserDetails(TestUtil.API_USER)
	public void downloadData_success() throws Exception {
		int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();
		int startingBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();
		
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
				.getBytes(StandardCharsets.UTF_8));
		
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
		
		// check if the file is inserted in the storage
		int endBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();

		ResponseBytes<GetObjectResponse> fileFromStorage = s3ClientService.downloadFile(s3Properties.getBucketName(), transferProcessStarted.getId());

		ContentDisposition contentDisposition = ContentDisposition.parse(fileFromStorage.response().contentDisposition());

		assertEquals(MediaType.TEXT_PLAIN_VALUE, fileFromStorage.response().contentType());
		assertEquals(FILE_NAME, contentDisposition.getFilename());
		assertEquals(fileContent, fileFromStorage.asUtf8String());
		// + 1 from test
		assertEquals(startingBucketFileCount + 1, endBucketFileCount);

    }
	
	@Test
	@DisplayName("Download data - fail")
    @WithUserDetails(TestUtil.API_USER)
	public void downloadData_fail() throws Exception {
		int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();
		int startingBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();
		
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
				.getBytes(StandardCharsets.UTF_8));
		
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
		int endBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();
		assertEquals(startingBucketFileCount, endBucketFileCount);
		
    }

	@Test
	@DisplayName("Download data - purpose policy - allowed")
	@WithUserDetails(TestUtil.API_USER)
	public void downloadData_PurposePolicy_allowed() throws Exception {
		int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();
		int startingBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();

		Constraint constraintPurpose = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.PURPOSE)
				.operator(Operator.EQ)
				// purpose must match with value from property file - demo
				.rightOperand("demo")
				.build();
		Agreement agreement = insertAgreement(constraintPurpose);

		String consumerPid = createNewId();
		String providerPid = createNewId();

		String transactionId = Base64.getEncoder()
				.encodeToString((consumerPid + "|" + providerPid).getBytes(StandardCharsets.UTF_8));

		// mock provider success response Download
		String fileContent = "Hello, World!";

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

		// + 1 from test
		assertEquals(startingTransferProcessCollectionSize + 1, transferProcessRepository.findAll().size());
		int endBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();
		assertEquals(startingBucketFileCount + 1, endBucketFileCount);

		TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {};

		String json = result.andReturn().getResponse().getContentAsString();
		GenericApiResponse<String> apiResp =  CatalogSerializer.deserializePlain(json, typeRef);

		assertNotNull(apiResp);
		assertTrue(apiResp.isSuccess());
		assertNull(apiResp.getData());
	}

	@Test
	@DisplayName("Download data - location policy - allowed")
	@WithUserDetails(TestUtil.API_USER)
	public void downloadData_LocationPolicy_allowed() throws Exception {
		int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();
		int startingBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();

		Constraint constraintPurpose = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.SPATIAL)
				.operator(Operator.EQ)
				// purpose must match with value from property file - EU
				.rightOperand("EU")
				.build();
		Agreement agreement = insertAgreement(constraintPurpose);

		String consumerPid = createNewId();
		String providerPid = createNewId();

		String transactionId = Base64.getEncoder()
				.encodeToString((consumerPid + "|" + providerPid).getBytes(StandardCharsets.UTF_8));

		// mock provider success response Download
		String fileContent = "Hello, World!";

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

		// + 1 from test
		assertEquals(startingTransferProcessCollectionSize + 1, transferProcessRepository.findAll().size());
		int endBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();
		assertEquals(startingBucketFileCount + 1, endBucketFileCount);

		TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {};

		String json = result.andReturn().getResponse().getContentAsString();
		GenericApiResponse<String> apiResp =  CatalogSerializer.deserializePlain(json, typeRef);

		assertNotNull(apiResp);
		assertTrue(apiResp.isSuccess());
		assertNull(apiResp.getData());
	}

	private Agreement insertAgreement(Constraint constraint) {
		Agreement agreement = Agreement.Builder.newInstance()
				.id(createNewId())
				.assignee(NegotiationMockObjectUtil.ASSIGNEE)
				.assigner(NegotiationMockObjectUtil.ASSIGNER)
				.target(NegotiationMockObjectUtil.TARGET)
				.timestamp(Instant.now().toString())
				.permission(Arrays.asList(Permission.Builder.newInstance()
						.action(Action.USE)
						.constraint(Arrays.asList(constraint))
						.build()))
				.build();

		agreementRepository.save(agreement);
		return agreement;
	}
}
