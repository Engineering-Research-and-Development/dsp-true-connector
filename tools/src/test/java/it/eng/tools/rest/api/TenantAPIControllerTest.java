package it.eng.tools.rest.api;

import it.eng.tools.exception.TenantNotFoundException;
import it.eng.tools.model.Tenant;
import it.eng.tools.model.TenantBucketCredentialsRequest;
import it.eng.tools.model.TenantCreateRequest;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.GenericFilterBuilder;
import it.eng.tools.service.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAPIControllerTest {

    private static final String TENANT_ID = "engineering";

    @Mock
    private TenantService tenantService;

    @Mock
    private Pageable pageable;
    @Mock
    private PagedResourcesAssembler<Tenant> pagedResourcesAssembler;
    @Mock
    private PlainTenantAssembler plainAssembler;
    @Mock
    private GenericFilterBuilder filterBuilder;

    @InjectMocks
    private TenantAPIController controller;

    PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(20, 0, 2, 1);
    private Page<Tenant> tenantPage;

    private Tenant buildTenant() {
        return Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("Engineering")
                .participantId("urn:connector:engineering")
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("Get all tenants returns list")
    void getAllTenants_returnsList() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        List<EntityModel<Tenant>> content = Collections.singletonList(EntityModel.of(buildTenant()));
        PagedModel<EntityModel<Tenant>> pagedModel = PagedModel.of(content, metadata);
        tenantPage = new PageImpl<>(Collections.singletonList(buildTenant()), pageable, 1);

        when(filterBuilder.buildFromRequest(any(HttpServletRequest.class)))
                .thenReturn(Map.of());
        when(tenantService.findAll(anyMap(), any(Pageable.class))).thenReturn(tenantPage);

        when(pagedResourcesAssembler.toModel(tenantPage, plainAssembler)).thenReturn((PagedModel) pagedModel);

        ResponseEntity<PagedAPIResponse> response =
                controller.getAllTenants(request, 0, 20, new String[]{"timestamp", "desc"});

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getResponse().isSuccess());
        assertFalse(response.getBody().getResponse().getData().getContent().isEmpty());

        verify(tenantService).findAll(any(Map.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Get tenant by ID returns tenant")
    void getTenantById_success() {
        Tenant tenant = buildTenant();
        when(tenantService.findById(TENANT_ID)).thenReturn(tenant);

        ResponseEntity<GenericApiResponse<Tenant>> response = controller.getTenantById(TENANT_ID);

        assertNotNull(response.getBody());
        assertEquals(TENANT_ID, response.getBody().getData().getId());
    }

    @Test
    @DisplayName("Get tenant by ID throws when not found")
    void getTenantById_notFound() {
        when(tenantService.findById(TENANT_ID)).thenThrow(new TenantNotFoundException("Not found"));

        assertThrows(TenantNotFoundException.class, () -> controller.getTenantById(TENANT_ID));
    }

    @Test
    @DisplayName("Create tenant persists and returns it")
    void createTenant_success() {
        Tenant tenant = buildTenant();
        TenantCreateRequest request = TenantCreateRequest.Builder.newInstance()
                .id(tenant.getId())
                .name(tenant.getName())
                .description(tenant.getDescription())
                .participantId(tenant.getParticipantId())
                .automaticNegotiation(tenant.isAutomaticNegotiation())
                .automaticTransfer(tenant.isAutomaticTransfer())
                .enabled(tenant.isEnabled())
                .build();
        when(tenantService.saveTenant(any(Tenant.class), any(TenantBucketCredentialsRequest.class))).thenReturn(tenant);

        ResponseEntity<GenericApiResponse<Tenant>> response = controller.createTenant(request);

        assertNotNull(response.getBody());
        assertEquals(TENANT_ID, response.getBody().getData().getId());
        verify(tenantService).saveTenant(any(Tenant.class), any(TenantBucketCredentialsRequest.class));
    }

    @Test
    @DisplayName("Enable tenant returns updated tenant")
    void enableTenant_success() {
        Tenant tenant = buildTenant();
        when(tenantService.enableTenant(TENANT_ID)).thenReturn(tenant);

        ResponseEntity<GenericApiResponse<Tenant>> response = controller.enableTenant(TENANT_ID);

        assertNotNull(response.getBody());
        assertEquals(TENANT_ID, response.getBody().getData().getId());
    }

    @Test
    @DisplayName("Disable tenant returns updated tenant")
    void disableTenant_success() {
        Tenant disabled = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("Engineering")
                .participantId("urn:connector:engineering")
                .enabled(false)
                .build();
        when(tenantService.disableTenant(TENANT_ID)).thenReturn(disabled);

        ResponseEntity<GenericApiResponse<Tenant>> response = controller.disableTenant(TENANT_ID);

        assertNotNull(response.getBody());
        assertEquals(TENANT_ID, response.getBody().getData().getId());
    }

    @Test
    @DisplayName("Delete tenant succeeds without error")
    void deleteTenant_success() {
        ResponseEntity<GenericApiResponse<Void>> response = controller.deleteTenant(TENANT_ID);

        verify(tenantService).deleteTenant(TENANT_ID);
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("Delete tenant throws when not found")
    void deleteTenant_notFound() {
        doThrow(new TenantNotFoundException("Not found")).when(tenantService).deleteTenant(TENANT_ID);

        assertThrows(TenantNotFoundException.class, () -> controller.deleteTenant(TENANT_ID));
    }

    @Test
    @DisplayName("Update tenant returns updated tenant")
    void updateTenant_success() {
        Tenant updates = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("Updated Name")
                .participantId("urn:connector:updated")
                .build();
        Tenant updated = buildTenant();
        when(tenantService.updateTenant(TENANT_ID, updates)).thenReturn(updated);

        ResponseEntity<GenericApiResponse<Tenant>> response = controller.updateTenant(TENANT_ID, updates);

        assertNotNull(response.getBody());
        assertEquals(TENANT_ID, response.getBody().getData().getId());
    }

    @Test
    @DisplayName("Update tenant throws when not found")
    void updateTenant_notFound() {
        Tenant updates = Tenant.Builder.newInstance()
                .id(TENANT_ID)
                .name("Updated Name")
                .participantId("urn:connector:updated")
                .build();
        when(tenantService.updateTenant(TENANT_ID, updates)).thenThrow(new TenantNotFoundException("Not found"));

        assertThrows(TenantNotFoundException.class, () -> controller.updateTenant(TENANT_ID, updates));
    }

    @Test
    @DisplayName("Enable tenant throws when not found")
    void enableTenant_notFound() {
        when(tenantService.enableTenant(TENANT_ID)).thenThrow(new TenantNotFoundException("Not found"));

        assertThrows(TenantNotFoundException.class, () -> controller.enableTenant(TENANT_ID));
    }

    @Test
    @DisplayName("Disable tenant throws when not found")
    void disableTenant_notFound() {
        when(tenantService.disableTenant(TENANT_ID)).thenThrow(new TenantNotFoundException("Not found"));

        assertThrows(TenantNotFoundException.class, () -> controller.disableTenant(TENANT_ID));
    }
}
