package it.eng.tools.s3.service;

import it.eng.tools.exception.S3ServerException;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.TemporaryBucketUser;
import it.eng.tools.s3.repository.TemporaryBucketUserRepository;
import it.eng.tools.service.FieldEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TemporaryBucketUserServiceTest {

    private static final String TRANSFER_PROCESS_ID = "transfer-process-123";
    private static final String BUCKET_NAME = "test-bucket";
    private static final String OBJECT_KEY = "data/object.bin";
    private static final String PLAIN_SECRET_KEY = "plain-secret-key";
    private static final String ENCRYPTED_SECRET_KEY = "encrypted-secret-key";

    @Mock
    private IamUserManagementService iamUserManagementService;

    @Mock
    private TemporaryBucketUserRepository temporaryBucketUserRepository;

    @Mock
    private FieldEncryptionService fieldEncryptionService;

    private TemporaryBucketUserService service;

    @BeforeEach
    void setUp() {
        service = new TemporaryBucketUserService(iamUserManagementService, temporaryBucketUserRepository, fieldEncryptionService);
    }

    // -------------------------------------------------------------------------
    // createTemporaryUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createTemporaryUser - should create IAM user, attach scoped policy, persist with encrypted key, return plain key")
    void createTemporaryUser_success() {
        when(fieldEncryptionService.encrypt(anyString())).thenReturn(ENCRYPTED_SECRET_KEY);
        when(temporaryBucketUserRepository.save(any(TemporaryBucketUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TemporaryBucketUser result = service.createTemporaryUser(TRANSFER_PROCESS_ID, BUCKET_NAME, OBJECT_KEY);

        // IAM user created
        ArgumentCaptor<BucketCredentialsEntity> iamCaptor = ArgumentCaptor.forClass(BucketCredentialsEntity.class);
        verify(iamUserManagementService).createUser(iamCaptor.capture());
        assertTrue(iamCaptor.getValue().getAccessKey().startsWith("TempUser-"),
                "Access key should be prefixed with TempUser-");

        // Scoped policy attached with correct naming
        ArgumentCaptor<String> policyNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> policyJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(iamUserManagementService).attachTemporaryPolicy(
                anyString(), policyNameCaptor.capture(), policyJsonCaptor.capture());
        assertTrue(policyNameCaptor.getValue().contains(TRANSFER_PROCESS_ID),
                "Policy name should contain transferProcessId");
        assertTrue(policyJsonCaptor.getValue().contains(BUCKET_NAME));
        assertTrue(policyJsonCaptor.getValue().contains(OBJECT_KEY));
        assertTrue(policyJsonCaptor.getValue().contains("s3:PutObject"));

        // Persisted entity has encrypted secret key
        ArgumentCaptor<TemporaryBucketUser> savedCaptor = ArgumentCaptor.forClass(TemporaryBucketUser.class);
        verify(temporaryBucketUserRepository).save(savedCaptor.capture());
        assertEquals(ENCRYPTED_SECRET_KEY, savedCaptor.getValue().getSecretKey());
        assertEquals(TRANSFER_PROCESS_ID, savedCaptor.getValue().getTransferProcessId());
        assertEquals(BUCKET_NAME, savedCaptor.getValue().getBucketName());
        assertEquals(OBJECT_KEY, savedCaptor.getValue().getObjectKey());

        // Returned entity carries the PLAIN key for immediate use
        assertNotNull(result);
        assertNotEquals(ENCRYPTED_SECRET_KEY, result.getSecretKey(),
                "Returned entity must expose plain secret key, not encrypted");
        assertEquals(TRANSFER_PROCESS_ID, result.getTransferProcessId());
        assertEquals(BUCKET_NAME, result.getBucketName());
        assertEquals(OBJECT_KEY, result.getObjectKey());
        assertTrue(result.getAccessKey().startsWith("TempUser-"));
    }

    @Test
    @DisplayName("createTemporaryUser - generated access keys are unique across calls")
    void createTemporaryUser_accessKeysAreUnique() {
        when(fieldEncryptionService.encrypt(anyString())).thenReturn(ENCRYPTED_SECRET_KEY);
        when(temporaryBucketUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TemporaryBucketUser first = service.createTemporaryUser("tp-1", BUCKET_NAME, OBJECT_KEY);
        TemporaryBucketUser second = service.createTemporaryUser("tp-2", BUCKET_NAME, OBJECT_KEY);

        assertNotEquals(first.getAccessKey(), second.getAccessKey());
        assertNotEquals(first.getSecretKey(), second.getSecretKey());
    }

    // -------------------------------------------------------------------------
    // getTemporaryUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getTemporaryUser - found: returns entity with decrypted secret key")
    void getTemporaryUser_found() {
        TemporaryBucketUser stored = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId(TRANSFER_PROCESS_ID)
                .accessKey("TempUser-abc12345")
                .secretKey(ENCRYPTED_SECRET_KEY)
                .bucketName(BUCKET_NAME)
                .objectKey(OBJECT_KEY)
                .build();

        when(temporaryBucketUserRepository.findById(TRANSFER_PROCESS_ID)).thenReturn(Optional.of(stored));
        when(fieldEncryptionService.decrypt(ENCRYPTED_SECRET_KEY)).thenReturn(PLAIN_SECRET_KEY);

        TemporaryBucketUser result = service.getTemporaryUser(TRANSFER_PROCESS_ID);

        assertEquals(TRANSFER_PROCESS_ID, result.getTransferProcessId());
        assertEquals("TempUser-abc12345", result.getAccessKey());
        assertEquals(PLAIN_SECRET_KEY, result.getSecretKey());
        assertEquals(BUCKET_NAME, result.getBucketName());
        assertEquals(OBJECT_KEY, result.getObjectKey());
    }

    @Test
    @DisplayName("getTemporaryUser - not found: throws S3ServerException")
    void getTemporaryUser_notFound() {
        when(temporaryBucketUserRepository.findById(TRANSFER_PROCESS_ID)).thenReturn(Optional.empty());

        S3ServerException ex = assertThrows(S3ServerException.class,
                () -> service.getTemporaryUser(TRANSFER_PROCESS_ID));

        assertTrue(ex.getMessage().contains(TRANSFER_PROCESS_ID));
        verify(fieldEncryptionService, never()).decrypt(anyString());
    }

    // -------------------------------------------------------------------------
    // deleteTemporaryUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deleteTemporaryUser - found: deletes IAM user, policy, and MongoDB document")
    void deleteTemporaryUser_found() {
        TemporaryBucketUser stored = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId(TRANSFER_PROCESS_ID)
                .accessKey("TempUser-abc12345")
                .secretKey(ENCRYPTED_SECRET_KEY)
                .bucketName(BUCKET_NAME)
                .objectKey(OBJECT_KEY)
                .build();

        when(temporaryBucketUserRepository.findById(TRANSFER_PROCESS_ID)).thenReturn(Optional.of(stored));

        service.deleteTemporaryUser(TRANSFER_PROCESS_ID);

        verify(iamUserManagementService).deleteUser("TempUser-abc12345");
        verify(iamUserManagementService).deletePolicy(contains(TRANSFER_PROCESS_ID));
        verify(temporaryBucketUserRepository).deleteById(TRANSFER_PROCESS_ID);
    }

    @Test
    @DisplayName("deleteTemporaryUser - not found: no IAM or DB operations performed")
    void deleteTemporaryUser_notFound() {
        when(temporaryBucketUserRepository.findById(TRANSFER_PROCESS_ID)).thenReturn(Optional.empty());

        service.deleteTemporaryUser(TRANSFER_PROCESS_ID);

        verify(iamUserManagementService, never()).deleteUser(anyString());
        verify(iamUserManagementService, never()).deletePolicy(anyString());
        verify(temporaryBucketUserRepository, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("deleteTemporaryUser - IAM deleteUser fails: still deletes policy and MongoDB document")
    void deleteTemporaryUser_iamDeleteUserFails_continuesCleanup() {
        TemporaryBucketUser stored = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId(TRANSFER_PROCESS_ID)
                .accessKey("TempUser-abc12345")
                .secretKey(ENCRYPTED_SECRET_KEY)
                .bucketName(BUCKET_NAME)
                .objectKey(OBJECT_KEY)
                .build();

        when(temporaryBucketUserRepository.findById(TRANSFER_PROCESS_ID)).thenReturn(Optional.of(stored));
        doThrow(new RuntimeException("MinIO unavailable")).when(iamUserManagementService).deleteUser(anyString());

        assertDoesNotThrow(() -> service.deleteTemporaryUser(TRANSFER_PROCESS_ID));

        verify(iamUserManagementService).deletePolicy(contains(TRANSFER_PROCESS_ID));
        verify(temporaryBucketUserRepository).deleteById(TRANSFER_PROCESS_ID);
    }

    @Test
    @DisplayName("deleteTemporaryUser - IAM deletePolicy fails: MongoDB document is still removed")
    void deleteTemporaryUser_iamDeletePolicyFails_stillDeletesMongoDocument() {
        TemporaryBucketUser stored = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId(TRANSFER_PROCESS_ID)
                .accessKey("TempUser-abc12345")
                .secretKey(ENCRYPTED_SECRET_KEY)
                .bucketName(BUCKET_NAME)
                .objectKey(OBJECT_KEY)
                .build();

        when(temporaryBucketUserRepository.findById(TRANSFER_PROCESS_ID)).thenReturn(Optional.of(stored));
        doThrow(new RuntimeException("policy not found")).when(iamUserManagementService).deletePolicy(anyString());

        assertDoesNotThrow(() -> service.deleteTemporaryUser(TRANSFER_PROCESS_ID));

        verify(temporaryBucketUserRepository).deleteById(TRANSFER_PROCESS_ID);
    }

    @Test
    @DisplayName("deleteTemporaryUser - both IAM calls fail: MongoDB document is still removed")
    void deleteTemporaryUser_bothIamCallsFail_stillDeletesMongoDocument() {
        TemporaryBucketUser stored = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId(TRANSFER_PROCESS_ID)
                .accessKey("TempUser-abc12345")
                .secretKey(ENCRYPTED_SECRET_KEY)
                .bucketName(BUCKET_NAME)
                .objectKey(OBJECT_KEY)
                .build();

        when(temporaryBucketUserRepository.findById(TRANSFER_PROCESS_ID)).thenReturn(Optional.of(stored));
        doThrow(new RuntimeException("MinIO unavailable")).when(iamUserManagementService).deleteUser(anyString());
        doThrow(new RuntimeException("MinIO unavailable")).when(iamUserManagementService).deletePolicy(anyString());

        assertDoesNotThrow(() -> service.deleteTemporaryUser(TRANSFER_PROCESS_ID));

        verify(temporaryBucketUserRepository).deleteById(TRANSFER_PROCESS_ID);
    }
}
