package it.eng.connector.integration.multitenant;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.IConstants;
import it.eng.tools.repository.AuditEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant isolation of the audit event API ({@code /api/v1/audit}), the dashboard ({@code /api/v1/dashboard}) and
 * the super-admin-only APIs.
 *
 * <p>A tenant admin sees only its own audit events and metrics: data added to the other tenant never changes its
 * numbers, the per-tenant breakdown is not shown to it, and a {@code tenantId} query parameter cannot select
 * another tenant. The tenant, user and property APIs are closed to tenant admins.
 */
public class AuditAndDashboardTenantIsolationIT extends AbstractTenantIsolationIT {

    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private ContractNegotiationRepository contractNegotiationRepository;
    @Autowired
    private TransferProcessRepository transferProcessRepository;

    private AuditEvent eventA;
    private AuditEvent eventB;
    private final List<String> seededNegotiationIds = new ArrayList<>();
    private final List<String> seededTransferIds = new ArrayList<>();
    private final List<String> seededEventIds = new ArrayList<>();

    @BeforeEach
    void seedEvents() {
        seededNegotiationIds.clear();
        seededTransferIds.clear();
        seededEventIds.clear();
        eventA = saveEvent(TENANT_A, "Isolation event of tenant A");
        eventB = saveEvent(TENANT_B, "Isolation event of tenant B");
    }

    @AfterEach
    void cleanup() {
        seededEventIds.forEach(auditEventRepository::deleteById);
        seededNegotiationIds.forEach(contractNegotiationRepository::deleteById);
        seededTransferIds.forEach(transferProcessRepository::deleteById);
    }

    // ---------------------------------------------------------------- audit events

