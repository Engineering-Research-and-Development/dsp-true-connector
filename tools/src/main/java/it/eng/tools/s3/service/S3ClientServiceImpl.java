package it.eng.tools.s3.service;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.model.S3UploadMode;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.upload.S3UploadStrategy;
import it.eng.tools.s3.service.upload.S3UploadStrategyFactory;
import it.eng.tools.s3.util.S3Utils;
import it.eng.tools.service.ApplicationPropertiesService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of the S3 client service.
 */
@Service
@Slf4j
public class S3ClientServiceImpl implements S3ClientService {

    private final S3ClientProvider s3ClientProvider;
    private final S3Properties s3Properties;
    private final BucketCredentialsService bucketCredentialsService;
    private final ApplicationPropertiesService applicationPropertiesService;
    private final S3UploadStrategyFactory uploadStrategyFactory;

    private static final String S3_UPLOAD_MODE_PROPERTY_KEY = "s3.upload.mode";

    /**
     * Constructor for S3ClientServiceImpl.
     *
     * @param s3ClientProvider         provider for S3 client (sync and async)
     * @param s3Properties             the S3 properties
     * @param bucketCredentialsService service for managing bucket credentials
     * @param applicationPropertiesService service for reading application properties from MongoDB
     * @param uploadStrategyFactory    factory for creating upload strategy instances
     */
    public S3ClientServiceImpl(S3ClientProvider s3ClientProvider,
                               S3Properties s3Properties,
                               BucketCredentialsService bucketCredentialsService,
                               ApplicationPropertiesService applicationPropertiesService,
                               S3UploadStrategyFactory uploadStrategyFactory) {
        this.s3ClientProvider = s3ClientProvider;
        this.s3Properties = s3Properties;
        this.bucketCredentialsService = bucketCredentialsService;
        this.applicationPropertiesService = applicationPropertiesService;
        this.uploadStrategyFactory = uploadStrategyFactory;
    }

    @Override
    public CompletableFuture<String> uploadFile(InputStream inputStream,
                                                Map<String, String> destinationS3Properties,
                                                String contentType,
                                                String contentDisposition) {

        BucketCredentialsEntity bucketCredentials = BucketCredentialsEntity.Builder.newInstance()
                    .bucketName(destinationS3Properties.get(S3Utils.BUCKET_NAME))
                    .accessKey(destinationS3Properties.get(S3Utils.ACCESS_KEY))
                    .secretKey(destinationS3Properties.get(S3Utils.SECRET_KEY))
                    .build();

        String bucketName = destinationS3Properties.get(S3Utils.BUCKET_NAME);
        String objectKey = destinationS3Properties.get(S3Utils.OBJECT_KEY);

        // Determine upload mode from configuration
        S3UploadMode uploadMode = getUploadMode();

        log.info("Uploading file {} to bucket {} using {} mode", objectKey, bucketName, uploadMode);

        S3ClientRequest s3ClientRequest = S3ClientRequest.from(
                destinationS3Properties.get(S3Utils.REGION),
                destinationS3Properties.get(S3Utils.ENDPOINT_OVERRIDE),
                bucketCredentials);

        // Get appropriate strategy from factory based on upload mode
        S3UploadStrategy strategy = uploadStrategyFactory.getStrategy(uploadMode);

        return strategy.uploadFile(inputStream, s3ClientRequest, bucketName, objectKey, contentType, contentDisposition);
    }

    /**
     * Determines the S3 upload mode from configuration.
     * Priority: 1. MongoDB property, 2. application.properties, 3. default (SYNC)
     *
     * @return the configured S3UploadMode
     */
    private S3UploadMode getUploadMode() {
        try {
            // First, try to get from MongoDB
            return applicationPropertiesService.getPropertyByKey(S3_UPLOAD_MODE_PROPERTY_KEY)
                    .map(property -> {
                        String value = property.getValue();
                        log.debug("Using S3 upload mode from MongoDB: {}", value);
                        return S3UploadMode.fromString(value);
                    })
                    .orElseGet(() -> {
                        // Fall back to application.properties
                        String value = s3Properties.getUploadMode();
                        log.debug("Using S3 upload mode from application.properties: {}", value);
                        return S3UploadMode.fromString(value);
                    });
        } catch (Exception e) {
            // If any error occurs, fall back to application.properties or default
            log.warn("Error reading upload mode from MongoDB, falling back to application.properties: {}", e.getMessage());
            return S3UploadMode.fromString(s3Properties.getUploadMode());
        }
    }


