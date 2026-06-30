package it.eng.negotiation.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.exception.PolicyEnforcementException;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.service.PolicyAdministrationPoint;
import it.eng.negotiation.policy.service.PolicyEnforcementPoint;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.PolicyEnforcementRepository;

@ExtendWith(MockitoExtension.class)
class AgreementAPIServiceTest {
	
	@Mock
	private AgreementRepository agreementRepository;
	@Mock
	private PolicyAdministrationPoint policyAdministrationPoint;
	@Mock
	private PolicyEnforcementPoint policyEnforcementPoint;
	@Mock
	private PolicyEnforcementRepository policyEnforcementRepository;
	
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
	
	@Mock
	private Pageable pageable;

	// find agreement by id
	@Test
	@DisplayName("Find agreement by id - success")
	public void findAgreementById() {
		when(agreementRepository.findById(NegotiationMockObjectUtil.AGREEMENT.getId()))
				.thenReturn(Optional.of(NegotiationMockObjectUtil.AGREEMENT));

		Agreement result = service.findAgreementById(NegotiationMockObjectUtil.AGREEMENT.getId());

		assertNotNull(result);
		assertEquals(NegotiationMockObjectUtil.AGREEMENT.getId(), result.getId());
	}

	@Test
	@DisplayName("Find agreement by id - not found")
	public void findAgreementById_notFound() {
		when(agreementRepository.findById(any(String.class))).thenReturn(Optional.empty());

		assertThrows(ContractNegotiationAPIException.class,
				() -> service.findAgreementById(NegotiationMockObjectUtil.AGREEMENT.getId()));
	}

	// find agreements
	@Test
	@DisplayName("Find agreements - returns page")
	public void findAgreements() {
		Page<Agreement> agreementPage = new PageImpl<>(List.of(NegotiationMockObjectUtil.AGREEMENT), pageable, 1);
		when(agreementRepository.findAll(any(Pageable.class))).thenReturn(agreementPage);

		Page<Agreement> result = service.findAgreements(pageable);

		assertNotNull(result);
		assertFalse(result.isEmpty());
		assertEquals(1, result.getTotalElements());
	}

	// enforce agreement
	@Test
	@DisplayName("Enforce agreement ok")
	public void enforceAgreement() {
		when(agreementRepository.findById(NegotiationMockObjectUtil.AGREEMENT.getId())).thenReturn(Optional.of(NegotiationMockObjectUtil.AGREEMENT));
//		when(policyEnforcementService.isAgreementValid(NegotiationMockObjectUtil.AGREEMENT)).thenReturn(true);
		when(policyEnforcementPoint.enforcePolicy(NegotiationMockObjectUtil.AGREEMENT, "enforceAgreement"))
			.thenReturn(policyDecisionAllowed);
		assertDoesNotThrow(()-> service.enforceAgreement(NegotiationMockObjectUtil.AGREEMENT.getId()));
	}
	
	// find agreement by id enriched
	@Test
	@DisplayName("Find agreement by id enriched - with enforcement count")
	public void findAgreementByIdEnriched_withEnforcement() {
		PolicyEnforcement pe = new PolicyEnforcement("pe-id", NegotiationMockObjectUtil.AGREEMENT.getId(), 3);
		when(agreementRepository.findById(NegotiationMockObjectUtil.AGREEMENT.getId()))
				.thenReturn(Optional.of(NegotiationMockObjectUtil.AGREEMENT));
		when(policyEnforcementRepository.findByAgreementId(NegotiationMockObjectUtil.AGREEMENT.getId()))
				.thenReturn(Optional.of(pe));

		JsonNode result = service.findAgreementByIdEnriched(NegotiationMockObjectUtil.AGREEMENT.getId());

		assertNotNull(result);
		assertNotNull(result.get("currentCount"));
		assertEquals(3, result.get("currentCount").asInt());
	}

	@Test
	@DisplayName("Find agreement by id enriched - no enforcement record")
	public void findAgreementByIdEnriched_withoutEnforcement() {
		when(agreementRepository.findById(NegotiationMockObjectUtil.AGREEMENT.getId()))
				.thenReturn(Optional.of(NegotiationMockObjectUtil.AGREEMENT));
		when(policyEnforcementRepository.findByAgreementId(NegotiationMockObjectUtil.AGREEMENT.getId()))
				.thenReturn(Optional.empty());

		JsonNode result = service.findAgreementByIdEnriched(NegotiationMockObjectUtil.AGREEMENT.getId());

		assertNotNull(result);
		assertFalse(result.has("currentCount"));
	}

	@Test
	@DisplayName("Find agreement by id enriched - agreement not found")
	public void findAgreementByIdEnriched_notFound() {
		when(agreementRepository.findById(any(String.class))).thenReturn(Optional.empty());

		assertThrows(ContractNegotiationAPIException.class,
				() -> service.findAgreementByIdEnriched(NegotiationMockObjectUtil.AGREEMENT.getId()));
	}

	@Test
	@DisplayName("Enforce agreement - not valid")
	public void enforceAgreement_not_valid() {
		when(agreementRepository.findById(NegotiationMockObjectUtil.AGREEMENT.getId())).thenReturn(Optional.of(NegotiationMockObjectUtil.AGREEMENT));
//		when(policyEnforcementService.isAgreementValid(NegotiationMockObjectUtil.AGREEMENT)).thenReturn(false);
		when(policyEnforcementPoint.enforcePolicy(NegotiationMockObjectUtil.AGREEMENT, "enforceAgreement"))
			.thenReturn(policyDecisionDenied);
		assertThrows(PolicyEnforcementException.class, ()-> service.enforceAgreement(NegotiationMockObjectUtil.AGREEMENT.getId()));
	}
}
