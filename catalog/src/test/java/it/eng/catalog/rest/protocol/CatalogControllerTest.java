package it.eng.catalog.rest.protocol;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.DatasetRequestMessage;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.service.DatasetService;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
public class CatalogControllerTest {

	@InjectMocks
    private CatalogController catalogController;

    @Mock
    private CatalogService catalogService;
    
    @Mock
    private DatasetService datasetService;

    private CatalogRequestMessage catalogRequestMessage = CatalogRequestMessage.Builder.newInstance().build();
    private DatasetRequestMessage datasetRequestMessage = DatasetRequestMessage.Builder.newInstance()
            .dataset(CatalogSerializer.serializeProtocol(CatalogMockObjectUtil.DATASET))
            .build();


    @Test
    @DisplayName("Get catalog - success")
    public void getCatalogSuccessfulTest() throws Exception {
        when(catalogService.getCatalog()).thenReturn(CatalogMockObjectUtil.CATALOG);
        JsonNode jsonNode = CatalogSerializer.serializeProtocolJsonNode(catalogRequestMessage);

        ResponseEntity<JsonNode> response = catalogController.getCatalog(null, jsonNode);

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), CatalogMockObjectUtil.CATALOG.getType()));
        assertTrue(StringUtils.contains(response.getBody().toString(), DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE));
    }

    @Test
    @DisplayName("Get catalog - not valid catalog request message")
    public void notValidCatalogRequestMessageTest() throws Exception {
        JsonNode jsonNode = CatalogSerializer.serializeProtocolJsonNode(datasetRequestMessage);

        Exception e = assertThrows(ValidationException.class, () -> catalogController.getCatalog(null, jsonNode));

        assertTrue(StringUtils.contains(e.getMessage(), "@type field not correct, expected dspace:CatalogRequestMessage"));
    }

    @Test
    @DisplayName("Get dataset - success")
    public void getDatasetSuccessfulTest() throws Exception {
        when(datasetService.getDatasetById(any())).thenReturn(CatalogMockObjectUtil.DATASET);

        JsonNode jsonNode = CatalogSerializer.serializeProtocolJsonNode(datasetRequestMessage);

        ResponseEntity<JsonNode> response = catalogController.getDataset(null, "1", jsonNode);

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), CatalogMockObjectUtil.DATASET.getType()));
        assertTrue(StringUtils.contains(response.getBody().toString(), DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE));
    }

    @Test
    @DisplayName("Get dataset - not valid dataset request message")
    public void notValidDatasetRequestMessageTest() throws Exception {
        JsonNode jsonNode = CatalogSerializer.serializeProtocolJsonNode(catalogRequestMessage);

        Exception e = assertThrows(ValidationException.class, () -> catalogController.getDataset(null, "1", jsonNode));

        assertTrue(StringUtils.contains(e.getMessage(), "@type field not correct, expected dspace:DatasetRequestMessage"));
    }
}
