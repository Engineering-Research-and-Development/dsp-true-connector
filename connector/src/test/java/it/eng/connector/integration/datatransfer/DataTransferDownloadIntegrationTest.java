package it.eng.connector.integration.datatransfer;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.negotiation.model.*;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.util.ToolsUtil;
import okhttp3.Credentials;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.wiremock.spring.InjectWireMock;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DataTransferDownloadIntegrationTest extends BaseIntegrationTest {

	@InjectWireMock 
	private WireMockServer wiremock;
	
	@Autowired
	private ArtifactRepository artifactRepository;
	@Autowired
	private DatasetRepository datasetRepository;
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
		datasetRepository.deleteAll();
		artifactRepository.deleteAll();
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
	@DisplayName("Download artifact file")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void downloadArtifactFile() throws Exception {
		String fileContent = "Hello, World!";
		String datasetId = createNewId();
		
		MockMultipartFile file 
			= new MockMultipartFile(
				"file", 
				"hello.txt", 
				MediaType.TEXT_PLAIN_VALUE, 
				fileContent.getBytes()
				);

		// insert the file in S3
		if (!s3ClientService.bucketExists(s3Properties.getBucketName())) {
			s3ClientService.createBucket(s3Properties.getBucketName());
		}

		ContentDisposition contentDisposition = ContentDisposition.attachment()
				.filename(file.getOriginalFilename())
				.build();

		String fileId = ToolsUtil.generateUniqueId();
//		try {
//			s3ClientService.uploadFile(s3Properties.getBucketName(), fileId, file.getBytes(),
//					file.getContentType(), contentDisposition.toString());
//		} catch (Exception e) {
//			throw new Exception("File storing aborted, " + e.getLocalizedMessage());
//		}

		Artifact artifact = Artifact.Builder.newInstance()
				.artifactType(ArtifactType.FILE)
				.filename(file.getOriginalFilename())
				.value(fileId)
				.build();
		artifactRepository.save(artifact);
		Dataset dataset = Dataset.Builder.newInstance()
				.id(datasetId)
				.hasPolicy(Collections.singleton(CatalogMockObjectUtil.OFFER))
				.artifact(artifact)
				.build();
		datasetRepository.save(dataset);

		Permission permission = Permission.Builder.newInstance()
    			.action(Action.USE)
    			.constraint(Arrays.asList(Constraint.Builder.newInstance()
    					.leftOperand(LeftOperand.COUNT)
    					.operator(Operator.LTEQ)
    					.rightOperand("5")
    					.build()))
    			.build();

		// Agreement valid
		Agreement agreement = Agreement.Builder.newInstance()
    			.assignee("assignee")
    			.assigner("assigner")
    			.target("test_dataset")
    			.permission(Arrays.asList(permission))
    			.build();
    	agreementRepository.save(agreement);

    	PolicyEnforcement policyEnforcement = new PolicyEnforcement(createNewId(), agreement.getId(), 0);
    	policyEnforcementRepository.save(policyEnforcement);

		// TransferProcess started
		TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.agreementId(agreement.getId())
				.state(TransferState.STARTED)
				.datasetId(dataset.getId())
				.build();
		transferProcessRepository.save(transferProcessStarted);

		String transactionId = Base64.getEncoder().encodeToString((transferProcessStarted.getConsumerPid() + "|" + transferProcessStarted.getProviderPid())
				.getBytes(StandardCharsets.UTF_8));
		
		MvcResult resultArtifact = mockMvc.perform(get("/artifacts/" + transactionId)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.TEXT_PLAIN_VALUE))
				.andReturn();
		String artifactResponse = resultArtifact.getResponse().getContentAsString();
		assertTrue(artifactResponse.contains(fileContent));
	}
	
	@Test
	@DisplayName("Download artifact external")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void downloadArtifactExternal() throws Exception {
		
		String mockUser = "mockUser";
		String mockPassword = "mockPassword";
		String mockAddress = wiremock.baseUrl() + "/helloworld";
		
		Artifact artifact = Artifact.Builder.newInstance()
				.artifactType(ArtifactType.EXTERNAL)
				.createdBy(CatalogMockObjectUtil.CREATOR)
				.created(CatalogMockObjectUtil.NOW)
				.lastModifiedDate(CatalogMockObjectUtil.NOW)
				.lastModifiedBy(CatalogMockObjectUtil.CREATOR)
				.value(mockAddress)
				.authorization(Credentials.basic(mockUser, mockPassword))
				.build();
		
		artifactRepository.save(artifact);
		
		Dataset dataset = Dataset.Builder.newInstance()
				.hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
				.artifact(artifact)
				.build();
		
		datasetRepository.save(dataset);
		
		Permission permission = Permission.Builder.newInstance()
    			.action(Action.USE)
    			.constraint(Arrays.asList(Constraint.Builder.newInstance()
    					.leftOperand(LeftOperand.COUNT)
    					.operator(Operator.LTEQ)
    					.rightOperand("5")
    					.build()))
    			.build();
		
		// Agreement valid
		Agreement agreement = Agreement.Builder.newInstance()
    			.assignee("assignee")
    			.assigner("assigner")
    			.target("test_dataset")
    			.permission(Arrays.asList(permission))
    			.build();
    	agreementRepository.save(agreement);
    	
    	PolicyEnforcement policyEnforcement = new PolicyEnforcement(createNewId(), agreement.getId(), 0);
    	policyEnforcementRepository.save(policyEnforcement);
    	
		// TransferProcess started
		TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.agreementId(agreement.getId())
				.state(TransferState.STARTED)
				.datasetId(dataset.getId())
				.build();
		transferProcessRepository.save(transferProcessStarted);
    	
		String transactionId = Base64.getEncoder().encodeToString((transferProcessStarted.getConsumerPid() + "|" + transferProcessStarted.getProviderPid())
				.getBytes(StandardCharsets.UTF_8));
		
		// mock provider success response Download
		String fileContent = "Hello, World!";
		String fileName = "helloworld.txt";
		
		
		WireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/helloworld")
				.withBasicAuth(mockUser, mockPassword)
				.willReturn(
	                aResponse().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
	                .withHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + fileName)
	                .withBody(fileContent.getBytes())));
		
		MvcResult resultArtifact = mockMvc.perform(get("/artifacts/" + transactionId)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.TEXT_PLAIN))
				.andReturn();
		String response = resultArtifact.getResponse().getContentAsString();
		assertTrue(StringUtils.equals(fileContent, response));
	}
	
	@Test
	@DisplayName("Download artifact - process not started")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void downloadArtifact_fail_not_started() throws Exception {
		Dataset dataset = Dataset.Builder.newInstance()
				.hasPolicy(Collections.singleton(CatalogMockObjectUtil.OFFER))
				.build();
		datasetRepository.save(dataset);
		
		Permission permission = Permission.Builder.newInstance()
    			.action(Action.USE)
    			.constraint(Arrays.asList(Constraint.Builder.newInstance()
    					.leftOperand(LeftOperand.COUNT)
    					.operator(Operator.LTEQ)
    					.rightOperand("5")
    					.build()))
    			.build();
		
		// Agreement valid
		Agreement agreement = Agreement.Builder.newInstance()
    			.assignee("assignee")
    			.assigner("assigner")
    			.target("test_dataset")
    			.permission(Arrays.asList(permission))
    			.build();
    	agreementRepository.save(agreement);
    	
    	PolicyEnforcement policyEnforcement = new PolicyEnforcement(createNewId(), agreement.getId(), 0);
    	policyEnforcementRepository.save(policyEnforcement);
    	
		// TransferProcess started
		TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.agreementId(agreement.getId())
				.state(TransferState.REQUESTED)
				.datasetId(dataset.getId())
				.build();
		transferProcessRepository.save(transferProcessStarted);
    	
		String transactionId = Base64.getEncoder().encodeToString((transferProcessStarted.getConsumerPid() + "|" + transferProcessStarted.getProviderPid())
				.getBytes(StandardCharsets.UTF_8));
		
		mockMvc.perform(get("/artifacts/" + transactionId)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isPreconditionFailed());
	}
	
	@Test
	@DisplayName("Download artifact - enforcmenet failed")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void downloadArtifact_fail_enforcement_failed() throws Exception {
		Dataset dataset = Dataset.Builder.newInstance()
				.hasPolicy(Collections.singleton(CatalogMockObjectUtil.OFFER))
				.build();
		datasetRepository.save(dataset);
		Permission permission = Permission.Builder.newInstance()
    			.action(Action.USE)
    			.constraint(Arrays.asList(Constraint.Builder.newInstance()
    					.leftOperand(LeftOperand.COUNT)
    					.operator(Operator.LTEQ)
    					.rightOperand("5")
    					.build()))
    			.build();
		
		// Agreement valid
		Agreement agreement = Agreement.Builder.newInstance()
    			.assignee("assignee")
    			.assigner("assigner")
    			.target("test_dataset")
    			.permission(Arrays.asList(permission))
    			.build();
    	agreementRepository.save(agreement);
    	
    	// simulate policy enforcement already over
    	PolicyEnforcement policyEnforcement = new PolicyEnforcement(createNewId(), agreement.getId(), 6);
    	policyEnforcementRepository.save(policyEnforcement);
    	
		// TransferProcess started
		TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
				.consumerPid(createNewId())
				.providerPid(createNewId())
				.agreementId(agreement.getId())
				.state(TransferState.REQUESTED)
				.datasetId(dataset.getId())
				.build();
		transferProcessRepository.save(transferProcessStarted);
    	
		String transactionId = Base64.getEncoder().encodeToString((transferProcessStarted.getConsumerPid() + "|" + transferProcessStarted.getProviderPid())
				.getBytes(StandardCharsets.UTF_8));
		
		mockMvc.perform(get("/artifacts/" + transactionId)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isPreconditionFailed());
	}
}
