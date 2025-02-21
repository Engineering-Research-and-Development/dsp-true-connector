package it.eng.connector.integration.datatransfer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.wiremock.spring.InjectWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;

import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.model.Permission;
import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.repository.ArtifactRepository;
import okhttp3.Credentials;

public class DataTransferDownloadIntegrationTest extends BaseIntegrationTest {
	private static final String CONTENT_TYPE_FIELD = "_contentType";
	private static final String DATASET_ID_METADATA = "datasetId";
	
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
	private MongoTemplate mongoTemplate;
	
	@AfterEach
	public void cleanup() {
		datasetRepository.deleteAll();
		artifactRepository.deleteAll();
		transferProcessRepository.deleteAll();
		agreementRepository.deleteAll();
		policyEnforcementRepository.deleteAll();
		mongoTemplate.getCollection(FS_FILES).drop();
		mongoTemplate.getCollection(FS_CHUNKS).drop();
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
		
		GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
		Document doc = new Document();
		//TODO check what happens if file.getContentType() is null
		doc.append(CONTENT_TYPE_FIELD, file.getContentType());
		doc.append(DATASET_ID_METADATA, datasetId);
		GridFSUploadOptions options = new GridFSUploadOptions()
				.chunkSizeBytes(1048576) // 1MB chunk size
				.metadata(doc);
		ObjectId fileId = gridFSBucket.uploadFromStream(file.getOriginalFilename(), file.getInputStream(), options);
		
		Artifact artifact = Artifact.Builder.newInstance()
				.artifactType(ArtifactType.FILE)
				.filename(file.getOriginalFilename())
				.value(fileId.toHexString())
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
				.getBytes(Charset.forName("UTF-8")));
		
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
				.getBytes(Charset.forName("UTF-8")));
		
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
				.getBytes(Charset.forName("UTF-8")));
		
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
				.getBytes(Charset.forName("UTF-8")));
		
		mockMvc.perform(get("/artifacts/" + transactionId)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isPreconditionFailed());
	}
}
