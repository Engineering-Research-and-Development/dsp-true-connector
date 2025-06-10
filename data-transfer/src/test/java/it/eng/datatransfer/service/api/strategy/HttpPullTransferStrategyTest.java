package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HttpPullTransferStrategyTest {

    @Mock
    private S3Properties s3Properties;
    @Mock
    private S3AsyncClient s3AsyncClient;

    @InjectMocks
    private HttpPullTransferStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = Mockito.spy(new HttpPullTransferStrategy(s3Properties, s3AsyncClient));
    }

    @Test
    @DisplayName("Should execute transfer successfully")
    void transfer_success() throws Exception {
        // Arrange
        when(s3Properties.getBucketName()).thenReturn("test");
        doReturn(CompletableFuture.completedFuture("test-key"))
                .when(strategy)
                .uploadStream(anyString(), isNull(), anyString(), anyString());

        // Act & Assert
        assertDoesNotThrow(() -> strategy.transfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED));
        verify(strategy).uploadStream(anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw DataTransferAPIException on upload failure")
    void transfer_uploadFails_throwsException() {
        // Arrange
        when(s3Properties.getBucketName()).thenReturn("test");
        TransferProcess process = DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED;
        doThrow(new RuntimeException("fail"))
                .when(strategy).uploadStream(anyString(), any(), anyString(), anyString());

        // Act & Assert
        DataTransferAPIException ex = assertThrows(DataTransferAPIException.class,
                () -> strategy.transfer(process));
        assertTrue(ex.getMessage().contains("Download failed"));
    }

    @Test
    @DisplayName("Should set Authorization header if present in endpoint properties")
    void transfer_withAuthorizationHeader() throws Exception {
        // Arrange
        when(s3Properties.getBucketName()).thenReturn("test");
        EndpointProperty authType = EndpointProperty.Builder.newInstance()
                .name(IConstants.AUTH_TYPE)
                .value("Bearer")
                .build();
        EndpointProperty token = EndpointProperty.Builder.newInstance()
                .name(IConstants.AUTHORIZATION)
                .value("token123")
                .build();
        TransferProcess process = mockTransferProcess("http://test", List.of(authType, token), null);

        doReturn(CompletableFuture.completedFuture("test-key"))
                .when(strategy).uploadStream(anyString(), eq("Bearer token123"), anyString(), anyString());

        // Act
        strategy.transfer(process);

        // Assert
        verify(strategy).uploadStream(anyString(), eq("Bearer token123"), anyString(), anyString());
    }

    @Test
    @DisplayName("Should not set Authorization header if not present")
    void transfer_withoutAuthorizationHeader() throws Exception {
        // Arrange
        when(s3Properties.getBucketName()).thenReturn("test");
        TransferProcess process = mockTransferProcess("http://test", List.of(), null);

        doReturn(CompletableFuture.completedFuture("test-key"))
                .when(strategy).uploadStream(anyString(), isNull(), anyString(), anyString());

        // Act
        strategy.transfer(process);

        // Assert
        verify(strategy).uploadStream(anyString(), isNull(), anyString(), anyString());
    }

    // Helper to create a mock TransferProcess
    private TransferProcess mockTransferProcess(String endpoint, List<EndpointProperty> props, String id) {
        DataAddress dataAddress = mock(DataAddress.class);
        when(dataAddress.getEndpoint()).thenReturn(endpoint);
        when(dataAddress.getEndpointProperties()).thenReturn(props);
        TransferProcess process = mock(TransferProcess.class);
        when(process.getDataAddress()).thenReturn(dataAddress);
        when(process.getId()).thenReturn(id != null ? id : "process-id");
        return process;
    }

}
