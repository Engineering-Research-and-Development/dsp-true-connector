package it.eng.connector.integration.datatransfer;

import com.github.tomakehurst.wiremock.WireMockServer;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferArtifactState;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.service.CancellationRegistry;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.IConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.wiremock.spring.InjectWireMock;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the suspend/resume flows in the data-transfer API.
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>A suspend call signals the cancellation registry and persists the {@code suspendedBy} role.</li>
 *   <li>A resume attempt by the wrong party is rejected with a 4xx error.</li>
 * </ul>
 */
public class DataTransferSuspendResumeIT extends BaseIntegrationTest {

    @InjectWireMock
    private WireMockServer wiremock;

    @Autowired
    private TransferProcessRepository transferProcessRepository;

    @Autowired
    private TransferArtifactStateRepository transferArtifactStateRepository;

    @Autowired
    private CancellationRegistry cancellationRegistry;

    private String savedTpId;

    /**
     * Removes all test data after each test.
     */
    @AfterEach
    public void cleanup() {
        if (savedTpId != null) {
            cancellationRegistry.deregister(savedTpId);
            savedTpId = null;
        }
        transferProcessRepository.deleteAll();
        transferArtifactStateRepository.deleteAll();
    }

    /**
     * Verifies that suspending a STARTED consumer transfer sends the suspension callback,
     * signals the cancellation registry, persists a {@link TransferArtifactState} with
     * {@code suspendedBy} set to the consumer role, and transitions the process to SUSPENDED.
     *
     * @throws Exception if MockMvc fails
     */
    @Test
    @DisplayName("Suspend transfer - signals cancellation registry and persists suspendedBy")
    @WithUserDetails(TestUtil.API_USER)
    public void suspendTransferSignalsCancellationAndPersistsSuspendedBy() throws Exception {
        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpoint(wiremock.baseUrl() + "/object")
                .endpointType("https://w3id.org/idsa/v4.1/HTTP")
                .build();

        TransferProcess tp = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .agreementId(createNewId())
                .state(TransferState.STARTED)
                .role(IConstants.ROLE_CONSUMER)
                .format(DataTransferFormat.HTTP_PULL.format())
                .datasetId("dataset-test")
                .callbackAddress(wiremock.baseUrl())
                .dataAddress(dataAddress)
                .build();
        TransferProcess saved = transferProcessRepository.save(tp);
        savedTpId = saved.getId();

        // Simulate an in-progress download so the registry has an entry
        AtomicBoolean cancellationToken = cancellationRegistry.register(saved.getId());

        // Stub the peer suspension endpoint
        wiremock.stubFor(post(urlPathMatching(".*/transfers/.*/suspension"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        mockMvc.perform(put(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + saved.getId() + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertTrue(cancellationToken.get(),
                "Cancellation token should have been signaled to true by suspendTransfer");

        Optional<TransferArtifactState> artifactStateOpt = transferArtifactStateRepository.findById(saved.getId());
        assertTrue(artifactStateOpt.isPresent(), "TransferArtifactState should have been persisted");
        assertEquals(IConstants.ROLE_CONSUMER, artifactStateOpt.get().getSuspendedBy(),
                "suspendedBy should be set to the consumer role");

        TransferProcess updated = transferProcessRepository.findById(saved.getId()).orElseThrow();
        assertEquals(TransferState.SUSPENDED, updated.getState(),
                "TransferProcess should be in SUSPENDED state");
    }

    /**
     * Verifies that a resume attempt by the wrong party is rejected with HTTP 400.
     *
     * <p>The transfer was suspended by the provider, so only the provider may resume it.
     * When the consumer attempts to resume, a 4xx response is expected.
     *
     * @throws Exception if MockMvc fails
     */
    @Test
    @DisplayName("Resume transfer - rejected when wrong party attempts resume")
    @WithUserDetails(TestUtil.API_USER)
    public void resumeByWrongPartyIsRejected() throws Exception {
        TransferProcess tp = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .agreementId(createNewId())
                .state(TransferState.SUSPENDED)
                .role(IConstants.ROLE_CONSUMER)
                .format(DataTransferFormat.HTTP_PULL.format())
                .datasetId("dataset-test")
                .callbackAddress(wiremock.baseUrl())
                .build();
        TransferProcess saved = transferProcessRepository.save(tp);

        // Provider suspended it — consumer must not be able to resume
        TransferArtifactState artifactState = TransferArtifactState.Builder.newInstance()
                .id(saved.getId())
                .suspendedBy(IConstants.ROLE_PROVIDER)
                .build();
        transferArtifactStateRepository.save(artifactState);

        mockMvc.perform(put(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + saved.getId() + "/start")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
