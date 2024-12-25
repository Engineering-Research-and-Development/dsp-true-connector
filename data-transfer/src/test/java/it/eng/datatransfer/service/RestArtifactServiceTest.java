package it.eng.datatransfer.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;

import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;

import it.eng.datatransfer.exceptions.DownloadException;
import it.eng.datatransfer.serializer.Serializer;
import it.eng.datatransfer.service.api.RestArtifactService;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.ExternalData;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.response.GenericApiResponse;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;

@ExtendWith(MockitoExtension.class)
public class RestArtifactServiceTest {

	private MockHttpServletResponse mockHttpServletResponse;
	@Mock
	private GridFSFindIterable gridFSFindIterable;
 	@Mock
 	private MongoDatabase mongoDatabase;
 	@Mock
 	private GridFSBucket gridFSBucket;
 	@Mock
 	private GridFSFile gridFSFile;
 	@Mock
 	private GridFSDownloadStream gridFSDownloadStream;
	@Mock
	private GenericApiResponse<Response> mockApiResponse;
	@Mock
	private Response mockResponse;
	@Mock
	private ResponseBody mockResponseBody;
	@Mock
	private DataTransferService dataTransferService;
	@Mock
	private MongoTemplate mongoTemplate;
	@Mock
	private ApplicationEventPublisher publisher;
    @Mock
	private ArtifactRepository artifactRepository;
    @Mock
    private OkHttpRestClient okHttpRestClient;

	@InjectMocks
	private RestArtifactService restArtifactService;
	
	private static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID_TRANSFER";
	private static final String PROVIDER_PID = "urn:uuid:PROVIDER_PID_TRANSFER";
	private static final String TRANSACTION_ID = Base64.encodeBase64URLSafeString((CONSUMER_PID + "|" + PROVIDER_PID).getBytes(Charset.forName("UTF-8")));
	
	@Test
	@DisplayName("Get artifact - decode transactionId fail")
	public void getArtifact_decodeTransactionIdFail() {
		String badTransactionId = Base64.encodeBase64URLSafeString((CONSUMER_PID + PROVIDER_PID).getBytes(Charset.forName("UTF-8")));
		
		assertThrows(DownloadException.class, () -> restArtifactService.getArtifact(badTransactionId, mockHttpServletResponse));
	}
	
