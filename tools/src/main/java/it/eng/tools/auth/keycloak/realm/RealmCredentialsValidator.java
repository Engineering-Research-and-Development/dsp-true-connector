package it.eng.tools.auth.keycloak.realm;

import it.eng.tools.auth.keycloak.KeycloakProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Validates a realm client secret with a real client-credentials request against the tenant's realm.
 */
@Slf4j
@Component
public class RealmCredentialsValidator {

    private final ObjectProvider<KeycloakProperties> keycloakProperties;
    private final OkHttpClient okHttpClient;

    /**
     * Constructs the validator.
     *
     * @param keycloakProperties the Keycloak settings, only available in Keycloak mode
     * @param okHttpClient       the HTTP client used for the token request
     */
    public RealmCredentialsValidator(ObjectProvider<KeycloakProperties> keycloakProperties,
                                     OkHttpClient okHttpClient) {
        this.keycloakProperties = keycloakProperties;
        this.okHttpClient = okHttpClient;
    }

    /**
     * Checks whether Keycloak validation is available in the current authentication mode.
     *
     * @return {@code true} when Keycloak settings are configured
     */
    public boolean isAvailable() {
        return keycloakProperties.getIfAvailable() != null;
    }

    /**
     * Requests a token for the realm using the supplied secret. Neither the secret nor any Keycloak
     * response detail is logged.
     *
     * @param realm        the Keycloak realm
     * @param clientSecret the client secret to validate
     * @return {@code true} if Keycloak issued a token for the credentials
     */
    public boolean isValid(String realm, String clientSecret) {
        KeycloakProperties props = keycloakProperties.getIfAvailable();
        if (props == null) {
            return false;
        }
        Request request = new Request.Builder()
                .url(props.tokenUrl(realm))
                .post(new FormBody.Builder()
                        .add("grant_type", "client_credentials")
                        .add("client_id", props.clientId())
                        .add("client_secret", clientSecret)
                        .build())
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (Exception e) {
            log.warn("Realm credential validation request failed for realm '{}'.", realm);
            return false;
        }
    }
}
