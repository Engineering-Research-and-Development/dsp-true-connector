package it.eng.datatransfer.service.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
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
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.DataTransferRequest;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.model.ExternalData;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.usagecontrol.UsageControlProperties;
import it.eng.tools.util.CredentialUtils;

@ExtendWith(MockitoExtension.class)
class DataTransferAPIServiceTest {
	
	private static final String ATTACHMENT_FILENAME = "attachment;filename=\"";
	
	private MockHttpServletResponse mockHttpServletResponse;
	
	@Mock
	private ApplicationEventPublisher publisher;
	@Mock
	private UsageControlProperties usageControlProperties;
	@Mock
	private InputStream inputStream;
	@Mock
	private GridFSFindIterable gridFSFindIterable;
 	@Mock
 	private GridFSFile gridFSFile;
 	@Mock
 	private GridFSDownloadStream gridFSDownloadStream;
	@Mock
	private GridFSBucket gridFSBucket;
	@Mock
	private MongoDatabase mongoDatabase;
	@Mock
	private MongoTemplate mongoTemplate;
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
	
	@Captor
	private ArgumentCaptor<TransferProcess> argCaptorTransferProcess;
	@Captor
	private ArgumentCaptor<DataAddress> argCaptorDataAddress;
	
	@InjectMocks
	private DataTransferAPIService apiService;
	
	private DataTransferRequest dataTransferRequest = new DataTransferRequest(DataTranferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED.getId(),
			DataTransferFormat.HTTP_PULL.name(),
			null);
	
