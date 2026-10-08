package it.eng.tools.auth.keycloak.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import it.eng.tools.service.FieldEncryptionService;
import jakarta.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class RealmCredentialsServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String SECRET = "plain-secret";

    @Mock
    private RealmCredentialsRepository repository;
    @Mock
    private FieldEncryptionService encryptionService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private RealmCredentialsService service;

    @Test
    @DisplayName("save encrypts the secret and publishes a change event")
    void saveEncryptsAndPublishes() {
        when(encryptionService.encrypt(SECRET)).thenReturn("cipher");
        when(repository.save(any(RealmCredentials.class))).thenAnswer(inv -> inv.getArgument(0));

        RealmCredentials saved = service.save(TENANT, SECRET);

        assertEquals("cipher", saved.getClientSecret());
        verify(eventPublisher).publishEvent(new RealmCredentialsChangedEvent(TENANT));
    }

    @Test
    @DisplayName("save preserves the existing version for updates")
    void savePreservesVersion() {
        RealmCredentials existing = RealmCredentials.Builder.newInstance()
                .tenantId(TENANT).clientSecret("old").version(3L).build();
        when(repository.findById(TENANT)).thenReturn(Optional.of(existing));
        when(encryptionService.encrypt(SECRET)).thenReturn("cipher");
        when(repository.save(any(RealmCredentials.class))).thenAnswer(inv -> inv.getArgument(0));

        service.save(TENANT, SECRET);

        ArgumentCaptor<RealmCredentials> captor = ArgumentCaptor.forClass(RealmCredentials.class);
        verify(repository).save(captor.capture());
        assertEquals(3L, captor.getValue().getVersion());
    }

    @Test
    @DisplayName("getDecrypted returns the decrypted secret")
    void getDecrypted() {
        RealmCredentials stored = RealmCredentials.Builder.newInstance()
                .tenantId(TENANT).clientSecret("cipher").build();
        when(repository.findById(TENANT)).thenReturn(Optional.of(stored));
        when(encryptionService.decrypt("cipher")).thenReturn(SECRET);

        assertEquals(Optional.of(SECRET), service.getDecrypted(TENANT));
    }

    @Test
    @DisplayName("getDecrypted returns empty when no credentials exist")
    void getDecryptedEmpty() {
        when(repository.findById(TENANT)).thenReturn(Optional.empty());

        assertTrue(service.getDecrypted(TENANT).isEmpty());
    }

    @Test
    @DisplayName("exists delegates to the repository")
    void exists() {
        when(repository.existsById(TENANT)).thenReturn(true);

        assertTrue(service.exists(TENANT));
        assertFalse(service.exists("other"));
    }

    @Test
    @DisplayName("delete removes credentials and publishes a change event")
    void deleteRemovesAndPublishes() {
        service.delete(TENANT);

        verify(repository).deleteById(TENANT);
        verify(eventPublisher).publishEvent(new RealmCredentialsChangedEvent(TENANT));
    }

    @Test
    @DisplayName("toString omits the secret")
    void toStringOmitsSecret() {
        RealmCredentials credentials = RealmCredentials.Builder.newInstance()
                .tenantId(TENANT).clientSecret(SECRET).build();

        assertFalse(credentials.toString().contains(SECRET));
    }

    @Test
    @DisplayName("build fails without a client secret")
    void buildRequiresSecret() {
        assertThrows(ValidationException.class,
                () -> RealmCredentials.Builder.newInstance().tenantId(TENANT).build());
    }
}
