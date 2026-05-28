package it.eng.dataplane.s3.io;

import it.eng.dataplane.api.io.SinkContext;
import it.eng.dataplane.api.io.SinkWriteResult;
import it.eng.dataplane.api.io.SinkWriter;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * S3-backed {@link SinkWriter} implementation.
 */
@Slf4j
@Component
public class S3SinkWriter implements SinkWriter {

    private static final String SINK_TYPE = "s3";
    private static final String CONTENT_TYPE = "contentType";
    private static final String CONTENT_DISPOSITION = "contentDisposition";

    private final S3ClientService s3ClientService;

    /**
     * Creates the S3 sink writer.
     *
     * @param s3ClientService S3 client service
     */
    public S3SinkWriter(S3ClientService s3ClientService) {
        this.s3ClientService = s3ClientService;
    }

    /**
     * Returns the sink type handled by this writer.
     *
     * @return {@code s3}
     */
    @Override
    public String getSinkType() {
        return SINK_TYPE;
    }

    /**
     * Writes the provided stream to S3.
     *
     * <p>Required context properties: {@code bucketName}, {@code objectKey}, {@code accessKey},
     * {@code secretKey}, {@code region}. The {@code endpointOverride} property is optional.</p>
     *
     * @param data data stream to upload
     * @param context sink context with all S3 connection properties
     * @return sink write result containing the uploaded object identifier
     */
    @Override
    public SinkWriteResult write(InputStream data, SinkContext context) {
        Map<String, String> properties = context.getProperties();
        String bucketName = properties.get(S3Utils.BUCKET_NAME);
        if (StringUtils.isBlank(bucketName)) {
            log.error("S3 sink write failed: bucketName is required");
            return SinkWriteResult.failure("bucketName is required");
        }
        String objectKey = properties.get(S3Utils.OBJECT_KEY);
        if (StringUtils.isBlank(objectKey)) {
            log.error("S3 sink write failed: objectKey is required (bucketName={})", bucketName);
            return SinkWriteResult.failure("objectKey is required");
        }
        String accessKey = properties.get(S3Utils.ACCESS_KEY);
        if (StringUtils.isBlank(accessKey)) {
            log.error("S3 sink write failed: accessKey is required (bucketName={}, objectKey={})", bucketName, objectKey);
            return SinkWriteResult.failure("accessKey is required");
        }
        String secretKey = properties.get(S3Utils.SECRET_KEY);
        if (StringUtils.isBlank(secretKey)) {
            log.error("S3 sink write failed: secretKey is required (bucketName={}, objectKey={})", bucketName, objectKey);
            return SinkWriteResult.failure("secretKey is required");
        }
        String region = properties.get(S3Utils.REGION);
        if (StringUtils.isBlank(region)) {
            log.error("S3 sink write failed: region is required (bucketName={}, objectKey={})", bucketName, objectKey);
            return SinkWriteResult.failure("region is required");
        }
        try {
            String etag = s3ClientService.uploadFile(
                    data,
                    properties,
                    properties.get(CONTENT_TYPE),
                    properties.get(CONTENT_DISPOSITION)
            ).join();
            return SinkWriteResult.success(etag);
        } catch (RuntimeException exception) {
            String errorMessage = extractErrorMessage(exception);
            log.error("S3 sink write failed for s3://{}/{}: {}", bucketName, objectKey, errorMessage);
            return SinkWriteResult.failure(errorMessage);
        }
    }

    private String extractErrorMessage(RuntimeException exception) {
        if (exception instanceof CompletionException completionException
                && completionException.getCause() != null
                && StringUtils.isNotBlank(completionException.getCause().getMessage())) {
            return completionException.getCause().getMessage();
        }
        if (StringUtils.isNotBlank(exception.getMessage())) {
            return exception.getMessage();
        }
        return "Failed to upload object to S3";
    }
}
