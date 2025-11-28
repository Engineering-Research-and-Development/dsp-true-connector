package it.eng.negotiation.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ContractRequestMessageTest {

    @Test
    @DisplayName("Verify valid plain object serialization  - initial ContractRequestMessage")
    public void testPlain_initial() {
        String result = NegotiationSerializer.serializePlain(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_INITIAL);
        assertFalse(result.contains(DSpaceConstants.CONTEXT));
        assertFalse(result.contains(DSpaceConstants.TYPE));
        assertTrue(result.contains(DSpaceConstants.ID));
        assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
        assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
        assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));
        assertTrue(result.contains(DSpaceConstants.OFFER));
        assertTrue(result.contains(DSpaceConstants.ASSIGNEE));
        assertTrue(result.contains(DSpaceConstants.ASSIGNER));
        assertTrue(result.contains(DSpaceConstants.TARGET));
        assertTrue(result.contains(DSpaceConstants.PERMISSION));
        assertTrue(result.contains(DSpaceConstants.ACTION));
        assertTrue(result.contains(DSpaceConstants.CONSTRAINT));
        assertTrue(result.contains(DSpaceConstants.LEFT_OPERAND));
        assertTrue(result.contains(DSpaceConstants.OPERATOR));
        assertTrue(result.contains(DSpaceConstants.RIGHT_OPERAND));

        ContractRequestMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractRequestMessage.class);
        assertEquals(NegotiationMockObjectUtil.CONSUMER_PID, javaObj.getConsumerPid());
        assertEquals(NegotiationMockObjectUtil.CALLBACK_ADDRESS, javaObj.getCallbackAddress());
        assertEquals(NegotiationMockObjectUtil.OFFER.getAssignee(), javaObj.getOffer().getAssignee());
        assertEquals(NegotiationMockObjectUtil.OFFER.getAssigner(), javaObj.getOffer().getAssigner());
        assertEquals(NegotiationMockObjectUtil.OFFER.getTarget(), javaObj.getOffer().getTarget());
        assertEquals(NegotiationMockObjectUtil.PERMISSION.getAction(), javaObj.getOffer().getPermission().get(0).getAction());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getLeftOperand(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getLeftOperand());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getOperator(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getOperator());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getRightOperand(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getRightOperand());
    }

    @Test
    @DisplayName("Verify valid plain object serialization - counteroffer ContractRequestMessage")
    public void testPlain_counteroffer() {
        String result = NegotiationSerializer.serializePlain(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_COUNTEROFFER);
        assertFalse(result.contains(DSpaceConstants.CONTEXT));
        assertFalse(result.contains(DSpaceConstants.TYPE));
        assertTrue(result.contains(DSpaceConstants.ID));
        assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
        assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
        assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));
        assertTrue(result.contains(DSpaceConstants.OFFER));
        assertTrue(result.contains(DSpaceConstants.ASSIGNEE));
        assertTrue(result.contains(DSpaceConstants.ASSIGNER));
        assertTrue(result.contains(DSpaceConstants.TARGET));
        assertTrue(result.contains(DSpaceConstants.PERMISSION));
        assertTrue(result.contains(DSpaceConstants.ACTION));
        assertTrue(result.contains(DSpaceConstants.CONSTRAINT));
        assertTrue(result.contains(DSpaceConstants.LEFT_OPERAND));
        assertTrue(result.contains(DSpaceConstants.OPERATOR));
        assertTrue(result.contains(DSpaceConstants.RIGHT_OPERAND));

        ContractRequestMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractRequestMessage.class);
        assertEquals(NegotiationMockObjectUtil.CONSUMER_PID, javaObj.getConsumerPid());
        assertEquals(NegotiationMockObjectUtil.PROVIDER_PID, javaObj.getProviderPid());
        assertEquals(NegotiationMockObjectUtil.OFFER.getAssignee(), javaObj.getOffer().getAssignee());
        assertEquals(NegotiationMockObjectUtil.OFFER.getAssigner(), javaObj.getOffer().getAssigner());
        assertEquals(NegotiationMockObjectUtil.OFFER.getTarget(), javaObj.getOffer().getTarget());
        assertEquals(NegotiationMockObjectUtil.PERMISSION.getAction(), javaObj.getOffer().getPermission().get(0).getAction());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getLeftOperand(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getLeftOperand());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getOperator(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getOperator());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getRightOperand(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getRightOperand());
    }

    @Test
    @DisplayName("Verify valid protocol object serialization")
    public void testProtocol_initial() {
        JsonNode result = NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_INITIAL);
        JsonNode context = result.get(DSpaceConstants.CONTEXT);
        assertNotNull(context);
        if (context.isArray()) {
            ArrayNode arrayNode = (ArrayNode) context;
            assertFalse(arrayNode.isEmpty());
            assertEquals(DSpaceConstants.DSPACE_2025_01_CONTEXT, arrayNode.get(0).asText());
        }
        assertEquals(result.get(DSpaceConstants.TYPE).asText(), NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_INITIAL.getType());
        assertEquals(result.get(DSpaceConstants.CONSUMER_PID).asText(), NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_INITIAL.getConsumerPid());
        assertEquals(result.get(DSpaceConstants.CALLBACK_ADDRESS).asText(), NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_INITIAL.getCallbackAddress());

        validateOfferProtocol(result.get(DSpaceConstants.OFFER));

        ContractRequestMessage javaObj = NegotiationSerializer.deserializeProtocol(result, ContractRequestMessage.class);
        assertEquals(NegotiationMockObjectUtil.CONSUMER_PID, javaObj.getConsumerPid());
        assertEquals(NegotiationMockObjectUtil.CALLBACK_ADDRESS, javaObj.getCallbackAddress());
        assertEquals(NegotiationMockObjectUtil.OFFER.getAssignee(), javaObj.getOffer().getAssignee());
        assertEquals(NegotiationMockObjectUtil.OFFER.getAssigner(), javaObj.getOffer().getAssigner());
        assertEquals(NegotiationMockObjectUtil.OFFER.getTarget(), javaObj.getOffer().getTarget());
        assertEquals(NegotiationMockObjectUtil.PERMISSION.getAction(), javaObj.getOffer().getPermission().get(0).getAction());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getLeftOperand(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getLeftOperand());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getOperator(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getOperator());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getRightOperand(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getRightOperand());
    }

    @Test
    @DisplayName("Verify valid protocol object serialization")
    public void testProtocol_counteroffer() {
        JsonNode result = NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_COUNTEROFFER);
        JsonNode context = result.get(DSpaceConstants.CONTEXT);
        assertNotNull(context);
        if (context.isArray()) {
            ArrayNode arrayNode = (ArrayNode) context;
            assertFalse(arrayNode.isEmpty());
            assertEquals(DSpaceConstants.DSPACE_2025_01_CONTEXT, arrayNode.get(0).asText());
        }
        assertEquals(result.get(DSpaceConstants.TYPE).asText(), NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_COUNTEROFFER.getType());
        assertEquals(result.get(DSpaceConstants.CONSUMER_PID).asText(), NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_COUNTEROFFER.getConsumerPid());
        assertEquals(result.get(DSpaceConstants.PROVIDER_PID).asText(), NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_COUNTEROFFER.getProviderPid());

        validateOfferProtocol(result.get(DSpaceConstants.OFFER));

        ContractRequestMessage javaObj = NegotiationSerializer.deserializeProtocol(result, ContractRequestMessage.class);
        assertEquals(NegotiationMockObjectUtil.CONSUMER_PID, javaObj.getConsumerPid());
        assertEquals(NegotiationMockObjectUtil.PROVIDER_PID, javaObj.getProviderPid());
        assertEquals(NegotiationMockObjectUtil.OFFER.getAssignee(), javaObj.getOffer().getAssignee());
        assertEquals(NegotiationMockObjectUtil.OFFER.getAssigner(), javaObj.getOffer().getAssigner());
        assertEquals(NegotiationMockObjectUtil.OFFER.getTarget(), javaObj.getOffer().getTarget());
        assertEquals(NegotiationMockObjectUtil.PERMISSION.getAction(), javaObj.getOffer().getPermission().get(0).getAction());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getLeftOperand(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getLeftOperand());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getOperator(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getOperator());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getRightOperand(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getRightOperand());
    }

    @Test
    @DisplayName("No required fields")
    public void validateInvalid() {
        assertThrows(ValidationException.class,
                () -> ContractRequestMessage.Builder.newInstance()
                        .build());
    }

    @Test
    @DisplayName("Missing @context and @type")
    public void missingContextAndType() {
        JsonNode result = NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_INITIAL);
        assertThrows(ValidationException.class, () -> NegotiationSerializer.deserializeProtocol(result, ContractRequestMessage.class));
    }

    @Test
    @DisplayName("Plain serialize/deserialize")
    public void equalsTestPlain() {
        String ss = NegotiationSerializer.serializePlain(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_INITIAL);
        ContractRequestMessage obj = NegotiationSerializer.deserializePlain(ss, ContractRequestMessage.class);
        assertThat(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_INITIAL).usingRecursiveComparison().isEqualTo(obj);
    }

    @Test
    @DisplayName("Protocol serialize/deserialize")
    public void equalsTestProtocol() {
        String ss = NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_INITIAL);
        ContractRequestMessage obj = NegotiationSerializer.deserializeProtocol(ss, ContractRequestMessage.class);
        assertThat(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_INITIAL).usingRecursiveComparison().isEqualTo(obj);
    }

    private void validateOfferProtocol(JsonNode offer) {
        assertEquals(NegotiationMockObjectUtil.OFFER.getAssignee(), offer.get(DSpaceConstants.ASSIGNEE).asText());
        assertEquals(NegotiationMockObjectUtil.OFFER.getAssigner(), offer.get(DSpaceConstants.ASSIGNER).asText());
        assertEquals(NegotiationMockObjectUtil.OFFER.getTarget(), offer.get(DSpaceConstants.TARGET).asText());
        JsonNode permissionNode = offer.get(DSpaceConstants.PERMISSION).get(0);
        assertEquals(NegotiationMockObjectUtil.PERMISSION.getAction().toString(), permissionNode.get(DSpaceConstants.ACTION).asText());
        JsonNode constraintNode = permissionNode.get(DSpaceConstants.CONSTRAINT).get(0);
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getLeftOperand().toString(), constraintNode.get(DSpaceConstants.LEFT_OPERAND).asText());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getOperator().toString(), constraintNode.get(DSpaceConstants.OPERATOR).asText());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getRightOperand(), constraintNode.get(DSpaceConstants.RIGHT_OPERAND).asText());
    }

    private void validateJavaObj(ContractRequestMessage javaObj) {
        assertEquals(NegotiationMockObjectUtil.CONSUMER_PID, javaObj.getConsumerPid());
        assertEquals(NegotiationMockObjectUtil.PROVIDER_PID, javaObj.getProviderPid());
        assertEquals(NegotiationMockObjectUtil.CALLBACK_ADDRESS, javaObj.getCallbackAddress());
        assertEquals(NegotiationMockObjectUtil.OFFER.getAssignee(), javaObj.getOffer().getAssignee());
        assertEquals(NegotiationMockObjectUtil.OFFER.getAssigner(), javaObj.getOffer().getAssigner());
        assertEquals(NegotiationMockObjectUtil.OFFER.getTarget(), javaObj.getOffer().getTarget());
        assertEquals(NegotiationMockObjectUtil.PERMISSION.getAction(), javaObj.getOffer().getPermission().get(0).getAction());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getLeftOperand(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getLeftOperand());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getOperator(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getOperator());
        assertEquals(NegotiationMockObjectUtil.CONSTRAINT.getRightOperand(), javaObj.getOffer().getPermission().get(0).getConstraint().get(0).getRightOperand());
    }
}
