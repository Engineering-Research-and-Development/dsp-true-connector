package it.eng.connector.integration.negotiation;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.negotiation.model.*;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.model.IConstants;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.s3.service.S3ClientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ContractNegotiationRequestedIT extends BaseIntegrationTest {
// -> REQUESTED
//	@PostMapping(path = "/request")

    @Autowired
    private CatalogRepository catalogRepository;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DataServiceRepository dataServiceRepository;
    @Autowired
    private DistributionRepository distributionRepository;
    @Autowired
    private ArtifactRepository artifactRepository;
    @Autowired
    private S3ClientService s3ClientService;
    @Autowired
    private ContractNegotiationRepository contractNegotiationRepository;
    @Autowired
    private OfferRepository offerRepository;

    private Catalog catalog;
    private Dataset dataset;

    @BeforeEach
    public void populateCatalog() {
        catalog = CatalogMockObjectUtil.createNewCatalog();
        dataset = catalog.getDataset().stream().findFirst().get();

        catalogRepository.save(catalog);
        datasetRepository.saveAll(catalog.getDataset());
        dataServiceRepository.saveAll(catalog.getService());
        distributionRepository.saveAll(catalog.getDistribution());
        artifactRepository.save(dataset.getArtifact());
    }

    @AfterEach
    public void cleanup() {
        contractNegotiationRepository.deleteAll();
        offerRepository.deleteAll();
        datasetRepository.deleteAll();
        catalogRepository.deleteAll();
        dataServiceRepository.deleteAll();
        distributionRepository.deleteAll();
        artifactRepository.deleteAll();
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiation_success() throws Exception {
        //needs to match offer in catalog
        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(Collections.singletonList(NegotiationMockObjectUtil.CONSTRAINT_COUNT_5))
                .build();
        String datasetOfferId = dataset.getHasPolicy().stream().findFirst().get().getId();
        Offer offerRequest = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .build();

        String fileContent = "Hello, World!";

        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "hello.txt",
                MediaType.TEXT_PLAIN_VALUE,
                fileContent.getBytes()
        );

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(file.getOriginalFilename())
                .build();

        Map<String, String> destinationS3Properties = createS3EndpointProperties(dataset.getId());

        try {
            s3ClientService.uploadFile(file.getInputStream(), destinationS3Properties,
                            file.getContentType(), contentDisposition.toString())
                    .get();
        } catch (Exception e) {
            throw new Exception("File storing aborted, " + e.getLocalizedMessage());
        }

        ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .offer(offerRequest)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/negotiations/request")
                                .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(contractRequestMessage)))
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        ContractNegotiation contractNegotiationRequested = NegotiationSerializer.deserializeProtocol(response, ContractNegotiation.class);
        assertNotNull(contractNegotiationRequested);
        assertEquals(ContractNegotiationState.REQUESTED, contractNegotiationRequested.getState());

        offerCheck(getContractNegotiationOverAPI(contractNegotiationRequested.getConsumerPid(),
                contractNegotiationRequested.getProviderPid()), dataset.getHasPolicy().stream().findFirst().get().getId());
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiation_negotiation_exists() throws Exception {

        ContractNegotiation contractNegotiationRequested = ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .callbackAddress("callbackAddress.test")
                .state(ContractNegotiationState.REQUESTED)
                .build();

        ContractRequestMessage crm = ContractRequestMessage.Builder.newInstance()
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .consumerPid(contractNegotiationRequested.getConsumerPid())
                .offer(NegotiationMockObjectUtil.OFFER)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/negotiations/request")
                                .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(crm)))
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        ContractNegotiationErrorMessage errorMessage = NegotiationSerializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
        assertNotNull(errorMessage);
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiation_invalid_offer() throws Exception {

        // offer with new UUID as ID, that does not exist in catalog
        Offer offer = Offer.Builder.newInstance()
                .target(NegotiationMockObjectUtil.TARGET)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(NegotiationMockObjectUtil.PERMISSION_COUNT_5))
                .build();

        ContractNegotiation contractNegotiationRequested = ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .callbackAddress("callbackAddress.test")
                .state(ContractNegotiationState.REQUESTED)
                .build();

        ContractRequestMessage crm = ContractRequestMessage.Builder.newInstance()
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .consumerPid(contractNegotiationRequested.getConsumerPid())
                .offer(offer)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/negotiations/request")
                                .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(crm)))
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        ContractNegotiationErrorMessage errorMessage = NegotiationSerializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
        assertNotNull(errorMessage);
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiation_invalid_offer_constraint() throws Exception {
        // offer with dateTime constraint - will not match with one in initial_data
        Offer offer = Offer.Builder.newInstance()
                .id("TO BE CHANGED")
                .target(NegotiationMockObjectUtil.TARGET)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(NegotiationMockObjectUtil.PERMISSION))
                .build();

        ContractNegotiation contractNegotiationRequested = ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .callbackAddress("callbackAddress.test")
                .state(ContractNegotiationState.REQUESTED)
                .build();

        ContractRequestMessage crm = ContractRequestMessage.Builder.newInstance()
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .consumerPid(contractNegotiationRequested.getConsumerPid())
                .offer(offer)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/negotiations/request")
                                .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(crm)))
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        ContractNegotiationErrorMessage errorMessage = NegotiationSerializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
        assertNotNull(errorMessage);
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiationCounteroffer_success() throws Exception {
        // Setup: Create an existing negotiation in OFFERED state
        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(Collections.singletonList(NegotiationMockObjectUtil.CONSTRAINT_COUNT_5))
                .build();

        String datasetOfferId = dataset.getHasPolicy().stream().findFirst().get().getId();
        Offer offer = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .originalId(datasetOfferId)
                .build();
        offerRepository.save(offer);

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .offer(offer)
                .state(ContractNegotiationState.OFFERED)
                .role(IConstants.ROLE_PROVIDER)
                .build();
        contractNegotiationRepository.save(existingNegotiation);

        // Consumer sends a counteroffer with the same constraints
        Offer counterOffer = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .build();

        ContractRequestMessage counterofferMessage = ContractRequestMessage.Builder.newInstance()
                .consumerPid(existingNegotiation.getConsumerPid())
                .providerPid(existingNegotiation.getProviderPid())
                .offer(counterOffer)
                .build();

        final ResultActions result = mockMvc.perform(
                post("/negotiations/" + existingNegotiation.getProviderPid() + "/request")
                        .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(counterofferMessage)))
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        ContractNegotiation contractNegotiationCounteroffer = NegotiationSerializer.deserializeProtocol(response, ContractNegotiation.class);
        assertNotNull(contractNegotiationCounteroffer);
        assertEquals(ContractNegotiationState.REQUESTED, contractNegotiationCounteroffer.getState());
        assertEquals(existingNegotiation.getProviderPid(), contractNegotiationCounteroffer.getProviderPid());
        assertEquals(existingNegotiation.getConsumerPid(), contractNegotiationCounteroffer.getConsumerPid());
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiationCounteroffer_providerPidMismatch() throws Exception {
        // Setup existing negotiation
        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(Collections.singletonList(NegotiationMockObjectUtil.CONSTRAINT_COUNT_5))
                .build();

        String datasetOfferId = dataset.getHasPolicy().stream().findFirst().get().getId();
        Offer offer = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .originalId(datasetOfferId)
                .build();
        offerRepository.save(offer);

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .offer(offer)
                .state(ContractNegotiationState.OFFERED)
                .role(IConstants.ROLE_PROVIDER)
                .build();
        contractNegotiationRepository.save(existingNegotiation);

        // Create counteroffer message with mismatched providerPid
        Offer counterOffer = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .build();

        ContractRequestMessage counterofferMessage = ContractRequestMessage.Builder.newInstance()
                .consumerPid(existingNegotiation.getConsumerPid())
                .providerPid(createNewId()) // Different providerPid
                .offer(counterOffer)
                .build();

        final ResultActions result = mockMvc.perform(
                post("/negotiations/" + existingNegotiation.getProviderPid() + "/request")
                        .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(counterofferMessage)))
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        ContractNegotiationErrorMessage errorMessage = NegotiationSerializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
        assertNotNull(errorMessage);
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiationCounteroffer_negotiationNotFound() throws Exception {
        // Try to send counteroffer for non-existent negotiation
        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(Collections.singletonList(NegotiationMockObjectUtil.CONSTRAINT_COUNT_5))
                .build();

        String nonExistentProviderPid = createNewId();
        String datasetOfferId = dataset.getHasPolicy().stream().findFirst().get().getId();

        Offer counterOffer = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .build();

        ContractRequestMessage counterofferMessage = ContractRequestMessage.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(nonExistentProviderPid)
                .offer(counterOffer)
                .build();

        final ResultActions result = mockMvc.perform(
                post("/negotiations/" + nonExistentProviderPid + "/request")
                        .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(counterofferMessage)))
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiationCounteroffer_invalidState() throws Exception {
        // Setup: Create an existing negotiation in REQUESTED state (invalid for counteroffer)
        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(Collections.singletonList(NegotiationMockObjectUtil.CONSTRAINT_COUNT_5))
                .build();

        String datasetOfferId = dataset.getHasPolicy().stream().findFirst().get().getId();
        Offer offer = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .originalId(datasetOfferId)
                .build();
        offerRepository.save(offer);

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .offer(offer)
                .state(ContractNegotiationState.REQUESTED) // Invalid state for counteroffer
                .role(IConstants.ROLE_PROVIDER)
                .build();
        contractNegotiationRepository.save(existingNegotiation);

        Offer counterOffer = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .build();

        ContractRequestMessage counterofferMessage = ContractRequestMessage.Builder.newInstance()
                .consumerPid(existingNegotiation.getConsumerPid())
                .providerPid(existingNegotiation.getProviderPid())
                .offer(counterOffer)
                .build();

        final ResultActions result = mockMvc.perform(
                post("/negotiations/" + existingNegotiation.getProviderPid() + "/request")
                        .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(counterofferMessage)))
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        ContractNegotiationErrorMessage errorMessage = NegotiationSerializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
        assertNotNull(errorMessage);
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiationCounteroffer_invalidOffer() throws Exception {
        // Setup existing negotiation
        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(Collections.singletonList(NegotiationMockObjectUtil.CONSTRAINT_COUNT_5))
                .build();

        String datasetOfferId = dataset.getHasPolicy().stream().findFirst().get().getId();
        Offer offer = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .originalId(datasetOfferId)
                .build();
        offerRepository.save(offer);

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .offer(offer)
                .state(ContractNegotiationState.OFFERED)
                .role(IConstants.ROLE_PROVIDER)
                .build();
        contractNegotiationRepository.save(existingNegotiation);

        // Create counteroffer with invalid offer (non-existent offer ID)
        Offer counterOffer = Offer.Builder.newInstance()
                .id(createNewId()) // Non-existent offer ID
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .build();

        ContractRequestMessage counterofferMessage = ContractRequestMessage.Builder.newInstance()
                .consumerPid(existingNegotiation.getConsumerPid())
                .providerPid(existingNegotiation.getProviderPid())
                .offer(counterOffer)
                .build();

        final ResultActions result = mockMvc.perform(
                post("/negotiations/" + existingNegotiation.getProviderPid() + "/request")
                        .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(counterofferMessage)))
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        ContractNegotiationErrorMessage errorMessage = NegotiationSerializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
        assertNotNull(errorMessage);
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiationCounteroffer_targetMismatch() throws Exception {
        // Setup existing negotiation
        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(Collections.singletonList(NegotiationMockObjectUtil.CONSTRAINT_COUNT_5))
                .build();

        String datasetOfferId = dataset.getHasPolicy().stream().findFirst().get().getId();
        Offer offer = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .originalId(datasetOfferId)
                .build();
        offerRepository.save(offer);

        ContractNegotiation existingNegotiation = ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .offer(offer)
                .state(ContractNegotiationState.OFFERED)
                .role(IConstants.ROLE_PROVIDER)
                .build();
        contractNegotiationRepository.save(existingNegotiation);

        // Create counteroffer with different target
        Offer counterOffer = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target("different-target-dataset-id") // Different target
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .build();

        ContractRequestMessage counterofferMessage = ContractRequestMessage.Builder.newInstance()
                .consumerPid(existingNegotiation.getConsumerPid())
                .providerPid(existingNegotiation.getProviderPid())
                .offer(counterOffer)
                .build();

        final ResultActions result = mockMvc.perform(
                post("/negotiations/" + existingNegotiation.getProviderPid() + "/request")
                        .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(counterofferMessage)))
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        ContractNegotiationErrorMessage errorMessage = NegotiationSerializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
        assertNotNull(errorMessage);
    }
}
