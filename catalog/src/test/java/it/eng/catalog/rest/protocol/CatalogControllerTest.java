package it.eng.catalog.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.DatasetRequestMessage;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.service.DatasetService;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CatalogControllerTest {

    @InjectMocks
    private CatalogController catalogController;

    @Mock
    private CatalogService catalogService;

    @Mock
    private DatasetService datasetService;

    private final CatalogRequestMessage catalogRequestMessage = CatalogRequestMessage.Builder.newInstance().build();
    private final DatasetRequestMessage datasetRequestMessage = DatasetRequestMessage.Builder.newInstance()
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
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().toString(), CatalogMockObjectUtil.CATALOG.getType()));
        assertTrue(StringUtils.contains(response.getBody().toString(), DSpaceConstants.DSPACE_2025_01_CONTEXT));
    }

    @Test
    @DisplayName("Get catalog - not valid catalog request message")
    public void notValidCatalogRequestMessageTest() throws Exception {
        JsonNode jsonNode = CatalogSerializer.serializeProtocolJsonNode(datasetRequestMessage);

        Exception e = assertThrows(ValidationException.class, () -> catalogController.getCatalog(null, jsonNode));

        assertTrue(StringUtils.contains(e.getMessage(), "@type field not correct, expected CatalogRequestMessage"));
    }

    @Test
    @DisplayName("Get dataset - success")
    public void getDatasetSuccessfulTest() throws Exception {
        when(datasetService.getDatasetById("1")).thenReturn(CatalogMockObjectUtil.DATASET);

        ResponseEntity<JsonNode> response = catalogController.getDataset(null, "1");

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().toString(), CatalogMockObjectUtil.DATASET.getType()));
        assertTrue(StringUtils.contains(response.getBody().toString(), DSpaceConstants.DSPACE_2025_01_CONTEXT));
    }
}
