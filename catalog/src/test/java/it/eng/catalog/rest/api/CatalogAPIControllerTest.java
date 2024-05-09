package it.eng.catalog.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.exceptions.CatalogNotFoundAPIException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Serializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.util.MockObjectUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CatalogAPIControllerTest {


    @InjectMocks
    CatalogAPIController catalogAPIController;
    @Mock
    private CatalogService catalogService;

    @Test
    public void getCatalogSuccessfulTest() {
        when(catalogService.getCatalog()).thenReturn(MockObjectUtil.CATALOG);
        ResponseEntity<JsonNode> response = catalogAPIController.getCatalog();

        verify(catalogService).getCatalog();
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.CATALOG.getType()));
    }


    @Test
    public void getCatalogByIdSuccessfulTest() {
        when(catalogService.getCatalogById(MockObjectUtil.CATALOG.getId())).thenReturn(Optional.of(MockObjectUtil.CATALOG));
        ResponseEntity<JsonNode> response = catalogAPIController.getCatalogById(MockObjectUtil.CATALOG.getId());

        verify(catalogService).getCatalogById(MockObjectUtil.CATALOG.getId());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.CATALOG.getType()));
    }

    @Test
    public void getCatalogByIdNotFoundTest() {
        when(catalogService.getCatalogById(MockObjectUtil.CATALOG.getId())).thenReturn(Optional.empty());

        Exception e = assertThrows(CatalogNotFoundAPIException.class, () -> catalogAPIController.getCatalogById(MockObjectUtil.CATALOG.getId()));

        assertTrue(StringUtils.contains(e.getMessage(), "Catalog with id" + MockObjectUtil.CATALOG.getId() + " not Found"));
    }


    @Test
    public void createCatalogSuccessfulTest() {
        String catalog = Serializer.serializePlain(MockObjectUtil.CATALOG);
        when(catalogService.saveCatalog(any(Catalog.class))).thenReturn(MockObjectUtil.CATALOG);

        ResponseEntity<JsonNode> response = catalogAPIController.createCatalog(catalog);

        verify(catalogService).saveCatalog(any());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().get("type").toString(), MockObjectUtil.CATALOG.getType()));
    }

    @Test
    public void deleteCatalogSuccessfulTest() {
        ResponseEntity<String> response = catalogAPIController.deleteCatalog(MockObjectUtil.CATALOG.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody(), "Catalog deleted successfully"));
    }

    @Test
    public void updateCatalogSuccessfulTest() {
        String catalog = Serializer.serializePlain(MockObjectUtil.CATALOG_FOR_UPDATE);
        when(catalogService.updateCatalog(any(String.class), any(Catalog.class))).thenReturn(MockObjectUtil.CATALOG_FOR_UPDATE);

        ResponseEntity<JsonNode> response = catalogAPIController.updateCatalog(MockObjectUtil.CATALOG_FOR_UPDATE.getId(), catalog);

        verify(catalogService).updateCatalog(any(String.class), any(Catalog.class));
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().get("type").toString(), MockObjectUtil.CATALOG.getType()));
    }
}
