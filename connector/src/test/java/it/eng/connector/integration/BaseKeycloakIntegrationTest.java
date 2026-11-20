package it.eng.connector.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Base integration test for Keycloak-secured scenarios.
 *
 * <p>Imports the seed realms from {@code ci/docker/keycloak_resources}:
 * <ul>
 *   <li>{@code platform} — machine-to-machine clients ({@code CONNECTOR}, client credentials grant)</li>
 *   <li>{@code connector-admin} — super-admins ({@code SUPER_ADMIN}, no tenant)</li>
 *   <li>{@code tenant-EngineeringA} — tenant admins ({@code ADMIN}), tenant id {@code engineering-a}</li>
 *   <li>{@code tenant-EngineeringB} — tenant admins ({@code ADMIN}), tenant id {@code engineering-b}</li>
 * </ul>
 *
 * <p>The connector trusts the realm of every enabled tenant through the trusted-issuer registry. The test
 * seed binds tenant {@code engineering-a} to {@code tenant-EngineeringA}; no tenant is bound to
 * {@code tenant-EngineeringB} unless a test inserts one. Platform tokens are trusted on protocol routes
 * (and get 403 on {@code /api/**}); super-admin tokens are trusted on {@code /api/**}.
 */
@TestPropertySource(properties = "application.auth.provider=KEYCLOAK")
public abstract class BaseKeycloakIntegrationTest extends BaseIntegrationTest {

    private static final String PLATFORM_REALM = "platform";
    private static final String SUPER_ADMIN_REALM = "connector-admin";
    private static final String TENANT_REALM = "tenant-EngineeringA";
    private static final String TENANT_B_REALM = "tenant-EngineeringB";
    private static final List<String> REALM_IMPORT_FILES = List.of(
            "platform-realm.json", "connector-admin-realm.json", "tenant-EngineeringA-realm.json",
            "tenant-EngineeringB-realm.json");
    private static final String KEYCLOAK_AUDIENCE = "dsp-true-connector";
    private static final String REALM_FRONTEND_URL_ATTRIBUTE = "frontendUrl";
    private static final String KEYCLOAK_UI_CLIENT_ID = "keycloak-api";
    private static final String KEYCLOAK_BACKEND_CLIENT_ID = "dsp-connector-backend";
    private static final String KEYCLOAK_BACKEND_CLIENT_SECRET = "dsp-connector-backend-secret";
    private static final String KEYCLOAK_ADMIN_USERNAME = "admin";
    private static final String KEYCLOAK_ADMIN_PASSWORD = "password";
    private static final String KEYCLOAK_TENANT_B_ADMIN_USERNAME = "adminB";
    private static final String KEYCLOAK_TENANT_B_ADMIN_PASSWORD = "password";
    private static final String KEYCLOAK_SUPER_ADMIN_USERNAME = "superadmin@mail.com";
    private static final String KEYCLOAK_SUPER_ADMIN_PASSWORD = "password";
    private static final Duration KEYCLOAK_STARTUP_TIMEOUT = Duration.ofMinutes(3);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static final KeycloakContainer KEYCLOAK_CONTAINER = new KeycloakContainer()
            .withStartupTimeout(KEYCLOAK_STARTUP_TIMEOUT);

    static {
        KEYCLOAK_CONTAINER.start();
        importRealms();
    }

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("application.auth.provider", () -> "KEYCLOAK");
        registry.add("server.port", () -> "0");
        // Tokens are requested through the same URL, so their iss is <base-url>/realms/<realm>.
        registry.add("application.keycloak.base-url", BaseKeycloakIntegrationTest::keycloakBaseUrl);
        registry.add("application.keycloak.platform-realm", () -> PLATFORM_REALM);
        registry.add("application.keycloak.admin-realm", () -> SUPER_ADMIN_REALM);
        registry.add("application.keycloak.audience", () -> KEYCLOAK_AUDIENCE);
        registry.add("application.keycloak.backend.client-id", () -> KEYCLOAK_BACKEND_CLIENT_ID);
        registry.add("application.keycloak.backend.client-secret", () -> KEYCLOAK_BACKEND_CLIENT_SECRET);
        registry.add("application.baseURL", () -> "http://localhost:8080/");
        registry.add("application.automatic.negotiation", () -> "false");
        registry.add("application.encryption.key", () -> "5xplehys9mtcatb");
        registry.add("s3.accessKey", () -> "minioadmin");
        registry.add("s3.secretKey", () -> "minioadmin");
        registry.add("s3.region", () -> "us-east-1");
        registry.add("s3.bucketName", () -> "dsp-true-connector");
        registry.add("server.ssl.enabled", () -> "false");
        registry.add("application.usagecontrol.enabled", () -> "true");
        registry.add("application.usagecontrol.constraint.location", () -> "EU");
        registry.add("application.usagecontrol.constraint.purpose", () -> "demo");
    }

    /**
     * Returns a bearer token for the tenant admin of the {@code tenant-EngineeringA} realm.
     *
     * @return a valid bearer token with the {@code ADMIN} realm role
     * @throws Exception when the token cannot be obtained
     */
    protected String adminAccessToken() throws Exception {
        return accessToken(TENANT_REALM, passwordGrant(KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_ADMIN_PASSWORD));
    }

    /**
     * Returns a bearer token for the tenant admin of the {@code tenant-EngineeringA} realm, requested through
     * another public client of that realm.
     *
     * @param clientId the public client with direct access grants, for example {@code admin-cli}
     * @return a bearer token whose audience and scopes are those of {@code clientId}
     * @throws Exception when the token cannot be obtained
     */
    protected String adminAccessToken(String clientId) throws Exception {
        return accessToken(TENANT_REALM, passwordGrant(clientId, KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_ADMIN_PASSWORD));
    }

    /**
     * Returns a bearer token for the tenant admin of the {@code tenant-EngineeringB} realm.
     *
     * @return a valid bearer token with the {@code ADMIN} realm role and tenant id {@code engineering-b}
     * @throws Exception when the token cannot be obtained
     */
    protected String tenantBAdminAccessToken() throws Exception {
        return accessToken(TENANT_B_REALM,
                passwordGrant(KEYCLOAK_TENANT_B_ADMIN_USERNAME, KEYCLOAK_TENANT_B_ADMIN_PASSWORD));
    }

    /**
     * Returns a machine-to-machine bearer token from the {@code platform} realm,
     * obtained with the client credentials grant.
     *
     * @return a valid bearer token with the {@code CONNECTOR} realm role
     * @throws Exception when the token cannot be obtained
     */
    protected String connectorAccessToken() throws Exception {
        return accessToken(PLATFORM_REALM, clientCredentialsGrant());
    }

    /**
     * Returns a bearer token for the super-admin of the {@code connector-admin} realm.
     *
     * @return a valid bearer token with the {@code SUPER_ADMIN} realm role
     * @throws Exception when the token cannot be obtained
     */
    protected String superAdminAccessToken() throws Exception {
        return accessToken(SUPER_ADMIN_REALM,
                passwordGrant(KEYCLOAK_SUPER_ADMIN_USERNAME, KEYCLOAK_SUPER_ADMIN_PASSWORD));
    }

    /**
     * Formats the given JWT for an Authorization header.
     *
     * @param accessToken the raw JWT access token
     * @return the Bearer header value
     */
    protected String bearerToken(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String accessToken(String realm, String formBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(realmUrl(realm) + "/protocol/openid-connect/token"))
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Unable to obtain Keycloak access token from realm '%s'. Status: %d, body: %s"
                    .formatted(realm, response.statusCode(), response.body()));
        }

        JsonNode responseJson = jsonMapper.readTree(response.body());
        JsonNode accessToken = responseJson.get("access_token");
        if (accessToken == null || accessToken.asText().isBlank()) {
            throw new IllegalStateException("Keycloak token response does not contain a usable access_token field.");
        }
        return accessToken.asText();
    }

    private static Path resolveRealmImportFile(String fileName) {
        Path currentDirectory = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                currentDirectory.resolve("ci/docker/keycloak_resources/" + fileName),
                currentDirectory.resolve("../ci/docker/keycloak_resources/" + fileName).normalize()
        );

        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to find ci/docker/keycloak_resources/" + fileName + " for Keycloak integration tests."
                ));
    }

    /**
     * Returns the Keycloak base URL of the test container, without a trailing slash.
     *
     * @return the Keycloak base URL
     */
    protected static String keycloakBaseUrl() {
        String authServerUrl = KEYCLOAK_CONTAINER.getAuthServerUrl();
        return authServerUrl.endsWith("/") ? authServerUrl.substring(0, authServerUrl.length() - 1) : authServerUrl;
    }

    /**
     * Returns the issuer URL of a realm in the test container, as carried in the {@code iss} claim.
     *
     * @param realm the realm name
     * @return the realm's issuer URL
     */
    protected static String realmUrl(String realm) {
        return keycloakBaseUrl() + "/realms/" + realm;
    }

    /**
     * Returns an admin client for the {@code master} realm of the shared test container.
     *
     * <p>The container is shared by every Keycloak integration test: a test that changes a realm through
     * this client must restore it.
     *
     * @return the Keycloak admin client
     */
    protected static Keycloak keycloakAdminClient() {
        return KEYCLOAK_CONTAINER.getKeycloakAdminClient();
    }

    private static void importRealms() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            for (String fileName : REALM_IMPORT_FILES) {
                RealmRepresentation realmRepresentation = objectMapper
                        .readValue(Files.readString(resolveRealmImportFile(fileName)), RealmRepresentation.class);
                // The seeds pin the issuer to http://localhost:8180; the container runs on a random port.
                if (realmRepresentation.getAttributes() != null) {
                    realmRepresentation.getAttributes().remove(REALM_FRONTEND_URL_ATTRIBUTE);
                }

                KEYCLOAK_CONTAINER.getKeycloakAdminClient().realms().create(realmRepresentation);
                boolean realmPresent = KEYCLOAK_CONTAINER.getKeycloakAdminClient().realms().findAll().stream()
                        .anyMatch(realm -> realmRepresentation.getRealm().equals(realm.getRealm()));
                if (!realmPresent) {
                    throw new IllegalStateException("Keycloak test realm '%s' was not created successfully."
                            .formatted(realmRepresentation.getRealm()));
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize Keycloak test realms.", exception);
        }
    }

    private static String passwordGrant(String username, String password) {
        return passwordGrant(KEYCLOAK_UI_CLIENT_ID, username, password);
    }

    private static String passwordGrant(String clientId, String username, String password) {
        return "grant_type=password"
                + "&client_id=" + encode(clientId)
                + "&username=" + encode(username)
                + "&password=" + encode(password);
    }

    private static String clientCredentialsGrant() {
        return "grant_type=client_credentials"
                + "&client_id=" + encode(KEYCLOAK_BACKEND_CLIENT_ID)
                + "&client_secret=" + encode(KEYCLOAK_BACKEND_CLIENT_SECRET);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
