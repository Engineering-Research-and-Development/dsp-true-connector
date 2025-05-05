package it.eng.datatransfer.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.datatransfer.exceptions.AgreementNotFoundException;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.util.DataTranferMockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.property.ConnectorProperties;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.usagecontrol.UsageControlProperties;
import it.eng.tools.util.CredentialUtils;

@ExtendWith(MockitoExtension.class)
class AgreementServiceTest {

	@Mock
	private TransferProcessRepository transferProcessRepository;
	@Mock
	private UsageControlProperties usageControlProperties;
	@Mock
	private OkHttpRestClient okHttpRestClient;
	@Mock
	private GenericApiResponse<String> apiResponse;
	@Mock
	private CredentialUtils credentialUtils;
	@Mock
	private ConnectorProperties connectorProperties;
	
	@InjectMocks
	private AgreementService service;
	
	@Test
	@DisplayName("Agreement valid - usageControl enabled")
	void isAgreementValid_uc_enabled() {
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		when(transferProcessRepository.findByConsumerPidAndProviderPid(DataTranferMockObjectUtil.CONSUMER_PID, DataTranferMockObjectUtil.PROVIDER_PID))
			.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));
		when(credentialUtils.getAPICredentials()).thenReturn("credentials");
		when(connectorProperties.getConnectorURL()).thenReturn("http://test.localhost:8080");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), isNull(), any(String.class)))
			.thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(true);

		boolean isValid = service.isAgreementValid(DataTranferMockObjectUtil.CONSUMER_PID, DataTranferMockObjectUtil.PROVIDER_PID);
		assertTrue(isValid);
	}
	
	@Test
	@DisplayName("Agreement invalid - usageControl enabled")
	void isAgreementValid_uc_enabled_call_false() {
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		when(transferProcessRepository.findByConsumerPidAndProviderPid(DataTranferMockObjectUtil.CONSUMER_PID, DataTranferMockObjectUtil.PROVIDER_PID))
			.thenReturn(Optional.of(DataTranferMockObjectUtil.TRANSFER_PROCESS_STARTED));
		when(credentialUtils.getAPICredentials()).thenReturn("credentials");
		when(connectorProperties.getConnectorURL()).thenReturn("http://test.localhost:8080");
		when(okHttpRestClient.sendRequestProtocol(any(String.class), isNull(), any(String.class)))
			.thenReturn(apiResponse);
		when(apiResponse.isSuccess()).thenReturn(false);

		boolean isValid = service.isAgreementValid(DataTranferMockObjectUtil.CONSUMER_PID, DataTranferMockObjectUtil.PROVIDER_PID);
		assertFalse(isValid);
	}
	
	@Test
	@DisplayName("Agreement invalid - transferProcess not found")
	void isAgreementValid_tn_not_found() {
		when(usageControlProperties.usageControlEnabled()).thenReturn(true);
		when(transferProcessRepository.findByConsumerPidAndProviderPid(DataTranferMockObjectUtil.CONSUMER_PID, DataTranferMockObjectUtil.PROVIDER_PID))
			.thenReturn(Optional.empty());
		
		assertThrows(AgreementNotFoundException.class, ()->
			service.isAgreementValid(DataTranferMockObjectUtil.CONSUMER_PID, DataTranferMockObjectUtil.PROVIDER_PID));
	}
	
	@Test
	@DisplayName("Agreement valid - usageControl disabled")
	void isAgreementValid_uc_disabled() {
		when(usageControlProperties.usageControlEnabled()).thenReturn(false);
		boolean isValid = service.isAgreementValid(DataTranferMockObjectUtil.CONSUMER_PID, DataTranferMockObjectUtil.PROVIDER_PID);
		assertTrue(isValid);
	}

}
