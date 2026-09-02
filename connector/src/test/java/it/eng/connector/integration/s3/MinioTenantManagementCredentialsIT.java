package it.eng.connector.integration.s3;

import io.minio.admin.MinioAdminClient;
import io.minio.admin.UserInfo;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.service.BucketCredentialsService;
import it.eng.tools.s3.service.S3BucketProvisionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Experimental MinIO policy investigations only; excluded from default verification until a working custom policy is found.")
public class MinioTenantManagementCredentialsIT extends BaseIntegrationTest {

    private static final String USER_MANAGER_POLICY_PREFIX = "user-manager-policy-";

    @Autowired
    private S3BucketProvisionService s3BucketProvisionService;

    @Autowired
    private BucketCredentialsService bucketCredentialsService;

    private final List<String> bucketNames = new ArrayList<>();
    private final List<String> usersToDelete = new ArrayList<>();
    private final List<String> policiesToDelete = new ArrayList<>();

    @AfterEach
    void cleanup() {
        if (!usersToDelete.isEmpty() || !policiesToDelete.isEmpty()) {
            MinioAdminClient rootAdminClient = buildAdminClient(minIOContainer.getUserName(), minIOContainer.getPassword());
            for (String currentUser : usersToDelete) {
                try {
                    rootAdminClient.deleteUser(currentUser);
                } catch (Exception ignored) {
                    // best-effort cleanup
                }
            }
            for (String currentPolicy : policiesToDelete) {
                try {
                    rootAdminClient.removeCannedPolicy(currentPolicy);
                } catch (Exception ignored) {
                    // best-effort cleanup
                }
            }
        }
        usersToDelete.clear();
        policiesToDelete.clear();
        for (String currentBucketName : bucketNames) {
            try {
                s3BucketProvisionService.cleanupBucket(currentBucketName);
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
        bucketNames.clear();
    }

    @Test
    @DisplayName("tenant management credentials can create and delete a temp user that uploads into the bucket")
    void tenantManagementCredentials_canManageTempUserAndUploadedObject() throws Exception {
        String bucketName = "mgmt-it-" + UUID.randomUUID().toString().substring(0, 8);
        bucketNames.add(bucketName);
        String objectKey = "temp-user-upload.txt";
        String fileContent = "temporary upload content";

        s3BucketProvisionService.createSecureBucket(bucketName);
        BucketCredentialsEntity managementCredentials = bucketCredentialsService.getBucketCredentials(bucketName);

        MinioAdminClient managementAdminClient = buildAdminClient(managementCredentials.getAccessKey(), managementCredentials.getSecretKey());
        try (S3Client managementS3Client = buildS3Client(managementCredentials.getAccessKey(), managementCredentials.getSecretKey())) {

            assertDoesNotThrow(() -> managementS3Client.listObjectsV2(
                    ListObjectsV2Request.builder().bucket(bucketName).build()));
            assertDoesNotThrow(() -> managementAdminClient.getUserInfo(managementCredentials.getAccessKey()));

            String tempAccessKey = "TempUser-" + UUID.randomUUID().toString().substring(0, 8);
            String tempSecretKey = UUID.randomUUID().toString();
            String tempPolicyName = "temp-policy-" + UUID.randomUUID().toString().substring(0, 8);
            usersToDelete.add(tempAccessKey);
            policiesToDelete.add(tempPolicyName);

            managementAdminClient.addUser(tempAccessKey, UserInfo.Status.ENABLED, tempSecretKey, null, null);
            managementAdminClient.addCannedPolicy(tempPolicyName, buildPutObjectPolicy(bucketName, objectKey));
            managementAdminClient.setPolicy(tempAccessKey, false, tempPolicyName);

            try (S3Client tempUserS3Client = buildS3Client(tempAccessKey, tempSecretKey)) {
                tempUserS3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(objectKey)
                                .build(),
                        RequestBody.fromString(fileContent));
            }

            byte[] uploadedBytes = managementS3Client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .build())
                    .asByteArray();
            assertArrayEquals(fileContent.getBytes(StandardCharsets.UTF_8), uploadedBytes);

            managementAdminClient.deleteUser(tempAccessKey);
            managementAdminClient.removeCannedPolicy(tempPolicyName);

            assertThrows(Exception.class, () -> managementAdminClient.getUserInfo(tempAccessKey));
            usersToDelete.remove(tempAccessKey);
            policiesToDelete.remove(tempPolicyName);
        }
    }

