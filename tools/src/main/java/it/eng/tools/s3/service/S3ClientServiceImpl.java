package it.eng.tools.s3.service;

import java.time.Duration;
import java.util.List;

import it.eng.tools.s3.properties.S3Properties;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * Implementation of the S3 client service.
 */
@Service
@Slf4j
public class S3ClientServiceImpl implements S3ClientService {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    /**
     * Constructor for S3ClientServiceImpl.
     *
     * @param s3Client the S3 client
     * @param s3Properties the S3 properties
     */
    public S3ClientServiceImpl(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
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
    public void uploadFile(String bucketName, String objectKey, byte[] data, String contentType, String contentDisposition) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentDisposition(contentDisposition)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(data));
            log.info("File {} uploaded successfully to bucket {}", objectKey, bucketName);
        } catch (Exception e) {
            log.error("Error uploading file {} to bucket {}: {}", objectKey, bucketName, e.getMessage());
            throw new RuntimeException("Error uploading file: " + e.getMessage(), e);
        }
    }

    @Override
    public ResponseBytes<GetObjectResponse> downloadFile(String bucketName, String objectKey) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
            log.info("File {} downloaded successfully from bucket {}", objectKey, bucketName);
            return objectBytes;
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
    public String generatePresignedUrl(String bucketName, String objectKey, Duration expiration) {
        try (S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(s3Client.serviceClientConfiguration().endpointOverride().get())
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3Properties.getAccessKey(), s3Properties.getSecretKey())))
                .region(s3Client.serviceClientConfiguration().region())
                .build()) {

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            log.info("Pre-signed URL generated successfully for file {} in bucket {}", objectKey, bucketName);
            return presignedRequest.url().toString();
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
}
