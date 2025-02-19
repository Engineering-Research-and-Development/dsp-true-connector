package it.eng.catalog.rest.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
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
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.DatasetService;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.model.Artifact;
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
        String dataset = CatalogSerializer.serializePlain(CatalogMockObjectUtil.DATASET);
        when(datasetService.saveDataset(any(), isNull(), anyString(), isNull())).thenReturn(CatalogMockObjectUtil.DATASET);
        ResponseEntity<GenericApiResponse<JsonNode>> response = datasetAPIController.saveDataset(null, CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null, dataset);

        verify(datasetService).saveDataset(any(), isNull(), anyString(), isNull());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().getData().get("type").toString(), CatalogMockObjectUtil.DATASET.getType()));
    }

    @Test
    @DisplayName("Delete dataset - success")
    public void deleteDatasetSuccessfulTest() {
        ResponseEntity<GenericApiResponse<Void>> response = datasetAPIController.deleteDataset(CatalogMockObjectUtil.DATASET.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().getMessage(), "Dataset deleted successfully"));
    }

    @Test
    @DisplayName("Update dataset - success")
    public void updateDatasetSuccessfulTest() {
        String dataset = CatalogSerializer.serializePlain(CatalogMockObjectUtil.DATASET_FOR_UPDATE);
        when(datasetService.updateDataset(any(String.class), any(Dataset.class), isNull(), anyString(), isNull())).thenReturn(CatalogMockObjectUtil.DATASET_FOR_UPDATE);
        ResponseEntity<GenericApiResponse<JsonNode>> response = 
        		datasetAPIController.updateDataset(CatalogMockObjectUtil.DATASET_FOR_UPDATE.getId(),
        				null,
        				CatalogMockObjectUtil.ARTIFACT_EXTERNAL.getValue(),
        				null,
        				dataset);

        verify(datasetService).updateDataset(any(String.class), any(Dataset.class), isNull(), anyString(), isNull());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().getData().get("type").toString(), CatalogMockObjectUtil.DATASET.getType()));

    }
    
    @Test
    @DisplayName("Get artifact from Dataset - success")
    public void getArtifactFromDatasetSuccessfulTest() {
        when(datasetService.getArtifactFromDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId())).thenReturn(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getArtifact());
        ResponseEntity<GenericApiResponse<JsonNode>> response = datasetAPIController.getArtifactFromDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(CatalogSerializer.deserializePlain(response.getBody().getData(), Artifact.class), CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getArtifact());
    }
    
    @Test
    @DisplayName("Get artifact from Dataset - failed")
    public void getArtifactFromDatasetFailedTest() {
    	when(datasetService.getArtifactFromDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId())).thenThrow(new ResourceNotFoundAPIException());
    	assertThrows(ResourceNotFoundAPIException.class,() -> datasetAPIController.getArtifactFromDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId()));
    }

    @Test
    @DisplayName("Get formats from Dataset - success")
    public void getFormatsFromDatasetSuccessfulTest() {
        when(datasetService.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId())).thenReturn(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getDistribution().stream().map(dist -> dist.getFormat().getId()).collect(Collectors.toList()));
        ResponseEntity<GenericApiResponse<List<String>>> response = datasetAPIController.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().getData().get(0),
        		CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getDistribution().stream().findFirst().get().getFormat().getId()));
    }
    
    @Test
    @DisplayName("Get formats from Dataset - failed")
    public void getFormatsFromDatasetFailedTest() {
        when(datasetService.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId())).thenThrow(new ResourceNotFoundAPIException());
        assertThrows(ResourceNotFoundAPIException.class,() -> datasetAPIController.getFormatsFromDataset(CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId()));
    }

}
