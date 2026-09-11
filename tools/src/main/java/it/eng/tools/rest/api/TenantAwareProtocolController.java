package it.eng.tools.rest.api;

import it.eng.tools.model.Tenant;
import it.eng.tools.service.TenantContextHolder;
import it.eng.tools.service.TenantService;

/**
 * Abstract base class for protocol controllers that require tenant resolution.
 * Subclasses call {@link #resolveTenant(String)} to look up and activate the tenant context
 * for the current request before delegating to DSP protocol logic.
 */
public abstract class TenantAwareProtocolController {

    private final TenantService tenantService;

    /**
     * Constructs the base controller with its tenant service dependency.
     *
     * @param tenantService the service used to look up tenants
     */
    protected TenantAwareProtocolController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Resolves the tenant for the given ID and sets it as the current thread's tenant context.
     * Only enabled tenants are accepted; a disabled or unknown tenant raises an exception.
     *
     * @param tenantId the tenant identifier extracted from the request path
     * @return the resolved {@link Tenant}
     */
    protected Tenant resolveTenant(String tenantId) {
        Tenant tenant = tenantService.findEnabledTenantById(tenantId);
        TenantContextHolder.setTenantId(tenant.getId());
        return tenant;
    }
}
