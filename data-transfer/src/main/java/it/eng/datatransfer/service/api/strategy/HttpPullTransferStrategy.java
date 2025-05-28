package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.model.ExternalData;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class HttpPullTransferStrategy implements DataTransferStrategy {

    private final OkHttpRestClient okHttpRestClient;
    private final S3ClientService s3ClientService;
    private final S3Properties s3Properties;

    public HttpPullTransferStrategy(OkHttpRestClient okHttpRestClient, S3ClientService s3ClientService,
                                    S3Properties s3Properties) {
        this.okHttpRestClient = okHttpRestClient;
        this.s3ClientService = s3ClientService;
        this.s3Properties = s3Properties;
    }

    @Override
    public void transfer(TransferProcess transferProcess) {
        log.info("Executing HTTP PULL transfer for process {}", transferProcess.getId());

        // get authorization information from Data Address if present
        String authorization = null;
        if (transferProcess.getDataAddress().getEndpointProperties() != null) {
            List<EndpointProperty> properties = transferProcess.getDataAddress().getEndpointProperties();
            String authType = properties.stream().filter(prop -> StringUtils.equals(prop.getName(), IConstants.AUTH_TYPE))
                    .findFirst().map(EndpointProperty::getValue).orElse(null);
            String token = properties.stream()
                    .filter(prop -> StringUtils.equals(prop.getName(), IConstants.AUTHORIZATION)).findFirst()
                    .map(EndpointProperty::getValue).orElse(null);

            authorization = authType + " " + token;
        }

        GenericApiResponse<ExternalData> response = okHttpRestClient.downloadData(transferProcess.getDataAddress().getEndpoint(),
                authorization);

        if (!response.isSuccess()) {
            log.error("Download aborted, {}", response.getMessage());
            throw new DataTransferAPIException("Download aborted, " + response.getMessage());
        }

        log.info("Downloaded transfer process id - {} data!", transferProcess.getId());

        log.info("Storing transfer process id - {} data...", transferProcess.getId());

        // Create bucket if it doesn't exist
        if (!s3ClientService.bucketExists(s3Properties.getBucketName())) {
            s3ClientService.createBucket(s3Properties.getBucketName());
        }

        // Upload file to S3
        try {
            s3ClientService.uploadFile(s3Properties.getBucketName(), transferProcess.getId(), response.getData().getData(),
                    response.getData().getContentType().toString(), response.getData().getContentDisposition());
        } catch (Exception e) {
            log.error("File storing aborted, {}", e.getMessage());
            throw new DataTransferAPIException("File storing aborted, " + e.getMessage());
        }
        log.info("Stored transfer process id - {} data!", transferProcess.getId());
    }
}
