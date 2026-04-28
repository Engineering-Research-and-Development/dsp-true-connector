package it.eng.negotiation.properties;

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
class ContractNegotiationPropertiesTest {

    private static final String TENANT_ID = "engineering";
    private static final String TENANT_CONNECTOR_ID = "tenant-connector-id";
    private static final String TENANT_CALLBACK = "http://tenant.example.com";
    private static final String GLOBAL_CALLBACK = "http://global.example.com";
    private static final String GLOBAL_CONNECTOR_ID = "connectorId";

    @Mock
    private TenantRepository tenantRepository;

    private ContractNegotiationProperties properties;

    private Tenant enabledTenant;

    @BeforeEach
    void setUp() {
        properties = new ContractNegotiationProperties(tenantRepository);
        ReflectionTestUtils.setField(properties, "callbackAddress", GLOBAL_CALLBACK);
        ReflectionTestUtils.setField(properties, "automaticNegotiation", false);
        ReflectionTestUtils.setField(properties, "maxRetries", 3);
        ReflectionTestUtils.setField(properties, "retryDelayMs", 2000L);
        ReflectionTestUtils.setField(properties, "serverPort", "8080");

        enabledTenant = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("Engineering")
                .connectorId(TENANT_CONNECTOR_ID)
                .callbackAddress(TENANT_CALLBACK)
                .automaticNegotiation(true)
                .enabled(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // -------------------------------------------------------------------------
    // connectorId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("connectorId returns tenant connectorId when tenant context is active")
    void connectorId_withActiveTenant_returnsTenantConnectorId() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(enabledTenant));

        assertEquals(TENANT_CONNECTOR_ID, properties.connectorId());
    }

    @Test
    @DisplayName("connectorId returns global default when no tenant context")
    void connectorId_withoutTenantContext_returnsGlobalDefault() {
        assertEquals(GLOBAL_CONNECTOR_ID, properties.connectorId());
    }

    @Test
    @DisplayName("connectorId returns global default when tenant is disabled")
    void connectorId_withDisabledTenant_returnsGlobalDefault() {
        Tenant disabledTenant = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("Engineering")
                .connectorId(TENANT_CONNECTOR_ID)
                .callbackAddress(TENANT_CALLBACK)
                .enabled(false)
                .build();
        TenantContextHolder.setTenantId(TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(disabledTenant));

        assertEquals(GLOBAL_CONNECTOR_ID, properties.connectorId());
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
                .connectorId(TENANT_CONNECTOR_ID)
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
    // isAutomaticNegotiation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isAutomaticNegotiation returns tenant setting when tenant context is active")
    void isAutomaticNegotiation_withActiveTenant_returnsTenantSetting() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(enabledTenant));

        assertTrue(properties.isAutomaticNegotiation());
    }

    @Test
    @DisplayName("isAutomaticNegotiation returns global setting when no tenant context")
    void isAutomaticNegotiation_withoutTenantContext_returnsGlobalSetting() {
        assertFalse(properties.isAutomaticNegotiation());
    }

    // -------------------------------------------------------------------------
    // getMaxRetries / getRetryDelayMs / serverPort / getAssignee
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getMaxRetries returns configured value")
    void getMaxRetries_returnsConfiguredValue() {
        assertEquals(3, properties.getMaxRetries());
    }

    @Test
    @DisplayName("getRetryDelayMs returns configured value")
    void getRetryDelayMs_returnsConfiguredValue() {
        assertEquals(2000L, properties.getRetryDelayMs());
    }

    @Test
    @DisplayName("serverPort returns configured port")
    void serverPort_returnsConfiguredPort() {
        assertEquals("8080", properties.serverPort());
    }

    @Test
    @DisplayName("getAssignee returns TRUEConnector v2")
    void getAssignee_returnsTrueConnectorV2() {
        assertEquals("TRUEConnector v2", properties.getAssignee());
    }
}