    @Override
    public void downloadFile(String bucketName, String objectKey, HttpServletResponse response) {
        validateBucketName(bucketName);
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            S3Client s3Client = getS3Client(bucketName);

            ResponseInputStream<GetObjectResponse> responseInputStream = s3Client.getObject(getObjectRequest);
            byte[] buffer = new byte[8192]; // 8KB buffer
            int bytesRead;
            while ((bytesRead = responseInputStream.read(buffer)) != -1) {
                response.getOutputStream().write(buffer, 0, bytesRead);
                response.getOutputStream().flush();
            }

            // Set response headers
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(responseInputStream.response().contentType());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, responseInputStream.response().contentDisposition());
            response.flushBuffer();
            log.info("File {} downloaded successfully from bucket {}", objectKey, bucketName);
        } catch (NoSuchKeyException e) {
            log.error("File {} not found in bucket {}", objectKey, bucketName);
            throw new RuntimeException("File not found: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error downloading file {} from bucket {}: {}", objectKey, bucketName, e.getMessage());
            throw new RuntimeException("Error downloading file: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String bucketName, String objectKey) {
        validateBucketName(bucketName);
        try {
            if (fileExists(bucketName, objectKey)) {
                S3Client s3Client = getS3Client(bucketName);
                DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build();
                s3Client.deleteObject(deleteObjectRequest);
                log.info("File {} deleted successfully from bucket {}", objectKey, bucketName);
            } else {
                log.info("File {} does not exist in bucket {}", objectKey, bucketName);
            }
        } catch (Exception e) {
            log.error("Error deleting file {} from bucket {}: {}", objectKey, bucketName, e.getMessage());
            throw new RuntimeException("Error deleting file: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean fileExists(String bucketName, String objectKey) {
        validateBucketName(bucketName);
        try {
            S3Client s3Client = getS3Client(bucketName);
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            s3Client.headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking if file {} exists in bucket {}: {}", objectKey, bucketName, e.getMessage());
            throw new RuntimeException("Error checking if file exists: " + e.getMessage(), e);
        }
    }

    private S3Client getS3Client(String bucketName) {
        log.info("Getting S3 Client for bucket {}", bucketName);
        BucketCredentialsEntity bucketCredentials = bucketCredentialsService.getBucketCredentials(bucketName);
        S3ClientRequest s3ClientRequest = S3ClientRequest.from(s3Properties.getRegion(),
                s3Properties.getEndpoint(),
                bucketCredentials);
        return s3ClientProvider.s3Client(s3ClientRequest);
    }

    @Override
    public String generateGetPresignedUrl(String bucketName, String objectKey, Duration expiration) {
        validateBucketName(bucketName);
        if (objectKey == null || objectKey.isEmpty()) {
            throw new IllegalArgumentException("Object key cannot be null or empty");
        }
        BucketCredentialsEntity bucketCredentials = bucketCredentialsService.getBucketCredentials(bucketName);

        String externalEndpoint = resolveExternalEndpoint(bucketName);
        boolean isAws = isAwsEndpoint(externalEndpoint);

        log.debug("Generating presigned URL - AWS mode: {}, endpoint: {}, bucket: {}, key: {}",
                isAws, externalEndpoint, bucketName, objectKey);

        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(bucketCredentials.getAccessKey(), bucketCredentials.getSecretKey())))
                .region(Region.of(s3Properties.getRegion()));

        if (isAws) {
            presignerBuilder.serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                    .pathStyleAccessEnabled(false)
                    .build());
        } else {
            presignerBuilder
                    .endpointOverride(URI.create(externalEndpoint))
                    .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build());
        }

        try (S3Presigner presigner = presignerBuilder.build()) {

            S3Client s3Client = getS3Client(bucketName);

            // First, get the object's metadata
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            HeadObjectResponse objectMetadata = s3Client.headObject(headObjectRequest);

            GetObjectRequest.Builder getObjectRequestBuilder = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey);

            // Add metadata from the original object
            if (objectMetadata.contentType() != null) {
                getObjectRequestBuilder.responseContentType(objectMetadata.contentType());
            }
            if (objectMetadata.contentDisposition() != null) {
                getObjectRequestBuilder.responseContentDisposition(objectMetadata.contentDisposition());
            }

            GetObjectRequest getObjectRequest = getObjectRequestBuilder.build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            log.info("Pre-signed URL generated successfully for file {} in bucket {} with metadata", objectKey, bucketName);
            return presignedRequest.url().toExternalForm();
        } catch (Exception e) {
            log.error("Error generating pre-signed URL for file {} in bucket {}: {}", objectKey, bucketName, e.getMessage());
            throw new RuntimeException("Error generating pre-signed URL: " + e.getMessage(), e);
        }
    }

    private String resolveExternalEndpoint(String bucketName) {
        String externalEndpoint = s3Properties.getExternalPresignedEndpoint();
        if (externalEndpoint == null || externalEndpoint.isBlank()) {
            // Fallback for AWS: build virtual-hosted-style endpoint using region and bucket name
            String region = s3Properties.getRegion();
            if (region == null || region.isBlank()) {
                throw new IllegalStateException("S3 region must be configured when externalPresignedEndpoint is blank");
            }
            if ("us-east-1".equals(region)) {
                externalEndpoint = String.format("https://%s.s3.amazonaws.com", bucketName);
            } else {
                externalEndpoint = String.format("https://%s.s3.%s.amazonaws.com", bucketName, region);
            }
            log.info("Derived externalPresignedEndpoint for AWS: {}", externalEndpoint);
        }
        return externalEndpoint;
    }

    @Override
    public List<String> listFiles(String bucketName) {
        validateBucketName(bucketName);
        try {
            S3Client s3Client = s3ClientProvider.adminS3Client();

            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();
            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            return response.contents().stream()
                    .map(S3Object::key)
                    .toList();
        } catch (Exception e) {
            log.error("Error listing files in bucket {}: {}", bucketName, e.getMessage());
            throw new RuntimeException("Error listing files: " + e.getMessage(), e);
        }
    }


    private void validateBucketName(String bucketName) {
        if (bucketName == null || bucketName.isEmpty()) {
            throw new IllegalArgumentException("Bucket name cannot be null or empty");
        }
        if (!bucketName.matches("^[a-z0-9][a-z0-9\\-]{1,61}[a-z0-9]$")) {
            throw new IllegalArgumentException("Invalid bucket name format: " + bucketName);
        }
    }

    /**
     * Detects if the endpoint indicates AWS S3 vs Minio/custom S3.
     *
     * @param endpoint the S3 endpoint URL, may be null or blank
     * @return true if AWS S3, false for Minio/custom S3
     */
    private boolean isAwsEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return true;
        }
        String lower = endpoint.toLowerCase();
        return lower.contains(".amazonaws.com") || lower.contains(".aws.");
    }
}
