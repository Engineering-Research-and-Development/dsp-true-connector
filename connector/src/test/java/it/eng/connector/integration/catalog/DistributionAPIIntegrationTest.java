package it.eng.connector.integration.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.core.type.TypeReference;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.DataService;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.model.Reference;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;

/**
 * Integration tests for DistributionAPIController.
 * Tests the interaction between the controller and other components.
 * Covers critical paths and edge cases including:
 * - Successful retrieval, creation, update, and deletion of distributions
 * - Error handling for non-existent resources
 * - Validation of input data
 * - Authorization checks
 */
public class DistributionAPIIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DistributionRepository distributionRepository;
    
    @Autowired
    private CatalogRepository catalogRepository;
    
    @Autowired
    private DataServiceRepository dataServiceRepository;
    
    private Distribution distribution;
    private Catalog catalog;
    private DataService dataService;
    
    @BeforeEach
    public void setup() {
        // Create a new DataService with unique ID to avoid conflicts
        dataService = DataService.Builder.newInstance()
            .id(createNewId())
            .keyword(Set.of("keyword1", "keyword2"))
            .theme(Set.of("theme1", "theme2"))
            .conformsTo(CatalogMockObjectUtil.CONFORMSTO)
            .creator(CatalogMockObjectUtil.CREATOR)
            .description(Set.of(CatalogMockObjectUtil.MULTILANGUAGE))
            .identifier(CatalogMockObjectUtil.IDENTIFIER)
            .issued(CatalogMockObjectUtil.ISSUED)
            .modified(CatalogMockObjectUtil.MODIFIED)
            .title(CatalogMockObjectUtil.TITLE)
            .endpointDescription("Test endpoint description")
            .endpointURL("http://test-endpoint.com")
            .build();
            
        // Create a new Distribution with reference to DataService
        distribution = Distribution.Builder.newInstance()
            .id(createNewId())
            .title(CatalogMockObjectUtil.TITLE)
            .description(Set.of(CatalogMockObjectUtil.MULTILANGUAGE))
            .issued(CatalogMockObjectUtil.ISSUED)
            .modified(CatalogMockObjectUtil.MODIFIED)
            .format(Reference.Builder.newInstance().id("HTTP:PULL").build())
            .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
            .accessService(Collections.singleton(dataService))
            .build();
            
        // Initialize the Catalog with references to DataService and Distribution
        catalog = Catalog.Builder.newInstance()
            .id(createNewId())
            .title(CatalogMockObjectUtil.TITLE)
            .description(Set.of(CatalogMockObjectUtil.MULTILANGUAGE))
            .service(Collections.singleton(dataService))
            .distribution(Collections.singleton(distribution))
            .build();
            
        // Save all objects to their respective repositories
        dataServiceRepository.save(dataService);
        distributionRepository.save(distribution);
        catalogRepository.save(catalog);
    }
    
    @AfterEach
    public void cleanup() {
        catalogRepository.deleteAll();
        distributionRepository.deleteAll();
        dataServiceRepository.deleteAll();
    }
    
    /**
     * Tests successful retrieval of a distribution by ID.
     * Verifies that the controller correctly interacts with the service layer
     * and returns the expected distribution.
     */
    @Test
    @DisplayName("Get distribution by ID - success")
    @WithUserDetails(TestUtil.API_USER)
    public void getDistributionById_success() throws Exception {
        // Test getting a distribution by ID
        final ResultActions result = mockMvc.perform(
                get(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1 + "/" + distribution.getId())
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        
        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<Distribution> apiResponse = CatalogSerializer.deserializePlain(response, 
            new TypeReference<GenericApiResponse<Distribution>>() {});
        
        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        assertEquals(apiResponse.getData().getId(), distribution.getId());
    }
    
    /**
     * Tests error handling when attempting to retrieve a non-existent distribution.
     * Verifies that the controller correctly handles the not found scenario.
     */
    @Test
    @DisplayName("Get distribution by ID - not found")
    @WithUserDetails(TestUtil.API_USER)
    public void getDistributionById_notFound() throws Exception {
        // Test getting a non-existent distribution
        final ResultActions result = mockMvc.perform(
                get(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1 + "/non-existent-id")
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
    
    /**
     * Tests successful retrieval of all distributions.
     * Verifies that the controller correctly interacts with the service layer
     * and returns the expected list of distributions.
     */
    @Test
    @DisplayName("Get all distributions - success")
    @WithUserDetails(TestUtil.API_USER)
    public void getAllDistributions_success() throws Exception {
        // Test getting all distributions
        final ResultActions result = mockMvc.perform(
                get(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1)
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        
        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<List<Distribution>> apiResponse = CatalogSerializer.deserializePlain(response, 
            new TypeReference<GenericApiResponse<List<Distribution>>>() {});
        
        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        assertTrue(apiResponse.getData().size() > 0);
    }
    
    /**
     * Tests successful creation of a new distribution.
     * Verifies that the controller correctly interacts with the service layer
     * and returns the created distribution.
     */
    @Test
    @DisplayName("Create distribution - success")
    @WithUserDetails(TestUtil.API_USER)
    public void createDistribution_success() throws Exception {
        // Test creating a new distribution
        Distribution newDistribution = Distribution.Builder.newInstance()
                .id(createNewId())
                .title(CatalogMockObjectUtil.TITLE)
                .description(Set.of(CatalogMockObjectUtil.MULTILANGUAGE))
                .issued(CatalogMockObjectUtil.ISSUED)
                .modified(CatalogMockObjectUtil.MODIFIED)
                .format(Reference.Builder.newInstance().id("HTTP:PULL").build())
                .hasPolicy(Set.of(CatalogMockObjectUtil.OFFER))
                .accessService(Collections.singleton(dataService))
                .build();
        
        String distributionJson = CatalogSerializer.serializePlain(newDistribution);
        
        final ResultActions result = mockMvc.perform(
                post(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1)
                .content(distributionJson)
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        
        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<Distribution> apiResponse = CatalogSerializer.deserializePlain(response, 
            new TypeReference<GenericApiResponse<Distribution>>() {});
        
        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        assertEquals(apiResponse.getData().getId(), newDistribution.getId());
    }
    
    /**
     * Tests validation when attempting to create a distribution with invalid data.
     * Verifies that the controller correctly handles the validation error.
     */
    @Test
    @DisplayName("Create distribution - invalid data")
    @WithUserDetails(TestUtil.API_USER)
    public void createDistribution_invalidData() throws Exception {
        // Test creating a distribution with invalid data
        String invalidJson = "{\"invalid\": \"data\"}";
        
        final ResultActions result = mockMvc.perform(
                post(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1)
                .content(invalidJson)
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
    
    /**
     * Tests successful update of an existing distribution.
     * Verifies that the controller correctly interacts with the service layer
     * and returns the updated distribution.
     */
    @Test
    @DisplayName("Update distribution - success")
    @WithUserDetails(TestUtil.API_USER)
    public void updateDistribution_success() throws Exception {
        // Test updating an existing distribution
        // Create a JSON representation of the updated distribution
        String distributionJson = CatalogSerializer.serializePlain(CatalogMockObjectUtil.DISTRIBUTION_FOR_UPDATE);
        
        final ResultActions result = mockMvc.perform(
                put(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1 + "/" + distribution.getId())
                .content(distributionJson)
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        
        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<Distribution> apiResponse = CatalogSerializer.deserializePlain(response, 
            new TypeReference<GenericApiResponse<Distribution>>() {});
        
        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        assertEquals(apiResponse.getData().getId(), distribution.getId());
    }
    
    /**
     * Tests error handling when attempting to update a non-existent distribution.
     * Verifies that the controller correctly handles the not found scenario.
     */
    @Test
    @DisplayName("Update distribution - not found")
    @WithUserDetails(TestUtil.API_USER)
    public void updateDistribution_notFound() throws Exception {
        // Test updating a non-existent distribution
        Distribution updatedDistribution = CatalogMockObjectUtil.DISTRIBUTION_FOR_UPDATE;
        String distributionJson = CatalogSerializer.serializePlain(updatedDistribution);
        
        final ResultActions result = mockMvc.perform(
                put(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1 + "/non-existent-id")
                .content(distributionJson)
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
    
    /**
     * Tests successful deletion of an existing distribution.
     * Verifies that the controller correctly interacts with the service layer
     * and returns a success response.
     */
    @Test
    @DisplayName("Delete distribution - success")
    @WithUserDetails(TestUtil.API_USER)
    public void deleteDistribution_success() throws Exception {
        // Test deleting an existing distribution
        final ResultActions result = mockMvc.perform(
                delete(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1 + "/" + distribution.getId())
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        
        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<Void> apiResponse = CatalogSerializer.deserializePlain(response, 
            new TypeReference<GenericApiResponse<Void>>() {});
        
        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
    }
    
    /**
     * Tests error handling when attempting to delete a non-existent distribution.
     * Verifies that the controller correctly handles the not found scenario.
     */
    @Test
    @DisplayName("Delete distribution - not found")
    @WithUserDetails(TestUtil.API_USER)
    public void deleteDistribution_notFound() throws Exception {
        // Test deleting a non-existent distribution
        final ResultActions result = mockMvc.perform(
                delete(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1 + "/non-existent-id")
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
    
    /**
     * Tests authorization by attempting to access the API without authentication.
     * Verifies that the controller correctly enforces authentication requirements.
     */
    @Test
    @DisplayName("Unauthorized access")
    public void unauthorized_access() throws Exception {
        // Test unauthorized access
        final ResultActions result = mockMvc.perform(
                get(ApiEndpoints.CATALOG_DISTRIBUTIONS_V1)
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isUnauthorized())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
