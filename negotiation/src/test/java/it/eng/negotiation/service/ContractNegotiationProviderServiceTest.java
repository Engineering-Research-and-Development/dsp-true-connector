package it.eng.negotiation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationProviderServiceTest {

    @Mock
    private ContractNegotiationPublisher publisher;
    @Mock
    private ContractNegotiationRepository repository;
    @Mock
	private OfferRepository offerRepository;
    @Mock
    private ContractNegotiationProperties properties;
    @Mock
	private OkHttpRestClient okHttpRestClient;
    @Mock
	private GenericApiResponse<String> apiResponse;
    @InjectMocks
    private ContractNegotiationProviderService service;
    
	@Captor
	private ArgumentCaptor<ContractNegotiation> argCaptorContractNegotiation;

    @Test
    public void startContractNegotiation() {
        ContractRequestMessage crm = ContractRequestMessage.Builder.newInstance()
                .consumerPid(ModelUtil.CONSUMER_PID)
                .offer(ModelUtil.OFFER)
                .callbackAddress(ModelUtil.CALLBACK_ADDRESS)
                .build();
        ContractNegotiation result = service.startContractNegotiation(crm);
        assertNotNull(result);
        assertEquals(result.getType(), "dspace:ContractNegotiation");
        verify(repository).save(argCaptorContractNegotiation.capture());
		//verify that status is updated to REQUESTED
		assertEquals(ContractNegotiationState.REQUESTED, argCaptorContractNegotiation.getValue().getState());
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
    
    @Test
    public void finalizeNegotiation_success() {
    	ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
                .consumerPid(ModelUtil.CONSUMER_PID)
                .providerPid(ModelUtil.PROVIDER_PID)
                .state(ContractNegotiationState.ACCEPTED)
                .build();
        when(repository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(cn));
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);

    	service.finalizeNegotiation(ModelUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE);
    	
		verify(repository).save(argCaptorContractNegotiation.capture());
		//verify that status is updated to FINAILZED
		assertEquals(ContractNegotiationState.FINALIZED, argCaptorContractNegotiation.getValue().getState());

    }

}
