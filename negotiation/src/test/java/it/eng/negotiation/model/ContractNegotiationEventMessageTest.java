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

public class ContractNegotiationEventMessageTest {

    private final ContractNegotiationEventMessage contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
            .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
            .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
            .eventType(ContractNegotiationEventType.ACCEPTED)
            .build();

    @Test
    @DisplayName("Verify valid plain object serialization")
    public void testPlain() {
        String result = NegotiationSerializer.serializePlain(contractNegotiationEventMessage);
        assertFalse(result.contains(DSpaceConstants.CONTEXT));
        assertFalse(result.contains(DSpaceConstants.TYPE));
        assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
        assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
        assertTrue(result.contains(DSpaceConstants.EVENT_TYPE));

        ContractNegotiationEventMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractNegotiationEventMessage.class);
        validateJavaObj(javaObj);
    }

    @Test
    @DisplayName("Verify valid protocol object serialization")
    public void testProtocol() {
        JsonNode result = NegotiationSerializer.serializeProtocolJsonNode(contractNegotiationEventMessage);
        JsonNode context = result.get(DSpaceConstants.CONTEXT);
        assertNotNull(context);
        if (context.isArray()) {
            ArrayNode arrayNode = (ArrayNode) context;
            assertFalse(arrayNode.isEmpty());
            assertEquals(DSpaceConstants.DSPACE_2025_01_CONTEXT, arrayNode.get(0).asText());
        }
        assertEquals(result.get(DSpaceConstants.TYPE).asText(), contractNegotiationEventMessage.getType());
        assertEquals(result.get(DSpaceConstants.CONSUMER_PID).asText(), contractNegotiationEventMessage.getConsumerPid());
        assertEquals(result.get(DSpaceConstants.PROVIDER_PID).asText(), contractNegotiationEventMessage.getProviderPid());
        assertEquals(result.get(DSpaceConstants.EVENT_TYPE).asText(), contractNegotiationEventMessage.getEventType().name());

        ContractNegotiationEventMessage javaObj = NegotiationSerializer.deserializeProtocol(result, ContractNegotiationEventMessage.class);
        validateJavaObj(javaObj);
    }

    @Test
    @DisplayName("No required fields")
    public void validateInvalid() {
        assertThrows(ValidationException.class,
                () -> ContractNegotiationEventMessage.Builder.newInstance()
                        .build());
    }

    @Test
    @DisplayName("Missing @context and @type")
    public void missingContextAndType() {
        JsonNode result = NegotiationSerializer.serializePlainJsonNode(contractNegotiationEventMessage);
        assertThrows(ValidationException.class, () -> NegotiationSerializer.deserializeProtocol(result, ContractNegotiationEventMessage.class));
    }

    @Test
    @DisplayName("Plain serialize/deserialize")
    public void equalsTestPlain() {
        String ss = NegotiationSerializer.serializePlain(contractNegotiationEventMessage);
        ContractNegotiationEventMessage obj = NegotiationSerializer.deserializePlain(ss, ContractNegotiationEventMessage.class);
        assertThat(contractNegotiationEventMessage).usingRecursiveComparison().isEqualTo(obj);
    }

    @Test
    @DisplayName("Protocol serialize/deserialize")
    public void equalsTestProtocol() {
        String ss = NegotiationSerializer.serializeProtocol(contractNegotiationEventMessage);
        ContractNegotiationEventMessage obj = NegotiationSerializer.deserializeProtocol(ss, ContractNegotiationEventMessage.class);
        assertThat(contractNegotiationEventMessage).usingRecursiveComparison().isEqualTo(obj);
    }

    private void validateJavaObj(ContractNegotiationEventMessage javaObj) {
        assertEquals(contractNegotiationEventMessage.getConsumerPid(), javaObj.getConsumerPid());
        assertEquals(contractNegotiationEventMessage.getProviderPid(), javaObj.getProviderPid());
        assertEquals(contractNegotiationEventMessage.getEventType(), javaObj.getEventType());
    }

}
