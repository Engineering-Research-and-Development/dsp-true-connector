package it.eng.catalog.rest.internal;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Hidden;
import it.eng.catalog.exceptions.ResourceNotFoundAPIException;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.DatasetService;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.response.GenericApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalDatasetControllerTest {

    private static final String DATASET_ID = CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getId();

    @InjectMocks
    private InternalDatasetController controller;

    @Mock
    private DatasetService datasetService;

    @Test
    @DisplayName("The controller is mapped under /internal/v1/datasets and hidden from the OpenAPI docs")
    void mappedUnderInternalZoneAndHidden() {
        assertArrayEquals(new String[]{ApiEndpoints.INTERNAL_DATASETS_V1},
                InternalDatasetController.class.getAnnotation(RequestMapping.class).path());
        assertNotNull(InternalDatasetController.class.getAnnotation(Hidden.class));
    }

    @Test
    @DisplayName("Formats are returned from the dataset service")
    void getFormatsFromDataset() {
        when(datasetService.getFormatsFromDataset(DATASET_ID)).thenReturn(List.of("HttpData-PULL"));

        ResponseEntity<GenericApiResponse<List<String>>> response = controller.getFormatsFromDataset(DATASET_ID);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(response.getBody().isSuccess());
        assertEquals(List.of("HttpData-PULL"), response.getBody().getData());
    }

    @Test
    @DisplayName("A missing dataset propagates the not-found exception for formats")
    void getFormatsFromDatasetNotFound() {
        when(datasetService.getFormatsFromDataset(DATASET_ID)).thenThrow(new ResourceNotFoundAPIException());

        assertThrows(ResourceNotFoundAPIException.class, () -> controller.getFormatsFromDataset(DATASET_ID));
    }

    @Test
    @DisplayName("The artifact is returned from the dataset service as plain JSON")
    void getArtifactFromDataset() {
        Artifact artifact = CatalogMockObjectUtil.DATASET_WITH_ARTIFACT.getArtifact();
        when(datasetService.getArtifactFromDataset(DATASET_ID)).thenReturn(artifact);

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.getArtifactFromDataset(DATASET_ID);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(artifact, CatalogSerializer.deserializePlain(response.getBody().getData(), Artifact.class));
    }

    @Test
    @DisplayName("A missing dataset propagates the not-found exception for the artifact")
    void getArtifactFromDatasetNotFound() {
        when(datasetService.getArtifactFromDataset(DATASET_ID)).thenThrow(new ResourceNotFoundAPIException());

        assertThrows(ResourceNotFoundAPIException.class, () -> controller.getArtifactFromDataset(DATASET_ID));
    }
}
