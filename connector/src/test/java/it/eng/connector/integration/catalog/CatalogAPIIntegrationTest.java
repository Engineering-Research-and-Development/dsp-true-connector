package it.eng.connector.integration.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.WireMockServer;
import it.eng.catalog.model.*;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.wiremock.spring.InjectWireMock;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CatalogAPIIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CatalogRepository catalogRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DistributionRepository distributionRepository;

    @Autowired
    private DataServiceRepository dataServiceRepository;

    @InjectWireMock
    private WireMockServer wiremock;

    private Catalog catalog;
    private Dataset dataset;
    private Catalog newCatalog;
    private Distribution distribution;
    private DataService dataService;

    @BeforeEach
    public void setup() {
        distribution = Distribution.Builder.newInstance()
                .description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("Distribution description").build())
                        .stream().collect(Collectors.toCollection(HashSet::new)))
                .issued(CatalogMockObjectUtil.ISSUED)
                .modified(CatalogMockObjectUtil.MODIFIED)
                .title(CatalogMockObjectUtil.TITLE)
                .accessService(Collections.singleton(CatalogMockObjectUtil.DATA_SERVICE))
                .build();

        dataService = DataService.Builder.newInstance()
                .keyword(Arrays.asList("keyword1", "keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
                .theme(Arrays.asList("white", "blue", "aqua").stream().collect(Collectors.toCollection(HashSet::new)))
                .conformsTo(CatalogMockObjectUtil.CONFORMSTO)
                .creator(CatalogMockObjectUtil.CREATOR)
                .description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("DataService description").build())
                        .stream().collect(Collectors.toCollection(HashSet::new)))
                .identifier(CatalogMockObjectUtil.IDENTIFIER)
                .issued(CatalogMockObjectUtil.ISSUED)
                .modified(CatalogMockObjectUtil.MODIFIED)
                .title(CatalogMockObjectUtil.TITLE)
                .endpointDescription("Description for test")
                .endpointURL(CatalogMockObjectUtil.ENDPOINT_URL)
                .build();

        dataset = Dataset.Builder.newInstance()
                .conformsTo(CatalogMockObjectUtil.CONFORMSTO)
                .creator(CatalogMockObjectUtil.CREATOR)
                .description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("Dataset description").build())
                        .stream().collect(Collectors.toCollection(HashSet::new)))
                .identifier(CatalogMockObjectUtil.IDENTIFIER)
                .keyword(Arrays.asList("keyword1", "keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
                .theme(Arrays.asList("white", "blue").stream().collect(Collectors.toCollection(HashSet::new)))
                .title(CatalogMockObjectUtil.TITLE)
                .hasPolicy(Collections.singleton(CatalogMockObjectUtil.OFFER))
                .build();

        catalog = Catalog.Builder.newInstance()
                .conformsTo(CatalogMockObjectUtil.CONFORMSTO)
                .creator(CatalogMockObjectUtil.CREATOR)
                .description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("Catalog description").build())
                        .stream().collect(Collectors.toCollection(HashSet::new)))
                .identifier(CatalogMockObjectUtil.IDENTIFIER)
                .keyword(Arrays.asList("keyword1", "keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
                .theme(Arrays.asList("white", "blue").stream().collect(Collectors.toCollection(HashSet::new)))
                .title(CatalogMockObjectUtil.TITLE)
                .participantId("urn:example:DataProviderA")
                .service(Collections.singleton(dataService))
                .dataset(Collections.singleton(dataset))
                .distribution(Collections.singleton(distribution))
                .hasPolicy(Arrays.asList(CatalogMockObjectUtil.OFFER).stream().collect(Collectors.toCollection(HashSet::new)))
                .homepage(CatalogMockObjectUtil.ENDPOINT_URL)
                .createdBy("admin@mail.com")
                .lastModifiedBy("admin@mail.com")
                .issued(CatalogMockObjectUtil.ISSUED)
                .modified(CatalogMockObjectUtil.MODIFIED)
                .build();

        newCatalog = Catalog.Builder.newInstance()
                .conformsTo(CatalogMockObjectUtil.CONFORMSTO)
                .creator(CatalogMockObjectUtil.CREATOR)
                .description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("Catalog description update").build())
                        .stream().collect(Collectors.toCollection(HashSet::new)))
                .identifier(CatalogMockObjectUtil.IDENTIFIER)
                .keyword(Arrays.asList("keyword1", "keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
                .theme(Arrays.asList("white", "blue", "aqua").stream().collect(Collectors.toCollection(HashSet::new)))
                .title(CatalogMockObjectUtil.TITLE)
                .participantId("urn:example:DataProviderA")
                .service(Arrays.asList(CatalogMockObjectUtil.DATA_SERVICE_FOR_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
                .dataset(Arrays.asList(CatalogMockObjectUtil.DATASET).stream().collect(Collectors.toCollection(HashSet::new)))
                .distribution(Arrays.asList(CatalogMockObjectUtil.DISTRIBUTION_FOR_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
                .hasPolicy(Arrays.asList(CatalogMockObjectUtil.OFFER_WITH_TARGET).stream().collect(Collectors.toCollection(HashSet::new)))
                .homepage(CatalogMockObjectUtil.ENDPOINT_URL)
                .createdBy("admin@mail.com")
                .lastModifiedBy("admin@mail.com")
                .issued(CatalogMockObjectUtil.ISSUED)
                .modified(CatalogMockObjectUtil.MODIFIED)
                .build();

        datasetRepository.save(dataset);
        distributionRepository.save(distribution);
        dataServiceRepository.save(dataService);
        catalogRepository.save(catalog);
    }

    @AfterEach
    public void cleanup() {
        datasetRepository.deleteAll();
        catalogRepository.deleteAll();
        distributionRepository.deleteAll();
        dataServiceRepository.deleteAll();
    }

    @Test
    @DisplayName("Get catalog by ID - success")
    public void getCatalogById_success() throws Exception {
        final ResultActions result = mockMvc.perform(
                adminGet(ApiEndpoints.CATALOG_CATALOGS_V1 + "/" + catalog.getId())
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<Catalog> apiResponse = CatalogSerializer.deserializePlain(response,
                new TypeReference<GenericApiResponse<Catalog>>() {});

        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        assertEquals(apiResponse.getData().getId(),catalog.getId());
        assertCatalogFields(apiResponse.getData(), catalog);
    }

    @Test
    @DisplayName("Get API catalog - success")
    public void getAllCatalogs_success() throws Exception {
        final ResultActions result = mockMvc.perform(
                adminGet(ApiEndpoints.CATALOG_CATALOGS_V1)
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<Catalog> apiResponse = CatalogSerializer.deserializePlain(response,
                new TypeReference<GenericApiResponse<Catalog>>() {});

        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        assertEquals(apiResponse.getData().getId(),catalog.getId());
        assertCatalogFields(apiResponse.getData(), catalog);
    }

    @Test
    @DisplayName("Create catalog - success")
    public void createCatalog_success() throws Exception {
        String catalogJson = CatalogSerializer.serializePlain(newCatalog);

        final ResultActions result = mockMvc.perform(
                adminPost(ApiEndpoints.CATALOG_CATALOGS_V1)
                        .content(catalogJson)
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<Catalog> apiResponse = CatalogSerializer.deserializePlain(response,
                new TypeReference<GenericApiResponse<Catalog>>() {});

        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        assertEquals(apiResponse.getData().getId(),newCatalog.getId());
        assertCatalogFields(apiResponse.getData(), newCatalog);
    }

    @Test
    @DisplayName("Update catalog - success")
    public void updateCatalog_success() throws Exception {
        String catalogJson = CatalogSerializer.serializePlain(newCatalog);

        final ResultActions result = mockMvc.perform(
                adminPut(ApiEndpoints.CATALOG_CATALOGS_V1 + "/" + catalog.getId())
                        .content(catalogJson)
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<Catalog> apiResponse = CatalogSerializer.deserializePlain(response,
                new TypeReference<GenericApiResponse<Catalog>>() {});

        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        assertEquals(apiResponse.getData().getId(),catalog.getId());
        assertCatalogFields(apiResponse.getData(), newCatalog);
    }

    @Test
    @DisplayName("Delete catalog - success")
    public void deleteCatalog_success() throws Exception {
        final ResultActions result = mockMvc.perform(
                adminDelete(ApiEndpoints.CATALOG_CATALOGS_V1 + "/" + catalog.getId())
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<Void> apiResponse = CatalogSerializer.deserializePlain(response,
                new TypeReference<GenericApiResponse<Void>>() {});

        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertTrue(catalogRepository.findById(catalog.getId()).isEmpty());
    }

    @Test
    @DisplayName("Unauthorized access")
    public void unauthorized_access() throws Exception {
        final ResultActions result = mockMvc.perform(
                get(ApiEndpoints.CATALOG_CATALOGS_V1)
                        .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    private void assertCatalogFields(Catalog responseCatalog, Catalog expectedCatalog) {
        assertNotNull(responseCatalog);
        assertTrue(responseCatalog.getConformsTo().equals(expectedCatalog.getConformsTo()));
        assertTrue(responseCatalog.getCreator().equals(expectedCatalog.getCreator()));
        assertTrue(responseCatalog.getIdentifier().equals(expectedCatalog.getIdentifier()));
        assertTrue(responseCatalog.getTitle().equals(expectedCatalog.getTitle()));
        assertTrue(responseCatalog.getParticipantId().equals(expectedCatalog.getParticipantId()));
        assertTrue(responseCatalog.getHomepage().equals(expectedCatalog.getHomepage()));

        // Check description
        assertNotNull(responseCatalog.getDescription());
        assertTrue(responseCatalog.getDescription().size() == expectedCatalog.getDescription().size());
        responseCatalog.getDescription().forEach(desc -> {
            assertTrue(expectedCatalog.getDescription().stream()
                    .anyMatch(expectedDesc -> expectedDesc.getLanguage().equals(desc.getLanguage())
                            && expectedDesc.getValue().equals(desc.getValue())));
        });

        // Check keywords and themes
        assertTrue(responseCatalog.getKeyword().containsAll(expectedCatalog.getKeyword()));
        assertTrue(responseCatalog.getTheme().containsAll(expectedCatalog.getTheme()));

        // Check datasets
        assertNotNull(responseCatalog.getDataset());
        assertTrue(responseCatalog.getDataset().size() == expectedCatalog.getDataset().size());
        responseCatalog.getDataset().forEach(dataset -> {
            assertTrue(expectedCatalog.getDataset().stream()
                    .anyMatch(expectedDataset -> expectedDataset.getId().equals(dataset.getId())));
        });

        // Check distributions
        assertNotNull(responseCatalog.getDistribution());
        assertTrue(responseCatalog.getDistribution().size() == expectedCatalog.getDistribution().size());
        responseCatalog.getDistribution().forEach(dist -> {
            assertTrue(expectedCatalog.getDistribution().stream()
                    .anyMatch(expectedDist -> expectedDist.getId().equals(dist.getId())
                            && expectedDist.getTitle().equals(dist.getTitle())));
        });

        // Check services
        assertNotNull(responseCatalog.getService());
        assertTrue(responseCatalog.getService().size() == expectedCatalog.getService().size());
        responseCatalog.getService().forEach(service -> {
            assertTrue(expectedCatalog.getService().stream()
                    .anyMatch(expectedService -> expectedService.getId().equals(service.getId())
                            && expectedService.getEndpointURL().equals(service.getEndpointURL())));
        });

        // Check policies
        assertNotNull(responseCatalog.getHasPolicy());
        assertTrue(responseCatalog.getHasPolicy().size() == expectedCatalog.getHasPolicy().size());
        responseCatalog.getHasPolicy().forEach(policy -> {
            assertTrue(expectedCatalog.getHasPolicy().stream()
                    .anyMatch(expectedPolicy -> expectedPolicy.getId().equals(policy.getId())));
        });
    }
}