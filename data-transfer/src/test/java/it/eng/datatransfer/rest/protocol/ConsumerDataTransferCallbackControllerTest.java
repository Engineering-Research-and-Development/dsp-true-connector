package it.eng.datatransfer.rest.protocol;

import it.eng.datatransfer.exceptions.TransferProcessNotFoundException;
import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferSuspensionMessage;
import it.eng.datatransfer.model.TransferTerminationMessage;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.DataTransferService;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.model.Tenant;
import it.eng.tools.service.TenantContextHolder;
import it.eng.tools.service.TenantService;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
public class ConsumerDataTransferCallbackControllerTest {

    private static final String TENANT_ID = DataTransferMockObjectUtil.TENANT_ID;

    @Mock
    private DataTransferService dataTransferService;
    @Mock
    private TenantService tenantService;

    @InjectMocks
    private ConsumerDataTransferCallbackController controller;

    @BeforeEach
    public void setUp() {
        Tenant tenant = Tenant.Builder.newInstance()
                .id(TENANT_ID).name("Engineering").participantId("c1")
                .enabled(true).build();
        when(tenantService.findEnabledTenantById(TENANT_ID)).thenReturn(tenant);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Start TransferProcess")
    public void startDataTransfer() {
        when(dataTransferService.startDataTransfer(any(TransferStartMessage.class), any(String.class), isNull()))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED);
        assertEquals(HttpStatus.OK,
                controller.startDataTransfer(TENANT_ID, DataTransferMockObjectUtil.CONSUMER_PID,
                        TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE)).getStatusCode());
    }

    @Test
    @DisplayName("Start TransferProcess - invalid request body")
    public void startDataTransfer_invalidBody() {
        assertThrows(ValidationException.class, () ->
                controller.startDataTransfer(TENANT_ID, DataTransferMockObjectUtil.CONSUMER_PID,
                        TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE)));
    }

    @Test
    @DisplayName("Start TransferProcess - error service")
    public void startDataTransfer_errorService() {
        when(dataTransferService.startDataTransfer(any(TransferStartMessage.class), any(String.class), isNull()))
                .thenThrow(new TransferProcessNotFoundException("TransferProcess not found test"));
        assertThrows(TransferProcessNotFoundException.class, () ->
                controller.startDataTransfer(TENANT_ID, DataTransferMockObjectUtil.CONSUMER_PID,
                        TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE)));
    }

    // complete
    @Test
    @DisplayName("Complete TransferProcess")
    public void completeDataTransfer() {
        when(dataTransferService.completeDataTransfer(any(TransferCompletionMessage.class), any(String.class), isNull()))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED);
        ResponseEntity<Void> response = controller.completeDataTransfer(TENANT_ID, DataTransferMockObjectUtil.CONSUMER_PID,
                TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Complete TransferProcess - invalid request body")
    public void completeDataTransfer_invalidBody() {
        assertThrows(ValidationException.class, () ->
                controller.completeDataTransfer(TENANT_ID, DataTransferMockObjectUtil.CONSUMER_PID,
                        TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE)));
    }

    @Test
    @DisplayName("Complete TransferProcess - error service")
    public void completeDataTransfer_errorService() {
        when(dataTransferService.completeDataTransfer(any(TransferCompletionMessage.class), any(String.class), isNull()))
                .thenThrow(TransferProcessNotFoundException.class);
        assertThrows(TransferProcessNotFoundException.class,
                () -> controller.completeDataTransfer(TENANT_ID, DataTransferMockObjectUtil.CONSUMER_PID,
                        TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE)));
    }

    // terminate transfer
    @Test
    @DisplayName("Terminate TransferProcess")
    public void terminateDataTransfer() {
        when(dataTransferService.terminateDataTransfer(any(TransferTerminationMessage.class), any(String.class), isNull()))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_TERMINATED);
        ResponseEntity<Void> response = controller.terminateDataTransfer(TENANT_ID, DataTransferMockObjectUtil.CONSUMER_PID,
                TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Terminate TransferProcess - invalid request body")
    public void terminateDataTransfer_invalidBody() {
        assertThrows(ValidationException.class, () ->
                controller.terminateDataTransfer(TENANT_ID, DataTransferMockObjectUtil.CONSUMER_PID,
                        TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE)));
    }

    @Test
    @DisplayName("Terminate TransferProcess - error service")
    public void terminateDataTransfer_errorService() {
        when(dataTransferService.terminateDataTransfer(any(TransferTerminationMessage.class), any(String.class), isNull()))
                .thenThrow(TransferProcessNotFoundException.class);
        assertThrows(TransferProcessNotFoundException.class,
                () -> controller.terminateDataTransfer(TENANT_ID, DataTransferMockObjectUtil.CONSUMER_PID,
                        TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE)));
    }

    // suspend transfer
    @Test
    @DisplayName("Suspend/pause TransferProcess")
    public void suspenseDataTransfer() {
        when(dataTransferService.suspendDataTransfer(any(TransferSuspensionMessage.class), any(String.class), isNull()))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED);
        ResponseEntity<Void> response = controller.suspenseDataTransfer(TENANT_ID, DataTransferMockObjectUtil.CONSUMER_PID,
                TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Suspend TransferProcess - invalid request body")
    public void suspenseDataTransfer_invalidBody() {
        assertThrows(ValidationException.class, () ->
                controller.suspenseDataTransfer(TENANT_ID, DataTransferMockObjectUtil.CONSUMER_PID,
                        TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE)));
    }

    @Test
    @DisplayName("Suspend TransferProcess - error service")
    public void suspendDataTransfer_errorService() {
        when(dataTransferService.suspendDataTransfer(any(TransferSuspensionMessage.class), any(String.class), isNull()))
                .thenThrow(TransferProcessNotFoundException.class);
        assertThrows(TransferProcessNotFoundException.class,
                () -> controller.suspenseDataTransfer(TENANT_ID, DataTransferMockObjectUtil.CONSUMER_PID,
                        TransferSerializer.serializeProtocolJsonNode(DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE)));
    }
}
