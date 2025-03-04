package it.eng.tools.event.applicationproperties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.tools.event.EventTestUtil;

@ExtendWith(MockitoExtension.class)
public class ApplicationPropertiesEventListenerTest {
    
    @InjectMocks
    private ApplicationPropertiesEventListener listener;
    
    @Test
    @DisplayName("Test handling ApplicationPropertyChangeEvent")
    public void testHandleApplicationPropertyChangeEvent() throws Exception {
        ApplicationPropertyChangeEvent event = EventTestUtil.createTestPropertyChangeEvent();
        
        assertDoesNotThrow(() -> listener.pplicationPropertyChanged(event));
    }
}
