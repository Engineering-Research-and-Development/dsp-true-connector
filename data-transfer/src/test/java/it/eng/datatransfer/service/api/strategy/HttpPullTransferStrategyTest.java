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
