package it.eng.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import it.eng.tools.auth.keycloak.KeycloakAuthenticationService;
import it.eng.tools.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collection;

/**
 * Service for registering users in Keycloak via the Admin REST API.
 *
 * <p>This service is active only when {@code application.auth.provider=KEYCLOAK}.
 * It calls {@code POST /admin/realms/{realm}/users} using a client-credentials token
 * obtained through the existing {@link KeycloakAuthenticationService}.
 */
@Service
@Slf4j
@Conditional(KeycloakAuthenticationModeCondition.class)
public class KeycloakUserService {

    private final KeycloakAuthenticationService keycloakAuthService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String adminServerUrl;
    private final String adminRealm;

    /**
     * Creates the service with its required dependencies.
     *
     * @param keycloakAuthService the service used to obtain a client-credentials access token
     * @param adminServerUrl      the Keycloak server base URL, e.g. {@code http://localhost:8180};
     *                            injected from {@code application.keycloak.admin.server-url}
     * @param adminRealm          the Keycloak realm name, e.g. {@code dsp-connector};
     *                            injected from {@code application.keycloak.admin.realm}
     */
    @Autowired
    public KeycloakUserService(
            KeycloakAuthenticationService keycloakAuthService,
            @Value("${application.keycloak.admin.server-url}") String adminServerUrl,
            @Value("${application.keycloak.admin.realm}") String adminRealm) {
        this.keycloakAuthService = keycloakAuthService;
        this.adminServerUrl = adminServerUrl;
        this.adminRealm = adminRealm;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a package-visible constructor for testing purposes.
     *
     * @param keycloakAuthService the service used to obtain a client-credentials access token
     * @param adminServerUrl      the Keycloak server base URL
     * @param adminRealm          the Keycloak realm name
     * @param httpClient          the HTTP client to use
     */
    KeycloakUserService(
            KeycloakAuthenticationService keycloakAuthService,
            String adminServerUrl,
            String adminRealm,
            HttpClient httpClient) {
        this.keycloakAuthService = keycloakAuthService;
        this.adminServerUrl = adminServerUrl;
        this.adminRealm = adminRealm;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Returns all users from Keycloak, optionally filtered by e-mail.
     *
     * @param email optional e-mail filter; may be {@code null} or blank
     * @return the matching users as JSON nodes
     * @throws BadRequestException if the Keycloak Admin API call fails
     */
    public Collection<JsonNode> findUsers(String email) {
        try {
            String token = keycloakAuthService.fetchToken("ROLE_ADMIN");
            String url = (adminServerUrl.endsWith("/") ? adminServerUrl.substring(0, adminServerUrl.length() - 1) : adminServerUrl)
                    + "/admin/realms/" + adminRealm + "/users";
            if (email != null && !email.isBlank()) {
                url += "?email=" + email + "&exact=true";
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BadRequestException("Keycloak returned " + response.statusCode()
                        + " when listing users: " + response.body());
            }
            JsonNode usersArray = objectMapper.readTree(response.body());
            return objectMapper.readerForListOf(JsonNode.class).readValue(usersArray);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to list users in Keycloak", e);
            throw new BadRequestException("Failed to list users in Keycloak: " + e.getMessage());
        }
    }
}
