package it.eng.negotiation.service.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.repository.PolicyEnforcementRepository;

@ExtendWith(MockitoExtension.class)
class PolicyEnforcementServiceTest {

	private static final String AGREEMENT_ID = "agreement_id";
	
	@InjectMocks
	private PolicyEnforcementService service;
	
	@Mock
	private PolicyEnforcementRepository repository;

	@Test
	public void policyEnforcementExists() {
		PolicyEnforcement pe = new PolicyEnforcement(UUID.randomUUID().toString(), AGREEMENT_ID, 0);

		when(repository.findByAgreementId(AGREEMENT_ID)).thenReturn(Optional.of(pe));

		assertTrue(service.policyEnforcementExists(AGREEMENT_ID));

		verify(repository).findByAgreementId(AGREEMENT_ID);
	}
	
	@Test
	public void policyEnforcementDoesNotExists() {
		when(repository.findByAgreementId(AGREEMENT_ID)).thenReturn(Optional.empty());

		assertFalse(service.policyEnforcementExists(AGREEMENT_ID));

		verify(repository).findByAgreementId(AGREEMENT_ID);
	}
}
