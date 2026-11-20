package it.eng.negotiation.service;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.tools.service.TenantContextHolder;
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
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.service.PolicyAdministrationPoint;
import it.eng.negotiation.policy.service.PolicyEnforcementPoint;
import it.eng.negotiation.repository.AgreementRepository;

@ExtendWith(MockitoExtension.class)
class AgreementAPIServiceTest {
	
	@Mock
	private AgreementRepository agreementRepository;
	@Mock
	private PolicyAdministrationPoint policyAdministrationPoint;
	@Mock
	private PolicyEnforcementPoint policyEnforcementPoint;
	
	@InjectMocks
	private AgreementAPIService service;
	
	PolicyDecision policyDecisionAllowed = PolicyDecision.Builder.newInstance()
			.allowed(true)
			.message("Policy allowed test")
			.build();
	
	PolicyDecision policyDecisionDenied = PolicyDecision.Builder.newInstance()
			.allowed(false)
			.message("Policy denied test")
			.build();
	
	// enforce agreement
	@Test
	@DisplayName("Enforce agreement ok")
	public void enforceAgreement() {
		when(agreementRepository.findAgreementById(NegotiationMockObjectUtil.AGREEMENT.getId())).thenReturn(Optional.of(NegotiationMockObjectUtil.AGREEMENT));
//		when(policyEnforcementService.isAgreementValid(NegotiationMockObjectUtil.AGREEMENT)).thenReturn(true);
		when(policyEnforcementPoint.enforcePolicy(NegotiationMockObjectUtil.AGREEMENT, "enforceAgreement"))
			.thenReturn(policyDecisionAllowed);
		assertDoesNotThrow(()-> service.enforceAgreement(NegotiationMockObjectUtil.AGREEMENT.getId()));
	}
	
	@Test
	@DisplayName("Enforce agreement - not valid")
	public void enforceAgreement_not_valid() {
		when(agreementRepository.findAgreementById(NegotiationMockObjectUtil.AGREEMENT.getId())).thenReturn(Optional.of(NegotiationMockObjectUtil.AGREEMENT));
//		when(policyEnforcementService.isAgreementValid(NegotiationMockObjectUtil.AGREEMENT)).thenReturn(false);
		when(policyEnforcementPoint.enforcePolicy(NegotiationMockObjectUtil.AGREEMENT, "enforceAgreement"))
			.thenReturn(policyDecisionDenied);
		assertThrows(PolicyEnforcementException.class, ()-> service.enforceAgreement(NegotiationMockObjectUtil.AGREEMENT.getId()));
	}

	// tenant isolation

	@Test
	@DisplayName("Enforce agreement - own tenant's agreement is enforced")
	public void enforceAgreement_ownTenant() {
		when(agreementRepository.findByIdAndTenantId(NegotiationMockObjectUtil.AGREEMENT.getId(), "tenant-a"))
			.thenReturn(Optional.of(NegotiationMockObjectUtil.AGREEMENT));
		when(policyEnforcementPoint.enforcePolicy(NegotiationMockObjectUtil.AGREEMENT, "enforceAgreement"))
			.thenReturn(policyDecisionAllowed);

		TenantContextHolder.setTenantId("tenant-a");
		try {
			assertDoesNotThrow(() -> service.enforceAgreement(NegotiationMockObjectUtil.AGREEMENT.getId()));
		} finally {
			TenantContextHolder.clear();
		}
		verify(agreementRepository, never()).findAgreementById(anyString());
	}

	@Test
	@DisplayName("Enforce agreement - another tenant's agreement is not found and no policy is evaluated")
	public void enforceAgreement_otherTenant_notFound() {
		when(agreementRepository.findByIdAndTenantId(NegotiationMockObjectUtil.AGREEMENT.getId(), "tenant-a"))
			.thenReturn(Optional.empty());

		TenantContextHolder.setTenantId("tenant-a");
		try {
			assertThrows(ContractNegotiationAPIException.class,
					() -> service.enforceAgreement(NegotiationMockObjectUtil.AGREEMENT.getId()));
		} finally {
			TenantContextHolder.clear();
		}
		verify(agreementRepository, never()).findAgreementById(anyString());
		verifyNoInteractions(policyEnforcementPoint);
	}
}
