package it.eng.datatransfer.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.exceptions.PresignedUrlExpiredException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.DataTransferRequest;
import it.eng.datatransfer.model.TransferArtifactState;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.datatransfer.service.CancellationRegistry;
import it.eng.datatransfer.service.api.strategy.HttpPullTransferStrategy;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.exceptions.TransferCancelledException;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.usagecontrol.UsageControlProperties;
import it.eng.tools.util.CredentialUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.data.domain.*;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.isA;

@ExtendWith(MockitoExtension.class)
class DataTransferAPIServiceTest {

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
    private AuditEventPublisher publisher;
    @Mock
    private DataTransferStrategyFactory transferStrategyFactory;
    @Mock
    private HttpPullTransferStrategy httpPullTransferStrategy;
    @Mock
    private ArtifactTransferService artifactTransferService;
    @Mock
    private CancellationRegistry cancellationRegistry;
    @Mock
    private TransferArtifactStateRepository transferArtifactStateRepository;
    @Mock
    private Pageable pageable;

    @Captor
    private ArgumentCaptor<TransferProcess> argCaptorTransferProcess;
    @Captor
    private ArgumentCaptor<AuditEventType> eventTypeCaptor;
    @Captor
    private ArgumentCaptor<String> descriptionCaptor;
    @Captor
    private ArgumentCaptor<Map<String, Object>> argCaptorAuditEventDetails;

    @InjectMocks
    private DataTransferAPIService apiService;

    private final DataTransferRequest dataTransferRequest = new DataTransferRequest(DataTransferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED.getId(),
            DataTransferFormat.HTTP_PULL.name(),
            null);

