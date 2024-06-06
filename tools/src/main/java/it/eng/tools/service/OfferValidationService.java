package it.eng.tools.service;

import it.eng.tools.event.contractnegotiation.OfferValidationRequest;
import lombok.Getter;

@Getter
public class OfferValidationService {
	
	private boolean isValid;

	public void validateOffer(OfferValidationRequest offerValidationRequest) {
		
		isValid = false;
	}

}
