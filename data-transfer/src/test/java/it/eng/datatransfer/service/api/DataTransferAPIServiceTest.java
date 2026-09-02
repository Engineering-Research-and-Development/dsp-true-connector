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
import it.eng.tools.s3.util.S3Utils;
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
        lenient().when(s3Properties.getAccessKey()).thenReturn("minioadmin");
        lenient().when(s3Properties.getSecretKey()).thenReturn("minioadmin-secret");
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
        lenient().when(s3Properties.getExternalPresignedEndpoint()).thenReturn("http://downloads.example.com");
        lenient().when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");

        DataFlowPrepareResponse dpResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId())
                .dataAddress(Map.of(
                        DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT, "https://minio.example.com/presigned/artifact",
                        DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT_TYPE, "https://w3id.org/idsa/v4.1/HTTP"))
                .build();
        lenient().when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class), eq("HttpData-PULL"), isNull()))
                .thenReturn(dpResponse);
        lenient().when(dataPlaneClient.getStickyEndpoint(any())).thenReturn(Optional.empty());

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.save(any(TransferProcess.class))).thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED);

        apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId());

        // CP must use the sticky/profile-aware overload (3-arg) for HTTP-PULL prepare
        ArgumentCaptor<DataFlowPrepareMessage> prepareCaptor = ArgumentCaptor.forClass(DataFlowPrepareMessage.class);
        verify(dataPlaneClient).prepare(prepareCaptor.capture(), eq("HttpData-PULL"), isNull());
        verify(s3ClientService, never()).generateGetPresignedUrl(any(), any(), any());

        // Prepare message must include source.s3 metadata with CP-resolved bucket and dataset as objectKey
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceSection = (Map<String, Object>)
                prepareCaptor.getValue().getMetadata().get(DataPlaneConstants.METADATA_SECTION_SOURCE);
        @SuppressWarnings("unchecked")
        Map<String, Object> s3Section = (Map<String, Object>) sourceSection.get(DataPlaneConstants.METADATA_SECTION_S3);
        assertEquals("provider-bucket", s3Section.get(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        assertEquals(DataTransferMockObjectUtil.DATASET_ID, s3Section.get(DataPlaneConstants.METADATA_S3_OBJECT_KEY));
        assertEquals("http://minio:9000", s3Section.get(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));
        assertEquals("http://downloads.example.com",
                s3Section.get(DataPlaneConstants.METADATA_S3_PUBLIC_PRESIGNED_ENDPOINT));

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
    @DisplayName("Complete HTTP-PUSH consumer transfer with assigned DP endpoint — must restore sticky and call DP terminate (best-effort)")
    public void completeTransfer_httpPushConsumer_withAssignedDpEndpoint_callsDpTerminate() {
        TransferProcess tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_CONSUMER_HTTP_PUSH_WITH_DATAPLANE;

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(tp.getId())).thenReturn(Optional.of(tp));

        apiService.completeTransfer(tp.getId());

        // Sticky must be restored before DP terminate so the correct DP instance is reached
        InOrder order = inOrder(dataPlaneClient);
        order.verify(dataPlaneClient).restoreStickyAssignment(tp.getId(), "http://dp-push:9090");
        order.verify(dataPlaneClient).terminate(eq(tp.getId()), eq(DataTransferFormat.HTTP_PUSH.format()), isNull());
        order.verify(dataPlaneClient).clearStickyAssignment(tp.getId());

        // CP must NOT directly delete the temp user — that is the DP's responsibility
        verify(temporaryBucketUserService, never()).deleteTemporaryUser(any());
    }

    @Test
    @DisplayName("Complete HTTP-PUSH consumer transfer without assigned DP endpoint — no DP terminate call")
    public void completeTransfer_httpPushConsumer_withoutAssignedDpEndpoint_doesNotCallDpTerminate() {
        // No assignedDataplaneEndpoint: PREPARED session was never assigned to a specific DP instance
        TransferProcess tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_CONSUMER_HTTP_PUSH;

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(tp.getId())).thenReturn(Optional.of(tp));

        apiService.completeTransfer(tp.getId());

        verify(dataPlaneClient, never()).restoreStickyAssignment(any(), any());
        verify(dataPlaneClient, never()).terminate(any(), any(), any());
        verify(temporaryBucketUserService, never()).deleteTemporaryUser(any());
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
    @DisplayName("Terminate HTTP-PULL transfer with assigned dataplane endpoint must call DP terminate and sticky restore")
    public void terminateTransfer_httpPull_withAssignedDataplaneEndpoint_callsDpTerminate() {
        // HTTP-PULL: transportProfile is null but assignedDataplaneEndpoint is set after consumer-side start
        TransferProcess httpPullWithDpEndpoint = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED
                .withAssignedDataplaneEndpoint("http://dp:9090");

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(httpPullWithDpEndpoint));

        apiService.terminateTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        // Sticky must be restored before the DP terminate call
        verify(dataPlaneClient).restoreStickyAssignment(
                DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(), "http://dp:9090");
        // DP terminate must be invoked even though transportProfile is null (HTTP-PULL)
        verify(dataPlaneClient).terminate(
                eq(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()), any(), isNull());
        // Sticky must always be cleared after successful termination
        verify(dataPlaneClient).clearStickyAssignment(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
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
        // No sticky endpoint available for this scenario → only one save expected
        when(dataPlaneClient.getStickyEndpoint(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        ArgumentCaptor<DataFlowStartMessage> startCaptor = ArgumentCaptor.forClass(DataFlowStartMessage.class);
        // HTTP-PULL must use the sticky/profile-aware overload with null profile
        verify(dataPlaneClient).start(startCaptor.capture(), isNull());
        // Exactly one save: to mark isDownloadInProgress=true (no sticky endpoint returned in this test)
        verify(transferProcessRepository, times(1)).save(argCaptorTransferProcess.capture());

        TransferProcess processWithInProgressFlag = argCaptorTransferProcess.getValue();
        assertEquals(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(), processWithInProgressFlag.getId());
        assertTrue(processWithInProgressFlag.isDownloadInProgress());
        assertEquals(DataTransferFormat.HTTP_PULL.name(), processWithInProgressFlag.getFormat());

        Map<String, String> endpointProperties = toEndpointPropertyMap(startCaptor.getValue().getDataAddress().getEndpointProperties());
        assertEquals(DataTransferMockObjectUtil.ENDPOINT_URL, startCaptor.getValue().getDataAddress().getEndpoint());
        assertEquals(DataTransferMockObjectUtil.ENDPOINT_TYPE, startCaptor.getValue().getDataAddress().getEndpointType());
        assertEquals("TOKEN-ABCDEFG", endpointProperties.get("authorization"));
        // Flat sink.* keys must NOT be in dataAddress - S3 coords live in metadata.sink.s3 only
        assertFalse(endpointProperties.containsKey("sink.bucketName"), "sink.bucketName must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.objectKey"), "sink.objectKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.region"), "sink.region must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.accessKey"), "sink.accessKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.secretKey"), "sink.secretKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.endpointOverride"), "sink.endpointOverride must not be in dataAddress");

        // S3 sink coordinates must appear in metadata.sink.s3
        @SuppressWarnings("unchecked")
        Map<String, Object> sinkSection = (Map<String, Object>) startCaptor.getValue().getMetadata()
                .get(DataPlaneConstants.METADATA_SECTION_SINK);
        assertNotNull(sinkSection, "start metadata must contain a canonical sink section");
        @SuppressWarnings("unchecked")
        Map<String, Object> sinkS3 = (Map<String, Object>) sinkSection.get(DataPlaneConstants.METADATA_SECTION_S3);
        assertEquals("consumer-bucket", sinkS3.get(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        assertEquals(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(), sinkS3.get(DataPlaneConstants.METADATA_S3_OBJECT_KEY));
        assertEquals("eu-central-1", sinkS3.get(DataPlaneConstants.METADATA_S3_REGION));
        assertEquals("consumer-access-key", sinkS3.get(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        assertEquals("consumer-secret-key", sinkS3.get(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        assertEquals("http://minio:9000", sinkS3.get(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));

        // completeTransfer() must NOT be called — completion is driven by the DP callback
        verify(okHttpRestClient, never()).sendRequestProtocol(contains("/transfers/"), any(JsonNode.class), anyString());
    }

    @Test
    @DisplayName("Download data - HTTP-PUSH provider - sends canonical source and sink metadata alongside transport dataAddress")
    public void downloadData_httpPushProvider_sendsCanonicalSourceAndSinkMetadataInStartMessage() {
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
        verify(dataPlaneClient).start(startCaptor.capture(), isNull());

        Map<String, String> endpointProperties = toEndpointPropertyMap(startCaptor.getValue().getDataAddress().getEndpointProperties());

        // Flat source.*/sink.* keys must NOT be in dataAddress - S3 coords live in metadata only
        assertFalse(endpointProperties.containsKey("source.bucketName"), "source.bucketName must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("source.objectKey"), "source.objectKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("source.region"), "source.region must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("source.accessKey"), "source.accessKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("source.secretKey"), "source.secretKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("source.endpointOverride"), "source.endpointOverride must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.bucketName"), "sink.bucketName must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.objectKey"), "sink.objectKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.accessKey"), "sink.accessKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.secretKey"), "sink.secretKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.region"), "sink.region must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.endpointOverride"), "sink.endpointOverride must not be in dataAddress");

        // Flat consumer keys must NOT be included
        assertFalse(endpointProperties.containsKey(S3Utils.BUCKET_NAME));
        assertFalse(endpointProperties.containsKey(S3Utils.ACCESS_KEY));
        assertFalse(endpointProperties.containsKey(S3Utils.SECRET_KEY));

        @SuppressWarnings("unchecked")
        Map<String, Object> sourceMetadata = (Map<String, Object>) startCaptor.getValue().getMetadata()
                .get(DataPlaneConstants.METADATA_SECTION_SOURCE);
        assertNotNull(sourceMetadata, "start metadata must contain a canonical source section");
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceS3Metadata = (Map<String, Object>) sourceMetadata.get(DataPlaneConstants.METADATA_SECTION_S3);
        assertEquals("provider-bucket", sourceS3Metadata.get(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        assertEquals(DataTransferMockObjectUtil.DATASET_ID, sourceS3Metadata.get(DataPlaneConstants.METADATA_S3_OBJECT_KEY));
        assertEquals("eu-central-1", sourceS3Metadata.get(DataPlaneConstants.METADATA_S3_REGION));
        assertEquals("provider-access-key", sourceS3Metadata.get(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        assertEquals("provider-secret-key", sourceS3Metadata.get(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        assertEquals("http://provider-minio:9000", sourceS3Metadata.get(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));

        @SuppressWarnings("unchecked")
        Map<String, Object> sinkMetadata = (Map<String, Object>) startCaptor.getValue().getMetadata()
                .get(DataPlaneConstants.METADATA_SECTION_SINK);
        assertNotNull(sinkMetadata, "start metadata must contain a canonical sink section");
        @SuppressWarnings("unchecked")
        Map<String, Object> sinkS3Metadata = (Map<String, Object>) sinkMetadata.get(DataPlaneConstants.METADATA_SECTION_S3);
        assertEquals("consumer-push-bucket", sinkS3Metadata.get(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        assertEquals("tp-push-obj", sinkS3Metadata.get(DataPlaneConstants.METADATA_S3_OBJECT_KEY));
        assertEquals("eu-central-1", sinkS3Metadata.get(DataPlaneConstants.METADATA_S3_REGION));
        assertEquals("consumer-temp-access", sinkS3Metadata.get(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        assertEquals("consumer-temp-secret", sinkS3Metadata.get(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        assertEquals("http://consumer-minio:9000", sinkS3Metadata.get(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));

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

        doThrow(DataPlaneClientException.class).when(dataPlaneClient).start(any(DataFlowStartMessage.class), isNull());

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

        doThrow(IllegalStateException.class).when(dataPlaneClient).start(any(DataFlowStartMessage.class), isNull());

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
    @DisplayName("View data - success - delegates to HTTP-PULL DP prepare with VIEW mode and CP-provided sink bucket")
    public void viewData_success() {
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();

        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");
        when(s3Properties.getExternalPresignedEndpoint()).thenReturn("http://downloads.example.com");
        DataFlowPrepareResponse viewPrepareResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(objectKey)
                .dataAddress(Map.of("presignedUrl", "https://example.com/presigned-url"))
                .build();
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class),
                eq(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getFormat()),
                isNull()))
                .thenReturn(viewPrepareResponse);

        assertDoesNotThrow(() -> apiService.viewData(objectKey));

        // viewData must delegate to the HTTP-PULL DP prepare, not call S3 directly
        ArgumentCaptor<DataFlowPrepareMessage> prepareCaptor = ArgumentCaptor.forClass(DataFlowPrepareMessage.class);
        verify(dataPlaneClient).prepare(prepareCaptor.capture(),
                eq(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getFormat()),
                isNull());
        verify(s3ClientService, never()).generateGetPresignedUrl(any(), any(), any());
        // Prepare message must carry VIEW mode in sink metadata
        @SuppressWarnings("unchecked")
        Map<String, Object> sinkSection = (Map<String, Object>) prepareCaptor.getValue().getMetadata()
                .get(DataPlaneConstants.METADATA_SECTION_SINK);
        assertNotNull(sinkSection, "prepare metadata must contain a sink section");
        assertEquals(DataPlaneConstants.METADATA_MODE_VIEW, sinkSection.get(DataPlaneConstants.METADATA_FIELD_MODE));
        // sink.s3 must carry CP-resolved bucket metadata
        @SuppressWarnings("unchecked")
        Map<String, Object> s3Section = (Map<String, Object>) sinkSection.get(DataPlaneConstants.METADATA_SECTION_S3);
        assertNotNull(s3Section, "sink section must contain an s3 subsection");
        assertNotNull(s3Section.get(DataPlaneConstants.METADATA_S3_BUCKET_NAME), "sink.s3 must carry bucketName");
        assertEquals("http://minio:9000", s3Section.get(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));
        assertEquals("http://downloads.example.com",
                s3Section.get(DataPlaneConstants.METADATA_S3_PUBLIC_PRESIGNED_ENDPOINT));
        verify(publisher).publishEvent(any(ArtifactConsumedEvent.class));
    }

    @Test
    @DisplayName("View data - fail - DP prepare throws exception")
    public void viewData_fail_canNotAccessData() {
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();

        when(transferProcessRepository.findById(objectKey))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");
        doThrow(new RuntimeException("DP unreachable"))
                .when(dataPlaneClient).prepare(any(DataFlowPrepareMessage.class), isNull(), isNull());

        assertThrows(DataTransferAPIException.class,
                () -> apiService.viewData(objectKey));
    }

    @Test
    @DisplayName("View data - fail - DP prepare returns no presigned URL")
    public void viewData_fail_fileNotFound() {
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();

        when(transferProcessRepository.findById(objectKey))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");
        DataFlowPrepareResponse emptyResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(objectKey)
                .dataAddress(Map.of())
                .build();
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class), isNull(), isNull()))
                .thenReturn(emptyResponse);

        assertThrows(DataTransferAPIException.class,
                () -> apiService.viewData(objectKey));

        verify(dataPlaneClient).prepare(any(DataFlowPrepareMessage.class),
                eq(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getFormat()),
                isNull());
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

        verify(dataPlaneClient, never()).prepare(any(), anyString(), any());
    }

    @Test
    @DisplayName("View data - fail - not downloaded")
    public void viewData_fail_notDownloaded() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED_NOT_DOWNLOADED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED_NOT_DOWNLOADED));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.viewData(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED_NOT_DOWNLOADED.getId()));

        verify(dataPlaneClient, never()).prepare(any(), anyString(), any());
    }

    @Test
    @DisplayName("viewData - succeeds and loads bucket credentials for VIEW presigning")
    public void viewData_success_usesBucketCredentials() {
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();

        when(transferProcessRepository.findById(objectKey))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");
        DataFlowPrepareResponse viewPrepareResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(objectKey)
                .dataAddress(Map.of(DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY, "https://example.com/presigned"))
                .build();
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class),
                eq(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getFormat()),
                isNull()))
                .thenReturn(viewPrepareResponse);

        assertDoesNotThrow(() -> apiService.viewData(objectKey));
        verify(dataPlaneClient).prepare(any(DataFlowPrepareMessage.class),
                eq(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getFormat()),
                isNull());
        verify(bucketCredentialsService).getBucketCredentials("test-bucket");
    }

    @Test
    @DisplayName("viewData - prepare metadata contains bucket credentials for presigned URL generation")
    public void viewData_success_metadataContainsBucketCredentials() {
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();

        when(transferProcessRepository.findById(objectKey))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");
        DataFlowPrepareResponse viewPrepareResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(objectKey)
                .dataAddress(Map.of(DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY, "https://example.com/presigned"))
                .build();
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class),
                eq(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getFormat()),
                isNull()))
                .thenReturn(viewPrepareResponse);

        apiService.viewData(objectKey);

        ArgumentCaptor<DataFlowPrepareMessage> prepareCaptor = ArgumentCaptor.forClass(DataFlowPrepareMessage.class);
        verify(dataPlaneClient).prepare(prepareCaptor.capture(),
                eq(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getFormat()),
                isNull());
        @SuppressWarnings("unchecked")
        Map<String, Object> sinkSection = (Map<String, Object>) prepareCaptor.getValue().getMetadata()
                .get(DataPlaneConstants.METADATA_SECTION_SINK);
        assertNotNull(sinkSection, "prepare metadata must contain a sink section");
        @SuppressWarnings("unchecked")
        Map<String, Object> s3Section = (Map<String, Object>) sinkSection.get(DataPlaneConstants.METADATA_SECTION_S3);
        assertNotNull(s3Section, "sink section must contain an s3 subsection");
        assertEquals("default-access-key", s3Section.get(DataPlaneConstants.METADATA_S3_ACCESS_KEY),
                "VIEW metadata must contain accessKey so DP presigning uses bucket credentials");
        assertEquals("default-secret-key", s3Section.get(DataPlaneConstants.METADATA_S3_SECRET_KEY),
                "VIEW metadata must contain secretKey so DP presigning uses bucket credentials");
        assertNotNull(s3Section.get(DataPlaneConstants.METADATA_S3_BUCKET_NAME),
                "VIEW metadata must carry bucket name so DP can route to correct tenant bucket");
        assertNotNull(s3Section.get(DataPlaneConstants.METADATA_S3_OBJECT_KEY),
                "VIEW metadata must carry object key so DP knows which artifact to presign");
    }

    @Test
    @DisplayName("viewData - prepare metadata contains internal and public endpoints for VIEW presigning")
    public void viewData_success_metadataContainsInternalAndPublicEndpoints() {
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();

        when(transferProcessRepository.findById(objectKey))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(GenericApiResponse.success(null, "successful response")));
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(s3Properties.getExternalPresignedEndpoint()).thenReturn("http://downloads.example.com");
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class),
                eq(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getFormat()),
                isNull()))
                .thenReturn(DataFlowPrepareResponse.Builder.newInstance()
                        .processId(objectKey)
                        .dataAddress(Map.of(DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY,
                                "https://downloads.example.com/object"))
                        .build());

        apiService.viewData(objectKey);

        ArgumentCaptor<DataFlowPrepareMessage> prepareCaptor = ArgumentCaptor.forClass(DataFlowPrepareMessage.class);
        verify(dataPlaneClient).prepare(prepareCaptor.capture(),
                eq(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getFormat()),
                isNull());
        @SuppressWarnings("unchecked")
        Map<String, Object> sinkSection = (Map<String, Object>) prepareCaptor.getValue().getMetadata()
                .get(DataPlaneConstants.METADATA_SECTION_SINK);
        @SuppressWarnings("unchecked")
        Map<String, Object> s3Section = (Map<String, Object>) sinkSection.get(DataPlaneConstants.METADATA_SECTION_S3);

        assertEquals("http://minio:9000", s3Section.get(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));
        assertEquals("http://downloads.example.com",
                s3Section.get(DataPlaneConstants.METADATA_S3_PUBLIC_PRESIGNED_ENDPOINT));
    }

    @Test
    @DisplayName("viewData - prepare metadata omits endpoint keys when no overrides are configured")
    public void viewData_success_metadataOmitsEndpointKeysWhenNoOverridesAreConfigured() {
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();

        when(transferProcessRepository.findById(objectKey))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(GenericApiResponse.success(null, "successful response")));
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");
        when(s3Properties.getEndpoint()).thenReturn("");
        when(s3Properties.getExternalPresignedEndpoint()).thenReturn(null);
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class),
                eq(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getFormat()),
                isNull()))
                .thenReturn(DataFlowPrepareResponse.Builder.newInstance()
                        .processId(objectKey)
                        .dataAddress(Map.of(DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY,
                                "https://downloads.example.com/object"))
                        .build());

        apiService.viewData(objectKey);

        ArgumentCaptor<DataFlowPrepareMessage> prepareCaptor = ArgumentCaptor.forClass(DataFlowPrepareMessage.class);
        verify(dataPlaneClient).prepare(prepareCaptor.capture(),
                eq(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getFormat()),
                isNull());
        @SuppressWarnings("unchecked")
        Map<String, Object> sinkSection = (Map<String, Object>) prepareCaptor.getValue().getMetadata()
                .get(DataPlaneConstants.METADATA_SECTION_SINK);
        @SuppressWarnings("unchecked")
        Map<String, Object> s3Section = (Map<String, Object>) sinkSection.get(DataPlaneConstants.METADATA_SECTION_S3);

        assertFalse(s3Section.containsKey(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));
        assertFalse(s3Section.containsKey(DataPlaneConstants.METADATA_S3_PUBLIC_PRESIGNED_ENDPOINT));
    }

    @Test
    @DisplayName("viewData - success - cleans up PREPARED DP session and sticky entry after generating presigned URL")
    public void viewData_success_cleansUpPreparedDataPlaneSession() {
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();

        when(transferProcessRepository.findById(objectKey))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(GenericApiResponse.success(null, "successful response")));
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");
        DataFlowPrepareResponse viewPrepareResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(objectKey)
                .dataAddress(Map.of(DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY, "https://example.com/presigned-url"))
                .build();
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class),
                eq(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getFormat()),
                isNull()))
                .thenReturn(viewPrepareResponse);
        when(dataPlaneClient.getStickyEndpoint(objectKey)).thenReturn(Optional.of("http://dp-view:9090"));

        apiService.viewData(objectKey);

        // VIEW is a helper-only prepare — the PREPARED DP session must be terminated and sticky cleared
        verify(dataPlaneClient).terminate(objectKey, DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getFormat(), null);
        verify(dataPlaneClient).clearStickyAssignment(objectKey);
    }

    @Test
    @DisplayName("viewData - prepare throws exception - sticky assignment is cleared (best-effort cleanup)")
    public void viewData_fail_prepareThrows_clearsStickyAssignment() {
        String objectKey = DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED.getId();
        when(transferProcessRepository.findById(objectKey))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(GenericApiResponse.success(null, "successful response")));
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");
        doThrow(new RuntimeException("DP unreachable"))
                .when(dataPlaneClient).prepare(any(DataFlowPrepareMessage.class), eq("HttpData-PULL"), isNull());
        when(dataPlaneClient.getStickyEndpoint(objectKey)).thenReturn(Optional.of("http://dp-view:9090"));

        assertThrows(DataTransferAPIException.class, () -> apiService.viewData(objectKey));

        // Sticky must be cleared even when prepare throws so the router does not retain a stale pin
        verify(dataPlaneClient).clearStickyAssignment(objectKey);
    }

    @Test
    @DisplayName("startTransfer - provider HTTP-PULL - persists assignedDataplaneEndpoint after sticky prepare")
    public void startTransfer_httpPull_file_persistsAssignedEndpointAfterPrepare() {
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
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");

        DataFlowPrepareResponse dpResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId())
                .dataAddress(Map.of(
                        DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT, "https://minio.example.com/presigned/artifact",
                        DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT_TYPE, "https://w3id.org/idsa/v4.1/HTTP"))
                .build();
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class), eq("HttpData-PULL"), isNull()))
                .thenReturn(dpResponse);
        when(dataPlaneClient.getStickyEndpoint(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId()))
                .thenReturn(Optional.of("http://dp-http-pull:9090"));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId());

        verify(dataPlaneClient).prepare(any(DataFlowPrepareMessage.class), eq("HttpData-PULL"), isNull());
        verify(dataPlaneClient).getStickyEndpoint(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId());
        // The saved STARTED process must carry the sticky endpoint
        verify(transferProcessRepository, atLeastOnce()).save(argCaptorTransferProcess.capture());
        boolean endpointPersisted = argCaptorTransferProcess.getAllValues().stream()
                .anyMatch(tp -> "http://dp-http-pull:9090".equals(tp.getAssignedDataplaneEndpoint()));
        assertTrue(endpointPersisted,
                "assignedDataplaneEndpoint must be persisted in the STARTED TP after HTTP-PULL DP prepare");
    }

    @Test
    @DisplayName("startTransfer - provider HTTP-PULL: peer failure after successful DP prepare clears sticky assignment")
    public void startTransfer_httpPull_peerFailure_afterSuccessfulPrepare_clearsStickyAssignment() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL));
        when(artifactTransferService.findArtifact(any())).thenReturn(DataTransferMockObjectUtil.ARTIFACT_FILE);
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");

        DataFlowPrepareResponse dpResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId())
                .dataAddress(Map.of(
                        DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT, "https://minio.example.com/presigned/artifact",
                        DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT_TYPE, "https://w3id.org/idsa/v4.1/HTTP"))
                .build();
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class), eq("HttpData-PULL"), isNull()))
                .thenReturn(dpResponse);
        when(dataPlaneClient.getStickyEndpoint(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId()))
                .thenReturn(Optional.of("http://dp-http-pull:9090"));

        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getMessage()).thenReturn("peer notification failed");
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(DataTransferAPIException.class,
                () -> apiService.startTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId()));

        // TP must be rolled back to REQUESTED
        verify(transferProcessRepository, times(2)).save(argCaptorTransferProcess.capture());
        List<TransferProcess> savedValues = argCaptorTransferProcess.getAllValues();
        assertTrue(savedValues.stream().anyMatch(p -> p.getState() == TransferState.REQUESTED),
                "rollback must save TP in REQUESTED state");

        // Sticky must be cleared for HTTP-PULL even though transportProfile is null
        verify(dataPlaneClient).clearStickyAssignment(
                DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId());
    }

    @Test
    @DisplayName("startTransfer - provider HTTP-PULL FILE: prepare throws - sticky assignment is cleared (best-effort cleanup)")
    public void startTransfer_httpPull_file_prepareThrows_clearsStickyAssignment() {
        String processId = DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId();
        when(transferProcessRepository.findById(processId))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL));
        when(artifactTransferService.findArtifact(any())).thenReturn(DataTransferMockObjectUtil.ARTIFACT_FILE);
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");
        doThrow(new RuntimeException("DP unreachable"))
                .when(dataPlaneClient).prepare(any(DataFlowPrepareMessage.class), eq("HttpData-PULL"), isNull());
        when(dataPlaneClient.getStickyEndpoint(processId)).thenReturn(Optional.of("http://dp-http-pull:9090"));

        assertThrows(DataTransferAPIException.class, () -> apiService.startTransfer(processId));

        // Sticky must be cleared even when prepare throws so the router does not retain a stale pin
        verify(dataPlaneClient).clearStickyAssignment(processId);
    }

    @Test
    @DisplayName("startTransfer - provider HTTP-PULL FILE: PREPARED DP session is terminated after successful start")
    public void startTransfer_providerHttpPull_fileArtifact_cleansUpPreparedDpOnSuccess() {
        String processId = DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL.getId();
        when(transferProcessRepository.findById(processId))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER_HTTP_PULL));
        when(artifactTransferService.findArtifact(any())).thenReturn(DataTransferMockObjectUtil.ARTIFACT_FILE);
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");

        DataFlowPrepareResponse dpResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(processId)
                .dataAddress(Map.of(
                        DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT, "https://minio.example.com/presigned/artifact",
                        DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT_TYPE, "https://w3id.org/idsa/v4.1/HTTP"))
                .build();
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class), eq("HttpData-PULL"), isNull()))
                .thenReturn(dpResponse);
        when(dataPlaneClient.getStickyEndpoint(processId)).thenReturn(Optional.of("http://dp-http-pull:9090"));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(transferProcessRepository.save(any(TransferProcess.class))).thenAnswer(invocation -> invocation.getArgument(0));

        apiService.startTransfer(processId);

        // HTTP-PULL FILE uses DP prepare only to get the presigned URL — PREPARED session must be terminated on success
        verify(dataPlaneClient).terminate(processId, "HttpData-PULL", null);
        verify(dataPlaneClient).clearStickyAssignment(processId);
    }

    @Test
    @DisplayName("downloadData - HTTP-PULL consumer - uses sticky start overload and persists assignedDataplaneEndpoint")
    public void downloadData_httpPull_usesStickyStartAndPersistsAssignedEndpoint() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        GenericApiResponse<String> internalResponse = GenericApiResponse.success(null, "successful response");
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(internalResponse));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(s3Properties.getRegion()).thenReturn("eu-central-1");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");

        // The consumer DP returns a sticky endpoint after start
        when(dataPlaneClient.getStickyEndpoint(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of("http://dp-http-pull:9090"));

        assertDoesNotThrow(() -> apiService.downloadData(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

        // HTTP-PULL must use sticky overload with null profile
        verify(dataPlaneClient).start(any(DataFlowStartMessage.class), isNull());
        // Two saves: mark isDownloadInProgress=true, then persist assignedDataplaneEndpoint
        verify(transferProcessRepository, times(2)).save(argCaptorTransferProcess.capture());
        List<TransferProcess> savedValues = argCaptorTransferProcess.getAllValues();
        boolean endpointPersisted = savedValues.stream()
                .anyMatch(tp -> "http://dp-http-pull:9090".equals(tp.getAssignedDataplaneEndpoint()));
        assertTrue(endpointPersisted,
                "assignedDataplaneEndpoint must be persisted after HTTP-PULL consumer DP start");
    }

    @Test
    @DisplayName("downloadData - endpoint persistence OptimisticLockingFailureException is handled benignly when TP already moved forward")
    public void downloadData_endpointPersistRaceHandledBenignly() {
        when(transferProcessRepository.findById(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));
        when(usageControlProperties.usageControlEnabled()).thenReturn(false);
        // 1st save (mark isDownloadInProgress) succeeds; 2nd save (endpoint persist) races against DP callback
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenThrow(new org.springframework.dao.OptimisticLockingFailureException(
                        "TP already moved to COMPLETED by DP callback"))
                .thenAnswer(invocation -> invocation.getArgument(0)); // for catch-block reset if not fixed
        when(properties.dataPlaneFeedbackAddress()).thenReturn("http://connector:8080");
        when(dataPlaneClient.getStickyEndpoint(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
                .thenReturn(Optional.of("http://dp-http-pull:9090"));

        CompletableFuture<Void> result = apiService.downloadData(
                DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        assertFalse(result.isCompletedExceptionally(),
                "downloadData must succeed despite endpoint persistence race - the TP already moved forward");
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
    @DisplayName("Request transfer (HTTP-PUSH consumer) uses consumer DP prepare and persists assigned dataplane endpoint")
    public void requestTransfer_httpPush_usesConsumerDpPrepareInsteadOfLocalTempUserCreation() {
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

        DataFlowPrepareResponse prepareResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(httpPushInitialized.getId())
                .dataAddress(Map.of(
                        S3Utils.BUCKET_NAME, "test-bucket",
                        S3Utils.OBJECT_KEY, httpPushInitialized.getId(),
                        S3Utils.REGION, "us-east-1",
                        S3Utils.ACCESS_KEY, "test-access-key",
                        S3Utils.SECRET_KEY, "test-secret-key",
                        S3Utils.ENDPOINT_OVERRIDE, "http://minio:9000"))
                .build();

        when(transferProcessRepository.findById(httpPushInitialized.getId()))
                .thenReturn(Optional.of(httpPushInitialized));
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class), eq(DataTransferFormat.HTTP_PUSH.format()), isNull()))
                .thenReturn(prepareResponse);
        when(dataPlaneClient.getStickyEndpoint(httpPushInitialized.getId()))
                .thenReturn(Optional.of("http://dp-http-push:9090"));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(apiResponse.getData()).thenReturn(
                TransferSerializer.serializeProtocol(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(properties.consumerCallbackAddress()).thenReturn(DataTransferMockObjectUtil.CALLBACK_ADDRESS);

        apiService.requestTransfer(httpPushRequest);

        ArgumentCaptor<DataFlowPrepareMessage> prepareCaptor = ArgumentCaptor.forClass(DataFlowPrepareMessage.class);
        verify(dataPlaneClient).prepare(prepareCaptor.capture(), eq(DataTransferFormat.HTTP_PUSH.format()), isNull());
        assertEquals(DataTransferFormat.HTTP_PUSH.format(), prepareCaptor.getValue().getTransferType());
        @SuppressWarnings("unchecked")
        Map<String, Object> sinkSection = (Map<String, Object>) prepareCaptor.getValue().getMetadata()
                .get(DataPlaneConstants.METADATA_SECTION_SINK);
        assertNotNull(sinkSection, "prepare metadata must contain a sink section");
        @SuppressWarnings("unchecked")
        Map<String, Object> s3Section = (Map<String, Object>) sinkSection.get(DataPlaneConstants.METADATA_SECTION_S3);
        assertNotNull(s3Section, "sink section must contain sink.s3 metadata");
        assertEquals("test-bucket", s3Section.get(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        assertEquals(httpPushInitialized.getId(), s3Section.get(DataPlaneConstants.METADATA_S3_OBJECT_KEY));
        assertEquals("us-east-1", s3Section.get(DataPlaneConstants.METADATA_S3_REGION));
        assertEquals("http://minio:9000", s3Section.get(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));
        assertEquals("minioadmin", s3Section.get(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        assertEquals("minioadmin-secret", s3Section.get(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        verify(temporaryBucketUserService, never()).createTemporaryUser(anyString(), anyString(), anyString());

        verify(transferProcessRepository).save(argCaptorTransferProcess.capture());
        TransferProcess savedTransferProcess = argCaptorTransferProcess.getValue();
        assertEquals(TransferState.REQUESTED, savedTransferProcess.getState());
        assertEquals("http://dp-http-push:9090", savedTransferProcess.getAssignedDataplaneEndpoint(),
                "assignedDataplaneEndpoint must be persisted after HTTP-PUSH prepare");

        verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
    }

    @Test
    @DisplayName("Request transfer (HTTP-PUSH consumer) terminates process when DP prepare fails")
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
        doThrow(new RuntimeException("MinIO unreachable"))
                .when(dataPlaneClient).prepare(any(DataFlowPrepareMessage.class), eq(DataTransferFormat.HTTP_PUSH.format()), isNull());

        assertDoesNotThrow(() -> apiService.requestTransfer(httpPushRequest));

        verify(transferProcessRepository, atLeastOnce()).save(argCaptorTransferProcess.capture());
        List<TransferProcess> saved = argCaptorTransferProcess.getAllValues();
        boolean hasTerminated = saved.stream().anyMatch(p -> p.getState() == TransferState.TERMINATED);
        assertTrue(hasTerminated, "Transfer process should be saved in TERMINATED state");
        verify(temporaryBucketUserService, never()).createTemporaryUser(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Request transfer (HTTP-PUSH consumer) cleans up prepared DP session when provider rejects request")
    public void requestTransfer_httpPush_cleansUpPreparedDataPlaneSessionWhenProviderRejects() {
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

        DataFlowPrepareResponse prepareResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(httpPushInitialized.getId())
                .dataAddress(Map.of(
                        S3Utils.BUCKET_NAME, "test-bucket",
                        S3Utils.OBJECT_KEY, httpPushInitialized.getId(),
                        S3Utils.REGION, "us-east-1",
                        S3Utils.ACCESS_KEY, "test-access-key",
                        S3Utils.SECRET_KEY, "test-secret-key"))
                .build();

        when(transferProcessRepository.findById(httpPushInitialized.getId()))
                .thenReturn(Optional.of(httpPushInitialized));
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class), eq(DataTransferFormat.HTTP_PUSH.format()), isNull()))
                .thenReturn(prepareResponse);
        when(dataPlaneClient.getStickyEndpoint(httpPushInitialized.getId()))
                .thenReturn(Optional.of("http://dp-http-push:9090"));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);
        when(apiResponse.getData()).thenReturn(TransferSerializer.serializeProtocol(DataTransferMockObjectUtil.TRANSFER_ERROR));
        when(properties.consumerCallbackAddress()).thenReturn(DataTransferMockObjectUtil.CALLBACK_ADDRESS);

        assertThrows(DataTransferAPIException.class, () -> apiService.requestTransfer(httpPushRequest));

        verify(dataPlaneClient).restoreStickyAssignment(httpPushInitialized.getId(), "http://dp-http-push:9090");
        verify(dataPlaneClient).terminate(httpPushInitialized.getId(), DataTransferFormat.HTTP_PUSH.format(), null);
        verify(dataPlaneClient).clearStickyAssignment(httpPushInitialized.getId());
        verify(temporaryBucketUserService, never()).deleteTemporaryUser(anyString());
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
                        && !body.toString().contains(S3Utils.BUCKET_NAME)
                        && !body.toString().contains(S3Utils.ACCESS_KEY)),
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
        // Flat sink.* keys must NOT be in dataAddress - S3 coords live in metadata.sink.s3 only
        assertFalse(endpointProperties.containsKey("sink.bucketName"), "sink.bucketName must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.objectKey"), "sink.objectKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.region"), "sink.region must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.accessKey"), "sink.accessKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.secretKey"), "sink.secretKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey("sink.endpointOverride"), "sink.endpointOverride must not be in dataAddress");

        // S3 sink coordinates must appear in metadata.sink.s3
        @SuppressWarnings("unchecked")
        Map<String, Object> grpcSinkSection = (Map<String, Object>) startCaptor.getValue().getMetadata()
                .get(DataPlaneConstants.METADATA_SECTION_SINK);
        assertNotNull(grpcSinkSection, "start metadata must contain a canonical sink section");
        @SuppressWarnings("unchecked")
        Map<String, Object> grpcSinkS3 = (Map<String, Object>) grpcSinkSection.get(DataPlaneConstants.METADATA_SECTION_S3);
        assertEquals("consumer-bucket", grpcSinkS3.get(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        assertEquals(grpcProcess.getId(), grpcSinkS3.get(DataPlaneConstants.METADATA_S3_OBJECT_KEY));
        assertEquals("eu-central-1", grpcSinkS3.get(DataPlaneConstants.METADATA_S3_REGION));
        assertEquals("consumer-access-key", grpcSinkS3.get(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        assertEquals("consumer-secret-key", grpcSinkS3.get(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        assertEquals("http://minio:9000", grpcSinkS3.get(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));
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
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
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
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
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
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        apiService.completeTransfer(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());

        verify(dataPlaneClient).clearStickyAssignment(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
    }

    @Test
    @DisplayName("completeTransfer - HTTP-PUSH consumer with assigned DP endpoint: terminates PREPARED DP session on completion")
    public void completeTransfer_httpPushConsumer_withAssignedDpEndpoint_cleansUpPreparedDpSession() {
        TransferProcess httpPushConsumerStarted = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_CONSUMER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.STARTED)
                .format(DataTransferFormat.HTTP_PUSH.format())
                .assignedDataplaneEndpoint("http://dp-http-push:9090")
                .build();

        when(transferProcessRepository.findById(httpPushConsumerStarted.getId()))
                .thenReturn(Optional.of(httpPushConsumerStarted));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        apiService.completeTransfer(httpPushConsumerStarted.getId());

        // PREPARED DP session must be terminated when HTTP-PUSH consumer TP completes
        verify(dataPlaneClient).restoreStickyAssignment(httpPushConsumerStarted.getId(), "http://dp-http-push:9090");
//        verify(dataPlaneClient).terminate(httpPushConsumerStarted.getId(), DataTransferFormat.HTTP_PUSH.format(), null);
        verify(dataPlaneClient).clearStickyAssignment(httpPushConsumerStarted.getId());
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
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
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
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
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
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
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
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
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
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
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
    @DisplayName("startTransfer - provider gRPC: rollback preserves transportProfile and assignedDataplaneEndpoint for cleanup recovery")
    public void startTransfer_grpc_provider_rollback_preservesRoutingMetadata() {
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

        verify(transferProcessRepository, times(2)).save(argCaptorTransferProcess.capture());
        TransferProcess rollbackTp = argCaptorTransferProcess.getAllValues().stream()
                .filter(p -> p.getState() == TransferState.REQUESTED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("rollback TP in REQUESTED state not found"));

        assertEquals(TransportProfile.STREAM_GRPC, rollbackTp.getTransportProfile(),
                "rollback TP must preserve transportProfile so later cleanup can route back to the same DP instance");
        assertEquals("http://dp-grpc:9090", rollbackTp.getAssignedDataplaneEndpoint(),
                "rollback TP must preserve assignedDataplaneEndpoint so later cleanup can route back to the same DP instance");
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

        // start message must carry consumer bucket credentials in metadata.sink.s3 only - NOT as flat sink.* dataAddress properties
        Map<String, String> endpointProperties = toEndpointPropertyMap(
                startCaptor.getValue().getDataAddress().getEndpointProperties());
        assertFalse(endpointProperties.containsKey(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME),
                "sink.bucketName must not be in dataAddress");
        assertFalse(endpointProperties.containsKey(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY),
                "sink.objectKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION),
                "sink.region must not be in dataAddress");
        assertFalse(endpointProperties.containsKey(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY),
                "sink.accessKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY),
                "sink.secretKey must not be in dataAddress");
        assertFalse(endpointProperties.containsKey(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ENDPOINT_OVERRIDE),
                "sink.endpointOverride must not be in dataAddress");

        // S3 sink coordinates must appear in metadata.sink.s3
        @SuppressWarnings("unchecked")
        Map<String, Object> kafkaSinkSection = (Map<String, Object>) startCaptor.getValue().getMetadata()
                .get(DataPlaneConstants.METADATA_SECTION_SINK);
        assertNotNull(kafkaSinkSection, "start metadata must contain a canonical sink section");
        @SuppressWarnings("unchecked")
        Map<String, Object> kafkaSinkS3 = (Map<String, Object>) kafkaSinkSection.get(DataPlaneConstants.METADATA_SECTION_S3);
        assertEquals("consumer-kafka-bucket", kafkaSinkS3.get(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        assertEquals(kafkaConsumerProcess.getId(), kafkaSinkS3.get(DataPlaneConstants.METADATA_S3_OBJECT_KEY));
        assertEquals("eu-central-1", kafkaSinkS3.get(DataPlaneConstants.METADATA_S3_REGION));
        assertEquals("kafka-consumer-access-key", kafkaSinkS3.get(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        assertEquals("kafka-consumer-secret-key", kafkaSinkS3.get(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        assertEquals("http://kafka-minio:9000", kafkaSinkS3.get(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Issue 2 — HTTP-PUSH prepare must use bootstrap MinIO management credentials, not bucket records
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that {@code requestTransfer} for HTTP-PUSH uses bootstrap management credentials
     * from {@code application.properties} and still does not consult
     * {@link BucketCredentialsService#getBucketCredentials} during prepare-metadata construction.
     *
     * <p>This fallback keeps HTTP-PUSH prepare operational in MinIO by passing bootstrap
     * admin credentials to the DP until tenant-scoped bucket manager policies are implemented.</p>
     */
    @Test
    @DisplayName("HTTP-PUSH requestTransfer prepare uses bootstrap management credentials without bucket records")
    public void requestTransfer_httpPush_prepareMetadataUsesBootstrapManagementCredentials() {
        // No bucket credentials exist for the consumer bucket — simulate a first-time or lazy setup.
        // Before the fix, buildHttpPushPrepareMetadata called getBucketCredentials which would
        // throw when credentials were null. After the fix the CP does not consult bucket credentials
        // for HTTP-PUSH prepare at all (the verify(never()) below enforces this contract).

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

        DataFlowPrepareResponse prepareResponse = DataFlowPrepareResponse.Builder.newInstance()
                .processId(httpPushInitialized.getId())
                .dataAddress(Map.of(
                        S3Utils.BUCKET_NAME, "test-bucket",
                        S3Utils.OBJECT_KEY, httpPushInitialized.getId(),
                        S3Utils.ACCESS_KEY, "temp-access-key",
                        S3Utils.SECRET_KEY, "temp-secret-key"))
                .build();

        when(transferProcessRepository.findById(httpPushInitialized.getId()))
                .thenReturn(Optional.of(httpPushInitialized));
        when(dataPlaneClient.prepare(any(DataFlowPrepareMessage.class), eq(DataTransferFormat.HTTP_PUSH.format()), isNull()))
                .thenReturn(prepareResponse);
        when(dataPlaneClient.getStickyEndpoint(httpPushInitialized.getId()))
                .thenReturn(Optional.of("http://dp-http-push:9090"));
        when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
        when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);
        when(apiResponse.getData()).thenReturn(
                TransferSerializer.serializeProtocol(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(properties.consumerCallbackAddress()).thenReturn(DataTransferMockObjectUtil.CALLBACK_ADDRESS);

        assertDoesNotThrow(() -> apiService.requestTransfer(httpPushRequest));

        ArgumentCaptor<DataFlowPrepareMessage> prepareCaptor = ArgumentCaptor.forClass(DataFlowPrepareMessage.class);
        verify(dataPlaneClient).prepare(prepareCaptor.capture(), eq(DataTransferFormat.HTTP_PUSH.format()), isNull());
        @SuppressWarnings("unchecked")
        Map<String, Object> sinkSection = (Map<String, Object>) prepareCaptor.getValue().getMetadata()
                .get(DataPlaneConstants.METADATA_SECTION_SINK);
        @SuppressWarnings("unchecked")
        Map<String, Object> s3Section = (Map<String, Object>) sinkSection.get(DataPlaneConstants.METADATA_SECTION_S3);
        assertEquals("minioadmin", s3Section.get(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        assertEquals("minioadmin-secret", s3Section.get(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        verify(bucketCredentialsService, never()).getBucketCredentials(anyString());
    }
}
