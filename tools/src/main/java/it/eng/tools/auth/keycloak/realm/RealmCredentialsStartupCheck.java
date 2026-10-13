package it.eng.tools.auth.keycloak.realm;

import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import it.eng.tools.model.Tenant;
import it.eng.tools.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Logs a warning, in Keycloak mode only, for every tenant bound to a realm that has no stored client credentials.
 */
@Slf4j
@Component
@Conditional(KeycloakAuthenticationModeCondition.class)
public class RealmCredentialsStartupCheck {

    private final TenantService tenantService;

    /**
     * Constructs the startup check.
     *
     * @param tenantService        the tenant service
     */
    public RealmCredentialsStartupCheck(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Warns about tenants with a realm but without configured credentials; never logs secrets.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnAboutMissingCredentials() {
        for (Tenant tenant : tenantService.findAllAsList()) {
            if (StringUtils.hasText(tenant.getRealm()) && !StringUtils.hasText(tenant.getClientSecret())) {
                log.warn("Tenant '{}' is bound to realm '{}' but has no realm credentials configured.",
                        tenant.getId(), tenant.getRealm());
            }
        }
    }
}
