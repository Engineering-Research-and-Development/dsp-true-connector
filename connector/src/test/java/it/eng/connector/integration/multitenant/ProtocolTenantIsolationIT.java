package it.eng.connector.integration.multitenant;

import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.model.TransferTerminationMessage;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.auth.ConnectorCredentialProvider;
import it.eng.tools.model.IConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant isolation of the DSP protocol routes for negotiations and transfers ({@code /{tenantId}/negotiations},
 * {@code /{tenantId}/consumer/negotiations}, {@code /{tenantId}/transfers}, {@code /{tenantId}/consumer/transfers}).
 *
 * <p>The tenant comes from the route. A connector addressing tenant A's route with the pids of tenant B's
 * negotiation or transfer gets "not found" and cannot change it; through tenant B's route it gets them.
 */
public class ProtocolTenantIsolationIT extends AbstractTenantIsolationIT {

    @Autowired
    private ContractNegotiationRepository contractNegotiationRepository;
    @Autowired
    private TransferProcessRepository transferProcessRepository;
    @Autowired
    private ConnectorCredentialProvider connectorCredentialProvider;

    private String connectorToken;
    private ContractNegotiation providerNegotiationB;
    private ContractNegotiation consumerNegotiationB;
    private TransferProcess providerTransferB;
    private TransferProcess consumerTransferB;

    @BeforeEach
    void seed() {
        connectorToken = connectorCredentialProvider.issueConnectorToken();
        providerNegotiationB = saveNegotiation(IConstants.ROLE_PROVIDER);
        consumerNegotiationB = saveNegotiation(IConstants.ROLE_CONSUMER);
        providerTransferB = saveTransfer(IConstants.ROLE_PROVIDER);
        consumerTransferB = saveTransfer(IConstants.ROLE_CONSUMER);
    }

    @AfterEach
    void cleanup() {
        List.of(providerNegotiationB, consumerNegotiationB).forEach(n -> contractNegotiationRepository.deleteById(n.getId()));
        List.of(providerTransferB, consumerTransferB).forEach(t -> transferProcessRepository.deleteById(t.getId()));
    }

    @Test
    @DisplayName("GET negotiation by pid: found through the owner's route, not found through another tenant's route")
    void getNegotiation_onlyThroughOwnersRoute() throws Exception {
        String providerPath = "/negotiations/" + providerNegotiationB.getProviderPid();
        String consumerPath = "/consumer/negotiations/" + consumerNegotiationB.getConsumerPid();

        protocolGet("/" + TENANT_B + providerPath).andExpectOk();
        protocolGet("/" + TENANT_B + consumerPath).andExpectOk();

        assertNotFound(protocolGet("/" + TENANT_A + providerPath).result());
        assertNotFound(protocolGet("/" + TENANT_A + consumerPath).result());
    }

    @Test
    @DisplayName("GET transfer by pid: found through the owner's route, not found through another tenant's route")
    void getTransfer_onlyThroughOwnersRoute() throws Exception {
        String providerPath = "/transfers/" + providerTransferB.getProviderPid();
        String consumerPath = "/consumer/transfers/" + consumerTransferB.getConsumerPid();

        protocolGet("/" + TENANT_B + providerPath).andExpectOk();
        protocolGet("/" + TENANT_B + consumerPath).andExpectOk();

        assertNotFound(protocolGet("/" + TENANT_A + providerPath).result());
        assertNotFound(protocolGet("/" + TENANT_A + consumerPath).result());
    }

    @Test
    @DisplayName("Negotiation termination through another tenant's route is not found and does not terminate it")
    void terminateNegotiation_throughOtherTenantsRoute_rejected() throws Exception {
        for (ContractNegotiation target : List.of(providerNegotiationB, consumerNegotiationB)) {
            String path = IConstants.ROLE_PROVIDER.equals(target.getRole())
                    ? "/" + TENANT_A + "/negotiations/" + target.getProviderPid() + "/termination"
                    : "/" + TENANT_A + "/consumer/negotiations/" + target.getConsumerPid() + "/termination";
            String message = NegotiationSerializer.serializeProtocol(ContractNegotiationTerminationMessage.Builder.newInstance()
                    .consumerPid(target.getConsumerPid())
                    .providerPid(target.getProviderPid())
                    .code("isolation-test")
                    .build());

            assertNotFound(protocolPost(path, message));

            ContractNegotiation stored = contractNegotiationRepository.findById(target.getId()).orElseThrow();
            assertEquals(ContractNegotiationState.REQUESTED, stored.getState());
        }
    }

    @Test
    @DisplayName("Transfer termination through another tenant's route is not found and does not terminate it")
    void terminateTransfer_throughOtherTenantsRoute_rejected() throws Exception {
        for (TransferProcess target : List.of(providerTransferB, consumerTransferB)) {
            String path = IConstants.ROLE_PROVIDER.equals(target.getRole())
                    ? "/" + TENANT_A + "/transfers/" + target.getProviderPid() + "/termination"
                    : "/" + TENANT_A + "/consumer/transfers/" + target.getConsumerPid() + "/termination";
            String message = TransferSerializer.serializeProtocol(TransferTerminationMessage.Builder.newInstance()
                    .consumerPid(target.getConsumerPid())
                    .providerPid(target.getProviderPid())
                    .code("isolation-test")
                    .build());

            assertNotFound(protocolPost(path, message));

            TransferProcess stored = transferProcessRepository.findById(target.getId()).orElseThrow();
            assertEquals(TransferState.STARTED, stored.getState());
        }
    }

    // ---------------------------------------------------------------- helpers

    private record Response(MvcResult result) {
        void andExpectOk() {
            assertEquals(200, result.getResponse().getStatus(), () -> body(result));
        }
    }

    private Response protocolGet(String path) throws Exception {
        return new Response(mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + connectorToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn());
    }

    private MvcResult protocolPost(String path, String body) throws Exception {
        return mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + connectorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private static void assertNotFound(MvcResult result) {
        int status = result.getResponse().getStatus();
        assertTrue(status == 404 || status == 400, "expected not found (404/400) but was " + status + ": " + body(result));
        String reason = body(result).toLowerCase();
        assertTrue(reason.contains("not found") || reason.contains("no transfer process found"),
                "expected a not-found reason: " + body(result));
    }

    private static String body(MvcResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (Exception e) {
            return "<unreadable>";
        }
    }

    private ContractNegotiation saveNegotiation(String role) {
        return contractNegotiationRepository.save(ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .callbackAddress("http://localhost:1/tenant-b")
                .role(role)
                .state(ContractNegotiationState.REQUESTED)
                .tenantId(TENANT_B)
                .build());
    }

    private TransferProcess saveTransfer(String role) {
        return transferProcessRepository.save(TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .agreementId(createNewId())
                .callbackAddress("http://localhost:1/tenant-b")
                .format(DataTransferFormat.HTTP_PULL.format())
                .role(role)
                .state(TransferState.STARTED)
                .tenantId(TENANT_B)
                .build());
    }
}
