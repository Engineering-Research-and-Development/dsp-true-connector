package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.exceptions.PresignedUrlExpiredException;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.datatransfer.service.CancellationRegistry;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

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
    @Mock
    private TransferArtifactStateRepository transferArtifactStateRepository;
    @Mock
    private CancellationRegistry cancellationRegistry;

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
        // Pre-load inner classes before any test opens a MockedConstruction<URL> scope.
        // Mockito/ByteBuddy inline mocking + mockConstruction(URL.class) causes a JVM
        // classloader interference that prevents inner classes from loading on-demand
        // inside the try-with-resources block.  Touching the class literals here forces
        // the JVM to resolve them while the classloader is in a clean state.
        @SuppressWarnings("unused")
        Class<?> preload = TransferArtifactState.Builder.class;
        try {
            Class.forName("it.eng.datatransfer.service.api.strategy.CheckpointCallbackImpl",
                    false, HttpPullTransferStrategy.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot pre-load CheckpointCallbackImpl", e);
        }

        // Set up default mock behavior
        when(transferArtifactStateRepository.findById(anyString())).thenReturn(Optional.empty());
        when(transferArtifactStateRepository.save(any(TransferArtifactState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cancellationRegistry.register(anyString())).thenReturn(new AtomicBoolean(false));
        
        // Runnable::run is a valid Executor that executes tasks on the calling thread
        strategy = new HttpPullTransferStrategy(s3ClientService, s3Properties, Runnable::run,
                transferArtifactStateRepository, cancellationRegistry);
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
                eq(TEST_CONTENT_DISPOSITION),
                any(AtomicBoolean.class),
                any()
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

            // The final flush in thenAccept must persist the in-memory state
            verify(transferArtifactStateRepository, atLeastOnce())
                    .save(any(TransferArtifactState.class));

            verify(s3ClientService).uploadFile(
                    any(InputStream.class),
                    eq(expectedDestinationS3Properties),
                    eq(TEST_CONTENT_TYPE),
                    eq(TEST_CONTENT_DISPOSITION),
                    any(AtomicBoolean.class),
                    any()
            );
        }
    }

    @Test
    @DisplayName("Should throw DataTransferAPIException on non-OK HTTP response")
    void transfer_throwsException_onHttpError() {
        TransferProcess transferProcess = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED;
        mockS3Properties(transferProcess.getId());

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);

            // Act & Assert
            CompletionException exception = assertThrows(CompletionException.class,
                    () -> strategy.transfer(transferProcess).join());
            assertTrue(exception.getCause() instanceof DataTransferAPIException);
            assertTrue(exception.getCause().getMessage().contains("Failed to get stream. HTTP response code: 400"));

            // Verify disconnect was called
            verify(mockConnection).disconnect();
        } catch (IOException e) {
            fail("Unexpected IOException in test setup: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should handle authorization header when present")
    void transfer_includesAuthorizationHeader_whenAuthDataPresent() throws Exception {
        TransferProcess transferProcess = mockTransferProcess("http://test", List.of(
                EndpointProperty.Builder.newInstance()
                        .name(IConstants.AUTH_TYPE)
                        .value("Bearer")
                        .build(),
                EndpointProperty.Builder.newInstance()
                        .name(IConstants.AUTHORIZATION)
                        .value("test-token")
                        .build()
        ));

        Map<String, String> expectedDestinationS3Properties = mockS3Properties(transferProcess.getId());

        when(s3ClientService.uploadFile(
                any(InputStream.class),
                eq(expectedDestinationS3Properties),
                eq(TEST_CONTENT_TYPE),
                eq(TEST_CONTENT_DISPOSITION),
                any(AtomicBoolean.class),
                any()
        )).thenReturn(CompletableFuture.completedFuture("test-etag"));

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            when(mockConnection.getContentType()).thenReturn(TEST_CONTENT_TYPE);
            when(mockConnection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION)).thenReturn(TEST_CONTENT_DISPOSITION);
            when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(TEST_CONTENT.getBytes()));

            // Act
            assertDoesNotThrow(() -> strategy.transfer(transferProcess).join());

            // Assert
            verify(mockConnection).setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer test-token");
            verify(s3ClientService).uploadFile(
                    any(InputStream.class),
                    eq(expectedDestinationS3Properties),
                    eq(TEST_CONTENT_TYPE),
                    eq(TEST_CONTENT_DISPOSITION),
                    any(AtomicBoolean.class),
                    any()
            );
        }
    }

    @Test
    @DisplayName("Should apply dynamic read timeout based on Content-Length")
    void transfer_dynamicReadTimeoutApplied_whenContentLengthAvailable() throws Exception {
        // Arrange — 100 MB file
        long contentLength = 100L * 1024L * 1024L;
        TransferProcess transferProcess = mockTransferProcess("http://test", List.of());
        mockS3Properties(transferProcess.getId());

        when(s3ClientService.uploadFile(any(InputStream.class), any(Map.class), anyString(), anyString(),
                any(AtomicBoolean.class), any())).thenReturn(CompletableFuture.completedFuture("test-etag"));

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

        when(s3ClientService.uploadFile(any(InputStream.class), any(Map.class), anyString(), anyString(),
                any(AtomicBoolean.class), any())).thenReturn(CompletableFuture.completedFuture("test-etag"));

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            when(mockConnection.getContentLengthLong()).thenReturn(-1L);
            when(mockConnection.getContentType()).thenReturn(TEST_CONTENT_TYPE);
            when(mockConnection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION)).thenReturn(TEST_CONTENT_DISPOSITION);
            when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(TEST_CONTENT.getBytes()));

            // Act
            assertDoesNotThrow(() -> strategy.transfer(transferProcess).join());

            // Assert — fallback timeout: 1_800_000 ms (30 minutes)
            verify(mockConnection).setReadTimeout(1_800_000);
        }
    }

    @Test
    @DisplayName("Should handle IOException and disconnect connection")
    void transfer_handlesIOException_andDisconnectsConnection() {
        TransferProcess transferProcess = mockTransferProcess("http://test", List.of());
        mockS3Properties(transferProcess.getId());

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> {
                    when(mock.openConnection()).thenThrow(new IOException("Connection failed"));
                })) {

            // Act & Assert
            CompletionException exception = assertThrows(CompletionException.class,
                    () -> strategy.transfer(transferProcess).join());
            assertTrue(exception.getCause() instanceof DataTransferAPIException);
            assertEquals("Connection failed", exception.getCause().getMessage());
        }
    }

    @Test
    @DisplayName("Should throw PresignedUrlExpiredException on HTTP 403")
    void transfer_throwsPresignedUrlExpiredException_on403() {
        TransferProcess transferProcess = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED;
        mockS3Properties(transferProcess.getId());

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_FORBIDDEN);

            CompletionException exception = assertThrows(CompletionException.class,
                    () -> strategy.transfer(transferProcess).join());
            assertInstanceOf(PresignedUrlExpiredException.class, exception.getCause());

            // Connection should be disconnected exactly once (not double-disconnected)
            verify(mockConnection, times(1)).disconnect();
        } catch (java.io.IOException e) {
            fail("Unexpected IOException in test setup: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should add Range header when resuming from a non-zero offset")
    void transfer_addsRangeHeader_whenResumingFromOffset() throws Exception {
        TransferProcess transferProcess = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED;
        mockS3Properties(transferProcess.getId());

        TransferArtifactState existingState = TransferArtifactState.Builder.newInstance()
                .id(transferProcess.getId())
                .downloadedBytes(512L)
                .build();
        when(transferArtifactStateRepository.findById(transferProcess.getId()))
                .thenReturn(Optional.of(existingState));

        when(s3ClientService.uploadFile(any(), any(), anyString(), anyString(), any(AtomicBoolean.class), any()))
                .thenReturn(CompletableFuture.completedFuture("etag"));

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenReturn(206);
            when(mockConnection.getContentType()).thenReturn(TEST_CONTENT_TYPE);
            when(mockConnection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION)).thenReturn(TEST_CONTENT_DISPOSITION);
            when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(TEST_CONTENT.getBytes()));

            assertDoesNotThrow(() -> strategy.transfer(transferProcess).join());

            verify(mockConnection).setRequestProperty(HttpHeaders.RANGE, "bytes=512-");
        }
    }

    @Test
    @DisplayName("onPartCompleted should persist rangeStart + totalBytesUploaded as absolute offset")
    void transfer_checkpointCallback_savesAbsoluteOffset() throws Exception {
        TransferProcess transferProcess = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED;
        mockS3Properties(transferProcess.getId());

        long rangeStart = 1024L;
        TransferArtifactState existingState = TransferArtifactState.Builder.newInstance()
                .id(transferProcess.getId())
                .downloadedBytes(rangeStart)
                .build();
        when(transferArtifactStateRepository.findById(transferProcess.getId()))
                .thenReturn(Optional.of(existingState));

        // Capture the checkpointCallback argument passed to uploadFile
        org.mockito.ArgumentCaptor<it.eng.tools.s3.service.upload.UploadCheckpointCallback> callbackCaptor =
                org.mockito.ArgumentCaptor.forClass(it.eng.tools.s3.service.upload.UploadCheckpointCallback.class);

        when(s3ClientService.uploadFile(any(), any(), anyString(), anyString(), any(AtomicBoolean.class), callbackCaptor.capture()))
                .thenAnswer(invocation -> {
                    it.eng.tools.s3.service.upload.UploadCheckpointCallback cb = callbackCaptor.getValue();
                    cb.onPartCompleted(1, "etag-1", 512L); // 512 bytes uploaded so far in this session
                    return CompletableFuture.completedFuture("etag-1");
                });

        try (MockedConstruction<URL> mockedUrl = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(mockConnection))) {

            when(mockConnection.getResponseCode()).thenReturn(206);
            when(mockConnection.getContentType()).thenReturn(TEST_CONTENT_TYPE);
            when(mockConnection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION)).thenReturn(TEST_CONTENT_DISPOSITION);
            when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(TEST_CONTENT.getBytes()));

            assertDoesNotThrow(() -> strategy.transfer(transferProcess).join());
        }

        // Verify that the saved state has absoluteOffset = rangeStart + totalBytesUploaded = 1024 + 512 = 1536
        org.mockito.ArgumentCaptor<TransferArtifactState> stateCaptor =
                org.mockito.ArgumentCaptor.forClass(TransferArtifactState.class);
        verify(transferArtifactStateRepository, atLeastOnce()).save(stateCaptor.capture());
        assertTrue(stateCaptor.getAllValues().stream()
                .anyMatch(s -> s.getDownloadedBytes() == rangeStart + 512L),
                "Expected saved state with downloadedBytes = " + (rangeStart + 512L));
    }

    private TransferProcess mockTransferProcess(String endpoint, List<EndpointProperty> endpointProperties) {
        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpoint(endpoint)
                .endpointProperties(endpointProperties)
                .build();

        return TransferProcess.Builder.newInstance()
                .id(new ObjectId().toHexString())
                .state(TransferState.STARTED)
                .dataAddress(dataAddress)
                .build();
    }

    private Map<String, String> mockS3Properties(String key) {
        lenient().when(s3Properties.getBucketName()).thenReturn(TEST_BUCKET);
        lenient().when(s3Properties.getEndpoint()).thenReturn(TEST_ENDPOINT);
        lenient().when(s3Properties.getRegion()).thenReturn(TEST_REGION);
        lenient().when(s3Properties.getAccessKey()).thenReturn(TEST_ACCESS_KEY);
        lenient().when(s3Properties.getSecretKey()).thenReturn(TEST_SECRET_KEY);

        return Map.of(
                S3Utils.OBJECT_KEY, key,
                S3Utils.BUCKET_NAME, TEST_BUCKET,
                S3Utils.ENDPOINT_OVERRIDE, TEST_ENDPOINT,
                S3Utils.REGION, TEST_REGION,
                S3Utils.ACCESS_KEY, TEST_ACCESS_KEY,
                S3Utils.SECRET_KEY, TEST_SECRET_KEY
        );
    }
}