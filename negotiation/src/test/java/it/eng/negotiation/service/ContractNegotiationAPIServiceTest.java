package it.eng.negotiation.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.model.Serializer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationAPIServiceTest {

	@Mock
	private OkHttpRestClient okHttpRestClient;
	@Mock
	private ContractNegotiationRepository repository;
	@Mock
	private ContractNegotiationProperties properties;
	@Mock
	private GenericApiResponse<String> apiResponse;

	@InjectMocks
	private ContractNegotiationAPIService service;
	
	@Test
	@DisplayName("Start contract negotiation success")
	public void startNegotiation_success() {
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class))).thenReturn(apiResponse);
		when(apiResponse.getData()).thenReturn(Serializer.serializeProtocol(ModelUtil.CONTRACT_NEGOTIATION));
		when(properties.callbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		
		service.startNegotiation(ModelUtil.FORWARD_TO, Serializer.serializeProtocolJsonNode(ModelUtil.OFFER));
		
		verify(repository).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Process posted offer - success")
	public void postContractOffer_success() {
		when(properties.callbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
			.thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);
		when(apiResponse.getData()).thenReturn(Serializer.serializeProtocol(ModelUtil.CONTRACT_NEGOTIATION_OFFERED));
		// plain jsonNode
		service.postContractOffer(ModelUtil.FORWARD_TO, Serializer.serializePlainJsonNode(ModelUtil.OFFER));
		
		verify(repository).save(any(ContractNegotiation.class));
	}
	
	@Test
	@DisplayName("Process posted offer - error")
	public void postContractOffer_error() {
		when(properties.callbackAddress()).thenReturn(ModelUtil.CALLBACK_ADDRESS);
		when(okHttpRestClient.sendRequestProtocol(any(String.class), any(JsonNode.class), any(String.class)))
			.thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);
		
		assertThrows(ContractNegotiationAPIException.class, ()->
			service.postContractOffer(ModelUtil.FORWARD_TO, Serializer.serializePlainJsonNode(ModelUtil.OFFER)));
	}
}
