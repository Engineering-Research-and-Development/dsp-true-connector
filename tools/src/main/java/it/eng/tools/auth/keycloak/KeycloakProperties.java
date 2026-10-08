package it.eng.tools.auth.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Typed Keycloak configuration, bound from {@code application.keycloak.*}. Only active in
 * {@code KEYCLOAK} authentication mode, where missing required settings fail startup.
 * No network call is made during validation, so an unreachable Keycloak does not prevent startup.
 *
 * @param baseUrl              Keycloak server base URL, e.g. {@code https://keycloak.example.com}
 * @param platformRealm        realm used for platform (super-admin) authentication
 * @param clientId             client id used in every realm
 * @param audience             expected token audience
 * @param platformClientSecret client secret for the platform realm; never logged
 * @param userAdmin            user administration settings
 */
@Validated
@ConfigurationProperties(prefix = "application.keycloak")
public record KeycloakProperties(
        @NotBlank(message = "application.keycloak.base-url must be set in KEYCLOAK mode")
        String baseUrl,
        @NotBlank(message = "application.keycloak.platform-realm must be set in KEYCLOAK mode")
        String platformRealm,
        @NotBlank(message = "application.keycloak.client-id must be set in KEYCLOAK mode")
        String clientId,
        @NotBlank(message = "application.keycloak.audience must be set in KEYCLOAK mode")
        String audience,
        @NotBlank(message = "application.keycloak.platform-client-secret must be set in KEYCLOAK mode")
        String platformClientSecret,
        @Valid @NotNull @DefaultValue UserAdmin userAdmin) {

    /**
     * User administration settings.
     *
     * @param enabled whether Keycloak user administration is enabled (declaration only, default false)
     */
    public record UserAdmin(@DefaultValue("false") boolean enabled) {
    }

    /**
     * Builds the token endpoint URL of a realm.
     *
     * @param realm the realm name
     * @return the realm token endpoint URL
     */
    public String tokenUrl(String realm) {
        return realmUrl(realm) + "/protocol/openid-connect/token";
    }

    /**
     * Builds the logout endpoint URL of a realm.
     *
     * @param realm the realm name
     * @return the realm logout endpoint URL
     */
    public String logoutUrl(String realm) {
        return realmUrl(realm) + "/protocol/openid-connect/logout";
    }

    /**
     * Builds the issuer URL of a realm.
     *
     * @param realm the realm name
     * @return the realm issuer URL
     */
    public String realmUrl(String realm) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/realms/" + realm;
    }

    @Override
    public String toString() {
        return "KeycloakProperties[baseUrl=" + baseUrl + ", platformRealm=" + platformRealm
                + ", clientId=" + clientId + ", audience=" + audience
                + ", platformClientSecret=****, userAdmin=" + userAdmin + "]";
    }
}
