package it.eng.catalog.rest.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.service.DatasetService;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
public class DatasetAPIControllerTest {

    @InjectMocks
    private DatasetAPIController datasetAPIController;

    @Mock
    private DatasetService datasetService;

    @Test
    @DisplayName("Get dataset by id - success")
    public void getDatasetByIdSuccessfulTest() {
        when(datasetService.getDatasetByIdForApi(CatalogMockObjectUtil.DATASET.getId())).thenReturn(CatalogMockObjectUtil.DATASET);
        ResponseEntity<GenericApiResponse<JsonNode>> response = datasetAPIController.getDatasetById(CatalogMockObjectUtil.DATASET.getId());

        verify(datasetService).getDatasetByIdForApi(CatalogMockObjectUtil.DATASET.getId());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), CatalogMockObjectUtil.DATASET.getType()));
    }

    @Test
    @DisplayName("Get all datasets - success")
    public void getAllDatasetsSuccessfulTest() {
        when(datasetService.getAllDatasets()).thenReturn(CatalogMockObjectUtil.DATASETS);
        ResponseEntity<GenericApiResponse<JsonNode>> response = datasetAPIController.getAllDatasets();

        verify(datasetService).getAllDatasets();
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), CatalogMockObjectUtil.DATASET.getType()));
    }

    @Test
    @DisplayName("Save dataset - success")
    public void saveDatasetSuccessfulTest() {
        String dataset = Serializer.serializePlain(CatalogMockObjectUtil.DATASET);
        when(datasetService.saveDataset(any())).thenReturn(CatalogMockObjectUtil.DATASET);
        ResponseEntity<GenericApiResponse<JsonNode>> response = datasetAPIController.saveDataset(dataset);

        verify(datasetService).saveDataset(any());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().getData().get("type").toString(), CatalogMockObjectUtil.DATASET.getType()));
    }

    @Test
    @DisplayName("Delete dataset - success")
    public void deleteDatasetSuccessfulTest() {
        ResponseEntity<GenericApiResponse<Object>> response = datasetAPIController.deleteDataset(CatalogMockObjectUtil.DATASET.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().getMessage(), "Dataset deleted successfully"));
    }

    @Test
    @DisplayName("Update dataset - success")
    public void updateDatasetSuccessfulTest() {
        String dataset = Serializer.serializePlain(CatalogMockObjectUtil.DATASET_FOR_UPDATE);
        when(datasetService.updateDataset(any(String.class), any())).thenReturn(CatalogMockObjectUtil.DATASET_FOR_UPDATE);
        ResponseEntity<GenericApiResponse<JsonNode>> response = datasetAPIController.updateDataset(CatalogMockObjectUtil.DATASET_FOR_UPDATE.getId(), dataset);

        verify(datasetService).updateDataset(any(String.class), any(Dataset.class));
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().getData().get("type").toString(), CatalogMockObjectUtil.DATASET.getType()));

    }
    
    @Test
    @DisplayName("Get fileId from Dataset - success")
    public void getFileIdFromDatasetSuccessfulTest() {
        when(datasetService.getArtifactIdFromDataset(CatalogMockObjectUtil.DATASET_WITH_FILE_ID.getId())).thenReturn(CatalogMockObjectUtil.DATASET_WITH_FILE_ID.getArtifact());
        ResponseEntity<GenericApiResponse<String>> response = datasetAPIController.getArtifactIdFromDataset(CatalogMockObjectUtil.DATASET_WITH_FILE_ID.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), CatalogMockObjectUtil.DATASET_WITH_FILE_ID.getArtifact()));
    }
    
    @Test
    @DisplayName("Get fileId from Dataset - failed")
    public void getFileIdFromDatasetFailedTest() {
    	when(datasetService.getArtifactIdFromDataset(CatalogMockObjectUtil.DATASET_WITH_FILE_ID.getId())).thenThrow(new ResourceNotFoundAPIException());
    	assertThrows(ResourceNotFoundAPIException.class,() -> datasetAPIController.getArtifactIdFromDataset(CatalogMockObjectUtil.DATASET_WITH_FILE_ID.getId()));
    }
    
    @Test
    @DisplayName("Get formats from Dataset - success")
    public void getFormatsFromDatasetSuccessfulTest() {
        when(datasetService.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_WITH_FILE_ID.getId())).thenReturn(CatalogMockObjectUtil.DATASET_WITH_FILE_ID.getDistribution().stream().map(dist -> dist.getFormat().getId()).collect(Collectors.toList()));
        ResponseEntity<GenericApiResponse<List<String>>> response = datasetAPIController.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_WITH_FILE_ID.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().getData().get(0),
        		CatalogMockObjectUtil.DATASET_WITH_FILE_ID.getDistribution().stream().findFirst().get().getFormat().getId()));
    }
    
    @Test
    @DisplayName("Get formats from Dataset - failed")
    public void getFormatsFromDatasetFailedTest() {
        when(datasetService.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_WITH_FILE_ID.getId())).thenThrow(new ResourceNotFoundAPIException());
        assertThrows(ResourceNotFoundAPIException.class,() -> datasetAPIController.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_WITH_FILE_ID.getId()));
    }

}
