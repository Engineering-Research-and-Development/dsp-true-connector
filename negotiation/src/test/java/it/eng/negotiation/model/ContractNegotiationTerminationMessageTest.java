package it.eng.negotiation.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ContractNegotiationTerminationMessageTest {

    private final ContractNegotiationTerminationMessage contractNegotiationTerminationMessage = ContractNegotiationTerminationMessage.Builder
            .newInstance()
            .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
            .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
            .code("Termination CD_123")
            .reason(Arrays.asList("Hello", "World"))
            .build();

    @Test
    @DisplayName("Verify valid plain object serialization")
    public void testPlain() {
        String result = NegotiationSerializer.serializePlain(contractNegotiationTerminationMessage);
        assertFalse(result.contains(DSpaceConstants.CONTEXT));
        assertFalse(result.contains(DSpaceConstants.TYPE));
        assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
        assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
        assertTrue(result.contains(DSpaceConstants.CODE));
        assertTrue(result.contains(DSpaceConstants.REASON));

        ContractNegotiationTerminationMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractNegotiationTerminationMessage.class);
        validateJavaObj(javaObj);
    }

    @Test
    @DisplayName("Verify valid protocol object serialization")
    public void testProtocol() {
        JsonNode result = NegotiationSerializer.serializeProtocolJsonNode(contractNegotiationTerminationMessage);
        JsonNode context = result.get(DSpaceConstants.CONTEXT);
        assertNotNull(context);
        if (context.isArray()) {
            ArrayNode arrayNode = (ArrayNode) context;
            assertFalse(arrayNode.isEmpty());
            assertEquals(DSpaceConstants.DSPACE_2025_01_CONTEXT, arrayNode.get(0).asText());
        }
        assertEquals(result.get(DSpaceConstants.TYPE).asText(), contractNegotiationTerminationMessage.getType());
        assertEquals(result.get(DSpaceConstants.CONSUMER_PID).asText(), contractNegotiationTerminationMessage.getConsumerPid());
        assertEquals(result.get(DSpaceConstants.PROVIDER_PID).asText(), contractNegotiationTerminationMessage.getProviderPid());
        assertEquals(result.get(DSpaceConstants.CODE).asText(), contractNegotiationTerminationMessage.getCode());
        assertEquals(result.get(DSpaceConstants.REASON).size(), 2);
        assertEquals(result.get(DSpaceConstants.REASON).get(0).asText(), "Hello");
        assertEquals(result.get(DSpaceConstants.REASON).get(1).asText(), "World");

        ContractNegotiationTerminationMessage javaObj = NegotiationSerializer.deserializeProtocol(result, ContractNegotiationTerminationMessage.class);
        validateJavaObj(javaObj);
    }

    @Test
    @DisplayName("No required fields")
    public void validateInvalid() {
        assertThrows(ValidationException.class,
                () -> ContractNegotiationTerminationMessage.Builder.newInstance()
                        .build());
    }

    @Test
    @DisplayName("Missing @context and @type")
    public void missingContextAndType() {
        JsonNode result = NegotiationSerializer.serializePlainJsonNode(contractNegotiationTerminationMessage);
        assertThrows(ValidationException.class, () -> NegotiationSerializer.deserializeProtocol(result, ContractNegotiationTerminationMessage.class));
    }

    @Test
    @DisplayName("Plain serialize/deserialize")
    public void equalsTestPlain() {
        String ss = NegotiationSerializer.serializePlain(contractNegotiationTerminationMessage);
        ContractNegotiationTerminationMessage obj = NegotiationSerializer.deserializePlain(ss, ContractNegotiationTerminationMessage.class);
        assertThat(contractNegotiationTerminationMessage).usingRecursiveComparison().isEqualTo(obj);
    }

    @Test
    @DisplayName("Protocol serialize/deserialize")
    public void equalsTestProtocol() {
        String ss = NegotiationSerializer.serializeProtocol(contractNegotiationTerminationMessage);
        ContractNegotiationTerminationMessage obj = NegotiationSerializer.deserializeProtocol(ss, ContractNegotiationTerminationMessage.class);
        assertThat(contractNegotiationTerminationMessage).usingRecursiveComparison().isEqualTo(obj);
    }

    private void validateJavaObj(ContractNegotiationTerminationMessage javaObj) {
        assertEquals(contractNegotiationTerminationMessage.getConsumerPid(), javaObj.getConsumerPid());
        assertEquals(contractNegotiationTerminationMessage.getProviderPid(), javaObj.getProviderPid());
        assertEquals(contractNegotiationTerminationMessage.getCode(), javaObj.getCode());
        assertEquals(contractNegotiationTerminationMessage.getReason(), javaObj.getReason());
        // must be 2 reasons
        assertEquals(2, javaObj.getReason().size());
    }
}
