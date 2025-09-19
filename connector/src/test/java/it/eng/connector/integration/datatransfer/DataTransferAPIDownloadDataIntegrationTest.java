package it.eng.connector.integration.datatransfer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.DatasetService;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.negotiation.model.*;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.BucketCredentialsService;
import it.eng.tools.s3.service.S3BucketProvisionService;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.wiremock.spring.InjectWireMock;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DataTransferAPIDownloadDataIntegrationTest extends BaseIntegrationTest {

    private static final String FILE_NAME = "hello.txt";
    private final String fileContent = "Hello, World!";

    @InjectWireMock
    private WireMockServer wiremock;

    @Autowired
    private TransferProcessRepository transferProcessRepository;

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private ContractNegotiationRepository contractNegotiationRepository;

    @Autowired
    private PolicyEnforcementRepository policyEnforcementRepository;

    @Autowired
    private CatalogRepository catalogRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private S3ClientService s3ClientService;

    @Autowired
    private S3BucketProvisionService s3BucketProvisionService;

    @Autowired
    private S3Properties s3Properties;

    @Autowired
    private BucketCredentialsService bucketCredentialsService;

    private Dataset mockDataset;

    @BeforeEach
    public void cleanup() {
        transferProcessRepository.deleteAll();
        agreementRepository.deleteAll();
        contractNegotiationRepository.deleteAll();
        policyEnforcementRepository.deleteAll();
        datasetRepository.deleteAll();
        catalogRepository.deleteAll();
        if (s3BucketProvisionService.bucketExists(s3Properties.getBucketName())) {
            List<String> files = s3ClientService.listFiles(s3Properties.getBucketName());
            if (files != null) {
                for (String file : files) {
                    s3ClientService.deleteFile(s3Properties.getBucketName(), file);
                }
            }
        }
        mockDataset = getMockDataset();
    }

    @Test
    @DisplayName("Download data - HTTP-PULL - success")
    public void downloadData_httpPull_success() throws Exception {
        int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();

        Agreement agreement = Agreement.Builder.newInstance()
                .id(createNewId())
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .target(mockDataset.getId())
                .timestamp(Instant.now().toString())
                .permission(Collections.singletonList(Permission.Builder.newInstance()
                        .action(Action.USE)
                        .constraint(Collections.singletonList(Constraint.Builder.newInstance()
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

        insertContractNegotiation(agreement, consumerPid, providerPid);

        String artifactURL = s3ClientService.generateGetPresignedUrl(s3Properties.getBucketName(), mockDataset.getId(), Duration.ofMinutes(10));

        EndpointProperty endpointProperty = EndpointProperty.Builder.newInstance()
                .name("https://w3id.org/edc/v0.0.1/ns/endpoint")
                .value(artifactURL)
                .build();
        EndpointProperty endpointTypeProperty = EndpointProperty.Builder.newInstance()
                .name("https://w3id.org/edc/v0.0.1/ns/endpointType")
                .value("https://w3id.org/idsa/v4.1/HTTP")
                .build();
        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpoint(artifactURL)
                .endpointProperties(List.of(endpointProperty, endpointTypeProperty))
                .endpointType("https://w3id.org/idsa/v4.1/HTTP")
                .build();

        TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
                .consumerPid(consumerPid)
                .providerPid(providerPid)
                .role(IConstants.ROLE_CONSUMER)
                .agreementId(agreement.getId())
                .callbackAddress(wiremock.baseUrl())
                .dataAddress(dataAddress)
                .state(TransferState.STARTED)
                .format(DataTransferFormat.HTTP_PULL.format())
                .build();
        transferProcessRepository.save(transferProcessStarted);

        WireMock.stubFor(WireMock.post(WireMock.urlMatching("/transfers/" + transferProcessStarted.getProviderPid() + "/completion"))
                .willReturn(
                        aResponse().withHeader("Content-Type", "application/json")
                                .withStatus(200)));

        // send request
        final ResultActions result =
                mockMvc.perform(
                        adminGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId() + "/download")
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {
        };

        String json = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<String> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

        assertNotNull(apiResp);
        assertTrue(apiResp.isSuccess());
        assertNotNull(apiResp.getData());
        assertEquals("Download started for transfer process " + transferProcessStarted.getId(), apiResp.getData());

        // check if the TransferProcess is inserted in the database
        TransferProcess transferProcessFromDb = transferProcessRepository.findById(transferProcessStarted.getId()).get();

        // this one should be skipped since we cannot guarantee that download will be done - transferProcessFromDb.isDownloaded() equal true
//        assertNotNull(transferProcessFromDb.getDataId());
        assertEquals(transferProcessStarted.getConsumerPid(), transferProcessFromDb.getConsumerPid());
        assertEquals(transferProcessStarted.getProviderPid(), transferProcessFromDb.getProviderPid());
        assertEquals(transferProcessStarted.getAgreementId(), transferProcessFromDb.getAgreementId());
        assertEquals(transferProcessStarted.getCallbackAddress(), transferProcessFromDb.getCallbackAddress());
        assertEquals(TransferState.COMPLETED, transferProcessFromDb.getState());
        // +1 from test
        assertEquals(startingTransferProcessCollectionSize + 1, transferProcessRepository.findAll().size());
    }

    @Test
    @DisplayName("Download data - HTTP-PUSH - success")
    public void downloadData_httpPush_success() throws Exception {
        int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();

        Agreement agreement = Agreement.Builder.newInstance()
                .id(createNewId())
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .target(mockDataset.getId())
                .timestamp(Instant.now().toString())
                .permission(Collections.singletonList(Permission.Builder.newInstance()
                        .action(Action.USE)
                        .constraint(Collections.singletonList(Constraint.Builder.newInstance()
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
        String transferProcessId = createNewId();

        insertContractNegotiation(agreement, consumerPid, providerPid);

        BucketCredentialsEntity bucketCredentials = bucketCredentialsService.getBucketCredentials(s3Properties.getBucketName());

        List<EndpointProperty> endpointProperties = List.of(
                EndpointProperty.Builder.newInstance()
                        .name(S3Utils.BUCKET_NAME)
                        .value(s3Properties.getBucketName())
                        .build(),
                EndpointProperty.Builder.newInstance()
                        .name(S3Utils.REGION)
                        .value(s3Properties.getRegion())
                        .build(),
                EndpointProperty.Builder.newInstance()
                        .name(S3Utils.OBJECT_KEY)
                        .value(transferProcessId)
                        .build(),
                EndpointProperty.Builder.newInstance()
                        .name(S3Utils.ACCESS_KEY)
                        .value(bucketCredentials.getAccessKey())
                        .build(),
                EndpointProperty.Builder.newInstance()
                        .name(S3Utils.SECRET_KEY)
                        .value(bucketCredentials.getSecretKey())
                        .build(),
                EndpointProperty.Builder.newInstance()
                        .name(S3Utils.ENDPOINT_OVERRIDE)
                        .value(s3Properties.getExternalPresignedEndpoint())
                        .build()
        );

        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpointProperties(endpointProperties)
                .build();

        TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
                .id(transferProcessId)
                .consumerPid(consumerPid)
                .providerPid(providerPid)
                .role(IConstants.ROLE_PROVIDER)
                .agreementId(agreement.getId())
                .callbackAddress(wiremock.baseUrl())
                .dataAddress(dataAddress)
                .datasetId(mockDataset.getId())
                .state(TransferState.STARTED)
                .format(DataTransferFormat.HTTP_PUSH.format())
                .build();
        transferProcessRepository.save(transferProcessStarted);

        WireMock.stubFor(WireMock.post(WireMock.urlMatching("/transfers/" + transferProcessStarted.getConsumerPid() + "/completion"))
                .willReturn(
                        aResponse().withHeader("Content-Type", "application/json")
                                .withStatus(200)));

        // send request
        final ResultActions result =
                mockMvc.perform(
                        adminGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId() + "/download")
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {
        };

        String json = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<String> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

        assertNotNull(apiResp);
        assertTrue(apiResp.isSuccess());
        assertNotNull(apiResp.getData());
        assertEquals("Download started for transfer process " + transferProcessStarted.getId(), apiResp.getData());

        // check if the TransferProcess is inserted in the database
        TransferProcess transferProcessFromDb = transferProcessRepository.findById(transferProcessStarted.getId()).get();

        // this one should be skipped since we cannot guarantee that download will be done - transferProcessFromDb.isDownloaded() equal true
//        assertNotNull(transferProcessFromDb.getDataId());
        assertEquals(transferProcessStarted.getConsumerPid(), transferProcessFromDb.getConsumerPid());
        assertEquals(transferProcessStarted.getProviderPid(), transferProcessFromDb.getProviderPid());
        assertEquals(transferProcessStarted.getAgreementId(), transferProcessFromDb.getAgreementId());
        assertEquals(transferProcessStarted.getCallbackAddress(), transferProcessFromDb.getCallbackAddress());
        assertEquals(TransferState.COMPLETED, transferProcessFromDb.getState());
        // +1 from test
        assertEquals(startingTransferProcessCollectionSize + 1, transferProcessRepository.findAll().size());
    }

    @Test
    @DisplayName("Download data - fail")
    public void downloadData_fail() throws Exception {
        int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();

        String consumerPid = createNewId();
        String providerPid = createNewId();

        Agreement agreement = Agreement.Builder.newInstance()
                .id(createNewId())
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .target(NegotiationMockObjectUtil.TARGET)
                .timestamp(Instant.now().toString())
                .permission(Collections.singletonList(Permission.Builder.newInstance()
                        .action(Action.USE)
                        .constraint(Collections.singletonList(Constraint.Builder.newInstance()
                                .leftOperand(LeftOperand.COUNT)
                                .operator(Operator.LTEQ)
                                .rightOperand("5")
                                .build()))
                        .build()))
                .build();

        agreementRepository.save(agreement);

        insertContractNegotiation(agreement, consumerPid, providerPid);

        PolicyEnforcement policyEnforcement = new PolicyEnforcement(createNewId(), agreement.getId(), 0);

        policyEnforcementRepository.save(policyEnforcement);

        String artifactURL = s3ClientService.generateGetPresignedUrl(s3Properties.getBucketName(), mockDataset.getId(), Duration.ofSeconds(1));

        Thread.sleep(2000); // wait for the presigned URL to expire

        EndpointProperty endpointProperty = EndpointProperty.Builder.newInstance()
                .name("https://w3id.org/edc/v0.0.1/ns/endpoint")
                .value(artifactURL)
                .build();
        EndpointProperty endpointTypeProperty = EndpointProperty.Builder.newInstance()
                .name("https://w3id.org/edc/v0.0.1/ns/endpointType")
                .value("https://w3id.org/idsa/v4.1/HTTP")
                .build();
        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpoint(artifactURL)
                .endpointProperties(List.of(endpointProperty, endpointTypeProperty))
                .endpointType("https://w3id.org/idsa/v4.1/HTTP")
                .build();

        TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
                .consumerPid(consumerPid)
                .providerPid(providerPid)
                .agreementId(agreement.getId())
                .callbackAddress(wiremock.baseUrl())
                .dataAddress(dataAddress)
                .state(TransferState.STARTED)
                .format(DataTransferFormat.HTTP_PULL.format())
                .build();
        transferProcessRepository.save(transferProcessStarted);

        // send request
        final ResultActions result =
                mockMvc.perform(
                        adminGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId() + "/download")
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isBadRequest());

        TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {
        };

        String json = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<String> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

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
    }

    private void insertContractNegotiation(Agreement agreement, String consumerPid, String providerPid) {
        ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                .id(createNewId())
                .agreement(agreement)
                .consumerPid(consumerPid)
                .providerPid(providerPid)
                .state(ContractNegotiationState.FINALIZED)
                .build();
        contractNegotiationRepository.save(contractNegotiation);
    }

    @Test
    @DisplayName("Download data - purpose policy - allowed")
    public void downloadData_PurposePolicy_allowed() throws Exception {
        int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();

        Constraint constraintPurpose = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.PURPOSE)
                .operator(Operator.EQ)
                // purpose must match with value from property file - demo
                .rightOperand("demo")
                .build();
        Agreement agreement = insertAgreement(constraintPurpose);

        String consumerPid = createNewId();
        String providerPid = createNewId();

        insertContractNegotiation(agreement, consumerPid, providerPid);

        String artifactURL = s3ClientService.generateGetPresignedUrl(s3Properties.getBucketName(), mockDataset.getId(), Duration.ofMinutes(10));

        EndpointProperty endpointProperty = EndpointProperty.Builder.newInstance()
                .name("https://w3id.org/edc/v0.0.1/ns/endpoint")
                .value(artifactURL)
                .build();
        EndpointProperty endpointTypeProperty = EndpointProperty.Builder.newInstance()
                .name("https://w3id.org/edc/v0.0.1/ns/endpointType")
                .value("https://w3id.org/idsa/v4.1/HTTP")
                .build();
        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpoint(artifactURL)
                .endpointProperties(List.of(endpointProperty, endpointTypeProperty))
                .endpointType("https://w3id.org/idsa/v4.1/HTTP")
                .build();

        TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
                .consumerPid(consumerPid)
                .providerPid(providerPid)
                .role(IConstants.ROLE_CONSUMER)
                .agreementId(agreement.getId())
                .callbackAddress(wiremock.baseUrl())
                .dataAddress(dataAddress)
                .state(TransferState.STARTED)
                .format(DataTransferFormat.HTTP_PULL.format())
                .build();
        transferProcessRepository.save(transferProcessStarted);

        WireMock.stubFor(WireMock.post(WireMock.urlMatching("/transfers/" + transferProcessStarted.getProviderPid() + "/completion"))
                .willReturn(
                        aResponse().withHeader("Content-Type", "application/json")
                                .withStatus(200)));

        // send request
        final ResultActions result =
                mockMvc.perform(
                        adminGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId() + "/download")
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {
        };

        String json = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<String> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

        assertNotNull(apiResp);
        assertTrue(apiResp.isSuccess());
        assertNotNull(apiResp.getData());
        assertEquals("Download started for transfer process " + transferProcessStarted.getId(), apiResp.getData());

        // + 1 from test
        assertEquals(startingTransferProcessCollectionSize + 1, transferProcessRepository.findAll().size());
    }

    @Test
    @DisplayName("Download data - location policy - allowed")
    public void downloadData_LocationPolicy_allowed() throws Exception {
        int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();

        Constraint constraintPurpose = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.SPATIAL)
                .operator(Operator.EQ)
                // purpose must match with value from property file - EU
                .rightOperand("EU")
                .build();
        Agreement agreement = insertAgreement(constraintPurpose);

        String consumerPid = createNewId();
        String providerPid = createNewId();

        insertContractNegotiation(agreement, consumerPid, providerPid);

        String artifactURL = s3ClientService.generateGetPresignedUrl(s3Properties.getBucketName(), mockDataset.getId(), Duration.ofMinutes(10));

        EndpointProperty endpointProperty = EndpointProperty.Builder.newInstance()
                .name("https://w3id.org/edc/v0.0.1/ns/endpoint")
                .value(artifactURL)
                .build();
        EndpointProperty endpointTypeProperty = EndpointProperty.Builder.newInstance()
                .name("https://w3id.org/edc/v0.0.1/ns/endpointType")
                .value("https://w3id.org/idsa/v4.1/HTTP")
                .build();
        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpoint(artifactURL)
                .endpointProperties(List.of(endpointProperty, endpointTypeProperty))
                .endpointType("https://w3id.org/idsa/v4.1/HTTP")
                .build();

        TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
                .consumerPid(consumerPid)
                .providerPid(providerPid)
                .role(IConstants.ROLE_CONSUMER)
                .agreementId(agreement.getId())
                .callbackAddress(wiremock.baseUrl())
                .dataAddress(dataAddress)
                .state(TransferState.STARTED)
                .format(DataTransferFormat.HTTP_PULL.format())
                .build();
        transferProcessRepository.save(transferProcessStarted);

        WireMock.stubFor(WireMock.post(WireMock.urlMatching("/transfers/" + transferProcessStarted.getProviderPid() + "/completion"))
                .willReturn(
                        aResponse().withHeader("Content-Type", "application/json")
                                .withStatus(200)));

        // send request
        final ResultActions result =
                mockMvc.perform(
                        adminGet(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId() + "/download")
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {
        };

        String json = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<String> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

        assertNotNull(apiResp);
        assertTrue(apiResp.isSuccess());
        // + 1 from test
        assertEquals(startingTransferProcessCollectionSize + 1, transferProcessRepository.findAll().size());
    }

    private Agreement insertAgreement(Constraint constraint) {
        Agreement agreement = Agreement.Builder.newInstance()
                .id(createNewId())
                .assignee(NegotiationMockObjectUtil.ASSIGNEE)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .target(CatalogMockObjectUtil.DATASET_ID)
                .timestamp(Instant.now().toString())
                .permission(Collections.singletonList(Permission.Builder.newInstance()
                        .action(Action.USE)
                        .constraint(Collections.singletonList(constraint))
                        .build()))
                .build();

        agreementRepository.save(agreement);
        return agreement;
    }

    private Dataset getMockDataset() {
        Dataset mockDataset = Dataset.Builder.newInstance()
                .id(CatalogMockObjectUtil.DATASET_ID)
                .conformsTo(CatalogMockObjectUtil.CONFORMSTO)
                .creator(CatalogMockObjectUtil.CREATOR)
                .distribution(Stream.of(CatalogMockObjectUtil.DISTRIBUTION).collect(Collectors.toCollection(HashSet::new)))
                .description(new HashSet<>(Collections.singletonList(CatalogMockObjectUtil.MULTILANGUAGE)))
                .issued(CatalogMockObjectUtil.ISSUED)
                .keyword(new HashSet<>(Arrays.asList("keyword1", "keyword2")))
                .identifier(CatalogMockObjectUtil.IDENTIFIER)
                .modified(CatalogMockObjectUtil.MODIFIED)
                .theme(new HashSet<>(Arrays.asList("white", "blue", "aqua")))
                .title(CatalogMockObjectUtil.TITLE)
                .hasPolicy(new HashSet<>(Collections.singletonList(CatalogMockObjectUtil.OFFER)))
                .build();

        Catalog mockCatalog = Catalog.Builder.newInstance()
                .id(createNewId())
                .title(CatalogMockObjectUtil.TITLE)
                .description(new HashSet<>(Collections.singletonList(CatalogMockObjectUtil.MULTILANGUAGE)))
                .dataset(Collections.emptySet())
                .build();
        catalogRepository.save(mockCatalog);

        MockMultipartFile mockFile = new MockMultipartFile(FILE_NAME, FILE_NAME, MediaType.TEXT_PLAIN_VALUE, fileContent.getBytes());
        datasetService.saveDataset(mockDataset, mockFile, null, null);
        return mockDataset;
    }
}
