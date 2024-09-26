package it.eng.negotiation.service;

import org.springframework.stereotype.Service;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.tools.usagecontrol.UsageControlProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AgreementAPIService {
	
	private final UsageControlProperties usageControlProperties;
	private final AgreementRepository agreementRepository;
	
	public AgreementAPIService(UsageControlProperties usageControlProperties, AgreementRepository agreementRepository) {
		super();
		this.usageControlProperties = usageControlProperties;
		this.agreementRepository = agreementRepository;
	}

	public void validateAgreement(String agreementId) {
		if(usageControlProperties.usageControlEnabled()) {
			log.info("Agreement received {}", agreementId);
			agreementRepository.findById(agreementId)
				.orElseThrow(() -> new ContractNegotiationAPIException("Agreement with Id " + agreementId + " not found."));
			
		} else {
			log.info("UsageControl DISABLED - will not check if agreement is present or valid");
		}
		return;
		// TODO add additional checks like contract dates
		//		LocalDateTime agreementStartDate = LocalDateTime.parse(agreement.getTimestamp(), FORMATTER);
		//		agreementStartDate.isBefore(LocalDateTime.now());
	}
}
