package it.eng.tools.s3.service;

import it.eng.tools.exception.S3ServerException;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.TemporaryBucketUser;
import it.eng.tools.s3.properties.S3Properties;
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
import static org.mockito.Mockito.*;

import org.mockito.InOrder;

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

    @Mock
    private S3Properties s3Properties;

    private TemporaryBucketUserService service;

    @BeforeEach
    void setUp() {
        service = new TemporaryBucketUserService(iamUserManagementService, temporaryBucketUserRepository,
                fieldEncryptionService, s3Properties);
        lenient().when(s3Properties.getAccessKey()).thenReturn("minioadmin");
        lenient().when(s3Properties.getSecretKey()).thenReturn("minioadmin-secret");
    }

    // -------------------------------------------------------------------------
    // createTemporaryUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createTemporaryUser (bootstrap) - persists encrypted key, stores bootstrap mgmt credentials, returns plain key")
    void createTemporaryUser_success() {
        when(fieldEncryptionService.encrypt(anyString())).thenReturn(ENCRYPTED_SECRET_KEY);
        when(temporaryBucketUserRepository.save(any(TemporaryBucketUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TemporaryBucketUser result = service.createTemporaryUser(TRANSFER_PROCESS_ID, BUCKET_NAME, OBJECT_KEY);

        // IAM user created with temp user credentials and bootstrap management credentials
        ArgumentCaptor<BucketCredentialsEntity> iamCaptor = ArgumentCaptor.forClass(BucketCredentialsEntity.class);
        verify(iamUserManagementService).createUser(iamCaptor.capture(),
                argThat(mgmt -> "minioadmin".equals(mgmt.getAccessKey())));
        assertTrue(iamCaptor.getValue().getAccessKey().startsWith("TempUser-"),
                "Access key should be prefixed with TempUser-");

        // Scoped policy attached with correct naming
        ArgumentCaptor<String> policyNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> policyJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(iamUserManagementService).attachTemporaryPolicy(
                argThat(managementCredentials -> BUCKET_NAME.equals(managementCredentials.getBucketName())
                        && "minioadmin".equals(managementCredentials.getAccessKey())
                        && "minioadmin-secret".equals(managementCredentials.getSecretKey())),
                anyString(), policyNameCaptor.capture(), policyJsonCaptor.capture());
        assertTrue(policyNameCaptor.getValue().contains(TRANSFER_PROCESS_ID),
                "Policy name should contain transferProcessId");
        assertTrue(policyJsonCaptor.getValue().contains(BUCKET_NAME));
        assertTrue(policyJsonCaptor.getValue().contains(OBJECT_KEY));
        assertTrue(policyJsonCaptor.getValue().contains("s3:PutObject"));

        // Persisted entity has encrypted secret key and stored bootstrap mgmt credentials
        ArgumentCaptor<TemporaryBucketUser> savedCaptor = ArgumentCaptor.forClass(TemporaryBucketUser.class);
        verify(temporaryBucketUserRepository).save(savedCaptor.capture());
        TemporaryBucketUser saved = savedCaptor.getValue();
        assertEquals(ENCRYPTED_SECRET_KEY, saved.getSecretKey());
        assertEquals(TRANSFER_PROCESS_ID, saved.getTransferProcessId());
        assertEquals(BUCKET_NAME, saved.getBucketName());
        assertEquals(OBJECT_KEY, saved.getObjectKey());
        assertEquals("minioadmin", saved.getMgmtAccessKey());
        assertEquals(ENCRYPTED_SECRET_KEY, saved.getMgmtSecretKey());

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
    @DisplayName("createTemporaryUser (explicit mgmt creds) - stores mgmt access key, encrypted secret, and endpoint")
    void createTemporaryUser_withExplicitManagementCredentials_storesMgmtCredentials() {
        when(fieldEncryptionService.encrypt(anyString())).thenReturn(ENCRYPTED_SECRET_KEY);
        when(temporaryBucketUserRepository.save(any(TemporaryBucketUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BucketCredentialsEntity mgmtCreds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName(BUCKET_NAME)
                .accessKey("dp-admin-key")
                .secretKey("dp-admin-secret")
                .endpointOverride("http://minio:9000")
                .build();

        service.createTemporaryUser(TRANSFER_PROCESS_ID, mgmtCreds, BUCKET_NAME, OBJECT_KEY);

        // Verify createUser was called with user adapter AND management credentials
        verify(iamUserManagementService).createUser(
                argThat(user -> user.getAccessKey().startsWith("TempUser-")),
                argThat(mgmt -> "dp-admin-key".equals(mgmt.getAccessKey())
                        && "http://minio:9000".equals(mgmt.getEndpointOverride())));

        ArgumentCaptor<TemporaryBucketUser> savedCaptor = ArgumentCaptor.forClass(TemporaryBucketUser.class);
        verify(temporaryBucketUserRepository).save(savedCaptor.capture());
        TemporaryBucketUser saved = savedCaptor.getValue();

        assertEquals("dp-admin-key", saved.getMgmtAccessKey());
        assertEquals(ENCRYPTED_SECRET_KEY, saved.getMgmtSecretKey());
        assertEquals("http://minio:9000", saved.getMgmtEndpoint());
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
    @DisplayName("deleteTemporaryUser (legacy CP entity) - uses bootstrap properties when no mgmt credentials stored")
    void deleteTemporaryUser_found_legacyEntityFallsBackToBootstrap() {
        // Pre-migration entity: no mgmtAccessKey stored ÔåÆ resolveManagementCredentials falls
        // back to bootstrapManagementCredentials which reads from S3Properties
        TemporaryBucketUser stored = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId(TRANSFER_PROCESS_ID)
                .accessKey("TempUser-abc12345")
                .secretKey(ENCRYPTED_SECRET_KEY)
                .bucketName(BUCKET_NAME)
                .objectKey(OBJECT_KEY)
                .build();

        when(temporaryBucketUserRepository.findById(TRANSFER_PROCESS_ID)).thenReturn(Optional.of(stored));
        service.deleteTemporaryUser(TRANSFER_PROCESS_ID);

        // User must be deleted before the policy — Minio rejects policy deletion while it is
        // still attached to a user (XMinioIAMPolicyInUse). Deleting the user first releases
        // the attachment so the subsequent policy deletion succeeds.
        InOrder order = inOrder(iamUserManagementService);
        order.verify(iamUserManagementService).deleteUser(argThat(managementCredentials ->
                BUCKET_NAME.equals(managementCredentials.getBucketName())
                        && "minioadmin".equals(managementCredentials.getAccessKey())
                        && "minioadmin-secret".equals(managementCredentials.getSecretKey())), eq("TempUser-abc12345"));
        order.verify(iamUserManagementService).deletePolicy(argThat(managementCredentials ->
                BUCKET_NAME.equals(managementCredentials.getBucketName())
                        && "minioadmin".equals(managementCredentials.getAccessKey())
                        && "minioadmin-secret".equals(managementCredentials.getSecretKey())), contains(TRANSFER_PROCESS_ID));
        verify(temporaryBucketUserRepository).deleteById(TRANSFER_PROCESS_ID);
    }

    @Test
    @DisplayName("deleteTemporaryUser (data-plane entity) - uses stored mgmt credentials, no S3Properties needed")
    void deleteTemporaryUser_found_dataPlaneEntityUsesStoredMgmtCredentials() {
        when(fieldEncryptionService.decrypt(ENCRYPTED_SECRET_KEY)).thenReturn("dp-admin-secret");

        TemporaryBucketUser stored = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId(TRANSFER_PROCESS_ID)
                .accessKey("TempUser-abc12345")
                .secretKey(ENCRYPTED_SECRET_KEY)
                .bucketName(BUCKET_NAME)
                .objectKey(OBJECT_KEY)
                .mgmtAccessKey("dp-admin-key")
                .mgmtSecretKey(ENCRYPTED_SECRET_KEY)
                .mgmtEndpoint("http://minio:9000")
                .build();

        when(temporaryBucketUserRepository.findById(TRANSFER_PROCESS_ID)).thenReturn(Optional.of(stored));
        service.deleteTemporaryUser(TRANSFER_PROCESS_ID);

        // Should use stored mgmt credentials, not bootstrap S3Properties
        verify(s3Properties, never()).getAccessKey();
        InOrder order = inOrder(iamUserManagementService);
        order.verify(iamUserManagementService).deleteUser(argThat(creds ->
                "dp-admin-key".equals(creds.getAccessKey())
                        && "dp-admin-secret".equals(creds.getSecretKey())
                        && "http://minio:9000".equals(creds.getEndpointOverride())), eq("TempUser-abc12345"));
        order.verify(iamUserManagementService).deletePolicy(argThat(creds ->
                "dp-admin-key".equals(creds.getAccessKey())), contains(TRANSFER_PROCESS_ID));
        verify(temporaryBucketUserRepository).deleteById(TRANSFER_PROCESS_ID);
    }

    @Test
    @DisplayName("deleteTemporaryUser - not found: no IAM or DB operations performed")
    void deleteTemporaryUser_notFound() {        when(temporaryBucketUserRepository.findById(TRANSFER_PROCESS_ID)).thenReturn(Optional.empty());

        service.deleteTemporaryUser(TRANSFER_PROCESS_ID);

        verify(iamUserManagementService, never()).deleteUser(any(BucketCredentialsEntity.class), anyString());
        verify(iamUserManagementService, never()).deletePolicy(any(BucketCredentialsEntity.class), anyString());
        verify(temporaryBucketUserRepository, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("deleteTemporaryUser - user deletion fails: still attempts policy deletion and removes MongoDB document")
    void deleteTemporaryUser_iamDeleteUserFails_continuesCleanup() {
        TemporaryBucketUser stored = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId(TRANSFER_PROCESS_ID)
                .accessKey("TempUser-abc12345")
                .secretKey(ENCRYPTED_SECRET_KEY)
                .bucketName(BUCKET_NAME)
                .objectKey(OBJECT_KEY)
                .build();

        when(temporaryBucketUserRepository.findById(TRANSFER_PROCESS_ID)).thenReturn(Optional.of(stored));
        doThrow(new RuntimeException("MinIO unavailable")).when(iamUserManagementService)
                .deleteUser(any(BucketCredentialsEntity.class), anyString());

        assertDoesNotThrow(() -> service.deleteTemporaryUser(TRANSFER_PROCESS_ID));

        verify(iamUserManagementService).deletePolicy(any(BucketCredentialsEntity.class), contains(TRANSFER_PROCESS_ID));
        verify(temporaryBucketUserRepository).deleteById(TRANSFER_PROCESS_ID);
    }

    @Test
    @DisplayName("deleteTemporaryUser - policy deletion fails after user is deleted: MongoDB document is still removed")
    void deleteTemporaryUser_iamDeletePolicyFails_stillDeletesMongoDocument() {
        TemporaryBucketUser stored = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId(TRANSFER_PROCESS_ID)
                .accessKey("TempUser-abc12345")
                .secretKey(ENCRYPTED_SECRET_KEY)
                .bucketName(BUCKET_NAME)
                .objectKey(OBJECT_KEY)
                .build();

        when(temporaryBucketUserRepository.findById(TRANSFER_PROCESS_ID)).thenReturn(Optional.of(stored));
        doThrow(new RuntimeException("policy not found")).when(iamUserManagementService)
                .deletePolicy(any(BucketCredentialsEntity.class), anyString());

        assertDoesNotThrow(() -> service.deleteTemporaryUser(TRANSFER_PROCESS_ID));

        verify(iamUserManagementService).deleteUser(any(BucketCredentialsEntity.class), eq("TempUser-abc12345"));
        verify(temporaryBucketUserRepository).deleteById(TRANSFER_PROCESS_ID);
    }

    @Test
    @DisplayName("deleteTemporaryUser - XMinioIAMPolicyInUse never occurs because user is deleted before policy")
    void deleteTemporaryUser_userDeletedFirst_policyInUseErrorAvoided() {
        // Regression test: before the fix the policy was deleted first, causing Minio to
        // return XMinioIAMPolicyInUse because the policy was still attached to the user.
        TemporaryBucketUser stored = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId(TRANSFER_PROCESS_ID)
                .accessKey("TempUser-abc12345")
                .secretKey(ENCRYPTED_SECRET_KEY)
                .bucketName(BUCKET_NAME)
                .objectKey(OBJECT_KEY)
                .build();

        when(temporaryBucketUserRepository.findById(TRANSFER_PROCESS_ID)).thenReturn(Optional.of(stored));
        // Simulate the old broken ordering: policy deletion would throw XMinioIAMPolicyInUse
        // when called BEFORE the user is removed. With the fix the user is removed first so
        // this stub is never reached and deletePolicy succeeds without error.
        doAnswer(invocation -> {
            // Verify the user was already deleted before this call
            verify(iamUserManagementService).deleteUser(any(BucketCredentialsEntity.class), eq("TempUser-abc12345"));
            return null;
        }).when(iamUserManagementService).deletePolicy(any(BucketCredentialsEntity.class), anyString());

        service.deleteTemporaryUser(TRANSFER_PROCESS_ID);

        InOrder order = inOrder(iamUserManagementService);
        order.verify(iamUserManagementService).deleteUser(any(BucketCredentialsEntity.class), eq("TempUser-abc12345"));
        order.verify(iamUserManagementService).deletePolicy(any(BucketCredentialsEntity.class), contains(TRANSFER_PROCESS_ID));
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
        doThrow(new RuntimeException("MinIO unavailable")).when(iamUserManagementService)
                .deleteUser(any(BucketCredentialsEntity.class), anyString());
        doThrow(new RuntimeException("MinIO unavailable")).when(iamUserManagementService)
                .deletePolicy(any(BucketCredentialsEntity.class), anyString());

        assertDoesNotThrow(() -> service.deleteTemporaryUser(TRANSFER_PROCESS_ID));

        verify(temporaryBucketUserRepository).deleteById(TRANSFER_PROCESS_ID);
    }
}
