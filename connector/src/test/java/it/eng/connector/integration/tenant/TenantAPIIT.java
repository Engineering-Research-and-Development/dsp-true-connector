package it.eng.connector.integration.tenant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jayway.jsonpath.JsonPath;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.TenantCreateRequest;
import it.eng.tools.model.Tenant;
import it.eng.tools.model.TenantUpdateRequest;
import it.eng.tools.repository.AuditEventRepository;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.repository.BucketCredentialsRepository;
import it.eng.tools.s3.service.BucketCredentialsService;
import it.eng.tools.s3.service.S3BucketProvisionService;
import it.eng.tools.serializer.ToolsSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
public class TenantAPIIT extends BaseIntegrationTest {

    private static final String NEW_TENANT_ID = "test-tenant-it";
    private static final String ENGINEERING_TENANT_ID = "engineering";
    private static final String NON_EXISTING_TENANT_ID = "non-existing-tenant-xyz";

    /** Tracks the server-generated UUID from API-created tenants for @AfterEach cleanup. */
    private String generatedTenantId;
    private final Set<String> createdTenantIds = new HashSet<>();
    private final Set<String> createdBuckets = new HashSet<>();

    @Value("${application.callback.address:http://localhost:8080/}")
    private String baseCallbackAddress;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private S3BucketProvisionService s3BucketProvisionService;

    @Autowired
    private BucketCredentialsService bucketCredentialsService;

    @Autowired
    private BucketCredentialsRepository bucketCredentialsRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @AfterEach
    public void cleanup() {
        tenantRepository.deleteById(NEW_TENANT_ID);
        if (generatedTenantId != null) {
            tenantRepository.deleteById(generatedTenantId);
            generatedTenantId = null;
        }
        for (String createdTenantId : createdTenantIds) {
            tenantRepository.deleteById(createdTenantId);
        }
        createdTenantIds.clear();
        for (String createdBucket : createdBuckets) {
            bucketCredentialsRepository.deleteById(createdBucket);
        }
        createdBuckets.clear();
    }

    private Tenant buildNewTenant() {
        return Tenant.Builder.newInstance()
                .id(NEW_TENANT_ID)
                .name("Test Tenant IT")
                .description("Integration test tenant")
                .participantId("urn:connector:test-it")
                .enabled(true)
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .build();
    }

    private TenantCreateRequest buildTenantCreateRequest(String tenantId, String participantId) {
        return TenantCreateRequest.Builder.newInstance()
                .id(tenantId)
                .name("Tenant " + tenantId)
                .description("Integration test tenant")
                .participantId(participantId)
                .enabled(true)
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .build();
    }

