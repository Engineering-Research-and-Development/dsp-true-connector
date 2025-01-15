package it.eng.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;

public class OfferTest {
	
	private static final String TARGET_A = "urn:uuid:TARGET";
	private static final String TARGET_B = "urn:uuid:TARGET_B";
	private static final String ASSIGNEE = "urn:uuid:ASSIGNEE";
	private static final String ASSIGNER = "urn:uuid:ASSIGNER";
	
	String id = UUID.randomUUID().toString();
	
	private Constraint constraint = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.DATE_TIME)
			.operator(Operator.GT)
			.rightOperand("2024-02-29T00:00:01+01:00")
			.build();
	
	private Permission permission = Permission.Builder.newInstance()
			.action(Action.USE)
			.target(TARGET_A)
			.constraint(Set.of(constraint))
			.build();

	private Offer offerA = Offer.Builder.newInstance()
			.id(id)
			.target(TARGET_A)
			.assignee(ASSIGNEE)
			.assigner(ASSIGNER)
			.permission(Set.of(permission))
			.build();
	
	private Offer offerB = Offer.Builder.newInstance()
			.id(id)
			.target(TARGET_B)
			.assignee(ASSIGNEE)
			.assigner(ASSIGNER)
			.permission(Set.of(CatalogMockObjectUtil.PERMISSION_ANONYMIZE))
			.build();
	@Test
	public void equalsTrue() {
		assertTrue(offerA.equals(offerA));
	}

	@Test
	public void equalsFalse() {
		assertFalse(offerA.equals(offerB));
	}
	
	@Test
	public void equalsList() {
		 Constraint constraint1 = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.DATE_TIME)
				.operator(Operator.GT)
				.rightOperand("2024-02-29T00:00:01+01:00")
				.build();
		 Constraint constraint2 = Constraint.Builder.newInstance()
					.leftOperand(LeftOperand.COUNT)
					.operator(Operator.EQ)
					.rightOperand("5")
					.build();
		 
		 Permission permission1 = Permission.Builder.newInstance()
				.action(Action.USE)
				.target(TARGET_A)
				.constraint(Set.of(constraint1, constraint2))
				.build();
		Permission permission2 = Permission.Builder.newInstance()
				.action(Action.ANONYMIZE)
				.target(TARGET_A)
				.constraint(Set.of(constraint2, constraint1))
				.build();
		
		Offer offerA = Offer.Builder.newInstance()
				.id(id)
				.target(TARGET_A)
				.assignee(ASSIGNEE)
				.assigner(ASSIGNER)
				.permission(Set.of(permission1, permission2))
				.build();
		Offer offerB = Offer.Builder.newInstance()
				.id(id)
				.target(TARGET_A)
				.assignee(ASSIGNEE)
				.assigner(ASSIGNER)
				.permission(Set.of(permission2, permission1))
				.build();
		assertTrue(offerA.equals(offerB));
	}
	
	@Test
	public void collection() {
		Set<Permission> permissions = new HashSet<>();
		permissions.add(CatalogMockObjectUtil.PERMISSION);
		Offer o = Offer.Builder.newInstance()
				.assignee(ASSIGNEE)
				.assigner(ASSIGNER)
				.permission(permissions)
				.build();
		
		o.getPermission().add(CatalogMockObjectUtil.PERMISSION_ANONYMIZE);
		o.getPermission();
		
		
		String oString = "{\r\n"
				+ "                    \"action\": \"USE\",\r\n"
				+ "                    \"constraint\": [\r\n"
				+ "                        {\r\n"
				+ "                            \"leftOperand\": \"COUNT\",\r\n"
				+ "                            \"operator\": \"EQ\",\r\n"
				+ "                            \"rightOperand\": \"5\"\r\n"
				+ "                        }\r\n"
				+ "                    ]\r\n"
				+ "                }";
		Permission p = CatalogSerializer.deserializePlain(oString, Permission.class);
		p.getConstraint();
		p.getConstraint().add(Constraint.Builder.newInstance().leftOperand(LeftOperand.COUNT).operator(Operator.GT).rightOperand("5").build());
		p.getConstraint();
	}
	
	@Test
	public void equalsTestPlain() {
		Offer offer = CatalogMockObjectUtil.OFFER;
		String ss = CatalogSerializer.serializePlain(offer);
		Offer offer2 = CatalogSerializer.deserializePlain(ss, Offer.class);
		assertThat(offer).usingRecursiveComparison().isEqualTo(offer2);
	}
	
	@Test
	public void equalsTestProtocol() {
		Offer offer = CatalogMockObjectUtil.OFFER;
		String ss = CatalogSerializer.serializeProtocol(offer);
		Offer offer2 = CatalogSerializer.deserializeProtocol(ss, Offer.class);
		assertThat(offer).usingRecursiveComparison().isEqualTo(offer2);
	}
	
}
