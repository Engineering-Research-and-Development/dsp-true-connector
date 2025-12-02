package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
public class HttpPullTransferStrategyTest {

    @Mock
    private S3Properties s3Properties;
    @Mock
    private S3ClientService s3ClientService;
    @Mock
    private HttpURLConnection mockConnection;

    private HttpPullTransferStrategy strategy;

    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_CONTENT = "test-content";
    private static final String TEST_CONTENT_TYPE = "application/json";
    private static final String TEST_CONTENT_DISPOSITION = "attachment; filename=test.json";
    private static final String TEST_ENDPOINT = "http://s3-endpoint";
    private static final String TEST_REGION = "us-east-1";
    private static final String TEST_ACCESS_KEY = "access-key";
    private static final String TEST_SECRET_KEY = "secret-key";

    @BeforeEach
    void setUp() {
        strategy = new HttpPullTransferStrategy(s3ClientService, s3Properties, false);
    }

    @Test
    @DisplayName("Should execute transfer successfully")
    void transfer_success() {
        TransferProcess transferProcess = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED;

        Map<String, String> expectedDestinationS3Properties = mockS3Properties(transferProcess.getId());

        when(s3ClientService.uploadFile(
                any(InputStream.class),
                eq(expectedDestinationS3Properties),
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
                    any(InputStream.class),
                    eq(expectedDestinationS3Properties),
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

    @Test
    @DisplayName("Should set Authorization header if present in endpoint properties")
    void transfer_withAuthorizationHeader() throws Exception {
        // Arrange
        EndpointProperty authType = EndpointProperty.Builder.newInstance()
                .name(IConstants.AUTH_TYPE)
                .value("Bearer")
                .build();
        EndpointProperty token = EndpointProperty.Builder.newInstance()
                .name(IConstants.AUTHORIZATION)
                .value("token123")
                .build();
        TransferProcess transferProcess = mockTransferProcess("http://test", List.of(authType, token));

        Map<String, String> expectedDestinationS3Properties = mockS3Properties(transferProcess.getId());

        when(s3ClientService.uploadFile(
                any(InputStream.class),
                eq(expectedDestinationS3Properties),
                eq(TEST_CONTENT_TYPE),
                eq(TEST_CONTENT_DISPOSITION)
        )).thenReturn(CompletableFuture.completedFuture("test-etag"));

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

            // Act
            strategy.transfer(transferProcess);

            // Assert
            verify(mockConnection).setRequestProperty(
                    eq(HttpHeaders.AUTHORIZATION),
                    eq("Bearer token123")
            );
            verify(s3ClientService).uploadFile(
                    any(InputStream.class),
                    eq(expectedDestinationS3Properties),
                    eq(TEST_CONTENT_TYPE),
                    anyString()
            );
        }
    }

    @Test
    @DisplayName("Should not set Authorization header if not present")
    void transfer_withoutAuthorizationHeader() throws Exception {
        // Arrange
        TransferProcess transferProcess = mockTransferProcess("http://test", List.of());

        Map<String, String> expectedDestinationS3Properties = mockS3Properties(transferProcess.getId());

        when(s3ClientService.uploadFile(
                any(InputStream.class),
                eq(expectedDestinationS3Properties),
                eq(TEST_CONTENT_TYPE),
                eq(TEST_CONTENT_DISPOSITION)
        )).thenReturn(CompletableFuture.completedFuture("test-etag"));

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
                    any(InputStream.class),
                    eq(expectedDestinationS3Properties),
                    eq(TEST_CONTENT_TYPE),
                    eq(TEST_CONTENT_DISPOSITION)
            );
        }
    }

    // Helper to create a mock TransferProcess
    private TransferProcess mockTransferProcess(String endpoint, List<EndpointProperty> props) {
        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpoint(endpoint)
                .endpointType(DataTransferMockObjectUtil.ENDPOINT_TYPE)
                .endpointProperties(props)
                .build();

        return TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(dataAddress)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .isDownloaded(true)
                .dataId(new ObjectId().toHexString())
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .state(TransferState.STARTED)
                .format(DataTransferFormat.HTTP_PULL.name())
                .build();
    }

    private Map<String, String> mockS3Properties(String objectKey) {
        // Configure S3Properties mock
        when(s3Properties.getBucketName()).thenReturn(TEST_BUCKET);
        when(s3Properties.getEndpoint()).thenReturn(TEST_ENDPOINT);
        when(s3Properties.getRegion()).thenReturn(TEST_REGION);
        when(s3Properties.getAccessKey()).thenReturn(TEST_ACCESS_KEY);
        when(s3Properties.getSecretKey()).thenReturn(TEST_SECRET_KEY);

        return Map.of(
                S3Utils.OBJECT_KEY, objectKey,
                S3Utils.BUCKET_NAME, TEST_BUCKET,
                S3Utils.ENDPOINT_OVERRIDE, TEST_ENDPOINT,
                S3Utils.REGION, TEST_REGION,
                S3Utils.ACCESS_KEY, TEST_ACCESS_KEY,
                S3Utils.SECRET_KEY, TEST_SECRET_KEY
        );
    }

}
