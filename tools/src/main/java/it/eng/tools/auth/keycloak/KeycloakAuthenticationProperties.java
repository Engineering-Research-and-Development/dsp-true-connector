package it.eng.tools.auth.keycloak;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration properties for Keycloak authentication.
 * Used for connector-to-connector authentication via OAuth2 client credentials flow.
 */
@Slf4j
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "application.keycloak.backend")
@ConditionalOnProperty(value = "application.keycloak.enable", havingValue = "true")
public class KeycloakAuthenticationProperties {

    /**
     * Keycloak client ID for the backend service account.
     */
    private String clientId;

    /**
     * Keycloak client secret for the backend service account.
     */
    private String clientSecret;

    /**
     * Token endpoint URL for obtaining access tokens.
     */
    private String tokenUrl;

    /**
     * Whether to enable token caching to avoid repeated token requests.
     */
    private boolean tokenCaching = true;

    @PostConstruct
    public void init() {
        log.info("=== KeycloakAuthenticationProperties LOADED ===");
        log.info("Client ID: {}", clientId);
        log.info("Token URL: {}", tokenUrl);
        log.info("Token Caching: {}", tokenCaching);
        log.info("Client Secret configured: {}", clientSecret != null && !clientSecret.isEmpty());
        log.info("=== End KeycloakAuthenticationProperties ===");
    }
}

