package it.eng.connector.integration.multitenant;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.tools.model.Tenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Base class for the API tenant isolation tests.
 *
 * <p>Two enabled tenants exist: {@link #TENANT_A} (the default test tenant) and {@link #TENANT_B}. Requests are
 * sent as a tenant admin whose stored tenant is one of them, exactly as {@code ApiTenantContextFilter} resolves a
 * real logged-in user, so the tenant comes from the principal and never from a header.
 */
public abstract class AbstractTenantIsolationIT extends BaseIntegrationTest {

    protected static final String TENANT_A = TENANT_ID;
    protected static final String TENANT_B = "isolation-tenant-b";

    @BeforeEach
    void ensureTenantB() {
        if (tenantRepository.findById(TENANT_B).isEmpty()) {
            tenantRepository.save(Tenant.Builder.newInstance()
                    .id(TENANT_B)
                    .name("IsolationTenantB")
                    .participantId("urn:connector:" + TENANT_B)
                    .bucketName(s3Properties.getBucketName())
                    .enabled(true)
                    .build());
        }
    }

    @AfterEach
    void removeTenantB() {
        tenantRepository.deleteById(TENANT_B);
    }

    /**
     * Returns a request post-processor that authenticates as an ADMIN bound to the given tenant.
     *
     * @param tenantId the tenant of the admin
     * @return the post-processor
     */
    protected static RequestPostProcessor adminOf(String tenantId) {
        return user(new User("urn:uuid:isolation-admin-" + tenantId, "Isolation", "Admin",
                "admin-" + tenantId + "@mail.com", "password", true, false, false, Role.ADMIN, tenantId));
    }

    /**
     * Returns the {@code data} node of a {@code GenericApiResponse} body.
     *
     * @param result the MVC result
     * @return the data node
     * @throws Exception if the body cannot be read
     */
    protected JsonNode data(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    /**
     * Returns the {@code data.content} node of a paged API response body.
     *
     * @param result the MVC result
     * @return the content array
     * @throws Exception if the body cannot be read
     */
    protected JsonNode pagedContent(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString())
                .path("response").path("data").path("content");
    }

    /**
     * Collects the {@code id} or {@code @id} values of the objects in a JSON array.
     *
     * @param array the JSON array
     * @return the ids
     */
    protected static Set<String> ids(JsonNode array) {
        Set<String> ids = new HashSet<>();
        array.forEach(node -> {
            JsonNode id = node.has("@id") ? node.get("@id") : node.get("id");
            if (id != null) {
                ids.add(id.asText());
            }
        });
        return ids;
    }

    /**
     * Asserts that a response body mentions none of the given values, such as ids of another tenant's documents.
     *
     * @param result    the MVC result
     * @param forbidden values that must not appear
     * @throws Exception if the body cannot be read
     */
    protected static void assertBodyDoesNotContain(MvcResult result, String... forbidden) throws Exception {
        String body = result.getResponse().getContentAsString();
        for (String value : forbidden) {
            assertFalse(body.contains(value), "Response must not contain '" + value + "' but was: " + body);
        }
    }
}