    @Test
    @DisplayName("sub-admin policy attached at user creation still cannot create delegated users")
    void subAdminPolicyAttachedAtCreation_stillCannotCreateDelegatedUsers() throws Exception {
        String firstBucketName = "md-a-" + UUID.randomUUID().toString().substring(0, 8);
        String firstManagerAccessKey = "SubAdminA-" + UUID.randomUUID().toString().substring(0, 8);
        String firstManagerSecretKey = UUID.randomUUID().toString();
        String managerPolicyName = USER_MANAGER_POLICY_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        String delegatedAccessKey = "TempUser-" + UUID.randomUUID().toString().substring(0, 8);
        String delegatedSecretKey = UUID.randomUUID().toString();

        bucketNames.add(firstBucketName);
        usersToDelete.add(delegatedAccessKey);
        usersToDelete.add(firstManagerAccessKey);
        policiesToDelete.add(managerPolicyName);

        try (S3Client rootS3Client = buildS3Client(minIOContainer.getUserName(), minIOContainer.getPassword())) {
            MinioAdminClient rootAdminClient = buildAdminClient(minIOContainer.getUserName(), minIOContainer.getPassword());

            rootS3Client.createBucket(CreateBucketRequest.builder().bucket(firstBucketName).build());
            rootAdminClient.addCannedPolicy(managerPolicyName, buildCreateUserAdminPolicy());
            rootAdminClient.addUser(firstManagerAccessKey, UserInfo.Status.ENABLED, firstManagerSecretKey, managerPolicyName, List.of());
            rootS3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                    .bucket(firstBucketName)
                    .policy(buildBucketManagerBucketPolicy(firstBucketName, firstManagerAccessKey))
                    .build());
        }

