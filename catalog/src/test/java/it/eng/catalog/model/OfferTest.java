package it.eng.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.util.MockObjectUtil;

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
			.permission(Set.of(MockObjectUtil.PERMISSION_ANONYMIZE))
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
		 Constraint constraint3 = Constraint.Builder.newInstance()
					.leftOperand(LeftOperand.DATE_TIME)
					.operator(Operator.GT)
					.rightOperand("2024-02-29T00:00:01+01:00")
					.build();
		 Constraint constraint4 = Constraint.Builder.newInstance()
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
				.constraint(Set.of(constraint3, constraint4))
				.build();
		Permission permission3 = Permission.Builder.newInstance()
				.action(Action.USE)
				.target(TARGET_A)
				.constraint(Set.of(constraint1, constraint2))
				.build();
		Permission permission4 = Permission.Builder.newInstance()
				.action(Action.ANONYMIZE)
				.target(TARGET_A)
				.constraint(Set.of(constraint3, constraint4))
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
				.permission(Set.of(permission3, permission4))
				.build();
		assertThat(offerA).isEqualTo(offerB);
		
		//check plain serialization
		String plain = Serializer.serializePlain(offerA);
		Offer offerPlain = Serializer.deserializePlain(plain, Offer.class);
		assertThat(offerPlain).isEqualTo(offerB);
		
		//check protocol serialization
		String protocol = Serializer.serializeProtocol(offerA);
		Offer offerProtocol = Serializer.deserializeProtocol(protocol, Offer.class);
		assertThat(offerProtocol).isEqualTo(offerB);
	}
	
	@Test
	public void collection() {
		Set<Permission> permissions = new HashSet<>();
		permissions.add(MockObjectUtil.PERMISSION);
		Offer o = Offer.Builder.newInstance()
				.assignee(ASSIGNEE)
				.assigner(ASSIGNER)
				.permission(permissions)
				.build();
		
		o.getPermission().add(MockObjectUtil.PERMISSION_ANONYMIZE);
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
		Permission p = Serializer.deserializePlain(oString, Permission.class);
		p.getConstraint();
		p.getConstraint().add(Constraint.Builder.newInstance().leftOperand(LeftOperand.ABSOLUTE_POSITION).operator(Operator.GT).rightOperand("DesniOperand").build());
		p.getConstraint();
	}
	
	@Test
	public void equalsTestPlain() {
		Offer offer = MockObjectUtil.OFFER;
		String ss = Serializer.serializePlain(offer);
		Offer offer2 = Serializer.deserializePlain(ss, Offer.class);
		assertThat(offer).isEqualTo(offer2);
	}
	
	@Test
	public void equalsTestProtocol() {
		Offer offer = MockObjectUtil.OFFER;
		String ss = Serializer.serializeProtocol(offer);
		Offer offer2 = Serializer.deserializeProtocol(ss, Offer.class);
		assertThat(offer).isEqualTo(offer2);
	}
	
	@Test
	public void aaaaa() {
		String str = "{\r\n"
				+ "  \"id\" : \"urn:offer_id\",\r\n"
				+ "  \"target\" : null,\r\n"
				+ "  \"assigner\" : \"assigner\",\r\n"
				+ "  \"assignee\" : \"assignee\",\r\n"
				+ "  \"permission\" : [ {\r\n"
				+ "    \"assigner\" : null,\r\n"
				+ "    \"assignee\" : null,\r\n"
				+ "    \"target\" : null,\r\n"
				+ "    \"action\" : \"USE\",\r\n"
				+ "    \"constraint\" : [ {\r\n"
				+ "      \"leftOperand\" : \"ABSOLUTE_POSITION\",\r\n"
				+ "      \"operator\" : \"EQ\",\r\n"
				+ "      \"rightOperand\" : \"EU\"\r\n"
				+ "    } ]\r\n"
				+ "  } ]\r\n"
				+ "}";
		Offer off = Serializer.deserializePlain(str, Offer.class);
		assertNotNull(off);
	}
}
