package it.eng.tools.service;

import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.exception.TenantNotFoundException;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.s3.service.S3BucketProvisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository, auditEventPublisher,
                s3BucketProvisionService, BASE_CALLBACK_URL);
    }

    private Tenant buildTenant(boolean enabled) {
        return Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("Engineering")
                .connectorId("urn:connector:engineering")
                .callbackAddress("http://localhost:8090")
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
        List<Tenant> tenants = Arrays.asList(buildTenant(true), buildTenant(false));
        when(tenantRepository.findAll()).thenReturn(tenants);

        List<Tenant> result = tenantService.findAll();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("saveTenant generates a non-null UUID id regardless of caller-supplied id")
    void saveTenant_generatesUuid_ignoresCallerSuppliedId() {
        Tenant input = buildTenant(true);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.saveTenant(input);

        assertNotNull(result.getId());
        assertNotEquals(TENANT_ID, result.getId(),
                "Server-generated UUID must not equal the caller-supplied id");
    }

    @Test
    @DisplayName("saveTenant derives callbackAddress as baseUrl/generatedId")
    void saveTenant_derivesCallbackAddress() {
        Tenant input = buildTenant(true);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.saveTenant(input);

        String expectedPrefix = BASE_CALLBACK_URL + "/";
        assertTrue(result.getCallbackAddress().startsWith(expectedPrefix),
                "callbackAddress must start with base URL");
        assertEquals(result.getCallbackAddress(), BASE_CALLBACK_URL + "/" + result.getId());
    }

    @Test
    @DisplayName("saveTenant preserves caller-supplied fields other than id and callbackAddress")
    void saveTenant_preservesOtherFields() {
        Tenant input = buildTenant(true);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.saveTenant(input);

        assertEquals("Engineering", result.getName());
        assertEquals("urn:connector:engineering", result.getConnectorId());
        assertTrue(result.isEnabled());
    }

    @Test
    @DisplayName("saveTenant publishes TENANT_CREATED audit event")
    void saveTenant_publishesAuditEvent() {
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        tenantService.saveTenant(buildTenant(true));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishEvent(captor.capture());
        assertEquals(AuditEventType.TENANT_CREATED, captor.getValue().getEventType());
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
}

