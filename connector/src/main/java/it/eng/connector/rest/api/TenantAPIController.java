package it.eng.connector.rest.api;

import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Tenant;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for managing tenants via the admin API.
 */
@RestController
@RequestMapping(path = ApiEndpoints.TENANTS_V1,
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class TenantAPIController {

    private final TenantService tenantService;

    /**
     * Constructs the controller with its service dependency.
     *
     * @param tenantService the tenant service
     */
    public TenantAPIController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Returns all tenants.
     *
     * @return 200 OK with the list of tenants
     */
    @GetMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<GenericApiResponse<List<Tenant>>> getAllTenants() {
        log.info("Fetching all tenants");
        List<Tenant> tenants = tenantService.findAll();
        return ResponseEntity.ok(GenericApiResponse.success(tenants, "Fetching all tenants"));
    }

    /**
     * Returns the tenant with the given ID.
     *
     * @param id the tenant identifier
     * @return 200 OK with the tenant, or 404 if not found
     */
    @GetMapping(path = "/{id}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<GenericApiResponse<Tenant>> getTenantById(@PathVariable String id) {
        log.info("Fetching tenant: {}", id);
        Tenant tenant = tenantService.findById(id);
        return ResponseEntity.ok(GenericApiResponse.success(tenant, "Tenant found"));
    }

    /**
     * Creates a new tenant.
     *
     * @param tenant the tenant to create
     * @return 200 OK with the created tenant
     */
    @PostMapping
    public ResponseEntity<GenericApiResponse<Tenant>> createTenant(@RequestBody Tenant tenant) {
        log.info("Creating tenant: {}", tenant.getId());
        Tenant saved = tenantService.saveTenant(tenant);
        return ResponseEntity.ok(GenericApiResponse.success(saved, "Tenant created"));
    }

    /**
     * Updates the mutable settings of the tenant with the given ID.
     *
     * @param id      the tenant identifier
     * @param updates the updated tenant settings
     * @return 200 OK with the updated tenant
     */
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericApiResponse<Tenant>> updateTenant(
            @PathVariable String id,
            @RequestBody Tenant updates) {
        log.info("Updating tenant: {}", id);
        Tenant updated = tenantService.updateTenant(id, updates);
        return ResponseEntity.ok(GenericApiResponse.success(updated, "Tenant updated"));
    }

    /**
     * Enables the tenant with the given ID.
     *
     * @param id the tenant identifier
     * @return 200 OK with the updated tenant
     */
    @PutMapping(path = "/{id}/enable", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<GenericApiResponse<Tenant>> enableTenant(@PathVariable String id) {
        log.info("Enabling tenant: {}", id);
        Tenant updated = tenantService.enableTenant(id);
        return ResponseEntity.ok(GenericApiResponse.success(updated, "Tenant enabled"));
    }

    /**
     * Disables the tenant with the given ID.
     *
     * @param id the tenant identifier
     * @return 200 OK with the updated tenant
     */
    @PutMapping(path = "/{id}/disable", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<GenericApiResponse<Tenant>> disableTenant(@PathVariable String id) {
        log.info("Disabling tenant: {}", id);
        Tenant updated = tenantService.disableTenant(id);
        return ResponseEntity.ok(GenericApiResponse.success(updated, "Tenant disabled"));
    }

    /**
     * Deletes the tenant with the given ID.
     *
     * @param id the tenant identifier
     * @return 200 OK on success, or 404 if not found
     */
    @DeleteMapping(path = "/{id}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<GenericApiResponse<Void>> deleteTenant(@PathVariable String id) {
        log.info("Deleting tenant: {}", id);
        tenantService.deleteTenant(id);
        return ResponseEntity.ok(GenericApiResponse.success(null, "Tenant deleted"));
    }
}
