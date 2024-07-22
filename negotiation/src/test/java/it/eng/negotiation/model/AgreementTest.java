package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.serializer.Serializer;
import jakarta.validation.ValidationException;

public class AgreementTest {

	Agreement agreement = Agreement.Builder.newInstance()
			.id(MockObjectUtil.generateUUID())
			.assignee(MockObjectUtil.ASSIGNEE)
			.assigner(MockObjectUtil.ASSIGNER)
			.target(MockObjectUtil.TARGET)
			.permission(Arrays.asList(Permission.Builder.newInstance()
					.action(Action.USE)
					.constraint(Arrays.asList(Constraint.Builder.newInstance()
							.leftOperand(LeftOperand.COUNT)
							.operator(Operator.EQ)
							.rightOperand("5")
							.build()))
					.build()))
			.build();

	@Test
	@DisplayName("Valid agreement")
	public void validAgreement() {
		assertNotNull(agreement, "Agreement should be created with all required fields");
	}
	
	@Test
	@DisplayName("No required fields")
	public void invalidAgreement() {
	assertThrows(ValidationException.class, () -> {
		Agreement.Builder.newInstance()
				.id(MockObjectUtil.generateUUID())
				.build();
		});
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = Serializer.serializePlainJsonNode(agreement);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, ContractAgreementMessage.class));
	}
	
}
