package it.eng.tools.service;

import it.eng.tools.auth.keycloak.RealmCredentialsValidator;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.realm.RealmCredentialsChangedEvent;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.exception.DuplicateTenantRealmException;
import it.eng.tools.exception.InvalidTenantRealmException;
import it.eng.tools.exception.TenantNotFoundException;
import it.eng.tools.model.BucketProvisioningMode;
import it.eng.tools.model.Tenant;
import it.eng.tools.model.TenantBucketCredentialsRequest;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.service.BucketConnectionVerificationService;
import it.eng.tools.s3.service.BucketCredentialsService;
import it.eng.tools.s3.service.S3BucketProvisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    private static final String TENANT_ID = "engineering-a";
    private static final String BASE_CALLBACK_URL = "http://localhost:8080";

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private S3BucketProvisionService s3BucketProvisionService;

    @Mock
    private BucketCredentialsService bucketCredentialsService;

    @Mock
    private BucketProvisioningModeResolver bucketProvisioningModeResolver;

    @Mock
    private BucketConnectionVerificationService bucketConnectionVerificationService;

    @Mock
    private Pageable pageable;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ObjectProvider<RealmCredentialsValidator> validatorProvider;

    @Mock
    private RealmCredentialsValidator credentialsValidator;

    private final FieldEncryptionService fieldEncryptionService = newEncryptionService();

    private static FieldEncryptionService newEncryptionService() {
        try {
            return new FieldEncryptionService("test-encryption-key");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository, auditEventPublisher,
                s3BucketProvisionService, bucketCredentialsService,
                bucketProvisioningModeResolver, bucketConnectionVerificationService, BASE_CALLBACK_URL,
                "platform", "connector-admin", fieldEncryptionService, eventPublisher, validatorProvider);
    }

    private Tenant buildRealmTenant(String encryptedSecret) {
        return Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("EngineeringA")
                .participantId("urn:connector:engineering")
                .realm("tenant-EngineeringA")
                .enabled(true)
                .clientSecret(encryptedSecret)
                .build();
    }

    @Test
    @DisplayName("setRealmCredentials stores the encrypted secret, publishes the event and an audit without the secret")
    void setRealmCredentials_valid_storesCiphertext() {
        when(validatorProvider.getIfAvailable()).thenReturn(credentialsValidator);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildRealmTenant(null)));
        when(credentialsValidator.accepts("tenant-EngineeringA", "plain-secret")).thenReturn(true);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant saved = tenantService.setRealmCredentials(TENANT_ID, "plain-secret");

        assertTrue(saved.isCredentialsConfigured());
        assertNotEquals("plain-secret", saved.getClientSecret());
        assertEquals("plain-secret", fieldEncryptionService.decrypt(saved.getClientSecret()));
        verify(eventPublisher).publishEvent(new RealmCredentialsChangedEvent(TENANT_ID));
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishEvent(audit.capture());
        assertEquals(AuditEventType.TENANT_UPDATED, audit.getValue().getEventType());
        assertFalse(audit.getValue().toString().contains("plain-secret"));
        assertEquals("REALM_CREDENTIALS_SET", audit.getValue().getDetails().get("changeType"));
    }

    @Test
    @DisplayName("setRealmCredentials stores nothing and publishes nothing when the realm rejects the secret")
    void setRealmCredentials_rejected_storesNothing() {
        when(validatorProvider.getIfAvailable()).thenReturn(credentialsValidator);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildRealmTenant(null)));
        when(credentialsValidator.accepts(anyString(), anyString())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> tenantService.setRealmCredentials(TENANT_ID, "wrong"));

        verify(tenantRepository, never()).save(any());
        verifyNoInteractions(eventPublisher, auditEventPublisher);
    }

    @Test
    @DisplayName("setRealmCredentials is rejected without Keycloak mode, without tenant, without realm and for a blank secret")
    void setRealmCredentials_invalidPreconditions_badRequest() {
        when(validatorProvider.getIfAvailable()).thenReturn(null);
        assertThrows(BadRequestException.class, () -> tenantService.setRealmCredentials(TENANT_ID, "s"));

        when(validatorProvider.getIfAvailable()).thenReturn(credentialsValidator);
        when(tenantRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> tenantService.setRealmCredentials("missing", "s"));

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant(true)));
        assertThrows(BadRequestException.class, () -> tenantService.setRealmCredentials(TENANT_ID, "s"));

        when(tenantRepository.findById("realm-tenant")).thenReturn(Optional.of(buildRealmTenant(null)));
        assertThrows(BadRequestException.class, () -> tenantService.setRealmCredentials("realm-tenant", " "));

        verify(tenantRepository, never()).save(any());
        verifyNoInteractions(credentialsValidator);
    }

    @Test
    @DisplayName("clearRealmCredentials removes the secret and publishes the event")
    void clearRealmCredentials_removesSecret() {
        when(validatorProvider.getIfAvailable()).thenReturn(credentialsValidator);
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(buildRealmTenant(fieldEncryptionService.encrypt("s"))));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant saved = tenantService.clearRealmCredentials(TENANT_ID);

        assertFalse(saved.isCredentialsConfigured());
        assertNull(saved.getClientSecret());
        verify(eventPublisher).publishEvent(new RealmCredentialsChangedEvent(TENANT_ID));
    }

    @Test
    @DisplayName("userManagementSecret decrypts the stored secret, empty when absent")
    void userManagementSecret_roundTrip() {
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(buildRealmTenant(fieldEncryptionService.encrypt("round-trip"))));
        when(tenantRepository.findById("none")).thenReturn(Optional.of(buildRealmTenant(null)));
        when(tenantRepository.findById("unknown")).thenReturn(Optional.empty());

        assertEquals(Optional.of("round-trip"), tenantService.userManagementSecret(TENANT_ID));
        assertTrue(tenantService.userManagementSecret("none").isEmpty());
        assertTrue(tenantService.userManagementSecret("unknown").isEmpty());
    }

    @Test
    @DisplayName("enable, disable and update keep the stored secret")
    void enableDisableUpdate_keepSecret() {
        String cipher = fieldEncryptionService.encrypt("keep-me");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildRealmTenant(cipher)));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bucketProvisioningModeResolver.resolve(any())).thenReturn(BucketProvisioningMode.AUTOMATIC);

        assertEquals(cipher, tenantService.enableTenant(TENANT_ID).getClientSecret());
        assertEquals(cipher, tenantService.disableTenant(TENANT_ID).getClientSecret());
        Tenant updates = Tenant.Builder.newInstance().id(TENANT_ID).name("Renamed")
                .participantId("urn:connector:engineering").build();
        assertEquals(cipher, tenantService.updateTenant(TENANT_ID, updates).getClientSecret());
    }

    @Test
    @DisplayName("updateTenant with the same realm keeps the stored secret and publishes no credentials change")
    void updateTenant_sameRealm_keepsSecret() {
        String cipher = fieldEncryptionService.encrypt("keep-me");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildRealmTenant(cipher)));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bucketProvisioningModeResolver.resolve(any())).thenReturn(BucketProvisioningMode.AUTOMATIC);
        Tenant updates = Tenant.Builder.newInstance().id(TENANT_ID).name("EngineeringA")
                .participantId("urn:connector:engineering").realm("tenant-EngineeringA").build();

        assertEquals(cipher, tenantService.updateTenant(TENANT_ID, updates).getClientSecret());

        verifyNoInteractions(eventPublisher);
        verify(auditEventPublisher, times(1)).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("updateTenant to another realm drops the stored secret, publishes the event and audits the removal")
    void updateTenant_realmChanged_dropsSecret() {
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(buildRealmTenant(fieldEncryptionService.encrypt("old-realm-secret"))));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bucketProvisioningModeResolver.resolve(any())).thenReturn(BucketProvisioningMode.AUTOMATIC);
        Tenant updates = Tenant.Builder.newInstance().id(TENANT_ID).name("EngineeringA")
                .participantId("urn:connector:engineering").realm("tenant-EngineeringB").build();

        Tenant saved = tenantService.updateTenant(TENANT_ID, updates);

        assertEquals("tenant-EngineeringB", saved.getRealm());
        assertNull(saved.getClientSecret());
        assertFalse(saved.isCredentialsConfigured());
        verify(eventPublisher).publishEvent(new RealmCredentialsChangedEvent(TENANT_ID));
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher, times(2)).publishEvent(audit.capture());
        AuditEvent removal = audit.getAllValues().get(1);
        assertEquals(AuditEventType.TENANT_UPDATED, removal.getEventType());
        assertEquals("REALM_CREDENTIALS_REMOVED", removal.getDetails().get("changeType"));
        assertFalse(audit.getAllValues().toString().contains("old-realm-secret"));
    }

    @Test
    @DisplayName("updateTenant setting a realm on a tenant without one publishes the event without a removal audit")
    void updateTenant_realmFromNull_publishesEventOnly() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant(true)));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bucketProvisioningModeResolver.resolve(any())).thenReturn(BucketProvisioningMode.AUTOMATIC);
        Tenant updates = Tenant.Builder.newInstance().id(TENANT_ID).name("EngineeringA")
                .participantId("urn:connector:engineering").realm("tenant-EngineeringA").build();

        Tenant saved = tenantService.updateTenant(TENANT_ID, updates);

        assertNull(saved.getClientSecret());
        verify(eventPublisher).publishEvent(new RealmCredentialsChangedEvent(TENANT_ID));
        verify(auditEventPublisher, times(1)).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("userManagementSecret of a loaded tenant decrypts its secret, empty when absent")
    void userManagementSecret_loadedTenant() {
        assertEquals(Optional.of("loaded"),
                tenantService.userManagementSecret(buildRealmTenant(fieldEncryptionService.encrypt("loaded"))));
        assertTrue(tenantService.userManagementSecret(buildRealmTenant(null)).isEmpty());
        verifyNoInteractions(tenantRepository);
    }

    private Tenant buildTenant(boolean enabled) {
        return Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("EngineeringA")
                .participantId("urn:connector:engineering")
                .enabled(enabled)
                .build();
    }

    private Tenant buildTenantWithRealm(boolean enabled, String realm) {
        return Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("EngineeringA")
                .participantId("urn:connector:engineering")
                .realm(realm)
                .enabled(enabled)
                .build();
    }

    private Tenant buildTenantWithBucket(boolean enabled, String bucketName) {
        return Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("EngineeringA")
                .participantId("urn:connector:engineering")
                .enabled(enabled)
                .bucketName(bucketName)
                .build();
    }

    @Test
    @DisplayName("findEnabledTenantById returns tenant when present and enabled")
    void findEnabledTenantById_success() {
        Tenant tenant = buildTenant(true);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        Tenant result = tenantService.findEnabledTenantById(TENANT_ID);

        assertNotNull(result);
        assertEquals(TENANT_ID, result.getId());
        assertTrue(result.isEnabled());
    }

    @Test
    @DisplayName("findEnabledTenants returns enabled tenants from the repository")
    void findEnabledTenants_returnsRepositoryResult() {
        List<Tenant> enabledTenants = List.of(buildTenant(true));
        when(tenantRepository.findByEnabled(true)).thenReturn(enabledTenants);

        List<Tenant> result = tenantService.findEnabledTenants();

        assertSame(enabledTenants, result);
        verify(tenantRepository).findByEnabled(true);
    }

    @Test
    @DisplayName("findEnabledTenantById publishes TENANT_NOT_FOUND and throws when tenant absent")
    void findEnabledTenantById_notFound_publishesAuditAndThrows() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(TenantNotFoundException.class,
                () -> tenantService.findEnabledTenantById(TENANT_ID));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishEvent(captor.capture());
        assertEquals(AuditEventType.TENANT_NOT_FOUND, captor.getValue().getEventType());
    }

    @Test
    @DisplayName("findEnabledTenantById publishes TENANT_NOT_FOUND and throws when tenant is disabled")
    void findEnabledTenantById_tenantDisabled_publishesAuditAndThrows() {
        Tenant disabled = buildTenant(false);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(disabled));

        assertThrows(TenantNotFoundException.class,
                () -> tenantService.findEnabledTenantById(TENANT_ID));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishEvent(captor.capture());
        assertEquals(AuditEventType.TENANT_NOT_FOUND, captor.getValue().getEventType());
    }

    @Test
    @DisplayName("findAll returns all tenants")
    void findAll_returnsList() {
        Map<String, Object> emptyFilters = new HashMap<>();

        List<Tenant> tenants = Arrays.asList(buildTenant(true), buildTenant(false));
        when(tenantRepository.findWithDynamicFilters(eq(emptyFilters), eq(Tenant.class), eq(pageable)))
                .thenReturn(new PageImpl<>(tenants));

        Page<Tenant> response = tenantService.findAll(emptyFilters, pageable);

        assertNotNull(response);
        assertEquals(2, response.getTotalElements());
        verify(tenantRepository).findWithDynamicFilters(anyMap(), eq(Tenant.class), any(Pageable.class));
    }

    @Test
    @DisplayName("saveTenant uses client-supplied id")
    void saveTenant_usesClientSuppliedId() {
        Tenant input = buildTenant(true);
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.findByBucketName(TenantService.BUCKET_NAME_PREFIX + TENANT_ID))
                .thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.saveTenant(input);

        assertEquals(TENANT_ID, result.getId(),
                "Service must use the client-supplied id");
    }

    @Test
    @DisplayName("saveTenant derives callbackAddress as baseUrl/id")
    void saveTenant_derivesCallbackAddress() {
        Tenant input = buildTenant(true);
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.findByBucketName(TenantService.BUCKET_NAME_PREFIX + TENANT_ID))
                .thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.saveTenant(input);

        String expectedCallbackAddress = BASE_CALLBACK_URL + "/" + result.getId();
        assertEquals(expectedCallbackAddress, result.getCallbackAddress(BASE_CALLBACK_URL),
                "callbackAddress must be baseURL/id");
    }

    @Test
    @DisplayName("saveTenant preserves caller-supplied fields")
    void saveTenant_preservesOtherFields() {
        Tenant input = buildTenantWithRealm(true, "engineering-realm");
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.findByBucketName(TenantService.BUCKET_NAME_PREFIX + TENANT_ID))
                .thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.saveTenant(input);

        assertEquals("EngineeringA", result.getName());
        assertEquals("urn:connector:engineering", result.getParticipantId());
        assertEquals("engineering-realm", result.getRealm());
        assertTrue(result.isEnabled());
    }

    @Test
    @DisplayName("saveTenant publishes TENANT_CREATED audit event")
    void saveTenant_publishesAuditEvent() {
        Tenant input = buildTenant(true);
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.findByBucketName(TenantService.BUCKET_NAME_PREFIX + TENANT_ID))
                .thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        tenantService.saveTenant(input);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishEvent(captor.capture());
        assertEquals(AuditEventType.TENANT_CREATED, captor.getValue().getEventType());
    }

    @Test
    @DisplayName("saveTenant throws IllegalArgumentException when tenant id has invalid characters")
    void saveTenant_invalidIdFormat_throwsIllegalArgumentException() {
        Tenant input = Tenant.Builder.newInstance()
                .id("invalid id!")
                .name("EngineeringA")
                .participantId("urn:connector:engineering")
                .enabled(true)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tenantService.saveTenant(input));

        assertTrue(ex.getMessage().contains("invalid"),
                "Exception message must mention the invalid id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"internal", "INTERNAL", "Internal"})
    @DisplayName("saveTenant rejects the reserved tenant id 'internal', case-insensitively, before saving anything")
    void saveTenant_reservedInternalId_throwsIllegalArgumentException(String tenantId) {
        Tenant input = Tenant.Builder.newInstance()
                .id(tenantId)
                .name("Internal")
                .participantId("urn:connector:internal")
                .enabled(true)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tenantService.saveTenant(input));

        assertTrue(ex.getMessage().contains("reserved"), "Exception message must say the id is reserved");
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("saveTenant throws IllegalArgumentException when id already exists")
    void saveTenant_duplicateId_throwsIllegalArgumentException() {
        Tenant existing = buildTenant(true);
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tenantService.saveTenant(buildTenant(true)));

        assertTrue(ex.getMessage().contains(TENANT_ID),
                "Exception message must mention the duplicate id");
    }

    @Test
    @DisplayName("saveTenant throws IllegalArgumentException when a tenant with the same participantId already exists")
    void saveTenant_duplicateParticipantId_throwsIllegalArgumentException() {
        Tenant existing = buildTenant(true);
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId("urn:connector:engineering")).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tenantService.saveTenant(buildTenant(true)));

        assertTrue(ex.getMessage().contains("urn:connector:engineering"),
                "Exception message must mention the duplicate participantId");
    }

    @Test
    @DisplayName("deleteTenant removes tenant and publishes TENANT_DELETED")
    void deleteTenant_success() {
        Tenant tenant = buildTenant(true);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        tenantService.deleteTenant(TENANT_ID);

        verify(tenantRepository).delete(tenant);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishEvent(captor.capture());
        assertEquals(AuditEventType.TENANT_DELETED, captor.getValue().getEventType());
    }

    @Test
    @DisplayName("deleteTenant throws TenantNotFoundException when tenant absent")
    void deleteTenant_notFound_throwsTenantNotFoundException() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(TenantNotFoundException.class,
                () -> tenantService.deleteTenant(TENANT_ID));
    }

    @Test
    @DisplayName("enableTenant sets enabled=true, saves, and publishes TENANT_ENABLED")
    void enableTenant_success() {
        Tenant disabled = buildTenantWithRealm(false, "engineering-realm");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(disabled));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.enableTenant(TENANT_ID);

        assertTrue(result.isEnabled());
        assertEquals("engineering-realm", result.getRealm());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishEvent(captor.capture());
        assertEquals(AuditEventType.TENANT_ENABLED, captor.getValue().getEventType());
    }

    @Test
    @DisplayName("disableTenant sets enabled=false, saves, and publishes TENANT_DISABLED")
    void disableTenant_success() {
        Tenant enabled = buildTenantWithRealm(true, "engineering-realm");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(enabled));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.disableTenant(TENANT_ID);

        assertFalse(result.isEnabled());
        assertEquals("engineering-realm", result.getRealm());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishEvent(captor.capture());
        assertEquals(AuditEventType.TENANT_DISABLED, captor.getValue().getEventType());
    }

    @Test
    @DisplayName("updateTenant preserves participantId from existing tenant, ignoring update body")
    void updateTenant_participantIdIsIgnored() {
        Tenant existing = buildTenant(true);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);

        String existingParticipantId = existing.getParticipantId();

        Tenant updates = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("New Name")
                .participantId("urn:connector:changed-value")
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .build();

        Tenant result = tenantService.updateTenant(TENANT_ID, updates);

        assertEquals(existingParticipantId, result.getParticipantId(),
                "participantId must remain unchanged regardless of update body");
        assertEquals("New Name", result.getName());
    }

    @Test
    @DisplayName("updateTenant preserves existing realm when the update omits it")
    void updateTenant_preservesRealmWhenOmitted() {
        Tenant existing = buildTenantWithRealm(true, "existing-realm");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        Tenant updates = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("Updated EngineeringA")
                .participantId(existing.getParticipantId())
                .build();

        Tenant result = tenantService.updateTenant(TENANT_ID, updates);

        assertEquals("existing-realm", result.getRealm());
    }

    @Test
    @DisplayName("updateTenant replaces existing realm when a new realm is supplied")
    void updateTenant_replacesRealmWhenSupplied() {
        Tenant existing = buildTenantWithRealm(true, "existing-realm");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        Tenant updates = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("Updated EngineeringA")
                .participantId(existing.getParticipantId())
                .realm("updated-realm")
                .build();

        Tenant result = tenantService.updateTenant(TENANT_ID, updates);

        assertEquals("updated-realm", result.getRealm());
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {"tenant x", "a/b", "http://evil/realms/x", " ", "realm?x", "r\u00e9alm", ".", ".."})
    @DisplayName("saveTenant rejects malformed, dot and dot-dot realms and never reaches persistence")
    void saveTenant_malformedRealm_throwsInvalidTenantRealmException(String realm) {
        Tenant input = buildTenantWithRealm(true, realm);
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());

        InvalidTenantRealmException ex = assertThrows(InvalidTenantRealmException.class,
                () -> tenantService.saveTenant(input));

        assertTrue(ex.getMessage().contains("^[A-Za-z0-9._-]+$"));
        assertTrue(ex.getMessage().contains("'" + realm + "'"));
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("saveTenant rejects a realm longer than 255 characters")
    void saveTenant_realmTooLong_throwsInvalidTenantRealmException() {
        Tenant input = buildTenantWithRealm(true, "a".repeat(256));
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());

        assertThrows(InvalidTenantRealmException.class, () -> tenantService.saveTenant(input));
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"platform", "Platform", "PLATFORM", "connector-admin", "Connector-Admin",
            "master", "Master", "MASTER"})
    @DisplayName("saveTenant rejects reserved realm names case-insensitively")
    void saveTenant_reservedRealm_throwsInvalidTenantRealmException(String realm) {
        Tenant input = buildTenantWithRealm(true, realm);
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());

        InvalidTenantRealmException ex = assertThrows(InvalidTenantRealmException.class,
                () -> tenantService.saveTenant(input));

        assertTrue(ex.getMessage().contains("reserved"));
        assertTrue(ex.getMessage().contains("'" + realm + "'"));
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("saveTenant rejects a realm already held by another tenant")
    void saveTenant_duplicateRealm_throwsDuplicateTenantRealmException() {
        Tenant input = buildTenantWithRealm(true, "tenant-EngineeringA");
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.existsByRealmAndIdNot("tenant-EngineeringA", TENANT_ID)).thenReturn(true);

        DuplicateTenantRealmException ex = assertThrows(DuplicateTenantRealmException.class,
                () -> tenantService.saveTenant(input));

        assertTrue(ex.getMessage().contains("'tenant-EngineeringA'"));
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("saveTenant accepts a valid realm and a null realm")
    void saveTenant_validAndNullRealm_succeeds() {
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(anyString())).thenReturn(Optional.empty());
        when(tenantRepository.existsByRealmAndIdNot("tenant-EngineeringA", TENANT_ID)).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("tenant-EngineeringA",
                tenantService.saveTenant(buildTenantWithRealm(true, "tenant-EngineeringA")).getRealm());
        assertNull(tenantService.saveTenant(buildTenant(true)).getRealm());
        verify(tenantRepository, never()).existsByRealmAndIdNot(isNull(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"tenant x", "a/b", "http://evil/realms/x", "", ".", ".."})
    @DisplayName("updateTenant rejects malformed, dot and dot-dot realms")
    void updateTenant_malformedRealm_throwsInvalidTenantRealmException(String realm) {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenantWithRealm(true, "existing-realm")));
        Tenant updates = buildTenantWithRealm(true, realm);

        assertThrows(InvalidTenantRealmException.class, () -> tenantService.updateTenant(TENANT_ID, updates));
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"platform", "Platform", "connector-admin", "CONNECTOR-ADMIN", "master", "Master"})
    @DisplayName("updateTenant rejects reserved realm names case-insensitively")
    void updateTenant_reservedRealm_throwsInvalidTenantRealmException(String realm) {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenantWithRealm(true, "existing-realm")));
        Tenant updates = buildTenantWithRealm(true, realm);

        assertThrows(InvalidTenantRealmException.class, () -> tenantService.updateTenant(TENANT_ID, updates));
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("updateTenant rejects a realm held by another tenant")
    void updateTenant_duplicateRealm_throwsDuplicateTenantRealmException() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenantWithRealm(true, "existing-realm")));
        when(tenantRepository.existsByRealmAndIdNot("taken-realm", TENANT_ID)).thenReturn(true);
        Tenant updates = buildTenantWithRealm(true, "taken-realm");

        assertThrows(DuplicateTenantRealmException.class, () -> tenantService.updateTenant(TENANT_ID, updates));
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("updateTenant accepts the tenant's own current realm")
    void updateTenant_ownRealm_succeeds() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenantWithRealm(true, "existing-realm")));
        when(tenantRepository.existsByRealmAndIdNot("existing-realm", TENANT_ID)).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);

        Tenant result = tenantService.updateTenant(TENANT_ID, buildTenantWithRealm(true, "existing-realm"));

        assertEquals("existing-realm", result.getRealm());
    }

    @Test
    @DisplayName("updateTenant with a null realm skips realm validation")
    void updateTenant_nullRealm_skipsValidation() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenantWithRealm(true, "existing-realm")));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);

        tenantService.updateTenant(TENANT_ID, buildTenant(true));

        verify(tenantRepository, never()).existsByRealmAndIdNot(anyString(), anyString());
    }

    @Test
    @DisplayName("updateTenant preserves existing bucketName, silently ignoring any bucketName in update body")
    void updateTenant_bucketNameIsImmutable() {
        Tenant existing = buildTenantWithBucket(true, "existing-bucket");
        String existingBucket = existing.getBucketName();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);

        Tenant updates = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("New Name")
                .participantId(existing.getParticipantId())
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName("should-be-ignored-bucket")
                .build();

        Tenant result = tenantService.updateTenant(TENANT_ID, updates);

        assertEquals(existingBucket, result.getBucketName(),
                "bucketName must remain unchanged regardless of update body");
        verify(s3BucketProvisionService, never()).ensureBucketCredentials(any());
    }

    @Test
    @DisplayName("updateTenant with EXISTING_BUCKET same bucket reconfirms and keeps bucket")
    void updateTenant_existingBucket_sameBucket_reconfirm() {
        Tenant existing = buildTenantWithBucket(true, "existing-bucket");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant updates = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("New Name")
                .participantId(existing.getParticipantId())
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .build();
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName("existing-bucket")
                .build();
        when(bucketProvisioningModeResolver.resolve(request)).thenReturn(BucketProvisioningMode.EXISTING_BUCKET);
        when(tenantRepository.findByBucketNameAndIdNot("existing-bucket", TENANT_ID)).thenReturn(Optional.empty());

        Tenant result = tenantService.updateTenant(TENANT_ID, updates, request);

        assertEquals("existing-bucket", result.getBucketName());
        verify(s3BucketProvisionService).ensureBucketCredentials("existing-bucket");
    }

    @Test
    @DisplayName("updateTenant with EXISTING_BUCKET different bucket migrates")
    void updateTenant_existingBucket_differentBucket_migrates() {
        Tenant existing = buildTenantWithBucket(true, "old-bucket");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant updates = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("New Name")
                .participantId(existing.getParticipantId())
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .build();
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName("new-bucket")
                .build();
        when(bucketProvisioningModeResolver.resolve(request)).thenReturn(BucketProvisioningMode.EXISTING_BUCKET);
        when(tenantRepository.findByBucketNameAndIdNot("new-bucket", TENANT_ID)).thenReturn(Optional.empty());

        Tenant result = tenantService.updateTenant(TENANT_ID, updates, request);

        assertEquals("new-bucket", result.getBucketName());
        verify(s3BucketProvisionService).ensureBucketCredentials("new-bucket");
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("BUCKET_MIGRATED", eventCaptor.getValue().getDetails().get("changeType"));
    }

    @Test
    @DisplayName("updateTenant with bucket conflict throws IllegalArgumentException")
    void updateTenant_bucketConflict_throwsIllegalArgumentException() {
        Tenant existing = buildTenantWithBucket(true, "old-bucket");
        Tenant conflicting = Tenant.Builder.newInstance()
                .id("other-tenant")
                .name("Other")
                .participantId("urn:connector:other")
                .enabled(true)
                .bucketName("shared-bucket")
                .build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(existing));
        when(tenantRepository.findByBucketNameAndIdNot("shared-bucket", TENANT_ID))
                .thenReturn(Optional.of(conflicting));

        Tenant updates = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("New Name")
                .participantId(existing.getParticipantId())
                .build();
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName("shared-bucket")
                .build();
        when(bucketProvisioningModeResolver.resolve(request)).thenReturn(BucketProvisioningMode.EXISTING_BUCKET);

        assertThrows(IllegalArgumentException.class, () -> tenantService.updateTenant(TENANT_ID, updates, request));
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("updateTenant with EXTERNAL_CREDENTIALS verifyConnection false rotates credentials")
    void updateTenant_externalCredentials_verifyFalse_rotatesCredentials() {
        Tenant existing = buildTenantWithBucket(true, "existing-bucket");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bucketCredentialsService.saveBucketCredentials(any(BucketCredentialsEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Tenant updates = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("New Name")
                .participantId(existing.getParticipantId())
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .build();
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName("existing-bucket")
                .accessKey("rotated-access")
                .secretKey("rotated-secret")
                .verifyConnection(false)
                .build();
        when(bucketProvisioningModeResolver.resolve(request)).thenReturn(BucketProvisioningMode.EXTERNAL_CREDENTIALS);
        when(tenantRepository.findByBucketNameAndIdNot("existing-bucket", TENANT_ID)).thenReturn(Optional.empty());

        tenantService.updateTenant(TENANT_ID, updates, request);

        verify(bucketCredentialsService).saveBucketCredentials(any(BucketCredentialsEntity.class));
        verify(bucketConnectionVerificationService, never()).verify(anyString(), anyString(), anyString());
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("CREDENTIALS_ROTATED", eventCaptor.getValue().getDetails().get("changeType"));
    }

    @Test
    @DisplayName("updateTenant with EXTERNAL_CREDENTIALS verifyConnection true fails before persistence")
    void updateTenant_externalCredentials_verifyTrue_failure() {
        Tenant existing = buildTenantWithBucket(true, "existing-bucket");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(existing));

        Tenant updates = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("New Name")
                .participantId(existing.getParticipantId())
                .build();
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName("existing-bucket")
                .accessKey("invalid-access")
                .secretKey("invalid-secret")
                .verifyConnection(true)
                .build();
        when(bucketProvisioningModeResolver.resolve(request)).thenReturn(BucketProvisioningMode.EXTERNAL_CREDENTIALS);
        when(tenantRepository.findByBucketNameAndIdNot("existing-bucket", TENANT_ID)).thenReturn(Optional.empty());
        when(bucketConnectionVerificationService.verify("existing-bucket", "invalid-access", "invalid-secret"))
                .thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> tenantService.updateTenant(TENANT_ID, updates, request));
        verify(tenantRepository, never()).save(any(Tenant.class));
        verify(bucketCredentialsService, never()).saveBucketCredentials(any(BucketCredentialsEntity.class));
    }

    @Test
    @DisplayName("saveTenant without bucketName auto-derives 'dsp-{tenantId}' and provisions S3 bucket")
    void saveTenant_withoutBucketName_autoDerivesAndProvisions() {
        Tenant input = buildTenant(true);
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.findByBucketName(TenantService.BUCKET_NAME_PREFIX + TENANT_ID))
                .thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.saveTenant(input);

        String expectedBucket = TenantService.BUCKET_NAME_PREFIX + TENANT_ID;
        ArgumentCaptor<String> bucketCaptor = ArgumentCaptor.forClass(String.class);
        verify(s3BucketProvisionService).ensureBucketCredentials(bucketCaptor.capture());
        assertEquals(expectedBucket, bucketCaptor.getValue(),
                "ensureBucketCredentials must be called with the auto-derived bucket name");
        assertEquals(expectedBucket, result.getBucketName(),
                "saved tenant must carry the auto-derived bucket name");
    }

    @Test
    @DisplayName("saveTenant ignores explicit bucketName in request body and always uses auto-derived name")
    void saveTenant_withExplicitBucketName_isIgnoredAndAutoDerivesName() {
        String suppliedBucket = "my-custom-bucket";
        String expectedBucket = TenantService.BUCKET_NAME_PREFIX + TENANT_ID;
        Tenant input = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("EngineeringA")
                .participantId("urn:connector:engineering")
                .enabled(true)
                .bucketName(suppliedBucket)
                .build();
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.findByBucketName(expectedBucket)).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.saveTenant(input);

        verify(s3BucketProvisionService).ensureBucketCredentials(expectedBucket);
        assertEquals(expectedBucket, result.getBucketName(),
                "caller-supplied bucketName must be silently dropped; auto-derived name must be used");
    }

    @Test
    @DisplayName("saveTenant throws IllegalArgumentException when derived bucket is already owned by another tenant")
    void saveTenant_derivedBucketAlreadyOwned_throwsIllegalArgumentException() {
        Tenant input = buildTenant(true);
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        Tenant conflicting = Tenant.Builder.newInstance()
                .id("other-tenant")
                .name("Other")
                .participantId("urn:connector:other")
                .enabled(true)
                .bucketName(TenantService.BUCKET_NAME_PREFIX + TENANT_ID)
                .build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.findByBucketName(TenantService.BUCKET_NAME_PREFIX + TENANT_ID))
                .thenReturn(Optional.of(conflicting));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tenantService.saveTenant(input));

        assertTrue(ex.getMessage().contains(TenantService.BUCKET_NAME_PREFIX + TENANT_ID),
                "Exception message must mention the conflicting bucket name");
    }

    @Test
    @DisplayName("saveTenant with EXISTING_BUCKET provisions supplied bucket")
    void saveTenant_existingBucketMode_usesSuppliedBucket() {
        Tenant input = buildTenant(true);
        String suppliedBucket = "existing-bucket";
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName(suppliedBucket)
                .build();
        when(bucketProvisioningModeResolver.resolve(request)).thenReturn(BucketProvisioningMode.EXISTING_BUCKET);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.findByBucketName(suppliedBucket)).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.saveTenant(input, request);

        verify(s3BucketProvisionService).ensureBucketCredentials(suppliedBucket);
        verifyNoInteractions(bucketCredentialsService, bucketConnectionVerificationService);
        assertEquals(suppliedBucket, result.getBucketName());
    }

    @Test
    @DisplayName("saveTenant with EXTERNAL_CREDENTIALS and verifyConnection false persists supplied credentials")
    void saveTenant_externalCredentialsVerifyFalse_persistsCredentials() {
        Tenant input = buildTenant(true);
        String suppliedBucket = "external-bucket";
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName(suppliedBucket)
                .accessKey("provided-access")
                .secretKey("provided-secret")
                .verifyConnection(false)
                .build();
        when(bucketProvisioningModeResolver.resolve(request)).thenReturn(BucketProvisioningMode.EXTERNAL_CREDENTIALS);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.findByBucketName(suppliedBucket)).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bucketCredentialsService.saveBucketCredentials(any(BucketCredentialsEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.saveTenant(input, request);

        verify(bucketCredentialsService).saveBucketCredentials(any(BucketCredentialsEntity.class));
        verify(s3BucketProvisionService, never()).ensureBucketCredentials(anyString());
        verifyNoInteractions(bucketConnectionVerificationService);
        assertEquals(suppliedBucket, result.getBucketName());
    }

    @Test
    @DisplayName("saveTenant with EXTERNAL_CREDENTIALS and verifyConnection true verifies then persists")
    void saveTenant_externalCredentialsVerifyTrue_success() {
        Tenant input = buildTenant(true);
        String suppliedBucket = "external-verified-bucket";
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName(suppliedBucket)
                .accessKey("provided-access")
                .secretKey("provided-secret")
                .verifyConnection(true)
                .build();
        when(bucketProvisioningModeResolver.resolve(request)).thenReturn(BucketProvisioningMode.EXTERNAL_CREDENTIALS);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.findByBucketName(suppliedBucket)).thenReturn(Optional.empty());
        when(bucketConnectionVerificationService.verify(suppliedBucket, "provided-access", "provided-secret"))
                .thenReturn(true);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bucketCredentialsService.saveBucketCredentials(any(BucketCredentialsEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        tenantService.saveTenant(input, request);

        verify(bucketConnectionVerificationService).verify(suppliedBucket, "provided-access", "provided-secret");
        verify(bucketCredentialsService).saveBucketCredentials(any(BucketCredentialsEntity.class));
        verify(s3BucketProvisionService, never()).ensureBucketCredentials(anyString());
    }

    @Test
    @DisplayName("saveTenant with EXTERNAL_CREDENTIALS and verifyConnection true fails before persistence")
    void saveTenant_externalCredentialsVerifyTrue_failure() {
        Tenant input = buildTenant(true);
        String suppliedBucket = "external-invalid-bucket";
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName(suppliedBucket)
                .accessKey("provided-access")
                .secretKey("provided-secret")
                .verifyConnection(true)
                .build();
        when(bucketProvisioningModeResolver.resolve(request)).thenReturn(BucketProvisioningMode.EXTERNAL_CREDENTIALS);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.findByBucketName(suppliedBucket)).thenReturn(Optional.empty());
        when(bucketConnectionVerificationService.verify(suppliedBucket, "provided-access", "provided-secret"))
                .thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> tenantService.saveTenant(input, request));

        verify(bucketConnectionVerificationService).verify(suppliedBucket, "provided-access", "provided-secret");
        verify(tenantRepository, never()).save(any(Tenant.class));
        verify(bucketCredentialsService, never()).saveBucketCredentials(any(BucketCredentialsEntity.class));
    }

    @Test
    @DisplayName("saveTenant with EXISTING_BUCKET throws on bucket ownership conflict before provisioning")
    void saveTenant_existingBucket_conflictThrows() {
        Tenant input = buildTenant(true);
        String suppliedBucket = "existing-owned-bucket";
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName(suppliedBucket)
                .build();
        Tenant conflicting = Tenant.Builder.newInstance()
                .id("other-tenant")
                .name("Other")
                .participantId("urn:connector:other")
                .enabled(true)
                .bucketName(suppliedBucket)
                .build();
        when(bucketProvisioningModeResolver.resolve(request)).thenReturn(BucketProvisioningMode.EXISTING_BUCKET);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.findByBucketName(suppliedBucket)).thenReturn(Optional.of(conflicting));

        assertThrows(IllegalArgumentException.class, () -> tenantService.saveTenant(input, request));

        verify(s3BucketProvisionService, never()).ensureBucketCredentials(anyString());
        verify(bucketCredentialsService, never()).saveBucketCredentials(any(BucketCredentialsEntity.class));
    }

    @Test
    @DisplayName("saveTenant with EXTERNAL_CREDENTIALS throws on bucket ownership conflict before persistence")
    void saveTenant_externalCredentials_conflictThrows() {
        Tenant input = buildTenant(true);
        String suppliedBucket = "external-owned-bucket";
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName(suppliedBucket)
                .accessKey("provided-access")
                .secretKey("provided-secret")
                .build();
        Tenant conflicting = Tenant.Builder.newInstance()
                .id("other-tenant")
                .name("Other")
                .participantId("urn:connector:other")
                .enabled(true)
                .bucketName(suppliedBucket)
                .build();
        when(bucketProvisioningModeResolver.resolve(request)).thenReturn(BucketProvisioningMode.EXTERNAL_CREDENTIALS);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.findByBucketName(suppliedBucket)).thenReturn(Optional.of(conflicting));

        assertThrows(IllegalArgumentException.class, () -> tenantService.saveTenant(input, request));

        verify(bucketConnectionVerificationService, never()).verify(anyString(), anyString(), anyString());
        verify(bucketCredentialsService, never()).saveBucketCredentials(any(BucketCredentialsEntity.class));
    }

    @Test
    @DisplayName("saveTenant with EXTERNAL_CREDENTIALS rejects invalid bucket name before persistence")
    void saveTenant_externalCredentials_invalidBucketNameThrows() {
        Tenant input = buildTenant(true);
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName("Invalid_Bucket_Name")
                .accessKey("provided-access")
                .secretKey("provided-secret")
                .build();
        when(bucketProvisioningModeResolver.resolve(request)).thenReturn(BucketProvisioningMode.EXTERNAL_CREDENTIALS);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tenantService.saveTenant(input, request));

        assertTrue(ex.getMessage().contains("invalid"));
        verify(tenantRepository, never()).save(any(Tenant.class));
        verify(bucketCredentialsService, never()).saveBucketCredentials(any(BucketCredentialsEntity.class));
        verify(s3BucketProvisionService, never()).ensureBucketCredentials(anyString());
    }
}
