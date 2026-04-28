package it.eng.tools.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantContextHolderTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Set and get tenant ID")
    void setAndGetTenantId() {
        TenantContextHolder.setTenantId("engineering");
        assertEquals("engineering", TenantContextHolder.getTenantId());
    }

    @Test
    @DisplayName("setTenantId also populates MDC")
    void setTenantId_populatesMdc() {
        TenantContextHolder.setTenantId("engineering");
        assertEquals("engineering", MDC.get(TenantContextHolder.MDC_TENANT_KEY));
    }

    @Test
    @DisplayName("Clear tenant ID removes value from ThreadLocal and MDC")
    void clearTenantId() {
        TenantContextHolder.setTenantId("engineering");
        TenantContextHolder.clear();
        assertNull(TenantContextHolder.getTenantId());
        assertNull(MDC.get(TenantContextHolder.MDC_TENANT_KEY));
    }

    @Test
    @DisplayName("setTenantId with null removes MDC entry")
    void setTenantId_null_removesMdcEntry() {
        TenantContextHolder.setTenantId("engineering");
        TenantContextHolder.setTenantId(null);
        assertNull(MDC.get(TenantContextHolder.MDC_TENANT_KEY));
    }

    @Test
    @DisplayName("Default tenant ID is null before any set")
    void defaultIsNull() {
        assertNull(TenantContextHolder.getTenantId());
    }
}
