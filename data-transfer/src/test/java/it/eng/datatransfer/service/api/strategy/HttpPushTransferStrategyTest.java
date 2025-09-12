package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import it.eng.tools.util.ToolsUtil;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HttpPushTransferStrategyTest {

    @Mock
    private S3Properties s3Properties;
    @Mock
    private S3ClientService s3ClientService;
    @Mock
    private HttpURLConnection mockConnection;

    @InjectMocks
    private HttpPushTransferStrategy strategy;

    private static final String TEST_DATASET_ID = "test-dataset-id";
    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_OBJECT_KEY = "test-object-key";
    private static final String TEST_ACCESS_KEY = "test-access-key";
    private static final String TEST_SECRET_KEY = "test-secret-key";
    private static final String TEST_REGION = "test-region";
    private static final String TEST_ENDPOINT_OVERRIDE = "http://test-endpoint";
    private static final String TEST_CONTENT = "test-content";
    private static final String TEST_CONTENT_TYPE = "application/json";
    private static final String TEST_CONTENT_DISPOSITION = "attachment; filename=test.json";

    @Test
    @DisplayName("Should execute transfer successfully")
    void transfer_success() {
        String consumerPid = ToolsUtil.generateUniqueId();
        String providerPid = ToolsUtil.generateUniqueId();
        String transferProcessId = ToolsUtil.generateUniqueId();

        List<EndpointProperty> endpointProperties = List.of(
                EndpointProperty.Builder.newInstance()
                        .name(S3Utils.BUCKET_NAME)
                        .value(TEST_BUCKET)
                        .build(),
                EndpointProperty.Builder.newInstance()
                        .name(S3Utils.REGION)
                        .value(TEST_REGION)
                        .build(),
                EndpointProperty.Builder.newInstance()
                        .name(S3Utils.OBJECT_KEY)
                        .value(TEST_OBJECT_KEY)
                        .build(),
                EndpointProperty.Builder.newInstance()
                        .name(S3Utils.ACCESS_KEY)
                        .value(TEST_ACCESS_KEY)
                        .build(),
                EndpointProperty.Builder.newInstance()
                        .name(S3Utils.SECRET_KEY)
                        .value(TEST_SECRET_KEY)
                        .build(),
                EndpointProperty.Builder.newInstance()
                        .name(S3Utils.ENDPOINT_OVERRIDE)
                        .value(TEST_ENDPOINT_OVERRIDE)
                        .build()
        );

        Map<String, String> endpointPropertiesMap = Map.of(
                S3Utils.BUCKET_NAME, TEST_BUCKET,
                S3Utils.REGION, TEST_REGION,
                S3Utils.OBJECT_KEY, TEST_OBJECT_KEY,
                S3Utils.ACCESS_KEY, TEST_ACCESS_KEY,
                S3Utils.SECRET_KEY, TEST_SECRET_KEY,
                S3Utils.ENDPOINT_OVERRIDE, TEST_ENDPOINT_OVERRIDE
        );


        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpointProperties(endpointProperties)
                .build();

        TransferProcess transferProcess = TransferProcess.Builder.newInstance()
                .id(transferProcessId)
                .consumerPid(consumerPid)
                .providerPid(providerPid)
                .role(IConstants.ROLE_PROVIDER)
                .dataAddress(dataAddress)
                .datasetId(TEST_DATASET_ID)
                .state(TransferState.STARTED)
                .format(DataTransferFormat.HTTP_PUSH.format())
                .build();

        when(s3Properties.getBucketName()).thenReturn(TEST_BUCKET);
        when(s3ClientService.generateGetPresignedUrl(eq(TEST_BUCKET), eq(TEST_DATASET_ID), any()))
                .thenReturn("http://presigned-url");

        when(s3ClientService.uploadFile(
                eq(endpointPropertiesMap),
                eq(TEST_OBJECT_KEY),
                any(InputStream.class),
                eq(TEST_CONTENT_TYPE),
                eq(TEST_CONTENT_DISPOSITION)
        )).thenReturn(CompletableFuture.completedFuture("test-etag"));

        /**
         * When you move the mocks inside the try block:
         *
         * The mocks are configured within the scope of the try block.
         * However, the MockedConstruction creates a new scope for mocking, and Mockito treats
         * these as "unnecessary stubbings" because:
         *
         * @Mock and @InjectMocks annotations create the mocks at the class level
         * These mocks are injected into strategy before entering the try block
         * When you define new behaviors inside the try block, Mockito sees them as redundant
         * because they're in a different scope than where they're actually used.
         *
         * Best Practice: Keep mock configurations that are needed throughout the test at the
         * method level (outside the try block), and only put construction-specific mocking
         * (like URL and HttpURLConnection) inside the MockedConstruction block.
         */
        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> {
                    // Configure the mock URL object
                    when(mock.openConnection()).thenReturn(mockConnection);

                })) {

            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            when(mockConnection.getContentType()).thenReturn(TEST_CONTENT_TYPE);
            when(mockConnection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION))
                    .thenReturn(TEST_CONTENT_DISPOSITION);
            when(mockConnection.getInputStream())
                    .thenReturn(new ByteArrayInputStream(TEST_CONTENT.getBytes()));

            // Act & Assert
            assertDoesNotThrow(() -> strategy.transfer(transferProcess));

            verify(s3ClientService).uploadFile(
                    eq(endpointPropertiesMap),
                    eq(TEST_OBJECT_KEY),
                    any(InputStream.class),
                    eq(TEST_CONTENT_TYPE),
                    eq(TEST_CONTENT_DISPOSITION)
            );
        } catch (Exception e) {
            fail("Test failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should throw DataTransferAPIException on upload failure")
    void transfer_uploadFails_throwsException() throws Exception {
        // Arrange
        TransferProcess transferProcess = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED;

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> {
                    // Configure the mock URL object
                    when(mock.openConnection()).thenReturn(mockConnection);
                })) {
            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);

            // Act & Assert
            DataTransferAPIException ex = assertThrows(DataTransferAPIException.class,
                    () -> strategy.transfer(transferProcess));
            assertTrue(ex.getMessage().contains("Failed to get stream. HTTP response code"));
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, mockConnection.getResponseCode());
        }
    }
}