	@Test
	@DisplayName("Get artifact - dataset has no artifactId")
	public void getArtifact_datasetHasNoArtifactId() {
		when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID)).thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED);
		GenericApiResponse<String> apiResponse = new GenericApiResponse<String>();
		apiResponse.setData("Dataset has no artifact");
		apiResponse.setSuccess(true);
		when(okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getDatasetId() + "/artifact", HttpMethod.GET, null))
		.thenReturn(Serializer.serializePlain(apiResponse));
		
		assertThrows(DownloadException.class, () -> restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));
		
	}
	
	@Test
	@DisplayName("Get artifact - artifact not present")
	public void getArtifact_artifactNotPresent() {
		when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID)).thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED);
		GenericApiResponse<String> apiResponse = new GenericApiResponse<String>();
		apiResponse.setData(DataTranferMockObjectUtil.ARTIFACT_FILE.getId());
		apiResponse.setSuccess(true);
		when(okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getDatasetId() + "/artifact", HttpMethod.GET, null))
		.thenReturn(Serializer.serializePlain(apiResponse));
		when(artifactRepository.findById(DataTranferMockObjectUtil.ARTIFACT_FILE.getId())).thenThrow(new DownloadException("No such artifact", HttpStatus.NOT_FOUND));
		
		assertThrows(DownloadException.class, () -> restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));
	}
	
	@Test
	@DisplayName("Get extranal data - success")
	public void getExternalData_success() {
		mockHttpServletResponse = new MockHttpServletResponse();
		when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID)).thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED);
		GenericApiResponse<String> apiResponse = new GenericApiResponse<String>();
		apiResponse.setData(DataTranferMockObjectUtil.ARTIFACT_EXTERNAL.getId());
		apiResponse.setSuccess(true);
		when(okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getDatasetId() + "/artifact", HttpMethod.GET, null))
		.thenReturn(Serializer.serializePlain(apiResponse));
		when(artifactRepository.findById(DataTranferMockObjectUtil.ARTIFACT_EXTERNAL.getId())).thenReturn(Optional.of(DataTranferMockObjectUtil.ARTIFACT_EXTERNAL));
		ExternalData externalData = new ExternalData();
		externalData.setData("some_data".getBytes());
		externalData.setContentType(MediaType.parse("text/plain; charset=utf-8"));
		GenericApiResponse<ExternalData> externalResponse = new GenericApiResponse<ExternalData>();
		externalResponse.setData(externalData);
		externalResponse.setSuccess(true);
		when(okHttpRestClient.downloadData(DataTranferMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null)).thenReturn(externalResponse);
		
		try (MockedStatic<GridFSBuckets> buckets = Mockito.mockStatic(GridFSBuckets.class);
				MockedStatic<IOUtils> utils = Mockito.mockStatic(IOUtils.class)) {
				buckets.when(() -> GridFSBuckets.create(mongoTemplate.getDb()))
		          .thenReturn(gridFSBucket);
				utils.when(() -> IOUtils.copy(any(), any())).thenReturn(1);

				assertDoesNotThrow(() ->restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));
			
		}
		
	}
	
	@Test
	@DisplayName("Get extranal data - fail")
	public void getExternalData_fail() {
		when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID)).thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED);
		GenericApiResponse<String> apiResponse = new GenericApiResponse<String>();
		apiResponse.setData(DataTranferMockObjectUtil.ARTIFACT_EXTERNAL.getId());
		apiResponse.setSuccess(true);
		when(okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getDatasetId() + "/artifact", HttpMethod.GET, null))
		.thenReturn(Serializer.serializePlain(apiResponse));
		when(artifactRepository.findById(DataTranferMockObjectUtil.ARTIFACT_EXTERNAL.getId())).thenReturn(Optional.of(DataTranferMockObjectUtil.ARTIFACT_EXTERNAL));
		GenericApiResponse<ExternalData> externalResponse = new GenericApiResponse<ExternalData>();
		externalResponse.setSuccess(false);
		when(okHttpRestClient.downloadData(DataTranferMockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null)).thenReturn(externalResponse);
		
		assertThrows(DownloadException.class, () -> restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));

	}
	
	@Test
    @DisplayName("Get file - success")
    public void getdFile_success() throws IOException {
		mockHttpServletResponse = new MockHttpServletResponse();
		when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID)).thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED);
		GenericApiResponse<String> apiResponse = new GenericApiResponse<String>();
		apiResponse.setData(DataTranferMockObjectUtil.ARTIFACT_FILE.getId());
		apiResponse.setSuccess(true);
		when(okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getDatasetId() + "/artifact", HttpMethod.GET, null))
		.thenReturn(Serializer.serializePlain(apiResponse));
		when(artifactRepository.findById(DataTranferMockObjectUtil.ARTIFACT_FILE.getId())).thenReturn(Optional.of(DataTranferMockObjectUtil.ARTIFACT_FILE));
		ObjectId objectId = new ObjectId(DataTranferMockObjectUtil.ARTIFACT_FILE.getValue());
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
			utils.when(() -> IOUtils.copy(any(), any())).thenReturn(1);

			assertDoesNotThrow(() ->restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));
		
		}
    }
	
	@Test
    @DisplayName("Get file - fail")
    public void getFile_fail() throws IOException {
		mockHttpServletResponse = new MockHttpServletResponse();
		when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID)).thenReturn(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED);
		GenericApiResponse<String> apiResponse = new GenericApiResponse<String>();
		apiResponse.setData(DataTranferMockObjectUtil.ARTIFACT_FILE.getId());
		apiResponse.setSuccess(true);
		when(okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED.getDatasetId() + "/artifact", HttpMethod.GET, null))
		.thenReturn(Serializer.serializePlain(apiResponse));
		when(artifactRepository.findById(DataTranferMockObjectUtil.ARTIFACT_FILE.getId())).thenReturn(Optional.of(DataTranferMockObjectUtil.ARTIFACT_FILE));
		when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
		when(gridFSBucket.find(any(Bson.class))).thenReturn(gridFSFindIterable);
		when(gridFSFindIterable.first()).thenReturn(null);
		try (MockedStatic<GridFSBuckets> buckets = Mockito.mockStatic(GridFSBuckets.class)) {
			buckets.when(() -> GridFSBuckets.create(mongoTemplate.getDb()))
	          .thenReturn(gridFSBucket);

			assertThrows(DownloadException.class, ()-> restArtifactService.getArtifact(TRANSACTION_ID, mockHttpServletResponse));
		
		}
    }
	
}
