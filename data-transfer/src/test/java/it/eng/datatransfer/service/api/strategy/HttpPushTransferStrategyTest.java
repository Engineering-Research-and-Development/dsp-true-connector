package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import it.eng.tools.util.ToolsUtil;
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
public class HttpPushTransferStrategyTest {

    @Mock
    private S3Properties s3Properties;
    @Mock
    private S3ClientService s3ClientService;
    @Mock
    private HttpURLConnection mockConnection;

    /**
     * Strategy under test. Created manually in setUp so we can inject a direct
     * (synchronous) executor — this ensures the CompletableFuture body runs on
     * the test thread, keeping MockedConstruction&lt;URL&gt; in scope.
     */
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

    /**
     * Injects a synchronous (direct) executor so that the async body in
     * {@code CompletableFuture.supplyAsync()} executes on the calling thread.
     * This makes {@code MockedConstruction<URL>} intercept {@code new URL(...)}
     * correctly within the test's try-with-resources scope.
     */
    @BeforeEach
    void setUp() {
        // Runnable::run is a valid Executor that executes tasks on the calling thread
        strategy = new HttpPushTransferStrategy(s3Properties, s3ClientService, Runnable::run);
    }

    @Test
    @DisplayName("Should execute transfer successfully")
    void transfer_success() throws Exception {
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
                any(InputStream.class),
                eq(endpointPropertiesMap),
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
                    eq(endpointPropertiesMap),
                    eq(TEST_CONTENT_TYPE),
                    eq(TEST_CONTENT_DISPOSITION)
            );
        }
    }

    @Test
    @DisplayName("Should throw DataTransferAPIException on upload failure")
    void transfer_uploadFails_throwsException() throws Exception {
        // Arrange
        TransferProcess transferProcess = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED;

        when(s3Properties.getBucketName()).thenReturn(TEST_BUCKET);
        when(s3ClientService.generateGetPresignedUrl(eq(TEST_BUCKET), eq(DataTransferMockObjectUtil.DATASET_ID), any()))
                .thenReturn("http://presigned-url");

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {
            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);

            // Act & Assert — supplyAsync wraps unchecked exceptions in CompletionException on .join()
            var ex = assertThrows(CompletionException.class,
                    () -> strategy.transfer(transferProcess).join());
            assertInstanceOf(DataTransferAPIException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("Failed to get stream. HTTP response code"));
        }
    }

    @Test
    @DisplayName("Should set dynamic read timeout when Content-Length is known")
    void transfer_dynamicReadTimeoutApplied_whenContentLengthKnown() throws Exception {
        // Arrange
        String consumerPid = it.eng.tools.util.ToolsUtil.generateUniqueId();
        String providerPid = it.eng.tools.util.ToolsUtil.generateUniqueId();
        String transferProcessId = it.eng.tools.util.ToolsUtil.generateUniqueId();

        List<EndpointProperty> endpointProperties = List.of(
                EndpointProperty.Builder.newInstance().name(S3Utils.BUCKET_NAME).value(TEST_BUCKET).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.REGION).value(TEST_REGION).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.OBJECT_KEY).value(TEST_OBJECT_KEY).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.ACCESS_KEY).value(TEST_ACCESS_KEY).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.SECRET_KEY).value(TEST_SECRET_KEY).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.ENDPOINT_OVERRIDE).value(TEST_ENDPOINT_OVERRIDE).build()
        );

        DataAddress dataAddress = DataAddress.Builder.newInstance().endpointProperties(endpointProperties).build();
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
        when(s3ClientService.uploadFile(any(InputStream.class), any(Map.class), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("test-etag"));

        // 100 MB content-length → dynamic timeout = ceil(100*1024*1024 * 1.1 / (1024*1024)) * 1000 = 111_000 ms
        long contentLength = 100L * 1024 * 1024;

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            when(mockConnection.getContentLengthLong()).thenReturn(contentLength);
            when(mockConnection.getContentType()).thenReturn(TEST_CONTENT_TYPE);
            when(mockConnection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION)).thenReturn(TEST_CONTENT_DISPOSITION);
            when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(TEST_CONTENT.getBytes()));

            // Act — .join() ensures the synchronous future has completed before asserting
            assertDoesNotThrow(() -> strategy.transfer(transferProcess).join());

            // Assert — dynamic read timeout must have been applied (>= 110_000 ms for 100 MB)
            ArgumentCaptor<Integer> timeoutCaptor = ArgumentCaptor.forClass(Integer.class);
            verify(mockConnection, atLeastOnce()).setReadTimeout(timeoutCaptor.capture());
            assertTrue(timeoutCaptor.getAllValues().stream().anyMatch(t -> t >= 110_000),
                    "Expected at least one setReadTimeout call with value >= 110_000 ms for 100 MB file");
        }
    }

    @Test
    @DisplayName("Should use fallback read timeout when Content-Length is not available")
    void transfer_fallbackReadTimeoutApplied_whenNoContentLength() throws Exception {
        // Arrange
        List<EndpointProperty> endpointProperties = List.of(
                EndpointProperty.Builder.newInstance().name(S3Utils.BUCKET_NAME).value(TEST_BUCKET).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.REGION).value(TEST_REGION).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.OBJECT_KEY).value(TEST_OBJECT_KEY).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.ACCESS_KEY).value(TEST_ACCESS_KEY).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.SECRET_KEY).value(TEST_SECRET_KEY).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.ENDPOINT_OVERRIDE).value(TEST_ENDPOINT_OVERRIDE).build()
        );

        DataAddress dataAddress = DataAddress.Builder.newInstance().endpointProperties(endpointProperties).build();
        TransferProcess transferProcess = TransferProcess.Builder.newInstance()
                .id(it.eng.tools.util.ToolsUtil.generateUniqueId())
                .consumerPid(it.eng.tools.util.ToolsUtil.generateUniqueId())
                .providerPid(it.eng.tools.util.ToolsUtil.generateUniqueId())
                .role(IConstants.ROLE_PROVIDER)
                .dataAddress(dataAddress)
                .datasetId(TEST_DATASET_ID)
                .state(TransferState.STARTED)
                .format(DataTransferFormat.HTTP_PUSH.format())
                .build();

        when(s3Properties.getBucketName()).thenReturn(TEST_BUCKET);
        when(s3ClientService.generateGetPresignedUrl(eq(TEST_BUCKET), eq(TEST_DATASET_ID), any()))
                .thenReturn("http://presigned-url");
        when(s3ClientService.uploadFile(any(InputStream.class), any(Map.class), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("test-etag"));

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            // Content-Length not set → getContentLengthLong returns -1
            when(mockConnection.getContentLengthLong()).thenReturn(-1L);
            when(mockConnection.getContentType()).thenReturn(TEST_CONTENT_TYPE);
            when(mockConnection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION)).thenReturn(TEST_CONTENT_DISPOSITION);
            when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(TEST_CONTENT.getBytes()));

            // Act — .join() ensures the synchronous future has completed before asserting
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
        List<EndpointProperty> endpointProperties = List.of(
                EndpointProperty.Builder.newInstance().name(S3Utils.BUCKET_NAME).value(TEST_BUCKET).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.REGION).value(TEST_REGION).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.OBJECT_KEY).value(TEST_OBJECT_KEY).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.ACCESS_KEY).value(TEST_ACCESS_KEY).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.SECRET_KEY).value(TEST_SECRET_KEY).build(),
                EndpointProperty.Builder.newInstance().name(S3Utils.ENDPOINT_OVERRIDE).value(TEST_ENDPOINT_OVERRIDE).build()
        );

        DataAddress dataAddress = DataAddress.Builder.newInstance().endpointProperties(endpointProperties).build();
        TransferProcess transferProcess = TransferProcess.Builder.newInstance()
                .id(it.eng.tools.util.ToolsUtil.generateUniqueId())
                .consumerPid(it.eng.tools.util.ToolsUtil.generateUniqueId())
                .providerPid(it.eng.tools.util.ToolsUtil.generateUniqueId())
                .role(IConstants.ROLE_PROVIDER)
                .dataAddress(dataAddress)
                .datasetId(TEST_DATASET_ID)
                .state(TransferState.STARTED)
                .format(DataTransferFormat.HTTP_PUSH.format())
                .build();

        when(s3Properties.getBucketName()).thenReturn(TEST_BUCKET);
        when(s3ClientService.generateGetPresignedUrl(eq(TEST_BUCKET), eq(TEST_DATASET_ID), any()))
                .thenReturn("http://presigned-url");

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenThrow(new java.io.IOException("Connection refused"));

            // Act & Assert — supplyAsync wraps thrown exceptions in CompletionException on .join()
            var ex = assertThrows(CompletionException.class,
                    () -> strategy.transfer(transferProcess).join());
            assertInstanceOf(DataTransferAPIException.class, ex.getCause());

            // Verify disconnect was called to release resources
            verify(mockConnection).disconnect();
        }
    }
}
