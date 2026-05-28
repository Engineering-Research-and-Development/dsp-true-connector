package it.eng.datatransfer.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.datatransfer.client.DataPlaneClient;
import it.eng.datatransfer.exceptions.DataPlaneClientException;
import it.eng.datatransfer.service.TransportProfileResolver;
import it.eng.datatransfer.model.TransportProfile;
import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.DataTransferRequest;
import it.eng.datatransfer.model.EndpointProperty;
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
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.TemporaryBucketUser;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.BucketCredentialsService;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.TenantBucketResolver;
import it.eng.tools.usagecontrol.UsageControlProperties;
import it.eng.tools.util.CredentialUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.inOrder;
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
    private BucketCredentialsService bucketCredentialsService;
    @Mock
    private TenantBucketResolver tenantBucketResolver;
    @Mock
    private TemporaryBucketUserService temporaryBucketUserService;
    @Mock
    private S3Properties s3Properties;
    @Mock
    private TransportProfileResolver transportProfileResolver;
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

    @BeforeEach
    void setUpS3AccessDefaults() {
        lenient().when(tenantBucketResolver.resolveBucketName(DataTransferMockObjectUtil.TENANT_ID)).thenReturn("test-bucket");
        lenient().when(bucketCredentialsService.getBucketCredentials(anyString())).thenAnswer(invocation -> BucketCredentialsEntity.Builder
                .newInstance()
                .bucketName(invocation.getArgument(0))
                .accessKey("default-access-key")
                .secretKey("default-secret-key")
                .build());
        lenient().when(s3Properties.getRegion()).thenReturn("us-east-1");
        lenient().when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
    }

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
    @DisplayName("startTransfer - provider HTTP-PULL delegates presigned URL generation to data plane (FILE artifact)")
    public void startTransfer_providerHttpPull_delegatesPrepareToDataPlane_fileArtifact() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL));
        when(artifactTransferService.findArtifact(any())).thenReturn(DataTransferMockObjectUtil.ARTIFACT_FILE);
        when(tenantBucketResolver.resolveBucketName(DataTransferMockObjectUtil.TENANT_ID)).thenReturn("provider-bucket");
        lenient().when(bucketCredentialsService.getBucketCredentials("provider-bucket")).thenReturn(
                BucketCredentialsEntity.Builder.newInstance()
                        .bucketName("provider-bucket")
                        .accessKey("provider-access-key")
                        .secretKey("provider-secret-key")
                        .build());
        lenient().when(s3Properties.getRegion()).thenReturn("us-east-1");
        lenient().when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        lenient().when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");

        DataFlowPrepareResponse dpResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId())
                .dataAddress(Map.of(
                        DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT, "https://minio.example.com/presigned/artifact",
                        DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT_TYPE, "https://w3id.org/idsa/v4.1/HTTP"))
                .build();
        lenient().when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class), eq("HttpData-PULL")))
                .thenReturn(dpResponse);
        // lenient stub for old S3 code path — replaced by DP prepare after production change
        lenient().when(s3ClientService.generateGetPresignedUrl(any(), any(), any()))
                .thenReturn("https://minio.example.com/presigned/artifact");

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.save(any(TransferProcess.class))).thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED);

        apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId());

        // After production change: CP must delegate to DP, not call S3 directly
        ArgumentCaptor<DataFlowPrepareMessage> prepareCaptor = ArgumentCaptor.forClass(DataFlowPrepareMessage.class);
        verify(dataPlaneClient).prepare(prepareCaptor.capture(), eq("HttpData-PULL"));
        verify(s3ClientService, never()).generateGetPresignedUrl(any(), any(), any());

        // Prepare message must include source.s3 metadata with CP-resolved bucket and dataset as objectKey
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceSection = (Map<String, Object>)
                prepareCaptor.getValue().getMetadata().get(DataPlaneConstants.METADATA_SECTION_SOURCE);
        @SuppressWarnings("unchecked")
        Map<String, Object> s3Section = (Map<String, Object>) sourceSection.get(DataPlaneConstants.METADATA_SECTION_S3);
        assertEquals("provider-bucket", s3Section.get(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        assertEquals(DataTransferMockObjectUtil.DATASET_ID, s3Section.get(DataPlaneConstants.METADATA_S3_OBJECT_KEY));

        // TransferStartMessage sent to peer must carry the DP-returned presigned URL
        verify(okHttpRestClient).sendRequestProtocol(
                anyString(),
                argThat(body -> body.toString().contains("https://minio.example.com/presigned/artifact")),
                anyString());
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
    @DisplayName("Suspend transfer process failed - download in progress")
    public void suspendTransfer_failedDownloadInProgress() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_DOWNLOADING.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_DOWNLOADING));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.suspendTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_DOWNLOADING.getId()));

        verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
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
        when(tenantBucketResolver.resolveBucketName(DataTransferMockObjectUtil.TENANT_ID)).thenReturn("consumer-bucket");
        when(bucketCredentialsService.getBucketCredentials("consumer-bucket")).thenReturn(BucketCredentialsEntity.Builder
                .newInstance()
                .bucketName("consumer-bucket")
                .accessKey("consumer-access-key")
                .secretKey("consumer-secret-key")
                .build());
        when(s3Properties.getRegion()).thenReturn("eu-central-1");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");

        assertDoesNotThrow(() -> apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        ArgumentCaptor<DataFlowStartMessage> startCaptor = ArgumentCaptor.forClass(DataFlowStartMessage.class);
        verify(dataPlaneClient).start(startCaptor.capture());
        // Exactly one save: to mark isDownloadInProgress=true
        verify(transferProcessRepository, times(1)).save(argCaptorTransferProcess.capture());

        TransferProcess processWithInProgressFlag = argCaptorTransferProcess.getValue();
        assertEquals(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(), processWithInProgressFlag.getId());
        assertTrue(processWithInProgressFlag.isDownloadInProgress());
        assertEquals(DataTransferFormat.HTTP_PULL.name(), processWithInProgressFlag.getFormat());

        Map<String, String> endpointProperties = toEndpointPropertyMap(startCaptor.getValue().getDataAddress().getEndpointProperties());
        assertEquals(DataTransferMockObjectUtil.ENDPOINT_URL, startCaptor.getValue().getDataAddress().getEndpoint());
        assertEquals(DataTransferMockObjectUtil.ENDPOINT_TYPE, startCaptor.getValue().getDataAddress().getEndpointType());
        assertEquals("TOKEN-ABCDEFG", endpointProperties.get("authorization"));
        assertEquals("consumer-bucket", endpointProperties.get("sink.bucketName"));
        assertEquals(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(), endpointProperties.get("sink.objectKey"));
        assertEquals("eu-central-1", endpointProperties.get("sink.region"));
        assertEquals("consumer-access-key", endpointProperties.get("sink.accessKey"));
        assertEquals("consumer-secret-key", endpointProperties.get("sink.secretKey"));
        assertEquals("http://minio:9000", endpointProperties.get("sink.endpointOverride"));

        // completeTransfer() must NOT be called — completion is driven by the DP callback
        verify(okHttpRestClient, never()).sendRequestProtocol(contains("/transfers/"), any(JsonNode.class), anyString());
    }

    @Test
    @DisplayName("Download data - HTTP-PUSH provider - sends source.* (provider bucket) and sink.* (consumer credentials) in start message")
    public void downloadData_httpPushProvider_sendsSourceAndSinkPropertiesInStartMessage() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_PROVIDER_HTTP_PUSH.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_PROVIDER_HTTP_PUSH));

        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));

        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantBucketResolver.resolveBucketName(DataTransferMockObjectUtil.TENANT_ID)).thenReturn("provider-bucket");
        when(bucketCredentialsService.getBucketCredentials("provider-bucket")).thenReturn(BucketCredentialsEntity.Builder
                .newInstance()
                .bucketName("provider-bucket")
                .accessKey("provider-access-key")
                .secretKey("provider-secret-key")
                .build());
        when(s3Properties.getRegion()).thenReturn("eu-central-1");
        when(s3Properties.getEndpoint()).thenReturn("http://provider-minio:9000");

        assertDoesNotThrow(() -> apiService.downloadData(
                DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_PROVIDER_HTTP_PUSH.getId()));

        ArgumentCaptor<DataFlowStartMessage> startCaptor = ArgumentCaptor.forClass(DataFlowStartMessage.class);
        verify(dataPlaneClient).start(startCaptor.capture());

        Map<String, String> endpointProperties = toEndpointPropertyMap(startCaptor.getValue().getDataAddress().getEndpointProperties());

        // source.* = provider's bucket (read-side for push)
        assertEquals("provider-bucket", endpointProperties.get("source.bucketName"));
        assertEquals(DataTransferMockObjectUtil.DATASET_ID, endpointProperties.get("source.objectKey"));
        assertEquals("eu-central-1", endpointProperties.get("source.region"));
        assertEquals("provider-access-key", endpointProperties.get("source.accessKey"));
        assertEquals("provider-secret-key", endpointProperties.get("source.secretKey"));
        assertEquals("http://provider-minio:9000", endpointProperties.get("source.endpointOverride"));

        // sink.* = consumer's credentials (write-side for push, translated from flat keys)
        assertEquals("consumer-push-bucket", endpointProperties.get("sink.bucketName"));
        assertEquals("tp-push-obj", endpointProperties.get("sink.objectKey"));
        assertEquals("consumer-temp-access", endpointProperties.get("sink.accessKey"));
        assertEquals("consumer-temp-secret", endpointProperties.get("sink.secretKey"));
        assertEquals("eu-central-1", endpointProperties.get("sink.region"));
        assertEquals("http://consumer-minio:9000", endpointProperties.get("sink.endpointOverride"));

        // Flat consumer keys must NOT be included (they are translated to sink.* only)
        assertFalse(endpointProperties.containsKey("bucketName"));
        assertFalse(endpointProperties.containsKey("accessKey"));
        assertFalse(endpointProperties.containsKey("secretKey"));

        // completeTransfer() must NOT be called — completion is driven by the DP callback
        verify(okHttpRestClient, never()).sendRequestProtocol(contains("/transfers/"), any(JsonNode.class), anyString());
    }

    @Test
    @DisplayName("Download data - fail - missing S3 region returns clear API exception")
    public void downloadData_fail_missingS3Region_returnsClearApiException() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantBucketResolver.resolveBucketName(DataTransferMockObjectUtil.TENANT_ID)).thenReturn("consumer-bucket");
        when(bucketCredentialsService.getBucketCredentials("consumer-bucket")).thenReturn(BucketCredentialsEntity.Builder
                .newInstance()
                .bucketName("consumer-bucket")
                .accessKey("consumer-access-key")
                .secretKey("consumer-secret-key")
                .build());
        when(s3Properties.getRegion()).thenReturn(null);

        CompletableFuture<Void> future = apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(DataTransferAPIException.class, ex.getCause());
        assertEquals("Missing required control plane S3 configuration: region", ex.getCause().getMessage());
        verify(dataPlaneClient, never()).start(any(DataFlowStartMessage.class));
        verify(transferProcessRepository, times(2)).save(any(TransferProcess.class));
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

    private Map<String, String> toEndpointPropertyMap(List<it.eng.dataplane.api.message.EndpointProperty> endpointProperties) {
        if (endpointProperties == null) {
            return Map.of();
        }
        return endpointProperties.stream()
                .collect(HashMap::new, (map, property) -> map.put(property.getName(), property.getValue()), HashMap::putAll);
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

    @Test
    @DisplayName("Download data with gRPC profile - persists profile on TP and uses profile-aware start")
    public void downloadData_withGrpcProfile_persistsProfileAndUsesProfileAwareStart() {
        TransferProcess grpcProcess = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .isDownloaded(false)
                .isDownloadInProgress(false)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_CONSUMER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.STARTED)
                .format(TransportProfile.STREAM_GRPC)
                .build();

        when(transferProcessRepository.findById(grpcProcess.getId())).thenReturn(Optional.of(grpcProcess));
        when(transportProfileResolver.resolve(TransportProfile.STREAM_GRPC)).thenReturn(TransportProfile.STREAM_GRPC);
        when(usageControlProperties.usageControlEnabled()).thenReturn(false);
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");
        when(tenantBucketResolver.resolveBucketName(DataTransferMockObjectUtil.TENANT_ID)).thenReturn("consumer-bucket");
        when(bucketCredentialsService.getBucketCredentials("consumer-bucket")).thenReturn(BucketCredentialsEntity.Builder
                .newInstance()
                .bucketName("consumer-bucket")
                .accessKey("consumer-access-key")
                .secretKey("consumer-secret-key")
                .build());
        when(s3Properties.getRegion()).thenReturn("eu-central-1");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");

        assertDoesNotThrow(() -> apiService.downloadData(grpcProcess.getId()));

        verify(transferProcessRepository).save(argCaptorTransferProcess.capture());
        TransferProcess saved = argCaptorTransferProcess.getValue();
        assertEquals(TransportProfile.STREAM_GRPC, saved.getTransportProfile(),
                "transport profile must be persisted on the TransferProcess before the DP start call");
        assertTrue(saved.isDownloadInProgress());

        ArgumentCaptor<DataFlowStartMessage> startCaptor = ArgumentCaptor.forClass(DataFlowStartMessage.class);
        verify(dataPlaneClient).start(startCaptor.capture(), eq(TransportProfile.STREAM_GRPC));
        verify(dataPlaneClient, never()).start(any(DataFlowStartMessage.class));
        assertNotNull(startCaptor.getValue().getMessageId());
        assertEquals(grpcProcess.getConsumerPid(), startCaptor.getValue().getParticipantId());
        assertEquals(grpcProcess.getProviderPid(), startCaptor.getValue().getCounterPartyId());
        assertEquals(DataPlaneConstants.DSPACE_2025_01_CONTEXT, startCaptor.getValue().getDataspaceContext());
        assertNotNull(startCaptor.getValue().getClaims());
        assertNotNull(startCaptor.getValue().getDataAddress());
        assertEquals("DataAddress", startCaptor.getValue().getDataAddress().getType());
        Map<String, String> endpointProperties = toEndpointPropertyMap(startCaptor.getValue().getDataAddress().getEndpointProperties());
        assertEquals("TOKEN-ABCDEFG", endpointProperties.get("authorization"));
        assertEquals("consumer-bucket", endpointProperties.get("sink.bucketName"));
        assertEquals(grpcProcess.getId(), endpointProperties.get("sink.objectKey"));
        assertEquals("eu-central-1", endpointProperties.get("sink.region"));
        assertEquals("consumer-access-key", endpointProperties.get("sink.accessKey"));
        assertEquals("consumer-secret-key", endpointProperties.get("sink.secretKey"));
        assertEquals("http://minio:9000", endpointProperties.get("sink.endpointOverride"));
    }

    @Test
    @DisplayName("Terminate transfer - with grpc profile calls profile-aware DP terminate then cleans up sticky")
    public void terminateTransfer_withTransportProfile_callsProfileAwareTerminateAndCleanup() {
        TransferProcess startedGrpc = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.STARTED)
                .format(TransportProfile.STREAM_GRPC)
                .transportProfile(TransportProfile.STREAM_GRPC)
                .build();

        when(transferProcessRepository.findById(startedGrpc.getId())).thenReturn(Optional.of(startedGrpc));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(), any(), any())).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        apiService.terminateTransfer(startedGrpc.getId());

        verify(dataPlaneClient).terminate(startedGrpc.getId(), TransportProfile.STREAM_GRPC, TransportProfile.STREAM_GRPC);
        verify(dataPlaneClient).clearStickyAssignment(startedGrpc.getId());
    }

    @Test
    @DisplayName("Terminate transfer - without transport profile skips DP terminate but cleans up sticky")
    public void terminateTransfer_withoutTransportProfile_skipsDpTerminateButCleansSticky() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(), any(), any())).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        apiService.terminateTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        verify(dataPlaneClient, never()).terminate(anyString(), anyString(), anyString());
        verify(dataPlaneClient).clearStickyAssignment(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
    }

    @Test
    @DisplayName("Complete transfer - cleans up sticky assignment after reaching terminal COMPLETED state")
    public void completeTransfer_cleansUpStickyAssignment() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(), any(), any())).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        apiService.completeTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        verify(dataPlaneClient).clearStickyAssignment(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
    }

    @Test
    @DisplayName("Suspend transfer - with grpc profile calls profile-aware DP suspend")
    public void suspendTransfer_withTransportProfile_callsProfileAwareSuspend() {
        TransferProcess startedGrpc = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.STARTED)
                .format(TransportProfile.STREAM_GRPC)
                .transportProfile(TransportProfile.STREAM_GRPC)
                .build();

        when(transferProcessRepository.findById(startedGrpc.getId())).thenReturn(Optional.of(startedGrpc));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(), any(), any())).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        apiService.suspendTransfer(startedGrpc.getId());

        verify(dataPlaneClient).suspend(startedGrpc.getId(), TransportProfile.STREAM_GRPC, TransportProfile.STREAM_GRPC);
    }

    @Test
    @DisplayName("Terminate transfer - DP terminate throws; sticky cleanup still runs and audit still fires")
    public void terminateTransfer_dpTerminateThrows_stickyCleanupRunsAndAuditFires() {
        TransferProcess startedGrpc = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.STARTED)
                .format(TransportProfile.STREAM_GRPC)
                .transportProfile(TransportProfile.STREAM_GRPC)
                .build();

        when(transferProcessRepository.findById(startedGrpc.getId())).thenReturn(Optional.of(startedGrpc));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(), any(), any())).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        doThrow(new DataPlaneClientException("DP unreachable"))
                .when(dataPlaneClient).terminate(startedGrpc.getId(), TransportProfile.STREAM_GRPC, TransportProfile.STREAM_GRPC);

        // must not propagate — DP terminate is best-effort after CP is already TERMINATED
        JsonNode result = apiService.terminateTransfer(startedGrpc.getId());

        assertNotNull(result);
        verify(dataPlaneClient).terminate(startedGrpc.getId(), TransportProfile.STREAM_GRPC, TransportProfile.STREAM_GRPC);
        // sticky assignment MUST be cleared even if DP terminate threw
        verify(dataPlaneClient).clearStickyAssignment(startedGrpc.getId());
        // audit event MUST fire
        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_TERMINATED, null);
    }

    @Test
    @DisplayName("Suspend transfer - DP suspend throws; DataTransferAPIException raised and audit fires")
    public void suspendTransfer_dpSuspendThrows_throwsDataTransferAPIExceptionAndAuditFires() {
        TransferProcess startedGrpc = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.STARTED)
                .format(TransportProfile.STREAM_GRPC)
                .transportProfile(TransportProfile.STREAM_GRPC)
                .build();

        when(transferProcessRepository.findById(startedGrpc.getId())).thenReturn(Optional.of(startedGrpc));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(), any(), any())).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        doThrow(new DataPlaneClientException("DP unreachable"))
                .when(dataPlaneClient).suspend(startedGrpc.getId(), TransportProfile.STREAM_GRPC, TransportProfile.STREAM_GRPC);

        // DP suspend failure must be surfaced as DataTransferAPIException
        assertThrows(DataTransferAPIException.class, () -> apiService.suspendTransfer(startedGrpc.getId()));

        // CP state was persisted SUSPENDED before the DP call
        verify(transferProcessRepository).save(any(TransferProcess.class));
        // audit event MUST fire even on DP failure
        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED, null);
    }

    @Test
    @DisplayName("downloadData - with transport profile persists assignedDataplaneEndpoint after DP start")
    public void downloadData_withProfile_persistsAssignedEndpoint() {
        TransferProcess grpcProcess = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .isDownloaded(false)
                .isDownloadInProgress(false)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_CONSUMER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.STARTED)
                .format(TransportProfile.STREAM_GRPC)
                .build();

        when(transferProcessRepository.findById(grpcProcess.getId())).thenReturn(Optional.of(grpcProcess));
        when(transportProfileResolver.resolve(TransportProfile.STREAM_GRPC)).thenReturn(TransportProfile.STREAM_GRPC);
        when(usageControlProperties.usageControlEnabled()).thenReturn(false);
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");

        // After start(), the router has stored a sticky entry; simulate that here
        when(dataPlaneClient.getStickyEndpoint(grpcProcess.getId()))
                .thenReturn(Optional.of("http://dp-grpc:9090"));

        assertDoesNotThrow(() -> apiService.downloadData(grpcProcess.getId()));

        // Should have called save() twice: once for isDownloadInProgress, once for assignedDataplaneEndpoint
        verify(transferProcessRepository, times(2)).save(argCaptorTransferProcess.capture());
        List<TransferProcess> savedValues = argCaptorTransferProcess.getAllValues();
        boolean endpointPersisted = savedValues.stream()
                .anyMatch(tp -> "http://dp-grpc:9090".equals(tp.getAssignedDataplaneEndpoint()));
        assertTrue(endpointPersisted, "assignedDataplaneEndpoint must be persisted after start with transport profile");
    }

    @Test
    @DisplayName("terminateTransfer - with persisted assignedDataplaneEndpoint restores sticky before DP call")
    public void terminateTransfer_withPersistedEndpoint_restoresStickyBeforeTerminate() {
        TransferProcess startedGrpc = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.STARTED)
                .format(TransportProfile.STREAM_GRPC)
                .transportProfile(TransportProfile.STREAM_GRPC)
                .assignedDataplaneEndpoint("http://dp-grpc:9090")
                .build();

        when(transferProcessRepository.findById(startedGrpc.getId())).thenReturn(Optional.of(startedGrpc));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(), any(), any())).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        apiService.terminateTransfer(startedGrpc.getId());

        // Sticky must be restored from the persisted endpoint before the DP terminate call
        InOrder inOrder = inOrder(dataPlaneClient);
        inOrder.verify(dataPlaneClient).restoreStickyAssignment(startedGrpc.getId(), "http://dp-grpc:9090");
        inOrder.verify(dataPlaneClient).terminate(startedGrpc.getId(), TransportProfile.STREAM_GRPC, TransportProfile.STREAM_GRPC);
        inOrder.verify(dataPlaneClient).clearStickyAssignment(startedGrpc.getId());
    }

    @Test
    @DisplayName("suspendTransfer - with persisted assignedDataplaneEndpoint restores sticky before DP call")
    public void suspendTransfer_withPersistedEndpoint_restoresStickyBeforeSuspend() {
        TransferProcess startedGrpc = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.STARTED)
                .format(TransportProfile.STREAM_GRPC)
                .transportProfile(TransportProfile.STREAM_GRPC)
                .assignedDataplaneEndpoint("http://dp-grpc:9090")
                .build();

        when(transferProcessRepository.findById(startedGrpc.getId())).thenReturn(Optional.of(startedGrpc));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(), any(), any())).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        apiService.suspendTransfer(startedGrpc.getId());

        // Sticky must be restored before the DP suspend call
        InOrder inOrder = inOrder(dataPlaneClient);
        inOrder.verify(dataPlaneClient).restoreStickyAssignment(startedGrpc.getId(), "http://dp-grpc:9090");
        inOrder.verify(dataPlaneClient).suspend(startedGrpc.getId(), TransportProfile.STREAM_GRPC, TransportProfile.STREAM_GRPC);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Task 5 – CP gRPC orchestration
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("startTransfer - provider gRPC calls prepare, sends prepared address in TransferStartMessage, persists profile + endpoint")
    public void startTransfer_grpc_provider_callsPrepare_sendsGrpcAddressAndPersistsProfile() {
        DataAddress requestDataAddress = DataAddress.Builder.newInstance()
                .endpointProperties(List.of(
                        EndpointProperty.Builder.newInstance().name("sourceType").value("S3").build(),
                        EndpointProperty.Builder.newInstance().name("finite").value("false").build(),
                        EndpointProperty.Builder.newInstance().name(DataPlaneConstants.METADATA_S3_BUCKET_NAME).value("source-bucket").build(),
                        EndpointProperty.Builder.newInstance().name(DataPlaneConstants.METADATA_S3_OBJECT_KEY).value("source-object").build(),
                        EndpointProperty.Builder.newInstance().name(DataPlaneConstants.METADATA_S3_REGION).value("eu-west-1").build()))
                .build();
        TransferProcess requestedGrpc = TransferProcess.Builder.newInstance()
                .id("tp-provider-grpc")
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .format(TransportProfile.STREAM_GRPC)
                .state(TransferState.REQUESTED)
                .dataAddress(requestDataAddress)
                .build();
        when(transferProcessRepository.findById(requestedGrpc.getId()))
                .thenReturn(Optional.of(requestedGrpc));
        when(transportProfileResolver.resolve(TransportProfile.STREAM_GRPC)).thenReturn(TransportProfile.STREAM_GRPC);
        when(tenantBucketResolver.resolveBucketName(DataTransferMockObjectUtil.TENANT_ID)).thenReturn("provider-bucket");
        when(bucketCredentialsService.getBucketCredentials("provider-bucket")).thenReturn(BucketCredentialsEntity.Builder
                .newInstance()
                .bucketName("provider-bucket")
                .accessKey("provider-access-key")
                .secretKey("provider-secret-key")
                .build());
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");

        DataFlowPrepareResponse prepareResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(requestedGrpc.getId())
                .dataAddress(Map.of("endpoint", "grpc://dp-grpc:5050", "sessionId", "sess-123"))
                .build();
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class), eq(TransportProfile.STREAM_GRPC), eq(TransportProfile.STREAM_GRPC)))
                .thenReturn(prepareResponse);
        when(dataPlaneClient.getStickyEndpoint(requestedGrpc.getId()))
                .thenReturn(Optional.of("http://dp-grpc:9090"));

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        apiService.startTransfer(requestedGrpc.getId());

        ArgumentCaptor<DataFlowPrepareMessage> prepareCaptor = ArgumentCaptor.forClass(DataFlowPrepareMessage.class);
        verify(dataPlaneClient).prepare(prepareCaptor.capture(),
                eq(TransportProfile.STREAM_GRPC), eq(TransportProfile.STREAM_GRPC));
        assertEquals(TransportProfile.STREAM_GRPC, prepareCaptor.getValue().getTransferType());
        assertFalse(prepareCaptor.getValue().getMetadata().containsKey(DataPlaneConstants.METADATA_FIELD_TRANSFER_TYPE));
        assertNotNull(prepareCaptor.getValue().getMessageId());
        assertEquals(requestedGrpc.getProviderPid(), prepareCaptor.getValue().getParticipantId());
        assertEquals(requestedGrpc.getConsumerPid(), prepareCaptor.getValue().getCounterPartyId());
        assertEquals(DataPlaneConstants.DSPACE_2025_01_CONTEXT, prepareCaptor.getValue().getDataspaceContext());
        assertNotNull(prepareCaptor.getValue().getClaims());
        assertEquals(Map.of(
                        DataPlaneConstants.METADATA_FIELD_SOURCE_TYPE, "S3",
                        DataPlaneConstants.METADATA_FIELD_FINITE, "false",
                        DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                DataPlaneConstants.METADATA_S3_BUCKET_NAME, "provider-bucket",
                                DataPlaneConstants.METADATA_S3_OBJECT_KEY, DataTransferMockObjectUtil.DATASET_ID,
                                DataPlaneConstants.METADATA_S3_REGION, "us-east-1",
                                DataPlaneConstants.METADATA_S3_ACCESS_KEY, "provider-access-key",
                                DataPlaneConstants.METADATA_S3_SECRET_KEY, "provider-secret-key",
                                DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE, "http://minio:9000")),
                prepareCaptor.getValue().getMetadata().get(DataPlaneConstants.METADATA_SECTION_SOURCE));

        // TP saved as STARTED must carry transportProfile and assignedDataplaneEndpoint
        verify(transferProcessRepository).save(argCaptorTransferProcess.capture());
        TransferProcess saved = argCaptorTransferProcess.getValue();
        assertEquals(TransferState.STARTED, saved.getState());
        assertEquals(TransportProfile.STREAM_GRPC, saved.getTransportProfile(),
                "transportProfile must be persisted on the STARTED provider TP");
        assertEquals("http://dp-grpc:9090", saved.getAssignedDataplaneEndpoint(),
                "assignedDataplaneEndpoint must be persisted after prepare");

        // TransferStartMessage sent to peer must carry the prepared gRPC address
        verify(okHttpRestClient).sendRequestProtocol(
                anyString(),
                argThat(body -> body.toString().contains("grpc://dp-grpc:5050")),
                anyString());
    }

    @Test
    @DisplayName("startTransfer - provider gRPC: peer failure rolls back TP, terminates prepared DP session, clears sticky")
    public void startTransfer_grpc_provider_rollback_peerFails_terminatesDpAndClearsSticky() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC));
        when(transportProfileResolver.resolve(TransportProfile.STREAM_GRPC)).thenReturn(TransportProfile.STREAM_GRPC);

        DataFlowPrepareResponse prepareResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId())
                .dataAddress(Map.of("endpoint", "grpc://dp-grpc:5050"))
                .build();
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class), eq(TransportProfile.STREAM_GRPC), eq(TransportProfile.STREAM_GRPC)))
                .thenReturn(prepareResponse);
        when(dataPlaneClient.getStickyEndpoint(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId()))
                .thenReturn(Optional.of("http://dp-grpc:9090"));

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("peer error");
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId()));

        // Two saves: once to pre-save STARTED, once to roll back to REQUESTED
        verify(transferProcessRepository, times(2)).save(argCaptorTransferProcess.capture());
        List<TransferProcess> savedValues = argCaptorTransferProcess.getAllValues();
        boolean hasRequested = savedValues.stream().anyMatch(p -> p.getState() == TransferState.REQUESTED);
        assertTrue(hasRequested, "rollback must save TP in REQUESTED state");

        // DP terminate must be called as best-effort cleanup
        verify(dataPlaneClient).terminate(
                DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId(),
                TransportProfile.STREAM_GRPC, TransportProfile.STREAM_GRPC);
        // Sticky must be cleared even after rollback
        verify(dataPlaneClient).clearStickyAssignment(
                DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId());
    }

    @Test
    @DisplayName("startTransfer - provider gRPC: DP terminate throws on rollback; sticky still cleared")
    public void startTransfer_grpc_provider_rollback_dpTerminateThrows_stickyStillCleared() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC));
        when(transportProfileResolver.resolve(TransportProfile.STREAM_GRPC)).thenReturn(TransportProfile.STREAM_GRPC);

        DataFlowPrepareResponse prepareResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId())
                .dataAddress(Map.of("endpoint", "grpc://dp-grpc:5050"))
                .build();
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class), eq(TransportProfile.STREAM_GRPC), eq(TransportProfile.STREAM_GRPC)))
                .thenReturn(prepareResponse);
        when(dataPlaneClient.getStickyEndpoint(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId()))
                .thenReturn(Optional.empty());
        doThrow(new DataPlaneClientException("DP unreachable"))
                .when(dataPlaneClient).terminate(anyString(), anyString(), anyString());

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("peer error");
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Rollback must not propagate the DP terminate exception
        assertThrows(DataTransferAPIException.class,
                () -> apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId()));

        // Sticky cleanup must still execute even when DP terminate threw
        verify(dataPlaneClient).clearStickyAssignment(
                DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId());
    }

    @Test
    @DisplayName("requestTransfer - gRPC with caller-supplied dataAddress preserves it in TransferRequestMessage")
    public void requestTransfer_grpc_withDataAddress_preservesIt() throws Exception {
        TransferProcess grpcInitialized = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_CONSUMER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.INITIALIZED)
                .build();

        // Build a dataAddress JSON node representing source hints
        JsonNode dataAddressNode = new ObjectMapper().readTree(
                "{\"endpointProperties\":[{\"name\":\"sourceType\",\"value\":\"S3\"},{\"name\":\"finite\",\"value\":\"true\"}]}");
        DataTransferRequest grpcRequest = new DataTransferRequest(
                grpcInitialized.getId(),
                TransportProfile.STREAM_GRPC,
                dataAddressNode);

        when(transferProcessRepository.findById(grpcInitialized.getId()))
                .thenReturn(Optional.of(grpcInitialized));
        when(transportProfileResolver.resolve(TransportProfile.STREAM_GRPC)).thenReturn(TransportProfile.STREAM_GRPC);
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(apiResponse.getData()).thenReturn(
                TransferSerializer.serializeProtocol(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER);
        when(properties.consumerCallbackAddress()).thenReturn(DataTransferMockObjectUtil.CALLBACK_ADDRESS);

        apiService.requestTransfer(grpcRequest);

        // TransferRequestMessage sent to provider must contain the source hints
        verify(okHttpRestClient).sendRequestProtocol(
                anyString(),
                argThat(body -> body.toString().contains("sourceType") && body.toString().contains("S3")),
                anyString());
        // No temp S3 user must be created for gRPC
        verify(temporaryBucketUserService, never()).createTemporaryUser(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("requestTransfer - gRPC without dataAddress omits it from TransferRequestMessage")
    public void requestTransfer_grpc_withoutDataAddress_omitsIt() {
        TransferProcess grpcInitialized = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_CONSUMER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.INITIALIZED)
                .build();

        DataTransferRequest grpcRequest = new DataTransferRequest(
                grpcInitialized.getId(),
                TransportProfile.STREAM_GRPC,
                null);  // no dataAddress

        when(transferProcessRepository.findById(grpcInitialized.getId()))
                .thenReturn(Optional.of(grpcInitialized));
        when(transportProfileResolver.resolve(TransportProfile.STREAM_GRPC)).thenReturn(TransportProfile.STREAM_GRPC);
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(apiResponse.getData()).thenReturn(
                TransferSerializer.serializeProtocol(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER);
        when(properties.consumerCallbackAddress()).thenReturn(DataTransferMockObjectUtil.CALLBACK_ADDRESS);

        apiService.requestTransfer(grpcRequest);

        // dataAddress must be absent in the outgoing message (no sourceType hints if none given)
        verify(okHttpRestClient).sendRequestProtocol(
                anyString(),
                argThat(body -> !body.toString().contains("sourceType")),
                anyString());
        verify(temporaryBucketUserService, never()).createTemporaryUser(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("requestTransfer - gRPC with invalid dataAddress fails explicitly")
    public void requestTransfer_grpc_withInvalidDataAddress_failsExplicitly() throws Exception {
        TransferProcess grpcInitialized = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_CONSUMER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.INITIALIZED)
                .build();

        DataTransferRequest grpcRequest = new DataTransferRequest(
                grpcInitialized.getId(),
                TransportProfile.STREAM_GRPC,
                new ObjectMapper().readTree("{\"endpointProperties\":\"not-an-array\"}"));

        when(transferProcessRepository.findById(grpcInitialized.getId()))
                .thenReturn(Optional.of(grpcInitialized));
        when(transportProfileResolver.resolve(TransportProfile.STREAM_GRPC)).thenReturn(TransportProfile.STREAM_GRPC);

        assertThrows(DataTransferAPIException.class, () -> apiService.requestTransfer(grpcRequest));

        verify(okHttpRestClient, never()).sendRequestProtocol(anyString(), any(JsonNode.class), anyString());
    }

    @Test
    @DisplayName("startTransfer - provider gRPC: missing prepare dataAddress fails and cleans prepared session")
    public void startTransfer_grpc_provider_missingPrepareDataAddress_failsAndCleansPreparedSession() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC));
        when(transportProfileResolver.resolve(TransportProfile.STREAM_GRPC)).thenReturn(TransportProfile.STREAM_GRPC);
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class), eq(TransportProfile.STREAM_GRPC), eq(TransportProfile.STREAM_GRPC)))
                .thenReturn(DataFlowPrepareResponse.Builder.newInstance()
                        .processId(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId())
                        .build());
        when(dataPlaneClient.getStickyEndpoint(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId()))
                .thenReturn(Optional.of("http://dp-grpc:9090"));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId()));

        verify(dataPlaneClient).restoreStickyAssignment(
                DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId(),
                "http://dp-grpc:9090");
        verify(dataPlaneClient).terminate(
                DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId(),
                TransportProfile.STREAM_GRPC,
                TransportProfile.STREAM_GRPC);
        verify(dataPlaneClient).clearStickyAssignment(
                DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId());
        verify(okHttpRestClient, never()).sendRequestProtocol(anyString(), any(JsonNode.class), anyString());
        verify(transferProcessRepository, never()).save(any(TransferProcess.class));
    }

    @ParameterizedTest
    @MethodSource("missingControlPlaneS3CredentialFields")
    @DisplayName("startTransfer - provider gRPC missing CP-owned S3 credential fails explicitly")
    public void startTransfer_grpc_provider_missingControlPlaneS3Credential_failsExplicitly(String missingField,
                                                                                             BucketCredentialsEntity bucketCredentials,
                                                                                             String expectedMessage) {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC));
        when(transportProfileResolver.resolve(TransportProfile.STREAM_GRPC)).thenReturn(TransportProfile.STREAM_GRPC);
        when(tenantBucketResolver.resolveBucketName(DataTransferMockObjectUtil.TENANT_ID)).thenReturn("provider-bucket");
        when(bucketCredentialsService.getBucketCredentials("provider-bucket")).thenReturn(bucketCredentials);
        when(s3Properties.getRegion()).thenReturn("us-east-1");

        DataTransferAPIException exception = assertThrows(DataTransferAPIException.class,
                () -> apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_GRPC.getId()));

        assertEquals(expectedMessage, exception.getMessage(), "unexpected message for missing " + missingField);
        verify(dataPlaneClient, never()).prepare(any(DataFlowPrepareMessage.class), anyString(), anyString());
        verify(okHttpRestClient, never()).sendRequestProtocol(anyString(), any(JsonNode.class), anyString());
        verify(transferProcessRepository, never()).save(any(TransferProcess.class));
    }

    private static Stream<Arguments> missingControlPlaneS3CredentialFields() {
        return Stream.of(
                Arguments.of("access key",
                        BucketCredentialsEntity.Builder.newInstance()
                                .bucketName("provider-bucket")
                                .accessKey(null)
                                .secretKey("provider-secret-key")
                                .build(),
                        "Missing required control plane S3 credentials for bucket provider-bucket: accessKey"),
                Arguments.of("secret key",
                        BucketCredentialsEntity.Builder.newInstance()
                                .bucketName("provider-bucket")
                                .accessKey("provider-access-key")
                                .secretKey(null)
                                .build(),
                        "Missing required control plane S3 credentials for bucket provider-bucket: secretKey"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Task 7 – Kafka CP bucket/credential alignment
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("startTransfer - provider Kafka: prepare metadata carries provider tenant S3 credentials and prepared Kafka address is sent to consumer")
    public void startTransfer_kafka_provider_callsPrepare_sendsKafkaAddressAndPropagatesProviderS3Credentials() {
        DataAddress requestDataAddress = DataAddress.Builder.newInstance()
                .endpointProperties(List.of(
                        EndpointProperty.Builder.newInstance().name("sourceType").value("S3").build(),
                        EndpointProperty.Builder.newInstance().name("finite").value("false").build()))
                .build();
        TransferProcess requestedKafka = TransferProcess.Builder.newInstance()
                .id("tp-provider-kafka")
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .format(TransportProfile.STREAM_KAFKA)
                .state(TransferState.REQUESTED)
                .dataAddress(requestDataAddress)
                .build();

        when(transferProcessRepository.findById(requestedKafka.getId()))
                .thenReturn(Optional.of(requestedKafka));
        when(transportProfileResolver.resolve(TransportProfile.STREAM_KAFKA)).thenReturn(TransportProfile.STREAM_KAFKA);
        when(tenantBucketResolver.resolveBucketName(DataTransferMockObjectUtil.TENANT_ID)).thenReturn("provider-bucket");
        when(bucketCredentialsService.getBucketCredentials("provider-bucket")).thenReturn(BucketCredentialsEntity.Builder
                .newInstance()
                .bucketName("provider-bucket")
                .accessKey("provider-access-key")
                .secretKey("provider-secret-key")
                .build());
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");

        DataFlowPrepareResponse prepareResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(requestedKafka.getId())
                .dataAddress(Map.of("endpoint", "kafka://broker:9092", "topic", "data-transfer-topic"))
                .build();
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class), eq(TransportProfile.STREAM_KAFKA), eq(TransportProfile.STREAM_KAFKA)))
                .thenReturn(prepareResponse);
        when(dataPlaneClient.getStickyEndpoint(requestedKafka.getId()))
                .thenReturn(Optional.of("http://dp-kafka:9090"));

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        apiService.startTransfer(requestedKafka.getId());

        ArgumentCaptor<DataFlowPrepareMessage> prepareCaptor = ArgumentCaptor.forClass(DataFlowPrepareMessage.class);
        verify(dataPlaneClient).prepare(prepareCaptor.capture(),
                eq(TransportProfile.STREAM_KAFKA), eq(TransportProfile.STREAM_KAFKA));
        assertEquals(TransportProfile.STREAM_KAFKA, prepareCaptor.getValue().getTransferType());
        assertNotNull(prepareCaptor.getValue().getMessageId());
        assertEquals(requestedKafka.getProviderPid(), prepareCaptor.getValue().getParticipantId());
        assertEquals(requestedKafka.getConsumerPid(), prepareCaptor.getValue().getCounterPartyId());
        assertEquals(DataPlaneConstants.DSPACE_2025_01_CONTEXT, prepareCaptor.getValue().getDataspaceContext());

        // prepare metadata must carry provider S3 source credentials under source.s3
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceSection = (Map<String, Object>) prepareCaptor.getValue().getMetadata()
                .get(DataPlaneConstants.METADATA_SECTION_SOURCE);
        assertNotNull(sourceSection, "prepare metadata must contain a 'source' section");
        @SuppressWarnings("unchecked")
        Map<String, Object> s3Section = (Map<String, Object>) sourceSection.get(DataPlaneConstants.METADATA_SECTION_S3);
        assertNotNull(s3Section, "source section must contain an 's3' sub-section");
        assertEquals("provider-bucket", s3Section.get(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        assertEquals(DataTransferMockObjectUtil.DATASET_ID, s3Section.get(DataPlaneConstants.METADATA_S3_OBJECT_KEY));
        assertEquals("us-east-1", s3Section.get(DataPlaneConstants.METADATA_S3_REGION));
        assertEquals("provider-access-key", s3Section.get(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        assertEquals("provider-secret-key", s3Section.get(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        assertEquals("http://minio:9000", s3Section.get(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));

        // TP saved as STARTED must carry the Kafka transportProfile
        verify(transferProcessRepository).save(argCaptorTransferProcess.capture());
        TransferProcess saved = argCaptorTransferProcess.getValue();
        assertEquals(TransferState.STARTED, saved.getState());
        assertEquals(TransportProfile.STREAM_KAFKA, saved.getTransportProfile(),
                "transportProfile must be persisted on the STARTED provider TP");
        assertEquals("http://dp-kafka:9090", saved.getAssignedDataplaneEndpoint(),
                "assignedDataplaneEndpoint must be persisted after prepare");

        // TransferStartMessage sent to peer must carry the prepared Kafka address
        verify(okHttpRestClient).sendRequestProtocol(
                anyString(),
                argThat(body -> body.toString().contains("kafka://broker:9092")),
                anyString());
    }

    @Test
    @DisplayName("downloadData - Kafka consumer - sends consumer tenant bucket credentials as sink.* in start message")
    public void downloadData_kafka_consumer_sendsConsumerBucketCredentialsAsSinkPropertiesInStartMessage() {
        TransferProcess kafkaConsumerProcess = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .isDownloaded(false)
                .isDownloadInProgress(false)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_CONSUMER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.STARTED)
                .format(TransportProfile.STREAM_KAFKA)
                .build();

        when(transferProcessRepository.findById(kafkaConsumerProcess.getId()))
                .thenReturn(Optional.of(kafkaConsumerProcess));
        when(transportProfileResolver.resolve(TransportProfile.STREAM_KAFKA)).thenReturn(TransportProfile.STREAM_KAFKA);
        when(usageControlProperties.usageControlEnabled()).thenReturn(false);
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");
        when(tenantBucketResolver.resolveBucketName(DataTransferMockObjectUtil.TENANT_ID)).thenReturn("consumer-kafka-bucket");
        when(bucketCredentialsService.getBucketCredentials("consumer-kafka-bucket")).thenReturn(BucketCredentialsEntity.Builder
                .newInstance()
                .bucketName("consumer-kafka-bucket")
                .accessKey("kafka-consumer-access-key")
                .secretKey("kafka-consumer-secret-key")
                .build());
        when(s3Properties.getRegion()).thenReturn("eu-central-1");
        when(s3Properties.getEndpoint()).thenReturn("http://kafka-minio:9000");

        assertDoesNotThrow(() -> apiService.downloadData(kafkaConsumerProcess.getId()));

        verify(transferProcessRepository).save(argCaptorTransferProcess.capture());
        TransferProcess saved = argCaptorTransferProcess.getValue();
        assertEquals(TransportProfile.STREAM_KAFKA, saved.getTransportProfile(),
                "Kafka transport profile must be persisted on the TransferProcess before the DP start call");
        assertTrue(saved.isDownloadInProgress());

        ArgumentCaptor<DataFlowStartMessage> startCaptor = ArgumentCaptor.forClass(DataFlowStartMessage.class);
        verify(dataPlaneClient).start(startCaptor.capture(), eq(TransportProfile.STREAM_KAFKA));
        verify(dataPlaneClient, never()).start(any(DataFlowStartMessage.class));

        // start message must carry consumer bucket credentials as sink.* properties
        Map<String, String> endpointProperties = toEndpointPropertyMap(
                startCaptor.getValue().getDataAddress().getEndpointProperties());
        assertEquals("consumer-kafka-bucket", endpointProperties.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME));
        assertEquals(kafkaConsumerProcess.getId(), endpointProperties.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY));
        assertEquals("eu-central-1", endpointProperties.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION));
        assertEquals("kafka-consumer-access-key", endpointProperties.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY));
        assertEquals("kafka-consumer-secret-key", endpointProperties.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY));
        assertEquals("http://kafka-minio:9000", endpointProperties.get(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ENDPOINT_OVERRIDE));
    }
}
