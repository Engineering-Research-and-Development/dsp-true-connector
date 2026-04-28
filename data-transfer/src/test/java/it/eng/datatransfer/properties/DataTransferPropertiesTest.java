package it.eng.datatransfer.properties;

import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.service.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataTransferPropertiesTest {

    private static final String TENANT_ID = "engineering";
    private static final String TENANT_CALLBACK = "http://tenant.example.com";
    private static final String GLOBAL_CALLBACK = "http://global.example.com";

    @Mock
    private TenantRepository tenantRepository;

    private DataTransferProperties properties;

    private Tenant enabledTenant;

    @BeforeEach
    void setUp() {
        properties = new DataTransferProperties(tenantRepository);
        ReflectionTestUtils.setField(properties, "callbackAddress", GLOBAL_CALLBACK);
        ReflectionTestUtils.setField(properties, "automaticTransfer", false);
        ReflectionTestUtils.setField(properties, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(properties, "retryDelayMs", 2000L);

        enabledTenant = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("Engineering")
                .connectorId("tenant-connector-id")
                .callbackAddress(TENANT_CALLBACK)
                .automaticTransfer(true)
                .enabled(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // -------------------------------------------------------------------------
    // providerCallbackAddress
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("providerCallbackAddress returns tenant callbackAddress when tenant context is active")
    void providerCallbackAddress_withActiveTenant_returnsTenantCallback() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(enabledTenant));

        assertEquals(TENANT_CALLBACK, properties.providerCallbackAddress());
    }

    @Test
    @DisplayName("providerCallbackAddress returns global property when no tenant context")
    void providerCallbackAddress_withoutTenantContext_returnsGlobalProperty() {
        assertEquals(GLOBAL_CALLBACK, properties.providerCallbackAddress());
    }

    @Test
    @DisplayName("providerCallbackAddress returns global property when tenant is disabled")
    void providerCallbackAddress_withDisabledTenant_returnsGlobalProperty() {
        Tenant disabledTenant = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("Engineering")
                .connectorId("tenant-connector-id")
                .callbackAddress(TENANT_CALLBACK)
                .enabled(false)
                .build();
        TenantContextHolder.setTenantId(TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(disabledTenant));

        assertEquals(GLOBAL_CALLBACK, properties.providerCallbackAddress());
    }

    // -------------------------------------------------------------------------
    // consumerCallbackAddress
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("consumerCallbackAddress appends /consumer to tenant callbackAddress")
    void consumerCallbackAddress_withActiveTenant_appendsConsumerSuffix() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(enabledTenant));

        assertEquals(TENANT_CALLBACK + "/consumer", properties.consumerCallbackAddress());
    }

    @Test
    @DisplayName("consumerCallbackAddress strips trailing slash before appending /consumer")
    void consumerCallbackAddress_withTrailingSlash_stripsSlash() {
        Tenant tenantWithSlash = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("Engineering")
                .connectorId("tenant-connector-id")
                .callbackAddress("http://tenant.example.com/")
                .enabled(true)
                .build();
        TenantContextHolder.setTenantId(TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantWithSlash));

        assertEquals("http://tenant.example.com/consumer", properties.consumerCallbackAddress());
    }

    @Test
    @DisplayName("consumerCallbackAddress returns global property with /consumer when no tenant context")
    void consumerCallbackAddress_withoutTenantContext_returnsGlobalWithConsumerSuffix() {
        assertEquals(GLOBAL_CALLBACK + "/consumer", properties.consumerCallbackAddress());
    }

    // -------------------------------------------------------------------------
    // isAutomaticTransfer
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isAutomaticTransfer returns tenant setting when tenant context is active")
    void isAutomaticTransfer_withActiveTenant_returnsTenantSetting() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(enabledTenant));

        assertTrue(properties.isAutomaticTransfer());
    }

    @Test
    @DisplayName("isAutomaticTransfer returns global setting when no tenant context")
    void isAutomaticTransfer_withoutTenantContext_returnsGlobalSetting() {
        assertFalse(properties.isAutomaticTransfer());
    }

    // -------------------------------------------------------------------------
    // getMaxRetryAttempts / getRetryDelayMs
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getMaxRetryAttempts returns configured value")
    void getMaxRetryAttempts_returnsConfiguredValue() {
        assertEquals(3, properties.getMaxRetryAttempts());
    }

    @Test
    @DisplayName("getRetryDelayMs returns configured value")
    void getRetryDelayMs_returnsConfiguredValue() {
        assertEquals(2000L, properties.getRetryDelayMs());
    }
}
