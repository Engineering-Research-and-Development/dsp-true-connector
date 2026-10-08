package it.eng.tools.rest.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body carrying a realm client secret; the secret is excluded from {@code toString}.
 *
 * @param clientSecret the Keycloak client secret
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RealmCredentialsRequest(String clientSecret) {

    @Override
    public String toString() {
        return "RealmCredentialsRequest[clientSecret=***]";
    }
}
