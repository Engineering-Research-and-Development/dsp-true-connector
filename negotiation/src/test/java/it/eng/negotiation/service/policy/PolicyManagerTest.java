package it.eng.negotiation.service.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import it.eng.negotiation.service.policy.PolicyManager;

@ExtendWith(MockitoExtension.class)
class PolicyManagerTest {

	private static final String AGREEMENT_ID = "agreement_id";
	
	@Mock
	private PolicyEnforcementRepository repository;
	
	@Captor
	private ArgumentCaptor<PolicyEnforcement> argPolicyEnforcement;
	
	@InjectMocks
	private PolicyManager manager;
	
	@Test
	@DisplayName("Access count for existing agreement")
	void getAccessCount() {
		PolicyEnforcement pe = new PolicyEnforcement();
		pe.setId(UUID.randomUUID().toString());
		pe.setAgreementId(AGREEMENT_ID);
		pe.setCount(5);
		when(repository.findByAgreementId(AGREEMENT_ID)).thenReturn(Optional.of(pe));
		
		assertEquals(5, manager.getAccessCount(AGREEMENT_ID));
	}

	@Test
	@DisplayName("Access count for NON existing agreement")
	void getAccessCount_not_found() {
		when(repository.findByAgreementId(AGREEMENT_ID)).thenReturn(Optional.empty());

		assertThrows(PolicyEnforcementException.class, 
				() -> manager.getAccessCount(AGREEMENT_ID));
	}
	
	@Test
	@DisplayName("Update access count")
	void opdateAccessCount() {
		PolicyEnforcement pe = new PolicyEnforcement();
		pe.setId(UUID.randomUUID().toString());
		pe.setAgreementId(AGREEMENT_ID);
		pe.setCount(5);
		when(repository.findByAgreementId(AGREEMENT_ID)).thenReturn(Optional.of(pe));
		
		manager.updateAccessCount(AGREEMENT_ID);
		
		verify(repository).save(argPolicyEnforcement.capture());
		assertEquals(6, argPolicyEnforcement.getValue().getCount());
	}
	
	@Test
	@DisplayName("Update access count - exception")
	void opdateAccessCount_exception() {
		when(repository.findByAgreementId(AGREEMENT_ID)).thenReturn(Optional.empty());
		
		assertThrows(PolicyEnforcementException.class, 
				() -> manager.getAccessCount(AGREEMENT_ID));
		
		verify(repository, times(0)).save(argPolicyEnforcement.capture());
	}

}
