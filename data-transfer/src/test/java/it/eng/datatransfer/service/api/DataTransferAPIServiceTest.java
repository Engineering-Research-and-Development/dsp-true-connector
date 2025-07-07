package it.eng.datatransfer.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.DataTransferRequest;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.api.strategy.HttpPullTransferStrategy;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.usagecontrol.UsageControlProperties;
import it.eng.tools.util.CredentialUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.isA;

@ExtendWith(MockitoExtension.class)
class DataTransferAPIServiceTest {

    private static final String FILENAME = "file.txt";
    private static final String CONTENT_DISPOSITION = ContentDisposition.attachment().filename(FILENAME).build().toString();

    private MockHttpServletResponse mockHttpServletResponse;

    @Mock
    private UsageControlProperties usageControlProperties;
    @Mock
    private OkHttpRestClient okHttpRestClient;
    @Mock
    private DataTransferProperties properties;
    @Mock
    private GenericApiResponse<String> apiResponse;
    @Mock
    private CredentialUtils credentialUtils;
    @Mock
    private TransferProcessRepository transferProcessRepository;
    @Mock
    private S3ClientService s3ClientService;
    @Mock
    private S3Properties s3Properties;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private DataTransferStrategyFactory transferStrategyFactory;
    @Mock
    private HttpPullTransferStrategy httpPullTransferStrategy;
    @Mock
    private ArtifactTransferService artifactTransferService;

    @Captor
    private ArgumentCaptor<TransferProcess> argCaptorTransferProcess;

    @InjectMocks
    private DataTransferAPIService apiService;

    private final DataTransferRequest dataTransferRequest = new DataTransferRequest(DataTranferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED.getId(),
            DataTransferFormat.HTTP_PULL.name(),
            null);

