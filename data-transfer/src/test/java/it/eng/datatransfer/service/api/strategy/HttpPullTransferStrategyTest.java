package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.model.IConstants;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HttpPullTransferStrategyTest {

    @Mock
    private HttpPullTransferService httpPullTransferService;
    @Mock
    private HttpPullSuspendResumeTransferService httpPullSuspendResumeTransferService;
    @Mock
    private OkHttpClient okHttpClient;
    @Mock
    private Request request;
    @Mock
    private Call call;
    @Mock
    private Response response;

    @InjectMocks
    private HttpPullTransferStrategy strategy;

    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_CONTENT = "test-content";
    private static final String TEST_CONTENT_TYPE = "application/json";
    private static final String TEST_CONTENT_DISPOSITION = "attachment; filename=test.json";

    @Test
    public void testSuspendResumeTransfer() throws DataTransferAPIException, IOException {
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

        when(call.execute()).thenReturn(response);
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(response.isSuccessful()).thenReturn(true);
        when(response.header("Accept-Ranges")).thenReturn("bytes"); // Simulate 206 Partial Content

        when(httpPullSuspendResumeTransferService.transfer(transferProcess))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act & Assert
        assertDoesNotThrow(() -> strategy.transfer(transferProcess));

        verify(httpPullSuspendResumeTransferService).transfer(transferProcess);
        verifyNoInteractions(httpPullTransferService);
    }

    @Test
    public void testSuspendResumeTransfer_Failure() {
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

        try {
            when(call.execute()).thenReturn(response);
            when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
            when(response.isSuccessful()).thenReturn(false);
            when(response.code()).thenReturn(404); // Simulate failure

            // Act & Assert
            DataTransferAPIException ex = assertThrows(DataTransferAPIException.class,
                    () -> strategy.transfer(transferProcess));
            assertTrue(ex.getMessage().contains("Failed to fetch data from endpoint"));

            verifyNoInteractions(httpPullTransferService);
            verifyNoInteractions(httpPullSuspendResumeTransferService);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testPullStrategy() {
        // Arrange
        TransferProcess transferProcess = mockTransferProcess("http://test", List.of());

        try {
            when(call.execute()).thenReturn(response);
            when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
            when(response.isSuccessful()).thenReturn(true);

            when(httpPullTransferService.transfer(transferProcess))
                    .thenReturn(CompletableFuture.completedFuture(null));

            // Act & Assert
            assertDoesNotThrow(() -> strategy.transfer(transferProcess));

            verify(httpPullTransferService).transfer(transferProcess);
            verifyNoInteractions(httpPullSuspendResumeTransferService);
        } catch (IOException | DataTransferAPIException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testPullStrategy_Failure() {
        // Arrange
        TransferProcess transferProcess = mockTransferProcess("http://test", List.of());

        try {
            when(call.execute()).thenReturn(response);
            when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
            when(response.isSuccessful()).thenReturn(false);
            when(response.code()).thenReturn(404); // Simulate failure

            // Act & Assert
            DataTransferAPIException ex = assertThrows(DataTransferAPIException.class,
                    () -> strategy.transfer(transferProcess));
            assertTrue(ex.getMessage().contains("Failed to fetch data from endpoint"));

            verifyNoInteractions(httpPullTransferService);
            verifyNoInteractions(httpPullSuspendResumeTransferService);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testServerErrorResponse() {
        // Arrange
        TransferProcess transferProcess = mockTransferProcess("http://test", List.of());

        try {
            when(call.execute()).thenReturn(response);
            when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
            when(response.isSuccessful()).thenReturn(false);

            // Act & Assert
            DataTransferAPIException ex = assertThrows(DataTransferAPIException.class,
                    () -> strategy.transfer(transferProcess));
            assertTrue(ex.getMessage().contains("Failed to fetch data from endpoint"));

            verifyNoInteractions(httpPullTransferService);
            verifyNoInteractions(httpPullSuspendResumeTransferService);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /*
        @Test
        @DisplayName("Should execute transfer successfully")
        void transfer_success() throws Exception {
            TransferProcess transferProcess = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED;

            when(s3Properties.getBucketName()).thenReturn(TEST_BUCKET);

            when(s3ClientService.uploadFile(
                    any(InputStream.class),
                    eq(TEST_BUCKET),
                    eq(transferProcess.getId()),
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
                        eq(TEST_BUCKET),
                        eq(transferProcess.getId()),
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

            when(s3Properties.getBucketName()).thenReturn(TEST_BUCKET);

            when(s3ClientService.uploadFile(
                    any(InputStream.class),
                    eq(TEST_BUCKET),
                    eq(transferProcess.getId()),
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
                        eq(TEST_BUCKET),
                        eq(transferProcess.getId()),
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

            when(s3Properties.getBucketName()).thenReturn(TEST_BUCKET);

            when(s3ClientService.uploadFile(
                    any(InputStream.class),
                    eq(TEST_BUCKET),
                    eq(transferProcess.getId()),
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
                        eq(TEST_BUCKET),
                        eq(transferProcess.getId()),
                        eq(TEST_CONTENT_TYPE),
                        eq(TEST_CONTENT_DISPOSITION)
                );
            }
        }

        */
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

}
