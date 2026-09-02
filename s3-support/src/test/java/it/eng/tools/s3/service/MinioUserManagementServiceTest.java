package it.eng.tools.s3.service;

import io.minio.admin.MinioAdminClient;
import io.minio.admin.UserInfo;
import it.eng.tools.exception.S3ServerException;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MinioUserManagementService}.
 */
@ExtendWith(MockitoExtension.class)
class MinioUserManagementServiceTest {

    @Mock
    private MinioAdminClient minioAdminClient;

    @InjectMocks
    private MinioUserManagementService service;

    private static BucketCredentialsEntity creds(String bucket) {
        return BucketCredentialsEntity.Builder.newInstance()
                .bucketName(bucket).accessKey("test-user").secretKey("test-secret").build();
    }

    // -------------------------------------------------------------------------
    // createUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createUser skips creation when user already exists in MinIO")
    void createUser_userAlreadyExists_skipsCreation() throws Exception {
        when(minioAdminClient.getUserInfo("test-user")).thenReturn(mock(UserInfo.class));

        assertDoesNotThrow(() -> service.createUser(creds("my-bucket")));
        verify(minioAdminClient, never()).addUser(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("createUser calls addUser when user does not exist")
    void createUser_userDoesNotExist_createsUser() throws Exception {
        when(minioAdminClient.getUserInfo("test-user")).thenThrow(new RuntimeException("not found"));
        doNothing().when(minioAdminClient).addUser(eq("test-user"), any(), eq("test-secret"), isNull(), isNull());

        assertDoesNotThrow(() -> service.createUser(creds("my-bucket")));
        verify(minioAdminClient).addUser(eq("test-user"), any(UserInfo.Status.class), eq("test-secret"), isNull(), isNull());
    }

    @Test
    @DisplayName("createUser throws S3ServerException when addUser fails")
    void createUser_addUserFails_throwsS3ServerException() throws Exception {
        when(minioAdminClient.getUserInfo("test-user")).thenThrow(new RuntimeException("not found"));
        doThrow(new RuntimeException("MinIO error")).when(minioAdminClient)
                .addUser(any(), any(), any(), any(), any());

        assertThrows(S3ServerException.class, () -> service.createUser(creds("my-bucket")));
    }

    // -------------------------------------------------------------------------
    // attachPolicyToUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("attachPolicyToUser attaches built-in consoleAdmin policy to user")
    void attachPolicyToUser_success() throws Exception {
        doNothing().when(minioAdminClient).setPolicy(anyString(), eq(false), anyString());

        assertDoesNotThrow(() -> service.attachPolicyToUser(creds("my-bucket")));

        verify(minioAdminClient).setPolicy(eq("test-user"), eq(false), eq("consoleAdmin"));
    }

    @Test
    @DisplayName("attachPolicyToUser uses consoleAdmin instead of a generated canned policy")
    void attachPolicyToUser_usesConsoleAdminPolicy() throws Exception {
        doNothing().when(minioAdminClient).setPolicy(anyString(), eq(false), anyString());

        service.attachPolicyToUser(creds("tenant-bucket"));

        verify(minioAdminClient, never()).addCannedPolicy(anyString(), anyString());
        verify(minioAdminClient).setPolicy(eq("test-user"), eq(false), eq("consoleAdmin"));
    }

    @Test
    @DisplayName("attachPolicyToUser throws S3ServerException when MinIO call fails")
    void attachPolicyToUser_minioError_throwsS3ServerException() throws Exception {
        doThrow(new RuntimeException("policy error")).when(minioAdminClient)
                .setPolicy(anyString(), eq(false), anyString());

        assertThrows(S3ServerException.class, () -> service.attachPolicyToUser(creds("my-bucket")));
    }

    // -------------------------------------------------------------------------
    // attachTemporaryPolicy
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("attachTemporaryPolicy creates and attaches temp policy")
    void attachTemporaryPolicy_success() throws Exception {
        doNothing().when(minioAdminClient).addCannedPolicy(anyString(), anyString());
        doNothing().when(minioAdminClient).setPolicy(anyString(), eq(false), anyString());

        assertDoesNotThrow(() -> service.attachTemporaryPolicy(creds("tenant-bucket"), "tp-user",
                "tp-policy", "{\"Version\":\"2012-10-17\"}"));

        verify(minioAdminClient).addCannedPolicy(eq("tp-policy"), anyString());
        verify(minioAdminClient).setPolicy(eq("tp-user"), eq(false), eq("tp-policy"));
    }

    @Test
    @DisplayName("attachTemporaryPolicy throws S3ServerException on MinIO failure")
    void attachTemporaryPolicy_failure_throwsS3ServerException() throws Exception {
        doThrow(new RuntimeException("minio error")).when(minioAdminClient)
                .addCannedPolicy(anyString(), anyString());

        assertThrows(S3ServerException.class,
                () -> service.attachTemporaryPolicy(creds("tenant-bucket"), "tp-user", "tp-policy", "{}"));
    }

    // -------------------------------------------------------------------------
    // deleteUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deleteUser calls MinIO deleteUser successfully")
    void deleteUser_success() throws Exception {
        doNothing().when(minioAdminClient).deleteUser("test-user");

        assertDoesNotThrow(() -> service.deleteUser(creds("tenant-bucket"), "test-user"));
        verify(minioAdminClient).deleteUser("test-user");
    }

    @Test
    @DisplayName("deleteUser throws S3ServerException on MinIO failure")
    void deleteUser_failure_throwsS3ServerException() throws Exception {
        doThrow(new RuntimeException("delete error")).when(minioAdminClient).deleteUser("test-user");

        assertThrows(S3ServerException.class, () -> service.deleteUser(creds("tenant-bucket"), "test-user"));
    }

    // -------------------------------------------------------------------------
    // deletePolicy
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deletePolicy calls MinIO removeCannedPolicy successfully")
    void deletePolicy_success() throws Exception {
        doNothing().when(minioAdminClient).removeCannedPolicy("my-policy");

        assertDoesNotThrow(() -> service.deletePolicy(creds("tenant-bucket"), "my-policy"));
        verify(minioAdminClient).removeCannedPolicy("my-policy");
    }

    @Test
    @DisplayName("deletePolicy throws S3ServerException on MinIO failure")
    void deletePolicy_failure_throwsS3ServerException() throws Exception {
        doThrow(new RuntimeException("remove error")).when(minioAdminClient).removeCannedPolicy("my-policy");

        assertThrows(S3ServerException.class, () -> service.deletePolicy(creds("tenant-bucket"), "my-policy"));
    }
}
