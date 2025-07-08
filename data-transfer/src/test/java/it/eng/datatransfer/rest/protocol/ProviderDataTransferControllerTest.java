package it.eng.datatransfer.rest.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.exceptions.TransferProcessExistsException;
import it.eng.datatransfer.exceptions.TransferProcessNotFoundException;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.DataTransferService;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProviderDataTransferControllerTest {

    @Mock
    private DataTransferService dataTransferService;

    @InjectMocks
    private ProviderDataTransferController controller;

    @Test
    @DisplayName("Get TransferProcess for ProviderPid")
    public void geTransferProcess() {
        when(dataTransferService.findTransferProcessByProviderPid(DataTransferMockObjectUtil.PROVIDER_PID))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER);
        assertEquals(HttpStatus.OK, controller.getTransferProcessByProviderPid(DataTransferMockObjectUtil.PROVIDER_PID).getStatusCode());
    }

    @Test
    @DisplayName("Get TransferProcess for ProviderPid - not found")
    public void transferProcessNtFound() {
        when(dataTransferService.findTransferProcessByProviderPid(DataTransferMockObjectUtil.PROVIDER_PID))
                .thenThrow(new TransferProcessNotFoundException("Not found"));
        assertThrows(TransferProcessNotFoundException.class,
                () -> controller.getTransferProcessByProviderPid(DataTransferMockObjectUtil.PROVIDER_PID).getStatusCode());
    }

    // initiate transfer
    @Test
    @DisplayName("Initiate TransferProcess")
    public void initateDataTransfer() {
        when(dataTransferService.initiateDataTransfer(any(TransferRequestMessage.class)))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER);
        ResponseEntity<JsonNode> response = controller.initiateDataTransfer(TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_REQUEST_MESSAGE));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    @DisplayName("Initiate TransferProcess - invalid request body")
    public void initateDataTransfer_invalidBody() {
        assertThrows(ValidationException.class, () ->
                controller.initiateDataTransfer(TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE))
        );
    }

    @Test
    @DisplayName("Initiate TransferProcess - service error")
    public void initateDataTransfer_service_error() {
        when(dataTransferService.initiateDataTransfer(any(TransferRequestMessage.class)))
                .thenThrow(new TransferProcessExistsException("message", DataTransferMockObjectUtil.PROVIDER_PID));
        assertThrows(TransferProcessExistsException.class, () ->
                controller.initiateDataTransfer(TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_REQUEST_MESSAGE))
        );
    }

    // start
    @Test
    @DisplayName("Start TransferProcess")
    public void startDataTransfer() {
        when(dataTransferService.startDataTransfer(any(TransferStartMessage.class), isNull(), any(String.class)))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED);

        ResponseEntity<Void> response = controller.startDataTransfer(DataTransferMockObjectUtil.PROVIDER_PID,
                TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Start TransferProcess - invalid request body")
    public void startDataTransfer_invalidBody() {
        assertThrows(ValidationException.class, () ->
                controller.startDataTransfer(DataTransferMockObjectUtil.PROVIDER_PID,
                        TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE)));
    }

    @Test
    @DisplayName("Start TransferProcess - error service")
    public void startDataTransfer_errorService() {
        when(dataTransferService.startDataTransfer(any(TransferStartMessage.class), isNull(), any(String.class)))
                .thenThrow(new TransferProcessNotFoundException("TransferProcess not found test"));
        assertThrows(TransferProcessNotFoundException.class, () ->
                controller.startDataTransfer(DataTransferMockObjectUtil.PROVIDER_PID,
                        TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE)));
    }

    // complete
    @Test
    @DisplayName("Complete TransferProcess")
    public void completeDataTransfer() {
        when(dataTransferService.completeDataTransfer(any(TransferCompletionMessage.class), isNull(), any(String.class)))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED);
        ResponseEntity<Void> response = controller.completeDataTransfer(DataTransferMockObjectUtil.PROVIDER_PID,
                TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Complete TransferProcess - invalid request body")
    public void completeDataTransfer_invalidBody() {
        assertThrows(ValidationException.class, () ->
                controller.completeDataTransfer(DataTransferMockObjectUtil.PROVIDER_PID, TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE))
        );
    }

    @Test
    @DisplayName("Complete TransferProcess - error service")
    public void completeDataTransfer_errorService() {
        when(dataTransferService.completeDataTransfer(any(TransferCompletionMessage.class), isNull(), any(String.class)))
                .thenThrow(TransferProcessNotFoundException.class);
        assertThrows(TransferProcessNotFoundException.class,
                () -> controller.completeDataTransfer(DataTransferMockObjectUtil.PROVIDER_PID,
                        TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE)));
    }

    // terminate data transfer
    @Test
    @DisplayName("Terminate TransferProcess")
    public void terminateDataTransfer() {
        when(dataTransferService.terminateDataTransfer(any(TransferTerminationMessage.class), isNull(), any(String.class)))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_TERMINATED);
        ResponseEntity<Void> response = controller.terminateDataTransfer(DataTransferMockObjectUtil.PROVIDER_PID,
                TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Terminate TransferProcess - invalid request body")
    public void terminateDataTransfer_invalidBody() {
        assertThrows(ValidationException.class, () ->
                controller.terminateDataTransfer(DataTransferMockObjectUtil.PROVIDER_PID, TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE))
        );
    }

    @Test
    @DisplayName("Terminate TransferProcess - error service")
    public void terminateDataTransfer_errorService() {
        when(dataTransferService.terminateDataTransfer(any(TransferTerminationMessage.class), isNull(), any(String.class)))
                .thenThrow(TransferProcessNotFoundException.class);
        assertThrows(TransferProcessNotFoundException.class,
                () -> controller.terminateDataTransfer(DataTransferMockObjectUtil.PROVIDER_PID,
                        TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE)));
    }

    // suspend data transfer
    @Test
    @DisplayName("Suspend/pause TransferProcess")
    public void suspenseDataTransfer() {
        when(dataTransferService.suspendDataTransfer(any(TransferSuspensionMessage.class), isNull(), any(String.class)))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER);
        ResponseEntity<Void> response = controller.suspenseDataTransfer(DataTransferMockObjectUtil.PROVIDER_PID,
                TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Suspend TransferProcess - invalid request body")
    public void suspenseDataTransfer_invalidBody() {
        assertThrows(ValidationException.class, () ->
                controller.suspenseDataTransfer(DataTransferMockObjectUtil.PROVIDER_PID, TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE))
        );
    }

    @Test
    @DisplayName("Suspend TransferProcess - error service")
    public void suspendDataTransfer_errorService() {
        when(dataTransferService.suspendDataTransfer(any(TransferSuspensionMessage.class), isNull(), any(String.class)))
                .thenThrow(TransferProcessNotFoundException.class);
        assertThrows(TransferProcessNotFoundException.class,
                () -> controller.suspenseDataTransfer(DataTransferMockObjectUtil.PROVIDER_PID,
                        TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE)));
    }
}
