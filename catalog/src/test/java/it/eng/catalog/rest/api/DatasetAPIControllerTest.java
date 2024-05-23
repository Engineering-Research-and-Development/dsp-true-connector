package it.eng.catalog.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.service.DatasetService;
import it.eng.catalog.util.MockObjectUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DatasetAPIControllerTest {

    @InjectMocks
    DatasetAPIController datasetAPIController;

    @Mock
    private DatasetService datasetService;

    @Test
    public void getDatasetByIdSuccessfulTest() {
        when(datasetService.getDatasetById(MockObjectUtil.DATASET.getId())).thenReturn(MockObjectUtil.DATASET);
        ResponseEntity<JsonNode> response = datasetAPIController.getDatasetById(MockObjectUtil.DATASET.getId());

        verify(datasetService).getDatasetById(MockObjectUtil.DATASET.getId());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.DATASET.getType()));
    }

    @Test
    public void getAllDatasetsSuccessfulTest() {
        when(datasetService.getAllDatasets()).thenReturn(MockObjectUtil.DATASETS);
        ResponseEntity<JsonNode> response = datasetAPIController.getAllDatasets();

        verify(datasetService).getAllDatasets();
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.DATASET.getType()));
    }

    @Test
    public void saveDatasetSuccessfulTest() {
        String dataset = Serializer.serializePlain(MockObjectUtil.DATASET);
        when(datasetService.saveDataset(any())).thenReturn(MockObjectUtil.DATASET);
        ResponseEntity<JsonNode> response = datasetAPIController.saveDataset(dataset);

        verify(datasetService).saveDataset(any());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().get("type").toString(), MockObjectUtil.DATASET.getType()));
    }

    @Test
    public void deleteDatasetSuccessfulTest() {
        ResponseEntity<String> response = datasetAPIController.deleteDataset(MockObjectUtil.DATASET.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody(), "Dataset deleted successfully"));
    }

    @Test
    public void updateDatasetSuccessfulTest() {
        String dataset = Serializer.serializePlain(MockObjectUtil.DATASET_FOR_UPDATE);
        when(datasetService.updateDataset(any(String.class), any())).thenReturn(MockObjectUtil.DATASET_FOR_UPDATE);
        ResponseEntity<JsonNode> response = datasetAPIController.updateDataset(MockObjectUtil.DATASET_FOR_UPDATE.getId(), dataset);

        verify(datasetService).updateDataset(any(String.class), any(Dataset.class));
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().get("type").toString(), MockObjectUtil.DATASET.getType()));

    }

}
