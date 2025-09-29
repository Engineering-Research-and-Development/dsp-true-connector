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

public class ContractOfferMessageTest {

    @Test
    @DisplayName("Verify valid plain object serialization")
    public void testPlain() {
        String result = NegotiationSerializer.serializePlain(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE);
        assertFalse(result.contains(DSpaceConstants.CONTEXT));
        assertFalse(result.contains(DSpaceConstants.TYPE));
        assertTrue(result.contains(DSpaceConstants.ID));
        assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
        assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
        assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));
        assertTrue(result.contains(DSpaceConstants.OFFER));
        ContractOfferMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractOfferMessage.class);
        validateJavaObj(javaObj);
    }

    @Test
    @DisplayName("Verify valid protocol object serialization")
    public void testProtocol() {
        JsonNode result = NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE);
        JsonNode context = result.get(DSpaceConstants.CONTEXT);
        assertNotNull(context);
        if (context.isArray()) {
            ArrayNode arrayNode = (ArrayNode) context;
            assertFalse(arrayNode.isEmpty());
            assertEquals(DSpaceConstants.DSPACE_2025_01_CONTEXT, arrayNode.get(0).asText());
        }
        assertEquals(result.get(DSpaceConstants.TYPE).asText(), NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE.getType());
        assertEquals(result.get(DSpaceConstants.CONSUMER_PID).asText(), NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE.getConsumerPid());
        assertEquals(result.get(DSpaceConstants.PROVIDER_PID).asText(), NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE.getProviderPid());
        assertEquals(result.get(DSpaceConstants.CALLBACK_ADDRESS).asText(), NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE.getCallbackAddress());

        JsonNode offer = result.get(DSpaceConstants.OFFER);
        assertNotNull(offer);
        validateOfferProtocol(offer);

        ContractOfferMessage javaObj = NegotiationSerializer.deserializeProtocol(result, ContractOfferMessage.class);
        validateJavaObj(javaObj);
    }

    @Test
    @DisplayName("No required fields")
    public void validateInvalid() {
        assertThrows(ValidationException.class,
                () -> ContractOfferMessage.Builder.newInstance()
                        .build());
    }

    @Test
    @DisplayName("Missing @context and @type")
    public void missingContextAndType() {
        JsonNode result = NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE);
        assertThrows(ValidationException.class, () -> NegotiationSerializer.deserializeProtocol(result, ContractOfferMessage.class));
    }

    @Test
    @DisplayName("Plain serialize/deserialize")
    public void equalsTestPlain() {
        ContractOfferMessage contractOfferMessage = NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE;
        String ss = NegotiationSerializer.serializePlain(contractOfferMessage);
        ContractOfferMessage obj = NegotiationSerializer.deserializePlain(ss, ContractOfferMessage.class);
        assertThat(contractOfferMessage).usingRecursiveComparison().isEqualTo(obj);
    }

    @Test
    @DisplayName("Protocol serialize/deserialize")
    public void equalsTestProtocol() {
        ContractOfferMessage contractOfferMessage = NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE;
        String ss = NegotiationSerializer.serializeProtocol(contractOfferMessage);
        ContractOfferMessage obj = NegotiationSerializer.deserializeProtocol(ss, ContractOfferMessage.class);
        assertThat(contractOfferMessage).usingRecursiveComparison().isEqualTo(obj);
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

    private void validateJavaObj(ContractOfferMessage javaObj) {
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
