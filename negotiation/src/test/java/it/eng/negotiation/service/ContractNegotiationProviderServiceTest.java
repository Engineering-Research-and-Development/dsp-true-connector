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
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.contractnegotiation.ContractNegotationOfferRequestEvent;
import it.eng.tools.model.IConstants;
import it.eng.tools.property.ConnectorProperties;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;

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
    @Mock
    private CredentialUtils credentialUtils;
    @Mock
    private ConnectorProperties connectorProperties;
    
    @InjectMocks
    private ContractNegotiationProviderService service;
    
	@Captor
	private ArgumentCaptor<ContractNegotiation> argCaptorContractNegotiation;
	@Captor
	private ArgumentCaptor<Offer> argCaptorOffer;

    @Test
    @DisplayName("Start contract negotiation success - automatic negotiation ON")
    public void startContractNegotiation_automaticON() throws InterruptedException {
    	when(properties.isAutomaticNegotiation()).thenReturn(true);
    	when(credentialUtils.getAPICredentials()).thenReturn("credentials");
    	when(connectorProperties.getConnectorURL()).thenReturn("http://test.connector.url");
        when(repository.findByProviderPidAndConsumerPid(eq(null), anyString())).thenReturn(Optional.ofNullable(null));
    	when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
    	when(apiResponse.isSuccess()).thenReturn(true);
		when(offerRepository.save(any(Offer.class))).thenReturn(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID);
    	
        ContractNegotiation result = service.startContractNegotiation(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE);
        
        assertNotNull(result);
        assertEquals(result.getType(), "dspace:ContractNegotiation");
        verify(repository).save(argCaptorContractNegotiation.capture());
        verify(offerRepository).save(argCaptorOffer.capture());
		//verify that status is updated to REQUESTED
		assertEquals(ContractNegotiationState.REQUESTED, argCaptorContractNegotiation.getValue().getState());
		assertEquals(NegotiationMockObjectUtil.CALLBACK_ADDRESS, argCaptorContractNegotiation.getValue().getCallbackAddress());
		assertEquals(NegotiationMockObjectUtil.CONSUMER_PID, argCaptorContractNegotiation.getValue().getConsumerPid());
		assertEquals(IConstants.ROLE_PROVIDER, argCaptorContractNegotiation.getValue().getRole());
		assertEquals(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE.getOffer().getId(), argCaptorOffer.getValue().getOriginalId());
		assertNotNull(argCaptorContractNegotiation.getValue().getProviderPid());
		verify(publisher).publishEvent(any(ContractNegotationOfferRequestEvent.class));
    }
    
    @Test
    @DisplayName("Start contract negotiation success - automatic negotiation OFF")
    public void startContractNegotiation_automatic_OFF() throws InterruptedException {
        when(repository.findByProviderPidAndConsumerPid(eq(null), anyString())).thenReturn(Optional.ofNullable(null));
        when(credentialUtils.getAPICredentials()).thenReturn("credentials");
        when(connectorProperties.getConnectorURL()).thenReturn("http://test.connector.url");
    	when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
    	when(apiResponse.isSuccess()).thenReturn(true);
    	when(offerRepository.save(any(Offer.class))).thenReturn(NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID);
    	
        ContractNegotiation result = service.startContractNegotiation(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE);
        
        assertNotNull(result);
        assertEquals(result.getType(), "dspace:ContractNegotiation");
        verify(repository).save(argCaptorContractNegotiation.capture());
        verify(offerRepository).save(argCaptorOffer.capture());
		//verify that status is updated to REQUESTED
        assertEquals(ContractNegotiationState.REQUESTED, argCaptorContractNegotiation.getValue().getState());
		assertEquals(NegotiationMockObjectUtil.CALLBACK_ADDRESS, argCaptorContractNegotiation.getValue().getCallbackAddress());
		assertEquals(NegotiationMockObjectUtil.CONSUMER_PID, argCaptorContractNegotiation.getValue().getConsumerPid());
		assertEquals(IConstants.ROLE_PROVIDER, argCaptorContractNegotiation.getValue().getRole());
		assertEquals(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE.getOffer().getId(), argCaptorOffer.getValue().getOriginalId());
		assertNotNull(argCaptorContractNegotiation.getValue().getProviderPid());
		verify(publisher, times(0)).publishEvent(any(ContractNegotationOfferRequestEvent.class));
    }
    
    @Test
    @DisplayName("Start contract negotiation failed - provider pid not blank")
    public void startContractNegotiation_providerPidNotBlank() throws InterruptedException {
    	ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
    			.callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
    			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
    			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
    			.offer(NegotiationMockObjectUtil.OFFER)
    			.build();
    	
        assertThrows(ProviderPidNotBlankException.class,()-> service.startContractNegotiation(contractRequestMessage));
        verify(repository, times(0)).save(any(ContractNegotiation.class));
    }
    
    @Test
    @DisplayName("Start contract negotiation failed - contract negotiation exists")
    public void startContractNegotiation_contractNegotiationExists() throws InterruptedException {
        when(repository.findByProviderPidAndConsumerPid(eq(null), anyString())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));
    	
        assertThrows(ContractNegotiationExistsException.class,()-> service.startContractNegotiation(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE));
        verify(repository, times(0)).save(any(ContractNegotiation.class));
    }
    
    @Test
    @DisplayName("Start contract negotiation failed - offer not valid")
    public void startContractNegotiation_offerNotValid() throws InterruptedException {
        when(repository.findByProviderPidAndConsumerPid(eq(null), anyString())).thenReturn(Optional.ofNullable(null));
        when(credentialUtils.getAPICredentials()).thenReturn("credentials");
		when(connectorProperties.getConnectorURL()).thenReturn("http://test.connector.url");
    	when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
    	when(apiResponse.isSuccess()).thenReturn(false);
    	
        assertThrows(OfferNotValidException.class,()-> service.startContractNegotiation(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE));
        verify(repository, times(0)).save(any(ContractNegotiation.class));
    }

    @Test
    @DisplayName("Get negotiation by provider pid - success")
    public void getNegotiationByProviderPid() {
        when(repository.findByProviderPid(anyString())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        ContractNegotiation result = service.getNegotiationByProviderPid(NegotiationMockObjectUtil.PROVIDER_PID);

        assertNotNull(result);

        assertEquals(result.getConsumerPid(), NegotiationMockObjectUtil.CONSUMER_PID);
        assertEquals(result.getProviderPid(), NegotiationMockObjectUtil.PROVIDER_PID);
        assertEquals(result.getState(), ContractNegotiationState.ACCEPTED);
    }

    @Test
    @DisplayName("Get negotiation by provider pid - negotiation not found")
    public void getNegotiationByProviderPid_notFound() {
        when(repository.findByProviderPid(anyString())).thenReturn(Optional.ofNullable(null));
        assertThrows(ContractNegotiationNotFoundException.class, () -> service.getNegotiationByProviderPid(NegotiationMockObjectUtil.PROVIDER_PID),
                "Expected getNegotiationByProviderPid to throw, but it didn't");
    }
    
    @Test
    @DisplayName("Get negotiation by id - success")
    public void getNegotiationById() {
        when(repository.findById(anyString())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        ContractNegotiation result = service.getNegotiationById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId());

        assertNotNull(result);

        assertEquals(result.getConsumerPid(), NegotiationMockObjectUtil.CONSUMER_PID);
        assertEquals(result.getProviderPid(), NegotiationMockObjectUtil.PROVIDER_PID);
        assertEquals(result.getState(), ContractNegotiationState.ACCEPTED);
    }

    @Test
    @DisplayName("Get negotiation by id - negotiation not found")
    public void getNegotiationById_notFound() {
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        assertThrows(ContractNegotiationNotFoundException.class, () -> service.getNegotiationById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId()),
                "Expected getNegotiationByProviderPid to throw, but it didn't");
    }
    
    @Test
    @DisplayName("Verify negotiation - success")
    public void verifyNegotiation_success() {
        when(repository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED));

    	service.verifyNegotiation(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE);
    	
		verify(repository).save(argCaptorContractNegotiation.capture());
		
		assertEquals(ContractNegotiationState.VERIFIED, argCaptorContractNegotiation.getValue().getState());

    }
    
    @Test
    @DisplayName("Verify negotiation - negotiation not found")
    public void verifyNegotiation_negotiationNotFound() {
        when(repository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.empty());

        assertThrows(ContractNegotiationNotFoundException.class, () -> service.verifyNegotiation(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE));
    }
    
    @Test
    @DisplayName("Verify negotiation - invalid state")
    public void verifyNegotiation_invalidState() {
        when(repository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        assertThrows(ContractNegotiationInvalidStateException.class, () -> service.verifyNegotiation(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE));
    }

    @Test
	@DisplayName("Process termination message success")
	public void handleTerminationRequest_success() {
		when(repository.findByProviderPid(any(String.class)))
			.thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED_PROIVDER));

		service.handleTerminationRequest(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.TERMINATION_MESSAGE);
		
		verify(repository).save(argCaptorContractNegotiation.capture());
		assertEquals(ContractNegotiationState.TERMINATED, argCaptorContractNegotiation.getValue().getState());
	}
	
	@Test
	@DisplayName("Process termination message failed - negotiation not found")
	public void handleTerminationRequest_fail() {
		when(repository.findByProviderPid(any(String.class)))
			.thenReturn(Optional.empty());

		assertThrows(ContractNegotiationNotFoundException.class, 
				() -> service.handleTerminationRequest(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.TERMINATION_MESSAGE));
	}
	
	@Test
	@DisplayName("Process termination message failed - already terminated")
	public void handleTerminationRequest_fail_alreadyTerminated() {
		when(repository.findByProviderPid(any(String.class)))
			.thenReturn(Optional.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_TERMINATED));

		assertThrows(ContractNegotiationInvalidStateException.class, 
				() -> service.handleTerminationRequest(NegotiationMockObjectUtil.PROVIDER_PID, NegotiationMockObjectUtil.TERMINATION_MESSAGE));
	}
}
