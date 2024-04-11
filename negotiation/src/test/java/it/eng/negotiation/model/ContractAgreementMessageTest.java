package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractAgreementMessageTest {

	private Constraint constraint = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.COUNT)
			.operator(Operator.EQ)
			.rightOperand("5")
			.build();
	
	private Permission permission = Permission.Builder.newInstance()
			.action(Action.USE)
			.constraint(Arrays.asList(constraint))
			.build();
	
	private Agreement agreement = Agreement.Builder.newInstance()
			.assignee(ModelUtil.ASSIGNEE)
			.assigner(ModelUtil.ASSIGNER)
			.target(ModelUtil.TARGET)
			.timestamp(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX")
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now()))
			.permission(Arrays.asList(permission))
			.build();
	
	private ContractAgreementMessage contractAgreementMessage = ContractAgreementMessage.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
			.agreement(agreement)
			.build();
		
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = Serializer.serializePlain(contractAgreementMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertFalse(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.AGREEMENT));
		
		ContractAgreementMessage javaObj = Serializer.deserializePlain(result, ContractAgreementMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = Serializer.serializeProtocolJsonNode(contractAgreementMessage);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_AGREEMENT).asText());
		validateAgreementProtocol(result.get(DSpaceConstants.DSPACE_AGREEMENT));
		
		ContractAgreementMessage javaObj = Serializer.deserializeProtocol(result, ContractAgreementMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractAgreementMessage.Builder.newInstance()
					.consumerPid(ModelUtil.CONSUMER_PID)
					.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
					.agreement(agreement)
					.build());
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = Serializer.serializePlainJsonNode(contractAgreementMessage);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, ContractAgreementMessage.class));
	}
	
	private void validateAgreementProtocol(JsonNode agreement) {
		assertNotNull(agreement.get(DSpaceConstants.ODRL_ASSIGNEE).asText());
		assertNotNull(agreement.get(DSpaceConstants.ODRL_ASSIGNER).asText());
		JsonNode permission = agreement.get(DSpaceConstants.ODRL_PERMISSION).get(0);
		assertNotNull(permission.get(DSpaceConstants.ODRL_ACTION).asText());
		JsonNode constraint = permission.get(DSpaceConstants.ODRL_CONSTRAINT).get(0);
		assertNotNull(constraint.get(DSpaceConstants.ODRL_LEFT_OPERAND).asText());
		assertNotNull(constraint.get(DSpaceConstants.ODRL_OPERATOR).asText());
		assertNotNull(constraint.get(DSpaceConstants.ODRL_RIGHT_OPERAND).asText());
	}
	
	private void validateJavaObj(ContractAgreementMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getAgreement());
		
		validateAgreement(javaObj.getAgreement());
	}
	
	private void validateAgreement(Agreement agreement) {
		assertEquals(ModelUtil.ASSIGNEE, agreement.getAssignee());
		assertEquals(ModelUtil.ASSIGNER, agreement.getAssigner());
		assertEquals(ModelUtil.TARGET, agreement.getTarget());
		
		var permission = agreement.getPermission().get(0);
		assertNotNull(permission);
		assertEquals(Action.USE, permission.getAction());
		
		var constraint = permission.getConstraint().get(0);
		assertNotNull(constraint);
		assertEquals(LeftOperand.COUNT, constraint.getLeftOperand());
		assertEquals(Operator.EQ, constraint.getOperator());
		assertEquals("5", constraint.getRightOperand());
		
	}
}
