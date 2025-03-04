package it.eng.tools.event.contractnegotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.tools.event.EventTestUtil;

public class ContractNegotationOfferRequestEventTest {
    
    @Test
    @DisplayName("Test ContractNegotationOfferRequestEvent creation and getters")
    public void testContractNegotationOfferRequestEvent() throws Exception {
        String consumerPid = EventTestUtil.TEST_CONSUMER_PID;
        String providerPid = EventTestUtil.TEST_PROVIDER_PID;
        JsonNode offer = EventTestUtil.TEST_OFFER;
        
        ContractNegotationOfferRequestEvent event = new ContractNegotationOfferRequestEvent(consumerPid, providerPid, offer);
        
        assertNotNull(event);
        assertEquals(consumerPid, event.getConsumerPid());
        assertEquals(providerPid, event.getProviderPid());
        assertEquals(offer, event.getOffer());
    }
    
    @Test
    @DisplayName("Test ContractNegotationOfferRequestEvent from EventTestUtil")
    public void testContractNegotationOfferRequestEventFromUtil() throws Exception {
        ContractNegotationOfferRequestEvent event = EventTestUtil.createTestOfferRequestEvent();
        
        assertNotNull(event);
        assertEquals(EventTestUtil.TEST_CONSUMER_PID, event.getConsumerPid());
        assertEquals(EventTestUtil.TEST_PROVIDER_PID, event.getProviderPid());
        assertEquals(EventTestUtil.TEST_OFFER, event.getOffer());
    }
}
