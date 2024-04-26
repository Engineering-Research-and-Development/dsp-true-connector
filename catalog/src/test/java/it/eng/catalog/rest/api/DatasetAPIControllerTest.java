package it.eng.catalog.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.rest.api.DatasetAPIController;
import it.eng.catalog.service.DataSetService;
import it.eng.catalog.util.MockObjectUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DatasetAPIControllerTest {

    DatasetAPIController datasetAPIController;

    @Mock
    private DataSetService dataSetService;

    @BeforeEach
    public void init() {
        datasetAPIController = new DatasetAPIController(dataSetService);
    }


    @Test
    public void getDatasetByIdSuccessfulTest() {
        when(dataSetService.getDataSetById(MockObjectUtil.DATASET.getId())).thenReturn(MockObjectUtil.DATASET);
        ResponseEntity<JsonNode> response = datasetAPIController.getDatasetById(MockObjectUtil.DATASET.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.DATASET.getType()));
    }

    @Test
    public void getAllDatasetsSuccessfulTest() {
        when(dataSetService.getAllDataSets()).thenReturn(MockObjectUtil.DATASETS);
        ResponseEntity<JsonNode> response = datasetAPIController.getAllDatasets();

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.DATASET.getType()));
    }

//    @Test
//    public void saveDatasetSuccessfulTest() {
//        ResponseEntity<String> response = datasetAPIController.saveDataset(MockObjectUtil.DATASET.toString());
//
//        assertNotNull(response);
//        assertTrue(response.getStatusCode().is2xxSuccessful());
//        assertTrue(StringUtils.contains(response.getBody(), "Dataset created successfully"));
//    }

    @Test
    public void deleteDatasetSuccessfulTest() {
        ResponseEntity<String> response = datasetAPIController.deleteDataset(MockObjectUtil.DATASET.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody(), "Dataset deleted successfully"));
    }

//    @Test
//    public void updateDatasetSuccessfulTest() {
//        ResponseEntity<String> response = datasetAPIController.updateDataset(MockObjectUtil.DATASET.getId(), MockObjectUtil.DATASET.toString());
//
//        assertNotNull(response);
//        assertTrue(response.getStatusCode().is2xxSuccessful());
//        assertTrue(StringUtils.contains(response.getBody(), "Dataset updated successfully"));
//    }

}
