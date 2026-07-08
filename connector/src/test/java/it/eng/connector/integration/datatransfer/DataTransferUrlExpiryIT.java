package it.eng.connector.integration.datatransfer;

import com.github.tomakehurst.wiremock.WireMockServer;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.model.Permission;
import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import it.eng.tools.model.IConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.wiremock.spring.InjectWireMock;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for the URL-expiry termination flow in the data-transfer module.
 *
 * <p>When a presigned download URL returns HTTP 403, the connector must automatically
 * send a {@code TransferTerminationMessage} to the peer and transition the local
 * {@link TransferProcess} to {@link TransferState#TERMINATED}.
 */
public class DataTransferUrlExpiryIT extends BaseIntegrationTest {

    @InjectWireMock
    private WireMockServer wiremock;

    @Autowired
    private DataTransferAPIService apiService;

    @Autowired
    private TransferProcessRepository transferProcessRepository;

    @Autowired
    private TransferArtifactStateRepository transferArtifactStateRepository;

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private ContractNegotiationRepository contractNegotiationRepository;

    @Autowired
    private PolicyEnforcementRepository policyEnforcementRepository;

    /**
     * Removes all test data after each test.
     */
    @AfterEach
    public void cleanup() {
        transferProcessRepository.deleteAll();
        transferArtifactStateRepository.deleteAll();
        agreementRepository.deleteAll();
        contractNegotiationRepository.deleteAll();
        policyEnforcementRepository.deleteAll();
    }

    /**
     * Verifies that when a presigned URL returns HTTP 403 (expired), the connector
     * sends a termination message to the peer and transitions the transfer process
     * to {@link TransferState#TERMINATED}.
     *
     * @throws Exception if the test setup or assertion fails
     */
    @Test
    @DisplayName("Download with expired URL terminates the transfer")
    @WithUserDetails(TestUtil.API_USER)
    public void downloadWithExpiredUrlTerminatesTransfer() throws Exception {
        Agreement agreement = Agreement.Builder.newInstance()
                .id(createNewId())
                .assignee("assignee")
                .assigner("assigner")
                .target("test-dataset")
                .permission(Collections.singletonList(Permission.Builder.newInstance()
                        .action(Action.USE)
                        .constraint(Collections.singletonList(Constraint.Builder.newInstance()
                                .leftOperand(LeftOperand.COUNT)
                                .operator(Operator.LTEQ)
                                .rightOperand("5")
                                .build()))
                        .build()))
                .build();
        agreementRepository.save(agreement);

        PolicyEnforcement policyEnforcement = new PolicyEnforcement(createNewId(), agreement.getId(), 0);
        policyEnforcementRepository.save(policyEnforcement);

        String consumerPid = createNewId();
        String providerPid = createNewId();
        ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                .id(createNewId())
                .agreement(agreement)
                .consumerPid(consumerPid)
                .providerPid(providerPid)
                .state(ContractNegotiationState.FINALIZED)
                .build();
        contractNegotiationRepository.save(contractNegotiation);

        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpoint(wiremock.baseUrl() + "/expired-object")
                .endpointType("https://w3id.org/idsa/v4.1/HTTP")
                .build();

        TransferProcess tp = TransferProcess.Builder.newInstance()
                .consumerPid(consumerPid)
                .providerPid(providerPid)
                .agreementId(agreement.getId())
                .state(TransferState.STARTED)
                .role(IConstants.ROLE_CONSUMER)
                .format(DataTransferFormat.HTTP_PULL.format())
                .datasetId("test-dataset")
                .callbackAddress(wiremock.baseUrl())
                .dataAddress(dataAddress)
                .build();
        TransferProcess saved = transferProcessRepository.save(tp);

        // Simulate an expired presigned URL — HTTP 403 triggers PresignedUrlExpiredException
        wiremock.stubFor(get(urlEqualTo("/expired-object"))
                .willReturn(aResponse().withStatus(403)));

        // Stub the termination callback sent after URL expiry
        wiremock.stubFor(post(urlPathMatching(".*/transfers/.*/termination"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        apiService.downloadData(saved.getId());

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            TransferProcess polled = transferProcessRepository.findById(saved.getId()).orElseThrow();
            assertEquals(TransferState.TERMINATED, polled.getState(),
                    "Transfer process should be TERMINATED after URL expiry");
        });

        wiremock.verify(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                urlPathMatching(".*/transfers/.*/termination")));
    }
}