	@Test
	@DisplayName("Find transfer process by id, state and all")
	public void findDataTransfers() {

		when(transferProcessRepository.findById(anyString())).thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));
		Collection<JsonNode> response = apiService.findDataTransfers("test", TransferState.REQUESTED.name(), null);
		assertNotNull(response);
		assertEquals(response.size(), 1);

		when(transferProcessRepository.findById(anyString())).thenReturn(Optional.empty());
		response = apiService.findDataTransfers("test_not_found", null, null);
		assertNotNull(response);
		assertTrue(response.isEmpty());

		when(transferProcessRepository.findByStateAndRole(anyString(), anyString())).thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));
		response =  apiService.findDataTransfers(null, TransferState.STARTED.name(), IConstants.ROLE_PROVIDER);
		assertNotNull(response);
		assertEquals(response.size(), 1);
		
		when(transferProcessRepository.findByRole(anyString()))
				.thenReturn(Arrays.asList(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER, DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));
		response =  apiService.findDataTransfers(null, null, IConstants.ROLE_PROVIDER);
		assertNotNull(response);
		assertEquals(response.size(), 2);
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
		
		assertThrows(DataTransferAPIException.class, ()->
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
		
		assertThrows(DataTransferAPIException.class, ()->
			apiService.requestTransfer(dataTransferRequest));
		
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Start transfer process success")
	public void startTransfer_success_requestedState() throws UnsupportedEncodingException {
		when(credentialUtils.getConnectorCredentials()).thenReturn("credentials");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId()))
		.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));
		
		apiService.startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId());
		
		verify(transferProcessRepository).save(any(TransferProcess.class));
	}
	
	@Test
	@DisplayName("Start transfer process failed - transfer process not found")
	public void startTransfer_failedNegotiationNotFound() {
		assertThrows(DataTransferAPIException.class, ()-> apiService.startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId()));
		
		verify(okHttpRestClient, times(0)).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@ParameterizedTest
	@DisplayName("Start transfer process failed - wrong transfer process state")
	@MethodSource("startTransfer_wrongStates")
	public void startTransfer_wrongNegotiationState(TransferProcess input) {
		
		when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
				.thenReturn(Optional.of(input));

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
		
		assertThrows(DataTransferAPIException.class, ()-> apiService.startTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.getId()));
	
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
		assertThrows(DataTransferAPIException.class, ()-> apiService.completeTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
		
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
		
		assertThrows(DataTransferAPIException.class, ()-> apiService.completeTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
	
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
		assertThrows(DataTransferAPIException.class, ()-> apiService.suspendTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
		
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
		
		assertThrows(DataTransferAPIException.class, ()-> apiService.suspendTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
	
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
		assertThrows(DataTransferAPIException.class, ()-> apiService.terminateTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
		
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
		
		assertThrows(DataTransferAPIException.class, ()-> apiService.terminateTransfer(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
	
		verify(okHttpRestClient).sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class));
		verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
	}
	
	@Test
    @DisplayName("Download data - success")
    public void downloadData_success() throws IOException {
		ExternalData data = new ExternalData();
		data.setContentDisposition(ATTACHMENT_FILENAME + "file.txt\"");
		data.setContentType(okhttp3.MediaType.get(MediaType.TEXT_PLAIN_VALUE));
		data.setData("data".getBytes());
		GenericApiResponse<ExternalData> dataResponse = GenericApiResponse.success(data, ATTACHMENT_FILENAME);
		
		GenericApiResponse<String> internalResponse = GenericApiResponse.success("successfull response", ATTACHMENT_FILENAME);
		
		ObjectId objectId = new ObjectId();
		when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
		when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
		.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
		.thenReturn(TransferSerializer.serializePlain(internalResponse));
		when(okHttpRestClient.downloadData(any(), any()))
		.thenReturn(dataResponse);
		when(gridFSBucket.uploadFromStream(anyString(), any(InputStream.class), any(GridFSUploadOptions.class))).thenReturn(objectId);
		when(transferProcessRepository.save(any(TransferProcess.class)))
		.thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED);
		
		try (MockedStatic<GridFSBuckets> buckets = Mockito.mockStatic(GridFSBuckets.class)) {
			buckets.when(() -> GridFSBuckets.create(mongoTemplate.getDb()))
	          .thenReturn(gridFSBucket);

		assertDoesNotThrow(()->  apiService.downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
		
		}
    }
	
	@Test
    @DisplayName("Download data - fail - can not store data")
    public void downloadData_fail_canNotStoreData() throws IOException {
		ExternalData data = new ExternalData();
		data.setContentDisposition(ATTACHMENT_FILENAME + "file.txt\"");
		data.setContentType(okhttp3.MediaType.get(MediaType.TEXT_PLAIN_VALUE));
		data.setData(null);
		GenericApiResponse<ExternalData> dataResponse = GenericApiResponse.success(data, ATTACHMENT_FILENAME);
		
		GenericApiResponse<String> internalResponse = GenericApiResponse.success("successfull response", ATTACHMENT_FILENAME);
		
		when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
		when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
		.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
		.thenReturn(TransferSerializer.serializePlain(internalResponse));
		when(okHttpRestClient.downloadData(any(), any()))
		.thenReturn(dataResponse);
		
		try (MockedStatic<GridFSBuckets> buckets = Mockito.mockStatic(GridFSBuckets.class)) {
			buckets.when(() -> GridFSBuckets.create(mongoTemplate.getDb()))
	          .thenReturn(gridFSBucket);

			assertThrows(DataTransferAPIException.class, ()->  apiService.downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));
		
		}
    }
	
	@Test
    @DisplayName("Download data - fail - can not download data")
    public void downloadData_fail_canNotDownloadData() throws IOException {
		GenericApiResponse<ExternalData> dataResponse = GenericApiResponse.error("Fail to download");
		
		GenericApiResponse<String> internalResponse = GenericApiResponse.success("successfull response", ATTACHMENT_FILENAME);
		
		when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
		.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
		.thenReturn(TransferSerializer.serializePlain(internalResponse));
		when(okHttpRestClient.downloadData( any(), any()))
		.thenReturn(dataResponse);
		

		assertThrows(DataTransferAPIException.class,
				() -> apiService.downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

    }
	
	@Test
    @DisplayName("Download data - fail - policy not valid")
    public void downloadData_fail_policyNotValid() throws IOException {
		
		GenericApiResponse<String> internalResponse = GenericApiResponse.error("Policy not valid");
		
		when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
		.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
		.thenReturn(TransferSerializer.serializePlain(internalResponse));
		

		assertThrows(DataTransferAPIException.class,
				() -> apiService.downloadData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()));

    }
	
	@ParameterizedTest
    @DisplayName("Download data - fail - wrong state")
	@MethodSource("download_wrongStates")
    public void downloadData_fail_wrongState(TransferProcess input) throws IOException {
		when(transferProcessRepository.findById(input.getId()))
		.thenReturn(Optional.of(input));
		

		assertThrows(DataTransferAPIException.class,
				() -> apiService.downloadData(input.getId()));

    }
	
	@Test
	@DisplayName("View data - success")
	public void viewData_success() {
		mockHttpServletResponse = new MockHttpServletResponse();
		when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId()))
		.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED));
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		GenericApiResponse<String> internalResponse = GenericApiResponse.success("successfull response", ATTACHMENT_FILENAME);
		when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
		.thenReturn(TransferSerializer.serializePlain(internalResponse));
		ObjectId objectId = new ObjectId(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getDataId());
		when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
		when(gridFSBucket.find(any(Bson.class))).thenReturn(gridFSFindIterable);
		when(gridFSFindIterable.first()).thenReturn(gridFSFile);
		when(gridFSFile.getObjectId()).thenReturn(objectId);
		Document doc = new Document();
		doc.append("_contentType", "application/json");
		when(gridFSFile.getMetadata()).thenReturn(doc);
		when(gridFSBucket.openDownloadStream(objectId)).thenReturn(gridFSDownloadStream);
		
		try (MockedStatic<GridFSBuckets> buckets = Mockito.mockStatic(GridFSBuckets.class);
				MockedStatic<IOUtils> utils = Mockito.mockStatic(IOUtils.class)) {
				buckets.when(() -> GridFSBuckets.create(mongoTemplate.getDb()))
		          .thenReturn(gridFSBucket);
				utils.when(() -> IOUtils.copyLarge(any(), any())).thenReturn(1L);

				assertDoesNotThrow(() ->apiService.viewData(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId(), mockHttpServletResponse));
			
		}
		
	}
	
	@Test
	@DisplayName("View data - fail - can not access data")
	public void viewData_fail_canNotAccessData() {
		mockHttpServletResponse = new MockHttpServletResponse();
		when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId()))
			.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED));
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		GenericApiResponse<String> internalResponse = GenericApiResponse.success("successfull response", ATTACHMENT_FILENAME);
		when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
			.thenReturn(TransferSerializer.serializePlain(internalResponse));
		ObjectId objectId = new ObjectId(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getDataId());
		when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
		when(gridFSBucket.find(any(Bson.class))).thenReturn(gridFSFindIterable);
		when(gridFSFindIterable.first()).thenReturn(gridFSFile);
		when(gridFSFile.getObjectId()).thenReturn(objectId);
		Document doc = new Document();
		doc.append("_contentType", "application/json");
		when(gridFSFile.getMetadata()).thenReturn(doc);
		when(gridFSBucket.openDownloadStream(objectId)).thenReturn(gridFSDownloadStream);

		try (MockedStatic<GridFSBuckets> buckets = Mockito.mockStatic(GridFSBuckets.class);
				MockedStatic<IOUtils> utils = Mockito.mockStatic(IOUtils.class)) {
				buckets.when(() -> GridFSBuckets.create(mongoTemplate.getDb()))
		          	.thenReturn(gridFSBucket);
				utils.when(() -> IOUtils.copyLarge(any(), any())).thenThrow(IOException.class);

				assertThrows(DataTransferAPIException.class,
						() -> apiService.viewData(
								DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId(),
								mockHttpServletResponse));

		}
		
	}
	
	@Test
	@DisplayName("View data - fail - no data")
	public void viewData_fail_noData() {
		mockHttpServletResponse = new MockHttpServletResponse();
		when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId()))
		.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED));
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		GenericApiResponse<String> internalResponse = GenericApiResponse.success("successfull response", ATTACHMENT_FILENAME);
		when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
		.thenReturn(TransferSerializer.serializePlain(internalResponse));
		when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
		when(gridFSBucket.find(any(Bson.class))).thenReturn(gridFSFindIterable);
		when(gridFSFindIterable.first()).thenReturn(null);
		Document doc = new Document();
		doc.append("_contentType", "application/json");
		
		try (MockedStatic<GridFSBuckets> buckets = Mockito.mockStatic(GridFSBuckets.class)) {
				buckets.when(() -> GridFSBuckets.create(mongoTemplate.getDb()))
		          .thenReturn(gridFSBucket);

				assertThrows(DataTransferAPIException.class,
						() -> apiService.viewData(
								DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId(),
								mockHttpServletResponse));

		}
		
	}
	
	@Test
    @DisplayName("View data - fail - policy not valid")
    public void viewData_fail_policyNotValid() throws IOException {
		
		GenericApiResponse<String> internalResponse = GenericApiResponse.error("Policy not valid");
		
		when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId()))
		.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED));
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
		.thenReturn(TransferSerializer.serializePlain(internalResponse));
		

		assertThrows(DataTransferAPIException.class,
				() -> apiService.viewData(
						DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED_AND_DOWNLOADED.getId(),
						mockHttpServletResponse));

    }
	
	@Test
    @DisplayName("View data - fail - not downloaded")
    public void viewData_fail_notDownloaded() throws IOException {
		
		when(transferProcessRepository.findById(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId()))
		.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));
		

		assertThrows(DataTransferAPIException.class,
				() -> apiService.viewData(
						DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId(),
						mockHttpServletResponse));

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
