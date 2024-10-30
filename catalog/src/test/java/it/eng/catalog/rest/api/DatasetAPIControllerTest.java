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
import it.eng.catalog.util.MockObjectUtil;
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
        when(datasetService.getDatasetByIdForApi(MockObjectUtil.DATASET.getId())).thenReturn(MockObjectUtil.DATASET);
        ResponseEntity<GenericApiResponse<JsonNode>> response = datasetAPIController.getDatasetById(MockObjectUtil.DATASET.getId());

        verify(datasetService).getDatasetByIdForApi(MockObjectUtil.DATASET.getId());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.DATASET.getType()));
    }

    @Test
    @DisplayName("Get all datasets - success")
    public void getAllDatasetsSuccessfulTest() {
        when(datasetService.getAllDatasets()).thenReturn(MockObjectUtil.DATASETS);
        ResponseEntity<GenericApiResponse<JsonNode>> response = datasetAPIController.getAllDatasets();

        verify(datasetService).getAllDatasets();
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.DATASET.getType()));
    }

    @Test
    @DisplayName("Save dataset - success")
    public void saveDatasetSuccessfulTest() {
        String dataset = Serializer.serializePlain(MockObjectUtil.DATASET);
        when(datasetService.saveDataset(any())).thenReturn(MockObjectUtil.DATASET);
        ResponseEntity<GenericApiResponse<JsonNode>> response = datasetAPIController.saveDataset(dataset);

        verify(datasetService).saveDataset(any());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().getData().get("type").toString(), MockObjectUtil.DATASET.getType()));
    }

    @Test
    @DisplayName("Delete dataset - success")
    public void deleteDatasetSuccessfulTest() {
        ResponseEntity<GenericApiResponse<Object>> response = datasetAPIController.deleteDataset(MockObjectUtil.DATASET.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().getMessage(), "Dataset deleted successfully"));
    }

    @Test
    @DisplayName("Update dataset - success")
    public void updateDatasetSuccessfulTest() {
        String dataset = Serializer.serializePlain(MockObjectUtil.DATASET_FOR_UPDATE);
        when(datasetService.updateDataset(any(String.class), any())).thenReturn(MockObjectUtil.DATASET_FOR_UPDATE);
        ResponseEntity<GenericApiResponse<JsonNode>> response = datasetAPIController.updateDataset(MockObjectUtil.DATASET_FOR_UPDATE.getId(), dataset);

        verify(datasetService).updateDataset(any(String.class), any(Dataset.class));
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().getData().get("type").toString(), MockObjectUtil.DATASET.getType()));

    }
    
    @Test
    @DisplayName("Get fileId from Dataset - success")
    public void getFileIdFromDatasetSuccessfulTest() {
        when(datasetService.getFileIdFromDataset(MockObjectUtil.DATASET_WITH_FILE_ID.getId())).thenReturn(MockObjectUtil.DATASET_WITH_FILE_ID.getFileId());
        ResponseEntity<GenericApiResponse<String>> response = datasetAPIController.getFileIdFromDataset(MockObjectUtil.DATASET_WITH_FILE_ID.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.DATASET_WITH_FILE_ID.getFileId()));
    }
    
    @Test
    @DisplayName("Get formats from Dataset - success")
    public void getFormatsFromDatasetSuccessfulTest() {
        when(datasetService.getFormatsFromDataset(MockObjectUtil.DATASET_WITH_FILE_ID.getId())).thenReturn(MockObjectUtil.DATASET_WITH_FILE_ID.getDistribution().stream().map(dist -> dist.getFormat().getId()).collect(Collectors.toList()));
        ResponseEntity<GenericApiResponse<List<String>>> response = datasetAPIController.getFormatsFromDataset(MockObjectUtil.DATASET_WITH_FILE_ID.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().getData().get(0),
        		MockObjectUtil.DATASET_WITH_FILE_ID.getDistribution().stream().findFirst().get().getFormat().getId()));
    }
    
    @Test
    @DisplayName("Get fileId from Dataset - failed")
    public void getFileIdFromDatasetFailedTest() {
        when(datasetService.getFileIdFromDataset(MockObjectUtil.DATASET_WITH_FILE_ID.getId())).thenThrow(new ResourceNotFoundAPIException());
        assertThrows(ResourceNotFoundAPIException.class,() -> datasetAPIController.getFileIdFromDataset(MockObjectUtil.DATASET_WITH_FILE_ID.getId()));
    }
    
    @Test
    @DisplayName("Get formats from Dataset - failed")
    public void getFormatsFromDatasetFailedTest() {
        when(datasetService.getFormatsFromDataset(MockObjectUtil.DATASET_WITH_FILE_ID.getId())).thenThrow(new ResourceNotFoundAPIException());
        assertThrows(ResourceNotFoundAPIException.class,() -> datasetAPIController.getFormatsFromDataset(MockObjectUtil.DATASET_WITH_FILE_ID.getId()));
    }

}
