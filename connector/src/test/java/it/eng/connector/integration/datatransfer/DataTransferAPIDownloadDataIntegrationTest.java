package it.eng.connector.integration.datatransfer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.WireMockServer;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.DatasetService;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.negotiation.model.*;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;
import org.wiremock.spring.InjectWireMock;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DataTransferAPIDownloadDataIntegrationTest extends BaseIntegrationTest {

    private static final String FILE_NAME = "hello.txt";
    private String fileContent = "Hello, World!";

    @InjectWireMock
    private WireMockServer wiremock;

    @Autowired
    private TransferProcessRepository transferProcessRepository;

    @Autowired
    private AgreementRepository agreementRepository;

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
    private S3Properties s3Properties;

    @BeforeEach
    public void cleanup() {
        transferProcessRepository.deleteAll();
        agreementRepository.deleteAll();
        policyEnforcementRepository.deleteAll();
        datasetRepository.deleteAll();
        catalogRepository.deleteAll();
        if (s3ClientService.bucketExists(s3Properties.getBucketName())) {
            List<String> files = s3ClientService.listFiles(s3Properties.getBucketName());
            if (files != null) {
                for (String file : files) {
                    s3ClientService.deleteFile(s3Properties.getBucketName(), file);
                }
            }
        }
    }

    @Test
    @DisplayName("Download data - success")
    @WithUserDetails(TestUtil.API_USER)
    public void downloadData_success() throws Exception {
        int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();
        int startingBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();

        Dataset mockDataset = getMockDataset();

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
                        get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId() + "/download")
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {
        };

        String json = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<String> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

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


        MockHttpServletResponse mockResponse = new MockHttpServletResponse();
        s3ClientService.downloadFile(s3Properties.getBucketName(), transferProcessStarted.getId(), mockResponse);

        ResponseBytes<GetObjectResponse> fileFromStorage = ResponseBytes.fromByteArray(GetObjectResponse.builder()
                        .contentType(mockResponse.getContentType())
                        .contentDisposition(mockResponse.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                        .build(),
                mockResponse.getContentAsByteArray());

        ContentDisposition contentDisposition = ContentDisposition.parse(fileFromStorage.response().contentDisposition());

        assertEquals(MediaType.TEXT_PLAIN_VALUE, fileFromStorage.response().contentType());
        assertEquals(FILE_NAME, contentDisposition.getFilename());
        assertEquals(fileContent, fileFromStorage.asUtf8String());
        // +2 from test; inserted Dataset with file and downloaded TransferProcess
        checkIfEndBucketFileCountIsAsExpected(startingBucketFileCount + 2);
    }

    @Test
    @DisplayName("Download data - fail")
    @WithUserDetails(TestUtil.API_USER)
    public void downloadData_fail() throws Exception {
        int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();
        int startingBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();

        Dataset mockDataset = getMockDataset();

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
                        get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId() + "/download")
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

        // check if the file is inserted in the database
        // 1 from initial data + 1 from test; dataset is added but not downloaded
        checkIfEndBucketFileCountIsAsExpected(startingBucketFileCount + 1);

    }

    @Test
    @DisplayName("Download data - purpose policy - allowed")
    @WithUserDetails(TestUtil.API_USER)
    public void downloadData_PurposePolicy_allowed() throws Exception {
        int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();
        int startingBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();

        Dataset mockDataset = getMockDataset();

        Constraint constraintPurpose = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.PURPOSE)
                .operator(Operator.EQ)
                // purpose must match with value from property file - demo
                .rightOperand("demo")
                .build();
        Agreement agreement = insertAgreement(constraintPurpose);

        String consumerPid = createNewId();
        String providerPid = createNewId();

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
                        get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId() + "/download")
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {
        };

        String json = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<String> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

        assertNotNull(apiResp);
        assertTrue(apiResp.isSuccess());
        assertNull(apiResp.getData());
        // + 1 from test
        assertEquals(startingTransferProcessCollectionSize + 1, transferProcessRepository.findAll().size());
        // +2 from test; inserted Dataset with file and downloaded TransferProcess
        checkIfEndBucketFileCountIsAsExpected(startingBucketFileCount + 2);
    }

    @Test
    @DisplayName("Download data - location policy - allowed")
    @WithUserDetails(TestUtil.API_USER)
    public void downloadData_LocationPolicy_allowed() throws Exception {
        int startingTransferProcessCollectionSize = transferProcessRepository.findAll().size();
        int startingBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();

        Dataset mockDataset = getMockDataset();

        Constraint constraintPurpose = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.SPATIAL)
                .operator(Operator.EQ)
                // purpose must match with value from property file - EU
                .rightOperand("EU")
                .build();
        Agreement agreement = insertAgreement(constraintPurpose);

        String consumerPid = createNewId();
        String providerPid = createNewId();

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
                        get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + transferProcessStarted.getId() + "/download")
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {
        };

        String json = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<String> apiResp = CatalogSerializer.deserializePlain(json, typeRef);

        assertNotNull(apiResp);
        assertTrue(apiResp.isSuccess());
        assertNull(apiResp.getData());
        // + 1 from test
        assertEquals(startingTransferProcessCollectionSize + 1, transferProcessRepository.findAll().size());
        // +2 from test; inserted Dataset with file and downloaded TransferProcess
        checkIfEndBucketFileCountIsAsExpected(startingBucketFileCount + 2);
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

    private Dataset getMockDataset() {
        Dataset mockDataset = Dataset.Builder.newInstance()
                .id(CatalogMockObjectUtil.DATASET_ID)
                .conformsTo(CatalogMockObjectUtil.CONFORMSTO)
                .creator(CatalogMockObjectUtil.CREATOR)
                .distribution(Arrays.asList(CatalogMockObjectUtil.DISTRIBUTION).stream().collect(Collectors.toCollection(HashSet::new)))
                .description(Arrays.asList(CatalogMockObjectUtil.MULTILANGUAGE).stream().collect(Collectors.toCollection(HashSet::new)))
                .issued(CatalogMockObjectUtil.ISSUED)
                .keyword(Arrays.asList("keyword1", "keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
                .identifier(CatalogMockObjectUtil.IDENTIFIER)
                .modified(CatalogMockObjectUtil.MODIFIED)
                .theme(Arrays.asList("white", "blue", "aqua").stream().collect(Collectors.toCollection(HashSet::new)))
                .title(CatalogMockObjectUtil.TITLE)
                .hasPolicy(Arrays.asList(CatalogMockObjectUtil.OFFER).stream().collect(Collectors.toCollection(HashSet::new)))
                .build();

        Catalog mockCatalog = Catalog.Builder.newInstance()
                .id(createNewId())
                .title(CatalogMockObjectUtil.TITLE)
                .description(Arrays.asList(CatalogMockObjectUtil.MULTILANGUAGE).stream().collect(Collectors.toCollection(HashSet::new)))
                .dataset(Collections.emptySet())
                .build();
        catalogRepository.save(mockCatalog);

        MockMultipartFile mockFile = new MockMultipartFile(FILE_NAME, FILE_NAME, MediaType.TEXT_PLAIN_VALUE, fileContent.getBytes());
        datasetService.saveDataset(mockDataset, mockFile, null, null);
        return mockDataset;
    }

    private void checkIfEndBucketFileCountIsAsExpected(int expectedEndBucketFileCount) throws InterruptedException {
        // Wait for S3 to reflect the deletion (max 5 seconds)
        int maxRetries = 10;
        int delayMs = 500;
        // this is to ensure that the test functions correctly since the count will never -10000
        int endBucketFileCount = -10000;
        for (int i = 0; i < maxRetries; i++) {
            endBucketFileCount = s3ClientService.listFiles(s3Properties.getBucketName()).size();
            if (endBucketFileCount == expectedEndBucketFileCount) break;
            Thread.sleep(delayMs);
        }
        assertEquals(endBucketFileCount, expectedEndBucketFileCount);
    }
}
