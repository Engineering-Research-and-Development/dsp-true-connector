package it.eng.datatransfer.service;

import it.eng.datatransfer.exceptions.AgreementNotFoundException;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.usagecontrol.UsageControlProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

    @InjectMocks
    private AgreementService service;

    @Test
    @DisplayName("Agreement valid - usageControl enabled")
    void isAgreementValid_uc_enabled() {
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(transferProcessRepository.findByConsumerPidAndProviderPid(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));
        when(okHttpRestClient.sendInternalPostRequest(any(String.class), isNull()))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        boolean isValid = service.isAgreementValid(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID);
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Agreement invalid - usageControl enabled")
    void isAgreementValid_uc_enabled_call_false() {
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(transferProcessRepository.findByConsumerPidAndProviderPid(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));
        when(okHttpRestClient.sendInternalPostRequest(any(String.class), isNull()))
                .thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(false);

        boolean isValid = service.isAgreementValid(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID);
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Agreement enforcement is a loopback call to /internal/v1/agreements/{id}/enforce, not a protocol call")
    void isAgreementValid_enforcesOverInternalZone() {
        String agreementId = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getAgreementId();
        String path = "/internal/v1/agreements/" + agreementId + "/enforce";
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(transferProcessRepository.findByConsumerPidAndProviderPid(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));
        when(okHttpRestClient.sendInternalPostRequest(eq(path), isNull())).thenReturn(apiResponse);
        when(apiResponse.isSuccess()).thenReturn(true);

        assertTrue(service.isAgreementValid(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID));

        verify(okHttpRestClient).sendInternalPostRequest(eq(path), isNull());
        verify(okHttpRestClient, never()).sendRequestProtocol(anyString(), any(), any(Supplier.class));
    }

    @Test
    @DisplayName("Agreement invalid - transferProcess not found")
    void isAgreementValid_tn_not_found() {
        when(usageControlProperties.usageControlEnabled()).thenReturn(true);
        when(transferProcessRepository.findByConsumerPidAndProviderPid(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID))
                .thenReturn(Optional.empty());

        assertThrows(AgreementNotFoundException.class, () ->
                service.isAgreementValid(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID));
    }

    @Test
    @DisplayName("Agreement valid - usageControl disabled")
    void isAgreementValid_uc_disabled() {
        when(usageControlProperties.usageControlEnabled()).thenReturn(false);
        boolean isValid = service.isAgreementValid(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID);
        assertTrue(isValid);
    }

}
