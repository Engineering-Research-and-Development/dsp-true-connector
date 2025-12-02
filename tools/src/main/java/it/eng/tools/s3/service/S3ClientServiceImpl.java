package it.eng.tools.s3.service;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.model.S3UploadMode;
import it.eng.tools.s3.properties.S3Properties;
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
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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

    private static final int CHUNK_SIZE = 50 * 1024 * 1024; // 50MB chunks
    private static final String S3_UPLOAD_MODE_PROPERTY_KEY = "s3.upload.mode";

    /**
     * Constructor for S3ClientServiceImpl.
     *
     * @param s3ClientProvider         provider for S3 client (sync and async)
     * @param s3Properties             the S3 properties
     * @param bucketCredentialsService service for managing bucket credentials
     * @param applicationPropertiesService service for reading application properties from MongoDB
     */
    public S3ClientServiceImpl(S3ClientProvider s3ClientProvider,
                               S3Properties s3Properties,
                               BucketCredentialsService bucketCredentialsService,
                               ApplicationPropertiesService applicationPropertiesService) {
        this.s3ClientProvider = s3ClientProvider;
        this.s3Properties = s3Properties;
        this.bucketCredentialsService = bucketCredentialsService;
        this.applicationPropertiesService = applicationPropertiesService;
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

        // Delegate to appropriate implementation based on upload mode
        if (uploadMode == S3UploadMode.ASYNC) {
            return uploadFileAsync(inputStream, s3ClientRequest, bucketName, objectKey, contentType, contentDisposition);
        } else {
            return uploadFileSync(inputStream, s3ClientRequest, bucketName, objectKey, contentType, contentDisposition);
        }
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

    /**
     * Uploads file using synchronous S3Client.
     * More compatible with Minio behind reverse proxies like Caddy.
     *
     * @param inputStream        the input stream to upload
     * @param s3ClientRequest    the S3 client request configuration
     * @param bucketName         the bucket name
     * @param objectKey          the object key
     * @param contentType        the content type
     * @param contentDisposition the content disposition
     * @return a CompletableFuture with the ETag
     */
    private CompletableFuture<String> uploadFileSync(InputStream inputStream,
                                                     S3ClientRequest s3ClientRequest,
                                                     String bucketName,
                                                     String objectKey,
                                                     String contentType,
                                                     String contentDisposition) {
        return CompletableFuture.supplyAsync(() -> {
            S3Client s3Client = s3ClientProvider.s3Client(s3ClientRequest);

            try {
                log.info("Creating multipart upload (SYNC) for key: {}", objectKey);

                CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .contentType(contentType)
                        .contentDisposition(contentDisposition)
                        .key(objectKey)
                        .build();

                CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
                String uploadId = createResponse.uploadId();

                log.info("Created multipart upload (SYNC) for key: {} with uploadId: {}", objectKey, uploadId);

                List<CompletedPart> completedParts = new ArrayList<>();
                int partNumber = 1;
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;
                ByteArrayOutputStream accumulator = new ByteArrayOutputStream();

                while ((bytesRead = inputStream.read(buffer)) > 0) {
                    accumulator.write(buffer, 0, bytesRead);

                    if (accumulator.size() >= CHUNK_SIZE) {
                        byte[] partData = accumulator.toByteArray();
                        CompletedPart part = uploadPartSync(s3Client, bucketName, objectKey, uploadId, partNumber, partData);
                        completedParts.add(part);
                        partNumber++;
                        accumulator.reset();
                    }
                }

                if (accumulator.size() > 0) {
                    byte[] partData = accumulator.toByteArray();
                    CompletedPart part = uploadPartSync(s3Client, bucketName, objectKey, uploadId, partNumber, partData);
                    completedParts.add(part);
                }

                log.info("All {} parts uploaded successfully (SYNC) for key: {}", completedParts.size(), objectKey);

                CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                        .parts(completedParts)
                        .build();

                CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .uploadId(uploadId)
                        .multipartUpload(completedUpload)
                        .build();

                log.info("Completing multipart upload (SYNC) for key: {} with uploadId: {}", objectKey, uploadId);

                CompleteMultipartUploadResponse completeResponse = s3Client.completeMultipartUpload(completeRequest);
                String eTag = completeResponse.eTag();

                log.info("Upload completed successfully (SYNC) for key: {} with ETag: {}", objectKey, eTag);

                return eTag;

            } catch (IOException e) {
                log.error("Failed to upload file (SYNC) {}: {}", objectKey, e.getMessage());
                throw new CompletionException("Failed to upload file", e);
            } catch (Exception e) {
                log.error("Failed to upload file (SYNC) {}: {}", objectKey, e.getMessage());
                throw new CompletionException("Failed to upload file", e);
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.error("Failed to close input stream: {}", e.getMessage());
                }
            }
        });
    }

    /**
     * Uploads a single part synchronously using S3Client.
     *
     * @param s3Client   the S3 client
     * @param bucketName the bucket name
     * @param objectKey  the object key
     * @param uploadId   the upload ID
     * @param partNumber the part number
     * @param partData   the part data
     * @return the completed part
     */
    private CompletedPart uploadPartSync(S3Client s3Client,
                                        String bucketName,
                                        String objectKey,
                                        String uploadId,
                                        int partNumber,
                                        byte[] partData) {
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();

        log.debug("Uploading part {} (SYNC) for key: {} ({} bytes)", partNumber, objectKey, partData.length);

        UploadPartResponse response = s3Client.uploadPart(uploadPartRequest,
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(partData));

        log.debug("Part {} uploaded successfully (SYNC) with ETag: {}", partNumber, response.eTag());

        return CompletedPart.builder()
                .partNumber(partNumber)
                .eTag(response.eTag())
                .build();
    }

    /**
     * Uploads file using asynchronous S3AsyncClient with parallel part uploads.
     * Faster but may have issues with Minio behind reverse proxies.
     *
     * @param inputStream        the input stream to upload
     * @param s3ClientRequest    the S3 client request configuration
     * @param bucketName         the bucket name
     * @param objectKey          the object key
     * @param contentType        the content type
     * @param contentDisposition the content disposition
     * @return a CompletableFuture with the ETag
     */
    private CompletableFuture<String> uploadFileAsync(InputStream inputStream,
                                                      S3ClientRequest s3ClientRequest,
                                                      String bucketName,
                                                      String objectKey,
                                                      String contentType,
                                                      String contentDisposition) {
        S3AsyncClient s3AsyncClient = s3ClientProvider.s3AsyncClient(s3ClientRequest);

        CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .contentType(contentType)
                .contentDisposition(contentDisposition)
                .key(objectKey)
                .build();

        log.info("Creating multipart upload (ASYNC) for key: {}", objectKey);

        return s3AsyncClient.createMultipartUpload(createMultipartUploadRequest)
                .thenComposeAsync(response -> {
                    String uploadId = response.uploadId();
                    log.info("Created multipart upload (ASYNC) for key: {} with uploadId: {}", objectKey, uploadId);

                    // Step 2: Read stream and upload parts in parallel
                    return uploadPartsAsync(inputStream, s3AsyncClient, bucketName, objectKey, uploadId);
                })
                .thenComposeAsync(uploadResult -> {
                    // Step 3: Complete multipart upload
                    return completeMultipartUpload(
                            s3AsyncClient,
                            bucketName,
                            objectKey,
                            uploadResult.uploadId(),
                            uploadResult.completedParts());
                })
                .exceptionally(throwable -> {
                    log.error("Failed to upload file (ASYNC) {}: {}", objectKey, throwable.getMessage());
                    throw new CompletionException("Failed to upload file", throwable);
                })
                .whenComplete((result, throwable) -> {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        log.error("Failed to close input stream: {}", e.getMessage());
                    }
                });
    }

    /**
     * Reads the input stream and uploads parts asynchronously in parallel.
     *
     * @param inputStream   the input stream to read from
     * @param s3AsyncClient the S3 async client
     * @param bucketName    the bucket name
     * @param objectKey     the object key
     * @param uploadId      the upload ID
     * @return a CompletableFuture with the upload result
     */
    private CompletableFuture<UploadResult> uploadPartsAsync(InputStream inputStream,
                                                              S3AsyncClient s3AsyncClient,
                                                              String bucketName,
                                                              String objectKey,
                                                              String uploadId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<CompletableFuture<CompletedPart>> partFutures = new ArrayList<>();
                int partNumber = 1;
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;

                ByteArrayOutputStream accumulator = new ByteArrayOutputStream();

                log.debug("Reading stream and initiating parallel uploads...");
                // Read stream and create upload futures for each part
                while ((bytesRead = inputStream.read(buffer)) > 0) {
                    accumulator.write(buffer, 0, bytesRead);

                    // Upload part when accumulator reaches buffer size
                    if (accumulator.size() >= CHUNK_SIZE) {
                        byte[] partData = accumulator.toByteArray();
                        final int currentPartNumber = partNumber;

                        // Create async upload for this part (non-blocking)
                        CompletableFuture<CompletedPart> partFuture = uploadPartAsync(
                                s3AsyncClient,
                                bucketName,
                                objectKey,
                                uploadId,
                                currentPartNumber,
                                partData);

                        partFutures.add(partFuture);
                        partNumber++;
                        accumulator.reset();
                    }
                }

                // Upload any remaining data as the last part
                if (accumulator.size() > 0) {
                    byte[] partData = accumulator.toByteArray();
                    final int currentPartNumber = partNumber;

                    CompletableFuture<CompletedPart> partFuture = uploadPartAsync(
                            s3AsyncClient,
                            bucketName,
                            objectKey,
                            uploadId,
                            currentPartNumber,
                            partData);

                    partFutures.add(partFuture);
                }

                // Wait for all parts to complete in parallel
                CompletableFuture<Void> allParts = CompletableFuture.allOf(
                        partFutures.toArray(new CompletableFuture[0]));

                return allParts.thenApply(v -> {
                    // Collect all completed parts
                    List<CompletedPart> completedParts = partFutures.stream()
                            .map(CompletableFuture::join)
                            .toList();

                    log.info("All {} parts uploaded successfully for key: {}", completedParts.size(), objectKey);
                    return new UploadResult(uploadId, completedParts);
                }).join();

            } catch (IOException e) {
                throw new CompletionException("Failed to read input stream", e);
            }
        });
    }

    /**
     * Uploads a single part asynchronously.
     *
     * @param s3AsyncClient the S3 async client
     * @param bucketName    the bucket name
     * @param objectKey     the object key
     * @param uploadId      the upload ID
     * @param partNumber    the part number
     * @param partData      the part data
     * @return a CompletableFuture with the completed part
     */
    private CompletableFuture<CompletedPart> uploadPartAsync(S3AsyncClient s3AsyncClient,
                                                              String bucketName,
                                                              String objectKey,
                                                              String uploadId,
                                                              int partNumber,
                                                              byte[] partData) {
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();

        log.debug("Uploading part {} for key: {} ({} bytes)", partNumber, objectKey, partData.length);

        return s3AsyncClient.uploadPart(uploadPartRequest, AsyncRequestBody.fromBytes(partData))
                .thenApply(response -> {
                    log.debug("Part {} uploaded successfully with ETag: {}", partNumber, response.eTag());
                    return CompletedPart.builder()
                            .partNumber(partNumber)
                            .eTag(response.eTag())
                            .build();
                });
    }

    /**
     * Completes the multipart upload.
     *
     * @param s3AsyncClient  the S3 async client
     * @param bucketName     the bucket name
     * @param objectKey      the object key
     * @param uploadId       the upload ID
     * @param completedParts the list of completed parts
     * @return a CompletableFuture with the ETag of the completed upload
     */
    private CompletableFuture<String> completeMultipartUpload(S3AsyncClient s3AsyncClient,
                                                               String bucketName,
                                                               String objectKey,
                                                               String uploadId,
                                                               List<CompletedPart> completedParts) {
        CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                .parts(completedParts)
                .build();

        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .uploadId(uploadId)
                .multipartUpload(completedMultipartUpload)
                .build();

        log.info("Completing multipart upload for key: {} with uploadId: {}", objectKey, uploadId);

        return s3AsyncClient.completeMultipartUpload(completeRequest)
                .thenApply(response -> {
                    log.info("Upload completed successfully for key: {} with ETag: {}", objectKey, response.eTag());
                    return response.eTag();
                });
    }

    /**
     * Helper record for passing upload state between async stages.
     */
    private record UploadResult(String uploadId, List<CompletedPart> completedParts) {
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
        try (S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create(s3Properties.getExternalPresignedEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(bucketCredentials.getAccessKey(), bucketCredentials.getSecretKey())))
                .region(Region.of(s3Properties.getRegion()))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build()) {

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
}
