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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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

    /**
     * Strategy under test. Created manually in setUp so we can inject a direct
     * (synchronous) executor — this ensures the CompletableFuture body runs on
     * the test thread, keeping MockedConstruction<URL> in scope.
     */
    private HttpPullTransferStrategy strategy;

    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_CONTENT = "test-content";
    private static final String TEST_CONTENT_TYPE = "application/json";
    private static final String TEST_CONTENT_DISPOSITION = "attachment; filename=test.json";
    private static final String TEST_ENDPOINT = "http://s3-endpoint";
    private static final String TEST_REGION = "us-east-1";
    private static final String TEST_ACCESS_KEY = "access-key";
    private static final String TEST_SECRET_KEY = "secret-key";

    /**
     * Injects a synchronous (direct) executor so that the async body in
     * {@code CompletableFuture.supplyAsync()} executes on the calling thread.
     * This makes {@code MockedConstruction<URL>} intercept {@code new URL(...)}
     * correctly within the test's try-with-resources scope.
     */
    @BeforeEach
    void setUp() {
        // Runnable::run is a valid Executor that executes tasks on the calling thread
        strategy = new HttpPullTransferStrategy(s3ClientService, s3Properties, Runnable::run);
    }

    @Test
    @DisplayName("Should execute transfer successfully")
    void transfer_success() throws Exception {
        TransferProcess transferProcess = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED;

        Map<String, String> expectedDestinationS3Properties = mockS3Properties(transferProcess.getId());

        when(s3ClientService.uploadFile(
                any(InputStream.class),
                eq(expectedDestinationS3Properties),
                eq(TEST_CONTENT_TYPE),
                eq(TEST_CONTENT_DISPOSITION)
        )).thenReturn(CompletableFuture.completedFuture("test-etag"));

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            when(mockConnection.getContentType()).thenReturn(TEST_CONTENT_TYPE);
            when(mockConnection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION))
                    .thenReturn(TEST_CONTENT_DISPOSITION);
            when(mockConnection.getInputStream())
                    .thenReturn(new ByteArrayInputStream(TEST_CONTENT.getBytes()));

            // Act — .join() ensures the synchronous future has completed before asserting
            assertDoesNotThrow(() -> strategy.transfer(transferProcess).join());

            verify(s3ClientService).uploadFile(
                    any(InputStream.class),
                    eq(expectedDestinationS3Properties),
                    eq(TEST_CONTENT_TYPE),
                    eq(TEST_CONTENT_DISPOSITION)
            );
        }
    }

    @Test
    @DisplayName("Should throw DataTransferAPIException on non-OK HTTP response")
    void transfer_uploadFails_throwsException() throws Exception {
        // Arrange
        TransferProcess transferProcess = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED;

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {
            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);

            // Act & Assert — supplyAsync wraps unchecked exceptions in CompletionException
            var ex = assertThrows(CompletionException.class,
                    () -> strategy.transfer(transferProcess).join());
            assertInstanceOf(DataTransferAPIException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("Failed to get stream. HTTP response code"));
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
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            when(mockConnection.getContentType()).thenReturn(TEST_CONTENT_TYPE);
            when(mockConnection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION))
                    .thenReturn(TEST_CONTENT_DISPOSITION);
            when(mockConnection.getInputStream())
                    .thenReturn(new ByteArrayInputStream(TEST_CONTENT.getBytes()));

            // Act — await future completion before verifying interactions
            assertDoesNotThrow(() -> strategy.transfer(transferProcess).join());

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
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            when(mockConnection.getContentType()).thenReturn(TEST_CONTENT_TYPE);
            when(mockConnection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION))
                    .thenReturn(TEST_CONTENT_DISPOSITION);
            when(mockConnection.getInputStream())
                    .thenReturn(new ByteArrayInputStream(TEST_CONTENT.getBytes()));

            // Act & Assert
            assertDoesNotThrow(() -> strategy.transfer(transferProcess).join());

            verify(s3ClientService).uploadFile(
                    any(InputStream.class),
                    eq(expectedDestinationS3Properties),
                    eq(TEST_CONTENT_TYPE),
                    eq(TEST_CONTENT_DISPOSITION)
            );
        }
    }

    @Test
    @DisplayName("Should set dynamic read timeout when Content-Length is known")
    void transfer_dynamicReadTimeoutApplied_whenContentLengthKnown() throws Exception {
        // Arrange - 100 MB file
        long contentLength = 100L * 1024 * 1024;
        TransferProcess transferProcess = mockTransferProcess("http://test", List.of());
        mockS3Properties(transferProcess.getId());

        when(s3ClientService.uploadFile(any(InputStream.class), any(Map.class), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("test-etag"));

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            when(mockConnection.getContentLengthLong()).thenReturn(contentLength);
            when(mockConnection.getContentType()).thenReturn(TEST_CONTENT_TYPE);
            when(mockConnection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION)).thenReturn(TEST_CONTENT_DISPOSITION);
            when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(TEST_CONTENT.getBytes()));

            // Act
            assertDoesNotThrow(() -> strategy.transfer(transferProcess).join());

            // Assert — dynamic timeout: ceil(100 * 1024 * 1024 * 1.1 / (1024 * 1024)) = 110 s → 110_000 ms
            ArgumentCaptor<Integer> timeoutCaptor = ArgumentCaptor.forClass(Integer.class);
            verify(mockConnection, atLeastOnce()).setReadTimeout(timeoutCaptor.capture());
            assertTrue(timeoutCaptor.getAllValues().stream().anyMatch(t -> t >= 110_000),
                    "Expected dynamic read timeout >= 110_000 ms for 100 MB file");
        }
    }

    @Test
    @DisplayName("Should use fallback read timeout when Content-Length is not available")
    void transfer_fallbackReadTimeoutApplied_whenNoContentLength() throws Exception {
        // Arrange
        TransferProcess transferProcess = mockTransferProcess("http://test", List.of());
        mockS3Properties(transferProcess.getId());

        when(s3ClientService.uploadFile(any(InputStream.class), any(Map.class), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("test-etag"));

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            when(mockConnection.getContentLengthLong()).thenReturn(-1L);
            when(mockConnection.getContentType()).thenReturn(TEST_CONTENT_TYPE);
            when(mockConnection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION)).thenReturn(TEST_CONTENT_DISPOSITION);
            when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(TEST_CONTENT.getBytes()));

            // Act
            assertDoesNotThrow(() -> strategy.transfer(transferProcess).join());

            // Assert — only FALLBACK_READ_TIMEOUT (1_800_000 ms = 30 min) should be set
            ArgumentCaptor<Integer> timeoutCaptor = ArgumentCaptor.forClass(Integer.class);
            verify(mockConnection, atLeastOnce()).setReadTimeout(timeoutCaptor.capture());
            assertTrue(timeoutCaptor.getAllValues().stream().allMatch(t -> t == 1_800_000),
                    "Expected only FALLBACK_READ_TIMEOUT (1_800_000 ms) when Content-Length is absent");
        }
    }

    @Test
    @DisplayName("Should disconnect on IOException")
    void transfer_disconnectsOnIOException() throws Exception {
        // Arrange
        TransferProcess transferProcess = mockTransferProcess("http://test", List.of());

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenThrow(new IOException("Connection refused"));

            // Act & Assert — supplyAsync wraps thrown exceptions in CompletionException on .join()
            var ex = assertThrows(CompletionException.class,
                    () -> strategy.transfer(transferProcess).join());
            assertInstanceOf(DataTransferAPIException.class, ex.getCause());

            // Verify disconnect was called to release resources
            verify(mockConnection).disconnect();
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
