package it.eng.negotiation.service;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.entity.ContractNegotiationEntity;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationServiceTest {

	@Mock
	private ContractNegotiationPublisher publisher;
	@Mock
	private ContractNegotiationRepository repository;

	private ContractNegotiationService service;
	
	@BeforeEach
	public void setup() {
		service = new ContractNegotiationService(publisher, repository, "conncector_id", false);
	}
	
	@Test
	public void startContractNegotiation() throws InterruptedException, ExecutionException {
		when(repository.findByProviderPidAndConsumerPid(eq(null), anyString())).thenReturn(Optional.ofNullable(null));
		ContractRequestMessage crm = ContractRequestMessage.Builder.newInstance()
				.consumerPid(ModelUtil.CONSUMER_PID)
//				.providerPid(ModelUtil.PROVIDER_PID)
				.offer(ModelUtil.OFFER)
				.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
				.build();
		CompletableFuture<JsonNode> result = service.startContractNegotiation(crm);
		assertNotNull(result);
		assertNotNull(result.get());
		assertEquals(result.get().get("@type").asText(), "dspace:ContractNegotiation");
	}
	
	@Test
	public void startContractNegotiation_exists() throws InterruptedException, ExecutionException {
		ContractRequestMessage crm = ContractRequestMessage.Builder.newInstance()
				.consumerPid(ModelUtil.CONSUMER_PID)
				.providerPid(ModelUtil.PROVIDER_PID)
				.offer(ModelUtil.OFFER)
				.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
				.build();
		ContractNegotiationEntity cne = new ContractNegotiationEntity();
		cne.setConsumerPid(ModelUtil.CONSUMER_PID);
		cne.setProviderPid(ModelUtil.PROVIDER_PID);
		when(repository.findByProviderPidAndConsumerPid(anyString(), anyString())).thenReturn(Optional.of(cne));
		CompletableFuture<JsonNode> result = service.startContractNegotiation(crm);
		assertNotNull(result);
		assertNotNull(result.get());
		assertEquals(result.get().get("@type").asText(), "dspace:ContractNegotiationErrorMessage");
	}


	@Test
	public void getNegotiationByProviderPid() throws InterruptedException, ExecutionException {
		ContractNegotiationEntity cne = new ContractNegotiationEntity();
		cne.setConsumerPid(ModelUtil.CONSUMER_PID);
		cne.setProviderPid(ModelUtil.PROVIDER_PID);
		cne.setState(ModelUtil.STATE);
		when(repository.findByProviderPid(anyString())).thenReturn(Optional.of(cne));
		CompletableFuture<ContractNegotiation> result = service.getNegotiationByProviderPid(ModelUtil.PROVIDER_PID);
		assertNotNull(result);
		assertNotNull(result.get());
		assertEquals(result.get().getConsumerPid(), ModelUtil.CONSUMER_PID);
		assertEquals(result.get().getProviderPid(), ModelUtil.PROVIDER_PID);
		assertEquals(result.get().getState().name(), ModelUtil.STATE);
	}

	@Test
	public void getNegotiationByProviderPid_notFound() {
		when(repository.findByProviderPid(anyString())).thenReturn(Optional.ofNullable(null));
		CompletableFuture<ContractNegotiation> result = service.getNegotiationByProviderPid(ModelUtil.PROVIDER_PID);
		assertNotNull(result);
		try {
			result.get();
		} catch (InterruptedException | ExecutionException e) {
			assertEquals(e.getCause().getMessage(), "Contract negotiation with provider pid " + ModelUtil.PROVIDER_PID + " not found");
		}
	}

}
