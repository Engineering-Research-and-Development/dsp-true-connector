package it.eng.catalog.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.DatasetRequestMessage;
import it.eng.catalog.model.Serializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.util.MockObjectUtil;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CatalogProtocolControllerTest {

    private CatalogProtocolController catalogProtocolController;

    @Mock
    private CatalogService catalogService;

    private CatalogRequestMessage catalogRequestMessage = CatalogRequestMessage.Builder.newInstance().build();
    private DatasetRequestMessage datasetRequestMessage = DatasetRequestMessage.Builder.newInstance()
            .dataset(Serializer.serializeProtocol(MockObjectUtil.DATASET))
            .build();

    @BeforeEach
    public void init() {
        catalogProtocolController = new CatalogProtocolController(catalogService);
    }

    @Test
    public void getCatalogSuccessfulTest() throws Exception {
        when(catalogService.getCatalog()).thenReturn(MockObjectUtil.CATALOG);
        JsonNode jsonNode = Serializer.serializeProtocolJsonNode(catalogRequestMessage);

        ResponseEntity<String> response = catalogProtocolController.getCatalog(null, jsonNode);

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody(), MockObjectUtil.CATALOG.getType()));
        assertTrue(StringUtils.contains(response.getBody(), DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE));
    }

    @Test
    public void notValidCatalogRequestMessageTest() throws Exception {
        JsonNode jsonNode = Serializer.serializeProtocolJsonNode(datasetRequestMessage);

        Exception e = assertThrows(ValidationException.class, () -> catalogProtocolController.getCatalog(null, jsonNode));

        assertTrue(StringUtils.contains(e.getMessage(), "@type field not correct, expected dspace:CatalogRequestMessage"));
    }

    @Test
    public void getDatasetSuccessfulTest() throws Exception {
        when(catalogService.getDataSetById(any())).thenReturn(MockObjectUtil.DATASET);

        JsonNode jsonNode = Serializer.serializeProtocolJsonNode(datasetRequestMessage);

        ResponseEntity<String> response = catalogProtocolController.getDataset(null, "1", jsonNode);

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody(), MockObjectUtil.DATASET.getType()));
        assertTrue(StringUtils.contains(response.getBody(), DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE));
    }

    @Test
    public void notValidDatasetRequestMessageTest() throws Exception {
        JsonNode jsonNode = Serializer.serializeProtocolJsonNode(catalogRequestMessage);

        Exception e = assertThrows(ValidationException.class, () -> catalogProtocolController.getDataset(null, "1", jsonNode));

        assertTrue(StringUtils.contains(e.getMessage(), "@type field not correct, expected dspace:DatasetRequestMessage"));
    }
}
