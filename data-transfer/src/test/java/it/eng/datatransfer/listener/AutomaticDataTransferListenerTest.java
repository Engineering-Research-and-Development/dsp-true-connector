package it.eng.datatransfer.listener;

import it.eng.datatransfer.event.AutoTransferDownloadEvent;
import it.eng.datatransfer.event.AutoTransferStartEvent;
import it.eng.datatransfer.service.AutomaticDataTransferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AutomaticDataTransferListenerTest {

    @Mock
    private AutomaticDataTransferService service;

    @InjectMocks
    private AutomaticDataTransferListener listener;

    @Test
    void handleAutoTransferStart_delegatesToService() {
        AutoTransferStartEvent event = new AutoTransferStartEvent("id");
        listener.handleAutoTransferStart(event);
        verify(service).processStart("id");
    }

    @Test
    void handleAutoTransferDownload_delegatesToService() {
        AutoTransferDownloadEvent event = new AutoTransferDownloadEvent("id");
        listener.handleAutoTransferDownload(event);
        verify(service).processDownload("id");
    }
}

