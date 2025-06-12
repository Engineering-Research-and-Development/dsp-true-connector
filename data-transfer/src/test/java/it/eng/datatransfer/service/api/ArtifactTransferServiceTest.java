package it.eng.datatransfer.service.api;

import it.eng.datatransfer.exceptions.DownloadException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtifactTransferServiceTest {

    @Mock
    private OkHttpRestClient okHttpRestClient;

    @InjectMocks
    private ArtifactTransferService artifactTransferService;

    private TransferProcess transferProcess;
    private static final String DATASET_ID = "test-dataset-id";
    private static final String VALID_RESPONSE = """
            {
                "success": true,
                "data": {
                    "id": "123",
                    "value": "test-value",
                    "artifactType": "FILE"
                }
            }
            """;
    private static final String ERROR_RESPONSE = """
            {
                "success": false,
                "data": null
            }
            """;

    @BeforeEach
    void setUp() {
        transferProcess = TransferProcess.Builder.newInstance()
                .state(TransferState.STARTED)
                .datasetId(DATASET_ID)
                .build();
    }

    @Test
    @DisplayName("Should successfully find artifact when valid response is received")
    void findArtifact_Success() {
        // Given
        String endpoint = ApiEndpoints.CATALOG_DATASETS_V1 + "/" + DATASET_ID + "/artifact";
        when(okHttpRestClient.sendInternalRequest(eq(endpoint), eq(HttpMethod.GET), any()))
                .thenReturn(VALID_RESPONSE);

        // When
        Artifact result = artifactTransferService.findArtifact(transferProcess);

        // Then
        assertNotNull(result);
        assertEquals("123", result.getId());
        assertEquals("test-value", result.getValue());
        assertEquals(ArtifactType.FILE, result.getArtifactType());
    }

    @Test
    @DisplayName("Should throw DownloadException when response data is null")
    void findArtifact_NullData() {
        // Given
        String endpoint = ApiEndpoints.CATALOG_DATASETS_V1 + "/" + DATASET_ID + "/artifact";
        when(okHttpRestClient.sendInternalRequest(eq(endpoint), eq(HttpMethod.GET), any()))
                .thenReturn(ERROR_RESPONSE);

        // When/Then
        DownloadException exception = assertThrows(DownloadException.class,
                () -> artifactTransferService.findArtifact(transferProcess));
        assertEquals("No such data exists", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw RuntimeException when invalid JSON is received")
    void findArtifact_InvalidJson() {
        // Given
        String endpoint = ApiEndpoints.CATALOG_DATASETS_V1 + "/" + DATASET_ID + "/artifact";
        when(okHttpRestClient.sendInternalRequest(eq(endpoint), eq(HttpMethod.GET), any()))
                .thenReturn("invalid json");

        // When/Then
        assertThrows(RuntimeException.class,
                () -> artifactTransferService.findArtifact(transferProcess));
    }
}