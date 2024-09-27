package it.eng.datatransfer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.exceptions.AgreementNotFoundException;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.util.MockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.usagecontrol.UsageControlProperties;
import it.eng.tools.util.CredentialUtils;

@ExtendWith(MockitoExtension.class)
class AgreementServiceTest {
	
	@Mock
	private TransferProcessRepository transferProcessRepository;
	@Mock
	private OkHttpRestClient okHttpRestClient;
	@Mock
	private CredentialUtils credentialUtils;
	@Mock
	private UsageControlProperties usageControlProperties; 

	@InjectMocks
	private AgreementService service;
	
	@Test
	@DisplayName("Agreement valid and usage control enabled")
	void agreementValid() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(MockObjectUtil.CONSUMER_PID, MockObjectUtil.PROVIDER_PID))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_STARTED));
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		when(okHttpRestClient.sendRequestProtocol(anyString(), isNull(), isNull()))
					.thenReturn(GenericApiResponse.success("Agreement OK", "Agreement OK"));
		service.isAgreementValid(MockObjectUtil.CONSUMER_PID, MockObjectUtil.PROVIDER_PID);
	}
	
	@Test
	@DisplayName("Agreement invalid - transfer process not found")
	void agreementInvalid_tp_not_found() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(MockObjectUtil.CONSUMER_PID, MockObjectUtil.PROVIDER_PID))
			.thenReturn(Optional.empty());
		
		assertThrows(AgreementNotFoundException.class,
				() -> service.isAgreementValid(MockObjectUtil.CONSUMER_PID, MockObjectUtil.PROVIDER_PID));
	}

	@Test
	@DisplayName("Agreement valid - usage control disabled")
	void isAgreementValid_usageControDisabled() {
		when(transferProcessRepository.findByConsumerPidAndProviderPid(MockObjectUtil.CONSUMER_PID, MockObjectUtil.PROVIDER_PID))
			.thenReturn(Optional.of(MockObjectUtil.TRANSFER_PROCESS_STARTED));
		when(usageControlProperties.usageControlEnabled()).thenReturn(false);
		
		service.isAgreementValid(MockObjectUtil.CONSUMER_PID, MockObjectUtil.PROVIDER_PID);
		
		verify(okHttpRestClient, times(0)).sendRequestProtocol(anyString(), any(JsonNode.class), anyString());
	}

}
