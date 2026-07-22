package it.eng.connector.integration.s3;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.exception.S3ServerException;
import it.eng.tools.s3.model.TemporaryBucketUser;
import it.eng.tools.s3.repository.TemporaryBucketUserRepository;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.service.FieldEncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TemporaryBucketUserServiceIT extends BaseIntegrationTest {

    private static final String BUCKET_NAME = "dsp-true-connector";
    private static final String OBJECT_KEY = "it-test/object.bin";

    @Autowired
    private TemporaryBucketUserService temporaryBucketUserService;

    @Autowired
    private TemporaryBucketUserRepository temporaryBucketUserRepository;

    @Autowired
    private FieldEncryptionService fieldEncryptionService;

    private String lastTransferProcessId;

    @AfterEach
    void cleanup() {
        if (lastTransferProcessId != null) {
            temporaryBucketUserRepository.deleteById(lastTransferProcessId);
            lastTransferProcessId = null;
        }
    }

    // -------------------------------------------------------------------------
    // createTemporaryUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createTemporaryUser - returned entity carries plain secret key and is persisted with encrypted key")
    void createTemporaryUser_persistsEncryptedAndReturnsPlain() {
        lastTransferProcessId = "tp-" + UUID.randomUUID();

        TemporaryBucketUser result = temporaryBucketUserService.createTemporaryUser(
                lastTransferProcessId, BUCKET_NAME, OBJECT_KEY);

        // Returned entity is fully populated
        assertNotNull(result);
        assertNotNull(result.getAccessKey());
        assertTrue(result.getAccessKey().startsWith("TempUser-"),
                "Access key should be prefixed with TempUser-");
        assertNotNull(result.getSecretKey());
        assertEquals(lastTransferProcessId, result.getTransferProcessId());
        assertEquals(BUCKET_NAME, result.getBucketName());
        assertEquals(OBJECT_KEY, result.getObjectKey());

        // MongoDB document exists and stores the encrypted (not plain) secret key
        Optional<TemporaryBucketUser> stored = temporaryBucketUserRepository.findById(lastTransferProcessId);
        assertTrue(stored.isPresent());
        String storedSecret = stored.get().getSecretKey();
        assertNotEquals(result.getSecretKey(), storedSecret,
                "Stored secret key must be encrypted, not equal to the plain key returned");

        // The stored secret decrypts back to the plain key
        assertEquals(result.getSecretKey(), fieldEncryptionService.decrypt(storedSecret));
    }

    @Test
    @DisplayName("createTemporaryUser - each call produces unique credentials")
    void createTemporaryUser_uniqueCredentialsPerCall() {
        String tp1 = "tp-" + UUID.randomUUID();
        String tp2 = "tp-" + UUID.randomUUID();

        TemporaryBucketUser first = temporaryBucketUserService.createTemporaryUser(tp1, BUCKET_NAME, "obj1");
        TemporaryBucketUser second = temporaryBucketUserService.createTemporaryUser(tp2, BUCKET_NAME, "obj2");

        assertNotEquals(first.getAccessKey(), second.getAccessKey());
        assertNotEquals(first.getSecretKey(), second.getSecretKey());

        // cleanup both
        temporaryBucketUserRepository.deleteById(tp1);
        temporaryBucketUserRepository.deleteById(tp2);
    }

    // -------------------------------------------------------------------------
    // getTemporaryUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getTemporaryUser - retrieves document and returns decrypted secret key")
    void getTemporaryUser_returnsDecryptedKey() {
        lastTransferProcessId = "tp-" + UUID.randomUUID();
        TemporaryBucketUser created = temporaryBucketUserService.createTemporaryUser(
                lastTransferProcessId, BUCKET_NAME, OBJECT_KEY);

        TemporaryBucketUser retrieved = temporaryBucketUserService.getTemporaryUser(lastTransferProcessId);

        assertEquals(created.getTransferProcessId(), retrieved.getTransferProcessId());
        assertEquals(created.getAccessKey(), retrieved.getAccessKey());
        assertEquals(created.getSecretKey(), retrieved.getSecretKey(),
                "Retrieved secret key should match the plain key from creation");
        assertEquals(created.getBucketName(), retrieved.getBucketName());
        assertEquals(created.getObjectKey(), retrieved.getObjectKey());
    }

    @Test
    @DisplayName("getTemporaryUser - throws S3ServerException for unknown transferProcessId")
    void getTemporaryUser_notFound_throwsS3ServerException() {
        String unknownId = "tp-does-not-exist-" + UUID.randomUUID();

        S3ServerException ex = assertThrows(S3ServerException.class,
                () -> temporaryBucketUserService.getTemporaryUser(unknownId));

        assertTrue(ex.getMessage().contains(unknownId));
    }

    // -------------------------------------------------------------------------
    // deleteTemporaryUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deleteTemporaryUser - removes IAM user, policy, and MongoDB document")
    void deleteTemporaryUser_removesAllResources() {
        String transferProcessId = "tp-" + UUID.randomUUID();
        temporaryBucketUserService.createTemporaryUser(transferProcessId, BUCKET_NAME, OBJECT_KEY);
        assertTrue(temporaryBucketUserRepository.findById(transferProcessId).isPresent(),
                "Document should exist before deletion");

        temporaryBucketUserService.deleteTemporaryUser(transferProcessId);

        assertFalse(temporaryBucketUserRepository.findById(transferProcessId).isPresent(),
                "MongoDB document should be removed after deletion");
    }

    @Test
    @DisplayName("deleteTemporaryUser - is idempotent: no error when called for unknown transferProcessId")
    void deleteTemporaryUser_unknownId_noException() {
        String unknownId = "tp-never-created-" + UUID.randomUUID();

        assertDoesNotThrow(() -> temporaryBucketUserService.deleteTemporaryUser(unknownId));
    }

    @Test
    @DisplayName("deleteTemporaryUser - after deletion getTemporaryUser throws S3ServerException")
    void deleteTemporaryUser_thenGetThrows() {
        String transferProcessId = "tp-" + UUID.randomUUID();
        temporaryBucketUserService.createTemporaryUser(transferProcessId, BUCKET_NAME, OBJECT_KEY);

        temporaryBucketUserService.deleteTemporaryUser(transferProcessId);

        assertThrows(S3ServerException.class,
                () -> temporaryBucketUserService.getTemporaryUser(transferProcessId));
    }
}
