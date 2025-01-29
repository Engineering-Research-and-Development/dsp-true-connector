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
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.util.CatalogMockObjectUtil;
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
        when(catalogService.getCatalogForApi()).thenReturn(CatalogMockObjectUtil.CATALOG);
        ResponseEntity<GenericApiResponse<JsonNode>> response = catalogAPIController.getCatalog();

        verify(catalogService).getCatalogForApi();
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), CatalogMockObjectUtil.CATALOG.getType()));
    }


    @Test
    @DisplayName("Get catalog by id - success")
    public void getCatalogByIdSuccessfulTest() {
        when(catalogService.getCatalogById(CatalogMockObjectUtil.CATALOG.getId())).thenReturn(CatalogMockObjectUtil.CATALOG);
        ResponseEntity<GenericApiResponse<JsonNode>> response = catalogAPIController.getCatalogById(CatalogMockObjectUtil.CATALOG.getId());

        verify(catalogService).getCatalogById(CatalogMockObjectUtil.CATALOG.getId());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), CatalogMockObjectUtil.CATALOG.getType()));
    }

    @Test
    @DisplayName("Get catalog - catalog not found")
    public void getCatalogByIdNotFoundTest() {
        when(catalogService.getCatalogById(CatalogMockObjectUtil.CATALOG.getId())).thenThrow(new ResourceNotFoundAPIException("Catalog with id" + CatalogMockObjectUtil.CATALOG.getId() + " not found"));

        Exception e = assertThrows(ResourceNotFoundAPIException.class, () -> catalogAPIController.getCatalogById(CatalogMockObjectUtil.CATALOG.getId()));

        assertTrue(StringUtils.contains(e.getMessage(), "Catalog with id" + CatalogMockObjectUtil.CATALOG.getId() + " not found"));
    }


    @Test
    @DisplayName("Create catalog - success")
    public void createCatalogSuccessfulTest() {
        String catalog = CatalogSerializer.serializePlain(CatalogMockObjectUtil.CATALOG);
        when(catalogService.saveCatalog(any(Catalog.class))).thenReturn(CatalogMockObjectUtil.CATALOG);

        ResponseEntity<GenericApiResponse<JsonNode>> response = catalogAPIController.createCatalog(catalog);

        verify(catalogService).saveCatalog(any());
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().getData().get("type").toString(), CatalogMockObjectUtil.CATALOG.getType()));
    }

    @Test
    @DisplayName("Delete catalog - success")
    public void deleteCatalogSuccessfulTest() {
        ResponseEntity<GenericApiResponse<Void>> response = catalogAPIController.deleteCatalog(CatalogMockObjectUtil.CATALOG.getId());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().getMessage(), "Catalog deleted successfully"));
    }

    @Test
    @DisplayName("Update catalog - success")
    public void updateCatalogSuccessfulTest() {
        String catalog = CatalogSerializer.serializePlain(CatalogMockObjectUtil.CATALOG_FOR_UPDATE);
        when(catalogService.updateCatalog(any(String.class), any(Catalog.class))).thenReturn(CatalogMockObjectUtil.CATALOG_FOR_UPDATE);

        ResponseEntity<GenericApiResponse<JsonNode>> response = catalogAPIController.updateCatalog(CatalogMockObjectUtil.CATALOG_FOR_UPDATE.getId(), catalog);

        verify(catalogService).updateCatalog(any(String.class), any(Catalog.class));
        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(StringUtils.contains(response.getBody().getData().get("type").toString(), CatalogMockObjectUtil.CATALOG.getType()));
    }
}
