package it.eng.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import it.eng.tools.auth.keycloak.KeycloakAuthenticationService;
import it.eng.tools.event.TenantUpdatedEvent;
import it.eng.tools.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/**
 * Service for managing users in Keycloak via the Admin REST API.
 *
 * <p>This service is active only when {@code application.auth.provider=KEYCLOAK}.
 * It calls {@code /admin/realms/{realm}/users} using a client-credentials token
 * obtained through the existing {@link KeycloakAuthenticationService}; the backend client's
 * service account therefore needs the {@code realm-management} roles {@code view-users} and
 * {@code manage-users}.
 *
 * <p>It also keeps the {@value #TENANT_NAME_ATTRIBUTE} user attribute, which Keycloak maps into the
 * access token's {@code tenant_name} claim, in sync with the tenant name stored in MongoDB.
 */
@Service
@Slf4j
@Conditional(KeycloakAuthenticationModeCondition.class)
public class KeycloakUserService {

    /** User attribute holding the user's tenant identifier, mapped to the {@code tenant_id} claim. */
    static final String TENANT_ID_ATTRIBUTE = "tenant_id";

    /** User attribute holding the user's tenant name, mapped to the {@code tenant_name} claim. */
    static final String TENANT_NAME_ATTRIBUTE = "tenant_name";

    private static final int PAGE_SIZE = 100;

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
            String url = usersUrl();
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

    /**
     * Writes the new tenant name to every Keycloak user of the updated tenant.
     *
     * <p>Events are dispatched asynchronously, so a failure cannot reach the caller that updated the
     * tenant; it is logged instead. Saving the tenant again retries the synchronization.
     *
     * @param event the tenant update event
     */
    @EventListener
    public void onTenantUpdated(TenantUpdatedEvent event) {
        try {
            int updated = syncTenantName(event.tenantId(), event.tenantName());
            log.info("Synchronized tenant name of tenant '{}' to {} Keycloak user(s)", event.tenantId(), updated);
        } catch (Exception e) {
            log.error("Failed to synchronize tenant name of tenant '{}' to Keycloak users", event.tenantId(), e);
        }
    }

    /**
     * Sets the {@value #TENANT_NAME_ATTRIBUTE} attribute of every user whose
     * {@value #TENANT_ID_ATTRIBUTE} attribute equals {@code tenantId}.
     *
     * @param tenantId   the tenant identifier
     * @param tenantName the tenant's current name
     * @return the number of users whose attribute was changed
     * @throws IOException          if a Keycloak Admin API call fails
     * @throws InterruptedException if the calling thread is interrupted
     */
    int syncTenantName(String tenantId, String tenantName) throws IOException, InterruptedException {
        String token = keycloakAuthService.fetchToken("ROLE_ADMIN");
        String query = URLEncoder.encode(TENANT_ID_ATTRIBUTE + ":" + tenantId, StandardCharsets.UTF_8);
        int updated = 0;
        for (int first = 0; ; first += PAGE_SIZE) {
            String url = usersUrl() + "?briefRepresentation=false&q=" + query + "&first=" + first + "&max=" + PAGE_SIZE;
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Authorization", "Bearer " + token)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Keycloak returned " + response.statusCode()
                        + " when searching users of tenant '" + tenantId + "': " + response.body());
            }
            JsonNode users = objectMapper.readTree(response.body());
            for (JsonNode user : users) {
                if (!tenantName.equals(user.path("attributes").path(TENANT_NAME_ATTRIBUTE).path(0).asText(null))) {
                    updateTenantName((ObjectNode) user, tenantName, token);
                    updated++;
                }
            }
            if (users.size() < PAGE_SIZE) {
                return updated;
            }
        }
    }

    private void updateTenantName(ObjectNode user, String tenantName, String token)
            throws IOException, InterruptedException {
        user.withObject("/attributes").set(TENANT_NAME_ATTRIBUTE, objectMapper.valueToTree(List.of(tenantName)));
        String userId = user.path("id").asText();
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                        .uri(URI.create(usersUrl() + "/" + userId))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(user)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 204) {
            throw new IOException("Keycloak returned " + response.statusCode()
                    + " when updating user '" + userId + "': " + response.body());
        }
    }

    private String usersUrl() {
        return (adminServerUrl.endsWith("/") ? adminServerUrl.substring(0, adminServerUrl.length() - 1) : adminServerUrl)
                + "/admin/realms/" + adminRealm + "/users";
    }
}
