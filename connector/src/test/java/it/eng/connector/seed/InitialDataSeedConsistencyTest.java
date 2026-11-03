package it.eng.connector.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that every {@code initial_data*.json} seed copy in the repository describes exactly one tenant
 * bound to a seeded Keycloak tenant realm, that the tenant id equals the {@code tenant_id} attribute of
 * that realm's users, and that every {@code tenantId} in the file refers to that tenant.
 */
class InitialDataSeedConsistencyTest {

    private static final int MINIMUM_SEED_COUNT = 12;
    private static final String TENANTS_COLLECTION = "tenants";
    private static final String REALM_RESOURCES = "ci/docker/keycloak_resources";
    private static final Set<String> TENANT_REALM_FILES = Set.of(
            "tenant-EngineeringA-realm.json",
            "tenant-EngineeringB-realm.json");
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("target", ".git", "node_modules");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path REPO_ROOT = resolveRepoRoot();

    /**
     * Finds every {@code initial_data*.json} file in the repository, outside build output.
     *
     * @return the seed file paths
     * @throws IOException if the repository cannot be walked
     */
    static Stream<Path> seedFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(REPO_ROOT)) {
            return paths
                    .filter(path -> path.getFileName().toString().matches("initial_data.*\\.json"))
                    .filter(path -> isOutsideSkippedDirectories(REPO_ROOT.relativize(path)))
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    @Test
    void allSeedCopiesAreFound() throws IOException {
        assertThat(seedFiles().count()).isGreaterThanOrEqualTo(MINIMUM_SEED_COUNT);
    }

    @Test
    void tenantRealmUsersCarryTheirTenantId() {
        assertThat(tenantIdByRealm()).containsOnlyKeys("tenant-EngineeringA", "tenant-EngineeringB");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("seedFiles")
    void tenantIdMatchesRealmAndAllTenantIdFields(Path seedFile) throws IOException {
        Map<String, String> tenantIdByRealm = tenantIdByRealm();
        JsonNode root = MAPPER.readTree(Files.readString(seedFile));

        JsonNode tenants = root.path(TENANTS_COLLECTION);
        assertThat(tenants.isArray()).isTrue();
        assertThat(tenants).hasSize(1);

        JsonNode tenant = tenants.get(0);
        String realm = tenant.path("realm").asText();
        assertThat(tenantIdByRealm).containsKey(realm);
        assertThat(realm).matches("^[A-Za-z0-9._-]+$")
                .isNotEqualToIgnoringCase("platform")
                .isNotEqualToIgnoringCase("connector-admin")
                .isNotEqualToIgnoringCase("master");
        String tenantId = tenant.path("_id").asText();
        assertThat(tenantId).isEqualTo(tenantIdByRealm.get(realm));

        List<String> tenantIdValues = new ArrayList<>();
        collectTenantIds(root, tenantIdValues);
        assertThat(tenantIdValues).allMatch(tenantId::equals);
    }

    /**
     * Reads the seeded tenant realms and maps each realm name to the single {@code tenant_id} its users carry.
     */
    private static Map<String, String> tenantIdByRealm() {
        Map<String, String> tenantIdByRealm = new HashMap<>();
        for (String fileName : TENANT_REALM_FILES) {
            JsonNode realm = readJson(REPO_ROOT.resolve(REALM_RESOURCES).resolve(fileName));
            List<String> userTenantIds = new ArrayList<>();
            realm.path("users").forEach(user ->
                    user.path("attributes").path("tenant_id").forEach(value -> userTenantIds.add(value.asText())));
            assertThat(userTenantIds).as("tenant_id of users in %s", fileName).isNotEmpty();
            assertThat(Set.copyOf(userTenantIds)).as("tenant_id of users in %s", fileName).hasSize(1);
            tenantIdByRealm.put(realm.path("realm").asText(), userTenantIds.get(0));
        }
        return tenantIdByRealm;
    }

    private static void collectTenantIds(JsonNode node, List<String> values) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if ("tenantId".equals(entry.getKey())) {
                    values.add(entry.getValue().asText());
                }
                collectTenantIds(entry.getValue(), values);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectTenantIds(child, values));
        }
    }

    private static boolean isOutsideSkippedDirectories(Path relativePath) {
        for (Path segment : relativePath) {
            if (SKIPPED_DIRECTORIES.contains(segment.toString())) {
                return false;
            }
        }
        return true;
    }

    private static JsonNode readJson(Path file) {
        try {
            return MAPPER.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path resolveRepoRoot() {
        Path currentDirectory = Path.of("").toAbsolutePath().normalize();
        return Stream.of(currentDirectory, currentDirectory.resolve("..").normalize())
                .filter(candidate -> Files.isDirectory(candidate.resolve(REALM_RESOURCES)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to find the repository root (" + REALM_RESOURCES + ") from " + currentDirectory));
    }
}
