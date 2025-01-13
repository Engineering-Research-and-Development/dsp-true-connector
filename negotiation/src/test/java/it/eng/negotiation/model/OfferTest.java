package it.eng.negotiation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.negotiation.serializer.NegotiationSerializer;
import jakarta.validation.ValidationException;

public class OfferTest {

	@Test
	public void validOffer() {
		Offer offer = Offer.Builder.newInstance()
				.target(NegotiationMockObjectUtil.TARGET)
				.assigner(NegotiationMockObjectUtil.ASSIGNER)
				.build();
		assertNotNull(offer, "Offer should be created with mandatory fields");
		assertNotNull(offer.getId());
	}
	
	@Test
	public void invalidOffer() {
		assertThrows(ValidationException.class, 
				() -> Offer.Builder.newInstance()
					.assignee(NegotiationMockObjectUtil.ASSIGNEE)
					.build());
	}	
	
	@Test
	public void equalsTrue() {
		String id = UUID.randomUUID().toString();
		Offer offer = Offer.Builder.newInstance()
				.id(id)
				.assigner(NegotiationMockObjectUtil.ASSIGNER)
				.target(NegotiationMockObjectUtil.TARGET)
				.build();
		Offer offerB = Offer.Builder.newInstance()
				.id(id)
				.assigner(NegotiationMockObjectUtil.ASSIGNER)
				.target(NegotiationMockObjectUtil.TARGET)
				.build();
		assertTrue(offer.equals(offerB));
	}

	@Test
	public void equalsFalse() {
		Offer offer = Offer.Builder.newInstance()
				.target(NegotiationMockObjectUtil.TARGET)
				.assigner(NegotiationMockObjectUtil.ASSIGNER)
				.build();
		Offer offerB = Offer.Builder.newInstance()
				.target("SomeDifferentTarget")
				.assigner(NegotiationMockObjectUtil.ASSIGNER)
				.build();
		assertFalse(offer.equals(offerB));
	}
	
	@Test
	public void equalsTest() {
		Offer offer = NegotiationMockObjectUtil.OFFER;
		String ss = NegotiationSerializer.serializePlain(offer);
		Offer offer2 = NegotiationSerializer.deserializePlain(ss, Offer.class);
		assertThat(offer).usingRecursiveComparison().isEqualTo(offer2);
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		Offer offer = NegotiationMockObjectUtil.OFFER;
		String ss = NegotiationSerializer.serializePlain(offer);
		Offer obj = NegotiationSerializer.deserializePlain(ss, Offer.class);
		assertThat(offer).usingRecursiveComparison().isEqualTo(obj);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		Offer offer = NegotiationMockObjectUtil.OFFER;
		String ss = NegotiationSerializer.serializeProtocol(offer);
		Offer obj = NegotiationSerializer.deserializeProtocol(ss, Offer.class);
		assertThat(offer).usingRecursiveComparison().isEqualTo(obj);
	}
	
}
