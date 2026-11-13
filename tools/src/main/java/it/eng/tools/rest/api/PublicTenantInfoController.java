package it.eng.tools.rest.api;

import it.eng.tools.model.PublicTenantInfo;
import it.eng.tools.service.TenantService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes public information about enabled tenants.
 */
@RestController
@RequestMapping(path = "/public/tenant", produces = MediaType.APPLICATION_JSON_VALUE)
public class PublicTenantInfoController {

    private final TenantService tenantService;

    /**
     * Creates the controller with its tenant service dependency.
     *
     * @param tenantService the tenant service
     */
    public PublicTenantInfoController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Returns the names and realms of enabled tenants.
     *
     * @return the public information for enabled tenants
     */
    @GetMapping
    public List<PublicTenantInfo> getPublicTenantInfo() {
        return tenantService.findEnabledTenants().stream()
                .map(tenant -> new PublicTenantInfo(tenant.getName(), tenant.getRealm()))
                .toList();
    }
}
