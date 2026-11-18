package it.eng.tools.auth.keycloak;

import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Keycloak trust configuration: the Keycloak base URL, the expected audience, the names of the
 * static realms and the client ids expected in platform tokens and in tenant and connector-admin tokens. It is the only place that turns a realm name into an issuer URL, see
 * {@link #issuerFor(String)}.
 */
@Slf4j
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "application.keycloak")
@Conditional(KeycloakAuthenticationModeCondition.class)
public class KeycloakTrustProperties {

    /**
     * Allowed realm names. Excludes {@code /}, so a realm name can never change the issuer path.
     */
    public static final Pattern REALM_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

    /**
     * Keycloak base URL, absolute {@code http} or {@code https}, stored without a trailing slash.
     */
    private String baseUrl;

    /**
     * Audience expected in access tokens.
     */
    private String audience = "dsp-true-connector";

    /**
     * Realm used for machine-to-machine (platform) access.
     */
    private String platformRealm = "platform";

    /**
     * Realm used for connector administrators.
     */
    private String adminRealm = "connector-admin";

    /**
     * Client id that a token of the platform realm must name in {@code azp}: the machine-to-machine client
     * that connectors use for DSP protocol calls. Inbound trust rule only; the outbound credential is
     * configured separately under {@code application.keycloak.backend}.
     */
    private String platformClientId = "dsp-connector-backend";

    /**
     * Client id that a token of a tenant realm or of the connector-admin realm must name in {@code azp}: the
     * public client the UI uses to log users in. Inbound trust rule only.
     */
    private String uiClientId = "keycloak-api";

    /**
     * Connect timeout for discovery document and JWK set requests.
     */
    private Duration discoveryConnectTimeout = Duration.ofSeconds(5);

    /**
     * Wait for the response headers of discovery document and JWK set requests. The whole request, body
     * included, is bounded by the connect plus the read timeout.
     */
    private Duration discoveryReadTimeout = Duration.ofSeconds(10);

    /**
     * Sets the Keycloak base URL, trimmed and without trailing slashes.
     *
     * @param baseUrl the Keycloak base URL, may be {@code null}
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? null : stripTrailingSlashes(baseUrl.trim());
    }

    /**
     * Validates the configuration. Fails startup on an invalid value.
     *
     * @throws IllegalStateException when a property is missing or invalid
     */
    @PostConstruct
    public void init() {
        validateBaseUrl();
        validateRealm(platformRealm, "application.keycloak.platform-realm");
        validateRealm(adminRealm, "application.keycloak.admin-realm");
        if (audience == null || audience.isBlank()) {
            throw new IllegalStateException("application.keycloak.audience must not be blank");
        }
        if (platformClientId == null || platformClientId.isBlank()) {
            throw new IllegalStateException("application.keycloak.platform-client-id must not be blank");
        }
        if (uiClientId == null || uiClientId.isBlank()) {
            throw new IllegalStateException("application.keycloak.ui-client-id must not be blank");
        }
        requirePositive(discoveryConnectTimeout, "application.keycloak.discovery-connect-timeout");
        requirePositive(discoveryReadTimeout, "application.keycloak.discovery-read-timeout");
        log.info("Keycloak trust configured: baseUrl={}, platformRealm={}, adminRealm={}, platformClientId={}, "
                + "uiClientId={}", baseUrl, platformRealm, adminRealm, platformClientId, uiClientId);
    }

    /**
     * Builds the issuer URL of a realm as {@code <base-url>/realms/<realm>}. Callers must use this
     * method instead of building issuer URLs themselves.
     *
     * @param realm the realm name, must match {@code ^[A-Za-z0-9._-]+$} and must not be {@code .} or {@code ..}
     * @return the issuer URL of the realm
     * @throws IllegalArgumentException when the realm name is not valid
     * @throws IllegalStateException when {@code base-url} is not configured
     */
    public String issuerFor(String realm) {
        if (realm == null || !REALM_PATTERN.matcher(realm).matches() || ".".equals(realm) || "..".equals(realm)) {
            throw new IllegalArgumentException("Invalid Keycloak realm name");
        }
        if (baseUrl == null) {
            throw new IllegalStateException("application.keycloak.base-url is not configured");
        }
        return baseUrl + "/realms/" + realm;
    }

    private void validateBaseUrl() {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalStateException("application.keycloak.base-url is required in Keycloak mode");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("application.keycloak.base-url is not a valid URL", e);
        }
        String scheme = uri.getScheme();
        boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        if (!uri.isAbsolute() || !httpScheme || uri.getHost() == null) {
            throw new IllegalStateException("application.keycloak.base-url must be an absolute http or https URL");
        }
    }

    private static void validateRealm(String realm, String property) {
        if (realm == null || !REALM_PATTERN.matcher(realm).matches() || ".".equals(realm) || "..".equals(realm)) {
            throw new IllegalStateException(property + " must match " + REALM_PATTERN.pattern());
        }
    }

    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(property + " must be a positive duration");
        }
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
