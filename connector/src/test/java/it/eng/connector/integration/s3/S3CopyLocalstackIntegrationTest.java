package it.eng.connector.integration.s3;

import it.eng.connector.integration.BaseIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.internal.multipart.MultipartS3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.multipart.MultipartConfiguration;
import software.amazon.awssdk.services.sts.StsAsyncClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.utils.ThreadFactoryBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR;

@Slf4j
public class S3CopyLocalstackIntegrationTest extends BaseIntegrationTest {

    private static final DockerImageName LOCALSTACK_DOCKER_IMAGE = DockerImageName.parse("localstack/localstack:4.2.0");

    public static final String STATEMENT_ATTRIBUTE = "Statement";

    private static final String region = "eu-central-1";
    // source - provider
    private static final String sourceBucket = "source-bucket";
    private static final String sourceObjectName = "source.txt";
    private static final String fileContent = "Hello, world!";

    // consumer - destination
    private final String destinationBucket = "destination-bucket";

    @Container
    private static final LocalStackContainer LOCALSTACK_CONTAINER = new LocalStackContainer(LOCALSTACK_DOCKER_IMAGE)
            .withServices(
                    LocalStackContainer.Service.S3,
                    LocalStackContainer.Service.IAM,
                    LocalStackContainer.Service.STS)
//            .withEnv("DEFAULT_REGION", region)
//            .withEnv("AWS_ACCESS_KEY_ID", "providerAccessKey")
//            .withEnv("AWS_SECRET_ACCESS_KEY", "providerSecretKey")
            .withEnv("ENFORCE_IAM", "true")
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(S3CopyLocalstackIntegrationTest.class)));


    @Test
    public void create2BucketsUploadFileAndCopyFromOneBucketToAnother() throws IOException {
        // Implement the test logic here
        // This is a placeholder for the actual test implementation
        log.info("Creating buckets, uploading file, and copying from one bucket to another...");
        String sourceAccessKey = "ASIAQAAAAAAAGMKEM7X5";
        String sourceSecretKey = "ASIAQAAAAAAAGMKEM7X5";
        var s3ClientProvider = getS3Client(LOCALSTACK_CONTAINER.getEndpoint(), sourceAccessKey, sourceSecretKey);

        s3ClientProvider.createBucket(CreateBucketRequest.builder()
                .bucket(sourceBucket)
                .build());
        s3ClientProvider.putObject(PutObjectRequest.builder()
                .bucket(sourceBucket)
                .key(sourceObjectName)
                .build(), RequestBody.fromBytes(fileContent.getBytes()));

        String destinationAccessKey = "AKIARZPUZDIKGB2VALC4";
        String destinationSecretKey = "AKIARZPUZDIKGB2VALC4";
        var s3ClientConsumer = getS3Client(LOCALSTACK_CONTAINER.getEndpoint(), destinationAccessKey, destinationSecretKey);
        s3ClientConsumer.createBucket(CreateBucketRequest.builder()
                .bucket(destinationBucket)
                .build());

        String destinationKey = "destination.txt";
        var copyRequest = CopyObjectRequest.builder()
                .sourceBucket(sourceBucket)
                .sourceKey(sourceObjectName)
                .destinationBucket(destinationBucket)
                .destinationKey(destinationKey)
                .acl(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL)
                .build();

        CopyObjectResponse copyObjectResponse = s3ClientProvider.copyObject(copyRequest);
        log.info("Copy Object Response: {}", copyObjectResponse.copyObjectResult().toString());

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(destinationBucket)
                .key(destinationKey)
                .build();

        ResponseInputStream<GetObjectResponse> getObjectResponse = s3ClientConsumer.getObject(getObjectRequest);
        log.info("Object response {}", getObjectResponse.response().metadata());
        String copiedContent = getObjectResponse.readAllBytes().toString();
        log.info("Copied content: {}", copiedContent);
        log.info("File copied successfully from source to destination bucket.");
    }

    @Test
    public void copyFilesFromSourceToDestinationBucket() throws IOException {
        // Implement the test logic here
        // This is a placeholder for the actual test implementation
        log.info("Copying files from source to destination bucket...");

        String sourceAccessKey = "providerAccessKey";
        String sourceSecretKey = "providerSecretKey";
        var s3ClientProvider = getS3Client(LOCALSTACK_CONTAINER.getEndpoint(), sourceAccessKey, sourceSecretKey);
        var iamClientProvider = getIamClient(LOCALSTACK_CONTAINER.getEndpoint(), sourceAccessKey, sourceSecretKey);
        var stsClientProvider = StsAsyncClient.builder()
                .credentialsProvider(localStackCredentials(sourceAccessKey, sourceSecretKey))
                .region(Region.of(region))
                .endpointOverride(LOCALSTACK_CONTAINER.getEndpoint())
                .build();

        String destinationAccessKey = "consumerAccessKey";
        String destinationSecretKey = "consumerSecretKey";
        var s3ClientConsumer = getS3Client(LOCALSTACK_CONTAINER.getEndpoint(), destinationAccessKey, destinationSecretKey);

        createSourceBucketAndUploadFile(s3ClientProvider);
        createCopySourceUser(iamClientProvider);
        // consumer
        destinationBucketLogic(s3ClientConsumer);

        // 1. Create the role with trust policy
        CreateRoleResponse roleResponse = iamClientProvider.createRole(CreateRoleRequest.builder()
                .roleName("source-user-role")
                .assumeRolePolicyDocument("{\n" +
                        "  \"Version\": \"2012-10-17\",\n" +
                        "  \"Statement\": [\n" +
                        "    {\n" +
                        "      \"Effect\": \"Allow\",\n" +
                        "      \"Principal\": {\"AWS\": \"arn:aws:iam::000000000000:user/source-user\"},\n" +
                        "      \"Action\": \"sts:AssumeRole\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}")
                .build());
        // 2. Attach the policy to the role
        iamClientProvider.attachRolePolicy(AttachRolePolicyRequest.builder()
                .roleName("source-user-role")
                .policyArn("arn:aws:iam::000000000000:policy/source-user-policy")
                .build());

        // 3. Use the role ARN in AssumeRoleRequest
        String roleArn = "arn:aws:iam::000000000000:role/source-user-role";
        AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .roleSessionName("isThisMandatory")
                .build();

//        String roleArn = "arn:aws:iam::000000000000:policy/source-user-policy";
//        AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
//                .roleArn(roleArn)
//                .roleSessionName("isThisMandatory")
//                .build();

        AssumeRoleResponse assumeRoleResponse = stsClientProvider.assumeRole(assumeRoleRequest).join();
        log.info("AccessKey {}", assumeRoleResponse.credentials().accessKeyId());
        log.info("SecretKet {}", assumeRoleResponse.credentials().secretAccessKey());
        log.info("SessionToken {}", assumeRoleResponse.credentials().sessionToken());

        copyFileFromSourceToDestinationBucket(s3ClientProvider, s3ClientConsumer, assumeRoleResponse.credentials().accessKeyId(),
                assumeRoleResponse.credentials().secretAccessKey(), assumeRoleResponse.credentials().sessionToken());

    }

    private static void createSourceBucketAndUploadFile(S3Client s3ClientProvider) {
        // create source bucket and upload artifact
        s3ClientProvider.createBucket(CreateBucketRequest.builder()
                .bucket(sourceBucket)
                .build());
        s3ClientProvider.putObject(PutObjectRequest.builder()
                .bucket(sourceBucket)
                .key(sourceObjectName)
                .build(), RequestBody.fromBytes(fileContent.getBytes()));

        // add policy to bucket ListBucket and GetObject
        String sourceBucketPolicy = "{\n" +
                "      \"Version\": \"2012-10-17\",\n" +
                "      \"Id\": \"Policy1611277539797\",\n" +
                "      \"Statement\": [\n" +
                "        {\n" +
                "          \"Sid\": \"Stmt1611277535086\",\n" +
                "          \"Effect\": \"Allow\",\n" +
                "          \"Principal\": {\n" +
                "            \"AWS\": \"arn:aws:iam::000000000000:user/source-user\"\n" +
                "          },\n" +
                "          \"Action\": [\n" +
                "              \"s3:ListBucket\",\n" +
                "              \"s3:PutObject\",\n" +
                "              \"s3:GetObject\"\n" +
                "            ],\n" +
                "          \"Resource\": [\n" +
                "              \"arn:aws:s3:::source-bucket\",\n" +
                "              \"arn:aws:s3:::source-bucket/*\"\n" +
                "            ]\n" +
                "        }\n" +
                "      ]\n" +
                "    }";
        var putBucketPolicyRequest = PutBucketPolicyRequest.builder()
                .bucket(sourceBucket)
                .policy(sourceBucketPolicy)
                .build();
        PutBucketPolicyResponse updateBucket = s3ClientProvider.putBucketPolicy(putBucketPolicyRequest);
        log.info("Bucket policy updated: {}", updateBucket.responseMetadata());
    }

    private void copyFileFromSourceToDestinationBucket(S3Client s3ClientProvider, S3Client s3ClientConsumer, String accessKey, String secretKey, String sessionToken) throws IOException {
        String destinationKey = "destination.txt";
        var copyRequest = CopyObjectRequest.builder()
                .sourceBucket(sourceBucket)
                .sourceKey(sourceObjectName)
                .destinationBucket(destinationBucket)
                .destinationKey(destinationKey)
                .acl(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL)
                .build();

        MultipartConfiguration multipartConfiguration
                = MultipartConfiguration.builder()
                .thresholdInBytes(5L * 1024L * 1024L)
                .minimumPartSizeInBytes(5L * 1024L * 1024L)
                .build();

        Executor executor = Executors.newFixedThreadPool(10,
                new ThreadFactoryBuilder().threadNamePrefix("aws-client").build());
//        AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials
//                .create(accessKey, secretKey));
        StaticCredentialsProvider staticCredentialsProvider = StaticCredentialsProvider.create(
                AwsSessionCredentials.create(accessKey, secretKey, sessionToken));

//        listBothBuckets(staticCredentialsProvider);

        S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .asyncConfiguration(b -> b.advancedOption(FUTURE_COMPLETION_EXECUTOR, executor))
                .credentialsProvider(staticCredentialsProvider)
                .region(Region.of(region))
                .crossRegionAccessEnabled(true)
                .endpointOverride(LOCALSTACK_CONTAINER.getEndpoint())
                .forcePathStyle(true)
                .build();

//        var s3CopyClient = getS3Client(sourceLocalstackEndpoint, accessKey, secretKey);

        var multipartClient = MultipartS3AsyncClient.create(s3AsyncClient, multipartConfiguration, true);
        CopyObjectResponse copyObjectResponse = multipartClient.copyObject(copyRequest).join();

//        try (S3Client s3 = S3Client.builder()
//                .credentialsProvider(staticCredentialsProvider)
//                .region(Region.of(region))
//                .endpointOverride(LOCALSTACK_CONTAINER_CONSUMER.getEndpoint())
//                .forcePathStyle(true)
//                .build()) {
//            CopyObjectResponse copyObjectResponse = s3.copyObject(copyRequest);
//            log.info("Copy Object Response: {}", copyObjectResponse.copyObjectResult().toString());
//        }

//        CopyObjectResponse copyObjectResponse = s3CopyClient.copyObject(copyRequest);
//        log.info("Copy Object Response: {}", copyObjectResponse.copyObjectResult().toString());
        // Verify the copied object
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(destinationBucket)
                .key(destinationKey)
                .build();

        ResponseInputStream<GetObjectResponse> getObjectResponse = s3ClientConsumer.getObject(getObjectRequest);
        log.info("Object response {}", getObjectResponse.response().metadata());
        String copiedContent = getObjectResponse.readAllBytes().toString();
        log.info("Copied content: {}", copiedContent);
        log.info("File copied successfully from source to destination bucket.");

    }

    private static void listBothBuckets(StaticCredentialsProvider staticCredentialsProvider) {
        try (S3Client s3 = S3Client.builder()
                .credentialsProvider(staticCredentialsProvider)
                .region(Region.of(region))
                .endpointOverride(LOCALSTACK_CONTAINER.getEndpoint())
                .forcePathStyle(true)
                .build()) {
            List<Bucket> buckets = s3.listBuckets().buckets();
            for (Bucket bucket : buckets) {
                log.info("SOURCE - bucket name: {}", bucket.name());
            }
        }
        try (S3Client s3 = S3Client.builder()
                .credentialsProvider(staticCredentialsProvider)
                .region(Region.of(region))
                .endpointOverride(LOCALSTACK_CONTAINER.getEndpoint())
                .forcePathStyle(true)
                .build()) {
            List<Bucket> buckets = s3.listBuckets().buckets();
            for (Bucket bucket : buckets) {
                log.info("DESTINATION - bucket name: {}", bucket.name());
            }
        }
    }

    private void destinationBucketLogic(S3Client s3ClientConsumer) {
        s3ClientConsumer.createBucket(CreateBucketRequest.builder()
                .bucket(destinationBucket)
                .build());

        var putBucketPolicyRequest = PutBucketPolicyRequest.builder()
                .bucket(destinationBucket)
                .policy(getDestinationBucketPolicy())
                .build();
        PutBucketPolicyResponse updateBucket = s3ClientConsumer.putBucketPolicy(putBucketPolicyRequest);
        log.info("Bucket policy updated: {}", updateBucket.responseMetadata());
    }

    private String getDestinationBucketPolicy() {
        return "{\n" +
                "  \"Version\": \"2012-10-17\",\n" +
                "  \"Statement\": [\n" +
                "    {\n" +
                "      \"Sid\": \"AllowSourceUserToPutObject\",\n" +
                "      \"Effect\": \"Allow\",\n" +
                "      \"Principal\": {\n" +
                "        \"AWS\": \"arn:aws:iam::000000000000:user/source-user\"\n" +
                "      },\n" +
                "      \"Action\": \"s3:PutObject\",\n" +
                "      \"Resource\": \"arn:aws:s3:::destination-bucket/*\",\n" +
                "      \"Condition\": {\n" +
                "        \"StringEquals\": {\n" +
                "          \"s3:x-amz-acl\": \"bucket-owner-full-control\"\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"Sid\": \"AllowSourceUserToListAndGetObjects\",\n" +
                "      \"Effect\": \"Allow\",\n" +
                "      \"Principal\": {\n" +
                "        \"AWS\": \"arn:aws:iam::000000000000:user/source-user\"\n" +
                "      },\n" +
                "      \"Action\": [\n" +
                "        \"s3:ListBucket\",\n" +
                "        \"s3:GetObject\"\n" +
                "      ],\n" +
                "      \"Resource\": [\n" +
                "        \"arn:aws:s3:::destination-bucket\",\n" +
                "        \"arn:aws:s3:::destination-bucket/*\"\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
    }

    private void createCopySourceUser(IamClient iamClientProvider) {
        String sourceUserPolicyName = "source-user-policy";
        String sourceUser = "source-user";

        // create user policy
        CreatePolicyResponse policyResponse = iamClientProvider.createPolicy(CreatePolicyRequest.builder()
                .policyName(sourceUserPolicyName)
                .policyDocument(sourceUserPolicy())
                .build());
        log.info("Policy created: {}", policyResponse.policy());
        log.info("Policy ARN: {}", policyResponse.policy().arn());
        log.info("Policy ID: {}", policyResponse.policy().policyId());

        // create user
        CreateUserResponse userResponse = iamClientProvider.createUser(CreateUserRequest.builder()
                .userName(sourceUser)
                .build());
        log.info("User created: {}", userResponse.user());
        log.info("User ARN: {}", userResponse.user().arn());

        // attach policy to the user
        AttachUserPolicyResponse attachPolicyResponse = iamClientProvider.attachUserPolicy(AttachUserPolicyRequest.builder()
                .userName(sourceUser)
                .policyArn("arn:aws:iam::000000000000:policy/" + sourceUserPolicyName)
                .build());
        log.info("Policy attached to user: {}", attachPolicyResponse.responseMetadata());
    }

    private String sourceUserPolicy() {
        return "{\n" +
                "        \"Version\": \"2012-10-17\",\n" +
                "        \"Statement\": [\n" +
                "          {\n" +
                "            \"Sid\": \"iamPermissions\",\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Action\": [\n" +
                "              \"s3:ListBucket\",\n" +
                "              \"s3:GetObject\"\n" +
                "            ],\n" +
                "            \"Resource\": [\n" +
                "              \"arn:aws:s3:::source-bucket\",\n" +
                "              \"arn:aws:s3:::source-bucket/*\"\n" +
                "            ]\n" +
                "          },\n" +
                "          {\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Action\": [\n" +
                "              \"s3:ListBucket\",\n" +
                "              \"s3:PutObject\",\n" +
                "              \"s3:PutObjectAcl\"\n" +
                "            ],\n" +
                "            \"Resource\": [\n" +
                "              \"arn:aws:s3:::destination-bucket\",\n" +
                "              \"arn:aws:s3:::destination-bucket/*\"\n" +
                "            ]\n" +
                "          }\n" +
                "        ]\n" +
                "      }";
    }

    private S3Client getS3Client(URI endpointOverride, String accessKey, String secretKey) {
        return S3Client.builder()
                .credentialsProvider(localStackCredentials(accessKey, secretKey))
                .region(Region.of(region))
                .endpointOverride(endpointOverride)
                .forcePathStyle(true)
                .build();
    }

    private IamClient getIamClient(URI endpointOverride, String accessKey, String secretKey) {
        return IamClient.builder()
                .credentialsProvider(localStackCredentials(accessKey, secretKey))
                .region(Region.AWS_GLOBAL)
                .endpointOverride(endpointOverride)
                .build();
    }

    private StaticCredentialsProvider localStackCredentials(String accessKey, String secretKey) {
        return StaticCredentialsProvider.create(AwsBasicCredentials
                .create(accessKey, secretKey));
    }
}
