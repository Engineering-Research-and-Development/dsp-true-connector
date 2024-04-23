package it.eng.negotiation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.negotiation.exception.ContractNegotiationExistsException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationProviderServiceTest {

    @Mock
    private ContractNegotiationPublisher publisher;
    @Mock
    private ContractNegotiationRepository repository;
    @Mock
    private ContractNegotiationProperties properties;
    @InjectMocks
    private ContractNegotiationProviderService service;

//    @BeforeEach
//    public void setup() {
//        service = new ContractNegotiationService(publisher, repository);
//    }

    @Test
    public void startContractNegotiation() {
        when(repository.findByProviderPidAndConsumerPid(eq(null), anyString())).thenReturn(Optional.ofNullable(null));
        ContractRequestMessage crm = ContractRequestMessage.Builder.newInstance()
                .consumerPid(ModelUtil.CONSUMER_PID)
                .offer(ModelUtil.OFFER)
                .callbackAddress(ModelUtil.CALLBACK_ADDRESS)
                .build();
        ContractNegotiation result = service.startContractNegotiation(crm);
        assertNotNull(result);
        assertEquals(result.getType(), "dspace:ContractNegotiation");
    }

    @Test
    public void startContractNegotiation_exists() {
        ContractRequestMessage crm = ContractRequestMessage.Builder.newInstance()
                .consumerPid(ModelUtil.CONSUMER_PID)
                .providerPid(ModelUtil.PROVIDER_PID)
                .offer(ModelUtil.OFFER)
                .callbackAddress(ModelUtil.CALLBACK_ADDRESS)
                .build();
        ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
                .consumerPid(ModelUtil.CONSUMER_PID)
                .providerPid(ModelUtil.PROVIDER_PID)
                .state(ContractNegotiationState.REQUESTED)
                .build();

        when(repository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(cn));
        assertThrows(ContractNegotiationExistsException.class, () -> service.startContractNegotiation(crm),
                "Expected startContractNegotiation to throw ContractNegotiationExistsException, but it did not");
    }

    @Test
    public void getNegotiationByProviderPid() {
        ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
                .consumerPid(ModelUtil.CONSUMER_PID)
                .providerPid(ModelUtil.PROVIDER_PID)
                .state(ContractNegotiationState.ACCEPTED)
                .build();

        when(repository.findByProviderPid(anyString())).thenReturn(Optional.of(cn));

        ContractNegotiation result = service.getNegotiationByProviderPid(ModelUtil.PROVIDER_PID);

        assertNotNull(result);

        assertEquals(result.getConsumerPid(), ModelUtil.CONSUMER_PID);
        assertEquals(result.getProviderPid(), ModelUtil.PROVIDER_PID);
        assertEquals(result.getState(), ContractNegotiationState.ACCEPTED);
    }

    @Test
    public void getNegotiationByProviderPid_notFound() {
        when(repository.findByProviderPid(anyString())).thenReturn(Optional.ofNullable(null));
        assertThrows(ContractNegotiationNotFoundException.class, () -> service.getNegotiationByProviderPid(ModelUtil.PROVIDER_PID),
                "Expected getNegotiationByProviderPid to throw, but it didn't");
    }

}
