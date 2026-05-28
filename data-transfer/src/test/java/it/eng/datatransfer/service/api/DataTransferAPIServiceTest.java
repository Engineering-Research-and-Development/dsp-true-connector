package it.eng.datatransfer.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.datatransfer.client.DataPlaneClient;
import it.eng.datatransfer.exceptions.DataPlaneClientException;
import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.DataTransferRequest;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.model.TemporaryBucketUser;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.TenantBucketResolver;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.HttpMethod;

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
    private AuditEventPublisher publisher;
    @Mock
    private ArtifactTransferService artifactTransferService;
    @Mock
    private DataPlaneClient dataPlaneClient;
    @Mock
    private S3ClientService s3ClientService;
    @Mock
    private TenantBucketResolver tenantBucketResolver;
    @Mock
    private TemporaryBucketUserService temporaryBucketUserService;
    @Mock
    private S3Properties s3Properties;
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

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_REQUESTED, null);
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

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_REQUESTED, null);
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
        when(transferProcessRepository.save(any(TransferProcess.class))).thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED);

        apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId());

        verify(transferProcessRepository).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STARTED, null);
    }

    @Test
    @DisplayName("Start transfer process success - provider HTTP-PULL generates presigned URL via S3")
    public void startTransfer_success_providerHttpPull_generatesPresignedUrlFromS3() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL));
        when(artifactTransferService.findArtifact(any())).thenReturn(DataTransferMockObjectUtil.ARTIFACT_FILE);
        when(tenantBucketResolver.resolveBucketName(DataTransferMockObjectUtil.TENANT_ID)).thenReturn("provider-bucket");
        when(s3ClientService.generateGetPresignedUrl(eq("provider-bucket"), eq(DataTransferMockObjectUtil.DATASET_ID), any()))
                .thenReturn("https://minio.example.com/presigned/artifact");
        when(transferProcessRepository.save(any(TransferProcess.class))).thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED);

        apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId());

        verify(s3ClientService).generateGetPresignedUrl(eq("provider-bucket"), eq(DataTransferMockObjectUtil.DATASET_ID), any());
        verify(dataPlaneClient, never()).prepare(any(), any());
        verify(transferProcessRepository).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Start transfer process failed - transfer process not found")
    public void startTransfer_failedNegotiationNotFound() {
        assertThrows(DataTransferAPIException.class, () -> apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND, null);
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

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR, null);
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
        when(transferProcessRepository.save(any(TransferProcess.class))).thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED);

        assertThrows(DataTransferAPIException.class, () -> apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId()));

        verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        // save() is called twice: once to pre-save STARTED before notifying peer,
        // and once to roll back to REQUESTED when the peer returns an error.
        verify(transferProcessRepository, times(2)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STARTED, null);
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

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_COMPLETED, null);
    }

    @Test
    @DisplayName("Complete transfer process failed - transfer process not found")
    public void completeTransfer_failedNegotiationNotFound() {
        assertThrows(DataTransferAPIException.class, () -> apiService.completeTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND, null);
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

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR, null);
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

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_COMPLETED, null);
    }

    @Test
    @DisplayName("Suspend transfer process success")
    public void suspendTransfer_success_requestedState() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        apiService.suspendTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        verify(transferProcessRepository).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED, null);
    }

    @Test
    @DisplayName("Suspend transfer process failed - transfer process not found")
    public void suspendTransfer_failedNegotiationNotFound() {
        assertThrows(DataTransferAPIException.class, () -> apiService.suspendTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND, null);
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

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR, null);
    }

    @Test
    @DisplayName("Suspend transfer process - download in progress no longer blocks suspension")
    public void suspendTransfer_failedDownloadInProgress() {
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_DOWNLOADING.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_DOWNLOADING));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        apiService.suspendTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_DOWNLOADING.getId());

        verify(dataPlaneClient).suspend(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_DOWNLOADING.getId(),
                DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_DOWNLOADING.getFormat());
        verify(transferProcessRepository).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED, null);
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
        verify(transferProcessRepository, times(1)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED, null);
    }

    @Test
    @DisplayName("startTransfer rejects resume when local role is not the suspend initiator")
    public void startTransfer_rejectsResumeForNonInitiator() {
        // SUSPENDED_CONSUMER: role=PROVIDER, suspendedBy=CONSUMER → PROVIDER ≠ CONSUMER → local is not initiator → reject
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_CONSUMER.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_CONSUMER));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_CONSUMER.getId()));

        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR, null);
    }

    @Test
    @DisplayName("startTransfer allows resume of legacy SUSPENDED transfer with suspendedBy == null (no-restriction case)")
    public void startTransfer_allowsResumeForLegacySuspendedTransfer_nullSuspendedBy() {
        // SUSPENDED_LEGACY: role=PROVIDER, suspendedBy=null → legacy record, must be resumable without restriction
        TransferProcess legacy = DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_LEGACY;
        when(transferProcessRepository.findById(legacy.getId()))
                .thenReturn(Optional.of(legacy));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> apiService.startTransfer(legacy.getId()));

        verify(transferProcessRepository).save(argThat(tp -> TransferState.STARTED.equals(tp.getState())));
        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STARTED, null);
    }

    @Test
    @DisplayName("startTransfer resumes HTTP-PULL transfer: reuses stored dataAddress and skips presigned URL generation")
    public void startTransfer_resumeAllowedForInitiator_reusesStoredDataAddressNoPresignedUrl() {
        // SUSPENDED_PROVIDER_HTTP_PULL: role=PROVIDER, suspendedBy=PROVIDER → initiator match → resume allowed
        TransferProcess suspended = DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER_HTTP_PULL;
        when(transferProcessRepository.findById(suspended.getId()))
                .thenReturn(Optional.of(suspended));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        apiService.startTransfer(suspended.getId());

        // Must NOT generate a fresh presigned URL on resume — the stored dataAddress must be reused
        verify(s3ClientService, never()).generateGetPresignedUrl(any(), any(), any());

        // Saved TP must carry the stored dataAddress endpoint unchanged
        verify(transferProcessRepository).save(argThat(tp ->
                TransferState.STARTED.equals(tp.getState())
                        && tp.getDataAddress() != null
                        && DataTransferMockObjectUtil.ENDPOINT_URL.equals(tp.getDataAddress().getEndpoint())));

        // Outbound TransferStartMessage must embed the stored dataAddress endpoint
        verify(okHttpRestClient).sendRequestProtocol(
                anyString(),
                argThat(body -> body.toString().contains(DataTransferMockObjectUtil.ENDPOINT_URL)),
                anyString());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STARTED, null);
    }

    @Test
    @DisplayName("startTransfer resume rollback preserves dataFlowState=SUSPENDED and suspendedBy when peer rejects start")
    public void startTransfer_resumeRollback_preservesDataFlowStateAndSuspendedBy() {
        // Given: a SUSPENDED TP with dataFlowState="SUSPENDED" and suspendedBy=PROVIDER (local is initiator)
        TransferProcess suspended = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.SUSPENDED)
                .dataFlowState("SUSPENDED")
                .suspendedBy(IConstants.ROLE_PROVIDER)
                .build();

        when(transferProcessRepository.findById(suspended.getId()))
                .thenReturn(Optional.of(suspended));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("peer rejected");
        // Both save() calls return the TP passed to them so the rollback builder has a consistent version
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThrows(DataTransferAPIException.class, () -> apiService.startTransfer(suspended.getId()));

        // save() must be called twice: once pre-save as STARTED, once for the SUSPENDED rollback
        verify(transferProcessRepository, times(2)).save(argCaptorTransferProcess.capture());
        List<TransferProcess> saves = argCaptorTransferProcess.getAllValues();
        TransferProcess firstSave = saves.get(0);
        TransferProcess rollbackSave = saves.get(1);

        // First save must pre-save as STARTED
        assertEquals(TransferState.STARTED, firstSave.getState());

        // Rollback must restore SUSPENDED with dataFlowState and suspendedBy intact
        assertEquals(TransferState.SUSPENDED, rollbackSave.getState());
        assertEquals("SUSPENDED", rollbackSave.getDataFlowState(),
                "rollback must restore dataFlowState to SUSPENDED, not drop it to null");
        assertEquals(IConstants.ROLE_PROVIDER, rollbackSave.getSuspendedBy(),
                "rollback must preserve suspendedBy from the original SUSPENDED record");

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STARTED, null);
    }

    @Test
    public void suspendTransfer_pausesDataplaneFirst() {
        TransferProcess started = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED
                .withIsDownloadInProgress(true)
                .withDataFlowState("STARTED");

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(started.getId()))
                .thenReturn(Optional.of(started));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        apiService.suspendTransfer(started.getId());

        // Dataplane must be paused BEFORE the DSP suspension message is sent
        InOrder inOrder = inOrder(dataPlaneClient, okHttpRestClient);
        inOrder.verify(dataPlaneClient).suspend(started.getId(), started.getFormat());
        inOrder.verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));

        // Saved TP must carry the local role as the initiator and show dataflow suspended
        verify(transferProcessRepository).save(argThat(tp ->
                TransferState.SUSPENDED.equals(tp.getState())
                        && "SUSPENDED".equals(tp.getDataFlowState())
                        && !tp.isDownloadInProgress()
                        && IConstants.ROLE_PROVIDER.equals(tp.getSuspendedBy())));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED, null);
    }

    @Test
    @DisplayName("suspendTransfer resumes dataplane and restores STARTED when outbound DSP suspension fails")
    public void suspendTransfer_rollsBackLocalPauseWhenPeerSuspensionFails() {
        TransferProcess started = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED
                .withIsDownloadInProgress(true)
                .withDataFlowState("STARTED");

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("peer rejected");
        when(transferProcessRepository.findById(started.getId()))
                .thenReturn(Optional.of(started));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThrows(DataTransferAPIException.class, () -> apiService.suspendTransfer(started.getId()));

        verify(dataPlaneClient).suspend(started.getId(), started.getFormat());
        verify(dataPlaneClient).resume(started.getId(), started.getFormat());
        verify(transferProcessRepository).save(argThat(tp ->
                TransferState.STARTED.equals(tp.getState())
                        && "STARTED".equals(tp.getDataFlowState())
                        && tp.isDownloadInProgress()
                        && tp.getSuspendedBy() == null));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED, null);
    }

    @Test
    @DisplayName("suspendTransfer rollback restores original isDownloadInProgress=false when process was not downloading")
    public void suspendTransfer_rollsBackLocalPauseWhenPeerSuspensionFails_notDownloading() {
        // Process was not actively downloading when suspend was called (isDownloadInProgress=false)
        TransferProcess started = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED
                .withIsDownloadInProgress(false)
                .withDataFlowState("STARTED");

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("peer rejected");
        when(transferProcessRepository.findById(started.getId()))
                .thenReturn(Optional.of(started));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThrows(DataTransferAPIException.class, () -> apiService.suspendTransfer(started.getId()));

        verify(dataPlaneClient).suspend(started.getId(), started.getFormat());
        verify(dataPlaneClient).resume(started.getId(), started.getFormat());
        // Rollback must restore the ORIGINAL isDownloadInProgress value (false), not hardcode true
        verify(transferProcessRepository).save(argThat(tp ->
                TransferState.STARTED.equals(tp.getState())
                        && "STARTED".equals(tp.getDataFlowState())
                        && !tp.isDownloadInProgress()
                        && tp.getSuspendedBy() == null));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED, null);
    }

    @Test
    @DisplayName("suspendTransfer records manual-intervention divergence when rollback dataplane resume also fails")
    public void suspendTransfer_recordsManualInterventionWhenRollbackResumeFails() {
        TransferProcess started = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED
                .withIsDownloadInProgress(true)
                .withDataFlowState("STARTED");

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("peer rejected");
        when(transferProcessRepository.findById(started.getId()))
                .thenReturn(Optional.of(started));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        doThrow(new DataPlaneClientException("dataplane resume failed"))
                .when(dataPlaneClient).resume(started.getId(), started.getFormat());

        assertThrows(DataTransferAPIException.class, () -> apiService.suspendTransfer(started.getId()));

        verify(dataPlaneClient).suspend(started.getId(), started.getFormat());
        verify(dataPlaneClient).resume(started.getId(), started.getFormat());
        verify(transferProcessRepository).save(argThat(tp ->
                TransferState.STARTED.equals(tp.getState())
                        && "SUSPENDED".equals(tp.getDataFlowState())
                        && StringUtils.contains(tp.getDataFlowErrorMessage(), "manual intervention")
                        && tp.getSuspendedBy() == null));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED, null);
    }

    @Test
    @DisplayName("suspendTransfer surfaces original DSP error even when rollback resume and divergence save both fail")
    public void suspendTransfer_originalExceptionSurfacedWhenRollbackResumeAndDivergenceSaveBothFail() {
        TransferProcess started = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED
                .withIsDownloadInProgress(false)
                .withDataFlowState("STARTED");

        DataTransferAPIException originalSuspensionError = new DataTransferAPIException("peer rejected suspension");

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn(originalSuspensionError.getMessage());
        when(transferProcessRepository.findById(started.getId()))
                .thenReturn(Optional.of(started));
        doThrow(new DataPlaneClientException("dataplane resume failed"))
                .when(dataPlaneClient).resume(started.getId(), started.getFormat());
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenThrow(new RuntimeException("divergence save failed"));

        DataTransferAPIException thrown = assertThrows(DataTransferAPIException.class,
                () -> apiService.suspendTransfer(started.getId()));

        assertEquals(originalSuspensionError.getMessage(), thrown.getMessage(),
                "original DSP suspension error must be surfaced even when divergence save also fails");

        verify(dataPlaneClient).suspend(started.getId(), started.getFormat());
        verify(dataPlaneClient).resume(started.getId(), started.getFormat());
        verify(transferProcessRepository).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED, null);
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

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_TERMINATED, null);
    }

    @Test
    @DisplayName("Terminate transfer process failed - transfer process not found")
    public void terminateTransfer_failedNegotiationNotFound() {
        assertThrows(DataTransferAPIException.class, () -> apiService.terminateTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND, null);
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

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR, null);
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

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_TERMINATED, null);
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

        // Only one save: mark isDownloadInProgress=true and fire-and-start.
        // Completion is driven by the DataFlowCallbackController callback, not here.
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        verify(dataPlaneClient).start(any(DataFlowStartMessage.class));
        // Exactly one save: to mark isDownloadInProgress=true
        verify(transferProcessRepository, times(1)).save(argCaptorTransferProcess.capture());

        TransferProcess processWithInProgressFlag = argCaptorTransferProcess.getValue();
        assertEquals(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(), processWithInProgressFlag.getId());
        assertTrue(processWithInProgressFlag.isDownloadInProgress());
        assertEquals(DataTransferFormat.HTTP_PULL.name(), processWithInProgressFlag.getFormat());

        // completeTransfer() must NOT be called — completion is driven by the DP callback
        verify(okHttpRestClient, never()).sendRequestProtocol(contains("/transfers/"), any(JsonNode.class), anyString());
    }

    @Test
    @DisplayName("Download data - fail - Data Plane call throws")
    public void downloadData_transferFail() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        // save returns the input as-is (realistic DB save); used for both isDownloadInProgress=true and the reset
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doThrow(DataPlaneClientException.class).when(dataPlaneClient).start(any(DataFlowStartMessage.class));

        CompletableFuture<Void> future = apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(DataPlaneClientException.class, ex.getCause());

        // Two saves: isDownloadInProgress=true at start, then isDownloadInProgress=false on failure reset
        verify(transferProcessRepository, times(2)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("Download data - fail - no Data Plane registered")
    public void downloadData_fail_strategyNotFound() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doThrow(IllegalStateException.class).when(dataPlaneClient).start(any(DataFlowStartMessage.class));

        CompletableFuture<Void> future = apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());

        // Two saves: isDownloadInProgress=true at start, then isDownloadInProgress=false on DP routing error
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
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();

        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        when(tenantBucketResolver.resolveBucketName(any())).thenReturn("test-bucket");
        when(s3ClientService.generateGetPresignedUrl(eq("test-bucket"), eq(objectKey), any()))
                .thenReturn("https://example.com/presigned-url");

        assertDoesNotThrow(() -> apiService.viewData(objectKey));

        verify(s3ClientService).generateGetPresignedUrl(eq("test-bucket"), eq(objectKey), any());
        verify(publisher).publishEvent(any(ArtifactConsumedEvent.class));
    }

    @Test
    @DisplayName("View data - fail - generate presignURL exception")
    public void viewData_fail_canNotAccessData() {
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();

        when(transferProcessRepository.findById(objectKey))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        when(tenantBucketResolver.resolveBucketName(any())).thenReturn("test-bucket");
        doThrow(new RuntimeException("S3 error generating presigned URL"))
                .when(s3ClientService).generateGetPresignedUrl(any(), any(), any());

        assertThrows(DataTransferAPIException.class,
                () -> apiService.viewData(objectKey));
    }

    @Test
    @DisplayName("View data - fail - S3 not reachable")
    public void viewData_fail_fileNotFound() {
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();

        when(transferProcessRepository.findById(objectKey))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        when(tenantBucketResolver.resolveBucketName(any())).thenReturn("test-bucket");
        doThrow(new RuntimeException("No S3 endpoint available"))
                .when(s3ClientService).generateGetPresignedUrl(any(), any(), any());

        assertThrows(DataTransferAPIException.class,
                () -> apiService.viewData(objectKey));

        verify(s3ClientService).generateGetPresignedUrl(any(), any(), any());
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

        verify(s3ClientService, times(0)).generateGetPresignedUrl(any(), any(), any());
    }

    @Test
    @DisplayName("View data - fail - not downloaded")
    public void viewData_fail_notDownloaded() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED_NOT_DOWNLOADED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED_NOT_DOWNLOADED));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.viewData(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED_NOT_DOWNLOADED.getId()));

        verify(s3ClientService, times(0)).generateGetPresignedUrl(any(), any(), any());
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

    @Test
    @DisplayName("Request transfer (HTTP-PUSH consumer) - creates temp S3 user directly in CP and sends TransferRequestMessage")
    public void requestTransfer_sendsDataFlowPrepareMessageToDataPlane() {
        TransferProcess httpPushInitialized = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_CONSUMER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.INITIALIZED)
                .build();
        DataTransferRequest httpPushRequest = new DataTransferRequest(
                httpPushInitialized.getId(),
                DataTransferFormat.HTTP_PUSH.format(),
                null);

        TemporaryBucketUser tempUser = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId(httpPushInitialized.getId())
                .bucketName("test-bucket")
                .objectKey(httpPushInitialized.getId())
                .accessKey("test-access-key")
                .secretKey("test-secret-key")
                .build();

        when(transferProcessRepository.findById(httpPushInitialized.getId()))
                .thenReturn(Optional.of(httpPushInitialized));
        when(tenantBucketResolver.resolveBucketName(DataTransferMockObjectUtil.TENANT_ID))
                .thenReturn("test-bucket");
        when(temporaryBucketUserService.createTemporaryUser(
                httpPushInitialized.getId(), "test-bucket", httpPushInitialized.getId()))
                .thenReturn(tempUser);
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(apiResponse.getData()).thenReturn(
                TransferSerializer.serializeProtocol(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER);
        when(properties.consumerCallbackAddress()).thenReturn(DataTransferMockObjectUtil.CALLBACK_ADDRESS);

        apiService.requestTransfer(httpPushRequest);

        // No longer calls dataPlaneClient.prepare() — CP creates temp user directly
        verify(dataPlaneClient, never()).prepare(any(), any());
        verify(temporaryBucketUserService).createTemporaryUser(
                httpPushInitialized.getId(), "test-bucket", httpPushInitialized.getId());

        // Verify a TransferRequestMessage was sent to the provider
        verify(okHttpRestClient).sendRequestProtocol(any(), any(), any());
    }

    @Test
    @DisplayName("Request transfer (HTTP-PUSH consumer) - terminates process when temp user creation fails")
    public void requestTransfer_terminatesProcessWhenDataPlanePrepareFails() {
        TransferProcess httpPushInitialized = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_CONSUMER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.INITIALIZED)
                .build();
        DataTransferRequest httpPushRequest = new DataTransferRequest(
                httpPushInitialized.getId(),
                DataTransferFormat.HTTP_PUSH.format(),
                null);

        when(transferProcessRepository.findById(httpPushInitialized.getId()))
                .thenReturn(Optional.of(httpPushInitialized));
        when(tenantBucketResolver.resolveBucketName(DataTransferMockObjectUtil.TENANT_ID))
                .thenReturn("test-bucket");
        doThrow(new RuntimeException("MinIO unreachable"))
                .when(temporaryBucketUserService).createTemporaryUser(anyString(), anyString(), anyString());

        // Should not propagate — exception is caught and process is terminated
        assertDoesNotThrow(() -> apiService.requestTransfer(httpPushRequest));

        verify(transferProcessRepository, atLeastOnce()).save(argCaptorTransferProcess.capture());
        List<TransferProcess> saved = argCaptorTransferProcess.getAllValues();
        boolean hasTerminated = saved.stream().anyMatch(p -> p.getState() == TransferState.TERMINATED);
        assertTrue(hasTerminated, "Transfer process should be saved in TERMINATED state");
    }

    @Test
    @DisplayName("Request transfer (HTTP-PULL consumer) - does not include push dataAddress in TransferRequestMessage")
    public void requestTransfer_doesNotIncludePushDataAddressForHttpPull() {
        // HTTP-PULL: consumer CP should NOT create temp S3 credentials or send dataAddress to provider
        DataTransferRequest httpPullRequest = new DataTransferRequest(
                DataTransferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED.getId(),
                DataTransferFormat.HTTP_PULL.format(),
                null);

        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(apiResponse.getData()).thenReturn(
                TransferSerializer.serializeProtocol(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER);
        when(properties.consumerCallbackAddress()).thenReturn(DataTransferMockObjectUtil.CALLBACK_ADDRESS);

        apiService.requestTransfer(httpPullRequest);

        // HTTP-PULL must NOT create any temporary S3 credentials — that is push-only
        verify(temporaryBucketUserService, never()).createTemporaryUser(anyString(), anyString(), anyString());

        // Verify the outgoing TransferRequestMessage does not carry a push dataAddress
        verify(okHttpRestClient).sendRequestProtocol(
                anyString(),
                argThat(body -> !body.toString().contains("\"dataAddress\"")
                        && !body.toString().contains("bucketName")
                        && !body.toString().contains("accessKey")),
                anyString());
    }

    private static Stream<Arguments> tck_supportedStates() {
        return Stream.of(
                Arguments.of("STARTED", "startTransfer"),
                Arguments.of("COMPLETED", "completeTransfer"),
                Arguments.of("SUSPENDED", "suspendTransfer"),
                Arguments.of("TERMINATED", "terminateTransfer")
        );
    }
}
