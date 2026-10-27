package it.eng.tools.rest.api;

import it.eng.tools.model.PublicTenantInfo;
import it.eng.tools.model.Tenant;
import it.eng.tools.service.TenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicTenantInfoControllerTest {

    @Mock
    private TenantService tenantService;

    @InjectMocks
    private PublicTenantInfoController controller;

    @Test
    @DisplayName("Returns public name and realm for enabled tenants")
    void getPublicTenantInfo_returnsNameAndRealm() {
        Tenant tenant = Tenant.Builder.newInstance()
                .id("engineering")
                .name("Engineering")
                .participantId("urn:connector:engineering")
                .realm("tenant_Engineering")
                .enabled(true)
                .build();
        when(tenantService.findEnabledTenants()).thenReturn(List.of(tenant));

        List<PublicTenantInfo> response = controller.getPublicTenantInfo();

        assertEquals(List.of(new PublicTenantInfo("Engineering", "tenant_Engineering")), response);
        verify(tenantService).findEnabledTenants();
    }

    @Test
    @DisplayName("Preserves a null realm for legacy tenants")
    void getPublicTenantInfo_preservesNullRealm() {
        Tenant tenant = Tenant.Builder.newInstance()
                .id("legacy")
                .name("Legacy")
                .participantId("urn:connector:legacy")
                .enabled(true)
                .build();
        when(tenantService.findEnabledTenants()).thenReturn(List.of(tenant));

        List<PublicTenantInfo> response = controller.getPublicTenantInfo();

        assertEquals(List.of(new PublicTenantInfo("Legacy", null)), response);
        verify(tenantService).findEnabledTenants();
    }
}
