package it.eng.negotiation.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ContractNegotiationErrorMessageTest {

    private final ContractNegotiationErrorMessage contractNegotiationErrorMessage = ContractNegotiationErrorMessage.Builder
            .newInstance()
            .consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
            .providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
            .code("Negotiation error code 123")
            .reason(Collections.singletonList("reason text goes here"))
            .build();

    @Test
    @DisplayName("Verify valid plain object serialization")
    public void testPlain() {
        String result = NegotiationSerializer.serializePlain(contractNegotiationErrorMessage);
        assertFalse(result.contains(DSpaceConstants.CONTEXT));
        assertFalse(result.contains(DSpaceConstants.TYPE));
        assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
        assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
        assertTrue(result.contains(DSpaceConstants.CODE));
        assertTrue(result.contains(DSpaceConstants.REASON));


        ContractNegotiationErrorMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractNegotiationErrorMessage.class);
        validateJavaObj(javaObj);
    }

    @Test
    @DisplayName("Verify valid protocol object serialization")
    public void testPlain_protocol() {
        JsonNode result = NegotiationSerializer.serializeProtocolJsonNode(contractNegotiationErrorMessage);
        JsonNode context = result.get(DSpaceConstants.CONTEXT);
        assertNotNull(context);
        if (context.isArray()) {
            ArrayNode arrayNode = (ArrayNode) context;
            assertFalse(arrayNode.isEmpty());
            assertEquals(DSpaceConstants.DSPACE_2025_01_CONTEXT, arrayNode.get(0).asText());
        }
        assertEquals(result.get(DSpaceConstants.TYPE).asText(), contractNegotiationErrorMessage.getType());
        assertEquals(result.get(DSpaceConstants.CONSUMER_PID).asText(), contractNegotiationErrorMessage.getConsumerPid());
        assertEquals(result.get(DSpaceConstants.PROVIDER_PID).asText(), contractNegotiationErrorMessage.getProviderPid());
        assertEquals(result.get(DSpaceConstants.CODE).asText(), contractNegotiationErrorMessage.getCode());
        assertEquals(result.get(DSpaceConstants.REASON).get(0).asText(), contractNegotiationErrorMessage.getReason().get(0));

        ContractNegotiationErrorMessage javaObj = NegotiationSerializer.deserializeProtocol(result, ContractNegotiationErrorMessage.class);
        validateJavaObj(javaObj);
    }

    @Test
    @DisplayName("No required fields")
    public void validateInvalid() {
        assertThrows(ValidationException.class,
                () -> ContractNegotiationErrorMessage.Builder.newInstance()
                        .build());
    }

    @Test
    @DisplayName("Missing @context and @type")
    public void missingContextAndType() {
        JsonNode result = NegotiationSerializer.serializePlainJsonNode(contractNegotiationErrorMessage);
        assertThrows(ValidationException.class, () -> NegotiationSerializer.deserializeProtocol(result, ContractNegotiationErrorMessage.class));
    }

    @Test
    @DisplayName("Plain serialize/deserialize")
    public void equalsTestPlain() {
        String ss = NegotiationSerializer.serializePlain(contractNegotiationErrorMessage);
        ContractNegotiationErrorMessage obj = NegotiationSerializer.deserializePlain(ss, ContractNegotiationErrorMessage.class);
        assertThat(contractNegotiationErrorMessage).usingRecursiveComparison().isEqualTo(obj);
    }

    @Test
    @DisplayName("Protocol serialize/deserialize")
    public void equalsTestProtocol() {
        String ss = NegotiationSerializer.serializeProtocol(contractNegotiationErrorMessage);
        ContractNegotiationErrorMessage obj = NegotiationSerializer.deserializeProtocol(ss, ContractNegotiationErrorMessage.class);
        assertThat(contractNegotiationErrorMessage).usingRecursiveComparison().isEqualTo(obj);
    }

    private void validateJavaObj(ContractNegotiationErrorMessage javaObj) {
        assertEquals(contractNegotiationErrorMessage.getConsumerPid(), javaObj.getConsumerPid());
        assertEquals(contractNegotiationErrorMessage.getProviderPid(), javaObj.getProviderPid());
        assertEquals(contractNegotiationErrorMessage.getCode(), javaObj.getCode());
        assertNotNull(javaObj.getReason().get(0));
        // must be exactly one in the array
        assertEquals(1, javaObj.getReason().size());
        assertEquals(contractNegotiationErrorMessage.getReason().get(0), javaObj.getReason().get(0));
    }
}
