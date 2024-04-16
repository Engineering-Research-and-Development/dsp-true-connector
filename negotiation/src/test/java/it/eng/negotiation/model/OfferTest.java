package it.eng.negotiation.model;

import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OfferTest {

	@Test
	public void validOffer() {
		Offer offer = Offer.Builder.newInstance()
				.target(ModelUtil.TARGET)
				.build();
		assertNotNull(offer, "Offer should be created with mandatory fields");
		assertNotNull(offer.getId());
	}
	
	@Test
	public void invalidOffer() {
		assertThrows(ValidationException.class, 
				() -> Offer.Builder.newInstance()
					.assignee(ModelUtil.ASSIGNEE)
					.build());
	}	
	
}
