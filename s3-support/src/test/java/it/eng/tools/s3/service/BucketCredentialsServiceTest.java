package it.eng.tools.s3.service;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BucketCredentialsServiceTest {

    @Mock
    private FieldEncryptionService fieldEncryptionService;

    @Mock
    private BucketCredentialsRepository bucketCredentialsRepository;

    @InjectMocks
    private BucketCredentialsService bucketCredentialsService;

    @Test
    @DisplayName("saveBucketCredentials keeps version null for first insert")
    void saveBucketCredentials_newBucket_keepsVersionNull() {
        BucketCredentialsEntity input = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("new-bucket")
                .accessKey("access")
                .secretKey("secret")
                .build();
        when(bucketCredentialsRepository.findByBucketName("new-bucket")).thenReturn(Optional.empty());
        when(fieldEncryptionService.encrypt("secret")).thenReturn("encrypted-secret");
        when(bucketCredentialsRepository.save(any(BucketCredentialsEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BucketCredentialsEntity saved = bucketCredentialsService.saveBucketCredentials(input);

        assertNull(saved.getVersion());
        assertEquals("encrypted-secret", saved.getSecretKey());
    }

    @Test
    @DisplayName("saveBucketCredentials carries existing version for updates")
    void saveBucketCredentials_existingBucket_carriesVersionForward() {
        BucketCredentialsEntity existing = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("existing-bucket")
                .accessKey("old-access")
                .secretKey("old-secret")
                .version(3L)
                .build();
        BucketCredentialsEntity input = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("existing-bucket")
                .accessKey("new-access")
                .secretKey("new-secret")
                .build();
        when(bucketCredentialsRepository.findByBucketName("existing-bucket")).thenReturn(Optional.of(existing));
        when(fieldEncryptionService.encrypt("new-secret")).thenReturn("encrypted-new-secret");
        when(bucketCredentialsRepository.save(any(BucketCredentialsEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        bucketCredentialsService.saveBucketCredentials(input);

        ArgumentCaptor<BucketCredentialsEntity> captor = ArgumentCaptor.forClass(BucketCredentialsEntity.class);
        verify(bucketCredentialsRepository).save(captor.capture());
        assertEquals(3L, captor.getValue().getVersion());
        assertEquals("encrypted-new-secret", captor.getValue().getSecretKey());
    }

    @Test
    @DisplayName("getBucketCredentials decrypts and returns stored version")
    void getBucketCredentials_decryptsAndReturnsVersion() {
        BucketCredentialsEntity stored = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket")
                .accessKey("stored-access")
                .secretKey("encrypted-secret")
                .version(7L)
                .build();
        when(bucketCredentialsRepository.findByBucketName("bucket")).thenReturn(Optional.of(stored));
        when(fieldEncryptionService.decrypt("encrypted-secret")).thenReturn("plain-secret");

        BucketCredentialsEntity result = bucketCredentialsService.getBucketCredentials("bucket");

        assertEquals("stored-access", result.getAccessKey());
        assertEquals("plain-secret", result.getSecretKey());
        assertEquals(7L, result.getVersion());
    }
}
