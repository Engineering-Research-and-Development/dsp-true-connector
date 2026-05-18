package it.eng.tools.s3.service;

import it.eng.tools.exception.S3ServerException;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.repository.BucketCredentialsRepository;
import it.eng.tools.service.FieldEncryptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BucketCredentialsService}.
 */
@ExtendWith(MockitoExtension.class)
class BucketCredentialsServiceTest {

    private static final String BUCKET = "test-bucket";
    private static final String PLAIN_SECRET = "plain-secret";
    private static final String ENCRYPTED_SECRET = "encrypted-secret";

    @Mock
    private FieldEncryptionService fieldEncryptionService;

    @Mock
    private BucketCredentialsRepository bucketCredentialsRepository;

    @InjectMocks
    private BucketCredentialsService service;

    // -------------------------------------------------------------------------
    // getBucketCredentials
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getBucketCredentials returns decrypted credentials when found")
    void getBucketCredentials_found_returnsDecrypted() {
        BucketCredentialsEntity stored = BucketCredentialsEntity.Builder.newInstance()
                .bucketName(BUCKET).accessKey("AKID").secretKey(ENCRYPTED_SECRET).build();
        when(bucketCredentialsRepository.findByBucketName(BUCKET)).thenReturn(Optional.of(stored));
        when(fieldEncryptionService.decrypt(ENCRYPTED_SECRET)).thenReturn(PLAIN_SECRET);

        BucketCredentialsEntity result = service.getBucketCredentials(BUCKET);

        assertEquals(BUCKET, result.getBucketName());
        assertEquals("AKID", result.getAccessKey());
        assertEquals(PLAIN_SECRET, result.getSecretKey());
        verify(fieldEncryptionService).decrypt(ENCRYPTED_SECRET);
    }

    @Test
    @DisplayName("getBucketCredentials throws S3ServerException when not found")
    void getBucketCredentials_notFound_throwsException() {
        when(bucketCredentialsRepository.findByBucketName(BUCKET)).thenReturn(Optional.empty());

        S3ServerException ex = assertThrows(S3ServerException.class,
                () -> service.getBucketCredentials(BUCKET));
        assertTrue(ex.getMessage().contains(BUCKET));
        verifyNoInteractions(fieldEncryptionService);
    }

    // -------------------------------------------------------------------------
    // saveBucketCredentials
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("saveBucketCredentials encrypts secretKey before saving")
    void saveBucketCredentials_encryptsSecretKey() {
        BucketCredentialsEntity input = BucketCredentialsEntity.Builder.newInstance()
                .bucketName(BUCKET).accessKey("AKID").secretKey(PLAIN_SECRET).build();
        when(fieldEncryptionService.encrypt(PLAIN_SECRET)).thenReturn(ENCRYPTED_SECRET);
        when(bucketCredentialsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.saveBucketCredentials(input);

        ArgumentCaptor<BucketCredentialsEntity> captor = ArgumentCaptor.forClass(BucketCredentialsEntity.class);
        verify(bucketCredentialsRepository).save(captor.capture());
        assertEquals(ENCRYPTED_SECRET, captor.getValue().getSecretKey());
        assertEquals(BUCKET, captor.getValue().getBucketName());
        assertEquals("AKID", captor.getValue().getAccessKey());
    }

    @Test
    @DisplayName("saveBucketCredentials returns the repository-saved entity")
    void saveBucketCredentials_returnsRepositoryResult() {
        BucketCredentialsEntity input = BucketCredentialsEntity.Builder.newInstance()
                .bucketName(BUCKET).accessKey("AKID").secretKey(PLAIN_SECRET).build();
        BucketCredentialsEntity saved = BucketCredentialsEntity.Builder.newInstance()
                .bucketName(BUCKET).accessKey("AKID").secretKey(ENCRYPTED_SECRET).build();
        when(fieldEncryptionService.encrypt(PLAIN_SECRET)).thenReturn(ENCRYPTED_SECRET);
        when(bucketCredentialsRepository.save(any())).thenReturn(saved);

        BucketCredentialsEntity result = service.saveBucketCredentials(input);

        assertSame(saved, result);
    }

    // -------------------------------------------------------------------------
    // bucketCredentialsExist
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("bucketCredentialsExist returns true when credentials found")
    void bucketCredentialsExist_found_returnsTrue() {
        BucketCredentialsEntity entity = BucketCredentialsEntity.Builder.newInstance()
                .bucketName(BUCKET).accessKey("ak").secretKey("sk").build();
        when(bucketCredentialsRepository.findByBucketName(BUCKET)).thenReturn(Optional.of(entity));

        assertTrue(service.bucketCredentialsExist(BUCKET));
    }

    @Test
    @DisplayName("bucketCredentialsExist returns false when not found")
    void bucketCredentialsExist_notFound_returnsFalse() {
        when(bucketCredentialsRepository.findByBucketName(BUCKET)).thenReturn(Optional.empty());

        assertFalse(service.bucketCredentialsExist(BUCKET));
    }
}
