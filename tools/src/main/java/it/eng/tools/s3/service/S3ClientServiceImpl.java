package it.eng.tools.s3.service;

import it.eng.tools.s3.properties.S3Properties;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Implementation of the S3 client service.
 */
@Service
@Slf4j
public class S3ClientServiceImpl implements S3ClientService {

    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final S3AsyncClient s3AsyncClient;
    private static final int CHUNK_SIZE = 50 * 1024 * 1024; // 50MB chunks

    /**
     * Constructor for S3ClientServiceImpl.
     *
     * @param s3Client the S3 client
     * @param s3Properties the S3 properties
     * @param s3AsyncClient the S3 async client
     */
    public S3ClientServiceImpl(S3Client s3Client, S3Properties s3Properties, S3AsyncClient s3AsyncClient) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
        this.s3AsyncClient = s3AsyncClient;
    }

    @Override
    public void createBucket(String bucketName) {
        try {
            if (!bucketExists(bucketName)) {
                CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                s3Client.createBucket(createBucketRequest);
                log.info("Bucket {} created successfully", bucketName);

            } else {
                log.info("Bucket {} already exists", bucketName);
            }
        } catch (Exception e) {
            log.error("Error creating bucket {}: {}", bucketName, e.getMessage());
            throw new RuntimeException("Error creating bucket: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteBucket(String bucketName) {
        try {
            if (bucketExists(bucketName)) {
                DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                s3Client.deleteBucket(deleteBucketRequest);
                log.info("Bucket {} deleted successfully", bucketName);
            } else {
                log.info("Bucket {} does not exist", bucketName);
            }
        } catch (Exception e) {
            log.error("Error deleting bucket {}: {}", bucketName, e.getMessage());
            throw new RuntimeException("Error deleting bucket: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean bucketExists(String bucketName) {
        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(headBucketRequest);
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking if bucket {} exists: {}", bucketName, e.getMessage());
            throw new RuntimeException("Error checking if bucket exists: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<String> uploadFile(InputStream inputStream,
                                                String bucketName,
                                                String key,
                                                String contentType,
                                                String contentDisposition) {

        // Create bucket if it doesn't exist
        if (!bucketExists(s3Properties.getBucketName())) {
            createBucket(s3Properties.getBucketName());
        }

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
                String uploadId = s3AsyncClient.createMultipartUpload(createMultipartUploadRequest)
                        .join()
                        .uploadId();

                log.info("Created multipart upload for key: {} with uploadId: {}", key, uploadId);
                List<CompletedPart> completedParts = new ArrayList<>();
                int partNumber = 1;
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;

                ByteArrayOutputStream accumulator = new ByteArrayOutputStream();

                while ((bytesRead = inputStream.read(buffer)) > 0) {
                    accumulator.write(buffer, 0, bytesRead);

                    // Upload part when accumulator reaches buffer size or on last part
                    if (accumulator.size() >= CHUNK_SIZE) {
                        byte[] partData = accumulator.toByteArray();
                        String eTag = uploadPart(bucketName, key, uploadId, partNumber, partData);

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
                    String eTag = uploadPart(bucketName, key, uploadId, partNumber, partData);

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
            }
        });
    }

    @Override
    public void downloadFile(String bucketName, String objectKey, HttpServletResponse response) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

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
        try {
            if (fileExists(bucketName, objectKey)) {
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
        try {
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

    @Override
    public String generateGetPresignedUrl(String bucketName, String objectKey, Duration expiration) {
        try (S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create(s3Properties.getExternalPresignedEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Properties.getAccessKey(), s3Properties.getSecretKey())))
                .region(Region.of(s3Properties.getRegion()))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build()) {


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
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();
            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            return response.contents().stream()
                    .map(s3Object -> s3Object.key())
                    .toList();
        } catch (Exception e) {
            log.error("Error listing files in bucket {}: {}", bucketName, e.getMessage());
            throw new RuntimeException("Error listing files: " + e.getMessage(), e);
        }
    }

    private String uploadPart(String bucketName, String key, String uploadId,
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
}
