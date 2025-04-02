package it.eng.negotiation.policy.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.negotiation.exception.PolicyEnforcementException;
import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.repository.PolicyEnforcementRepository;

@ExtendWith(MockitoExtension.class)
class PolicyAdministrationPointTest {
	
	private static final String AGREEMENT_ID = "agreement_id";
	
	@Mock
	private PolicyEnforcementRepository policyEnforcementRepository;
	
	@Captor
	private ArgumentCaptor<PolicyEnforcement> argPolicyEnforcement;

	@InjectMocks
	private PolicyAdministrationPoint policyAdministrationPoint;
	
	@Test
	@DisplayName("Update access count")
	void opdateAccessCount() {
		PolicyEnforcement pe = new PolicyEnforcement();
		pe.setId(UUID.randomUUID().toString());
		pe.setAgreementId(AGREEMENT_ID);
		pe.setCount(5);
		when(policyEnforcementRepository.findByAgreementId(AGREEMENT_ID)).thenReturn(Optional.of(pe));
		
		policyAdministrationPoint.updateAccessCount(AGREEMENT_ID);
		
		verify(policyEnforcementRepository).save(argPolicyEnforcement.capture());
		assertEquals(6, argPolicyEnforcement.getValue().getCount());
	}
	
	@Test
	@DisplayName("Update access count - exception")
	void opdateAccessCount_exception() {
		when(policyEnforcementRepository.findByAgreementId(AGREEMENT_ID)).thenReturn(Optional.empty());
		
		assertThrows(PolicyEnforcementException.class, 
				() -> policyAdministrationPoint.updateAccessCount(AGREEMENT_ID));
		
		verify(policyEnforcementRepository, times(0)).save(argPolicyEnforcement.capture());
	}
	
	@Test
	public void policyEnforcementExists() {
		PolicyEnforcement pe = new PolicyEnforcement(UUID.randomUUID().toString(), AGREEMENT_ID, 0);

		when(policyEnforcementRepository.findByAgreementId(AGREEMENT_ID)).thenReturn(Optional.of(pe));

		assertTrue(policyAdministrationPoint.policyEnforcementExists(AGREEMENT_ID));

		verify(policyEnforcementRepository).findByAgreementId(AGREEMENT_ID);
	}
	
	@Test
	public void policyEnforcementDoesNotExists() {
		when(policyEnforcementRepository.findByAgreementId(AGREEMENT_ID)).thenReturn(Optional.empty());

		assertFalse(policyAdministrationPoint.policyEnforcementExists(AGREEMENT_ID));

		verify(policyEnforcementRepository).findByAgreementId(AGREEMENT_ID);
	}
	
}
