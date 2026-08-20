package it.eng.tools.rest.api;

import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Tenant;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.GenericFilterBuilder;
import it.eng.tools.service.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for managing tenants via the admin API.
 */
@RestController
@RequestMapping(path = ApiEndpoints.TENANTS_V1,
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class TenantAPIController {

    private final GenericFilterBuilder filterBuilder;
    private final PagedResourcesAssembler<Tenant> pagedResourcesAssembler;
    private final PlainTenantAssembler plainAssembler;

    private final TenantService tenantService;

    /**
     * Constructs the controller with its service dependency.
     *
     * @param filterBuilder the filter builder
     * @param pagedResourcesAssembler the paged resources assembler
     * @param plainAssembler the plain tenant assembler
     * @param tenantService the tenant service
     * */
    public TenantAPIController(GenericFilterBuilder filterBuilder, PagedResourcesAssembler<Tenant> pagedResourcesAssembler,
                               PlainTenantAssembler plainAssembler, TenantService tenantService) {
        this.filterBuilder = filterBuilder;
        this.pagedResourcesAssembler = pagedResourcesAssembler;
        this.plainAssembler = plainAssembler;
        this.tenantService = tenantService;
    }

    /**
     * Returns all tenants.
     *
     * @param request HttpServletRequest containing all filter parameters
     * @param page    pagination page number (default 0)
     * @param size    pagination parameters
     * @param sort    sorting parameters in the format "field,direction"
     *
     * @return GenericApiResponse with matching tenants
     */
    @GetMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<PagedAPIResponse> getAllTenants(HttpServletRequest request,
                  @RequestParam(defaultValue = "0") int page,
                  @RequestParam(defaultValue = "20") int size,
                  @RequestParam(defaultValue = "timestamp,desc") String[] sort) {

        log.info("Fetching all tenants");

        Sort.Direction direction = (sort.length > 1 && sort[1].equalsIgnoreCase("desc")) ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sorting = Sort.by(direction, sort[0]);
        Pageable pageable = PageRequest.of(page, size, sorting);
        // Build filter map automatically from ALL request parameters
        Map<String, Object> filters = filterBuilder.buildFromRequest(request);

        log.debug("Generated filters: {}", filters);

        Page<Tenant> tenants = tenantService.findAll(filters, pageable);
        PagedModel<EntityModel<Object>> pagedModel = pagedResourcesAssembler.toModel(tenants, plainAssembler);

        String filterString = filters.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(", "));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(PagedAPIResponse.of(pagedModel,
                        "Tenants - Page " + page + " of " + tenants.getTotalPages() + ", Size: " + size +
                                ", Sort: " + sorting + ", Filters: [" + filterString + "]"));
    }

    /**
     * Returns all tenants as a list needed for user creation.
     *
     * @return 200 OK with the list of tenants
     */
    @GetMapping(path = "/list")
    public ResponseEntity<GenericApiResponse<List<Tenant>>> getAllTenants() {
        log.info("Fetching all tenants");
        List<Tenant> tenants = tenantService.findAllAsList();
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
     * Updates the mutable settings of an existing tenant.
     *
     * <p>{@code participantId} is read-only after creation; any value supplied in the request
     * body is silently ignored and the stored value is preserved.
     *
     * @param id      the tenant identifier
     * @param updates the tenant body with the fields to update
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
