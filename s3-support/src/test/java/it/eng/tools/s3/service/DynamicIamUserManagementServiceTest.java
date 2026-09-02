package it.eng.tools.s3.service;

import io.minio.admin.MinioAdminClient;
import it.eng.tools.s3.configuration.MinioAdminClientFactory;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicIamUserManagementServiceTest {

    @Mock
    private MinioAdminClientFactory minioAdminClientFactory;

    @Mock
    private MinioAdminClient minioAdminClient;

    private DynamicIamUserManagementService service;

    @BeforeEach
    void setUp() {
        service = new DynamicIamUserManagementService(minioAdminClientFactory);
    }

    // -------------------------------------------------------------------------
    // Minio routing (non-AWS endpoint)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createUser (2-param) - Minio endpoint: uses management credentials for admin client, user credentials for addUser")
    void createUser_minioEndpoint_usesMgmtCredsForAdminClientAndUserCredsForAddUser() throws Exception {
        when(minioAdminClientFactory.get("http://minio:9000", "admin", "admin-secret"))
                .thenReturn(minioAdminClient);
        // MinioAdminClient.getUserInfo throws when user not found ÔåÆ createUser proceeds to addUser
        doThrow(new RuntimeException("not found")).when(minioAdminClient).getUserInfo("TempUser-abc");
        doNothing().when(minioAdminClient).addUser(eq("TempUser-abc"), any(), eq("temp-sk"), isNull(), isNull());

        BucketCredentialsEntity userCreds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket").accessKey("TempUser-abc").secretKey("temp-sk")
                .build();

        BucketCredentialsEntity mgmtCreds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket").accessKey("admin").secretKey("admin-secret")
                .endpointOverride("http://minio:9000")
                .build();

        service.createUser(userCreds, mgmtCreds);

        verify(minioAdminClientFactory).get("http://minio:9000", "admin", "admin-secret");
        verify(minioAdminClient).addUser(eq("TempUser-abc"), any(), eq("temp-sk"), isNull(), isNull());
    }

    @Test
    @DisplayName("createUser (1-param, no mgmt) - blank endpoint routes to AWS no-op")
    void createUser_oneParam_noEndpoint_routesToAwsNoOp() {
        BucketCredentialsEntity creds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket").accessKey("TempUser-abc").secretKey("sk")
                .build(); // no endpointOverride ÔåÆ AWS

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.createUser(creds));
        verifyNoInteractions(minioAdminClientFactory);
    }

    @Test
    @DisplayName("attachTemporaryPolicy - Minio endpoint: delegates to MinioUserManagementService via factory")
    void attachTemporaryPolicy_minioEndpoint_delegatesToMinioService() throws Exception {
        when(minioAdminClientFactory.get("http://minio:9000", "admin", "secret"))
                .thenReturn(minioAdminClient);
        doNothing().when(minioAdminClient).addCannedPolicy(anyString(), anyString());
        doNothing().when(minioAdminClient).setPolicy(anyString(), eq(false), anyString());

        BucketCredentialsEntity mgmtCreds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket").accessKey("admin").secretKey("secret")
                .endpointOverride("http://minio:9000")
                .build();

        service.attachTemporaryPolicy(mgmtCreds, "TempUser-abc", "my-policy", "{}");

        verify(minioAdminClientFactory).get("http://minio:9000", "admin", "secret");
        verify(minioAdminClient).addCannedPolicy("my-policy", "{}");
        verify(minioAdminClient).setPolicy("TempUser-abc", false, "my-policy");
    }

    @Test
    @DisplayName("deleteUser - Minio endpoint: delegates to MinioUserManagementService")
    void deleteUser_minioEndpoint_delegatesToMinioService() throws Exception {
        when(minioAdminClientFactory.get("http://minio:9000", "admin", "secret"))
                .thenReturn(minioAdminClient);
        doNothing().when(minioAdminClient).deleteUser("TempUser-abc");

        BucketCredentialsEntity mgmtCreds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket").accessKey("admin").secretKey("secret")
                .endpointOverride("http://minio:9000")
                .build();

        service.deleteUser(mgmtCreds, "TempUser-abc");

        verify(minioAdminClient).deleteUser("TempUser-abc");
    }

    @Test
    @DisplayName("deletePolicy - Minio endpoint: delegates to MinioUserManagementService")
    void deletePolicy_minioEndpoint_delegatesToMinioService() throws Exception {
        when(minioAdminClientFactory.get("http://minio:9000", "admin", "secret"))
                .thenReturn(minioAdminClient);
        doNothing().when(minioAdminClient).removeCannedPolicy("my-policy");

        BucketCredentialsEntity mgmtCreds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket").accessKey("admin").secretKey("secret")
                .endpointOverride("http://minio:9000")
                .build();

        service.deletePolicy(mgmtCreds, "my-policy");

        verify(minioAdminClient).removeCannedPolicy("my-policy");
    }

    // -------------------------------------------------------------------------
    // AWS routing (blank or amazonaws.com endpoint)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("attachTemporaryPolicy - blank endpoint (AWS): routes to AwsUserManagementService which throws UnsupportedOperationException")
    void attachTemporaryPolicy_blankEndpoint_routesToAwsStub() {
        BucketCredentialsEntity mgmtCreds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket").accessKey("aws-ak").secretKey("aws-sk")
                .build(); // no endpointOverride ÔåÆ AWS

        assertThrows(UnsupportedOperationException.class,
                () -> service.attachTemporaryPolicy(mgmtCreds, "TempUser-abc", "policy", "{}"));

        verifyNoInteractions(minioAdminClientFactory);
    }

    @Test
    @DisplayName("attachTemporaryPolicy - amazonaws.com endpoint: routes to AwsUserManagementService which throws")
    void attachTemporaryPolicy_awsEndpoint_routesToAwsStub() {
        BucketCredentialsEntity mgmtCreds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket").accessKey("aws-ak").secretKey("aws-sk")
                .endpointOverride("https://s3.amazonaws.com")
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> service.attachTemporaryPolicy(mgmtCreds, "TempUser-abc", "policy", "{}"));

        verifyNoInteractions(minioAdminClientFactory);
    }

    // -------------------------------------------------------------------------
    // Endpoint detection edge cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Minio detection: endpoint with .aws. in URL is treated as AWS")
    void awsDetection_dotAwsDot_routesToAwsStub() {
        BucketCredentialsEntity mgmtCreds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket").accessKey("k").secretKey("s")
                .endpointOverride("https://bucket.s3.aws.example.com")
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> service.attachTemporaryPolicy(mgmtCreds, "user", "policy", "{}"));
    }

    @Test
    @DisplayName("Minio detection: custom endpoint without AWS markers routes to Minio")
    void minioDetection_customEndpoint_routesToMinio() throws Exception {
        when(minioAdminClientFactory.get("http://custom-s3:9000", "admin", "s"))
                .thenReturn(minioAdminClient);
        doNothing().when(minioAdminClient).addCannedPolicy(anyString(), anyString());
        doNothing().when(minioAdminClient).setPolicy(anyString(), eq(false), anyString());

        BucketCredentialsEntity mgmtCreds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket").accessKey("admin").secretKey("s")
                .endpointOverride("http://custom-s3:9000")
                .build();

        service.attachTemporaryPolicy(mgmtCreds, "user", "policy", "{}");

        verify(minioAdminClientFactory).get("http://custom-s3:9000", "admin", "s");
    }
}