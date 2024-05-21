package it.eng.catalog.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.DataService;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.service.DataServiceService;
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
public class DataServiceAPIControllerTest {

    @InjectMocks
    DataServiceAPIController dataServiceAPIController;

    @Mock
    private DataServiceService dataServiceService;

    @Test
    public void getDataServiceById_success() {
        when(dataServiceService.getDataServiceById(MockObjectUtil.DATA_SERVICE.getId())).thenReturn(MockObjectUtil.DATA_SERVICE);
        ResponseEntity<JsonNode> response = dataServiceAPIController.getDataServiceById(MockObjectUtil.DATA_SERVICE.getId());

        verify(dataServiceService).getDataServiceById(MockObjectUtil.DATA_SERVICE.getId());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.DATA_SERVICE.getType()));
    }

    @Test
    public void getAllDataService_success() {
        when(dataServiceService.getAllDataServices()).thenReturn(MockObjectUtil.DATA_SERVICES);
        ResponseEntity<JsonNode> response = dataServiceAPIController.getAllDataServices();

        verify(dataServiceService).getAllDataServices();
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.DATA_SERVICE.getType()));
    }

    @Test
    public void saveDataService_success() {
        String dataService = Serializer.serializePlain(MockObjectUtil.DATA_SERVICE);
        when(dataServiceService.saveDataService(any())).thenReturn(MockObjectUtil.DATA_SERVICE);
        ResponseEntity<JsonNode> response = dataServiceAPIController.saveDataService(dataService);

        verify(dataServiceService).saveDataService(any());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().get("type").toString(), MockObjectUtil.DATA_SERVICE.getType()));
    }

    @Test
    public void deleteDataService_success() {
        ResponseEntity<String> response = dataServiceAPIController.deleteDataService(MockObjectUtil.DATA_SERVICE.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody(), "Data service deleted successfully"));
    }

    @Test
    public void updateDataService_success() {
        String dataService = Serializer.serializePlain(MockObjectUtil.DATA_SERVICE_FOR_UPDATE);
        when(dataServiceService.updateDataService(any(String.class), any())).thenReturn(MockObjectUtil.DATA_SERVICE_FOR_UPDATE);
        ResponseEntity<JsonNode> response = dataServiceAPIController.updateDataService(MockObjectUtil.DATA_SERVICE_FOR_UPDATE.getId(), dataService);

        verify(dataServiceService).updateDataService(any(String.class), any(DataService.class));
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().get("type").toString(), MockObjectUtil.DATA_SERVICE.getType()));

    }
}
