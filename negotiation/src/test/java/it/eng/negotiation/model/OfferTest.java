package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import jakarta.validation.ValidationException;

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
	
	@Test
	public void equalsTrue() {
		String id = UUID.randomUUID().toString();
		Offer offer = Offer.Builder.newInstance()
				.id(id)
				.target(ModelUtil.TARGET)
				.build();
		Offer offerB = Offer.Builder.newInstance()
				.id(id)
				.target(ModelUtil.TARGET)
				.build();
		assertTrue(offer.equals(offerB));
	}

	@Test
	public void equalsFalse() {
		Offer offer = Offer.Builder.newInstance()
				.target(ModelUtil.TARGET)
				.build();
		Offer offerB = Offer.Builder.newInstance()
				.target("SomeDifferentTarget")
				.build();
		assertFalse(offer.equals(offerB));
	}
	
}
