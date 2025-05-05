package it.eng.tools.event.datatransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.tools.event.EventTestUtil;

public class InitializeTransferProcessTest {
    
    @Test
    @DisplayName("Test InitializeTransferProcess creation and getters")
    public void testInitializeTransferProcess() throws Exception {
        String callbackAddress = EventTestUtil.TEST_CALLBACK_ADDRESS;
        String agreementId = EventTestUtil.TEST_AGREEMENT_ID;
        String datasetId = EventTestUtil.TEST_DATASET_ID;
        String role = EventTestUtil.TEST_ROLE;
        
        InitializeTransferProcess event = new InitializeTransferProcess(
            callbackAddress, agreementId, datasetId, role);
        
        assertNotNull(event);
        assertEquals(callbackAddress, event.getCallbackAddress());
        assertEquals(agreementId, event.getAgreementId());
        assertEquals(datasetId, event.getDatasetId());
        assertEquals(role, event.getRole());
    }
    
    @Test
    @DisplayName("Test InitializeTransferProcess from EventTestUtil")
    public void testInitializeTransferProcessFromUtil() throws Exception {
        InitializeTransferProcess event = EventTestUtil.createTestInitializeTransferProcess();
        
        assertNotNull(event);
        assertEquals(EventTestUtil.TEST_CALLBACK_ADDRESS, event.getCallbackAddress());
        assertEquals(EventTestUtil.TEST_AGREEMENT_ID, event.getAgreementId());
        assertEquals(EventTestUtil.TEST_DATASET_ID, event.getDatasetId());
        assertEquals(EventTestUtil.TEST_ROLE, event.getRole());
    }
}
