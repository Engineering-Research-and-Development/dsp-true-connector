package it.eng.negotiation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.exception.ContractNegotiationExistsException;
import it.eng.negotiation.exception.ContractNegotiationInvalidStateException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.exception.OfferNotValidException;
import it.eng.negotiation.exception.ProviderPidNotBlankException;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.contractnegotiation.ContractNegotationOfferRequestEvent;
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
    @DisplayName("Start contract negotiation success - automatic negotiation ON")
    public void startContractNegotiation_automaticON() throws InterruptedException {
    	when(properties.isAutomaticNegotiation()).thenReturn(true);
        when(repository.findByProviderPidAndConsumerPid(eq(null), anyString())).thenReturn(Optional.ofNullable(null));
    	when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
    	when(apiResponse.isSuccess()).thenReturn(true);
        ContractNegotiation result = service.startContractNegotiation(ModelUtil.CONTRACT_REQUEST_MESSAGE);
        assertNotNull(result);
        assertEquals(result.getType(), "dspace:ContractNegotiation");
        verify(repository).save(argCaptorContractNegotiation.capture());
		//verify that status is updated to REQUESTED
		assertEquals(ContractNegotiationState.REQUESTED, argCaptorContractNegotiation.getValue().getState());
		assertEquals(ModelUtil.CALLBACK_ADDRESS, argCaptorContractNegotiation.getValue().getCallbackAddress());
		assertEquals(ModelUtil.CONSUMER_PID, argCaptorContractNegotiation.getValue().getConsumerPid());
		assertNotNull(argCaptorContractNegotiation.getValue().getProviderPid());
		verify(publisher).publishEvent(any(ContractNegotationOfferRequestEvent.class));
    }
    
    @Test
    @DisplayName("Start contract negotiation success - automatic negotiation OFF")
    public void startContractNegotiation_automatic_OFF() throws InterruptedException {
        when(repository.findByProviderPidAndConsumerPid(eq(null), anyString())).thenReturn(Optional.ofNullable(null));
    	when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
    	when(apiResponse.isSuccess()).thenReturn(true);
        ContractNegotiation result = service.startContractNegotiation(ModelUtil.CONTRACT_REQUEST_MESSAGE);
        assertNotNull(result);
        assertEquals(result.getType(), "dspace:ContractNegotiation");
        verify(repository).save(argCaptorContractNegotiation.capture());
		//verify that status is updated to REQUESTED
        assertEquals(ContractNegotiationState.REQUESTED, argCaptorContractNegotiation.getValue().getState());
		assertEquals(ModelUtil.CALLBACK_ADDRESS, argCaptorContractNegotiation.getValue().getCallbackAddress());
		assertEquals(ModelUtil.CONSUMER_PID, argCaptorContractNegotiation.getValue().getConsumerPid());
		assertNotNull(argCaptorContractNegotiation.getValue().getProviderPid());
		verify(publisher, times(0)).publishEvent(any(ContractNegotationOfferRequestEvent.class));
    }
    
    @Test
    @DisplayName("Start contract negotiation failed - provider pid not blank")
    public void startContractNegotiation_providerPidNotBlank() throws InterruptedException {
    	ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
    			.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
    			.consumerPid(ModelUtil.CONSUMER_PID)
    			.providerPid(ModelUtil.PROVIDER_PID)
    			.offer(ModelUtil.OFFER)
    			.build();
    	
        assertThrows(ProviderPidNotBlankException.class,()-> service.startContractNegotiation(contractRequestMessage));
        verify(repository, times(0)).save(any(ContractNegotiation.class));
    }
    
    @Test
    @DisplayName("Start contract negotiation failed - contract negotiation exists")
    public void startContractNegotiation_contractNegotiationExists() throws InterruptedException {
        when(repository.findByProviderPidAndConsumerPid(eq(null), anyString())).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED));
    	
        assertThrows(ContractNegotiationExistsException.class,()-> service.startContractNegotiation(ModelUtil.CONTRACT_REQUEST_MESSAGE));
        verify(repository, times(0)).save(any(ContractNegotiation.class));
    }
    
    @Test
    @DisplayName("Start contract negotiation failed - offer not valid")
    public void startContractNegotiation_offerNotValid() throws InterruptedException {
        when(repository.findByProviderPidAndConsumerPid(eq(null), anyString())).thenReturn(Optional.ofNullable(null));
    	when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
    	when(apiResponse.isSuccess()).thenReturn(false);
    	
        assertThrows(OfferNotValidException.class,()-> service.startContractNegotiation(ModelUtil.CONTRACT_REQUEST_MESSAGE));
        verify(repository, times(0)).save(any(ContractNegotiation.class));
    }

    @Test
    public void getNegotiationByProviderPid() {
        when(repository.findByProviderPid(anyString())).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED));

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
    public void getNegotiationById() {
        when(repository.findById(anyString())).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        ContractNegotiation result = service.getNegotiationById(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId());

        assertNotNull(result);

        assertEquals(result.getConsumerPid(), ModelUtil.CONSUMER_PID);
        assertEquals(result.getProviderPid(), ModelUtil.PROVIDER_PID);
        assertEquals(result.getState(), ContractNegotiationState.ACCEPTED);
    }

    @Test
    public void getNegotiationById_notFound() {
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        assertThrows(ContractNegotiationNotFoundException.class, () -> service.getNegotiationById(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId()),
                "Expected getNegotiationByProviderPid to throw, but it didn't");
    }
    
    @Test
    public void verifyNegotiation_success() {
        when(repository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_AGREED));

    	service.verifyNegotiation(ModelUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE);
    	
		verify(repository).save(argCaptorContractNegotiation.capture());
		
		assertEquals(ContractNegotiationState.VERIFIED, argCaptorContractNegotiation.getValue().getState());

    }
    
    @Test
    public void verifyNegotiation_negotiationNotFound() {
        when(repository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.empty());

        assertThrows(ContractNegotiationNotFoundException.class, () -> service.verifyNegotiation(ModelUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE));
    }
    
    @Test
    public void verifyNegotiation_invalidState() {
        when(repository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(ModelUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        assertThrows(ContractNegotiationInvalidStateException.class, () -> service.verifyNegotiation(ModelUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE));
    }

}
