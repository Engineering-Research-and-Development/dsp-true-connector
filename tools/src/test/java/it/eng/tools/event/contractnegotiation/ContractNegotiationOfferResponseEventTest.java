package it.eng.tools.event.contractnegotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.tools.event.EventTestUtil;

public class ContractNegotiationOfferResponseEventTest {
    
    @Test
    @DisplayName("Test ContractNegotiationOfferResponseEvent creation and getters - accepted")
    public void testContractNegotiationOfferResponseEventAccepted() throws Exception {
        String consumerPid = EventTestUtil.TEST_CONSUMER_PID;
        String providerPid = EventTestUtil.TEST_PROVIDER_PID;
        boolean offerAccepted = true;
        JsonNode offer = EventTestUtil.TEST_OFFER;
        
        ContractNegotiationOfferResponseEvent event = new ContractNegotiationOfferResponseEvent(
            consumerPid, providerPid, offerAccepted, offer);
        
        assertNotNull(event);
        assertEquals(consumerPid, event.getConsumerPid());
        assertEquals(providerPid, event.getProviderPid());
        assertTrue(event.isOfferAccepted());
        assertEquals(offer, event.getOffer());
    }
    
    @Test
    @DisplayName("Test ContractNegotiationOfferResponseEvent creation and getters - declined")
    public void testContractNegotiationOfferResponseEventDeclined() throws Exception {
        String consumerPid = EventTestUtil.TEST_CONSUMER_PID;
        String providerPid = EventTestUtil.TEST_PROVIDER_PID;
        boolean offerAccepted = false;
        JsonNode offer = EventTestUtil.TEST_OFFER;
        
        ContractNegotiationOfferResponseEvent event = new ContractNegotiationOfferResponseEvent(
            consumerPid, providerPid, offerAccepted, offer);
        
        assertNotNull(event);
        assertEquals(consumerPid, event.getConsumerPid());
        assertEquals(providerPid, event.getProviderPid());
        assertFalse(event.isOfferAccepted());
        assertEquals(offer, event.getOffer());
    }
    
    @Test
    @DisplayName("Test ContractNegotiationOfferResponseEvent from EventTestUtil - accepted")
    public void testContractNegotiationOfferResponseEventFromUtilAccepted() throws Exception {
        ContractNegotiationOfferResponseEvent event = EventTestUtil.createTestOfferResponseEvent(true);
        
        assertNotNull(event);
        assertEquals(EventTestUtil.TEST_CONSUMER_PID, event.getConsumerPid());
        assertEquals(EventTestUtil.TEST_PROVIDER_PID, event.getProviderPid());
        assertTrue(event.isOfferAccepted());
        assertEquals(EventTestUtil.TEST_OFFER, event.getOffer());
    }
    
    @Test
    @DisplayName("Test ContractNegotiationOfferResponseEvent from EventTestUtil - declined")
    public void testContractNegotiationOfferResponseEventFromUtilDeclined() throws Exception {
        ContractNegotiationOfferResponseEvent event = EventTestUtil.createTestOfferResponseEvent(false);
        
        assertNotNull(event);
        assertEquals(EventTestUtil.TEST_CONSUMER_PID, event.getConsumerPid());
        assertEquals(EventTestUtil.TEST_PROVIDER_PID, event.getProviderPid());
        assertFalse(event.isOfferAccepted());
        assertEquals(EventTestUtil.TEST_OFFER, event.getOffer());
    }
    
    @Test
    @DisplayName("Test ContractNegotiationOfferResponseEvent equals and hashCode")
    public void testContractNegotiationOfferResponseEventEqualsAndHashCode() throws Exception {
        ContractNegotiationOfferResponseEvent event1 = EventTestUtil.createTestOfferResponseEvent(true);
        ContractNegotiationOfferResponseEvent event2 = EventTestUtil.createTestOfferResponseEvent(true);
        
        // Since we're using Lombok's @Data, equals and hashCode should work correctly
        assertEquals(event1.getConsumerPid(), event2.getConsumerPid());
        assertEquals(event1.getProviderPid(), event2.getProviderPid());
        assertEquals(event1.isOfferAccepted(), event2.isOfferAccepted());
        assertEquals(event1.getOffer(), event2.getOffer());
        
        // Test equals and hashCode directly
        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
    }
}
