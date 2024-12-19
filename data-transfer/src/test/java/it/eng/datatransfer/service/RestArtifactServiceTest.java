package it.eng.datatransfer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;

import org.apache.tomcat.util.codec.binary.Base64;
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
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;

import it.eng.datatransfer.exceptions.DownloadException;
import it.eng.datatransfer.serializer.Serializer;
import it.eng.datatransfer.service.api.RestArtifactService;
import it.eng.datatransfer.util.MockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.response.GenericApiResponse;
import okhttp3.Response;
import okhttp3.ResponseBody;

@ExtendWith(MockitoExtension.class)
public class RestArtifactServiceTest {

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
	@DisplayName("Get artifact - success")
	public void getArtifact_success() {
		when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID)).thenReturn(MockObjectUtil.TRANSFER_PROCESS_STARTED);
		GenericApiResponse<String> apiResponse = new GenericApiResponse<String>();
		apiResponse.setData(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE.getId());
		apiResponse.setSuccess(true);
		when(okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + MockObjectUtil.TRANSFER_PROCESS_STARTED.getDatasetId() + "/artifact", HttpMethod.GET, null))
		.thenReturn(Serializer.serializePlain(apiResponse));
		when(artifactRepository.findById(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE.getId())).thenReturn(Optional.of(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE));
		
		Artifact artifact = restArtifactService.getArtifact(TRANSACTION_ID);
		
		assertEquals(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE, artifact);
		
	}
	
	@Test
	@DisplayName("Get artifact - decode transactionId fail")
	public void getArtifact_decodeTransactionIdFail() {
		String badTransactionId = Base64.encodeBase64URLSafeString((CONSUMER_PID + PROVIDER_PID).getBytes(Charset.forName("UTF-8")));
		
		assertThrows(DownloadException.class, () -> restArtifactService.getArtifact(badTransactionId));
	}
	
	@Test
	@DisplayName("Get artifact - dataset has no artifactId")
	public void getArtifact_datasetHasNoArtifactId() {
		when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID)).thenReturn(MockObjectUtil.TRANSFER_PROCESS_STARTED);
		GenericApiResponse<String> apiResponse = new GenericApiResponse<String>();
		apiResponse.setData("Dataset has no artifact");
		apiResponse.setSuccess(true);
		when(okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + MockObjectUtil.TRANSFER_PROCESS_STARTED.getDatasetId() + "/artifact", HttpMethod.GET, null))
		.thenReturn(Serializer.serializePlain(apiResponse));
		
		assertThrows(DownloadException.class, () -> restArtifactService.getArtifact(TRANSACTION_ID));
		
	}
	
	@Test
	@DisplayName("Get artifact - artifact not present")
	public void getArtifact_artifactNotPresent() {
		when(dataTransferService.findTransferProcess(CONSUMER_PID, PROVIDER_PID)).thenReturn(MockObjectUtil.TRANSFER_PROCESS_STARTED);
		GenericApiResponse<String> apiResponse = new GenericApiResponse<String>();
		apiResponse.setData(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE.getId());
		apiResponse.setSuccess(true);
		when(okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + MockObjectUtil.TRANSFER_PROCESS_STARTED.getDatasetId() + "/artifact", HttpMethod.GET, null))
		.thenReturn(Serializer.serializePlain(apiResponse));
		when(artifactRepository.findById(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE.getId())).thenThrow(new DownloadException("No such artifact", HttpStatus.NOT_FOUND));
		
		assertThrows(DownloadException.class, () -> restArtifactService.getArtifact(TRANSACTION_ID));
	}
	
	@Test
	@DisplayName("Get extranal data - success")
	public void getExternalData_success() {
		when(okHttpRestClient.downloadData(it.eng.tools.util.MockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null)).thenReturn(mockApiResponse);
		when(mockApiResponse.getData()).thenReturn(mockResponse);
		when(mockApiResponse.isSuccess()).thenReturn(true);
		when(mockResponse.body()).thenReturn(mockResponseBody);
		
		Response response = restArtifactService.getExternalData(it.eng.tools.util.MockObjectUtil.ARTIFACT_EXTERNAL.getValue());
		
		assertEquals(mockResponseBody, response.body());
		
	}
	
	@Test
	@DisplayName("Get extranal data - fail")
	public void getExternalData_fail() {
		when(okHttpRestClient.downloadData(it.eng.tools.util.MockObjectUtil.ARTIFACT_EXTERNAL.getValue(), null)).thenReturn(mockApiResponse);
		when(mockApiResponse.isSuccess()).thenReturn(false);
		
		assertThrows(DownloadException.class, () -> restArtifactService.getExternalData(it.eng.tools.util.MockObjectUtil.ARTIFACT_EXTERNAL.getValue()));

	}
	
	@Test
    @DisplayName("Get file - success")
    public void getdFile_success() throws IOException {
		ObjectId objectId = new ObjectId(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE.getValue());
		when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
		when(gridFSBucket.find(any(Bson.class))).thenReturn(gridFSFindIterable);
		when(gridFSFindIterable.first()).thenReturn(gridFSFile);
		when(gridFSFile.getObjectId()).thenReturn(objectId);
		when(gridFSBucket.openDownloadStream(objectId)).thenReturn(gridFSDownloadStream);
		try (MockedStatic<GridFSBuckets> buckets = Mockito.mockStatic(GridFSBuckets.class)) {
			buckets.when(() -> GridFSBuckets.create(mongoTemplate.getDb()))
	          .thenReturn(gridFSBucket);

		GridFsResource gridFsResource = restArtifactService.streamAttachment(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE.getValue());
		
		assertNotNull(gridFsResource);

		}
    }
	
	@Test
    @DisplayName("Get file - fail")
    public void getFile_fail() throws IOException {
		when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
		when(gridFSBucket.find(any(Bson.class))).thenReturn(gridFSFindIterable);
		when(gridFSFindIterable.first()).thenReturn(null);
		try (MockedStatic<GridFSBuckets> buckets = Mockito.mockStatic(GridFSBuckets.class)) {
			buckets.when(() -> GridFSBuckets.create(mongoTemplate.getDb()))
	          .thenReturn(gridFSBucket);

		assertThrows(DownloadException.class, ()-> restArtifactService.streamAttachment(it.eng.tools.util.MockObjectUtil.ARTIFACT_FILE.getValue()));
		
		}
    }
	
}
