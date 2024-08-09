package it.eng.negotiation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.negotiation.serializer.Serializer;
import jakarta.validation.ValidationException;

public class OfferTest {

	@Test
	public void validOffer() {
		Offer offer = Offer.Builder.newInstance()
				.target(MockObjectUtil.TARGET)
				.assigner(MockObjectUtil.ASSIGNER)
				.build();
		assertNotNull(offer, "Offer should be created with mandatory fields");
		assertNotNull(offer.getId());
	}
	
	@Test
	public void invalidOffer() {
		assertThrows(ValidationException.class, 
				() -> Offer.Builder.newInstance()
					.assignee(MockObjectUtil.ASSIGNEE)
					.build());
	}	
	
	@Test
	public void equalsTrue() {
		String id = UUID.randomUUID().toString();
		Offer offer = Offer.Builder.newInstance()
				.id(id)
				.assigner(MockObjectUtil.ASSIGNER)
				.target(MockObjectUtil.TARGET)
				.build();
		Offer offerB = Offer.Builder.newInstance()
				.id(id)
				.assigner(MockObjectUtil.ASSIGNER)
				.target(MockObjectUtil.TARGET)
				.build();
		assertTrue(offer.equals(offerB));
	}

	@Test
	public void equalsFalse() {
		Offer offer = Offer.Builder.newInstance()
				.target(MockObjectUtil.TARGET)
				.assigner(MockObjectUtil.ASSIGNER)
				.build();
		Offer offerB = Offer.Builder.newInstance()
				.target("SomeDifferentTarget")
				.assigner(MockObjectUtil.ASSIGNER)
				.build();
		assertFalse(offer.equals(offerB));
	}
	
	@Test
	public void equalsTest() {
		Offer offer = MockObjectUtil.OFFER;
		String ss = Serializer.serializePlain(offer);
		Offer offer2 = Serializer.deserializePlain(ss, Offer.class);
		assertThat(offer).usingRecursiveComparison().isEqualTo(offer2);
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		Offer offer = MockObjectUtil.OFFER;
		String ss = Serializer.serializePlain(offer);
		Offer obj = Serializer.deserializePlain(ss, Offer.class);
		assertThat(offer).usingRecursiveComparison().isEqualTo(obj);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		Offer offer = MockObjectUtil.OFFER;
		String ss = Serializer.serializeProtocol(offer);
		Offer obj = Serializer.deserializeProtocol(ss, Offer.class);
		assertThat(offer).usingRecursiveComparison().isEqualTo(obj);
	}
	
}
