package it.eng.tools.event;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.eng.tools.event.applicationproperties.ApplicationPropertyChangeEvent;
import it.eng.tools.event.contractnegotiation.ContractNegotationOfferRequestEvent;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponseEvent;
import it.eng.tools.event.datatransfer.InitializeTransferProcess;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.model.ApplicationProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Field;

public class EventTestUtil {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    public static final String TEST_CONSUMER_PID = "consumer-pid-123";
    public static final String TEST_PROVIDER_PID = "provider-pid-456";
    public static final String TEST_AGREEMENT_ID = "agreement-id-789";
    public static final String TEST_DATASET_ID = "dataset-id-012";
    public static final String TEST_CALLBACK_ADDRESS = "https://callback.example.com";
    public static final String TEST_ROLE = "CONSUMER";
    
    public static final ApplicationProperty TEST_OLD_PROPERTY = ApplicationProperty.Builder.newInstance()
            .key("test.property")
            .value("old-value")
            .build();
            
    public static final ApplicationProperty TEST_NEW_PROPERTY = ApplicationProperty.Builder.newInstance()
            .key("test.property")
            .value("new-value")
            .build();
            
    public static final Authentication TEST_AUTHENTICATION = new UsernamePasswordAuthenticationToken("testuser", "password");
    
    public static final JsonNode TEST_OFFER;
    
    static {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("offerId", "offer-123");
        node.put("assetId", "asset-456");
        TEST_OFFER = node;
    }
    
    public static ApplicationPropertyChangeEvent createTestPropertyChangeEvent() throws Exception {
        return new ApplicationPropertyChangeEvent(TEST_OLD_PROPERTY, TEST_NEW_PROPERTY, TEST_AUTHENTICATION);
    }
    
    public static ContractNegotationOfferRequestEvent createTestOfferRequestEvent() throws Exception {
        return new ContractNegotationOfferRequestEvent(TEST_CONSUMER_PID, TEST_PROVIDER_PID, TEST_OFFER);
    }
    
    public static ContractNegotiationOfferResponseEvent createTestOfferResponseEvent(boolean accepted) throws Exception {
        return new ContractNegotiationOfferResponseEvent(TEST_CONSUMER_PID, TEST_PROVIDER_PID, accepted, TEST_OFFER);
    }
    
    public static InitializeTransferProcess createTestInitializeTransferProcess() throws Exception {
        return new InitializeTransferProcess(TEST_CALLBACK_ADDRESS, TEST_AGREEMENT_ID, TEST_DATASET_ID, TEST_ROLE);
    }
    
    public static ArtifactConsumedEvent createTestArtifactConsumedEvent() throws Exception {
        return new ArtifactConsumedEvent(TEST_AGREEMENT_ID);
    }
    
    public static void setField(Object object, String fieldName, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(object, value);
    }
    
    public static <T> T getField(Object object, String fieldName, Class<T> fieldType) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return fieldType.cast(field.get(object));
    }
}
