package it.eng.datatransfer.service.api;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.exceptions.DownloadException;
import it.eng.datatransfer.service.DataTransferService;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.model.ExternalData;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventPublisher;
import org.apache.tomcat.util.codec.binary.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ContentDisposition;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RestArtifactServiceTest {

    private static final String FILE = "test.json";
    private static final String CONTENT_DISPOSITION = ContentDisposition.attachment()
            .filename(FILE)
            .build()
            .toString();
    private MockHttpServletResponse mockHttpServletResponse;
    @Mock
    private DataTransferService dataTransferService;
    @Mock
    private AuditEventPublisher publisher;
    @Mock
    private OkHttpRestClient okHttpRestClient;
    @Mock
    private ArtifactTransferService artifactTransferService;

    @InjectMocks
    private RestArtifactService restArtifactService;

    private static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID_TRANSFER";
    private static final String PROVIDER_PID = "urn:uuid:PROVIDER_PID_TRANSFER";
    private static final String TRANSACTION_ID = Base64.encodeBase64URLSafeString((CONSUMER_PID + "|" + PROVIDER_PID).getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("Get artifact - decode transactionId fail")
    public void getArtifact_decodeTransactionIdFail() {
        String badTransactionId = Base64.encodeBase64URLSafeString((CONSUMER_PID + PROVIDER_PID).getBytes(StandardCharsets.UTF_8));

        assertThrows(DownloadException.class, () -> restArtifactService.getArtifact(badTransactionId, mockHttpServletResponse));
    }

    @Test
    @DisplayName("Get artifact - dataset has no artifact")
    public void getArtifact_datasetHasNoArtifactId() {
        when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED);
        doThrow(DownloadException.class).when(artifactTransferService)
                .findArtifact(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED);

        assertThrows(DownloadException.class, () -> restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));
    }

    @Test
    @DisplayName("Get external data - success")
    public void getExternalData_success() {
        mockHttpServletResponse = new MockHttpServletResponse();
        when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED);
        when(artifactTransferService.findArtifact(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED))
                .thenReturn(DataTransferMockObjectUtil.ARTIFACT_EXTERNAL);

        ExternalData externalData = new ExternalData();
        externalData.setData("some_data".getBytes());
        externalData.setContentType(okhttp3.MediaType.parse("text/plain; charset=utf-8"));
        GenericApiResponse<ExternalData> externalResponse = new GenericApiResponse<ExternalData>();
        externalResponse.setData(externalData);
        externalResponse.setSuccess(true);
        when(okHttpRestClient.downloadData(DataTransferMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null))
                .thenReturn(externalResponse);

        assertDoesNotThrow(() -> restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));

        verify(publisher).publishEvent(any(ArtifactConsumedEvent.class));
    }

    @Test
    @DisplayName("Get external data - fail")
    public void getExternalData_fail() {
        when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED);
        when(artifactTransferService.findArtifact(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED))
                .thenReturn(DataTransferMockObjectUtil.ARTIFACT_EXTERNAL);
        GenericApiResponse<ExternalData> externalResponse = new GenericApiResponse<ExternalData>();
        externalResponse.setSuccess(false);
        when(okHttpRestClient.downloadData(DataTransferMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null))
                .thenReturn(externalResponse);

        assertThrows(DownloadException.class, () -> restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));
    }

    @Test
    @DisplayName("Get file - throws DataTransferAPIException because FILE artifacts use presigned URLs now")
    public void getFile_throwsUnsupported() {
        when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED);
        when(artifactTransferService.findArtifact(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED))
                .thenReturn(DataTransferMockObjectUtil.ARTIFACT_FILE);

        assertThrows(DataTransferAPIException.class, () -> restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));
    }

}
