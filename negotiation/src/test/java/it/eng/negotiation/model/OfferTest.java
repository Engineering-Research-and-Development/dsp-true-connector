package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import jakarta.validation.ValidationException;

public class OfferTest {

	@Test
	public void validOffer() {
		Offer offer = Offer.Builder.newInstance()
				.assigner(ModelUtil.ASSIGNER)
				.assignee(ModelUtil.ASSIGNEE)
				.permission(Arrays.asList(ModelUtil.PERMISSION))
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
