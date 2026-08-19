package it.eng.tools.service;

import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    private static final String TENANT_ID = "engineering";
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

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository, auditEventPublisher,
                s3BucketProvisionService, bucketCredentialsService,
                bucketProvisioningModeResolver, bucketConnectionVerificationService, BASE_CALLBACK_URL);
    }

    private Tenant buildTenant(boolean enabled) {
        return Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("Engineering")
                .participantId("urn:connector:engineering")
                .enabled(enabled)
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
        Tenant input = buildTenant(true);
        when(bucketProvisioningModeResolver.resolve(any(TenantBucketCredentialsRequest.class)))
                .thenReturn(BucketProvisioningMode.AUTOMATIC);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findByParticipantId(input.getParticipantId())).thenReturn(Optional.empty());
        when(tenantRepository.findByBucketName(TenantService.BUCKET_NAME_PREFIX + TENANT_ID))
                .thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.saveTenant(input);

        assertEquals("Engineering", result.getName());
        assertEquals("urn:connector:engineering", result.getParticipantId());
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
                .name("Engineering")
                .participantId("urn:connector:engineering")
                .enabled(true)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tenantService.saveTenant(input));

        assertTrue(ex.getMessage().contains("invalid"),
                "Exception message must mention the invalid id");
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
        Tenant disabled = buildTenant(false);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(disabled));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.enableTenant(TENANT_ID);

        assertTrue(result.isEnabled());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishEvent(captor.capture());
        assertEquals(AuditEventType.TENANT_ENABLED, captor.getValue().getEventType());
    }

    @Test
    @DisplayName("disableTenant sets enabled=false, saves, and publishes TENANT_DISABLED")
    void disableTenant_success() {
        Tenant enabled = buildTenant(true);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(enabled));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.disableTenant(TENANT_ID);

        assertFalse(result.isEnabled());
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
    @DisplayName("updateTenant preserves existing bucketName, silently ignoring any bucketName in update body")
    void updateTenant_bucketNameIsImmutable() {
        Tenant existing = buildTenant(true);
        String existingBucket = existing.getBucketName();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

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
                .name("Engineering")
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
}
