package it.eng.datatransfer.service.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.tools.serializer.ToolsSerializer;
import org.apache.tomcat.util.codec.binary.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.exceptions.DownloadException;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.DataTransferService;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ExternalData;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;

@ExtendWith(MockitoExtension.class)
public class RestArtifactServiceTest {

	private static final String TEST_BUCKET = "test-bucket";
	private static final String FILE = "test.json";
	private static final String CONTENT_DISPOSITION = ContentDisposition.attachment()
			.filename(FILE)
			.build()
			.toString();
	private MockHttpServletResponse mockHttpServletResponse;
	@Mock
	private S3ClientService s3ClientService;
 	@Mock
	private S3Properties s3Properties;
	@Mock
	private DataTransferService dataTransferService;
	@Mock
	private ApplicationEventPublisher publisher;
    @Mock
    private OkHttpRestClient okHttpRestClient;

	@InjectMocks
	private RestArtifactService restArtifactService;
	
	private static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID_TRANSFER";
	private static final String PROVIDER_PID = "urn:uuid:PROVIDER_PID_TRANSFER";
	private static final String TRANSACTION_ID = Base64.encodeBase64URLSafeString((CONSUMER_PID + "|" + PROVIDER_PID).getBytes(StandardCharsets.UTF_8));
	
	@Test
	@DisplayName("Get artifact - decode transactionId fail")
	public void getArtifact_decodeTransactionIdFail() {
		String badTransactionId = Base64.encodeBase64URLSafeString((CONSUMER_PID + PROVIDER_PID).getBytes(StandardCharsets.UTF_8));
		
		assertThrows(DownloadException.class, () -> restArtifactService.getArtifact(badTransactionId, mockHttpServletResponse));
	}
	
	@Test
	@DisplayName("Get artifact - dataset has no artifact")
	public void getArtifact_datasetHasNoArtifactId() {
		when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID)).thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED);
		GenericApiResponse<Artifact> apiResponse = new GenericApiResponse<Artifact>();
		apiResponse.setData(null);
		apiResponse.setSuccess(false);
		when(okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getDatasetId() + "/artifact", HttpMethod.GET, null))
		.thenReturn(TransferSerializer.serializePlain(apiResponse));
		
		assertThrows(DownloadException.class, () -> restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));
		
	}
	
	@Test
	@DisplayName("Get extranal data - success")
	public void getExternalData_success() {
		mockHttpServletResponse = new MockHttpServletResponse();
		when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID)).thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED);
		GenericApiResponse<Artifact> apiResponse = new GenericApiResponse<Artifact>();
		apiResponse.setData(DataTranferMockObjectUtil.ARTIFACT_EXTERNAL);
		apiResponse.setSuccess(true);
		when(okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getDatasetId() + "/artifact", HttpMethod.GET, null))
		.thenReturn(TransferSerializer.serializePlain(apiResponse));
		ExternalData externalData = new ExternalData();
		externalData.setData("some_data".getBytes());
		externalData.setContentType(okhttp3.MediaType.parse("text/plain; charset=utf-8"));
		GenericApiResponse<ExternalData> externalResponse = new GenericApiResponse<ExternalData>();
		externalResponse.setData(externalData);
		externalResponse.setSuccess(true);
		when(okHttpRestClient.downloadData(DataTranferMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null)).thenReturn(externalResponse);
		

		assertDoesNotThrow(() ->restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));
			

	}
	
	@Test
	@DisplayName("Get extranal data - fail")
	public void getExternalData_fail() {
		when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID)).thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED);
		GenericApiResponse<Artifact> apiResponse = new GenericApiResponse<Artifact>();
		apiResponse.setData(DataTranferMockObjectUtil.ARTIFACT_EXTERNAL);
		apiResponse.setSuccess(true);
		when(okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getDatasetId() + "/artifact", HttpMethod.GET, null))
		.thenReturn(TransferSerializer.serializePlain(apiResponse));
		GenericApiResponse<ExternalData> externalResponse = new GenericApiResponse<ExternalData>();
		externalResponse.setSuccess(false);
		when(okHttpRestClient.downloadData(DataTranferMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null)).thenReturn(externalResponse);
		
		assertThrows(DownloadException.class, () -> restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));

	}
	
	@Test
    @DisplayName("Get file - success")
    public void getFile_success() {
		mockHttpServletResponse = new MockHttpServletResponse();
	    when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID))
	        .thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED);
		GenericApiResponse<JsonNode> apiResponse = new GenericApiResponse<JsonNode>();
		apiResponse.setData(ToolsSerializer.serializePlainJsonNode(DataTranferMockObjectUtil.ARTIFACT_FILE));
		apiResponse.setSuccess(true);
		when(okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getDatasetId() + "/artifact", HttpMethod.GET, null))
			.thenReturn(TransferSerializer.serializePlain(apiResponse));

	    when(s3Properties.getBucketName()).thenReturn(TEST_BUCKET);
	    when(s3ClientService.fileExists(TEST_BUCKET, DataTranferMockObjectUtil.ARTIFACT_FILE.getValue()))
	        .thenReturn(true);

	    ResponseBytes<GetObjectResponse> s3Response = Mockito.mock(ResponseBytes.class);
	    GetObjectResponse objectResponse = GetObjectResponse.builder()
	        .contentType(MediaType.APPLICATION_JSON_VALUE)
	        .contentDisposition(CONTENT_DISPOSITION)
	        .build();
	    when(s3Response.response()).thenReturn(objectResponse);
	    when(s3Response.asByteArray()).thenReturn("test data".getBytes());
	    doNothing().when(s3ClientService).downloadFile(TEST_BUCKET, DataTranferMockObjectUtil.ARTIFACT_FILE.getValue(), mockHttpServletResponse);

			assertDoesNotThrow(() ->restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));		
		}
	
	@Test
    @DisplayName("Get file - fail")
    public void getFile_fail() {
		mockHttpServletResponse = new MockHttpServletResponse();
	    when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID))
	        .thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED);
		GenericApiResponse<Artifact> apiResponse = new GenericApiResponse<Artifact>();
		apiResponse.setData(DataTranferMockObjectUtil.ARTIFACT_FILE);
		apiResponse.setSuccess(true);
		when(okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getDatasetId() + "/artifact", HttpMethod.GET, null))
		.thenReturn(TransferSerializer.serializePlain(apiResponse));

	    when(s3Properties.getBucketName()).thenReturn(TEST_BUCKET);
	    when(s3ClientService.fileExists(TEST_BUCKET, DataTranferMockObjectUtil.ARTIFACT_FILE.getValue()))
	        .thenReturn(false);
		
	    assertThrows(DataTransferAPIException.class, () -> restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));
    }
	
}
