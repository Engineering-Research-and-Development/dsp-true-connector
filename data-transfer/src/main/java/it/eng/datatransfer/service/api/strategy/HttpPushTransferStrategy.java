package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class HttpPushTransferStrategy implements DataTransferStrategy {

    @Override
    public void transfer(TransferProcess transferProcess) {
        log.info("Executing HTTP PUSH transfer for process {}", transferProcess.getId());
        // Implementation will go here
    }
}
