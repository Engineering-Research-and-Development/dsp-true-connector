package it.eng.connector.integration.datatransfer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.WireMockServer;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.negotiation.model.*;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;
import org.wiremock.spring.InjectWireMock;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
	private S3ClientService s3ClientService;

	@Autowired
	private S3Properties s3Properties;

	private static final String FILE_NAME = "hello.txt";

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

	@ParameterizedTest
	@MethodSource("getValidConstraints")
	@DisplayName("View data - success")
    @WithUserDetails(TestUtil.API_USER)
	public void viewData_success(Constraint constraint) throws Exception {
		String fileContent = "Hello, World!";

		Agreement agreement = insertAgreement(constraint, 0);

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

		// insert the file in S3
		if (!s3ClientService.bucketExists(s3Properties.getBucketName())) {
			s3ClientService.createBucket(s3Properties.getBucketName());
		}

		ContentDisposition contentDisposition = ContentDisposition.attachment()
				.filename(FILE_NAME)
				.build();

		try {
			s3ClientService.uploadFile(s3Properties.getBucketName(), transferProcessStarted.getId(), fileContent.getBytes(),
					MediaType.TEXT_PLAIN_VALUE, contentDisposition.toString());
		} catch (Exception e) {
			throw new Exception("File storing aborted, " + e.getLocalizedMessage());
		}
		// send request
		final ResultActions result =
    			mockMvc.perform(
    					get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId() + "/view")
    					.contentType(MediaType.APPLICATION_JSON));

		result.andExpect(status().isOk())
    		.andExpect(content().contentType(MediaType.TEXT_PLAIN));


		String response = result.andReturn().getResponse().getContentAsString();
    	
		assertEquals(contentDisposition.toString(), result.andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
		assertEquals(response, fileContent);
		
		
		// check if the TransferProcess is inserted in the database
		TransferProcess transferProcessFromDb = transferProcessRepository.findById(transferProcessStarted.getId()).get();

		assertTrue(transferProcessFromDb.isDownloaded());
		assertEquals(transferProcessStarted.getConsumerPid(), transferProcessFromDb.getConsumerPid());
		assertEquals(transferProcessStarted.getProviderPid(), transferProcessFromDb.getProviderPid());
		assertEquals(transferProcessStarted.getAgreementId(), transferProcessFromDb.getAgreementId());
		assertEquals(transferProcessStarted.getCallbackAddress(), transferProcessFromDb.getCallbackAddress());
		assertEquals(transferProcessStarted.getState(), transferProcessFromDb.getState());
		
		// check if the PolicyEnforcement count is increased
		// waiting for 1 second to give time to the publisher to increase the policy access count
		TimeUnit.SECONDS.sleep(1);
		PolicyEnforcement enforcementFromDb = policyEnforcementRepository.findByAgreementId(agreement.getId()).get();
		// increase count from initial 0 to 1
		assertEquals(1, enforcementFromDb.getCount());
		
    }

	@ParameterizedTest
	@MethodSource("getInvalidConstraints")
	@DisplayName("View data - fail policy invalid")
	@WithUserDetails(TestUtil.API_USER)
	public void viewData_fail_policyInvalid(Constraint constraint) throws Exception {
		Agreement agreement = insertAgreement(constraint, 6);
		
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

	private static Stream<Constraint> getValidConstraints() {
		return Stream.of(NegotiationMockObjectUtil.CONSTRAINT, NegotiationMockObjectUtil.CONSTRAINT_COUNT_5, NegotiationMockObjectUtil.CONSTRAINT_PURPOSE,
				NegotiationMockObjectUtil.CONSTRAINT_SPATIAL);
	}

	private static Stream<Constraint> getInvalidConstraints() {
		Constraint constraintPurpose = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.PURPOSE)
				.operator(Operator.EQ)
				.rightOperand("test")
				.build();

		Constraint constraintSpatial = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.SPATIAL)
				.operator(Operator.EQ)
				.rightOperand("USA")
				.build();

		return Stream.of(NegotiationMockObjectUtil.CONSTRAINT_DATEIME_INVALID, NegotiationMockObjectUtil.CONSTRAINT_COUNT_5,
				constraintPurpose, constraintSpatial);
	}

	private Agreement insertAgreement(Constraint constraint, int currentCount) {
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

		PolicyEnforcement policyEnforcement = new PolicyEnforcement(createNewId(), agreement.getId(), currentCount);
		policyEnforcementRepository.save(policyEnforcement);

		return agreement;
	}

}
