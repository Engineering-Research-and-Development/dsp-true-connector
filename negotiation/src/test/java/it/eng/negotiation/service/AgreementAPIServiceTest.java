package it.eng.negotiation.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.negotiation.exception.PolicyEnforcementException;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.service.policy.PolicyEnforcementService;

@ExtendWith(MockitoExtension.class)
class AgreementAPIServiceTest {
	
	@Mock
	private AgreementRepository agreementRepository;
	@Mock
	private PolicyEnforcementService policyEnforcementService;
	
	@InjectMocks
	private AgreementAPIService service;
	
	// enforce agreement
	@Test
	@DisplayName("Enforce agreement ok")
	public void enforceAgreement() {
		when(agreementRepository.findById(NegotiationMockObjectUtil.AGREEMENT.getId())).thenReturn(Optional.of(NegotiationMockObjectUtil.AGREEMENT));
		when(policyEnforcementService.isAgreementValid(NegotiationMockObjectUtil.AGREEMENT)).thenReturn(true);
		
		assertDoesNotThrow(()-> service.enforceAgreement(NegotiationMockObjectUtil.AGREEMENT.getId()));
	}
	
	@Test
	@DisplayName("Enforce agreement - not valid")
	public void enforceAgreement_not_valid() {
		when(agreementRepository.findById(NegotiationMockObjectUtil.AGREEMENT.getId())).thenReturn(Optional.of(NegotiationMockObjectUtil.AGREEMENT));
		when(policyEnforcementService.isAgreementValid(NegotiationMockObjectUtil.AGREEMENT)).thenReturn(false);
		
		assertThrows(PolicyEnforcementException.class, ()-> service.enforceAgreement(NegotiationMockObjectUtil.AGREEMENT.getId()));
	}
}
