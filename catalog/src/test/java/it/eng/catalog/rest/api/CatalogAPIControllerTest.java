package it.eng.catalog.rest.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.util.MockObjectUtil;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
public class CatalogAPIControllerTest {

    @InjectMocks
    private CatalogAPIController catalogAPIController;
    @Mock
    private CatalogService catalogService;

    @Test
    @DisplayName("Get catalog - success")
    public void getCatalogSuccessfulTest() {
        when(catalogService.getCatalog()).thenReturn(MockObjectUtil.CATALOG);
        ResponseEntity<GenericApiResponse<JsonNode>> response = catalogAPIController.getCatalog();

        verify(catalogService).getCatalog();
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.CATALOG.getType()));
    }


    @Test
    @DisplayName("Get catalog by id - success")
    public void getCatalogByIdSuccessfulTest() {
        when(catalogService.getCatalogById(MockObjectUtil.CATALOG.getId())).thenReturn(MockObjectUtil.CATALOG);
        ResponseEntity<GenericApiResponse<JsonNode>> response = catalogAPIController.getCatalogById(MockObjectUtil.CATALOG.getId());

        verify(catalogService).getCatalogById(MockObjectUtil.CATALOG.getId());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.CATALOG.getType()));
    }

    @Test
    @DisplayName("Get catalog - catalog not found")
    public void getCatalogByIdNotFoundTest() {
        when(catalogService.getCatalogById(MockObjectUtil.CATALOG.getId())).thenThrow(new ResourceNotFoundAPIException("Catalog with id" + MockObjectUtil.CATALOG.getId() + " not found"));

        Exception e = assertThrows(ResourceNotFoundAPIException.class, () -> catalogAPIController.getCatalogById(MockObjectUtil.CATALOG.getId()));

        assertTrue(StringUtils.contains(e.getMessage(), "Catalog with id" + MockObjectUtil.CATALOG.getId() + " not found"));
    }


    @Test
    @DisplayName("Create catalog - success")
    public void createCatalogSuccessfulTest() {
        String catalog = Serializer.serializePlain(MockObjectUtil.CATALOG);
        when(catalogService.saveCatalog(any(Catalog.class))).thenReturn(MockObjectUtil.CATALOG);

        ResponseEntity<GenericApiResponse<JsonNode>> response = catalogAPIController.createCatalog(catalog);

        verify(catalogService).saveCatalog(any());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().getData().get("type").toString(), MockObjectUtil.CATALOG.getType()));
    }

    @Test
    @DisplayName("Delete catalog - success")
    public void deleteCatalogSuccessfulTest() {
        ResponseEntity<GenericApiResponse<Object>> response = catalogAPIController.deleteCatalog(MockObjectUtil.CATALOG.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().getMessage(), "Catalog deleted successfully"));
    }

    @Test
    @DisplayName("Update catalog - success")
    public void updateCatalogSuccessfulTest() {
        String catalog = Serializer.serializePlain(MockObjectUtil.CATALOG_FOR_UPDATE);
        when(catalogService.updateCatalog(any(String.class), any(Catalog.class))).thenReturn(MockObjectUtil.CATALOG_FOR_UPDATE);

        ResponseEntity<GenericApiResponse<JsonNode>> response = catalogAPIController.updateCatalog(MockObjectUtil.CATALOG_FOR_UPDATE.getId(), catalog);

        verify(catalogService).updateCatalog(any(String.class), any(Catalog.class));
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().getData().get("type").toString(), MockObjectUtil.CATALOG.getType()));
    }
}
