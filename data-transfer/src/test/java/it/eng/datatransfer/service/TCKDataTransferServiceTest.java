package it.eng.datatransfer.service;

import it.eng.datatransfer.exceptions.TransferProcessNotFoundException;
import it.eng.datatransfer.repository.TransferProcessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TCKDataTransferServiceTest {

    @InjectMocks
    private TCKDataTransferService service;

    @Mock
    private TransferProcessRepository processRepository;

    @Test
    public void testGetTransferProcess() {
        when(processRepository.findByConsumerPidAndProviderPid("consumerPid", "providerPid"))
                .thenReturn(Optional.empty()); // or a mock TransferProcess object
        assertThrows(TransferProcessNotFoundException.class, () ->
                service.findByConsumerPidAndProviderPid("consumerPid", "providerPid")
        );
    }

}
