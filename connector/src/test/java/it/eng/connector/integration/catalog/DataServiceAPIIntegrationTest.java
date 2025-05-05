package it.eng.connector.integration.catalog;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.core.type.TypeReference;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.DataService;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.catalog.util.DataServiceUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;

/**
 * Integration tests for DataServiceAPIController.
 * Tests the interaction between the controller and other components.
 * Covers critical paths and edge cases including:
 * - Successful retrieval, creation, update, and deletion of data services
 * - Error handling for non-existent resources
 * - Validation of input data
 * - Authorization checks
 */
public class DataServiceAPIIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DataServiceRepository dataServiceRepository;
    
    @Autowired
    private CatalogRepository catalogRepository;
    
    private DataService dataService;
    private Catalog catalog;
    
    @BeforeEach
    public void setup() {
        // Create a new DataService object instead of using the static one to avoid OptimisticLockingFailure
        dataService = DataService.Builder.newInstance()
            .id(createNewId())
            .keyword(Set.of("DataService keyword1", "DataService keyword2"))
            .theme(Set.of("DataService theme1", "DataService theme2"))
            .conformsTo(CatalogMockObjectUtil.CONFORMSTO)
            .creator(CatalogMockObjectUtil.CREATOR)
            .description(Set.of(CatalogMockObjectUtil.MULTILANGUAGE))
            .identifier(CatalogMockObjectUtil.IDENTIFIER)
            .issued(CatalogMockObjectUtil.ISSUED)
            .modified(CatalogMockObjectUtil.MODIFIED)
            .title(CatalogMockObjectUtil.TITLE)
            .endpointURL("http://dataservice.com")
            .endpointDescription("endpoint description")
            .build();
            
        // Initialize the Catalog before adding, deleting, or updating dataservice
        catalog = Catalog.Builder.newInstance()
            .service(Collections.singleton(dataService))
            .build();
            
        dataServiceRepository.save(dataService);
        catalogRepository.save(catalog);
    }
    
    @AfterEach
    public void cleanup() {
        dataServiceRepository.deleteAll();
        catalogRepository.deleteAll();
    }
    
    /**
     * Tests successful retrieval of a data service by ID.
     * Verifies that the controller correctly interacts with the service layer
     * and returns the expected data service
     */
    @Test
    @DisplayName("Get data service by ID - success")
    @WithUserDetails(TestUtil.API_USER)
    public void getDataServiceById_success() throws Exception {
        // Test getting a data service by ID
        final ResultActions result = mockMvc.perform(
                get(ApiEndpoints.CATALOG_DATA_SERVICES_V1 + "/" + dataService.getId())
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        
        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<DataService> apiResponse = CatalogSerializer.deserializePlain(response, new TypeReference<GenericApiResponse<DataService>>() {});
        
        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        assertTrue(apiResponse.getData().getId().equals(dataService.getId()));
    }
    
    /**
     * Tests error handling when attempting to retrieve a non-existent data service.
     * Verifies that the controller correctly handles the not found scenario
     */
    @Test
    @DisplayName("Get data service by ID - not found")
    @WithUserDetails(TestUtil.API_USER)
    public void getDataServiceById_notFound() throws Exception {
        // Test getting a non-existent data service
        final ResultActions result = mockMvc.perform(
                get(ApiEndpoints.CATALOG_DATA_SERVICES_V1 + "/non-existent-id")
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
    
    /**
     * Tests successful retrieval of all data services.
     * Verifies that the controller correctly interacts with the service layer
     * and returns the expected list of data services
     */
    @Test
    @DisplayName("Get all data services - success")
    @WithUserDetails(TestUtil.API_USER)
    public void getAllDataServices_success() throws Exception {
        // Test getting all data services
        final ResultActions result = mockMvc.perform(
                get(ApiEndpoints.CATALOG_DATA_SERVICES_V1)
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        
        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<java.util.List<DataService>> apiResponse = CatalogSerializer.deserializePlain(response, 
            new TypeReference<GenericApiResponse<java.util.List<DataService>>>() {});
        
        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        assertTrue(apiResponse.getData().size() > 0);
    }
    
    /**
     * Tests successful creation of a new data service.
     * Verifies that the controller correctly interacts with the service layer
     * and returns the created data service
     */
    @Test
    @DisplayName("Create data service - success")
    @WithUserDetails(TestUtil.API_USER)
    public void createDataService_success() throws Exception {
        // Test creating a new data service
        DataService newDataService = DataServiceUtil.DATA_SERVICE_UPDATE;
        String dataServiceJson = CatalogSerializer.serializePlain(newDataService);
        
        final ResultActions result = mockMvc.perform(
                post(ApiEndpoints.CATALOG_DATA_SERVICES_V1)
                .content(dataServiceJson)
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        
        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<DataService> apiResponse = CatalogSerializer.deserializePlain(response, 
            new TypeReference<GenericApiResponse<DataService>>() {});
        
        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        // check id is the same
        assertTrue(apiResponse.getData().getId().equals(newDataService.getId()));
        assertTrue(apiResponse.getData().getTitle().contains(newDataService.getTitle()));
    }
    
    /**
     * Tests validation when attempting to create a data service with invalid data.
     * Verifies that the controller correctly handles the validation error
     */
    @Test
    @DisplayName("Create data service - invalid data")
    @WithUserDetails(TestUtil.API_USER)
    @Disabled("Until Serializer is fixed to throw exception in case of invalid string vs default object with all fields null")
    public void createDataService_invalidData() throws Exception {
        // Test creating a data service with invalid data
        String invalidJson = "{\"invalid\": \"data\"}";
        
        final ResultActions result = mockMvc.perform(
                post(ApiEndpoints.CATALOG_DATA_SERVICES_V1)
                .content(invalidJson)
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
    
    /**
     * Tests successful update of an existing data service.
     * Verifies that the controller correctly interacts with the service layer
     * and returns the updated data service
     */
    @Test
    @DisplayName("Update data service - success")
    @WithUserDetails(TestUtil.API_USER)
    public void updateDataService_success() throws Exception {
        // Test updating an existing data service
        // Create a JSON representation of the updated data service
        String dataServiceJson = CatalogSerializer.serializePlain(DataServiceUtil.DATA_SERVICE_UPDATE);
        
        final ResultActions result = mockMvc.perform(
                put(ApiEndpoints.CATALOG_DATA_SERVICES_V1 + "/" + dataService.getId())
                .content(dataServiceJson)
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        
        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<DataService> apiResponse = CatalogSerializer.deserializePlain(response, 
            new TypeReference<GenericApiResponse<DataService>>() {});
        
        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        // check id is the same
        assertTrue(apiResponse.getData().getId().equals(dataService.getId()));
        assertTrue(apiResponse.getData().getTitle().contains(DataServiceUtil.DATA_SERVICE_UPDATE.getTitle()));
    }
    
    /**
     * Tests error handling when attempting to update a non-existent data service.
     * Verifies that the controller correctly handles the not found scenario
     */
    @Test
    @DisplayName("Update data service - not found")
    @WithUserDetails(TestUtil.API_USER)
    public void updateDataService_notFound() throws Exception {
        // Test updating a non-existent data service
        DataService updatedDataService = DataServiceUtil.DATA_SERVICE_UPDATE;
        String dataServiceJson = CatalogSerializer.serializePlain(updatedDataService);
        
        final ResultActions result = mockMvc.perform(
                put(ApiEndpoints.CATALOG_DATA_SERVICES_V1 + "/non-existent-id")
                .content(dataServiceJson)
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
    
    /**
     * Tests successful deletion of an existing data service.
     * Verifies that the controller correctly interacts with the service layer
     * and returns a success response
     */
    @Test
    @DisplayName("Delete data service - success")
    @WithUserDetails(TestUtil.API_USER)
    public void deleteDataService_success() throws Exception {
        // Test deleting an existing data service
        final ResultActions result = mockMvc.perform(
                delete(ApiEndpoints.CATALOG_DATA_SERVICES_V1 + "/" + dataService.getId())
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        
        String response = result.andReturn().getResponse().getContentAsString();
        GenericApiResponse<Void> apiResponse = CatalogSerializer.deserializePlain(response, 
            new TypeReference<GenericApiResponse<Void>>() {});
        
        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        // check in dataserviceRepository not found
        assertTrue(dataServiceRepository.findById(dataService.getId()).isEmpty());
    }
    
    /**
     * Tests error handling when attempting to delete a non-existent data service.
     * Verifies that the controller correctly handles the not found scenario
     */
    @Test
    @DisplayName("Delete data service - not found")
    @WithUserDetails(TestUtil.API_USER)
    public void deleteDataService_notFound() throws Exception {
        // Test deleting a non-existent data service
        final ResultActions result = mockMvc.perform(
                delete(ApiEndpoints.CATALOG_DATA_SERVICES_V1 + "/non-existent-id")
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
    
    /**
     * Tests authorization by attempting to access the API without authentication.
     * Verifies that the controller correctly enforces authentication requirements
     */
    @Test
    @DisplayName("Unauthorized access")
    public void unauthorized_access() throws Exception {
        // Test unauthorized access
        final ResultActions result = mockMvc.perform(
                get(ApiEndpoints.CATALOG_DATA_SERVICES_V1)
                .contentType(MediaType.APPLICATION_JSON));
        
        result.andExpect(status().isUnauthorized())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
