package it.eng.datatransfer.service.api;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.exceptions.DownloadException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.service.TransferProcessStrategy;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ExternalData;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventPublisher;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class RestArtifactService {

    private final TransferProcessStrategy dataTransferService;
    private final OkHttpRestClient okHttpRestClient;
    private final AuditEventPublisher publisher;
    private final ArtifactTransferService artifactTransferService;

    /**
     * Creates a new {@code RestArtifactService}.
     *
     * @param dataTransferService     strategy for locating transfer processes
     * @param okHttpRestClient        HTTP client for external artifact requests
     * @param publisher               audit event publisher
     * @param artifactTransferService artifact lookup service
     */
    public RestArtifactService(TransferProcessStrategy dataTransferService,
                               OkHttpRestClient okHttpRestClient,
                               AuditEventPublisher publisher,
                               ArtifactTransferService artifactTransferService) {
        super();
        this.dataTransferService = dataTransferService;
        this.okHttpRestClient = okHttpRestClient;
        this.publisher = publisher;
        this.artifactTransferService = artifactTransferService;
    }

    /**
     * Streams artifact data for the given transaction ID to the HTTP response.
     * FILE-type artifacts are no longer served via this endpoint — the Data Plane
     * issues a presigned URL embedded directly in the {@code TransferStartMessage}.
     *
     * @param transactionId Base64-encoded {@code consumerPid|providerPid} token
     * @param response      servlet response to write data into
     */
    public void getArtifact(String transactionId, HttpServletResponse response) {
        TransferProcess transferProcess = getTransferProcessForTransactionId(transactionId);
        Artifact artifact = artifactTransferService.findArtifact(transferProcess);

        switch (artifact.getArtifactType()) {
            case FILE:
                throw new DataTransferAPIException("FILE artifacts are served via a presigned URL in the TransferStartMessage — direct download through this endpoint is no longer supported.");
            case EXTERNAL:
                getExternalData(artifact.getValue(), artifact.getAuthorization(), response);
                break;

            default:
                log.error("Wrong artifact type: {}", artifact.getArtifactType());
                throw new DownloadException("Error while downloading data", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        publisher.publishEvent(new ArtifactConsumedEvent(transferProcess.getAgreementId(), transferProcess.getTenantId()));
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
