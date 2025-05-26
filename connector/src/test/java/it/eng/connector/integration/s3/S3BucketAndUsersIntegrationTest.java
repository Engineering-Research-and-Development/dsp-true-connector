package it.eng.connector.integration.s3;

import it.eng.connector.integration.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
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
import software.amazon.awssdk.utils.ThreadFactoryBuilder;

import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR;

public class S3BucketAndUsersIntegrationTest extends BaseIntegrationTest {

    private static final DockerImageName LOCALSTACK_DOCKER_IMAGE = DockerImageName.parse("localstack/localstack:4.2.0");

    private static final String region = "eu-central-1";

    private static final String LOCALSTACK_CONTAINER_ACCESS_KEY = "accessKey";
    private static final String LOCALSTACK_CONTAINER_SECRET_KEY = "secretKey";

    private static final String sourceBucket = "source-bucket";
    private static final String sourceObjectName = "source.txt";
    private static final String fileContent = "Hello, world!";

    private static final String sourceUserPolicyName = "source-user-policy";
    private static final String sourceUser = "source-user";

    private String sourceAccessKeyId;
    private String sourceSecretAccessKey;

    private final String destinationBucket = "destination-bucket";
    private final String destinationObjectName = "transferred.txt";
    private static final String destinationUser = "destination-user";
    private static final String destinationUserPolicyName = "destination-user-policy";
    private String destinationAccessKeyId;
    private String destinationSecretAccessKey;

