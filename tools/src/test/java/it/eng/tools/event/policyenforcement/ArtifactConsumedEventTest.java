package it.eng.tools.event.policyenforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.tools.event.EventTestUtil;

public class ArtifactConsumedEventTest {
    
    @Test
    @DisplayName("Test ArtifactConsumedEvent creation and getters")
    public void testArtifactConsumedEvent() throws Exception {
        String agreementId = EventTestUtil.TEST_AGREEMENT_ID;
        
        ArtifactConsumedEvent event = new ArtifactConsumedEvent(agreementId);
        
        assertNotNull(event);
        assertEquals(agreementId, event.getAgreementId());
    }
    
    @Test
    @DisplayName("Test ArtifactConsumedEvent from EventTestUtil")
    public void testArtifactConsumedEventFromUtil() throws Exception {
        ArtifactConsumedEvent event = EventTestUtil.createTestArtifactConsumedEvent();
        
        assertNotNull(event);
        assertEquals(EventTestUtil.TEST_AGREEMENT_ID, event.getAgreementId());
    }
}
