package it.eng.negotiation.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ContractAgreementMessageTest {

    private final Constraint constraint = Constraint.Builder.newInstance()
            .leftOperand(LeftOperand.COUNT)
            .operator(Operator.EQ)
            .rightOperand("5")
            .build();

    private final Permission permission = Permission.Builder.newInstance()
            .action(Action.USE)
            .constraint(Collections.singletonList(constraint))
            .build();

    private final Agreement agreement = Agreement.Builder.newInstance()
            .assignee(NegotiationMockObjectUtil.ASSIGNEE)
            .assigner(NegotiationMockObjectUtil.ASSIGNER)
            .target(NegotiationMockObjectUtil.TARGET)
            .timestamp(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX")
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now()))
            .permission(Collections.singletonList(permission))
            .build();

    private final ContractAgreementMessage contractAgreementMessage = ContractAgreementMessage.Builder.newInstance()
            .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
            .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
            .agreement(agreement)
            .build();

    @Test
    @DisplayName("Verify valid plain object serialization")
    public void testPlain() {
        String result = NegotiationSerializer.serializePlain(contractAgreementMessage);
        assertFalse(result.contains(DSpaceConstants.CONTEXT));
        assertFalse(result.contains(DSpaceConstants.TYPE));
        assertTrue(result.contains(DSpaceConstants.ID));
        assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
        assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
        assertTrue(result.contains(DSpaceConstants.AGREEMENT));

        ContractAgreementMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractAgreementMessage.class);
        validateJavaObj(javaObj);
    }

    @Test
    @DisplayName("Verify valid protocol object serialization")
    public void testProtocol() {
        JsonNode result = NegotiationSerializer.serializeProtocolJsonNode(contractAgreementMessage);
        JsonNode context = result.get(DSpaceConstants.CONTEXT);
        assertNotNull(context);
        if (context.isArray()) {
            ArrayNode arrayNode = (ArrayNode) context;
            assertFalse(arrayNode.isEmpty());
            assertEquals(DSpaceConstants.DSPACE_2025_01_CONTEXT, arrayNode.get(0).asText());
        }
        assertEquals(result.get(DSpaceConstants.TYPE).asText(), contractAgreementMessage.getType());
        assertEquals(result.get(DSpaceConstants.CONSUMER_PID).asText(), contractAgreementMessage.getConsumerPid());
        assertEquals(result.get(DSpaceConstants.PROVIDER_PID).asText(), contractAgreementMessage.getProviderPid());
        assertNotNull(result.get(DSpaceConstants.AGREEMENT).asText());
        validateAgreementProtocol(result.get(DSpaceConstants.AGREEMENT));

        ContractAgreementMessage javaObj = NegotiationSerializer.deserializeProtocol(result, ContractAgreementMessage.class);
        validateJavaObj(javaObj);
    }

    @Test
    @DisplayName("No required fields")
    public void validateInvalid() {
        assertThrows(ValidationException.class,
                () -> ContractAgreementMessage.Builder.newInstance()
                        .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                        .agreement(agreement)
                        .build());
    }

    @Test
    @DisplayName("Missing @context and @type")
    public void missingContextAndType() {
        JsonNode result = NegotiationSerializer.serializePlainJsonNode(contractAgreementMessage);
        assertThrows(ValidationException.class, () -> NegotiationSerializer.deserializeProtocol(result, ContractAgreementMessage.class));
    }

    @Test
    @DisplayName("Plain serialize/deserialize")
    public void equalsTestPlain() {
        String ss = NegotiationSerializer.serializePlain(contractAgreementMessage);
        ContractAgreementMessage obj = NegotiationSerializer.deserializePlain(ss, ContractAgreementMessage.class);
        assertThat(contractAgreementMessage).usingRecursiveComparison().isEqualTo(obj);
    }

    @Test
    @DisplayName("Protocol serialize/deserialize")
    public void equalsTestProtocol() {
        String ss = NegotiationSerializer.serializeProtocol(contractAgreementMessage);
        ContractAgreementMessage obj = NegotiationSerializer.deserializeProtocol(ss, ContractAgreementMessage.class);
        assertThat(contractAgreementMessage).usingRecursiveComparison().isEqualTo(obj);
    }

    private void validateAgreementProtocol(JsonNode agreementForTesting) {
        assertEquals(agreement.getAssignee(), agreementForTesting.get(DSpaceConstants.ASSIGNEE).asText());
        assertEquals(agreement.getAssigner(), agreementForTesting.get(DSpaceConstants.ASSIGNER).asText());
        assertEquals(agreement.getTarget(), agreementForTesting.get(DSpaceConstants.TARGET).asText());
        assertEquals(agreement.getTimestamp(), agreementForTesting.get(DSpaceConstants.TIMESTAMP).asText());
        JsonNode permissionNode = agreementForTesting.get(DSpaceConstants.PERMISSION).get(0);
        assertEquals(permission.getAction().toString(), permissionNode.get(DSpaceConstants.ACTION).asText());
        JsonNode constraintNode = permissionNode.get(DSpaceConstants.CONSTRAINT).get(0);
        assertEquals(constraint.getLeftOperand().toString(), constraintNode.get(DSpaceConstants.LEFT_OPERAND).asText());
        assertEquals(constraint.getOperator().toString(), constraintNode.get(DSpaceConstants.OPERATOR).asText());
        assertEquals(constraint.getRightOperand(), constraintNode.get(DSpaceConstants.RIGHT_OPERAND).asText());
    }

    private void validateJavaObj(ContractAgreementMessage javaObj) {
        assertEquals(contractAgreementMessage.getConsumerPid(), javaObj.getConsumerPid());
        assertEquals(contractAgreementMessage.getProviderPid(), javaObj.getProviderPid());

        validateAgreement(javaObj.getAgreement());
    }

    private void validateAgreement(Agreement agreementForTesting) {
        assertEquals(NegotiationMockObjectUtil.ASSIGNEE, agreementForTesting.getAssignee());
        assertEquals(NegotiationMockObjectUtil.ASSIGNER, agreementForTesting.getAssigner());
        assertEquals(NegotiationMockObjectUtil.TARGET, agreementForTesting.getTarget());

        // compare fetched permission/constraint against the class-level fixtures (avoid shadowing)
        Permission fetchedPermission = agreementForTesting.getPermission().get(0);
        assertEquals(permission.getAction(), fetchedPermission.getAction());

        Constraint fetchedConstraint = fetchedPermission.getConstraint().get(0);
        assertEquals(constraint.getLeftOperand(), fetchedConstraint.getLeftOperand());
        assertEquals(constraint.getOperator(), fetchedConstraint.getOperator());
        assertEquals(constraint.getRightOperand(), fetchedConstraint.getRightOperand());

        assertEquals(agreement.getAssignee(), agreementForTesting.getAssignee());
        assertEquals(agreement.getAssigner(), agreementForTesting.getAssigner());
        assertEquals(agreement.getTarget(), agreementForTesting.getTarget());
        assertEquals(agreement.getTimestamp(), agreementForTesting.getTimestamp());
        Permission permissionNode = agreementForTesting.getPermission().get(0);
        assertEquals(permission.getAction(), permissionNode.getAction());
        Constraint constraintNode = permissionNode.getConstraint().get(0);
        assertEquals(constraint.getLeftOperand(), constraintNode.getLeftOperand());
        assertEquals(constraint.getOperator(), constraintNode.getOperator());
        assertEquals(constraint.getRightOperand(), constraintNode.getRightOperand());

    }
}
