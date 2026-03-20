package it.eng.datatransfer.listener;

import it.eng.datatransfer.event.AutoTransferDownloadEvent;
import it.eng.datatransfer.event.AutoTransferStartEvent;
import it.eng.datatransfer.service.AutomaticDataTransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;

public class AutomaticDataTransferListenerTest {
    @Mock
    private AutomaticDataTransferService service;

    @InjectMocks
    private AutomaticDataTransferListener listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

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