        MinioAdminClient firstManagerAdminClient = buildAdminClient(firstManagerAccessKey, firstManagerSecretKey);
        RuntimeException exception = assertThrows(RuntimeException.class, () -> firstManagerAdminClient.addUser(
                delegatedAccessKey,
                UserInfo.Status.ENABLED,
                delegatedSecretKey,
                null,
                List.of()));
        assertTrue(exception.getMessage().contains("AccessDenied"));
        assertTrue(exception.getMessage().contains("/minio/admin/v3/add-user"));
    }

    @Test
    @DisplayName("isolated delegated policy still does not grant own bucket access or delegated temp user creation")
    void isolatedDelegatedPolicy_stillDoesNotGrantOwnBucketAccessOrDelegatedTempUserCreation() throws Exception {
        String firstBucketName = "iso-a-" + UUID.randomUUID().toString().substring(0, 8);
        String secondBucketName = "iso-b-" + UUID.randomUUID().toString().substring(0, 8);
        String managerAccessKey = "IsoMgr-" + UUID.randomUUID().toString().substring(0, 8);
        String managerSecretKey = UUID.randomUUID().toString();
        String managerPolicyName = USER_MANAGER_POLICY_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        String tempAccessKey = "TempUser-" + UUID.randomUUID().toString().substring(0, 8);
        String tempSecretKey = UUID.randomUUID().toString();
        String managerObjectKey = "manager.txt";
        String managerContent = "manager-content";

        bucketNames.add(firstBucketName);
        bucketNames.add(secondBucketName);
        usersToDelete.add(managerAccessKey);
        usersToDelete.add(tempAccessKey);
        policiesToDelete.add(managerPolicyName);

        try (S3Client rootS3Client = buildS3Client(minIOContainer.getUserName(), minIOContainer.getPassword())) {
            MinioAdminClient rootAdminClient = buildAdminClient(minIOContainer.getUserName(), minIOContainer.getPassword());

            rootS3Client.createBucket(CreateBucketRequest.builder().bucket(firstBucketName).build());
            rootS3Client.createBucket(CreateBucketRequest.builder().bucket(secondBucketName).build());
            rootAdminClient.addCannedPolicy(managerPolicyName, buildIsolatedDelegatedManagerPolicy(firstBucketName));
            rootAdminClient.addUser(managerAccessKey, UserInfo.Status.ENABLED, managerSecretKey, managerPolicyName, List.of());
        }

        try (S3Client managerS3Client = buildS3Client(managerAccessKey, managerSecretKey)) {
            S3Exception putException = assertThrows(S3Exception.class, () -> managerS3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(firstBucketName)
                            .key(managerObjectKey)
                            .build(),
                    RequestBody.fromString(managerContent)));
            assertTrue(putException.getMessage().contains("Access Denied"));
        }

        MinioAdminClient managerAdminClient = buildAdminClient(managerAccessKey, managerSecretKey);
        RuntimeException addUserException = assertThrows(RuntimeException.class, () -> managerAdminClient.addUser(
                tempAccessKey,
                UserInfo.Status.ENABLED,
                tempSecretKey,
                managerPolicyName,
                List.of()));
        assertTrue(addUserException.getMessage().contains("AccessDenied"));
        assertTrue(addUserException.getMessage().contains("/minio/admin/v3/add-user"));
    }

    @Test
    @DisplayName("two bucket managers can access only their own bucket objects")
    void twoBucketManagers_canAccessOnlyTheirOwnBuckets() {
        String firstBucketName = "mgmt-a-" + UUID.randomUUID().toString().substring(0, 8);
        String secondBucketName = "mgmt-b-" + UUID.randomUUID().toString().substring(0, 8);
        bucketNames.add(firstBucketName);
        bucketNames.add(secondBucketName);

        String firstObjectKey = "first-owner.txt";
        String secondObjectKey = "second-owner.txt";
        String firstContent = "first-owner-content";
        String secondContent = "second-owner-content";

        s3BucketProvisionService.createSecureBucket(firstBucketName);
        s3BucketProvisionService.createSecureBucket(secondBucketName);

        BucketCredentialsEntity firstManagerCredentials = bucketCredentialsService.getBucketCredentials(firstBucketName);
        BucketCredentialsEntity secondManagerCredentials = bucketCredentialsService.getBucketCredentials(secondBucketName);

        try (S3Client firstManagerS3Client = buildS3Client(firstManagerCredentials.getAccessKey(), firstManagerCredentials.getSecretKey());
             S3Client secondManagerS3Client = buildS3Client(secondManagerCredentials.getAccessKey(), secondManagerCredentials.getSecretKey())) {

            firstManagerS3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(firstBucketName)
                            .key(firstObjectKey)
                            .build(),
                    RequestBody.fromString(firstContent));
            secondManagerS3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(secondBucketName)
                            .key(secondObjectKey)
                            .build(),
                    RequestBody.fromString(secondContent));

            assertArrayEquals(firstContent.getBytes(StandardCharsets.UTF_8),
                    firstManagerS3Client.getObjectAsBytes(GetObjectRequest.builder()
                                    .bucket(firstBucketName)
                                    .key(firstObjectKey)
                                    .build())
                            .asByteArray());
            assertArrayEquals(secondContent.getBytes(StandardCharsets.UTF_8),
                    secondManagerS3Client.getObjectAsBytes(GetObjectRequest.builder()
                                    .bucket(secondBucketName)
                                    .key(secondObjectKey)
                                    .build())
                            .asByteArray());

            assertThrows(S3Exception.class, () -> firstManagerS3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(secondBucketName)
                    .key(secondObjectKey)
                    .build()));
            assertThrows(S3Exception.class, () -> secondManagerS3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(firstBucketName)
                    .key(firstObjectKey)
                    .build()));
        }
    }

    private S3Client buildS3Client(String accessKey, String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(s3Properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(s3Properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private MinioAdminClient buildAdminClient(String accessKey, String secretKey) {
        return MinioAdminClient.builder()
                .endpoint(s3Properties.getEndpoint())
                .credentials(accessKey, secretKey)
                .build();
    }

    private String buildPutObjectPolicy(String currentBucketName, String objectKey) {
        return String.format("""
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Action": ["s3:PutObject"],
                            "Resource": ["arn:aws:s3:::%s/%s"]
                        }
                    ]
                }
                """, currentBucketName, objectKey);
    }

    private String buildCreateUserAdminPolicy() {
        return """
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Sid": "AllowDelegatedUserCreation",
                            "Effect": "Allow",
                            "Action": [
                                "admin:CreateUser"
                            ],
                            "Resource": ["arn:aws:s3:::*"]
                        }
                    ]
                }
                """;
    }

    private String buildBucketManagerBucketPolicy(String currentBucketName, String accessKey) {
        return String.format("""
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Sid": "AllowBucketManagerAccess",
                            "Effect": "Allow",
                            "Principal": {
                                "AWS": ["arn:aws:iam::*:user/%s"]
                            },
                            "Action": [
                                "s3:ListBucket",
                                "s3:GetObject",
                                "s3:PutObject"
                            ],
                            "Resource": [
                                "arn:aws:s3:::%s",
                                "arn:aws:s3:::%s/*"
                            ]
                        }
                    ]
                }
                """, accessKey, currentBucketName, currentBucketName);
    }

    private String buildIsolatedDelegatedManagerPolicy(String bucketName) {
        return String.format("""
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Sid": "BucketIsolation",
                            "Effect": "Allow",
                            "Action": ["s3:*"],
                            "Resource": [
                                "arn:aws:s3:::%s",
                                "arn:aws:s3:::%s/*"
                            ]
                        },
                        {
                            "Sid": "DelegatedUserProvisioning",
                            "Effect": "Allow",
                            "Action": [
                                "admin:CreateUser",
                                "admin:AttachUserOrGroupPolicy"
                            ],
                            "Resource": ["arn:aws:s3:::*"]
                        }
                    ]
                }
                """, bucketName, bucketName);
    }
}
