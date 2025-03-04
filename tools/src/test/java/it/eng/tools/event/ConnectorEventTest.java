package it.eng.tools.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ConnectorEventTest {
    
    @Test
    @DisplayName("Test ConnectorEvent implementation")
    public void testConnectorEventImplementation() {
        UUID testUuid = UUID.randomUUID();
        
        // Using lambda to implement the interface
        ConnectorEvent event = () -> testUuid;
        
        assertNotNull(event);
        assertEquals(testUuid, event.getConsumer());
    }
}
