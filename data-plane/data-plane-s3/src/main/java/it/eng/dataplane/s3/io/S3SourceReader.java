package it.eng.dataplane.s3.io;

import it.eng.dataplane.api.io.SourceContext;
import it.eng.dataplane.api.io.SourceOpenResult;
import it.eng.dataplane.api.io.SourceReader;
import it.eng.tools.s3.configuration.S3ClientFactory;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.util.S3Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * S3-backed {@link SourceReader} implementation.
 *
 * <p>All S3 connection details (bucket name, object key, credentials, region, and optional
 * endpoint override) must be supplied as explicit properties in the {@link SourceContext}.
 * This reader does not consult any local DP state such as {@code BucketCredentialsService}
 * or {@code S3Properties}; it relies entirely on CP-provided material.</p>
 */
@Slf4j
@Component
public class S3SourceReader implements SourceReader {

    private static final String SOURCE_TYPE = "s3";

    private final S3ClientFactory s3ClientFactory;

    /**
     * Creates the S3 source reader.
     *
     * @param s3ClientFactory S3 client factory (static or dynamic depending on context)
     */
    public S3SourceReader(S3ClientFactory s3ClientFactory) {
        this.s3ClientFactory = s3ClientFactory;
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
     * Opens an S3 object for reading using CP-provided credentials.
     *
     * <p>Required context properties: {@code bucketName}, {@code objectKey}, {@code accessKey},
     * {@code secretKey}, {@code region}. The {@code endpointOverride} property is optional and
     * used for MinIO or other non-AWS endpoints.</p>
     *
     * <p>If any required property is missing or blank, this method throws
     * {@link IllegalArgumentException} immediately, before attempting to connect to S3. This is
     * intentional: missing required properties indicate a programming error in the CP-provided
     * payload, not a recoverable runtime failure. S3 SDK errors during connection or object
     * retrieval are caught and returned as {@link SourceOpenResult#failure(String)}.</p>
     *
     * @param context source context containing all S3 connection properties
     * @return opened S3 source result; never {@code null}
     * @throws IllegalArgumentException if any required property is missing or blank
     */
    @Override
    public SourceOpenResult open(SourceContext context) {
        String bucketName = requireProperty(context, S3Utils.BUCKET_NAME);
        String objectKey = requireProperty(context, S3Utils.OBJECT_KEY);
        String accessKey = requireProperty(context, S3Utils.ACCESS_KEY);
        String secretKey = requireProperty(context, S3Utils.SECRET_KEY);
        String region = requireProperty(context, S3Utils.REGION);
        String endpointOverride = context.getProperty(S3Utils.ENDPOINT_OVERRIDE);
        try {
            BucketCredentialsEntity credentials = BucketCredentialsEntity.Builder.newInstance()
                    .bucketName(bucketName)
                    .accessKey(accessKey)
                    .secretKey(secretKey)
                    .build();
            S3ClientRequest clientRequest = S3ClientRequest.from(region, endpointOverride, credentials);
            S3Client s3Client = s3ClientFactory.getClient(clientRequest);
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
        } catch (SdkException e) {
            log.error("Failed to open S3 object s3://{}/{}: {}", bucketName, objectKey, e.getMessage());
            return SourceOpenResult.failure("S3 error opening s3://" + bucketName + "/" + objectKey + ": " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected error opening S3 object s3://{}/{}: {}", bucketName, objectKey, e.getMessage());
            return SourceOpenResult.failure("Unexpected error opening s3://" + bucketName + "/" + objectKey + ": " + e.getMessage());
        }
    }

    private String requireProperty(SourceContext context, String key) {
        String value = context.getProperty(key);
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }
}
