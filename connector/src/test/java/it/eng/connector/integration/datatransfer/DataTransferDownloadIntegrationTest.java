package it.eng.connector.integration.datatransfer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;
import org.wiremock.spring.InjectWireMock;

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

	// from initial_data
	private String datasetId = "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5";
	
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
	
	@Test
	@DisplayName("Download artifact file")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void downloadArtifactFile() throws Exception {
		
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
				.datasetId(datasetId)
				.build();
		transferProcessRepository.save(transferProcessStarted);
    	
		String transactionId = Base64.getEncoder().encodeToString((transferProcessStarted.getConsumerPid() + "|" + transferProcessStarted.getProviderPid())
				.getBytes(Charset.forName("UTF-8")));
		
		MvcResult resultArtifact = mockMvc.perform(get("/artifacts/" + transactionId)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andReturn();
		String artifact = resultArtifact.getResponse().getContentAsString();
		assertTrue(artifact.contains("John"));
		assertTrue(artifact.contains("Doe"));
		
		transferProcessRepository.deleteById(transferProcessStarted.getId());
		agreementRepository.delete(agreement);
		policyEnforcementRepository.deleteById(policyEnforcement.getId());
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
		
		artifactRepository.deleteById(artifact.getId());
		datasetRepository.deleteById(dataset.getId());
		transferProcessRepository.deleteById(transferProcessStarted.getId());
		agreementRepository.deleteById(agreement.getId());
		policyEnforcementRepository.deleteById(policyEnforcement.getId());
	}
	
	@Test
	@DisplayName("Download artifact - process not started")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void downloadArtifact_fail_not_started() throws Exception {
		
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
				.datasetId(datasetId)
				.build();
		transferProcessRepository.save(transferProcessStarted);
    	
		String transactionId = Base64.getEncoder().encodeToString((transferProcessStarted.getConsumerPid() + "|" + transferProcessStarted.getProviderPid())
				.getBytes(Charset.forName("UTF-8")));
		
		mockMvc.perform(get("/artifacts/" + transactionId)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isPreconditionFailed());
		
		transferProcessRepository.deleteById(transferProcessStarted.getId());
		agreementRepository.delete(agreement);
		policyEnforcementRepository.deleteById(policyEnforcement.getId());
	}
	
	@Test
	@DisplayName("Download artifact - enforcmenet failed")
	@WithUserDetails(TestUtil.CONNECTOR_USER)
	public void downloadArtifact_fail_enforcement_failed() throws Exception {
		
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
				.datasetId(datasetId)
				.build();
		transferProcessRepository.save(transferProcessStarted);
    	
		String transactionId = Base64.getEncoder().encodeToString((transferProcessStarted.getConsumerPid() + "|" + transferProcessStarted.getProviderPid())
				.getBytes(Charset.forName("UTF-8")));
		
		mockMvc.perform(get("/artifacts/" + transactionId)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isPreconditionFailed());
		
		transferProcessRepository.deleteById(transferProcessStarted.getId());
		agreementRepository.delete(agreement);
		policyEnforcementRepository.deleteById(policyEnforcement.getId());
	}
}
