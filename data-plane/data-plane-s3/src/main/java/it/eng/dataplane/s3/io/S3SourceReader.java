package it.eng.dataplane.s3.io;

import it.eng.dataplane.api.io.SourceContext;
import it.eng.dataplane.api.io.SourceOpenResult;
import it.eng.dataplane.api.io.SourceReader;
import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.BucketCredentialsService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;

/**
 * S3-backed {@link SourceReader} implementation.
 */
@Component
public class S3SourceReader implements SourceReader {

    private static final String SOURCE_TYPE = "s3";
    private static final String BUCKET_NAME = "bucketName";
    private static final String OBJECT_KEY = "objectKey";

    private final S3ClientProvider s3ClientProvider;
    private final BucketCredentialsService bucketCredentialsService;
    private final S3Properties s3Properties;

    /**
     * Creates the S3 source reader.
     *
     * @param s3ClientProvider S3 client provider
     * @param bucketCredentialsService bucket credentials service
     * @param s3Properties S3 properties
     */
    public S3SourceReader(S3ClientProvider s3ClientProvider,
                          BucketCredentialsService bucketCredentialsService,
                          S3Properties s3Properties) {
        this.s3ClientProvider = s3ClientProvider;
        this.bucketCredentialsService = bucketCredentialsService;
        this.s3Properties = s3Properties;
    }

    /**
     * Returns the source type handled by this reader.
     *
     * @return {@code s3}
     */
    @Override
    public String getSourceType() {
        return SOURCE_TYPE;
    }

    /**
     * Opens an S3 object for reading.
     *
     * @param context source context containing bucket and object details
     * @return opened S3 source result
     * @throws IOException never thrown directly but kept for API compatibility
     */
    @Override
    public SourceOpenResult open(SourceContext context) throws IOException {
        String bucketName = requireProperty(context, BUCKET_NAME);
        String objectKey = requireProperty(context, OBJECT_KEY);
        BucketCredentialsEntity bucketCredentials = bucketCredentialsService.getBucketCredentials(bucketName);
        S3ClientRequest clientRequest = S3ClientRequest.from(
                s3Properties.getRegion(),
                s3Properties.getEndpoint(),
                bucketCredentials
        );
        S3Client s3Client = s3ClientProvider.s3Client(clientRequest);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        ResponseInputStream<GetObjectResponse> responseInputStream = s3Client.getObject(getObjectRequest);
        GetObjectResponse response = responseInputStream.response();
        return SourceOpenResult.success(
                responseInputStream,
                response.contentType(),
                response.contentLength(),
                true
        );
    }

    private String requireProperty(SourceContext context, String key) {
        String value = context.getProperty(key);
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }
}