    @Test
    @DisplayName("Find transfer process by id - ignores other filters")
    public void findDataTransfers_byId() {
        when(transferProcessRepository.findById(anyString())).thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));
        Collection<JsonNode> response = apiService.findDataTransfers("test", TransferState.REQUESTED.name(), null, "ignored", "ignored", "ignored");
        assertNotNull(response);
        assertEquals(1, response.size());

        // Verify that dynamic filter method is not called when ID is provided
        verify(transferProcessRepository, never()).findWithDynamicFilters(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Find transfer process by dataset_id only")
    public void findDataTransfers_byDatasetId() {
        when(transferProcessRepository.findWithDynamicFilters(null, null, DataTranferMockObjectUtil.DATASET_ID, null, null))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        Collection<JsonNode> response = apiService.findDataTransfers(null, null, null, DataTranferMockObjectUtil.DATASET_ID, null, null);

        assertNotNull(response);
        assertEquals(1, response.size());
        verify(transferProcessRepository).findWithDynamicFilters(null, null, DataTranferMockObjectUtil.DATASET_ID, null, null);
    }

    @Test
    @DisplayName("Find transfer process by datasetId and role")
    public void findDataTransfers_byDatasetIdAndRole() {
        when(transferProcessRepository.findWithDynamicFilters(null, IConstants.ROLE_PROVIDER, DataTranferMockObjectUtil.DATASET_ID, null, null))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        Collection<JsonNode> response = apiService.findDataTransfers(null, null, IConstants.ROLE_PROVIDER, DataTranferMockObjectUtil.DATASET_ID, null, null);

        assertNotNull(response);
        assertEquals(1, response.size());
        verify(transferProcessRepository).findWithDynamicFilters(null, IConstants.ROLE_PROVIDER, DataTranferMockObjectUtil.DATASET_ID, null, null);
    }

    @Test
    @DisplayName("Find transfer process by datasetId, state and role")
    public void findDataTransfers_byDatasetIdStateAndRole() {
        when(transferProcessRepository.findWithDynamicFilters(
                TransferState.STARTED.name(), IConstants.ROLE_PROVIDER, DataTranferMockObjectUtil.DATASET_ID, null, null))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        Collection<JsonNode> response = apiService.findDataTransfers(null, TransferState.STARTED.name(), IConstants.ROLE_PROVIDER,
                DataTranferMockObjectUtil.DATASET_ID, null, null);

        assertNotNull(response);
        assertEquals(1, response.size());
        verify(transferProcessRepository).findWithDynamicFilters(
                TransferState.STARTED.name(), IConstants.ROLE_PROVIDER, DataTranferMockObjectUtil.DATASET_ID, null, null);
    }

    @Test
    @DisplayName("Find transfer process by providerPid only")
    public void findDataTransfers_byProviderPid() {
        when(transferProcessRepository.findWithDynamicFilters(null, null, null, DataTranferMockObjectUtil.PROVIDER_PID, null))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));

        Collection<JsonNode> response = apiService.findDataTransfers(null, null, null, null, DataTranferMockObjectUtil.PROVIDER_PID, null);

        assertNotNull(response);
        assertEquals(1, response.size());
        verify(transferProcessRepository).findWithDynamicFilters(null, null, null, DataTranferMockObjectUtil.PROVIDER_PID, null);
    }

    @Test
    @DisplayName("Find transfer process by providerPid and role")
    public void findDataTransfers_byProviderPidAndRole() {
        when(transferProcessRepository.findWithDynamicFilters(null, IConstants.ROLE_PROVIDER, null, DataTranferMockObjectUtil.PROVIDER_PID, null))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));

        Collection<JsonNode> response = apiService.findDataTransfers(null, null, IConstants.ROLE_PROVIDER, null, DataTranferMockObjectUtil.PROVIDER_PID, null);

        assertNotNull(response);
        assertEquals(1, response.size());
        verify(transferProcessRepository).findWithDynamicFilters(null, IConstants.ROLE_PROVIDER, null, DataTranferMockObjectUtil.PROVIDER_PID, null);
    }

    @Test
    @DisplayName("Find transfer process by consumerPid only")
    public void findDataTransfers_byConsumerPid() {
        when(transferProcessRepository.findWithDynamicFilters(null, null, null, null, DataTranferMockObjectUtil.CONSUMER_PID))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER));

        Collection<JsonNode> response = apiService.findDataTransfers(null, null, null, null, null, DataTranferMockObjectUtil.CONSUMER_PID);

        assertNotNull(response);
        assertEquals(1, response.size());
        verify(transferProcessRepository).findWithDynamicFilters(null, null, null, null, DataTranferMockObjectUtil.CONSUMER_PID);
    }

    @Test
    @DisplayName("Find transfer process by consumerPid and state")
    public void findDataTransfers_byConsumerPidAndState() {
        when(transferProcessRepository.findWithDynamicFilters(TransferState.REQUESTED.name(), null, null, null, DataTranferMockObjectUtil.CONSUMER_PID))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER));

        Collection<JsonNode> response = apiService.findDataTransfers(null, TransferState.REQUESTED.name(), null, null, null, DataTranferMockObjectUtil.CONSUMER_PID);

        assertNotNull(response);
        assertEquals(1, response.size());
        verify(transferProcessRepository).findWithDynamicFilters(TransferState.REQUESTED.name(), null, null, null, DataTranferMockObjectUtil.CONSUMER_PID);
    }

    @Test
    @DisplayName("Find transfer process falls back to state and role when no specific filters")
    public void findDataTransfers_fallbackToStateAndRole() {
        when(transferProcessRepository.findWithDynamicFilters(TransferState.STARTED.name(), IConstants.ROLE_PROVIDER, null, null, null))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        Collection<JsonNode> response = apiService.findDataTransfers(null, TransferState.STARTED.name(), IConstants.ROLE_PROVIDER, null, null, null);

        assertNotNull(response);
        assertEquals(1, response.size());
        verify(transferProcessRepository).findWithDynamicFilters(TransferState.STARTED.name(), IConstants.ROLE_PROVIDER, null, null, null);
    }

    @Test
    @DisplayName("Find transfer process falls back to role only")
    public void findDataTransfers_fallbackToRole() {
        when(transferProcessRepository.findWithDynamicFilters(null, IConstants.ROLE_PROVIDER, null, null, null))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER, DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        Collection<JsonNode> response = apiService.findDataTransfers(null, null, IConstants.ROLE_PROVIDER, null, null, null);

        assertNotNull(response);
        assertEquals(2, response.size());
        verify(transferProcessRepository).findWithDynamicFilters(null, IConstants.ROLE_PROVIDER, null, null, null);
    }

    @Test
    @DisplayName("Find transfer process with all filters applied")
    public void findDataTransfers_allFiltersApplied() {
        when(transferProcessRepository.findWithDynamicFilters(TransferState.STARTED.name(), IConstants.ROLE_PROVIDER, DataTranferMockObjectUtil.DATASET_ID, null, null))
                .thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        Collection<JsonNode> response = apiService.findDataTransfers(null, TransferState.STARTED.name(), IConstants.ROLE_PROVIDER, DataTranferMockObjectUtil.DATASET_ID, null, null);

        assertNotNull(response);
        assertEquals(1, response.size());
        verify(transferProcessRepository).findWithDynamicFilters(TransferState.STARTED.name(), IConstants.ROLE_PROVIDER, DataTranferMockObjectUtil.DATASET_ID, null, null);
    }

    @Test
    @DisplayName("Request transfer process success")
    public void startNegotiation_success() {
        when(transferProcessRepository.findById(anyString())).thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(TransferSerializer.serializeProtocol(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));
        when(apiResponse.isSuccess()).thenReturn(true);
        when(properties.consumerCallbackAddress()).thenReturn(DataTranferMockObjectUtil.CALLBACK_ADDRESS);
        when(transferProcessRepository.save(any(TransferProcess.class))).thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER);

        apiService.requestTransfer(dataTransferRequest);

        verify(transferProcessRepository).save(argCaptorTransferProcess.capture());
        assertEquals(IConstants.ROLE_CONSUMER, argCaptorTransferProcess.getValue().getRole());
    }

    @Test
    @DisplayName("Request transfer process failed")
    public void startNegotiation_failed() {
        when(transferProcessRepository.findById(anyString())).thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(TransferSerializer.serializeProtocol(DataTranferMockObjectUtil.TRANSFER_ERROR));
        when(properties.consumerCallbackAddress()).thenReturn(DataTranferMockObjectUtil.CALLBACK_ADDRESS);

        assertThrows(DataTransferAPIException.class, () ->
                apiService.requestTransfer(dataTransferRequest));

        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Request transfer process json exception")
    public void startNegotiation_jsonException() {
        when(transferProcessRepository.findById(anyString())).thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn("not a JSON");
        when(apiResponse.isSuccess()).thenReturn(true);
        when(properties.consumerCallbackAddress()).thenReturn(DataTranferMockObjectUtil.CALLBACK_ADDRESS);

        assertThrows(DataTransferAPIException.class, () ->
                apiService.requestTransfer(dataTransferRequest));

        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Start transfer process success")
    public void startTransfer_success_requestedState() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));
        when(artifactTransferService.findArtifact(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER))
                .thenReturn(DataTranferMockObjectUtil.ARTIFACT_FILE);

        apiService.startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId());

        verify(transferProcessRepository).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Start transfer process failed - transfer process not found")
    public void startTransfer_failedNegotiationNotFound() {
        assertThrows(DataTransferAPIException.class, () -> apiService.startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @ParameterizedTest
    @DisplayName("Start transfer process failed - wrong transfer process state")
    @MethodSource("startTransfer_wrongStates")
    public void startTransfer_wrongNegotiationState(TransferProcess input) {

        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(input));
        when(artifactTransferService.findArtifact(input))
                .thenReturn(DataTranferMockObjectUtil.ARTIFACT_FILE);

        assertThrows(DataTransferAPIException.class,
                () -> apiService.startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(transferProcessRepository).findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Start transfer process failed - bad request")
    public void startTransfer_failedBadRequest() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));
        when(artifactTransferService.findArtifact(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER))
                .thenReturn(DataTranferMockObjectUtil.ARTIFACT_FILE);

        assertThrows(DataTransferAPIException.class, () -> apiService.startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId()));

        verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Complete transfer process success")
    public void completeTransfer_success_requestedState() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        apiService.completeTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        verify(transferProcessRepository).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Complete transfer process failed - transfer process not found")
    public void completeTransfer_failedNegotiationNotFound() {
        assertThrows(DataTransferAPIException.class, () -> apiService.completeTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @ParameterizedTest
    @DisplayName("Complete transfer process failed - wrong transfer process state")
    @MethodSource("completeTransfer_wrongStates")
    public void completeTransfer_wrongNegotiationState(TransferProcess input) {

        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()))
                .thenReturn(Optional.of(input));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.completeTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()));

        verify(transferProcessRepository).findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Complete transfer process failed - bad request")
    public void completeTransfer_failedBadRequest() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        assertThrows(DataTransferAPIException.class, () -> apiService.completeTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Suspend transfer process success")
    public void suspendTransfer_success_requestedState() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        apiService.suspendTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        verify(transferProcessRepository).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Suspend transfer process failed - transfer process not found")
    public void suspendTransfer_failedNegotiationNotFound() {
        assertThrows(DataTransferAPIException.class, () -> apiService.suspendTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @ParameterizedTest
    @DisplayName("Suspend transfer process failed - wrong transfer process state")
    @MethodSource("suspendTransfer_wrongStates")
    public void suspendTransfer_wrongNegotiationState(TransferProcess input) {

        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()))
                .thenReturn(Optional.of(input));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.suspendTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()));

        verify(transferProcessRepository).findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Suspend transfer process failed - bad request")
    public void suspendTransfer_failedBadRequest() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        assertThrows(DataTransferAPIException.class, () -> apiService.suspendTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Terminate transfer process success")
    public void terminateTransfer_success_requestedState() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        apiService.terminateTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        verify(transferProcessRepository).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Terminate transfer process failed - transfer process not found")
    public void terminateTransfer_failedNegotiationNotFound() {
        assertThrows(DataTransferAPIException.class, () -> apiService.terminateTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @ParameterizedTest
    @DisplayName("Terminate transfer process failed - wrong transfer process state")
    @MethodSource("terminateTransfer_wrongStates")
    public void terminateTransfer_wrongNegotiationState(TransferProcess input) {

        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()))
                .thenReturn(Optional.of(input));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.terminateTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()));

        verify(transferProcessRepository).findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Terminate transfer process failed - bad request")
    public void terminateTransfer_failedBadRequest() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        assertThrows(DataTransferAPIException.class, () -> apiService.terminateTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Download data - success")
    public void downloadData_success() {
        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null,
                "successful response");
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));

        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED);
        when(transferStrategyFactory.getStrategy(any(String.class))).thenReturn(httpPullTransferStrategy);
        doNothing().when(httpPullTransferStrategy).transfer(isA(TransferProcess.class));

        assertDoesNotThrow(() -> apiService.downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(transferStrategyFactory, times(1)).getStrategy(any(String.class));
        verify(httpPullTransferStrategy).transfer(argCaptorTransferProcess.capture());
        verify(transferProcessRepository, times(1)).save(any(TransferProcess.class));

        TransferProcess capturedProcess = argCaptorTransferProcess.getValue();
        assertEquals(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(), capturedProcess.getId());
        assertEquals(DataTransferFormat.HTTP_PULL.name(), capturedProcess.getFormat());
    }

    @Test
    @DisplayName("Download data - fail - can not store data")
    public void downloadData_transferFail() {
        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        when(transferStrategyFactory.getStrategy(any(String.class))).thenReturn(httpPullTransferStrategy);

        doThrow(DataTransferAPIException.class).when(httpPullTransferStrategy).transfer(isA(TransferProcess.class));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
    }

    @Test
    @DisplayName("Download data - fail - strategy not found")
    public void downloadData_fail_strategyNotFound() {
        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));

        when(transferStrategyFactory.getStrategy(any(String.class)))
                .thenThrow(DataTransferAPIException.class);

        assertThrows(DataTransferAPIException.class,
                () -> apiService.downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
    }

    @Test
    @DisplayName("Download data - fail - policy not valid")
    public void downloadData_fail_policyNotValid() {
        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        GenericApiResponse<String> internalResponse = GenericApiResponse.error("Policy not valid");
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

    }

    @ParameterizedTest
    @DisplayName("Download data - fail - wrong state")
    @MethodSource("download_wrongStates")
    public void downloadData_fail_wrongState(TransferProcess input) {
        when(transferProcessRepository.findById(input.getId()))
                .thenReturn(Optional.of(input));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.downloadData(input.getId()));
    }

    @Test
    @DisplayName("View data - success")
    public void viewData_success() {
        mockHttpServletResponse = new MockHttpServletResponse();
        String bucketName = "test-bucket";
        String objectKey = DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId();

        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));

        when(s3Properties.getBucketName()).thenReturn(bucketName);
        when(s3ClientService.fileExists(bucketName, objectKey)).thenReturn(true);

        when(s3ClientService.generateGetPresignedUrl(bucketName, objectKey, Duration.ofDays(7L)))
                .thenReturn("http://example.com/presigned-url");

        assertDoesNotThrow(() -> apiService.viewData(objectKey));

        verify(s3ClientService).fileExists(bucketName, objectKey);
        verify(s3ClientService).generateGetPresignedUrl(bucketName, objectKey, Duration.ofDays(7L));
        verify(applicationEventPublisher)
                .publishEvent(any(ArtifactConsumedEvent.class));
    }

    @Test
    @DisplayName("View data - fail - generate presignURL exception")
    public void viewData_fail_canNotAccessData() {
        String bucketName = "test-bucket";
        String objectKey = DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId();

        when(transferProcessRepository.findById(objectKey))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));

        when(s3Properties.getBucketName()).thenReturn(bucketName);
        when(s3ClientService.fileExists(bucketName, objectKey)).thenReturn(true);
        doThrow(RuntimeException.class).when(s3ClientService).generateGetPresignedUrl(bucketName, objectKey, Duration.ofDays(7L));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.viewData(objectKey));
    }

    @Test
    @DisplayName("View data - fail - file not found")
    public void viewData_fail_fileNotFound() {
        String bucketName = "test-bucket";
        String objectKey = DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId();

        when(transferProcessRepository.findById(objectKey))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));

        when(s3Properties.getBucketName()).thenReturn(bucketName);
        when(s3ClientService.fileExists(bucketName, objectKey)).thenReturn(false);

        assertThrows(DataTransferAPIException.class,
                () -> apiService.viewData(objectKey));

        verify(s3ClientService).fileExists(bucketName, objectKey);
    }


    @Test
    @DisplayName("View data - fail - policy not valid")
    public void viewData_fail_policyNotValid() {
        GenericApiResponse<String> internalResponse = GenericApiResponse.error("Policy not valid");

        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.viewData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId()));

        verify(s3ClientService, times(0)).fileExists(anyString(), anyString());
        verify(s3ClientService, times(0)).generateGetPresignedUrl(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("View data - fail - not downloaded")
    public void viewData_fail_notDownloaded() {
        when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.viewData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(s3ClientService, times(0)).fileExists(anyString(), anyString());
        verify(s3ClientService, times(0)).generateGetPresignedUrl(anyString(), anyString(), any(Duration.class));
    }

    private static Stream<Arguments> startTransfer_wrongStates() {
        return Stream.of(
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED),
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED),
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_TERMINATED)
        );
    }

    private static Stream<Arguments> completeTransfer_wrongStates() {
        return Stream.of(
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED),
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_TERMINATED),
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER),
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER)
        );
    }

    private static Stream<Arguments> suspendTransfer_wrongStates() {
        return Stream.of(
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED),
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_TERMINATED),
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER),
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER)
        );
    }

    private static Stream<Arguments> terminateTransfer_wrongStates() {
        return Stream.of(
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_COMPLETED),
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_TERMINATED)
        );
    }

    private static Stream<Arguments> download_wrongStates() {
        return Stream.of(
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_TERMINATED),
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER),
                Arguments.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER)
        );
    }
}