    @Test
    @DisplayName("GET /audit lists only the tenant's own audit events")
    void auditList_returnsOnlyOwn() throws Exception {
        MvcResult resultA = mockMvc.perform(get(ApiEndpoints.AUDIT_V1).param("size", "1000").with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        Set<String> idsA = ids(pagedContent(resultA));
        assertTrue(idsA.contains(eventA.getId()));
        assertBodyDoesNotContain(resultA, eventB.getId(), eventB.getDescription());

        MvcResult resultB = mockMvc.perform(get(ApiEndpoints.AUDIT_V1).param("size", "1000").with(adminOf(TENANT_B)))
                .andExpect(status().isOk()).andReturn();
        assertTrue(ids(pagedContent(resultB)).contains(eventB.getId()));
        assertBodyDoesNotContain(resultB, eventA.getId(), eventA.getDescription());
    }

    @ParameterizedTest(name = "GET {0}?tenantId=<other tenant> returns only own data")
    @ValueSource(strings = {ApiEndpoints.AUDIT_V1, ApiEndpoints.NEGOTIATION_V1, ApiEndpoints.TRANSFER_DATATRANSFER_V1})
    @DisplayName("A tenantId query parameter cannot select the other tenant's data")
    void tenantIdQueryParameter_isIgnored(String path) throws Exception {
        ContractNegotiation negotiationB = saveNegotiation(TENANT_B);
        TransferProcess transferB = saveTransfer(TENANT_B);

        MvcResult result = mockMvc.perform(get(path).param("tenantId", TENANT_B).param("size", "1000")
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();

        assertBodyDoesNotContain(result, eventB.getId(), negotiationB.getId(), transferB.getId());
    }

    @Test
    @DisplayName("GET /audit/{id}: own audit event found, other tenant's audit event 404")
    void auditById_ownFound_otherNotFound() throws Exception {
        MvcResult own = mockMvc.perform(get(ApiEndpoints.AUDIT_V1 + "/" + eventA.getId()).with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(eventA.getId(), data(own).path("id").asText());

        MvcResult other = mockMvc.perform(get(ApiEndpoints.AUDIT_V1 + "/" + eventB.getId()).with(adminOf(TENANT_A)))
                .andExpect(status().isNotFound()).andReturn();
        assertBodyDoesNotContain(other, eventB.getDescription());
    }

    // ---------------------------------------------------------------- dashboard

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {ApiEndpoints.DASHBOARD_NEGOTIATIONS_V1, ApiEndpoints.DASHBOARD_TRANSFERS_V1,
            ApiEndpoints.DASHBOARD_EVENTS_V1, ApiEndpoints.DASHBOARD_SUMMARY_V1})
    @DisplayName("Dashboard numbers of a tenant do not change when the other tenant gets data, and show no per-tenant breakdown")
    void dashboard_unaffectedByOtherTenant(String path) throws Exception {
        JsonNode before = data(mockMvc.perform(get(path).with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn());
        JsonNode otherBefore = data(mockMvc.perform(get(path).with(adminOf(TENANT_B)))
                .andExpect(status().isOk()).andReturn());

        for (int i = 0; i < 3; i++) {
            saveNegotiation(TENANT_B);
            saveTransfer(TENANT_B);
            saveEvent(TENANT_B, "Extra event of tenant B " + i);
        }

        MvcResult afterResult = mockMvc.perform(get(path).with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        JsonNode after = data(afterResult);
        JsonNode otherAfter = data(mockMvc.perform(get(path).with(adminOf(TENANT_B)))
                .andExpect(status().isOk()).andReturn());

        for (String section : sections(path)) {
            JsonNode metricsBefore = section.isEmpty() ? before : before.path(section);
            JsonNode metricsAfter = section.isEmpty() ? after : after.path(section);
            JsonNode otherMetricsBefore = section.isEmpty() ? otherBefore : otherBefore.path(section);
            JsonNode otherMetricsAfter = section.isEmpty() ? otherAfter : otherAfter.path(section);

            assertEquals(metricsBefore.path("totalCount").asLong(), metricsAfter.path("totalCount").asLong(),
                    "tenant A " + path + " " + section + " totalCount changed by tenant B data");
            assertEquals(metricsBefore.path("byState"), metricsAfter.path("byState"));
            assertEquals(metricsBefore.path("byEventType"), metricsAfter.path("byEventType"));
            assertTrue(otherMetricsAfter.path("totalCount").asLong() >= otherMetricsBefore.path("totalCount").asLong() + 3,
                    "tenant B " + path + " " + section + " must count its own new data");
            JsonNode byTenant = metricsAfter.path("byTenant");
            assertTrue(byTenant.isMissingNode() || byTenant.isNull() || byTenant.isEmpty(),
                    "per-tenant breakdown must not be shown to a tenant admin: " + byTenant);
        }
        assertBodyDoesNotContain(afterResult, TENANT_B);
    }

    // ---------------------------------------------------------------- super-admin only APIs

    @ParameterizedTest(name = "GET {0} as tenant admin is forbidden")
    @ValueSource(strings = {ApiEndpoints.TENANTS_V1, ApiEndpoints.TENANTS_V1 + "/list", ApiEndpoints.TENANTS_V1 + "/" + TENANT_B,
            ApiEndpoints.USERS_V1, ApiEndpoints.USERS_V1 + "/some-user-id", ApiEndpoints.PROPERTIES_V1 + "/"})
    @DisplayName("Tenant, user and property APIs are closed to tenant admins")
    void superAdminApis_forbiddenForTenantAdmin(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).with(adminOf(TENANT_A)))
                .andExpect(status().isForbidden()).andReturn();
        assertBodyDoesNotContain(result, TENANT_B);
    }

    @Test
    @DisplayName("A tenant admin cannot switch to the other tenant with X-Tenant-Id")
    void tenantHeader_forbiddenForTenantAdmin() throws Exception {
        mockMvc.perform(get(ApiEndpoints.AUDIT_V1).header("X-Tenant-Id", TENANT_B).with(adminOf(TENANT_A)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- seed

    private static List<String> sections(String path) {
        return ApiEndpoints.DASHBOARD_SUMMARY_V1.equals(path)
                ? List.of("negotiations", "transfers", "events")
                : List.of("");
    }

    private AuditEvent saveEvent(String tenantId, String description) {
        AuditEvent event = auditEventRepository.save(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_NEGOTIATION_CONTRACT_NEGOTIATION)
                .description(description)
                // the event metrics window is evaluated in UTC
                .timestamp(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .tenantId(tenantId)
                .build());
        seededEventIds.add(event.getId());
        return event;
    }

    private ContractNegotiation saveNegotiation(String tenantId) {
        ContractNegotiation negotiation = contractNegotiationRepository.save(ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .callbackAddress("http://localhost:1/" + tenantId)
                .role(IConstants.ROLE_PROVIDER)
                .state(ContractNegotiationState.REQUESTED)
                .tenantId(tenantId)
                .build());
        seededNegotiationIds.add(negotiation.getId());
        return negotiation;
    }

    private TransferProcess saveTransfer(String tenantId) {
        TransferProcess transfer = transferProcessRepository.save(TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .agreementId(createNewId())
                .callbackAddress("http://localhost:1/" + tenantId)
                .format(DataTransferFormat.HTTP_PULL.format())
                .role(IConstants.ROLE_PROVIDER)
                .state(TransferState.REQUESTED)
                .tenantId(tenantId)
                .build());
        seededTransferIds.add(transfer.getId());
        return transfer;
    }
}
