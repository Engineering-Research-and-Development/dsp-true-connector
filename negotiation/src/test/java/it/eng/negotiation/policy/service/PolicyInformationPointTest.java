package it.eng.negotiation.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.repository.PolicyEnforcementRepository;

@ExtendWith(MockitoExtension.class)
class PolicyInformationPointTest {
	
	@Mock
	private PolicyEnforcementRepository policyEnforcementRepository;

	@InjectMocks
	private PolicyInformationPoint policyInformationPoint;

	private Agreement agreement;
	
	@BeforeEach
	public void setUp() {
		// Create a test Agreement
		agreement = Agreement.Builder.newInstance()
				.id(NegotiationMockObjectUtil.generateUUID())
				.assignee(NegotiationMockObjectUtil.ASSIGNEE)
				.assigner(NegotiationMockObjectUtil.ASSIGNER)
				.target(NegotiationMockObjectUtil.TARGET)
				.timestamp(ZonedDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
				.permission(Arrays.asList(NegotiationMockObjectUtil.PERMISSION_COUNT_5))
				.build();
	}

	@Test
	public void getUserLocation() {
		String location = policyInformationPoint.getLocation();
		assertEquals("EU", location);
	}

	@Test
	public void getAccessTime() {
		LocalDateTime accessTime = policyInformationPoint.getAccessTime();
		assertNotNull(accessTime);
		// The access time should be close to the current time
		assertTrue(accessTime.isAfter(LocalDateTime.now().minusSeconds(1)));
		assertTrue(accessTime.isBefore(LocalDateTime.now().plusSeconds(1)));
	}

	@Test
	public void getAccessPurpose() {
		String purpose = policyInformationPoint.getAccessPurpose();
		assertEquals("dsp", purpose);
	}

	@Test
	public void getAllAttributes() {
		PolicyEnforcement policyEnforcement = new PolicyEnforcement();
		policyEnforcement.setAgreementId(agreement.getId());
		policyEnforcement.setCount(3);
		when(policyEnforcementRepository.findByAgreementId(any())).thenReturn(Optional.of(policyEnforcement));
		Map<String, Object> attributes = policyInformationPoint.getAllAttributes(agreement);

		// Verify that all expected attributes are present
		assertEquals(agreement.getId(), attributes.get("agreementId"));
		assertEquals(agreement.getAssignee(), attributes.get("assignee"));
		assertEquals(agreement.getAssigner(), attributes.get("assigner"));
		assertNotNull(attributes.get("accessTime"));
		assertEquals("EU", attributes.get("location"));
		// update when purposeService is implemented
		assertEquals("dsp", attributes.get("purpose"));
	}

}