    private TenantUpdateRequest buildTenantUpdateRequest(String name, String description) {
        return TenantUpdateRequest.Builder.newInstance()
                .name(name)
                .description(description)
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/tenants as SUPER_ADMIN returns 200 with GenericApiResponse")
    public void getTenantsAsSuperAdmin_returns200() throws Exception {
        mockMvc.perform(get(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response.success").value(true))
                .andExpect(jsonPath("$.response.message").exists());
    }

    @Test
    @DisplayName("GET /api/v1/tenants as ADMIN returns 403")
    public void getTenants_asAdmin_returns403() throws Exception {
        mockMvc.perform(get(ApiEndpoints.TENANTS_V1)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/tenants unauthenticated returns 401")
    public void getTenants_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ApiEndpoints.TENANTS_V1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/tenants/engineering returns the default Engineering tenant")
    public void getEngineeringTenant_exists() throws Exception {
        mockMvc.perform(get(ApiEndpoints.TENANTS_V1 + "/" + ENGINEERING_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.id").value("engineering"));
    }

    @Test
    @DisplayName("POST /api/v1/tenants as SUPER_ADMIN creates a tenant and returns 200 with client-supplied id")
    public void createTenant_asSuperAdmin_returns200() throws Exception {
        TenantCreateRequest newTenant = buildTenantCreateRequest(NEW_TENANT_ID, "urn:connector:test-it");

        MvcResult result = mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(newTenant))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.name").value("Tenant " + NEW_TENANT_ID))
                .andExpect(jsonPath("$.data.id").value(NEW_TENANT_ID))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        generatedTenantId = JsonPath.read(responseBody, "$.data.id");
    }

    @Test
    @DisplayName("POST /api/v1/tenants - server uses client-supplied id")
    public void createTenant_usesClientSuppliedId() throws Exception {
        String clientId = "my-custom-tenant-id";
        TenantCreateRequest request = buildTenantCreateRequest(clientId, "urn:connector:custom-id-test");

        MvcResult result = mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(clientId))
                .andReturn();

        generatedTenantId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    @Test
    @DisplayName("POST /api/v1/tenants - callbackAddress is derived from base URL and tenant id")
    public void createTenant_derivesCallbackAddressFromBaseUrl() throws Exception {
        String tenantId = "callback-test-tenant";
        TenantCreateRequest request = buildTenantCreateRequest(tenantId, "urn:connector:callback-test");

        mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(tenantId));

        // Verify callbackAddress is derived as base + "/" + tenantId
        Tenant saved = tenantRepository.findById(tenantId).orElseThrow();
        String expectedBase = baseCallbackAddress.endsWith("/")
                ? baseCallbackAddress.substring(0, baseCallbackAddress.length() - 1)
                : baseCallbackAddress;
        assertThat(saved.getCallbackAddress(baseCallbackAddress)).isEqualTo(expectedBase + "/" + tenantId);
        generatedTenantId = tenantId;
    }

    @Test
    @DisplayName("POST /api/v1/tenants with duplicate id returns 400")
    public void createTenant_duplicateId_returns400() throws Exception {
        tenantRepository.save(buildNewTenant());

        TenantCreateRequest duplicate = buildTenantCreateRequest(NEW_TENANT_ID, "urn:connector:different");

        mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(duplicate))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/tenants with duplicate participantId returns 400")
    public void createTenant_duplicateParticipantId_returns400() throws Exception {
        // Pre-save a tenant with a known participantId
        tenantRepository.save(buildNewTenant());

        // Attempt to create a second tenant with the same participantId
        TenantCreateRequest duplicate = buildTenantCreateRequest("different-id", "urn:connector:test-it");

        mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(duplicate))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/tenants with bucketName only returns 200 and keeps supplied bucket name")
    public void createTenant_withBucketNameOnly_returns200() throws Exception {
        String tenantId = "bucket-only-tenant";
        String bucketName = "tb2-existing-bucket-name-only";
        TenantCreateRequest request = TenantCreateRequest.Builder.newInstance()
                .id(tenantId)
                .name("Bucket Only Tenant")
                .participantId("urn:connector:tb2-existing-bucket")
                .enabled(true)
                .bucketName(bucketName)
                .build();

        mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bucketName").value(bucketName));

        BucketCredentialsEntity credentials = bucketCredentialsService.getBucketCredentials(bucketName);
        assertNotNull(credentials);
        createdBuckets.add(bucketName);
        generatedTenantId = tenantId;
    }

    @Test
    @DisplayName("POST /api/v1/tenants with external credentials and verifyConnection false returns 200")
    public void createTenant_externalCredentialsVerifyFalse_returns200() throws Exception {
        String tenantId = "external-no-verify-tenant";
        String bucketName = "tb2-external-no-verify";
        String accessKey = "provided-access-key";
        String secretKey = "provided-secret-key";
        TenantCreateRequest request = TenantCreateRequest.Builder.newInstance()
                .id(tenantId)
                .name("External Credentials Tenant")
                .participantId("urn:connector:tb2-external-no-verify")
                .enabled(true)
                .bucketName(bucketName)
                .accessKey(accessKey)
                .secretKey(secretKey)
                .verifyConnection(false)
                .build();

        mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bucketName").value(bucketName));

        BucketCredentialsEntity stored = bucketCredentialsService.getBucketCredentials(bucketName);
        assertThat(stored.getAccessKey()).isEqualTo(accessKey);
        assertThat(stored.getSecretKey()).isEqualTo(secretKey);
        createdBuckets.add(bucketName);
        generatedTenantId = tenantId;
    }

    @Test
    @DisplayName("POST /api/v1/tenants with external credentials and verifyConnection true valid returns 200")
    public void createTenant_externalCredentialsVerifyTrueValid_returns200() throws Exception {
        String tenantId = "external-verify-valid-tenant";
        String bucketName = "tb2-external-verify-valid";
        s3BucketProvisionService.ensureBucketCredentials(bucketName);
        createdBuckets.add(bucketName);
        BucketCredentialsEntity candidate = bucketCredentialsService.getBucketCredentials(bucketName);
        bucketCredentialsRepository.deleteById(bucketName);

        TenantCreateRequest request = TenantCreateRequest.Builder.newInstance()
                .id(tenantId)
                .name("External Verified Tenant")
                .participantId("urn:connector:tb2-external-verify-valid")
                .enabled(true)
                .bucketName(bucketName)
                .accessKey(candidate.getAccessKey())
                .secretKey(candidate.getSecretKey())
                .verifyConnection(true)
                .build();

        mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bucketName").value(bucketName));

        generatedTenantId = tenantId;
    }

    @Test
    @DisplayName("POST /api/v1/tenants with external credentials and verifyConnection true invalid returns 400 and does not persist tenant")
    public void createTenant_externalCredentialsVerifyTrueInvalid_returns400(CapturedOutput output) throws Exception {
        String tenantId = "external-verify-invalid-tenant";
        String bucketName = "tb2-external-verify-invalid";
        String secretKey = "invalid-secret-key-should-not-leak";
        TenantCreateRequest request = TenantCreateRequest.Builder.newInstance()
                .id(tenantId)
                .name("External Invalid Tenant")
                .participantId("urn:connector:tb2-external-verify-invalid")
                .enabled(true)
                .bucketName(bucketName)
                .accessKey("invalid-access-key")
                .secretKey(secretKey)
                .verifyConnection(true)
                .build();

        MvcResult result = mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain(secretKey);
        assertThat(output.getAll()).doesNotContain(secretKey);
        assertThat(tenantRepository.findById(tenantId)).isEmpty();
        assertThat(bucketCredentialsRepository.findById(bucketName)).isEmpty();
    }

    @Test
    @DisplayName("POST /api/v1/tenants with external credentials invalid bucket name returns 400 and does not persist")
    public void createTenant_externalCredentialsInvalidBucketName_returns400() throws Exception {
        String tenantId = "external-invalid-bucket-format-tenant";
        String bucketName = "Invalid_Bucket_Name";
        TenantCreateRequest request = TenantCreateRequest.Builder.newInstance()
                .id(tenantId)
                .name("External Invalid Bucket Format Tenant")
                .participantId("urn:connector:tb2-external-invalid-bucket-format")
                .enabled(true)
                .bucketName(bucketName)
                .accessKey("provided-access-key")
                .secretKey("provided-secret-key")
                .verifyConnection(false)
                .build();

        mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        assertThat(tenantRepository.findById(tenantId)).isEmpty();
        assertThat(bucketCredentialsRepository.findById(bucketName)).isEmpty();
    }

    @Test
    @DisplayName("POST /api/v1/tenants with bucketName conflict returns 400 for existing and external modes")
    public void createTenant_bucketNameConflict_returns400_forBothModes() throws Exception {
        String conflictingBucket = "tb2-conflict-bucket";
        Tenant existing = Tenant.Builder.newInstance()
                .id("existing-owner-tenant")
                .name("Existing Owner")
                .participantId("urn:connector:existing-owner")
                .enabled(true)
                .bucketName(conflictingBucket)
                .build();
        tenantRepository.save(existing);

        TenantCreateRequest existingBucketRequest = TenantCreateRequest.Builder.newInstance()
                .id("conflict-existing-mode")
                .name("Conflict Existing Mode")
                .participantId("urn:connector:conflict-existing-mode")
                .enabled(true)
                .bucketName(conflictingBucket)
                .build();

        mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(existingBucketRequest))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        TenantCreateRequest externalCredentialsRequest = TenantCreateRequest.Builder.newInstance()
                .id("conflict-external-mode")
                .name("Conflict External Mode")
                .participantId("urn:connector:conflict-external-mode")
                .enabled(true)
                .bucketName(conflictingBucket)
                .accessKey("provided-access")
                .secretKey("provided-secret")
                .verifyConnection(false)
                .build();

        mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(externalCredentialsRequest))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        tenantRepository.deleteById(existing.getId());
        assertThat(tenantRepository.findById("conflict-existing-mode")).isEmpty();
        assertThat(tenantRepository.findById("conflict-external-mode")).isEmpty();
    }

    @Test
    public void deleteTenant_asSuperAdmin_returns200() throws Exception {
        Tenant tenantToDelete = Tenant.Builder.newInstance()
                .id(NEW_TENANT_ID)
                .name("Tenant To Delete")
                .participantId("urn:connector:delete-me")
                .enabled(true)
                .build();
        tenantRepository.save(tenantToDelete);

        mockMvc.perform(delete(ApiEndpoints.TENANTS_V1 + "/" + NEW_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id} updates mutable fields and returns 200")
    public void updateTenant_asSuperAdmin_returns200() throws Exception {
        tenantRepository.save(buildNewTenant());

        TenantUpdateRequest request = TenantUpdateRequest.Builder.newInstance()
                .name("Updated Name")
                .description("Updated description")
                .automaticNegotiation(true)
                .automaticTransfer(false)
                .build();

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + NEW_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.description").value("Updated description"))
                .andExpect(jsonPath("$.data.automaticNegotiation").value(true));
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id} with no bucket fields keeps existing bucket and credentials")
    public void updateTenant_withoutBucketFields_preservesBucketAndCredentials() throws Exception {
        String tenantId = "tb3-ordinary-update";
        String originalBucket = "tb3-ordinary-update-bucket";
        s3BucketProvisionService.ensureBucketCredentials(originalBucket);
        createdBuckets.add(originalBucket);
        BucketCredentialsEntity originalCredentials = bucketCredentialsService.getBucketCredentials(originalBucket);
        Tenant existing = Tenant.Builder.newInstance()
                .id(tenantId)
                .name("Ordinary Update")
                .participantId("urn:connector:tb3-ordinary-update")
                .enabled(true)
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(originalBucket)
                .build();
        tenantRepository.save(existing);
        createdTenantIds.add(tenantId);

        TenantUpdateRequest request = buildTenantUpdateRequest("Ordinary Update Renamed", "Updated");
        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + tenantId)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bucketName").value(originalBucket));

        BucketCredentialsEntity afterUpdate = bucketCredentialsService.getBucketCredentials(originalBucket);
        assertThat(afterUpdate.getAccessKey()).isEqualTo(originalCredentials.getAccessKey());
        assertThat(afterUpdate.getSecretKey()).isEqualTo(originalCredentials.getSecretKey());
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id} with bucketName only migrates to available bucket")
    public void updateTenant_bucketNameOnly_migrates() throws Exception {
        String tenantId = "tb3-migrate-tenant";
        String oldBucket = "tb3-migrate-old";
        String newBucket = "tb3-migrate-new";
        s3BucketProvisionService.ensureBucketCredentials(oldBucket);
        s3BucketProvisionService.ensureBucketCredentials(newBucket);
        createdBuckets.add(oldBucket);
        createdBuckets.add(newBucket);
        tenantRepository.save(Tenant.Builder.newInstance()
                .id(tenantId)
                .name("Migrate Tenant")
                .participantId("urn:connector:tb3-migrate-tenant")
                .enabled(true)
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(oldBucket)
                .build());
        createdTenantIds.add(tenantId);

        TenantUpdateRequest request = TenantUpdateRequest.Builder.newInstance()
                .name("Migrate Tenant")
                .description("Migrated")
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(newBucket)
                .build();

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + tenantId)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bucketName").value(newBucket));
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id} with bucket owned by another tenant returns 400 and preserves original bucket")
    public void updateTenant_bucketConflict_returns400() throws Exception {
        String ownerTenantId = "tb3-owner-tenant";
        String targetTenantId = "tb3-target-tenant";
        String ownerBucket = "tb3-owner-bucket";
        String targetBucket = "tb3-target-bucket";
        s3BucketProvisionService.ensureBucketCredentials(ownerBucket);
        s3BucketProvisionService.ensureBucketCredentials(targetBucket);
        createdBuckets.add(ownerBucket);
        createdBuckets.add(targetBucket);
        tenantRepository.save(Tenant.Builder.newInstance()
                .id(ownerTenantId)
                .name("Owner")
                .participantId("urn:connector:tb3-owner")
                .enabled(true)
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(ownerBucket)
                .build());
        tenantRepository.save(Tenant.Builder.newInstance()
                .id(targetTenantId)
                .name("Target")
                .participantId("urn:connector:tb3-target")
                .enabled(true)
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(targetBucket)
                .build());
        createdTenantIds.add(ownerTenantId);
        createdTenantIds.add(targetTenantId);

        TenantUpdateRequest request = TenantUpdateRequest.Builder.newInstance()
                .name("Target")
                .description("Conflict")
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(ownerBucket)
                .build();

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + targetTenantId)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isBadRequest());

        Tenant reloaded = tenantRepository.findById(targetTenantId).orElseThrow();
        assertThat(reloaded.getBucketName()).isEqualTo(targetBucket);
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id} with external credentials verifyConnection false rotates credentials")
    public void updateTenant_externalCredentialsVerifyFalse_rotatesCredentials() throws Exception {
        String tenantId = "tb3-rotate-no-verify";
        String bucket = "tb3-rotate-no-verify-bucket";
        s3BucketProvisionService.ensureBucketCredentials(bucket);
        createdBuckets.add(bucket);
        tenantRepository.save(Tenant.Builder.newInstance()
                .id(tenantId)
                .name("Rotate No Verify")
                .participantId("urn:connector:tb3-rotate-no-verify")
                .enabled(true)
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(bucket)
                .build());
        createdTenantIds.add(tenantId);

        TenantUpdateRequest request = TenantUpdateRequest.Builder.newInstance()
                .name("Rotate No Verify")
                .description("Rotate")
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(bucket)
                .accessKey("rotated-access")
                .secretKey("rotated-secret")
                .verifyConnection(false)
                .build();

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + tenantId)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bucketName").value(bucket));

        BucketCredentialsEntity rotated = bucketCredentialsService.getBucketCredentials(bucket);
        assertThat(rotated.getAccessKey()).isEqualTo("rotated-access");
        assertThat(rotated.getSecretKey()).isEqualTo("rotated-secret");

        List<AuditEvent> events = auditEventRepository.findAll().stream()
                .filter(event -> event.getEventType() == AuditEventType.TENANT_UPDATED)
                .toList();
        assertThat(events).isNotEmpty();
        AuditEvent latest = events.get(events.size() - 1);
        assertThat(latest.getDetails()).containsEntry("changeType", "CREDENTIALS_ROTATED");
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id} with external credentials verifyConnection true valid succeeds")
    public void updateTenant_externalCredentialsVerifyTrue_valid_returns200() throws Exception {
        String tenantId = "tb3-verify-valid";
        String bucket = "tb3-verify-valid-bucket";
        s3BucketProvisionService.ensureBucketCredentials(bucket);
        createdBuckets.add(bucket);
        BucketCredentialsEntity candidate = bucketCredentialsService.getBucketCredentials(bucket);
        tenantRepository.save(Tenant.Builder.newInstance()
                .id(tenantId)
                .name("Verify Valid")
                .participantId("urn:connector:tb3-verify-valid")
                .enabled(true)
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(bucket)
                .build());
        createdTenantIds.add(tenantId);

        TenantUpdateRequest request = TenantUpdateRequest.Builder.newInstance()
                .name("Verify Valid")
                .description("Rotate")
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(bucket)
                .accessKey(candidate.getAccessKey())
                .secretKey(candidate.getSecretKey())
                .verifyConnection(true)
                .build();

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + tenantId)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bucketName").value(bucket));
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id} with external credentials verifyConnection true invalid returns 400 and does not leak secret")
    public void updateTenant_externalCredentialsVerifyTrue_invalid_returns400(CapturedOutput output) throws Exception {
        String tenantId = "tb3-verify-invalid";
        String bucket = "tb3-verify-invalid-bucket";
        s3BucketProvisionService.ensureBucketCredentials(bucket);
        createdBuckets.add(bucket);
        BucketCredentialsEntity before = bucketCredentialsService.getBucketCredentials(bucket);
        tenantRepository.save(Tenant.Builder.newInstance()
                .id(tenantId)
                .name("Verify Invalid")
                .participantId("urn:connector:tb3-verify-invalid")
                .enabled(true)
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(bucket)
                .build());
        createdTenantIds.add(tenantId);
        String secretKey = "invalid-secret-key-should-not-leak";

        TenantUpdateRequest request = TenantUpdateRequest.Builder.newInstance()
                .name("Verify Invalid")
                .description("Rotate")
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(bucket)
                .accessKey("invalid-access")
                .secretKey(secretKey)
                .verifyConnection(true)
                .build();

        MvcResult result = mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + tenantId)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(secretKey);
        assertThat(output.getAll()).doesNotContain(secretKey);
        List<AuditEvent> events = auditEventRepository.findAll().stream()
                .filter(event -> event.getEventType() == AuditEventType.TENANT_UPDATED)
                .toList();
        if (!events.isEmpty()) {
            String detailsString = Objects.toString(events.get(events.size() - 1).getDetails(), "");
            assertThat(detailsString).doesNotContain(secretKey);
        }

        BucketCredentialsEntity after = bucketCredentialsService.getBucketCredentials(bucket);
        assertThat(after.getAccessKey()).isEqualTo(before.getAccessKey());
        assertThat(after.getSecretKey()).isEqualTo(before.getSecretKey());
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id} rotates credentials twice for same bucket")
    public void updateTenant_externalCredentials_doubleRotation_succeeds() throws Exception {
        String tenantId = "tb3-double-rotation";
        String bucket = "tb3-double-rotation-bucket";
        s3BucketProvisionService.ensureBucketCredentials(bucket);
        createdBuckets.add(bucket);
        BucketCredentialsEntity firstCandidate = bucketCredentialsService.getBucketCredentials(bucket);
        tenantRepository.save(Tenant.Builder.newInstance()
                .id(tenantId)
                .name("Double Rotation")
                .participantId("urn:connector:tb3-double-rotation")
                .enabled(true)
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(bucket)
                .build());
        createdTenantIds.add(tenantId);

        TenantUpdateRequest firstRotation = TenantUpdateRequest.Builder.newInstance()
                .name("Double Rotation")
                .description("First rotation")
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(bucket)
                .accessKey(firstCandidate.getAccessKey())
                .secretKey(firstCandidate.getSecretKey())
                .verifyConnection(true)
                .build();

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + tenantId)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(firstRotation))))
                .andExpect(status().isOk());

        BucketCredentialsEntity afterFirstRotation = bucketCredentialsService.getBucketCredentials(bucket);

        TenantUpdateRequest secondRotation = TenantUpdateRequest.Builder.newInstance()
                .name("Double Rotation")
                .description("Second rotation")
                .automaticNegotiation(false)
                .automaticTransfer(false)
                .bucketName(bucket)
                .accessKey("second-rotated-access")
                .secretKey("second-rotated-secret")
                .verifyConnection(false)
                .build();

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + tenantId)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(secondRotation))))
                .andExpect(status().isOk());

        BucketCredentialsEntity afterSecondRotation = bucketCredentialsService.getBucketCredentials(bucket);
        assertThat(afterSecondRotation.getAccessKey()).isEqualTo("second-rotated-access");
        assertThat(afterSecondRotation.getSecretKey()).isEqualTo("second-rotated-secret");
        assertThat(afterSecondRotation.getVersion()).isGreaterThanOrEqualTo(afterFirstRotation.getVersion());
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id} on non-existing tenant returns 404")
    public void updateTenant_nonExisting_returns404() throws Exception {
        TenantUpdateRequest request = buildTenantUpdateRequest("Should Not Update", "Missing tenant");

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + NON_EXISTING_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(request))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id}/enable enables tenant and returns enabled=true")
    public void enableTenant_asSuperAdmin_returns200() throws Exception {
        Tenant disabled = Tenant.Builder.newInstance()
                .id(NEW_TENANT_ID)
                .name("Disabled Tenant")
                .participantId("urn:connector:test-it")
                .enabled(false)
                .build();
        tenantRepository.save(disabled);

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + NEW_TENANT_ID + "/enable")
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id}/disable disables tenant and returns enabled=false")
    public void disableTenant_asSuperAdmin_returns200() throws Exception {
        tenantRepository.save(buildNewTenant());

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + NEW_TENANT_ID + "/disable")
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id}/enable on non-existing tenant returns 404")
    public void enableTenant_nonExisting_returns404() throws Exception {
        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + NON_EXISTING_TENANT_ID + "/enable")
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id}/disable on non-existing tenant returns 404")
    public void disableTenant_nonExisting_returns404() throws Exception {
        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + NON_EXISTING_TENANT_ID + "/disable")
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("DELETE /api/v1/tenants/{id} on non-existing tenant returns 404")
    public void deleteTenant_nonExisting_returns404() throws Exception {
        mockMvc.perform(delete(ApiEndpoints.TENANTS_V1 + "/" + NON_EXISTING_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("DELETE /api/v1/tenants/{id} then GET returns 404")
    public void deleteTenant_thenGet_returns404() throws Exception {
        tenantRepository.save(buildNewTenant());

        mockMvc.perform(delete(ApiEndpoints.TENANTS_V1 + "/" + NEW_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get(ApiEndpoints.TENANTS_V1 + "/" + NEW_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /api/v1/tenants/{id} for non-existing tenant returns 404")
    public void getTenantById_nonExisting_returns404() throws Exception {
        mockMvc.perform(get(ApiEndpoints.TENANTS_V1 + "/" + NON_EXISTING_TENANT_ID)
                        .with(user("super").roles("SUPER_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/tenants with missing required fields returns 400 with GenericApiResponse")
    public void createTenant_missingRequiredFields_returns400() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"missing id, name, participantId\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<>() {};
        GenericApiResponse<String> response = ToolsSerializer.deserializePlain(
                result.getResponse().getContentAsString(), typeRef);

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getMessage());
        assertThat(response.getMessage()).isNotBlank();
        assertNull(response.getData());
    }

    @Test
    @DisplayName("POST /api/v1/tenants as ADMIN returns 403")
    public void createTenant_asAdmin_returns403() throws Exception {
        mockMvc.perform(post(ApiEndpoints.TENANTS_V1)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Objects.requireNonNull(ToolsSerializer.serializePlain(buildNewTenant()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/v1/tenants/{id} as ADMIN returns 403")
    public void deleteTenant_asAdmin_returns403() throws Exception {
        mockMvc.perform(delete(ApiEndpoints.TENANTS_V1 + "/" + ENGINEERING_TENANT_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id} with different participantId succeeds but preserves original")
    public void updateTenant_participantIdIsIgnored_returnsOriginalValue() throws Exception {
        Tenant original = buildNewTenant();
        tenantRepository.save(original);
        String originalParticipantId = original.getParticipantId();

        String body = """
                {
                  "name": "Updated Name",
                  "description": "Updated description",
                  "participantId": "urn:connector:changed",
                  "automaticNegotiation": false,
                  "automaticTransfer": false
                }
                """;

        mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + original.getId())
                        .with(user("super").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.participantId").value(originalParticipantId));
    }
}
