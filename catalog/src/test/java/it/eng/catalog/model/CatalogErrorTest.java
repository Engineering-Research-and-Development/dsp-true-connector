package it.eng.catalog.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class CatalogErrorTest {

    CatalogError catalogError = CatalogError.Builder.newInstance()
            .code("cat err code")
            .reason(Arrays.asList(
                    Reason.Builder.newInstance()
                            .language("en")
                            .value("Not correct")
                            .build(),
                    Reason.Builder.newInstance()
                            .language("it")
                            .value("same but in Italian")
                            .build()))
            .build();

    @Test
    @DisplayName("Verify valid plain object serialization")
    public void testPlain() {
        String result = CatalogSerializer.serializePlain(catalogError);
        assertFalse(result.contains(DSpaceConstants.CONTEXT));
        assertFalse(result.contains(DSpaceConstants.TYPE));
        assertTrue(result.contains(DSpaceConstants.CODE));
        assertTrue(result.contains(DSpaceConstants.REASON));

        CatalogError javaObj = CatalogSerializer.deserializePlain(result, CatalogError.class);
        validateJavaObj(javaObj);
    }

    @Test
    @DisplayName("Verify valid protocol object serialization")
    public void testPlain_protocol() {
        JsonNode result = CatalogSerializer.serializeProtocolJsonNode(catalogError);
        JsonNode context = result.get(DSpaceConstants.CONTEXT);
        assertNotNull(context);
        if (context.isArray()) {
            ArrayNode arrayNode = (ArrayNode) context;
            assertFalse(arrayNode.isEmpty());
            assertEquals(DSpaceConstants.DSPACE_2025_01_CONTEXT, arrayNode.get(0).asText());
        }

        assertNotNull(result.get(DSpaceConstants.TYPE).asText());
        assertNotNull(result.get(DSpaceConstants.CODE).asText());
        assertNotNull(result.get(DSpaceConstants.REASON).asText());

        CatalogError javaObj = CatalogSerializer.deserializeProtocol(result, CatalogError.class);
        validateJavaObj(javaObj);
    }

    @Test
    @DisplayName("Missing @context and @type")
    public void missingContextAndType() {
        JsonNode result = CatalogSerializer.serializePlainJsonNode(catalogError);
        assertThrows(ValidationException.class, () -> CatalogSerializer.deserializeProtocol(result, CatalogError.class));
    }

    @Test
    @DisplayName("no required fields")
    public void validateInvalid() {
        assertDoesNotThrow(() -> CatalogError.Builder.newInstance()
                .build());
    }

    @Test
    @DisplayName("Plain serialize/deserialize")
    public void equalsTestPlain() {
        String ss = CatalogSerializer.serializePlain(catalogError);
        CatalogError catalogError2 = CatalogSerializer.deserializePlain(ss, CatalogError.class);
        assertThat(catalogError).usingRecursiveComparison().isEqualTo(catalogError2);
    }

    @Test
    @DisplayName("Protocol serialize/deserialize")
    public void equalsTestProtocol() {
        String ss = CatalogSerializer.serializeProtocol(catalogError);
        CatalogError catalogError2 = CatalogSerializer.deserializeProtocol(ss, CatalogError.class);
        assertThat(catalogError).usingRecursiveComparison().isEqualTo(catalogError2);
    }

    private void validateJavaObj(CatalogError javaObj) {
        assertNotNull(javaObj);
        assertNotNull(javaObj.getCode());
        // must be exact one in array
        assertNotNull(javaObj.getReason().get(0));
    }
}
