package it.eng.dcp.common.model;

import it.eng.tools.model.DSpaceConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaseDcpMessageTest {

    @Test
    @DisplayName("builder fails when type is missing")
    void builderFailsWhenTypeMissing() {
        // Using a concrete subclass for testing the abstract BaseDcpMessage
        // Note: TestMessage is in dcp module, so we need a simple test implementation here
        assertThrows(jakarta.validation.ValidationException.class, () -> {
            // This test validates that subclasses must provide a non-null type
            new BaseDcpMessage() {
                @Override
                public String getType() {
                    return null; // Invalid - should trigger validation
                }
            }.validateBase();
        });
    }

    @Test
    @DisplayName("validateBase succeeds when type provided and default context present")
    void validateBaseSucceedsWithType() {
        BaseDcpMessage message = new BaseDcpMessage() {
            @Override
            public String getType() {
                return "TestType";
            }
        };
        
        message.getContext().add(DSpaceConstants.DCP_CONTEXT);
        
        // Should not throw exception
        assertDoesNotThrow(() -> message.validateBase());
        
        assertEquals("TestType", message.getType());
        assertFalse(message.getContext().isEmpty());
        assertTrue(message.getContext().contains(DSpaceConstants.DCP_CONTEXT));
    }
}

