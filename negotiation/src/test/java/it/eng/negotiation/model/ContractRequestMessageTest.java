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

    private final ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
            .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
            .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
            .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
            .offer(NegotiationMockObjectUtil.OFFER)
            .build();

    @Test
    @DisplayName("Verify valid plain object serialization")
    public void testPlain() {
        String result = NegotiationSerializer.serializePlain(contractRequestMessage);
        assertFalse(result.contains(DSpaceConstants.CONTEXT));
        assertFalse(result.contains(DSpaceConstants.TYPE));
        assertTrue(result.contains(DSpaceConstants.ID));
        assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
        assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
        assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));

        ContractRequestMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractRequestMessage.class);
        validateJavaObj(javaObj);
    }

    @Test
    @DisplayName("Verify valid plain object serialization - initial ContractRequestMessage")
    public void testContractRequest_consumer() {
        ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .offer(NegotiationMockObjectUtil.OFFER)
                .build();
        String result = NegotiationSerializer.serializePlain(contractRequestMessage);
        assertFalse(result.contains(DSpaceConstants.CONTEXT));
        assertFalse(result.contains(DSpaceConstants.TYPE));
//        assertTrue(result.contains(DSpaceConstants.ID));
        assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
        assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
        assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));
    }

    @Test
    @DisplayName("Verify valid plain object serialization - contains offer")
    public void testPlain_offer() {
        ContractRequestMessage contractRequestMessageOffer = ContractRequestMessage.Builder.newInstance()
                .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
                .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
                .callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
                .offer(NegotiationMockObjectUtil.OFFER)
                .build();
        String result = NegotiationSerializer.serializePlain(contractRequestMessageOffer);
        assertFalse(result.contains(DSpaceConstants.CONTEXT));
        assertFalse(result.contains(DSpaceConstants.TYPE));
        assertTrue(result.contains(DSpaceConstants.ID));
        assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
        assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
        assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));

        ContractRequestMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractRequestMessage.class);
        validateJavaObj(javaObj);
        assertNotNull(javaObj.getOffer().getId());
        assertNotNull(javaObj.getOffer().getTarget());
    }

    @Test
    @DisplayName("Verify valid protocol object serialization")
    public void testProtocol() {
        JsonNode result = NegotiationSerializer.serializeProtocolJsonNode(contractRequestMessage);
        JsonNode context = result.get(DSpaceConstants.CONTEXT);
        assertNotNull(context);
        if (context.isArray()) {
            ArrayNode arrayNode = (ArrayNode) context;
            assertFalse(arrayNode.isEmpty());
            assertEquals(DSpaceConstants.DSPACE_2025_01_CONTEXT, arrayNode.get(0).asText());
        }
        assertEquals(result.get(DSpaceConstants.TYPE).asText(), contractRequestMessage.getType());
        assertEquals(result.get(DSpaceConstants.CONSUMER_PID).asText(), contractRequestMessage.getConsumerPid());
        assertEquals(result.get(DSpaceConstants.PROVIDER_PID).asText(), contractRequestMessage.getProviderPid());
        assertEquals(result.get(DSpaceConstants.CALLBACK_ADDRESS).asText(), contractRequestMessage.getCallbackAddress());

        validateOfferProtocol(result.get(DSpaceConstants.OFFER));

        ContractRequestMessage javaObj = NegotiationSerializer.deserializeProtocol(result, ContractRequestMessage.class);
        validateJavaObj(javaObj);
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
        JsonNode result = NegotiationSerializer.serializePlainJsonNode(contractRequestMessage);
        assertThrows(ValidationException.class, () -> NegotiationSerializer.deserializeProtocol(result, ContractRequestMessage.class));
    }

    @Test
    @DisplayName("Plain serialize/deserialize")
    public void equalsTestPlain() {
        String ss = NegotiationSerializer.serializePlain(contractRequestMessage);
        ContractRequestMessage obj = NegotiationSerializer.deserializePlain(ss, ContractRequestMessage.class);
        assertThat(contractRequestMessage).usingRecursiveComparison().isEqualTo(obj);
    }

    @Test
    @DisplayName("Protocol serialize/deserialize")
    public void equalsTestProtocol() {
        String ss = NegotiationSerializer.serializeProtocol(contractRequestMessage);
        ContractRequestMessage obj = NegotiationSerializer.deserializeProtocol(ss, ContractRequestMessage.class);
        assertThat(contractRequestMessage).usingRecursiveComparison().isEqualTo(obj);
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
