package it.eng.negotiation.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.MockObjectUtil;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.tools.usagecontrol.UsageControlProperties;

@ExtendWith(MockitoExtension.class)
class AgreementAPIServiceTest {
	
	private final String AGREEMENT_ID = "test";

	@Mock
	private UsageControlProperties usageControlProperties;
	@Mock
	private AgreementRepository agreementRepository;
	
	@InjectMocks
	private AgreementAPIService service;
	
	@Test
	@DisplayName("Validate agreement - usage control enabled and valid")
	void validateAgreement() {
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		when(agreementRepository.findById(AGREEMENT_ID)).thenReturn(Optional.of(MockObjectUtil.AGREEMENT));
		
		assertDoesNotThrow(() -> {
			service.validateAgreement(AGREEMENT_ID);
	    });
	}

	@Test
	@DisplayName("Validate agreement - usage control enabled and invalid")
	public void validateAgreement_notValid() {
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		when(agreementRepository.findById(AGREEMENT_ID)).thenReturn(Optional.empty());
		assertThrows(ContractNegotiationAPIException.class, ()-> service.validateAgreement(AGREEMENT_ID));
	}
	
	@Test
	@DisplayName("Validate agreement - usage control disabled")
	void validateAgreement_uc_disabled() {
		when(usageControlProperties.usageControlEnabled()).thenReturn(false);
		
		assertDoesNotThrow(() -> {
			service.validateAgreement(AGREEMENT_ID);
	    });
		verifyNoInteractions(agreementRepository);
	}
}
