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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @InjectMocks
    private AutomaticNegotiationService service;

    @Captor
    private ArgumentCaptor<ContractNegotiation> cnCaptor;

    // ── processAgreed ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("processAgreed - succeeds on first attempt, no save or termination")
    public void processAgreed_success() {
        when(repository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(properties.getMaxRetryAttempts()).thenReturn(3);

        service.processAgreed(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());

        verify(apiService).sendContractAgreementMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());
        verify(repository, never()).save(any(ContractNegotiation.class));
        verify(apiService, never()).sendContractNegotiationTerminationMessage(any());
    }

    @Test
    @DisplayName("processAgreed - fails once then succeeds, retryCount saved once")
    public void processAgreed_retryOnce_thenSuccess() {
        when(repository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(properties.getMaxRetryAttempts()).thenReturn(3);
        when(properties.getRetryDelayMs()).thenReturn(0L);
        doThrow(new RuntimeException("timeout"))
                .doReturn(null)
                .when(apiService).sendContractAgreementMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());
        when(repository.save(any())).thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED);

        service.processAgreed(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());

        verify(repository).save(cnCaptor.capture());
        assertEquals(1, cnCaptor.getValue().getRetryCount());
        verify(apiService, never()).sendContractNegotiationTerminationMessage(any());
    }

    @Test
    @DisplayName("processAgreed - exhausts all retries, termination succeeds")
    public void processAgreed_exhaustsRetries_terminationSucceeds() {
        when(repository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(properties.getMaxRetryAttempts()).thenReturn(2);
        when(properties.getRetryDelayMs()).thenReturn(0L);
        doThrow(new RuntimeException("timeout"))
                .when(apiService).sendContractAgreementMessage(any());
        when(repository.save(any())).thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED);

        service.processAgreed(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());

        // 3 saves: retryCount 1, 2, 3 (last triggers termination)
        verify(repository, times(3)).save(any(ContractNegotiation.class));
        verify(apiService).sendContractNegotiationTerminationMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());
        verify(publisher, never()).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("processAgreed - exhausts retries and termination also fails, force-terminates locally")
    public void processAgreed_exhaustsRetries_terminationAlsoFails() {
        when(repository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        when(properties.getMaxRetryAttempts()).thenReturn(0);
        doThrow(new RuntimeException("timeout"))
                .when(apiService).sendContractAgreementMessage(any());
        doThrow(new RuntimeException("peer unreachable"))
                .when(apiService).sendContractNegotiationTerminationMessage(any());
        when(repository.save(any())).thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED);

        service.processAgreed(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId());

        verify(repository, times(2)).save(cnCaptor.capture());
        assertEquals(ContractNegotiationState.TERMINATED, cnCaptor.getValue().getState());
        verify(publisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    @DisplayName("processAgreed - CN not found throws ContractNegotiationNotFoundException")
    public void processAgreed_cnNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(ContractNegotiationNotFoundException.class,
                () -> service.processAgreed("unknown-id"));

        verify(apiService, never()).sendContractAgreementMessage(any());
    }

    @Test
    @DisplayName("processAgreed - resumes from persisted retryCount equal to maxRetries")
    public void resumeFromPersistedRetryCount() {
        ContractNegotiation cnWithRetries = ContractNegotiation.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .state(ContractNegotiationState.REQUESTED)
                .retryCount(2)
                .build();
        when(repository.findById(cnWithRetries.getId())).thenReturn(Optional.of(cnWithRetries));
        when(properties.getMaxRetryAttempts()).thenReturn(2);
        doThrow(new RuntimeException("timeout"))
                .when(apiService).sendContractAgreementMessage(any());
        when(repository.save(any())).thenReturn(cnWithRetries);

        service.processAgreed(cnWithRetries.getId());

        // Only one send attempt before exhaustion (retryCount already at max)
        verify(apiService, times(1)).sendContractAgreementMessage(any());
        verify(apiService).sendContractNegotiationTerminationMessage(cnWithRetries.getId());
    }

    // ── processAccepted ────────────────────────────────────────────────────────

    @Test
    @DisplayName("processAccepted - succeeds on first attempt")
    public void processAccepted_success() {
        when(repository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));
        when(properties.getMaxRetryAttempts()).thenReturn(3);

        service.processAccepted(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId());

        verify(apiService).sendContractNegotiationEventMessageAccepted(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId());
        verify(repository, never()).save(any(ContractNegotiation.class));
        verify(apiService, never()).sendContractNegotiationTerminationMessage(any());
    }

    // ── processVerify ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("processVerify - succeeds on first attempt")
    public void processVerify_success() {
        when(repository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED));
        when(properties.getMaxRetryAttempts()).thenReturn(3);

        service.processVerify(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED.getId());

        verify(apiService).sendContractAgreementVerificationMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED.getId());
        verify(repository, never()).save(any(ContractNegotiation.class));
        verify(apiService, never()).sendContractNegotiationTerminationMessage(any());
    }

    // ── processFinalize ────────────────────────────────────────────────────────

    @Test
    @DisplayName("processFinalize - succeeds on first attempt")
    public void processFinalize_success() {
        when(repository.findById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()))
                .thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED));
        when(properties.getMaxRetryAttempts()).thenReturn(3);

        service.processFinalize(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());

        verify(apiService).sendContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
        verify(repository, never()).save(any(ContractNegotiation.class));
        verify(apiService, never()).sendContractNegotiationTerminationMessage(any());
    }
}

