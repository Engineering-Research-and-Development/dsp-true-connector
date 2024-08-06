package it.eng.catalog.rest.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.model.Distribution;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.service.DistributionService;
import it.eng.catalog.util.MockObjectUtil;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
public class DistributionAPIControllerTest {

    @InjectMocks
    private DistributionAPIController distributionAPIController;

    @Mock
    private DistributionService distributionService;


    @Test
    @DisplayName("Get distribution by id - success")
    public void getDistributionById_success() {
        when(distributionService.getDistributionById(MockObjectUtil.DISTRIBUTION.getId())).thenReturn(MockObjectUtil.DISTRIBUTION);
        ResponseEntity<GenericApiResponse<JsonNode>> response = distributionAPIController.getDistributionById(MockObjectUtil.DISTRIBUTION.getId());

        verify(distributionService).getDistributionById(MockObjectUtil.DISTRIBUTION.getId());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.DISTRIBUTION.getType()));
    }

    @Test
    @DisplayName("Get all distributions - success")
    public void getAllDistributions_success() {
        when(distributionService.getAllDistributions()).thenReturn(MockObjectUtil.DISTRIBUTIONS);
        ResponseEntity<GenericApiResponse<JsonNode>> response = distributionAPIController.getAllDistributions();

        verify(distributionService).getAllDistributions();
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.DISTRIBUTION.getType()));
    }

    @Test
    @DisplayName("Save distribution - success")
    public void saveDistribution_success() {
        String distribution = Serializer.serializePlain(MockObjectUtil.DISTRIBUTION);
        when(distributionService.saveDistribution(any())).thenReturn(MockObjectUtil.DISTRIBUTION);
        ResponseEntity<GenericApiResponse<JsonNode>> response = distributionAPIController.saveDistribution(distribution);

        verify(distributionService).saveDistribution(any());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().getData().get("type").toString(), MockObjectUtil.DISTRIBUTION.getType()));
    }

    @Test
    @DisplayName("Delete distribution - success")
    public void deleteDistribution_success() {
        ResponseEntity<GenericApiResponse<Object>> response = distributionAPIController.deleteDistribution(MockObjectUtil.DISTRIBUTION.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().getMessage(), "Distribution deleted successfully"));
    }

    @Test
    @DisplayName("Update distribution - success")
    public void updateDistribution_success() {
        String distribution = Serializer.serializePlain(MockObjectUtil.DISTRIBUTION_FOR_UPDATE);
        when(distributionService.updateDistribution(any(String.class), any())).thenReturn(MockObjectUtil.DISTRIBUTION_FOR_UPDATE);
        ResponseEntity<GenericApiResponse<JsonNode>> response = distributionAPIController.updateDistribution(MockObjectUtil.DISTRIBUTION_FOR_UPDATE.getId(), distribution);

        verify(distributionService).updateDistribution(any(String.class), any(Distribution.class));
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().getData().get("type").toString(), MockObjectUtil.DISTRIBUTION.getType()));

    }
}

