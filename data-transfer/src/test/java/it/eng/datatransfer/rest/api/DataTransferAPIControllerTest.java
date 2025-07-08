package it.eng.datatransfer.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.event.TransferArtifactEvent;
import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.DataTransferRequest;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.response.GenericApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataTransferAPIControllerTest {

    @Mock
    private DataTransferAPIService apiService;
    @Mock
    private ApplicationEventPublisher publisher;

    @InjectMocks
    private DataTransferAPIController controller;

    private final DataTransferRequest dataTransferRequest = new DataTransferRequest(DataTransferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED.getId(),
            DataTransferFormat.HTTP_PULL.name(),
            null);

    @Test
    @DisplayName("Find transfer process by id, state and all")
    public void getTransfersProcess() {
        when(apiService.findDataTransfers(anyString(), anyString(), isNull()))
                .thenReturn(Collections.singletonList(TransferSerializer.serializePlainJsonNode(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER)));
        ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.getTransfersProcess("test", TransferState.REQUESTED.name(), null);
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertFalse(response.getBody().getData().isEmpty());

        when(apiService.findDataTransfers(anyString(), isNull(), isNull()))
                .thenReturn(new ArrayList<>());
        response = controller.getTransfersProcess("test_not_found", null, null);
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertTrue(response.getBody().getData().isEmpty());

        when(apiService.findDataTransfers(isNull(), anyString(), anyString()))
                .thenReturn(Collections.singletonList(TransferSerializer.serializePlainJsonNode(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED)));
        response = controller.getTransfersProcess(null, TransferState.STARTED.name(), DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getRole());
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertFalse(response.getBody().getData().isEmpty());

        when(apiService.findDataTransfers(isNull(), isNull(), isNull()))
                .thenReturn(Arrays.asList(TransferSerializer.serializePlainJsonNode(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER),
                        TransferSerializer.serializePlainJsonNode(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED)));
        response = controller.getTransfersProcess(null, null, null);
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertFalse(response.getBody().getData().isEmpty());

    }

    @Test
    @DisplayName("Request transfer process success")
    public void requestTransfer_success() {
        when(apiService.requestTransfer(any(DataTransferRequest.class)))
                .thenReturn(TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.requestTransfer(dataTransferRequest);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).requestTransfer(dataTransferRequest);
    }

    @Test
    @DisplayName("Request transfer process failed")
    public void requestTransfer_failed() {
        doThrow(new DataTransferAPIException("Something not correct - tests"))
                .when(apiService).requestTransfer(any(DataTransferRequest.class));

        assertThrows(DataTransferAPIException.class, () -> controller.requestTransfer(dataTransferRequest));
    }

    @Test
    @DisplayName("Start transfer process success")
    public void startTransfer_success() {

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
    }

    @Test
    @DisplayName("Start transfer process failed")
    public void startTransfer_failed() {

        doThrow(new DataTransferAPIException("Something not correct - tests"))
                .when(apiService).startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        assertThrows(DataTransferAPIException.class, () -> controller.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
    }

    @Test
    @DisplayName("Complete transfer process success")
    public void completeTransfer_success() {

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.completeTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).completeTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
    }

    @Test
    @DisplayName("Complete transfer process failed")
    public void completeTransfer_failed() {

        doThrow(new DataTransferAPIException("Something not correct - tests"))
                .when(apiService).completeTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());

        assertThrows(DataTransferAPIException.class, () -> controller.completeTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()));
    }

    @Test
    @DisplayName("Suspend transfer process success")
    public void suspendTransfer_success() {

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.suspendTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).suspendTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER.getId());
    }

    @Test
    @DisplayName("Suspend transfer process failed")
    public void suspendTransfer_failed() {

        doThrow(new DataTransferAPIException("Something not correct - tests"))
                .when(apiService).suspendTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER.getId());

        assertThrows(DataTransferAPIException.class, () -> controller.suspendTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER.getId()));
    }

    @Test
    @DisplayName("Terminate transfer process success")
    public void terminateTransfer_success() {

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.terminateTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).terminateTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId());
    }

    @Test
    @DisplayName("Terminate transfer process failed")
    public void terminateTransfer_failed() {

        doThrow(new DataTransferAPIException("Something not correct - tests"))
                .when(apiService).terminateTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId());

        assertThrows(DataTransferAPIException.class, () -> controller.terminateTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId()));
    }

    @Test
    @DisplayName("Download data - success")
    public void downloadData_success() throws IllegalStateException {
        ArgumentCaptor<TransferArtifactEvent> eventCaptor = ArgumentCaptor.forClass(TransferArtifactEvent.class);
        when(apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> controller.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(publisher).publishEvent(eventCaptor.capture());

        TransferArtifactEvent capturedEvent = eventCaptor.getValue();
        assertTrue(capturedEvent.isDownload());
        assertEquals(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(),
                capturedEvent.getTransferProcessId());
        assertEquals("Download completed successfully for process " + DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(), capturedEvent.getMessage());
    }

    @Test
    @DisplayName("Download data - fail")
    public void downloadData_fail() throws IllegalStateException {
        ArgumentCaptor<TransferArtifactEvent> eventCaptor = ArgumentCaptor.forClass(TransferArtifactEvent.class);
        DataTransferAPIException exception = new DataTransferAPIException("message");

        when(apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(CompletableFuture.failedFuture(exception));

        controller.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        verify(publisher).publishEvent(eventCaptor.capture());

        TransferArtifactEvent capturedEvent = eventCaptor.getValue();
        assertFalse(capturedEvent.isDownload());
        assertEquals(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(),
                capturedEvent.getTransferProcessId());
        assertEquals("Download failed: " + exception.getMessage(),
                capturedEvent.getMessage());
    }

    @Test
    @DisplayName("View data - success")
    public void viewData_success() throws IllegalStateException {
        when(apiService.viewData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn("http://presignUrl.test/viewData");

        assertDoesNotThrow(() -> controller.viewData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
    }

    @Test
    @DisplayName("View data - fail")
    public void viewData_fail() throws IllegalStateException {
        doThrow(new DataTransferAPIException("message")).when(apiService).viewData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        assertThrows(DataTransferAPIException.class, () -> controller.viewData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
    }
}
