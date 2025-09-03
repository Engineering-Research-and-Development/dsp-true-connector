package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferSuspensionMessage;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.event.datatransfer.ResumeDataTransfer;
import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.repository.TransferStateRepository;
import it.eng.tools.s3.service.BucketCredentialsService;
import it.eng.tools.s3.service.PresignedBucketDownloader;
import it.eng.tools.service.AuditEventPublisher;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HttpPullSuspendResumeTransferServiceTest {

    @Mock
    private AuditEventPublisher auditEventPublisher;
    @Mock
    private BucketCredentialsService bucketCredentialsService;
    @Mock
    private S3Properties s3Properties;
    @Mock
    private S3ClientProvider s3ClientProvider;
    @Mock
    private OkHttpClient httpClient;
    @Mock
    private TransferStateRepository stateRepository;
    @Mock
    private TransferProcessRepository transferProcessRepository;

    @InjectMocks
    private HttpPullSuspendResumeTransferService httpPullSuspendResumeTransferService;

    @Mock
    private BucketCredentialsEntity bucketCredentialsEntity;
    @Mock
    private S3AsyncClient asyncS3Client;

    @Test
    @DisplayName("Test download with valid URL")
    public void testDownloadWithValidUrl() {
        TransferProcess transferProcess = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;

        when(s3Properties.getBucketName()).thenReturn("test-bucket");
        when(s3Properties.getRegion()).thenReturn("test-region");
        when(bucketCredentialsService.getBucketCredentials(anyString())).thenReturn(bucketCredentialsEntity);

        when(s3ClientProvider.s3AsyncClient(any(S3ClientRequest.class))).thenReturn(asyncS3Client);

        try (MockedConstruction<PresignedBucketDownloader> mockedDownloader = mockConstruction(PresignedBucketDownloader.class,
                (mock, context) -> {
                    doNothing().when(mock).run();

                })) {
            // Act & Assert
            assertDoesNotThrow(() -> httpPullSuspendResumeTransferService.transfer(transferProcess));
        }
    }

    @Test
    @DisplayName("Test suspend transfer")
    public void testSuspendTransfer() throws NoSuchFieldException, IllegalAccessException {
        TransferSuspensionMessage transferSuspensionMessage = DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE;
        TransferProcess transferProcess = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_CONSUMER;
        PresignedBucketDownloader downloaderMock = mock(PresignedBucketDownloader.class);

        // 1. Get the Field object for 'myMap'
        Field mapField = HttpPullSuspendResumeTransferService.class.getDeclaredField("downloaders");

        // 2. Make the field accessible (if it's private)
        mapField.setAccessible(true);

        // 3. Get the Map instance from the object
        @SuppressWarnings("unchecked") // Suppress unchecked cast warning
        Map<String, PresignedBucketDownloader> map = (Map<String, PresignedBucketDownloader>) mapField.get(httpPullSuspendResumeTransferService);

        // 4. Insert the value into the map
        map.put(transferProcess.getId(), downloaderMock);

        when(transferProcessRepository.findByConsumerPidAndProviderPid(anyString(), anyString()))
                .thenReturn(Optional.of(transferProcess));

        assertDoesNotThrow(() -> httpPullSuspendResumeTransferService.onSuspendTransfer(transferSuspensionMessage));
        verify(downloaderMock).pause();
    }

    @Test
    @DisplayName("Test onResume")
    public void testOnResume() throws NoSuchFieldException, IllegalAccessException {
        TransferProcess transferProcess = DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_CONSUMER;
        when(transferProcessRepository.findById(transferProcess.getId())).thenReturn(Optional.of(transferProcess));
        PresignedBucketDownloader downloaderMock = mock(PresignedBucketDownloader.class);

        // 1. Get the Field object for 'myMap'
        Field mapField = HttpPullSuspendResumeTransferService.class.getDeclaredField("downloaders");

        // 2. Make the field accessible (if it's private)
        mapField.setAccessible(true);

        // 3. Get the Map instance from the object
        @SuppressWarnings("unchecked") // Suppress unchecked cast warning
        Map<String, PresignedBucketDownloader> map = (Map<String, PresignedBucketDownloader>) mapField.get(httpPullSuspendResumeTransferService);

        // 4. Insert the value into the map
        map.put(transferProcess.getId(), downloaderMock);

        ResumeDataTransfer resumeDataTransfer = new ResumeDataTransfer(transferProcess.getId());

        assertDoesNotThrow(() -> httpPullSuspendResumeTransferService.onResume(resumeDataTransfer));
        verify(downloaderMock).resume();
    }

    @Test
    @DisplayName("Test onTransferComplete")
    public void testOnTransferComplete() throws NoSuchFieldException, IllegalAccessException {
        TransferProcess transferProcess = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_CONSUMER;
        PresignedBucketDownloader downloaderMock = mock(PresignedBucketDownloader.class);

        // 1. Get the Field object for 'myMap'
        Field mapField = HttpPullSuspendResumeTransferService.class.getDeclaredField("downloaders");

        // 2. Make the field accessible (if it's private)
        mapField.setAccessible(true);
        // 3. Get the Map instance from the object
        @SuppressWarnings("unchecked") // Suppress unchecked cast warning
        Map<String, PresignedBucketDownloader> map = (Map<String, PresignedBucketDownloader>) mapField.get(httpPullSuspendResumeTransferService);
        // 4. Insert the value into the map
        map.put(transferProcess.getId(), downloaderMock);

        when(transferProcessRepository.findByConsumerPidAndProviderPid(anyString(), anyString())).thenReturn(Optional.of(transferProcess));

        TransferCompletionMessage transferCompletionMessage = DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE;

        assertDoesNotThrow(() -> httpPullSuspendResumeTransferService.onTransferComplete(transferCompletionMessage));

        verify(downloaderMock).stop();
    }
}
