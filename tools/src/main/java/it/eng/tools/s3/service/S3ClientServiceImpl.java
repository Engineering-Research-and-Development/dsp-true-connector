package it.eng.tools.s3.service;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.properties.S3Properties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    private static final int CHUNK_SIZE = 50 * 1024 * 1024; // 50MB chunks

    /**
     * Constructor for S3ClientServiceImpl.
     *
     * @param s3ClientProvider         provider for S3 client (sync and async)
     * @param s3Properties             the S3 properties
     * @param bucketCredentialsService service for managing bucket credentials
     */
    public S3ClientServiceImpl(S3ClientProvider s3ClientProvider,
                               S3Properties s3Properties,
                               BucketCredentialsService bucketCredentialsService) {
        this.s3ClientProvider = s3ClientProvider;
        this.s3Properties = s3Properties;
        this.bucketCredentialsService = bucketCredentialsService;
    }

    @Override
    public CompletableFuture<String> uploadFile(InputStream inputStream,
                                                String bucketName,
                                                String key,
                                                String contentType,
                                                String contentDisposition) {

        BucketCredentialsEntity bucketCredentials = bucketCredentialsService.getBucketCredentials(bucketName);
        log.info("Uploading file {} to bucket {}", key, bucketName);
        S3ClientRequest s3ClientRequest = S3ClientRequest.from(s3Properties.getRegion(),
                null,
                bucketCredentials);

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting multipart upload for key: {}", key);
                CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .contentType(contentType)
                        .contentDisposition(contentDisposition)
                        .key(key)
                        .build();

                log.info("Creating multipart upload for key: {}", key);
                S3AsyncClient s3AsyncClient = s3ClientProvider.s3AsyncClient(s3ClientRequest);
                String uploadId = s3AsyncClient.createMultipartUpload(createMultipartUploadRequest)
                        .join()
                        .uploadId();

                log.info("Created multipart upload for key: {} with uploadId: {}", key, uploadId);
                List<CompletedPart> completedParts = new ArrayList<>();
                int partNumber = 1;
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;

                ByteArrayOutputStream accumulator = new ByteArrayOutputStream();

                log.debug("Opening stream...");
                while ((bytesRead = inputStream.read(buffer)) > 0) {
                    accumulator.write(buffer, 0, bytesRead);

                    // Upload part when accumulator reaches buffer size or on last part
                    if (accumulator.size() >= CHUNK_SIZE) {
                        byte[] partData = accumulator.toByteArray();
                        String eTag = uploadPart(s3AsyncClient, bucketName, key, uploadId, partNumber, partData);

                        completedParts.add(CompletedPart.builder()
                                .partNumber(partNumber)
                                .eTag(eTag)
                                .build());

                        partNumber++;
                        accumulator.reset();
                    }
                }

                // Upload any remaining data as the last part
                if (accumulator.size() > 0) {
                    byte[] partData = accumulator.toByteArray();
                    String eTag = uploadPart(s3AsyncClient, bucketName, key, uploadId, partNumber, partData);

                    completedParts.add(CompletedPart.builder()
                            .partNumber(partNumber)
                            .eTag(eTag)
                            .build());
                }

                CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                        .parts(completedParts)
                        .build();

                CompleteMultipartUploadRequest completeMultipartUploadRequest =
                        CompleteMultipartUploadRequest.builder()
                                .bucket(bucketName)
                                .key(key)
                                .uploadId(uploadId)
                                .multipartUpload(completedMultipartUpload)
                                .build();

                log.info("Completing multipart upload for key: {} with uploadId: {}", key, uploadId);
                return s3AsyncClient.completeMultipartUpload(completeMultipartUploadRequest)
                        .join()
                        .eTag();

            } catch (IOException e) {
                throw new CompletionException("Failed to upload large stream to S3", e);
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.error("Failed to close input stream: {}", e.getMessage());
                }
            }
        });
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
    public String generatePresignedGETUrl(String bucketName, String objectKey, Duration expiration) {
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
    public String generatePresignedPUTUrl(String bucketName, String objectKey, Duration expiration) {
        validateBucketName(bucketName);
        if (StringUtils.isBlank(objectKey)) {
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

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .putObjectRequest(putObjectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
            log.info("Pre-signed PUT URL generated successfully for file {} in bucket {}", objectKey, bucketName);
            return presignedRequest.url().toExternalForm();
        } catch (Exception e) {
            log.error("Error generating pre-signed PUT URL for file {} in bucket {}: {}", objectKey, bucketName, e.getMessage());
            throw new RuntimeException("Error generating pre-signed PUT URL: " + e.getMessage(), e);
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

    private String uploadPart(S3AsyncClient s3AsyncClient, String bucketName, String key, String uploadId,
                              int partNumber, byte[] partData) {
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();
        log.info("Uploading part {} for key: {} with uploadId: {}", partNumber, key, uploadId);
        return s3AsyncClient.uploadPart(uploadPartRequest,
                        AsyncRequestBody.fromBytes(partData))
                .join()
                .eTag();
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
