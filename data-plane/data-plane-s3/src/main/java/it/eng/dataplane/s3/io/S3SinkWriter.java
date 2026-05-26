package it.eng.dataplane.s3.io;

import it.eng.dataplane.api.io.SinkContext;
import it.eng.dataplane.api.io.SinkWriteResult;
import it.eng.dataplane.api.io.SinkWriter;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * S3-backed {@link SinkWriter} implementation.
 */
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
     * @param data data stream to upload
     * @param context sink context with S3 properties
     * @return sink write result containing the uploaded object identifier
     */
    @Override
    public SinkWriteResult write(InputStream data, SinkContext context) {
        Map<String, String> properties = context.getProperties();
        String bucketName = properties.get(S3Utils.BUCKET_NAME);
        if (StringUtils.isBlank(bucketName)) {
            return SinkWriteResult.failure("bucketName is required");
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
            return SinkWriteResult.failure(extractErrorMessage(exception));
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
