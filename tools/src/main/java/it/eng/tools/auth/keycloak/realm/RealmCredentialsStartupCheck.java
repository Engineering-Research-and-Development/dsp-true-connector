package it.eng.tools.auth.keycloak.realm;

import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
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

    private final TenantRepository tenantRepository;
    private final RealmCredentialsService realmCredentialsService;

    /**
     * Constructs the startup check.
     *
     * @param tenantRepository        the tenant repository
     * @param realmCredentialsService the realm credentials service
     */
    public RealmCredentialsStartupCheck(TenantRepository tenantRepository,
                                        RealmCredentialsService realmCredentialsService) {
        this.tenantRepository = tenantRepository;
        this.realmCredentialsService = realmCredentialsService;
    }

    /**
     * Warns about tenants with a realm but without configured credentials; never logs secrets.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnAboutMissingCredentials() {
        for (Tenant tenant : tenantRepository.findAll()) {
            if (StringUtils.hasText(tenant.getRealm()) && !realmCredentialsService.exists(tenant.getId())) {
                log.warn("Tenant '{}' is bound to realm '{}' but has no realm credentials configured.",
                        tenant.getId(), tenant.getRealm());
            }
        }
    }
}
