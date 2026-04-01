package it.eng.negotiation.service;

import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.service.AuditEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AutomaticNegotiationServiceTest {

    @Mock
    private ContractNegotiationAPIService apiService;
    @Mock
    private ContractNegotiationProperties properties;
    @Mock
    private ContractNegotiationRepository repository;
    @Mock
    private AuditEventPublisher publisher;
    @Mock
    private TaskScheduler taskScheduler;

    @InjectMocks
    private AutomaticNegotiationService service;

    @Captor
    private ArgumentCaptor<ContractNegotiation> cnCaptor;

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * Captures the Runnable most recently scheduled on taskScheduler and runs it
     * immediately, simulating the retry without any real delay.
     * Resets the scheduler mock before capturing so each call is independent of
     * previous invocations and the ArgumentCaptor always holds the latest value.
     */
    private void runScheduledRetry() {
        // Capture the Runnable from the single schedule() call that just happened,
        // then clear so the next runScheduledRetry() starts from a clean slate.
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(captor.capture(), any(Instant.class));
        Runnable retry = captor.getValue();
        clearInvocations(taskScheduler);
        retry.run();
    }

    // ── processAgreed ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("processAgreed - succeeds on first attempt, no save or termination")
    public void processAgreed_success() {
        when(repository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(properties.getMaxRetries()).thenReturn(3);

        service.processAgreed(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());

        verify(apiService).sendContractAgreementMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());
        verify(repository, never()).save(any(ContractNegotiation.class));
        verify(apiService, never()).sendContractNegotiationTerminationMessage(any());
        verifyNoInteractions(taskScheduler);
    }

    @Test
    @DisplayName("processAgreed - fails once then succeeds on scheduled retry, retryCount saved once")
    public void processAgreed_retryOnce_thenSuccess() {
        when(repository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(properties.getMaxRetries()).thenReturn(3);
        when(properties.getRetryDelayMs()).thenReturn(0L);
        // First call throws; second call (via scheduled retry) returns normally.
        doThrow(new RuntimeException("timeout"))
                .doReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED)
                .when(apiService).sendContractAgreementMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());
        when(repository.save(any())).thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED);

        service.processAgreed(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());

        // retryCount=1 persisted, retry scheduled — capture and run it synchronously
        verify(repository).save(cnCaptor.capture());
        assertEquals(1, cnCaptor.getValue().getRetryCount());
        runScheduledRetry();

        verify(apiService, times(2)).sendContractAgreementMessage(any());
        verify(apiService, never()).sendContractNegotiationTerminationMessage(any());
    }

    @Test
    @DisplayName("processAgreed - exhausts all retries, termination succeeds")
    public void processAgreed_exhaustsRetries_terminationSucceeds() {
        // maxRetryAttempts=2 → attempts at retryCount 0, 1, 2; third failure (nextAttempt=3 > 2) terminates.
        // AtomicReference tracks the latest saved CN so findById returns the correct
        // retryCount on each retry — otherwise the counter resets to 0 every time.
        var saved = new AtomicReference<>(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED);
        when(repository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId()))
                .thenAnswer(inv -> Optional.of(saved.get()));
        when(properties.getMaxRetries()).thenReturn(2);
        when(properties.getRetryDelayMs()).thenReturn(0L);
        doThrow(new RuntimeException("timeout"))
                .when(apiService).sendContractAgreementMessage(any());
        when(repository.save(any())).thenAnswer(inv -> {
            ContractNegotiation cn = inv.getArgument(0);
            saved.set(cn);
            return cn;
        });

        // Attempt 0 fails → retryCount=1, retry scheduled
        service.processAgreed(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());
        // Attempt 1 fails → retryCount=2, retry scheduled
        runScheduledRetry();
        // Attempt 2 fails → retryCount=3 > maxRetries=2 → terminateGracefully
        runScheduledRetry();

        verify(repository, times(3)).save(any(ContractNegotiation.class));
        verify(apiService).sendContractNegotiationTerminationMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());
        verify(publisher, never()).publishEvent(any(AuditEvent.class));
        verifyNoInteractions(taskScheduler); // no further retry scheduled after exhaustion
    }

    @Test
    @DisplayName("processAgreed - exhausts retries and termination also fails, force-terminates locally")
    public void processAgreed_exhaustsRetries_terminationAlsoFails() {
        // maxRetryAttempts=0 → first failure (nextAttempt=1 > 0) immediately terminates.
        when(repository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(properties.getMaxRetries()).thenReturn(0);
        doThrow(new RuntimeException("timeout"))
                .when(apiService).sendContractAgreementMessage(any());
        doThrow(new RuntimeException("peer unreachable"))
                .when(apiService).sendContractNegotiationTerminationMessage(any());
        when(repository.save(any())).thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED);

        service.processAgreed(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());

        // save(retryCount=1) + save(TERMINATED)
        verify(repository, times(2)).save(cnCaptor.capture());
        assertEquals(ContractNegotiationState.TERMINATED, cnCaptor.getValue().getState());
        verify(publisher).publishEvent(any(AuditEvent.class));
        verifyNoInteractions(taskScheduler);
    }

    @Test
    @DisplayName("processAgreed - CN not found throws ContractNegotiationNotFoundException")
    public void processAgreed_cnNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> service.processAgreed("unknown-id"));

        verify(apiService, never()).sendContractAgreementMessage(any());
        verifyNoInteractions(taskScheduler);
    }

    @Test
    @DisplayName("processAgreed - resumes from persisted retryCount equal to maxRetries")
    public void resumeFromPersistedRetryCount() {
        // CN already has retryCount=2 == maxRetries=2 → first failure exhausts budget immediately.
        ContractNegotiation cnWithRetries = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .state(ContractNegotiationState.REQUESTED)
                .retryCount(2)
                .build();
        when(repository.findById(cnWithRetries.getId())).thenReturn(Optional.of(cnWithRetries));
        when(properties.getMaxRetries()).thenReturn(2);
        doThrow(new RuntimeException("timeout"))
                .when(apiService).sendContractAgreementMessage(any());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processAgreed(cnWithRetries.getId());

        // Only one send attempt before exhaustion (retryCount already at max)
        verify(apiService, times(1)).sendContractAgreementMessage(any());
        verify(apiService).sendContractNegotiationTerminationMessage(cnWithRetries.getId());
        verifyNoInteractions(taskScheduler);
    }

    // ── processAccepted ────────────────────────────────────────────────────────

    @Test
    @DisplayName("processAccepted - succeeds on first attempt")
    public void processAccepted_success() {
        when(repository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));
        when(properties.getMaxRetries()).thenReturn(3);

        service.processAccepted(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId());

        verify(apiService).sendContractNegotiationEventMessageAccepted(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId());
        verify(repository, never()).save(any(ContractNegotiation.class));
        verify(apiService, never()).sendContractNegotiationTerminationMessage(any());
        verifyNoInteractions(taskScheduler);
    }

    // ── processVerify ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("processVerify - succeeds on first attempt")
    public void processVerify_success() {
        when(repository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED));
        when(properties.getMaxRetries()).thenReturn(3);

        service.processVerify(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED.getId());

        verify(apiService).sendContractAgreementVerificationMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED.getId());
        verify(repository, never()).save(any(ContractNegotiation.class));
        verify(apiService, never()).sendContractNegotiationTerminationMessage(any());
        verifyNoInteractions(taskScheduler);
    }

    // ── processFinalize ────────────────────────────────────────────────────────

    @Test
    @DisplayName("processFinalize - succeeds on first attempt")
    public void processFinalize_success() {
        when(repository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED));
        when(properties.getMaxRetries()).thenReturn(3);

        service.processFinalize(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());

        verify(apiService).sendContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
        verify(repository, never()).save(any(ContractNegotiation.class));
        verify(apiService, never()).sendContractNegotiationTerminationMessage(any());
        verifyNoInteractions(taskScheduler);
    }
}

