package it.eng.datatransfer.service.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;

import it.eng.datatransfer.exceptions.DownloadException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.DataTransferService;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ExternalData;
import it.eng.tools.response.GenericApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Service
@Slf4j
public class RestArtifactService {

	private final DataTransferService dataTransferService;
    private final OkHttpRestClient okHttpRestClient;
	private final ApplicationEventPublisher publisher;
	private final S3ClientService s3ClientService;
	private final S3Properties s3Properties;

	public RestArtifactService(DataTransferService dataTransferService,
							   OkHttpRestClient okHttpRestClient,
							   ApplicationEventPublisher publisher,
							   S3ClientService s3ClientService,
							   S3Properties s3Properties) {
		super();
		this.dataTransferService = dataTransferService;
        this.okHttpRestClient = okHttpRestClient;
		this.publisher = publisher;
		this.s3ClientService = s3ClientService;
		this.s3Properties = s3Properties;
	}

	public void getArtifact(String transactionId, HttpServletResponse response) {
		TransferProcess transferProcess = getTransferProcessForTransactionId(transactionId);
		Artifact artifact = findArtifact(transferProcess);
		
		switch (artifact.getArtifactType()) {
		case FILE:
			getFile(artifact.getValue(), response);
			break;
		case EXTERNAL:
			getExternalData(artifact.getValue(), artifact.getAuthorization(), response);
			break;

		default:
			log.error("Wrong artifact type: {}", artifact.getArtifactType());
			throw new DownloadException("Error while downloading data", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		publisher.publishEvent(new ArtifactConsumedEvent(transferProcess.getAgreementId()));
	}

	private Artifact findArtifact(TransferProcess transferProcess) {
		TypeReference<GenericApiResponse<Artifact>> typeRef = new TypeReference<GenericApiResponse<Artifact>>() {};

		String response = okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + transferProcess.getDatasetId() + "/artifact", HttpMethod.GET, null);
		GenericApiResponse<Artifact> genericResponse = TransferSerializer.deserializePlain(response, typeRef);
		
		if (genericResponse.getData() == null) {
			throw new DownloadException("No such data exists", HttpStatus.NOT_FOUND);
		}
		return genericResponse.getData();
	}

	private void getFile(String fileId, HttpServletResponse response) {
		// Check if file exists in S3
		if (!s3ClientService.fileExists(s3Properties.getBucketName(), fileId)) {
			log.error("Data not found in S3");
			throw new DataTransferAPIException("Data not found in S3");
		}

		try {
			// Download file from S3
			ResponseBytes<GetObjectResponse> s3Response = s3ClientService.downloadFile(s3Properties.getBucketName(), fileId);

			// Set response headers
			response.setStatus(HttpStatus.OK.value());
			response.setContentType(s3Response.response().contentType());
			response.setHeader(HttpHeaders.CONTENT_DISPOSITION, s3Response.response().contentDisposition());

			// Write data to response
			response.getOutputStream().write(s3Response.asByteArray());
			response.flushBuffer();
		} catch (IOException e) {
			log.error("Error while sending file", e);
			throw new DownloadException("Error while downloading data", HttpStatus.INTERNAL_SERVER_ERROR);
		}
    }
	
	private void getExternalData(String value, String authorization, HttpServletResponse response) {
		GenericApiResponse<ExternalData> externalData = okHttpRestClient.downloadData(value, authorization);
		
		if (externalData.isSuccess()) {
			try {
				response.setStatus(HttpStatus.OK.value());
				response.setContentType(externalData.getData().getContentType().toString());
				response.setHeader(HttpHeaders.CONTENT_DISPOSITION, externalData.getData().getContentDisposition());
				response.getOutputStream().write(externalData.getData().getData());
				response.flushBuffer();
			} catch (IOException e) {
				log.error("Error while downloading external data", e);
				throw new DownloadException("Error while downloading data", HttpStatus.INTERNAL_SERVER_ERROR);
			}
		} else {
			log.error("Could not download external data: {}", externalData.getMessage());
			throw new DownloadException("Could not download external data", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		
		
	}
	
	private TransferProcess getTransferProcessForTransactionId(String transactionId) {
		String[] tokens = new String(Base64.decodeBase64URLSafe(transactionId), StandardCharsets.UTF_8).split("\\|");
		if (tokens.length != 2) {
			log.error("Wrong transaction id");
			throw new DownloadException("Wrong transaction id", HttpStatus.BAD_REQUEST);
		}
		String consumerPid = tokens[0];
		String providerPid = tokens[1];
		return dataTransferService.findTransferProcess(consumerPid, providerPid);
	}
}