    @Container
    private static final LocalStackContainer LOCALSTACK_CONTAINER_CONSUMER = new LocalStackContainer(LOCALSTACK_DOCKER_IMAGE)
            .withServices(
                    LocalStackContainer.Service.S3,
                    LocalStackContainer.Service.IAM,
                    LocalStackContainer.Service.STS)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(S3BucketAndUsersIntegrationTest.class)));

    @Container
    private static final LocalStackContainer LOCALSTACK_CONTAINER_PROVIDER = new LocalStackContainer(LOCALSTACK_DOCKER_IMAGE)
            .withServices(
                    LocalStackContainer.Service.S3,
                    LocalStackContainer.Service.IAM,
                    LocalStackContainer.Service.STS)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(S3BucketAndUsersIntegrationTest.class)));

    @BeforeAll
    static void setupClient() {
        System.setProperty("localstack.enabled", "true");
        System.setProperty("localstack.s3.endpoint", LOCALSTACK_CONTAINER_CONSUMER.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        System.setProperty("localstack.s3.accessKey", LOCALSTACK_CONTAINER_ACCESS_KEY);
        System.setProperty("localstack.s3.secretKey", LOCALSTACK_CONTAINER_SECRET_KEY);
        System.setProperty("localstack.s3.region", region);
    }

    @BeforeEach
    void setUp() {
        LOCALSTACK_CONTAINER_CONSUMER.start();
        LOCALSTACK_CONTAINER_PROVIDER.start();
        System.out.println("LocalStack Consumer started at: " + LOCALSTACK_CONTAINER_CONSUMER.getEndpoint());
        System.out.println("LocalStack Provider started at: " + LOCALSTACK_CONTAINER_PROVIDER.getEndpoint());
////        localstack.enabled and localstack.s3.endpoint
//        System.setProperty("localstack.enabled", "true");
//        System.setProperty("localstack.s3.endpoint", LOCALSTACK_CONTAINER_CONSUMER.getEndpoint().toString());
//        System.setProperty("localstack.s3.region", region);
//        System.setProperty("localstack.s3.accessKey", LOCALSTACK_CONTAINER_ACCESS_KEY);
//        System.setProperty("localstack.s3.secretKey", LOCALSTACK_CONTAINER_SECRET_KEY);
    }

    @AfterEach
    void tearDown() {
        LOCALSTACK_CONTAINER_CONSUMER.stop();
        LOCALSTACK_CONTAINER_PROVIDER.stop();
    }

    @Test
    public void createUsersAndBuckets() {
        var s3ClientProvider = getS3Client(LOCALSTACK_CONTAINER_CONSUMER.getEndpoint(),
                LOCALSTACK_CONTAINER_PROVIDER.getAccessKey(), LOCALSTACK_CONTAINER_CONSUMER.getSecretKey());
        var iamClientProvider = getIamClient(LOCALSTACK_CONTAINER_CONSUMER.getEndpoint(),
                LOCALSTACK_CONTAINER_PROVIDER.getAccessKey(), LOCALSTACK_CONTAINER_CONSUMER.getSecretKey());

        // create source bucket and upload artifact
        s3ClientProvider.createBucket(CreateBucketRequest.builder()
                .bucket(sourceBucket)
                .build());
        s3ClientProvider.putObject(PutObjectRequest.builder()
                .bucket(sourceBucket)
                .key(sourceObjectName)
                .build(), RequestBody.fromBytes(fileContent.getBytes()));

        // create user policy
        iamClientProvider.createPolicy(CreatePolicyRequest.builder()
                .policyName(sourceUserPolicyName)
                .policyDocument(sourceUserPolicy())
                .build());

        // create user
        iamClientProvider.createUser(CreateUserRequest.builder()
                .userName(sourceUser)
                .build());

        // attach policy to the user
        AttachUserPolicyResponse policyResponse = iamClientProvider.attachUserPolicy(AttachUserPolicyRequest.builder()
                .userName(sourceUser)
                .policyArn("arn:aws:iam::000000000000:policy/" + sourceUserPolicyName)
                .build());

        var sourceCredentials = iamClientProvider.createAccessKey(CreateAccessKeyRequest.builder()
                .userName(sourceUser)
                .build());
        sourceAccessKeyId = sourceCredentials.accessKey().accessKeyId();
        sourceSecretAccessKey = sourceCredentials.accessKey().secretAccessKey();

        System.out.println("Source Access Key ID: " + sourceAccessKeyId);
        System.out.println("Source Secret Access Key: " + sourceSecretAccessKey);

        var s3ClientConsumer = getS3Client(LOCALSTACK_CONTAINER_CONSUMER.getEndpoint(),
                LOCALSTACK_CONTAINER_CONSUMER.getAccessKey(), LOCALSTACK_CONTAINER_CONSUMER.getSecretKey());
        var iamClientConsumer = getIamClient(LOCALSTACK_CONTAINER_CONSUMER.getEndpoint(),
                LOCALSTACK_CONTAINER_CONSUMER.getAccessKey(), LOCALSTACK_CONTAINER_CONSUMER.getSecretKey());

        // consumer
        s3ClientConsumer.createBucket(CreateBucketRequest.builder()
                .bucket(destinationBucket)
                .build());
        iamClientConsumer.createPolicy(CreatePolicyRequest.builder()
                .policyName(destinationUserPolicyName)
                .policyDocument(destinationUserPolicy())
                .build());
        iamClientConsumer.createUser(CreateUserRequest.builder()
                .userName(destinationUser)
                .build());
        iamClientConsumer.attachUserPolicy(AttachUserPolicyRequest.builder()
                .userName(destinationUser)
                .policyArn("arn:aws:iam::000000000000:policy/" + destinationUserPolicyName)
                .build());
        var destinationCredentials = iamClientConsumer.createAccessKey(CreateAccessKeyRequest.builder()
                .userName(destinationUser)
                .build());
        destinationAccessKeyId = destinationCredentials.accessKey().accessKeyId();
        destinationSecretAccessKey = destinationCredentials.accessKey().secretAccessKey();
        System.out.println("Destination Access Key ID: " + destinationAccessKeyId);
        System.out.println("Destination Secret Access Key: " + destinationSecretAccessKey);

        // COPY from one bucket to another
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
        AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials
                .create(sourceAccessKeyId, sourceSecretAccessKey));

        S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .asyncConfiguration(b -> b.advancedOption(FUTURE_COMPLETION_EXECUTOR, executor))
                .credentialsProvider(credentialsProvider)
                .region(Region.of(region))
                .crossRegionAccessEnabled(true)
                .endpointOverride(LOCALSTACK_CONTAINER_PROVIDER.getEndpoint())
                .forcePathStyle(true)
                .build();

        var multipartClient = MultipartS3AsyncClient.create(s3AsyncClient, multipartConfiguration, true);

//        CopyObjectResponse copyObjectResponse = multipartClient.copyObject(copyRequest).join();
        CopyObjectResponse copyObjectResponse = s3ClientProvider.copyObject(copyRequest);

        System.out.println("Copy Object Response: " + copyObjectResponse);
        // Verify the copied object
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(destinationBucket)
                .key(destinationKey)
                .build();

        ResponseInputStream<GetObjectResponse> getObjectResponse = s3ClientConsumer.getObject(getObjectRequest);
        System.out.println("Get Object Response: " + getObjectResponse.response());
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

    private String sourceUserPolicy() {
        return "{\n" +
                "    \"Version\": \"2012-10-17\",\n" +
                "    \"Statement\": [\n" +
                "        {\n" +
                "            \"Sid\": \"iamPermissions\",\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Action\": [\n" +
                "                \"iam:DeleteRolePolicy\",\n" +
                "                \"iam:TagRole\",\n" +
                "                \"iam:CreateRole\",\n" +
                "                \"iam:DeleteRole\",\n" +
                "                \"iam:PutRolePolicy\",\n" +
                "                \"iam:GetUser\"\n" +
                "            ],\n" +
                "            \"Resource\": [\n" +
                "                \"arn:aws:iam::000000000000:role/*\",\n" +
                "                \"arn:aws:iam::000000000000:user/" + sourceUser + "\"\n" +
                "            ]\n" +
                "        },\n" +
                "        {\n" +
                "            \"Sid\": \"stsPermissions\",\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Action\": \"sts:AssumeRole\",\n" +
                "            \"Resource\": \"arn:aws:iam::000000000000:role/*\"\n" +
                "        }\n" +
                "    ]\n" +
                "}";
    }

    private String destinationUserPolicy() {
        return "{\n" +
                "    \"Version\": \"2012-10-17\",\n" +
                "    \"Statement\": [\n" +
                "        {\n" +
                "            \"Sid\": \"s3Permissions\",\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Action\": [\n" +
                "                \"s3:PutBucketPolicy\",\n" +
                "                \"s3:GetBucketPolicy\",\n" +
                "                \"s3:DeleteBucketPolicy\"\n" +
                "            ],\n" +
                "            \"Resource\": \"arn:aws:s3:::" + destinationBucket + "\"\n" +
                "        }\n" +
                "    ]\n" +
                "}";
    }
}
