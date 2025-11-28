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

public class ContractNegotiationOfferedIT extends BaseIntegrationTest {
// -> OFFERED
//	@PostMapping(path = "/negotiations/offers") - initial
//	@PostMapping("/consumer/negotiations/{consumerPid}/offers") - counteroffer

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
    public void createNegotiationWithOffer_success() throws Exception {
        // needs to match offer in catalog
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

        MockMultipartFile file = new MockMultipartFile(
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

        ContractOfferMessage contractOfferMessage = ContractOfferMessage.Builder.newInstance()
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .offer(offerRequest)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/negotiations/offers")
                                .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(contractOfferMessage)))
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        ContractNegotiation contractNegotiationOffered = NegotiationSerializer.deserializeProtocol(response, ContractNegotiation.class);
        assertNotNull(contractNegotiationOffered);
        assertEquals(ContractNegotiationState.OFFERED, contractNegotiationOffered.getState());

        // Verify the negotiation was created with the correct offer target
        ContractNegotiation savedNegotiation = getContractNegotiationOverAPI(contractNegotiationOffered.getConsumerPid(),
                contractNegotiationOffered.getProviderPid());
        assertNotNull(savedNegotiation.getOffer());
        assertEquals(dataset.getId(), savedNegotiation.getOffer().getTarget());
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiationWithOffer_negotiation_exists() throws Exception {
        // Save an existing negotiation to database first
        ContractNegotiation contractNegotiationOffered = ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress("callbackAddress.test")
                .state(ContractNegotiationState.OFFERED)
                .role(IConstants.ROLE_CONSUMER)
                .build();
        contractNegotiationRepository.save(contractNegotiationOffered);

        ContractOfferMessage com = ContractOfferMessage.Builder.newInstance()
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .providerPid(contractNegotiationOffered.getProviderPid())
                .offer(NegotiationMockObjectUtil.OFFER)
                .build();

        final ResultActions result =
                mockMvc.perform(
                        post("/negotiations/offers")
                                .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(com)))
                                .contentType(MediaType.APPLICATION_JSON));
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        ContractNegotiationErrorMessage errorMessage = NegotiationSerializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
        assertNotNull(errorMessage);
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiationOfferCounteroffer_success() throws Exception {
        // Setup: Create an existing negotiation in REQUESTED state
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
                .state(ContractNegotiationState.REQUESTED)
                .role(IConstants.ROLE_CONSUMER)
                .build();
        contractNegotiationRepository.save(existingNegotiation);

        // Provider sends a counteroffer with the same constraints
        Offer counterOffer = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .build();

        ContractOfferMessage counterofferMessage = ContractOfferMessage.Builder.newInstance()
                .consumerPid(existingNegotiation.getConsumerPid())
                .providerPid(existingNegotiation.getProviderPid())
                .offer(counterOffer)
                .build();

        final ResultActions result = mockMvc.perform(
                post("/consumer/negotiations/" + existingNegotiation.getConsumerPid() + "/offers")
                        .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(counterofferMessage)))
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        ContractNegotiation contractNegotiationCounteroffer = NegotiationSerializer.deserializeProtocol(response, ContractNegotiation.class);
        assertNotNull(contractNegotiationCounteroffer);
        assertEquals(ContractNegotiationState.OFFERED, contractNegotiationCounteroffer.getState());
        assertEquals(existingNegotiation.getProviderPid(), contractNegotiationCounteroffer.getProviderPid());
        assertEquals(existingNegotiation.getConsumerPid(), contractNegotiationCounteroffer.getConsumerPid());
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiationOfferCounteroffer_consumerPidMismatch() throws Exception {
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
                .state(ContractNegotiationState.REQUESTED)
                .role(IConstants.ROLE_CONSUMER)
                .build();
        contractNegotiationRepository.save(existingNegotiation);

        // Create counteroffer message with mismatched consumerPid
        Offer counterOffer = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .build();

        ContractOfferMessage counterofferMessage = ContractOfferMessage.Builder.newInstance()
                .consumerPid(createNewId()) // Different consumerPid
                .providerPid(existingNegotiation.getProviderPid())
                .offer(counterOffer)
                .build();

        final ResultActions result = mockMvc.perform(
                post("/consumer/negotiations/" + existingNegotiation.getConsumerPid() + "/offers")
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
    public void createNegotiationOfferCounteroffer_negotiationNotFound() throws Exception {
        // Try to send counteroffer for non-existent negotiation
        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(Collections.singletonList(NegotiationMockObjectUtil.CONSTRAINT_COUNT_5))
                .build();

        String nonExistentConsumerPid = createNewId();
        String datasetOfferId = dataset.getHasPolicy().stream().findFirst().get().getId();

        Offer counterOffer = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .build();

        ContractOfferMessage counterofferMessage = ContractOfferMessage.Builder.newInstance()
                .consumerPid(nonExistentConsumerPid)
                .providerPid(createNewId())
                .offer(counterOffer)
                .build();

        final ResultActions result = mockMvc.perform(
                post("/consumer/negotiations/" + nonExistentConsumerPid + "/offers")
                        .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(counterofferMessage)))
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void createNegotiationOfferCounteroffer_invalidState() throws Exception {
        // Setup: Create an existing negotiation in OFFERED state (invalid for counteroffer from provider)
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
                .state(ContractNegotiationState.OFFERED) // Invalid state for counteroffer
                .role(IConstants.ROLE_CONSUMER)
                .build();
        contractNegotiationRepository.save(existingNegotiation);

        Offer counterOffer = Offer.Builder.newInstance()
                .id(datasetOfferId)
                .target(dataset.getId())
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .permission(Collections.singletonList(permission))
                .build();

        ContractOfferMessage counterofferMessage = ContractOfferMessage.Builder.newInstance()
                .consumerPid(existingNegotiation.getConsumerPid())
                .providerPid(existingNegotiation.getProviderPid())
                .offer(counterOffer)
                .build();

        final ResultActions result = mockMvc.perform(
                post("/consumer/negotiations/" + existingNegotiation.getConsumerPid() + "/offers")
                        .content(Objects.requireNonNull(NegotiationSerializer.serializeProtocol(counterofferMessage)))
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        ContractNegotiationErrorMessage errorMessage = NegotiationSerializer.deserializeProtocol(response, ContractNegotiationErrorMessage.class);
        assertNotNull(errorMessage);
    }


}

