package it.eng.dcp.model;

import it.eng.tools.model.DSpaceConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaseDcpMessageTest {

    @Test
    @DisplayName("builder fails when type is missing")
    void builderFailsWhenTypeMissing() {
        TestMessage.Builder b = TestMessage.Builder.newInstance();
        b.payload("p");
        assertThrows(jakarta.validation.ValidationException.class, b::build);
    }

    @Test
    @DisplayName("builder succeeds when type provided and default context present")
    void builderSucceedsWithType() {
        TestMessage.Builder b = TestMessage.Builder.newInstance();
        b.type("TestType");
        b.payload("p");
        TestMessage m = b.build();
        assertEquals("TestType", m.getType());
        assertFalse(m.getContext().isEmpty());
        assertTrue(m.getContext().contains(DSpaceConstants.DCP_CONTEXT));
    }
}
