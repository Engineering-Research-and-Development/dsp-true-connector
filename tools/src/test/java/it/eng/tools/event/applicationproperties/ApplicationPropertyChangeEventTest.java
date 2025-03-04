package it.eng.tools.event.applicationproperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.tools.event.EventTestUtil;
import it.eng.tools.model.ApplicationProperty;
import org.springframework.security.core.Authentication;

public class ApplicationPropertyChangeEventTest {
    
    @Test
    @DisplayName("Test ApplicationPropertyChangeEvent creation and getters")
    public void testApplicationPropertyChangeEvent() throws Exception {
        ApplicationProperty oldValue = EventTestUtil.TEST_OLD_PROPERTY;
        ApplicationProperty newValue = EventTestUtil.TEST_NEW_PROPERTY;
        Authentication authentication = EventTestUtil.TEST_AUTHENTICATION;
        
        ApplicationPropertyChangeEvent event = new ApplicationPropertyChangeEvent(oldValue, newValue, authentication);
        
        assertNotNull(event);
        assertEquals(oldValue, event.getOldValue());
        assertEquals(newValue, event.getNewValue());
        assertEquals(authentication, event.getAuthentication());
    }
    
    @Test
    @DisplayName("Test ApplicationPropertyChangeEvent from EventTestUtil")
    public void testApplicationPropertyChangeEventFromUtil() throws Exception {
        ApplicationPropertyChangeEvent event = EventTestUtil.createTestPropertyChangeEvent();
        
        assertNotNull(event);
        assertEquals(EventTestUtil.TEST_OLD_PROPERTY, event.getOldValue());
        assertEquals(EventTestUtil.TEST_NEW_PROPERTY, event.getNewValue());
        assertEquals(EventTestUtil.TEST_AUTHENTICATION, event.getAuthentication());
    }
}