    @Test
    @DisplayName("Find transfer process by id - ignores other filters")
    public void findDataTransfers_byId() {
        String id = DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId();
        when(transferProcessRepository.findById(id))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));
        TransferProcess response = apiService.findTransferProcessById(id);
        assertNotNull(response);
        assertEquals(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId(), response.getId());
        // Verify that dynamic filter method is not called when ID is provided
        verify(transferProcessRepository).findById(id);
    }

    @Test
    @DisplayName("Find transfer process with empty filters returns all")
    public void findDataTransfers_emptyFilters() {
        Map<String, Object> emptyFilters = new HashMap<>();

        when(transferProcessRepository.findWithDynamicFilters(eq(emptyFilters), eq(TransferProcess.class), eq(pageable)))
                .thenReturn(new PageImpl<>(Arrays.asList(
                        DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER,
                        DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED)
                ));

        Page<TransferProcess> response = apiService.findDataTransfers(emptyFilters, pageable);

        assertNotNull(response);
        assertEquals(2, response.getTotalElements());
        verify(transferProcessRepository).findWithDynamicFilters(anyMap(), eq(TransferProcess.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Find transfer process with null filters returns all")
    public void findDataTransfers_nullFilters() {
        when(transferProcessRepository.findWithDynamicFilters(isNull(), eq(TransferProcess.class), eq(pageable)))
                .thenReturn(new PageImpl<>(Arrays.asList(
                        DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER,
                        DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED)
                ));

        Page<TransferProcess> response = apiService.findDataTransfers(null, pageable);

        assertNotNull(response);
        assertEquals(2, response.getTotalElements());
        verify(transferProcessRepository).findWithDynamicFilters(isNull(), eq(TransferProcess.class), eq(pageable));
    }

    @ParameterizedTest
    @DisplayName("Find transfer process with different filter combinations")
    @MethodSource("filterCombinations")
    void findDataTransfers_withFilters(String testName, Map<String, Object> filters, Page<TransferProcess> expectedResults) {
        when(transferProcessRepository.findWithDynamicFilters(anyMap(), eq(TransferProcess.class), any(Pageable.class)))
                .thenReturn(expectedResults);

        Page<TransferProcess> response = apiService.findDataTransfers(filters, pageable);

        assertNotNull(response);
        assertEquals(expectedResults.getNumberOfElements(), response.getTotalElements());
        verify(transferProcessRepository).findWithDynamicFilters(filters, TransferProcess.class, pageable);
    }

    private static Stream<Arguments> filterCombinations() {
        return Stream.of(
                Arguments.of("Find by datasetId only",
                        Map.of("datasetId", DataTransferMockObjectUtil.DATASET_ID),
                        new PageImpl<>(Collections.singletonList(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED))),

                Arguments.of("Find by datasetId and role",
                        Map.of(
                                "datasetId", DataTransferMockObjectUtil.DATASET_ID,
                                "role", IConstants.ROLE_PROVIDER),
                        new PageImpl<>(Collections.singletonList(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED))),

                Arguments.of("Find by state and role",
                        Map.of(
                                "state", TransferState.STARTED.name(),
                                "role", IConstants.ROLE_CONSUMER),
                        new PageImpl<>(Collections.singletonList(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED))),

                Arguments.of("Find by multiple states",
                        Map.of(
                                "state", Arrays.asList(TransferState.STARTED.name(), TransferState.COMPLETED.name()),
                                "role", IConstants.ROLE_PROVIDER),
                        new PageImpl<>(Arrays.asList(
                                DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED,
                                DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER))),

                Arguments.of("Find by providerPid only",
                        Map.of("providerPid", DataTransferMockObjectUtil.PROVIDER_PID),
                        new PageImpl<>(Collections.singletonList(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER))),

                Arguments.of("Find by consumerPid and state",
                        Map.of(
                                "consumerPid", DataTransferMockObjectUtil.CONSUMER_PID,
                                "state", TransferState.REQUESTED.name()),
                        new PageImpl<>(Collections.singletonList(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER)))
        );
    }

    @Test
    @DisplayName("Find transfer process with multi-value filters (IN query)")
    public void findDataTransfers_multiValueFilters() {
        Map<String, Object> filters = Map.of(
                "state", Arrays.asList(TransferState.STARTED.name(), TransferState.COMPLETED.name()),
                "role", IConstants.ROLE_PROVIDER
        );
        pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp"));

        when(transferProcessRepository.findWithDynamicFilters(anyMap(), eq(TransferProcess.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Arrays.asList(
                        DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED,
                        DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER
                ), pageable, 2));

        Page<TransferProcess> response = apiService.findDataTransfers(filters, pageable);

        assertNotNull(response);
        assertEquals(2, response.getTotalElements());
        verify(transferProcessRepository).findWithDynamicFilters(filters, TransferProcess.class, pageable);
    }

    @Test
    @DisplayName("Request transfer process success")
    public void startNegotiation_success() {
        when(transferProcessRepository.findById(anyString())).thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(TransferSerializer.serializeProtocol(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));
        when(apiResponse.isSuccess()).thenReturn(true);
        when(properties.consumerCallbackAddress()).thenReturn(DataTransferMockObjectUtil.CALLBACK_ADDRESS);
        when(transferProcessRepository.save(any(TransferProcess.class))).thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER);

        apiService.requestTransfer(dataTransferRequest);

        verify(transferProcessRepository).save(argCaptorTransferProcess.capture());
        assertEquals(IConstants.ROLE_CONSUMER, argCaptorTransferProcess.getValue().getRole());

        verifyAuditEvent(AuditEventType.TRANSFER_REQUESTED, null);
    }

    @Test
    @DisplayName("Request transfer process failed")
    public void startNegotiation_failed() {
        when(transferProcessRepository.findById(anyString())).thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(TransferSerializer.serializeProtocol(DataTransferMockObjectUtil.TRANSFER_ERROR));
        when(properties.consumerCallbackAddress()).thenReturn(DataTransferMockObjectUtil.CALLBACK_ADDRESS);

        assertThrows(DataTransferAPIException.class, () ->
                apiService.requestTransfer(dataTransferRequest));

        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_REQUESTED, null);
    }

    @Test
    @DisplayName("Request transfer process json exception")
    public void startNegotiation_jsonException() {
        when(transferProcessRepository.findById(anyString())).thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn("not a JSON");
        when(apiResponse.isSuccess()).thenReturn(true);
        when(properties.consumerCallbackAddress()).thenReturn(DataTransferMockObjectUtil.CALLBACK_ADDRESS);

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
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));

        apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId());

        verify(transferProcessRepository).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_STARTED, null);
    }

    @Test
    @DisplayName("Start transfer process failed - transfer process not found")
    public void startTransfer_failedNegotiationNotFound() {
        assertThrows(DataTransferAPIException.class, () -> apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_NOT_FOUND, null);
    }

    @ParameterizedTest
    @DisplayName("Start transfer process failed - wrong transfer process state")
    @MethodSource("startTransfer_wrongStates")
    public void startTransfer_wrongNegotiationState(TransferProcess input) {

        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(input));

        assertThrows(TransferProcessInvalidStateException.class, //DataTransferAPIException.class,
                () -> apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(transferProcessRepository).findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_STATE_TRANSITION_ERROR, null);
    }

    @Test
    @DisplayName("Start transfer process failed - bad request")
    public void startTransfer_failedBadRequest() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("error");
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));

        assertThrows(DataTransferAPIException.class, () -> apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId()));

        verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_STARTED, null);
    }

    @Test
    @DisplayName("Start transfer - initial HTTP_PUSH by provider auto-triggers upload")
    public void startTransfer_initialHttpPush_autoTriggersUpload() {
        TransferProcess tpRequestedProviderPush = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .state(TransferState.REQUESTED)
                .format(DataTransferFormat.HTTP_PUSH.format())
                .build();

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(tpRequestedProviderPush.getId()))
                .thenReturn(Optional.of(tpRequestedProviderPush));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        apiService.startTransfer(tpRequestedProviderPush.getId());

        // Verify auto-trigger fired: findById is called once by startTransfer() and once
        // asynchronously by downloadData(). downloadData() exits early (state is not STARTED)
        // but only after the repository lookup, giving us a reliable second call to assert on.
        verify(transferProcessRepository, timeout(2000).atLeast(2)).findById(eq(tpRequestedProviderPush.getId()));
        verify(transferProcessRepository).save(any(TransferProcess.class));
        verifyAuditEvent(AuditEventType.TRANSFER_STARTED, null);
    }

    @Test
    @DisplayName("Complete transfer process success")
    public void completeTransfer_success_requestedState() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        apiService.completeTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        verify(transferProcessRepository).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_COMPLETED, null);
    }

    @Test
    @DisplayName("Complete transfer process failed - transfer process not found")
    public void completeTransfer_failedNegotiationNotFound() {
        assertThrows(DataTransferAPIException.class, () -> apiService.completeTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_NOT_FOUND, null);
    }

    @ParameterizedTest
    @DisplayName("Complete transfer process failed - wrong transfer process state")
    @MethodSource("completeTransfer_wrongStates")
    public void completeTransfer_wrongNegotiationState(TransferProcess input) {

        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()))
                .thenReturn(Optional.of(input));

        assertThrows(TransferProcessInvalidStateException.class, //DataTransferAPIException.class,
                () -> apiService.completeTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()));

        verify(transferProcessRepository).findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_STATE_TRANSITION_ERROR, null);
    }

    @Test
    @DisplayName("Complete transfer process failed - bad request")
    public void completeTransfer_failedBadRequest() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("error");
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        assertThrows(DataTransferAPIException.class, () -> apiService.completeTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_FAILED, null);
    }

    @Test
    @DisplayName("Suspend transfer process success")
    public void suspendTransfer_success_requestedState() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));
        when(transferArtifactStateRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.empty());

        apiService.suspendTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        verify(transferProcessRepository).save(any(TransferProcess.class));

        verify(publisher, times(2)).publishEvent(eventTypeCaptor.capture(), descriptionCaptor.capture(), argCaptorAuditEventDetails.capture());
        List<AuditEventType> capturedEvents = eventTypeCaptor.getAllValues();
        assertTrue(capturedEvents.contains(AuditEventType.TRANSFER_SUSPENDED));
        assertTrue(capturedEvents.contains(AuditEventType.TRANSFER_PAUSED));
    }

    @Test
    @DisplayName("Suspend transfer process failed - transfer process not found")
    public void suspendTransfer_failedNegotiationNotFound() {
        assertThrows(DataTransferAPIException.class, () -> apiService.suspendTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_NOT_FOUND, null);
    }

    @ParameterizedTest
    @DisplayName("Suspend transfer process failed - wrong transfer process state")
    @MethodSource("suspendTransfer_wrongStates")
    public void suspendTransfer_wrongNegotiationState(TransferProcess input) {

        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()))
                .thenReturn(Optional.of(input));

        assertThrows(TransferProcessInvalidStateException.class, //DataTransferAPIException.class,
                () -> apiService.suspendTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()));

        verify(transferProcessRepository).findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_STATE_TRANSITION_ERROR, null);
    }

    @Test
    @DisplayName("Suspend transfer process failed - bad request")
    public void suspendTransfer_failedBadRequest() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("error");
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        assertThrows(DataTransferAPIException.class, () -> apiService.suspendTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_SUSPENDED, null);
    }

    @Test
    @DisplayName("Terminate transfer process success")
    public void terminateTransfer_success_requestedState() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        apiService.terminateTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        verify(transferProcessRepository).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_TERMINATED, null);
    }

    @Test
    @DisplayName("Terminate transfer process failed - transfer process not found")
    public void terminateTransfer_failedNegotiationNotFound() {
        assertThrows(DataTransferAPIException.class, () -> apiService.terminateTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_NOT_FOUND, null);
    }

    @ParameterizedTest
    @DisplayName("Terminate transfer process failed - wrong transfer process state")
    @MethodSource("terminateTransfer_wrongStates")
    public void terminateTransfer_wrongNegotiationState(TransferProcess input) {

        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()))
                .thenReturn(Optional.of(input));

        assertThrows(TransferProcessInvalidStateException.class, //DataTransferAPIException.class,
                () -> apiService.terminateTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()));

        verify(transferProcessRepository).findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId());
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_STATE_TRANSITION_ERROR, null);
    }

    @Test
    @DisplayName("Terminate transfer process failed - bad request")
    public void terminateTransfer_failedBadRequest() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("Terminate transfer process failed");
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        assertThrows(DataTransferAPIException.class, () -> apiService.terminateTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.TRANSFER_TERMINATED, null);
    }

    @Test
    @DisplayName("Download data - success")
    public void downloadData_success() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null,
                "successful response");
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));

        // First save (isDownloadInProgress=true): return the saved object as-is (realistic DB save behaviour).
        // Second save (completion): return the fully downloaded process.
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED);
        when(transferStrategyFactory.getStrategy(any(String.class))).thenReturn(httpPullTransferStrategy);
        when(httpPullTransferStrategy.transfer(isA(TransferProcess.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(transferStrategyFactory, times(1)).getStrategy(any(String.class));
        verify(httpPullTransferStrategy).transfer(argCaptorTransferProcess.capture());
        // Two saves: once to mark isDownloadInProgress=true, once to mark isDownloaded=true on completion
        verify(transferProcessRepository, times(2)).save(argCaptorTransferProcess.capture());

        // The process passed to the strategy should have isDownloadInProgress=true
        TransferProcess processPassedToStrategy = argCaptorTransferProcess.getAllValues().get(0);
        assertEquals(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(), processPassedToStrategy.getId());
        assertTrue(processPassedToStrategy.isDownloadInProgress());
        assertEquals(DataTransferFormat.HTTP_PULL.name(), processPassedToStrategy.getFormat());
    }

    @Test
    @DisplayName("Download data - fail - can not store data")
    public void downloadData_transferFail() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        when(transferStrategyFactory.getStrategy(any(String.class))).thenReturn(httpPullTransferStrategy);
        // save returns the input as-is (realistic DB save); used for both isDownloadInProgress=true and the reset
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doThrow(DataTransferAPIException.class).when(httpPullTransferStrategy).transfer(isA(TransferProcess.class));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        // Two saves: isDownloadInProgress=true at start, then isDownloadInProgress=false on failure reset
        verify(transferProcessRepository, times(2)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Download data - fail - strategy not found")
    public void downloadData_fail_strategyNotFound() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(transferStrategyFactory.getStrategy(any(String.class)))
                .thenThrow(DataTransferAPIException.class);

        assertThrows(DataTransferAPIException.class,
                () -> apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        // Two saves: isDownloadInProgress=true at start, then isDownloadInProgress=false on strategy error
        verify(transferProcessRepository, times(2)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Download data - fail - policy not valid")
    public void downloadData_fail_policyNotValid() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        GenericApiResponse<String> internalResponse = GenericApiResponse.error("Policy not valid");
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        // save is called once for isDownloadInProgress=true, then once to reset on policy failure
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompletableFuture<Void> future = apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(DataTransferAPIException.class, ex.getCause());

        // Two saves: isDownloadInProgress=true at start, then isDownloadInProgress=false on policy failure reset
        verify(transferProcessRepository, times(2)).save(any(TransferProcess.class));
    }

    @ParameterizedTest
    @DisplayName("Download data - fail - wrong state")
    @MethodSource("download_wrongStates")
    public void downloadData_fail_wrongState(TransferProcess input) {
        when(transferProcessRepository.findById(input.getId()))
                .thenReturn(Optional.of(input));

        // Validation throws synchronously so the exception propagates directly to the caller.
        assertThrows(DataTransferAPIException.class, () -> apiService.downloadData(input.getId()));
    }

    @Test
    @DisplayName("Download data - fail - already downloaded")
    public void downloadData_fail_alreadyDownloaded() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED));

        // Validation throws synchronously so the exception propagates directly to the caller.
        DataTransferAPIException ex = assertThrows(DataTransferAPIException.class,
                () -> apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId()));
        assertTrue(ex.getMessage().contains("has already been downloaded"));
    }

    @Test
    @DisplayName("Download data - fail - download already in progress (isDownloadInProgress=true)")
    public void downloadData_fail_concurrentDownload() {
        // Simulate a transfer process that already has isDownloadInProgress=true in the DB
        // (set by a previous request that is still running or by the startup recovery scenario).
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_DOWNLOADING.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_DOWNLOADING));

        // Should throw synchronously since the guard check happens before the async work.
        DataTransferAPIException ex = assertThrows(DataTransferAPIException.class,
                () -> apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_DOWNLOADING.getId()));
        assertTrue(ex.getMessage().contains("already in progress"));

        // No save should be called — the guard aborted before any DB write.
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Reset stale isDownloadInProgress flags on startup")
    public void resetStaleDownloadingFlags_resetsStaleRecords() {
        List<TransferProcess> staleProcesses = List.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_DOWNLOADING);
        when(transferProcessRepository.findAllByIsDownloadInProgressTrue()).thenReturn(staleProcesses);
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // PostConstruct is not called by @InjectMocks, so invoke it directly.
        apiService.resetStaleDownloadingFlags();

        verify(transferProcessRepository).findAllByIsDownloadInProgressTrue();
        verify(transferProcessRepository).save(argCaptorTransferProcess.capture());
        TransferProcess savedProcess = argCaptorTransferProcess.getValue();
        assertFalse(savedProcess.isDownloadInProgress());
    }

    @Test
    @DisplayName("Reset stale isDownloadInProgress flags on startup - no stale records")
    public void resetStaleDownloadingFlags_noStaleRecords() {
        when(transferProcessRepository.findAllByIsDownloadInProgressTrue()).thenReturn(List.of());

        apiService.resetStaleDownloadingFlags();

        verify(transferProcessRepository).findAllByIsDownloadInProgressTrue();
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("View data - success")
    public void viewData_success() {
        String bucketName = "test-bucket";
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();

        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));

        when(s3Properties.getBucketName()).thenReturn(bucketName);
        when(s3ClientService.fileExists(bucketName, objectKey)).thenReturn(true);

        when(s3ClientService.generateGetPresignedUrl(bucketName, objectKey, Duration.ofDays(7L)))
                .thenReturn("https://example.com/presigned-url");

        assertDoesNotThrow(() -> apiService.viewData(objectKey));

        verify(s3ClientService).fileExists(bucketName, objectKey);
        verify(s3ClientService).generateGetPresignedUrl(bucketName, objectKey, Duration.ofDays(7L));
        verify(publisher).publishEvent(any(ArtifactConsumedEvent.class));
    }

    @Test
    @DisplayName("View data - fail - generate presignURL exception")
    public void viewData_fail_canNotAccessData() {
        String bucketName = "test-bucket";
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();

        when(transferProcessRepository.findById(objectKey))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
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
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();

        when(transferProcessRepository.findById(objectKey))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
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

        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.viewData(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()));

        verify(s3ClientService, times(0)).fileExists(anyString(), anyString());
        verify(s3ClientService, times(0)).generateGetPresignedUrl(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("View data - fail - not downloaded")
    public void viewData_fail_notDownloaded() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED_NOT_DOWNLOADED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED_NOT_DOWNLOADED));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.viewData(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED_NOT_DOWNLOADED.getId()));

        verify(s3ClientService, times(0)).fileExists(anyString(), anyString());
        verify(s3ClientService, times(0)).generateGetPresignedUrl(anyString(), anyString(), any(Duration.class));
    }

    private static Stream<Arguments> startTransfer_wrongStates() {
        return Stream.of(
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED),
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED),
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_TERMINATED)
        );
    }

    private static Stream<Arguments> completeTransfer_wrongStates() {
        return Stream.of(
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED),
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_TERMINATED),
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER),
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER)
        );
    }

    private static Stream<Arguments> suspendTransfer_wrongStates() {
        return Stream.of(
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED),
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_TERMINATED),
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER),
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER)
        );
    }

    private static Stream<Arguments> terminateTransfer_wrongStates() {
        return Stream.of(
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED),
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_TERMINATED)
        );
    }

    private static Stream<Arguments> download_wrongStates() {
        return Stream.of(
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_TERMINATED),
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER),
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER)
        );
    }

    private void verifyAuditEvent(AuditEventType eventType, String description) {
        verify(publisher).publishEvent(eventTypeCaptor.capture(), descriptionCaptor.capture(), argCaptorAuditEventDetails.capture());
        assertEquals(eventType, eventTypeCaptor.getValue());
        if (StringUtils.isNotBlank(description)) {
            assertEquals(description, descriptionCaptor.getValue());
        }
        assertNotNull(argCaptorAuditEventDetails.getValue());
    }

    private static Stream<Arguments> tck_supportedStates() {
        return Stream.of(
                Arguments.of("STARTED", "startTransfer"),
                Arguments.of("COMPLETED", "completeTransfer"),
                Arguments.of("SUSPENDED", "suspendTransfer"),
                Arguments.of("TERMINATED", "terminateTransfer")
        );
    }

    @Test
    @DisplayName("suspendTransfer signals CancellationRegistry and records suspendedBy after successful peer response")
    void suspendTransferSignalsCancellationRegistryAndRecordsSuspendedBy() {
        TransferProcess tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        when(transferProcessRepository.findById(tp.getId())).thenReturn(Optional.of(tp));
        when(apiResponse.isSuccess()).thenReturn(true);
        when(okHttpRestClient.sendRequestProtocol(anyString(), any(), any())).thenReturn(apiResponse);
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transferArtifactStateRepository.findById(tp.getId())).thenReturn(Optional.empty());
        when(transferArtifactStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        apiService.suspendTransfer(tp.getId());

        verify(cancellationRegistry).signal(tp.getId());
        verify(transferArtifactStateRepository).save(
                argThat(s -> tp.getRole().equals(s.getSuspendedBy())));
    }

    @Test
    @DisplayName("startTransfer rejects resume when suspendedBy does not match local role")
    void startTransfer_resumeRejected_wrongRole() {
        TransferProcess suspendedTp = TransferProcess.Builder.newInstance()
                .id(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER.getId())
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_CONSUMER)
                .state(TransferState.SUSPENDED)
                .format(DataTransferFormat.HTTP_PULL.format())
                .build();

        when(transferProcessRepository.findById(suspendedTp.getId())).thenReturn(Optional.of(suspendedTp));

        TransferArtifactState artifactState = TransferArtifactState.Builder.newInstance()
                .id(suspendedTp.getId())
                .suspendedBy(IConstants.ROLE_PROVIDER)
                .build();
        when(transferArtifactStateRepository.findById(suspendedTp.getId()))
                .thenReturn(Optional.of(artifactState));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.startTransfer(suspendedTp.getId()));
    }

    @Test
    @DisplayName("startTransfer on SUSPENDED process emits TRANSFER_RESUMED and triggers download when consumer resumes pull")
    void startTransfer_onSuspended_emitsResumedAudit() {
        TransferProcess suspendedTp = TransferProcess.Builder.newInstance()
                .id(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER.getId())
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_CONSUMER)
                .state(TransferState.SUSPENDED)
                .format(DataTransferFormat.HTTP_PULL.format())
                .build();

        when(transferProcessRepository.findById(suspendedTp.getId())).thenReturn(Optional.of(suspendedTp));

        TransferArtifactState artifactState = TransferArtifactState.Builder.newInstance()
                .id(suspendedTp.getId())
                .suspendedBy(IConstants.ROLE_CONSUMER)
                .build();
        when(transferArtifactStateRepository.findById(suspendedTp.getId()))
                .thenReturn(Optional.of(artifactState));

        when(apiResponse.isSuccess()).thenReturn(true);
        when(okHttpRestClient.sendRequestProtocol(anyString(), any(), any())).thenReturn(apiResponse);
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> apiService.startTransfer(suspendedTp.getId()));

        verify(publisher).publishEvent(eq(AuditEventType.TRANSFER_RESUMED), anyString(), any());
    }

    @Test
    @DisplayName("downloadData handles TransferCancelledException by keeping checkpoint and resetting in-progress flag")
    void downloadDataHandlesCancelledException() throws Exception {
        TransferProcess tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        when(transferProcessRepository.findById(tp.getId())).thenReturn(Optional.of(tp));
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(transferStrategyFactory.getStrategy(any()))
                .thenReturn(tProcess -> CompletableFuture.failedFuture(
                        new java.util.concurrent.CompletionException(
                                new TransferCancelledException(tp.getId()))));

        assertDoesNotThrow(() -> apiService.downloadData(tp.getId()).get());

        verify(transferProcessRepository, atLeastOnce())
                .save(argThat(saved -> !saved.isDownloadInProgress()));
        verify(cancellationRegistry).deregister(tp.getId());
        verify(publisher).publishEvent(eq(AuditEventType.TRANSFER_PAUSED), anyString(), any());
        verify(publisher, never()).publishEvent(eq(AuditEventType.TRANSFER_FAILED), anyString(), any());
        verify(okHttpRestClient, never()).sendRequestProtocol(anyString(), any(), any());
    }

    @Test
    @DisplayName("downloadData handles PresignedUrlExpiredException by sending 409 termination and emitting TRANSFER_URL_EXPIRED")
    void downloadDataHandlesPresignedUrlExpiredException() throws Exception {
        TransferProcess tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        when(transferProcessRepository.findById(tp.getId())).thenReturn(Optional.of(tp));
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(transferStrategyFactory.getStrategy(any()))
                .thenReturn(tProcess -> CompletableFuture.failedFuture(
                        new java.util.concurrent.CompletionException(
                                new PresignedUrlExpiredException(tp.getId()))));

        when(apiResponse.isSuccess()).thenReturn(true);
        when(okHttpRestClient.sendRequestProtocol(anyString(), any(), any())).thenReturn(apiResponse);

        assertDoesNotThrow(() -> apiService.downloadData(tp.getId()).get());

        verify(publisher).publishEvent(eq(AuditEventType.TRANSFER_URL_EXPIRED), anyString(), any());
        verify(okHttpRestClient, atLeastOnce()).sendRequestProtocol(anyString(), any(), any());
        verify(cancellationRegistry).deregister(tp.getId());
        verify(transferProcessRepository, atLeastOnce())
                .save(argThat(saved -> !saved.isDownloadInProgress()));
    }

    // -----------------------------------------------------------------------
    // Race-condition tests: async upload completes while transfer is suspended
    // -----------------------------------------------------------------------

    /**
     * HTTP-PULL / async-upload race — Fix A (whenComplete OLE prevention).
     *
     * <p>When an async S3 upload finishes AFTER {@code suspendTransfer()} has
     * already advanced the MongoDB {@code @Version}, the old code attempted to
     * save a stale entity and triggered an {@code OptimisticLockingFailureException}.
     * The fix re-reads the entity inside {@code whenComplete} so the save always
     * uses the current version. This test verifies that the re-read entity
     * (SUSPENDED state) is what gets saved, not the stale STARTED entity.</p>
     */
    @Test
    @DisplayName("downloadData HTTP-PULL async race: whenComplete re-reads entity to prevent OLE from concurrent suspend")
    void downloadData_httpPull_asyncRace_whenCompleteReadsEntityFreshly_preventsOle() throws Exception {
        TransferProcess startedPull = TransferProcess.Builder.newInstance()
                .id(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId())
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_CONSUMER)
                .format(DataTransferFormat.HTTP_PULL.format())
                .state(TransferState.STARTED)
                .isDownloaded(false)
                .isDownloadInProgress(false)
                .build();

        // Concurrent suspend ran while the async upload was in-flight: DB now
        // holds SUSPENDED state at a higher @Version.
        TransferProcess suspendedPull = TransferProcess.Builder.newInstance()
                .id(startedPull.getId())
                .consumerPid(startedPull.getConsumerPid())
                .providerPid(startedPull.getProviderPid())
                .dataAddress(startedPull.getDataAddress())
                .agreementId(startedPull.getAgreementId())
                .callbackAddress(startedPull.getCallbackAddress())
                .role(startedPull.getRole())
                .format(startedPull.getFormat())
                .state(TransferState.SUSPENDED)
                .isDownloaded(false)
                .isDownloadInProgress(true)
                .build();

        // First call: downloadData() initial read → STARTED.
        // Second+ calls: whenComplete re-read (and completeTransfer attempt) → SUSPENDED.
        when(transferProcessRepository.findById(startedPull.getId()))
                .thenReturn(Optional.of(startedPull))
                .thenReturn(Optional.of(suspendedPull));
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transferStrategyFactory.getStrategy(any()))
                .thenReturn(tProcess -> CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> apiService.downloadData(startedPull.getId()).get());

        // whenComplete must have triggered a fresh findById before saving.
        verify(transferProcessRepository, atLeast(2)).findById(startedPull.getId());

        // The completion save must carry the fresh entity's state (SUSPENDED),
        // not the stale STARTED state — and must mark isDownloaded=true.
        verify(transferProcessRepository, times(2)).save(argCaptorTransferProcess.capture());
        TransferProcess savedOnCompletion = argCaptorTransferProcess.getAllValues().get(1);
        assertTrue(savedOnCompletion.isDownloaded(), "isDownloaded must be true after upload completes");
        assertFalse(savedOnCompletion.isDownloadInProgress(), "isDownloadInProgress must be cleared");
        assertEquals(TransferState.SUSPENDED, savedOnCompletion.getState(),
                "state must come from the fresh re-read (SUSPENDED), not the stale entity (STARTED)");
    }

    /**
     * HTTP-PUSH / async-upload race — Fix A (whenComplete OLE prevention).
     *
     * <p>Same OLE-prevention guarantee as the PULL variant but exercised on the
     * provider side with {@code HTTP_PUSH} format.</p>
     */
    @Test
    @DisplayName("downloadData HTTP-PUSH async race: whenComplete re-reads entity to prevent OLE from concurrent suspend")
    void downloadData_httpPush_asyncRace_whenCompleteReadsEntityFreshly_preventsOle() throws Exception {
        TransferProcess startedPush = TransferProcess.Builder.newInstance()
                .id(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId())
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .format(DataTransferFormat.HTTP_PUSH.format())
                .state(TransferState.STARTED)
                .isDownloaded(false)
                .isDownloadInProgress(false)
                .build();

        TransferProcess suspendedPush = TransferProcess.Builder.newInstance()
                .id(startedPush.getId())
                .consumerPid(startedPush.getConsumerPid())
                .providerPid(startedPush.getProviderPid())
                .dataAddress(startedPush.getDataAddress())
                .agreementId(startedPush.getAgreementId())
                .callbackAddress(startedPush.getCallbackAddress())
                .role(startedPush.getRole())
                .format(startedPush.getFormat())
                .state(TransferState.SUSPENDED)
                .isDownloaded(false)
                .isDownloadInProgress(true)
                .build();

        when(transferProcessRepository.findById(startedPush.getId()))
                .thenReturn(Optional.of(startedPush))
                .thenReturn(Optional.of(suspendedPush));
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transferStrategyFactory.getStrategy(any()))
                .thenReturn(tProcess -> CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> apiService.downloadData(startedPush.getId()).get());

        verify(transferProcessRepository, atLeast(2)).findById(startedPush.getId());

        verify(transferProcessRepository, times(2)).save(argCaptorTransferProcess.capture());
        TransferProcess savedOnCompletion = argCaptorTransferProcess.getAllValues().get(1);
        assertTrue(savedOnCompletion.isDownloaded(), "isDownloaded must be true after upload completes");
        assertFalse(savedOnCompletion.isDownloadInProgress(), "isDownloadInProgress must be cleared");
        assertEquals(TransferState.SUSPENDED, savedOnCompletion.getState(),
                "state must come from the fresh re-read (SUSPENDED), not the stale entity (STARTED)");
    }

    /**
     * HTTP-PULL resume after async race — Fix B (Case A shortcut).
     *
     * <p>When the consumer resumes a SUSPENDED transfer that already has
     * {@code isDownloaded=true} (upload finished during the suspension window),
     * {@code startTransfer()} must call {@code completeTransfer()} directly
     * rather than triggering a redundant re-download.</p>
     */
    @Test
    @DisplayName("startTransfer Case A HTTP-PULL async: isDownloaded=true skips re-download and completes transfer directly")
    void startTransfer_caseA_httpPull_asyncScenario_alreadyDownloaded_completesDirectly() {
        // PULL consumer, SUSPENDED, upload already finished during suspension
        TransferProcess suspendedDownloadedPull = TransferProcess.Builder.newInstance()
                .id(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER.getId())
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_CONSUMER)
                .format(DataTransferFormat.HTTP_PULL.format())
                .state(TransferState.SUSPENDED)
                .isDownloaded(true)
                .build();

        // After startTransfer() saves STARTED, completeTransfer() reads this entity.
        TransferProcess startedPull = suspendedDownloadedPull.copyWithNewTransferState(TransferState.STARTED);

        // First findById: startTransfer() reads SUSPENDED+downloaded.
        // Second findById: completeTransfer() (via runAsync) reads the STARTED entity.
        when(transferProcessRepository.findById(suspendedDownloadedPull.getId()))
                .thenReturn(Optional.of(suspendedDownloadedPull))
                .thenReturn(Optional.of(startedPull));
        when(transferArtifactStateRepository.findById(suspendedDownloadedPull.getId()))
                .thenReturn(Optional.empty());
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(apiResponse.isSuccess()).thenReturn(true);
        when(okHttpRestClient.sendRequestProtocol(anyString(), any(), any())).thenReturn(apiResponse);

        assertDoesNotThrow(() -> apiService.startTransfer(suspendedDownloadedPull.getId()));

        // Two protocol messages expected: one for the start handshake, one for completeTransfer().
        verify(okHttpRestClient, timeout(2000).times(2)).sendRequestProtocol(anyString(), any(), any());
        // No re-download: strategy must never be invoked.
        verify(transferStrategyFactory, never()).getStrategy(anyString());
    }

    /**
     * HTTP-PUSH resume after async race — Fix B (Case A shortcut).
     *
     * <p>Same as the PULL variant but for the provider side: when the provider
     * resumes a SUSPENDED push transfer that has {@code isDownloaded=true},
     * {@code completeTransfer()} must fire instead of a redundant re-upload.</p>
     */
    @Test
    @DisplayName("startTransfer Case A HTTP-PUSH async: isDownloaded=true skips re-upload and completes transfer directly")
    void startTransfer_caseA_httpPush_asyncScenario_alreadyDownloaded_completesDirectly() {
        TransferProcess suspendedDownloadedPush = TransferProcess.Builder.newInstance()
                .id(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER.getId())
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .format(DataTransferFormat.HTTP_PUSH.format())
                .state(TransferState.SUSPENDED)
                .isDownloaded(true)
                .build();

        TransferProcess startedPush = suspendedDownloadedPush.copyWithNewTransferState(TransferState.STARTED);

        when(transferProcessRepository.findById(suspendedDownloadedPush.getId()))
                .thenReturn(Optional.of(suspendedDownloadedPush))
                .thenReturn(Optional.of(startedPush));
        when(transferArtifactStateRepository.findById(suspendedDownloadedPush.getId()))
                .thenReturn(Optional.empty());
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(apiResponse.isSuccess()).thenReturn(true);
        when(okHttpRestClient.sendRequestProtocol(anyString(), any(), any())).thenReturn(apiResponse);

        assertDoesNotThrow(() -> apiService.startTransfer(suspendedDownloadedPush.getId()));

        verify(okHttpRestClient, timeout(2000).times(2)).sendRequestProtocol(anyString(), any(), any());
        verify(transferStrategyFactory, never()).getStrategy(anyString());
    }

    /**
     * HTTP-PULL normal (sync) resume — regression guard.
     *
     * <p>When a PULL consumer resumes a transfer that has NOT yet downloaded
     * ({@code isDownloaded=false}), the standard {@code downloadData()} path
     * must be triggered — not the Case-A shortcut that calls
     * {@code completeTransfer()} directly.</p>
     */
    @Test
    @DisplayName("startTransfer Case A HTTP-PULL sync: isDownloaded=false triggers normal downloadData path")
    void startTransfer_caseA_httpPull_syncScenario_notDownloaded_triggersDownload() {
        TransferProcess suspendedPull = TransferProcess.Builder.newInstance()
                .id(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER.getId())
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_CONSUMER)
                .format(DataTransferFormat.HTTP_PULL.format())
                .state(TransferState.SUSPENDED)
                .isDownloaded(false)
                .build();

        TransferArtifactState artifactState = TransferArtifactState.Builder.newInstance()
                .id(suspendedPull.getId())
                .suspendedBy(IConstants.ROLE_CONSUMER)
                .build();

        when(transferProcessRepository.findById(suspendedPull.getId())).thenReturn(Optional.of(suspendedPull));
        when(transferArtifactStateRepository.findById(suspendedPull.getId()))
                .thenReturn(Optional.of(artifactState));
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(apiResponse.isSuccess()).thenReturn(true);
        when(okHttpRestClient.sendRequestProtocol(anyString(), any(), any())).thenReturn(apiResponse);
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");

        assertDoesNotThrow(() -> apiService.startTransfer(suspendedPull.getId()));

        // downloadData() is triggered asynchronously: it calls findById again before invoking the strategy.
        // Wait for that second call as proof that downloadData() fired (not completeTransfer()).
        verify(transferProcessRepository, timeout(2000).atLeast(2)).findById(suspendedPull.getId());
        // Only one protocol message (the start handshake): completeTransfer() was NOT called directly.
        verify(okHttpRestClient, times(1)).sendRequestProtocol(anyString(), any(), any());
    }

    /**
     * HTTP-PUSH normal (sync) resume — regression guard.
     *
     * <p>When a PUSH provider resumes a transfer that has NOT yet uploaded
     * ({@code isDownloaded=false}), the standard {@code downloadData()} path
     * must be triggered — not the Case-A shortcut.</p>
     */
    @Test
    @DisplayName("startTransfer Case A HTTP-PUSH sync: isDownloaded=false triggers normal upload path")
    void startTransfer_caseA_httpPush_syncScenario_notDownloaded_triggersUpload() {
        TransferProcess suspendedPush = TransferProcess.Builder.newInstance()
                .id(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER.getId())
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .format(DataTransferFormat.HTTP_PUSH.format())
                .state(TransferState.SUSPENDED)
                .isDownloaded(false)
                .build();

        when(transferProcessRepository.findById(suspendedPush.getId())).thenReturn(Optional.of(suspendedPush));
        when(transferArtifactStateRepository.findById(suspendedPush.getId())).thenReturn(Optional.empty());
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(apiResponse.isSuccess()).thenReturn(true);
        when(okHttpRestClient.sendRequestProtocol(anyString(), any(), any())).thenReturn(apiResponse);
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");

        assertDoesNotThrow(() -> apiService.startTransfer(suspendedPush.getId()));

        verify(transferProcessRepository, timeout(2000).atLeast(2)).findById(suspendedPush.getId());
        verify(okHttpRestClient, times(1)).sendRequestProtocol(anyString(), any(), any());
    }
}
