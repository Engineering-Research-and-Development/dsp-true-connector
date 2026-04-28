package it.eng.connector.integration.datatransfer;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.negotiation.model.*;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.util.S3Utils;
import it.eng.tools.service.FieldEncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DataTransferProcessRequestedIT extends BaseIntegrationTest {
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
    @Autowired
    private FieldEncryptionService fieldEncryptionService;
    private Catalog catalog;
    private Dataset dataset;
    private Distribution distribution;
    private Distribution distributionHttpPush;

    @BeforeEach
    public void populateCatalog() {
        distribution = Distribution.Builder.newInstance()
                .format(DataTransferFormat.HTTP_PULL.format())
                .accessService(CatalogMockObjectUtil.DATA_SERVICE)
                .build();
        distributionHttpPush = Distribution.Builder.newInstance()
                .format(DataTransferFormat.HTTP_PUSH.format())
                .accessService(CatalogMockObjectUtil.DATA_SERVICE)
                .build();
        dataset = Dataset.Builder.newInstance()
                .hasPolicy(Collections.singleton(CatalogMockObjectUtil.OFFER))
                .distribution(new HashSet<>(Arrays.asList(distribution, distributionHttpPush)))
                .build();
        catalog = Catalog.Builder.newInstance()
                .dataset(Collections.singleton(dataset))
                .build();

        distributionRepository.save(distribution);
        distributionRepository.save(distributionHttpPush);
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
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/" + TENANT_ID + "/transfers/request")
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
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/" + TENANT_ID + "/transfers/request")
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
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/" + TENANT_ID + "/transfers/request")
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
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/" + TENANT_ID + "/transfers/request")
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
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .agreementId(createNewId())
                .format(DataTransferFormat.HTTP_PULL.format())
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/" + TENANT_ID + "/transfers/request")
                                .content(TransferSerializer.serializeProtocol(transferRequestMessage))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Basic YXNkckBtYWlsLmNvbTpwYXNzd29yZA=="));
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        String response = result.andReturn().getResponse().getContentAsString();
        TransferError transferError = TransferSerializer.deserializeProtocol(response, TransferError.class);
        assertNotNull(transferError);
    }

    @Test
    @DisplayName("DataTransfer requested - HTTP_PUSH - secretKey is encrypted before being stored in MongoDB")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void initiateDataTransfer_httpPush_secretKeyEncryptedInMongoDB() throws Exception {
        String plainSecretKey = "plain-secret-key-for-test";

        Agreement agreement = Agreement.Builder.newInstance()
                .assignee("assignee")
                .assigner("assigner")
                .target("test_dataset")
                .permission(Arrays.asList(Permission.Builder.newInstance().action(Action.USE).build()))
                .build();
        agreementRepository.save(agreement);

        ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .callbackAddress("callbackAddress.test")
                .agreement(agreement)
                .state(ContractNegotiationState.FINALIZED)
                .role(IConstants.ROLE_PROVIDER)
                .build();
        contractNegotiationRepository.save(contractNegotiation);

        TransferProcess transferProcessInitialized = TransferProcess.Builder.newInstance()
                .consumerPid(IConstants.TEMPORARY_CONSUMER_PID)
                .providerPid(createNewId())
                .format(DataTransferFormat.HTTP_PUSH.format())
                .agreementId(agreement.getId())
                .state(TransferState.INITIALIZED)
                .datasetId(dataset.getId())
                .build();
        transferProcessRepository.save(transferProcessInitialized);

        // DataAddress carries a plain secretKey, as the consumer sends it to the provider
        DataAddress httpPushDataAddress = DataAddress.Builder.newInstance()
                .endpointProperties(List.of(
                        EndpointProperty.Builder.newInstance().name(S3Utils.BUCKET_NAME).value("consumer-bucket").build(),
                        EndpointProperty.Builder.newInstance().name(S3Utils.REGION).value("us-east-1").build(),
                        EndpointProperty.Builder.newInstance().name(S3Utils.OBJECT_KEY).value("consumer-object-key").build(),
                        EndpointProperty.Builder.newInstance().name(S3Utils.ACCESS_KEY).value("consumer-access-key").build(),
                        EndpointProperty.Builder.newInstance().name(S3Utils.SECRET_KEY).value(plainSecretKey).build(),
                        EndpointProperty.Builder.newInstance().name(S3Utils.ENDPOINT_OVERRIDE).value("http://consumer-minio:9000").build()
                ))
                .build();

        TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
                .consumerPid(createNewId())
                .agreementId(agreement.getId())
                .format(DataTransferFormat.HTTP_PUSH.format())
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .dataAddress(httpPushDataAddress)
                .build();

        mockMvc.perform(
                post("/" + TENANT_ID + "/transfers/request")
                        .content(TransferSerializer.serializeProtocol(transferRequestMessage))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        // Load the persisted TransferProcess from MongoDB
        Optional<TransferProcess> saved = transferProcessRepository.findById(transferProcessInitialized.getId());
        assertTrue(saved.isPresent(), "TransferProcess must be saved to MongoDB");

        String storedSecretKey = saved.get().getDataAddress().getEndpointProperties().stream()
                .filter(p -> S3Utils.SECRET_KEY.equals(p.getName()))
                .findFirst()
                .map(EndpointProperty::getValue)
                .orElse(null);

        assertNotNull(storedSecretKey, "secretKey endpoint property must be present in stored DataAddress");
        assertNotEquals(plainSecretKey, storedSecretKey,
                "secretKey must be encrypted in MongoDB — plain text must never be stored");
        assertEquals(plainSecretKey, fieldEncryptionService.decrypt(storedSecretKey),
                "Decrypting the stored secretKey must yield the original plain value");

        // cleanup
        agreementRepository.delete(agreement);
        contractNegotiationRepository.delete(contractNegotiation);
        transferProcessRepository.deleteById(transferProcessInitialized.getId());
    }
}
