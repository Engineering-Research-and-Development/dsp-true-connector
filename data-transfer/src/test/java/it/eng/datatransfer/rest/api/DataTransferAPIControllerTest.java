package it.eng.datatransfer.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.DataTransferRequest;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.GenericFilterBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataTransferAPIControllerTest {

    @Mock
    private HttpServletResponse response;

    @Mock
    private DataTransferAPIService apiService;

    @Mock
    private GenericFilterBuilder filterBuilder;

    @InjectMocks
    private DataTransferAPIController controller;

    private DataTransferRequest dataTransferRequest = new DataTransferRequest(DataTranferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED.getId(),
            DataTransferFormat.HTTP_PULL.name(),
            null);

    @Test
    @DisplayName("Find transfer process with generic filters")
    public void getTransfersProcess_genericFilters() {
        // Create mock request
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", TransferState.REQUESTED.name());
        request.setParameter("role", IConstants.ROLE_PROVIDER);

        // Create expected filter map
        Map<String, Object> expectedFilters = Map.of(
                "state", TransferState.REQUESTED.name(),
                "role", IConstants.ROLE_PROVIDER
        );

        when(filterBuilder.buildFromRequest(any(HttpServletRequest.class)))
                .thenReturn(expectedFilters);
        when(apiService.findDataTransfers(any(Map.class)))
                .thenReturn(Arrays.asList(TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER)));

        ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.getTransfersProcess(null, request);

        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertFalse(response.getBody().getData().isEmpty());

        verify(filterBuilder).buildFromRequest(request);
        verify(apiService).findDataTransfers(expectedFilters);
    }

    @Test
    @DisplayName("Find transfer process by id with path variable")
    public void getTransfersProcess_byId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String transferProcessId = "test-id";

        Map<String, Object> emptyFilters = new HashMap<>();
        Map<String, Object> expectedFilters = Map.of("id", transferProcessId);

        when(filterBuilder.buildFromRequest(any(HttpServletRequest.class)))
                .thenReturn(emptyFilters);
        when(apiService.findDataTransfers(any(Map.class)))
                .thenReturn(Arrays.asList(TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER)));

        ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.getTransfersProcess(transferProcessId, request);

        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertFalse(response.getBody().getData().isEmpty());

        verify(filterBuilder).buildFromRequest(request);
        verify(apiService).findDataTransfers(expectedFilters);
    }

    @Test
    @DisplayName("Find transfer process with multiple filters")
    public void getTransfersProcess_multipleFilters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", TransferState.STARTED.name());
        request.setParameter("role", IConstants.ROLE_CONSUMER);
        request.setParameter("datasetId", DataTranferMockObjectUtil.DATASET_ID);
        request.setParameter("isDownloaded", "true");

        Map<String, Object> expectedFilters = Map.of(
                "state", TransferState.STARTED.name(),
                "role", IConstants.ROLE_CONSUMER,
                "datasetId", DataTranferMockObjectUtil.DATASET_ID,
                "isDownloaded", true
        );

        when(filterBuilder.buildFromRequest(any(HttpServletRequest.class)))
                .thenReturn(expectedFilters);
        when(apiService.findDataTransfers(any(Map.class)))
                .thenReturn(Arrays.asList(TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED)));

        ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.getTransfersProcess(null, request);

        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());

        verify(filterBuilder).buildFromRequest(request);
        verify(apiService).findDataTransfers(expectedFilters);
    }

    @Test
    @DisplayName("Find transfer process with no filters returns all")
    public void getTransfersProcess_noFilters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Map<String, Object> emptyFilters = new HashMap<>();

        when(filterBuilder.buildFromRequest(any(HttpServletRequest.class)))
                .thenReturn(emptyFilters);
        when(apiService.findDataTransfers(any(Map.class)))
                .thenReturn(Arrays.asList(
                        TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER),
                        TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED)
                ));

        ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.getTransfersProcess(null, request);

        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertEquals(2, response.getBody().getData().size());

        verify(filterBuilder).buildFromRequest(request);
        verify(apiService).findDataTransfers(emptyFilters);
    }

    @Test
    @DisplayName("Find transfer process with datetime filters")
    public void getTransfersProcess_datetimeFilters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("created", "2024-01-01T10:00:00Z");
        request.setParameter("state", TransferState.COMPLETED.name());

        Map<String, Object> expectedFilters = Map.of(
                "created", "2024-01-01T10:00:00Z", // GenericFilterBuilder will convert this to Instant
                "state", TransferState.COMPLETED.name()
        );

        when(filterBuilder.buildFromRequest(any(HttpServletRequest.class)))
                .thenReturn(expectedFilters);
        when(apiService.findDataTransfers(any(Map.class)))
                .thenReturn(Arrays.asList(TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED)));

        ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.getTransfersProcess(null, request);

        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());

        verify(filterBuilder).buildFromRequest(request);
        verify(apiService).findDataTransfers(expectedFilters);
    }

    @Test
    @DisplayName("Find transfer process with path variable overrides query param id")
    public void getTransfersProcess_pathVariableOverridesQueryParam() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("id", "query-param-id"); // This should be ignored
        String pathVariableId = "path-variable-id";

        // Use mutable HashMap instead of immutable Map.of() since controller modifies the map
        Map<String, Object> initialFilters = new HashMap<>();
        initialFilters.put("id", "query-param-id");
        
        Map<String, Object> expectedFilters = Map.of("id", pathVariableId); // Path variable wins

        when(filterBuilder.buildFromRequest(any(HttpServletRequest.class)))
                .thenReturn(initialFilters);
        when(apiService.findDataTransfers(any(Map.class)))
                .thenReturn(Arrays.asList(TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER)));

        ResponseEntity<GenericApiResponse<Collection<JsonNode>>> response = controller.getTransfersProcess(pathVariableId, request);

        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());

        verify(filterBuilder).buildFromRequest(request);
        verify(apiService).findDataTransfers(expectedFilters);
    }

    @Test
    @DisplayName("Request transfer process success")
    public void requestTransfer_success() {
        Map<String, Object> map = new HashMap<>();
        map.put("transferProcessId", DataTranferMockObjectUtil.FORWARD_TO);
        map.put(DSpaceConstants.FORMAT, DataTransferFormat.HTTP_PULL.name());
        map.put(DSpaceConstants.DATA_ADDRESS, TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.DATA_ADDRESS));

        when(apiService.requestTransfer(any(DataTransferRequest.class)))
                .thenReturn(TransferSerializer.serializeProtocolJsonNode(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.requestTransfer(dataTransferRequest);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).requestTransfer(dataTransferRequest);
    }

    @Test
    @DisplayName("Request transfer process failed")
    public void requestTransfer_failed() {
        Map<String, Object> map = new HashMap<>();
        map.put("transferProcessId", DataTranferMockObjectUtil.FORWARD_TO);
        map.put(DSpaceConstants.FORMAT, DataTransferFormat.HTTP_PULL.name());
        map.put(DSpaceConstants.DATA_ADDRESS, TransferSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.DATA_ADDRESS));

        doThrow(new DataTransferAPIException("Something not correct - tests"))
                .when(apiService).requestTransfer(any(DataTransferRequest.class));

        assertThrows(DataTransferAPIException.class, () -> controller.requestTransfer(dataTransferRequest));
    }

    @Test
    @DisplayName("Start transfer process success")
    public void startTransfer_success() throws UnsupportedEncodingException {

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
    }

    @Test
    @DisplayName("Start transfer process failed")
    public void startTransfer_failed() throws UnsupportedEncodingException {

        doThrow(new DataTransferAPIException("Something not correct - tests"))
                .when(apiService).startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        assertThrows(DataTransferAPIException.class, () -> controller.startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
    }

    @Test
    @DisplayName("Complete transfer process success")
    public void completeTransfer_success() {

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.completeTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).completeTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
    }

    @Test
    @DisplayName("Complete transfer process failed")
    public void completeTransfer_failed() {

        doThrow(new DataTransferAPIException("Something not correct - tests"))
                .when(apiService).completeTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        assertThrows(DataTransferAPIException.class, () -> controller.completeTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
    }

    @Test
    @DisplayName("Suspend transfer process success")
    public void suspendTransfer_success() {

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.suspendTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).suspendTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
    }

    @Test
    @DisplayName("Suspend transfer process failed")
    public void suspendTransfer_failed() {

        doThrow(new DataTransferAPIException("Something not correct - tests"))
                .when(apiService).suspendTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        assertThrows(DataTransferAPIException.class, () -> controller.suspendTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
    }

    @Test
    @DisplayName("Terminate transfer process success")
    public void terminateTransfer_success() {

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.terminateTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).terminateTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId());
    }

    @Test
    @DisplayName("Terminate transfer process failed")
    public void terminateTransfer_failed() {

        doThrow(new DataTransferAPIException("Something not correct - tests"))
                .when(apiService).terminateTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId());

        assertThrows(DataTransferAPIException.class, () -> controller.terminateTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_TERMINATED.getId()));
    }

    @Test
    @DisplayName("Download data - success")
    public void downloadData_success() throws IllegalStateException, IOException {
        doNothing().when(apiService).downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
        ;


        assertDoesNotThrow(() -> controller.downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

    }

    @Test
    @DisplayName("Download data - fail")
    public void downloadData_fail() throws IllegalStateException, IOException {
        doThrow(new DataTransferAPIException("message")).when(apiService).downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        assertThrows(DataTransferAPIException.class, () -> controller.downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
    }

    @Test
    @DisplayName("View data - success")
    public void viewData_success() throws IllegalStateException, IOException {
        when(apiService.viewData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn("http://presignUrl.test/viewData");

        assertDoesNotThrow(() -> controller.viewData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
    }

    @Test
    @DisplayName("View data - fail")
    public void viewData_fail() throws IllegalStateException, IOException {
        doThrow(new DataTransferAPIException("message")).when(apiService).viewData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        assertThrows(DataTransferAPIException.class, () -> controller.viewData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
    }
}