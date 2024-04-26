package it.eng.catalog.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.exceptions.CatalogNotFoundAPIException;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.util.MockObjectUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CatalogAPIControllerTest {


    CatalogAPIController catalogAPIController;
    @Mock
    private CatalogService catalogService;

    @BeforeEach
    public void init() {
        catalogAPIController = new CatalogAPIController(catalogService);
    }

    @Test
    public void getCatalogSuccessfulTest() {
        when(catalogService.getCatalog()).thenReturn(MockObjectUtil.CATALOG);
        ResponseEntity<JsonNode> response = catalogAPIController.getCatalog();

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody().toString(), MockObjectUtil.CATALOG.getType()));
    }


    @Test
    public void getCatalogByIdSuccessfulTest() {
        when(catalogService.getCatalogById(MockObjectUtil.CATALOG.getId())).thenReturn(Optional.of(MockObjectUtil.CATALOG));
        ResponseEntity<JsonNode> response = catalogAPIController.getCatalogById(MockObjectUtil.CATALOG.getId());

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
        ResponseEntity<String> response = catalogAPIController.createCatalog(MockObjectUtil.CATALOG.toString());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody(), "Catalog created successfully"));
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
        ResponseEntity<String> response = catalogAPIController.updateCatalog(MockObjectUtil.CATALOG.getId(), MockObjectUtil.CATALOG.toString());

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StringUtils.contains(response.getBody(), "Catalog updated successfully"));
    }
}
