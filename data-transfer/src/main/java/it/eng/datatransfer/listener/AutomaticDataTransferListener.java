package it.eng.datatransfer.listener;

import it.eng.datatransfer.event.AutoTransferDownloadEvent;
import it.eng.datatransfer.event.AutoTransferStartEvent;
import it.eng.datatransfer.service.AutomaticDataTransferService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AutomaticDataTransferListener {
    private final AutomaticDataTransferService automaticDataTransferService;

    public AutomaticDataTransferListener(AutomaticDataTransferService automaticDataTransferService) {
        this.automaticDataTransferService = automaticDataTransferService;
    }

    @EventListener
    public void handleAutoTransferStart(AutoTransferStartEvent event) {
        automaticDataTransferService.processStart(event.transferProcessId());
    }

    @EventListener
    public void handleAutoTransferDownload(AutoTransferDownloadEvent event) {
        automaticDataTransferService.processDownload(event.transferProcessId());
    }
}
