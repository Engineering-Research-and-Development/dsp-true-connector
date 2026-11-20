package it.eng.tools.auth.keycloak;

import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Configuration properties of the outbound connector-to-connector client (OAuth2 client credentials flow).
 * <p>
 * The token endpoint is not configured: {@link KeycloakAuthenticationService} takes it from the discovery
 * document of the {@code platform} realm.
 */
@Slf4j
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "application.keycloak.backend")
@Conditional(KeycloakAuthenticationModeCondition.class)
public class KeycloakAuthenticationProperties {

    /**
     * Keycloak client ID for the backend service account.
     */
    private String clientId;

    /**
     * Keycloak client secret for the backend service account, from configuration or the environment.
     */
    private String clientSecret;

    /**
     * Whether the connector-to-connector token is cached until shortly before it expires.
     */
    private boolean tokenCaching = true;

    /**
     * Logs the loaded configuration. The client secret is never logged, only whether one is set.
     */
    @PostConstruct
    public void init() {
        log.info("Keycloak outbound client configured: clientId={}, tokenCaching={}, clientSecretConfigured={}",
                clientId, tokenCaching, clientSecret != null && !clientSecret.isEmpty());
    }
}
